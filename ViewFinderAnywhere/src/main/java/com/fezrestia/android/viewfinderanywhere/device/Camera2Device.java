package com.fezrestia.android.viewfinderanywhere.device;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;

import com.fezrestia.android.lib.util.log.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

/**
 * Camera functions based on Camera API 2.0.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Device implements CameraPlatformInterface {
    // Log tag.
    private static final String TAG = "Camera2Device";

    // Master context.
    private Context mContext;

    // Client callback handler.
    private Handler mClientCallbackHandler;

    // Worker thread.
    private ExecutorService mBackWorker;
    private static class BackWorkerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(TAG + "-BackWorker");
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        }
    }
    private static final int SHUTDOWN_AWAIT_TIMEOUT_MILLIS = 2000;

    // Camera API 2.0 related.
    private CameraManager mCamMng;
    private String mCamId = null;
    private CameraCharacteristics mCamCharacteristics = null;
    private StreamConfigurationMap mStreamConfigMap = null;
    private CameraDevice mCamDevice = null;
    private Surface mEvfSurface = null;
    private CameraCaptureSession mCamSession = null;
    private CaptureRequest.Builder mEvfReqBuilder = null;
    private CaptureRequest.Builder mStillCapReqBuilder = null;
    private ImageReader mStillImgReader = null;

    // Parameters.
    private int mRequestId = 0;
    private Size mPreviewStreamFrameSize = null;
    private Rect mCropRegionRect = null;
    private static final int JPEG_QUALITY = 95;

    // Client callback.
    private OpenCallback mClientOpenCallback = null;
    private CloseCallback mClientCloseCallback = null;
    private BindSurfaceCallback mClientBindSurfaceCallback = null;
    private ScanCallback mClientScanCallback = null;
    private CancelScanCallback mClientCancelScanCallback = null;
    private StillCaptureCallback mClientStillCaptureCallback = null;

    // Internal callback.
    private CameraAvailabilityCallback mCameraAvailabilityCallback;
    private CameraStateCallback mCameraStateCallback = null;
    private CaptureSessionStateCallback mCaptureSessionStateCallback = null;
    private CaptureCallback mCaptureCallback = null;

    // Sounds.
    private MediaActionSound mShutterSound;

    // Camera thread handler.
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;

    // Orientation.
    private int mOrientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN;
    private OrientationEventListenerImpl mOrientationEventListenerImpl;
    private class OrientationEventListenerImpl extends OrientationEventListener {
        /**
         * CONSTRUCTOR.
         */
        OrientationEventListenerImpl(Context context, int rate) {
            super(context, rate);
            // NOP.
        }

        @Override
        public void onOrientationChanged(int orientation) {
            mOrientationDegree = orientation;
        }
    }

    /**
     * Request TAG.
     */
    private class RequestTag {
        // Capture ID integer.
        private final int mRequestId;
        // Rotation, like as 0, 90, 180, or 270.
        private final int mRotationDeg;

        /**
         * CONSTRUCTOR.
         *
         * @param requestId Request ID.
         * @param rotationDeg Rotation degree.
         */
        RequestTag(int requestId, int rotationDeg) {
            mRequestId = requestId;
            mRotationDeg = rotationDeg;
        }

        int getRequestId() {
            return mRequestId;
        }

        int getRotation() {
            return mRotationDeg;
        }
    }

    // Invalid request ID.
    private static final int INVALID_REQUEST_ID = -1;

    /**
     * CONSTRUCTOR.
     *
     * @param context Master context.
     * @param callbackHandler Callback handler thread.
     */
    //TODO: Consider facing.
    public Camera2Device(Context context, Handler callbackHandler) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : E");

        mContext = context;
        mClientCallbackHandler = callbackHandler;

        // Camera manager.
        mCamMng = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        // Camera thread.
        mCameraHandlerThread = new HandlerThread(TAG, Thread.NORM_PRIORITY);
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        // Internal callback.
        mCameraAvailabilityCallback = new CameraAvailabilityCallback();
        mCamMng.registerAvailabilityCallback(
                mCameraAvailabilityCallback,
                mCameraHandler);

        // Orientation.
        mOrientationEventListenerImpl = new OrientationEventListenerImpl(
                mContext,
                SensorManager.SENSOR_DELAY_NORMAL);

        // Worker thread.
        mBackWorker = Executors.newSingleThreadExecutor(new BackWorkerThreadFactory());

        // Sound.
        mShutterSound = new MediaActionSound();
        mShutterSound.load(MediaActionSound.SHUTTER_CLICK);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : X");
    }

    @Override
    public void release() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : E");

        // Internal callback.
        if (mCamMng != null && mCameraAvailabilityCallback != null) {
            mCamMng.unregisterAvailabilityCallback(mCameraAvailabilityCallback);
            mCameraAvailabilityCallback = null;
        }

        // Client callback.
        mClientOpenCallback = null;
        mClientCloseCallback = null;
        mClientBindSurfaceCallback = null;
        mClientScanCallback = null;
        mClientCancelScanCallback = null;
        mClientStillCaptureCallback = null;

        // Internal callback.
        mCameraAvailabilityCallback = null;
        mCameraStateCallback = null;
        mCaptureSessionStateCallback = null;
        mCaptureCallback = null;

        // Camera thread.
        mCameraHandlerThread.quitSafely();
        try {
            mCameraHandlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Back worker.
        mBackWorker.shutdown();
        try {
            mBackWorker.awaitTermination(SHUTDOWN_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Orientation.
        mOrientationEventListenerImpl.disable();
        mOrientationEventListenerImpl = null;

        // Camera2 related.
        mCamMng = null;
        mCamId = null;
        mCamCharacteristics = null;
        mStreamConfigMap = null;
        mCamDevice = null;
        mEvfSurface = null;
        mCamSession = null;
        mEvfReqBuilder = null;
        mStillCapReqBuilder = null;
        if (mStillImgReader != null) {
            mStillImgReader.close();
            mStillImgReader = null;
        }

        // Sound.
        mShutterSound.release();

        // Context related.
        mContext = null;
        mClientCallbackHandler = null;
        mCameraHandlerThread = null;
        mCameraHandler = null;
        mBackWorker = null;

        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : X");
    }

    @Override
    public void openAsync(float evfAspectWH, OpenCallback openCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "openAsync()");

        // Check.
        if (openCallback == null) throw new NullPointerException("callback == null");

        // Cache.
        mClientOpenCallback = openCallback;

        mCameraHandler.post(new OpenTask(evfAspectWH));
    }

    private class OpenTask implements Runnable {
        private final float mViewFinderAspectRatioWH;

        /**
         * CONSTRUCTOR.
         *
         * @param aspectRatioWH Frame aspect ratio.
         */
        OpenTask(float aspectRatioWH) {
            mViewFinderAspectRatioWH = aspectRatioWH;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "OpenTask.run() : E");

            // Check.
            if (mCamDevice != null) {
                // Already opened.
                if (Log.IS_DEBUG) Log.logError(TAG, "Camera is already opened.");
                notifyOpenCallback(true);
                return;
            }

            // Get static configurations.
            try {
                // ID.
                mCamId = PDR2.getBackCameraId(mCamMng);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera ID : DONE");
                if (mCamId == null) {
                    if (Log.IS_DEBUG) Log.logError(TAG, "Back camera is not available.");
                    throw new CameraAccessException(
                            CameraAccessException.CAMERA_ERROR,
                            "Back facing camera is not available.");
                }

                // Characteristics.
                mCamCharacteristics = mCamMng.getCameraCharacteristics(mCamId);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera Characteristics : DONE");

                // Stream configurations.
                mStreamConfigMap = mCamCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera stream config map : DONE");

                // Log.
                if (Log.IS_DEBUG) {
                    PDR2.logOutputImageFormat(mStreamConfigMap);
                    PDR2.logSurfaceTextureOutputSizes(mStreamConfigMap);
                }

            } catch (CameraAccessException e) {
                if (Log.IS_DEBUG) Log.logError(TAG, "Failed to get back facing camera ID.");
                e.printStackTrace();
                notifyOpenCallback(false);
                return;
            }

            // Parameters.
            mRequestId = 0;
            mPreviewStreamFrameSize = PDR2.getPreviewStreamFrameSize(mCamCharacteristics);
            mCropRegionRect = PDR2.getAspectConsideredScalerCropRegion(
                    mCamCharacteristics,
                    mViewFinderAspectRatioWH);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Setup parameters : DONE");

            // Open.
            mCameraStateCallback = new CameraStateCallback();
            try {
                mCamMng.openCamera(mCamId, mCameraStateCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                if (Log.IS_DEBUG) Log.logError(TAG, "Failed to request open camera.");
                notifyOpenCallback(false);
                return;
            } catch (SecurityException e) {
                e.printStackTrace();
                if (Log.IS_DEBUG) Log.logError(TAG, "Open camera is not permitted.");
                notifyOpenCallback(false);
                return;
            }
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Open request : DONE");

            if (mStillImgReader == null) {
                // Create still capture request and image reader.
                // Size.
                Size jpegSize = PDR2.getOptimalJpegFrameSize(
                        mStreamConfigMap,
                        mPreviewStreamFrameSize);
                // Image reader.
                mStillImgReader = ImageReader.newInstance(
                        jpegSize.getWidth(),
                        jpegSize.getHeight(),
                        ImageFormat.JPEG,
                        2);
                mStillImgReader.setOnImageAvailableListener(
                        new OnImageAvailableListenerImpl(),
                        mCameraHandler);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Still ImageReader : DONE");
            }

            // Orientation.
            mOrientationEventListenerImpl.enable();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Orientation : DONE");

            if (Log.IS_DEBUG) Log.logDebug(TAG, "OpenTask.run() : X");
        }
    }

    @Override
    public void closeAsync(CloseCallback closeCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "closeAsync()");

        // Check.
        if (closeCallback == null) throw new NullPointerException("callback == null");

        // Cache.
        mClientCloseCallback = closeCallback;

        mCameraHandler.post(new UnbindSurfaceTask());
        mCameraHandler.post(new CloseTask());
    }

    private class CloseTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : E");

            // Check.
            if (mCamDevice == null) {
                // Already closed.
                if (Log.IS_DEBUG) Log.logError(TAG, "Camera is already closed.");
                notifyCloseCallback(false);
                return;
            }

            // Close.
            mCamDevice.close();
            mCamDevice = null;

            // Orientation.
            mOrientationEventListenerImpl.disable();

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : X");
        }
    }

    @Override
    public void bindPreviewSurfaceAsync(
            TextureView textureView,
            BindSurfaceCallback bindSurfaceCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "bindPreviewSurfaceAsync()");

        // Check.
        if (bindSurfaceCallback == null) throw new NullPointerException("callback == null");

        int previewWidth = mPreviewStreamFrameSize.getWidth();
        int previewHeight = mPreviewStreamFrameSize.getHeight();
        int finderWidth = textureView.getWidth();
        int finderHeight = textureView.getHeight();

        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "  Preview Frame Size = " + previewWidth + "x" + previewHeight);
            Log.logDebug(TAG, "  Finder Size = " + finderWidth + "x" + finderHeight);
        }

        // Transform matrix.
        Matrix matrix = PDR2.getTextureViewTransformMatrix(
                mContext,
                previewWidth,
                previewHeight,
                finderWidth,
                finderHeight);
        textureView.setTransform(matrix);

        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();

        // Cache.
        surfaceTexture.setDefaultBufferSize(
                mPreviewStreamFrameSize.getWidth(),
                mPreviewStreamFrameSize.getHeight());
        mEvfSurface = new Surface(surfaceTexture);
        mClientBindSurfaceCallback = bindSurfaceCallback;

        mCameraHandler.post(new UnbindSurfaceTask());
        mCameraHandler.post(new BindSurfaceTask());
    }

    private class BindSurfaceTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "BindSurfaceTask.run() : E");

            if (mCamDevice != null) {
                // Outputs.
                List<Surface> outputs = new ArrayList<>();
                outputs.add(mEvfSurface);
                outputs.add(mStillImgReader.getSurface());

                // Internal callback.
                mCaptureSessionStateCallback = new CaptureSessionStateCallback();

                try {
                    mCamDevice.createCaptureSession(
                            outputs,
                            mCaptureSessionStateCallback,
                            mCameraHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    if (Log.IS_DEBUG) Log.logError(TAG, "Failed to configure outputs.");
                }
            } else {
                if (Log.IS_DEBUG) Log.logError(TAG, "Camera is already released.");
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "BindSurfaceTask.run() : X");
        }
    }

    private class UnbindSurfaceTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "UnbindSurfaceTask.run() : E");

            if (mCamDevice != null && mCamSession != null) {
                // Stop previous streaming.
                stopEvfStream();

                // Close.
                mCamSession.close();
                mCamSession = null;
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "UnbindSurfaceTask.run() : X");
        }
    }

    private void startEvfStream() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "startEvfStream() : E");

        if (mCamDevice != null && mCamSession != null) {
            // Create builder.
            try {
                mEvfReqBuilder = mCamDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mEvfReqBuilder.addTarget(mEvfSurface);

                // Parameters.
                // Mode.
                mEvfReqBuilder.set(
                        CaptureRequest.CONTROL_MODE,
                        CaptureRequest.CONTROL_MODE_AUTO);
                // AF.
                mEvfReqBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // AE.
                mEvfReqBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                mEvfReqBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
                mEvfReqBuilder.set(
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
                // AWB.
                mEvfReqBuilder.set(
                        CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_AUTO);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            // Build request.
            CaptureRequest evfReq = mEvfReqBuilder.build();

            if (mCaptureCallback == null) {
                mCaptureCallback = new CaptureCallback();
            }

            // Request.
            try {
                mCamSession.setRepeatingRequest(evfReq, mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            if (Log.IS_DEBUG) Log.logError(TAG, "Camera/Session is already released.");
        }


        if (Log.IS_DEBUG) Log.logDebug(TAG, "startEvfStream() : X");
    }

    private void stopEvfStream() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stopEvfStream() : E");

        if (mCamDevice != null && mCamSession != null) {
            try {
                mCamSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            if (Log.IS_DEBUG) Log.logError(TAG, "Camera/Session is already released.");
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stopEvfStream() : X");
    }

    @Override
    public void requestScanAsync(ScanCallback scanCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScanAsync()");

        // Check.
        if (scanCallback == null) throw new NullPointerException("callback == null");

        mClientScanCallback = scanCallback;
        mCameraHandler.post(new ScanTask());
    }

    private class ScanTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ScanTask.run() : E");

            // Control trigger.
            // Setup.
            mEvfReqBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
//            mEvfReqBuilder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            CaptureRequest controlTriggerReq = mEvfReqBuilder.build();

            // Reset.
            mEvfReqBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
//            mEvfReqBuilder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            // Repeating request.
            // Setup.
            mEvfReqBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);

            CaptureRequest repeatingReq = mEvfReqBuilder.build();

            // Reset.
            mEvfReqBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);

            try {
                mCamSession.capture(controlTriggerReq, mCaptureCallback, mCameraHandler);
                mCamSession.setRepeatingRequest(repeatingReq, mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "ScanTask.run() : X");
        }
    }

    @Override
    public void requestCancelScanAsync(CancelScanCallback cancelScanCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScanAsync()");

        // Check.
        if (cancelScanCallback == null) throw new NullPointerException("callback == null");

        mClientCancelScanCallback = cancelScanCallback;
        mCameraHandler.post(new CancelScanTask());
    }

    private class CancelScanTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : E");

            // Control trigger.
            // Setup.
            mEvfReqBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
//            mEvfReqBuilder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);

            CaptureRequest controlTriggerReq = mEvfReqBuilder.build();

            // Reset.
            mEvfReqBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
//            mEvfReqBuilder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            // Repeating request.
            // Setup.
            mEvfReqBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);

            CaptureRequest repeatingReq = mEvfReqBuilder.build();

            // Reset.
            mEvfReqBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);

            try {
                mCamSession.capture(controlTriggerReq, mCaptureCallback, mCameraHandler);
                mCamSession.setRepeatingRequest(repeatingReq, mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : X");
        }
    }

    @Override
    public int requestStillCaptureAsync(StillCaptureCallback stillCaptureCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCaptureAsync()");

        // Check.
        if (stillCaptureCallback == null) throw new NullPointerException("callback == null");

        mClientStillCaptureCallback = stillCaptureCallback;

        ++mRequestId;

        mCameraHandler.post(new StillCaptureTask(mRequestId));
        return mRequestId;
    }

    private class StillCaptureTask implements Runnable {
        private final int mFixedReqId;

        /**
         * CONSTRUCTOR.
         *
         * @param requestId Capture request ID.
         */
        StillCaptureTask(int requestId) {
            mFixedReqId = requestId;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : E");
            if (Log.IS_DEBUG) Log.logDebug(TAG, "  mFixedReqId = " + mFixedReqId);

            // Create picture request.
            if (mStillCapReqBuilder == null) {
                try {
                    mStillCapReqBuilder = mCamDevice.createCaptureRequest(
                            CameraDevice.TEMPLATE_STILL_CAPTURE);

                    // AF.
                    mStillCapReqBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // AE.
                    mStillCapReqBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON);
                    mStillCapReqBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF);
                    mStillCapReqBuilder.set(
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
                    // AWB.
                    mStillCapReqBuilder.set(
                            CaptureRequest.CONTROL_AWB_MODE,
                            CaptureRequest.CONTROL_AWB_MODE_AUTO);

                    // JPEG Quality.
                    mStillCapReqBuilder.set(
                            CaptureRequest.JPEG_QUALITY,
                            (byte) JPEG_QUALITY);
                } catch (CameraAccessException e) {
                    if (Log.IS_DEBUG) Log.logError(TAG, "Failed to create capture request.");
                    e.printStackTrace();

                    // Notify.
                    StillCaptureCallback callback = mClientStillCaptureCallback;
                    notifyShutterDoneCallback(mFixedReqId);
                    notifyCaptureDoneCallback(mFixedReqId);
                    notifyPhotoStoreReadyCallback(callback, mFixedReqId, null);
                    return;
                }
            }

            // Surface.
            mStillCapReqBuilder.addTarget(mStillImgReader.getSurface());

            // Orientation.
            int jpegRot = PDR2.getFrameOrientation(
                    mCamCharacteristics,
                    mOrientationDegree);
            mStillCapReqBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegRot);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "  JPEG Rot = " + jpegRot);

            // Tag.
            RequestTag reqTag = new RequestTag(mRequestId, jpegRot);
            mStillCapReqBuilder.setTag(reqTag);

            CaptureRequest jpegReq = mStillCapReqBuilder.build();

            // Stop preview.
            stopEvfStream();

            try {
                mCamSession.capture(jpegReq, mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                if (Log.IS_DEBUG) Log.logError(TAG, "Failed to capture.");
                e.printStackTrace();

                // Notify.
                StillCaptureCallback callback = mClientStillCaptureCallback;
                notifyShutterDoneCallback(mFixedReqId);
                notifyCaptureDoneCallback(mFixedReqId);
                notifyPhotoStoreReadyCallback(callback, mFixedReqId, null);
                return;
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : X");
        }

    }

    private class HandleStillCaptureResultTask implements Runnable {
        public final String TAG = "HandleStillCaptureResultTask";

        private final StillCaptureCallback mCallback;
        private final RequestTag mReqTag;

        /**
         * CONSTRUCTOR.
         *
         * @param callback Callback.
         * @param reqTag Request tag.
         */
        HandleStillCaptureResultTask(
                StillCaptureCallback callback,
                RequestTag reqTag) {
            mCallback = callback;
            mReqTag = reqTag;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");
            if (Log.IS_DEBUG) Log.logDebug(TAG, "  Request ID = " + mReqTag.getRequestId());
            if (Log.IS_DEBUG) Log.logDebug(TAG, "  Rotation = " + mReqTag.getRotation());

            // Get image.
            Image img = mStillImgReader.acquireNextImage();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "    acquireLatestImage() : DONE");
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "    WIDTH  = " + img.getWidth());
                Log.logDebug(TAG, "    HEIGHT = " + img.getHeight());
                Log.logDebug(TAG, "    CROP   = " + img.getCropRect().toShortString());
            }
            Image.Plane[] planes = img.getPlanes();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "    getPlanes() : DONE");
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "    Plane x" + planes.length);
            }
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "    buffer.get() : DONE");

            // Close.
            img.close();

            // Process JPEG.
            byte[] resultJpeg = PDR2.doCropRotJpeg(
                    data,
                    mReqTag.getRotation(),
                    (float) mCropRegionRect.width() / (float) mCropRegionRect.height(),
                    JPEG_QUALITY);

            // Notify.
            notifyPhotoStoreReadyCallback(mCallback, mReqTag.getRequestId(), resultJpeg);

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
        }
    }



    ////// INTERNAL CALLBACKS /////////////////////////////////////////////////////////////////////

    private class CameraAvailabilityCallback extends CameraManager.AvailabilityCallback {
        public final String TAG = "CameraAvailabilityCallback";

        @Override
        public void onCameraAvailable(@NonNull String cameraId) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraAvailable() : ID=" + cameraId);

            // NOP.

        }

        @Override
        public void onCameraUnavailable(@NonNull String cameraId) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraUnavailable() : ID=" + cameraId);

            // NOP.

        }
    }

    private class CameraStateCallback extends CameraDevice.StateCallback {
        public final String TAG = "CameraStateCallback";

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOpened()");

            notifyOpenCallback(true);

            // Cache instance.
            mCamDevice = camera;
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClosed()");
            super.onClosed(camera);

            notifyCloseCallback(true);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onDisconnected()");

            notifyOpenCallback(false);
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onError()");

            notifyOpenCallback(false);
        }
    }

    private class CaptureSessionStateCallback extends CameraCaptureSession.StateCallback {
        public final String TAG = "CaptureSessionStateCallback";

        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
            super.onActive(session);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onActive()");
            // NOP.
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClosed()");

            // Stop EVF.
            stopEvfStream();

            // Release.
            mCamSession = null;
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigured()");

            // Cache.
            mCamSession = session;

            // Notify.
            notifyBindSurfaceCallback(true);

            // Start EVF.
            startEvfStream();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigureFailed()");

            // Cache.
            mCamSession = null;

            // Notify.
            notifyBindSurfaceCallback(false);
        }

        @Override
        public void onReady(@NonNull CameraCaptureSession session) {
            super.onReady(session);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReady()");
            // NOP.
        }

        @Override
        public void onSurfacePrepared(
                @NonNull CameraCaptureSession session,
                @NonNull Surface surface) {
            super.onSurfacePrepared(session, surface);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfacePrepared()");
            // NOP.
        }
    }

    private class CaptureCallback extends CameraCaptureSession.CaptureCallback {
        public final String TAG = "CaptureCallback";

        @Override
        public void onCaptureCompleted(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureCompleted()");

            Integer intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT);

            if (intent == null) {
                if (Log.IS_DEBUG) Log.logError(TAG, "CaptureIntent is null.");
            } else switch (intent) {
                case CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW:
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_PREVIEW");
                    handlePreviewIntentCaptureCompleted(result);
                    break;

                case CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE:
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_STILL_CAPTURE");

                    RequestTag reqTag = (RequestTag) request.getTag();
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "    getTag() : DONE");

                    // Release scan lock.
                    mCameraHandler.post(new CancelScanTask());

                    // Restart preview.
                    startEvfStream();

                    // Handle result in background.
                    HandleStillCaptureResultTask task = new HandleStillCaptureResultTask(
                            mClientStillCaptureCallback,
                            reqTag);
                    mBackWorker.execute(task);

                    // Notify capture done, client may request next capture.
                    if (reqTag != null) {
                        notifyCaptureDoneCallback(reqTag.getRequestId());
                    } else {
                        if (Log.IS_DEBUG) Log.logError(TAG, "RequestID is null.");
                        notifyCaptureDoneCallback(INVALID_REQUEST_ID);
                    }
                    break;

                default:
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle unexpected INTENT");
                    break;
            }
        }

        private void handlePreviewIntentCaptureCompleted(TotalCaptureResult result) {
            // Parameters.
            Integer afTrigger = result.get(CaptureResult.CONTROL_AF_TRIGGER);
            Integer aeTrigger = result.get(CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER);
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (Log.IS_DEBUG) {
                PDR2.logAfTrigger(afTrigger);
                PDR2.logAfState(afState);
                PDR2.logAeTrigger(aeTrigger);
                PDR2.logAeState(aeState);
            }

            // Scan.
            if (isScanRequired()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  Scan required.");

                // AF.
                boolean isAfLocked = false;
                boolean isAfSucceeded = false;
                if (PDR2.isAfAvailable(result)) {
                    isAfLocked = PDR2.isAfLocked(result);
                    isAfSucceeded = PDR2.isAfSucceeded(result);
                }

                // AE.
                boolean isAeLocked = false;
                if (PDR2.isAeAvailable(result)) {
                    isAeLocked = PDR2.isAeLocked(result);
                }

                // Notify.
                if (isAfLocked && isAeLocked) {
                    notifyScanDoneCallback(isAfSucceeded);
                }
            }

            // Cancel scan.
            if (isCancelScanRequired()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  Cancel scan required.");

                // AF.
                boolean isAfLocked = false;
                if (PDR2.isAfAvailable(result)) {
                    isAfLocked = PDR2.isAfLocked(result);
                }

                // AE.
                boolean isAeLocked = false;
                if (PDR2.isAeAvailable(result)) {
                    isAeLocked = PDR2.isAeLocked(result);
                }

                // Notify.
                if (!isAfLocked && !isAeLocked) {
                    notifyCancelScanDoneCallback();
                }
            }
        }

        @Override
        public void onCaptureFailed(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureFailed()");
            // NOP.
        }

        @Override
        public void onCaptureProgressed(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureProgressed()");
            // NOP.
        }

        @Override
        public void onCaptureSequenceCompleted(
                @NonNull CameraCaptureSession session,
                int sequenceId,
                long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureSequenceCompleted()");
            // NOP.
        }

        @Override
        public void onCaptureSequenceAborted(
                @NonNull CameraCaptureSession session,
                int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureSequenceAborted()");
            // NOP.
        }

        @Override
        public void onCaptureStarted(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                long timestamp,
                long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureStarted()");

            Integer intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT);

            if (intent == null) {
                if (Log.IS_DEBUG) Log.logError(TAG, "CaptureIntent is null.");
            } else if (intent == CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_STILL_CAPTURE");

                // TAG.
                RequestTag reqTag = (RequestTag) request.getTag();

                // Shutter sound.
//                mShutterSound.play(MediaActionSound.SHUTTER_CLICK);

                // Notify.
                if (reqTag != null) {
                    notifyShutterDoneCallback(reqTag.getRequestId());
                } else {
                    if (Log.IS_DEBUG) Log.logError(TAG, "RequestID is null.");
                    notifyShutterDoneCallback(INVALID_REQUEST_ID);
                }
            }
        }
    }

    private class OnImageAvailableListenerImpl implements ImageReader.OnImageAvailableListener {
        public final String TAG = "OnImageAvailableListenerImpl";

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onImageAvailable()");
            // NOP.
        }
    }



    ////// CLIENT CALLBACK ////////////////////////////////////////////////////////////////////////

    private void notifyOpenCallback(boolean isSuccess) {
        if (mClientOpenCallback != null) {
            mClientCallbackHandler.post(new NotifyOpenCallback(mClientOpenCallback, isSuccess));
            mClientOpenCallback = null;
        }
    }

    private static class NotifyOpenCallback implements Runnable {
        private final OpenCallback mOpenCallback;
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param openCallback Callback.
         * @param isSuccess Open is success or not.
         */
        NotifyOpenCallback(OpenCallback openCallback, boolean isSuccess) {
            mOpenCallback = openCallback;
            mIsSuccess = isSuccess;
        }

        @Override
        public void run (){
            mOpenCallback.onOpened(mIsSuccess);
        }
    }

    private void notifyCloseCallback(boolean isSuccess) {
        if (mClientCloseCallback != null) {
            mClientCallbackHandler.post(new NotifyCloseCallback(mClientCloseCallback, isSuccess));
            mClientCloseCallback = null;
        }
    }

    private static class NotifyCloseCallback implements Runnable {
        private final CloseCallback mCloseCallback;
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param closeCallback Callback.
         * @param isSuccess Close is success or not.
         *
         */
        NotifyCloseCallback(CloseCallback closeCallback, boolean isSuccess) {
            mCloseCallback = closeCallback;
            mIsSuccess = isSuccess;
        }

        @Override
        public void run (){
            mCloseCallback.onClosed(mIsSuccess);
        }
    }

    private void notifyBindSurfaceCallback(boolean isSuccess) {
        if (mClientBindSurfaceCallback != null) {
            mClientCallbackHandler.post(
                    new NotifyBindSurfaceCallback(mClientBindSurfaceCallback, isSuccess));
            mClientBindSurfaceCallback = null;
        }
    }

    private static class NotifyBindSurfaceCallback implements Runnable {
        private final BindSurfaceCallback mBindSurfaceCallback;
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param bindSurfaceCallback Callback.
         * @param isSuccess Bind surface is success or not.
         */
        NotifyBindSurfaceCallback(
                BindSurfaceCallback bindSurfaceCallback,
                boolean isSuccess) {
            mBindSurfaceCallback = bindSurfaceCallback;
            mIsSuccess = isSuccess;
        }

        @Override
        public void run (){
            mBindSurfaceCallback.onSurfaceBound(mIsSuccess);
        }
    }

    private boolean isScanRequired() {
        return mClientScanCallback != null;
    }

    private void notifyScanDoneCallback(boolean isSuccess) {
        if (mClientScanCallback != null) {
            mClientCallbackHandler.post(
                    new NotifyScanDoneCallback(mClientScanCallback, isSuccess));
            mClientScanCallback = null;
        }
    }

    private static class NotifyScanDoneCallback implements Runnable {
        private final ScanCallback mScanCallback;
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param scanCallback Callback.
         * @param isSuccess Scan is success or not.
         */
        NotifyScanDoneCallback(ScanCallback scanCallback, boolean isSuccess) {
            mScanCallback = scanCallback;
            mIsSuccess = isSuccess;
        }

        @Override
        public void run() {
            mScanCallback.onScanDone(mIsSuccess);
        }
    }

    private boolean isCancelScanRequired() {
        return mClientCancelScanCallback != null;
    }

    private void notifyCancelScanDoneCallback() {
        if (mClientCancelScanCallback != null) {
            mClientCallbackHandler.post(
                    new NotifyCancelScanDoneCallback(mClientCancelScanCallback));
            mClientCancelScanCallback = null;
        }
    }

    private static class NotifyCancelScanDoneCallback implements Runnable {
        private final CancelScanCallback mCancelScanCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param cancelScanCallback Callback.
         */
        NotifyCancelScanDoneCallback(CancelScanCallback cancelScanCallback) {
            mCancelScanCallback = cancelScanCallback;
        }

        @Override
        public void run() {
            mCancelScanCallback.onCancelScanDone();
        }
    }

    private void notifyShutterDoneCallback(int requestId) {
        if (mClientStillCaptureCallback != null) {
            mClientCallbackHandler.post(
                    new NotifyShutterDoneCallback(mClientStillCaptureCallback, requestId));

            // Do not set null to mClientStillCaptureCallback. Wait for capture done.
        }
    }

    private static class NotifyShutterDoneCallback implements Runnable {
        private final StillCaptureCallback mStillCaptureCallback;
        private final int mRequestId;

        /**
         * CONSTRUCTOR.
         *
         * @param stillCaptureCallback Callback.
         * @param requestId Capture request ID.
         */
        NotifyShutterDoneCallback(
                StillCaptureCallback stillCaptureCallback,
                int requestId) {
            mStillCaptureCallback = stillCaptureCallback;
            mRequestId = requestId;
        }

        @Override
        public void run() {
            mStillCaptureCallback.onShutterDone(mRequestId);
        }
    }

    private void notifyCaptureDoneCallback(int requestId) {
        if (mClientStillCaptureCallback != null) {
            mClientCallbackHandler.post(
                    new NotifyCaptureDoneCallback(mClientStillCaptureCallback, requestId));
            mClientStillCaptureCallback = null;
        }
    }

    private static class NotifyCaptureDoneCallback implements Runnable {
        private final StillCaptureCallback mStillCaptureCallback;
        private final int mRequestId;

        NotifyCaptureDoneCallback(
                StillCaptureCallback stillCaptureCallback,
                int requestId) {
            mStillCaptureCallback = stillCaptureCallback;
            mRequestId = requestId;
        }

        @Override
        public void run() {
            mStillCaptureCallback.onCaptureDone(mRequestId);
        }
    }

    private void notifyPhotoStoreReadyCallback(
            StillCaptureCallback callback,
            int requestId,
            byte[] data) {
        mClientCallbackHandler.post(
                new NotifyPhotoStoreReadyCallback(
                        callback,
                        requestId,
                        data));
    }

    private static class NotifyPhotoStoreReadyCallback implements Runnable {
        private final StillCaptureCallback mStillCaptureCallback;
        private final int mRequestId;
        private final byte[] mData;

        /**
         * CONSTRUCTOR.
         *
         * @param stillCaptureCallback Callback.
         * @param requestId Capture request ID.
         * @param data JPEG frame data.
         */
        NotifyPhotoStoreReadyCallback(
                StillCaptureCallback stillCaptureCallback,
                int requestId,
                byte[] data) {
            mStillCaptureCallback = stillCaptureCallback;
            mRequestId = requestId;
            mData = data;
        }

        @Override
        public void run() {
            mStillCaptureCallback.onPhotoStoreReady(mRequestId, mData);
        }
    }
}
