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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
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
    private static final int SHUTDOWN_AWAIT_TIMEOUT_MILLIS = 5000;

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

    // Internal callback.
    private CameraAvailabilityCallback mCameraAvailabilityCallback;
    private CameraStateCallback mCameraStateCallback = null;
    private CaptureSessionStateCallback mCaptureSessionStateCallback = null;
    private CaptureCallback mCaptureCallback = null;

    // Sounds.
    private MediaActionSound mShutterSound;

    // Camera thread handler.
    private HandlerThread mCallbackHandlerThread;
    private Handler mCallbackHandler;
    private HandlerThread mRequestHandlerThread;
    private Handler mRequestHandler;

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

    /**
     * CONSTRUCTOR.
     *
     * @param context Master context.
     * @param callbackHandler Callback handler thread.
     */
    //TODO: Consider facing.
    public Camera2Device(Context context, Handler callbackHandler) {
        mContext = context;
        mClientCallbackHandler = callbackHandler;
    }

    @Override
    public void prepare() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "prepare() : E");

        // Camera manager.
        mCamMng = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        // Camera thread.
        mCallbackHandlerThread = new HandlerThread("camera-callback", Thread.NORM_PRIORITY);
        mCallbackHandlerThread.start();
        mCallbackHandler = new Handler(mCallbackHandlerThread.getLooper());

        // API request handler.
        mRequestHandlerThread = new HandlerThread("request", Thread.NORM_PRIORITY);
        mRequestHandlerThread.start();
        mRequestHandler = new Handler(mRequestHandlerThread.getLooper());

        // Worker thread.
        mBackWorker = Executors.newSingleThreadExecutor(new BackWorkerThreadFactory());

        // Internal callback.
        mCameraAvailabilityCallback = new CameraAvailabilityCallback();
        mCamMng.registerAvailabilityCallback(
                mCameraAvailabilityCallback,
                mCallbackHandler);

        // Orientation.
        mOrientationEventListenerImpl = new OrientationEventListenerImpl(
                mContext,
                SensorManager.SENSOR_DELAY_NORMAL);

        // Sound.
        mShutterSound = new MediaActionSound();
        mShutterSound.load(MediaActionSound.SHUTTER_CLICK);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "prepare() : X");
    }

    @Override
    public void release() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : E");

        // API request handler.
        mRequestHandlerThread.quitSafely();
        try {
            mRequestHandlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Camera thread.
        mCallbackHandlerThread.quitSafely();
        try {
            mCallbackHandlerThread.join();
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

        // Internal callback.
        if (mCamMng != null && mCameraAvailabilityCallback != null) {
            mCamMng.unregisterAvailabilityCallback(mCameraAvailabilityCallback);
            mCameraAvailabilityCallback = null;
        }

        // Internal callback.
        mCameraAvailabilityCallback = null;
        mCameraStateCallback = null;
        mCaptureSessionStateCallback = null;
        mCaptureCallback = null;

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
        mCallbackHandlerThread = null;
        mCallbackHandler = null;
        mRequestHandlerThread = null;
        mRequestHandler = null;
        mBackWorker = null;

        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : X");
    }

    @Override
    public void openAsync(float evfAspectWH, OpenCallback openCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "openAsync()");

        mRequestHandler.post(new OpenTask(evfAspectWH, openCallback));
    }

    private class OpenTask implements Runnable {
        final String TAG = "OpenTask";

        private final float mViewFinderAspectRatioWH;
        private final OpenCallback mCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param aspectRatioWH Frame aspect ratio.
         * @param callback OpenCallback.
         */
        OpenTask(float aspectRatioWH, OpenCallback callback) {
            mViewFinderAspectRatioWH = aspectRatioWH;
            mCallback = callback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");

            if (mCamDevice != null) {
                Log.logError(TAG, "Early Return : Camera is already opened.");
                mClientCallbackHandler.post( () -> mCallback.onOpened(true) );
                return;
            }

            // Get static configurations.
            try {
                // ID.
                mCamId = PDR2.getBackCameraId(mCamMng);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera ID : DONE");
                if (mCamId == null) {
                    Log.logError(TAG, "Back camera is not available.");
                    mClientCallbackHandler.post( () -> mCallback.onOpened(false) );
                }

                // Characteristics.
                mCamCharacteristics = mCamMng.getCameraCharacteristics(mCamId);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera Characteristics : DONE");

                // Stream configurations.
                mStreamConfigMap = mCamCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera stream config map : DONE");

                if (Log.IS_DEBUG) {
                    PDR2.logOutputImageFormat(mStreamConfigMap);
                    PDR2.logSurfaceTextureOutputSizes(mStreamConfigMap);
                }

            } catch (CameraAccessException e) {
                Log.logError(TAG, "Failed to get back facing camera ID.");
                mClientCallbackHandler.post( () -> mCallback.onOpened(false) );
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
                mCamMng.openCamera(mCamId, mCameraStateCallback, mCallbackHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Log.logError(TAG, "Failed to request open camera.");
                mClientCallbackHandler.post( () -> mCallback.onOpened(false) );
                return;
            } catch (SecurityException e) {
                e.printStackTrace();
                Log.logError(TAG, "Open camera is not permitted.");
                mClientCallbackHandler.post( () -> mCallback.onOpened(false) );
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
                        mCallbackHandler);
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Still ImageReader : DONE");
            }

            // Orientation.
            mOrientationEventListenerImpl.enable();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Orientation : DONE");

            boolean isSucceeded = mCameraStateCallback.waitForOpened();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera open " + (isSucceeded ? "SUCCEEDED" : "FAILED"));
            mClientCallbackHandler.post( () -> mCallback.onOpened(isSucceeded) );

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
        }
    }

    @Override
    public void closeAsync(CloseCallback closeCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "closeAsync()");

        mRequestHandler.post(new UnbindSurfaceTask());
        mRequestHandler.post(new CloseTask(closeCallback));
    }

    private class CloseTask implements Runnable {
        final String TAG = "CloseTask";

        private final CloseCallback mCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param callback CloseCallback.
         */
        CloseTask(CloseCallback callback) {
            mCallback = callback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");

            if (mCamDevice == null) {
                Log.logError(TAG, "Early Return : Camera is already closed.");
                mClientCallbackHandler.post( () -> mCallback.onClosed(true) );
                return;
            }

            // Camera.
            mCamDevice.close();
            mCamDevice = null;

            // Orientation.
            mOrientationEventListenerImpl.disable();

            boolean isSucceeded = mCameraStateCallback.waitForClosed();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera close " + (isSucceeded ? "SUCCEEDED" : "FAILED"));
            mClientCallbackHandler.post( () -> mCallback.onClosed(isSucceeded) );

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
        }
    }

    @Override
    public void bindPreviewSurfaceAsync(
            TextureView textureView,
            BindSurfaceCallback bindSurfaceCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "bindPreviewSurfaceAsync()");

        mRequestHandler.post(new BindSurfaceTask(textureView, bindSurfaceCallback));
    }

    private class BindSurfaceTask implements Runnable {
        final String TAG = "BindSurfaceTask";

        private final TextureView mTextureView;
        private final BindSurfaceCallback mCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param textureView TextureView for EVF.
         * @param callback BindSurfaceCallback.
         */
        BindSurfaceTask(TextureView textureView, BindSurfaceCallback callback) {
            mTextureView = textureView;
            mCallback = callback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");

            if (mCamSession != null) {
                Log.logError(TAG, "Early Return : mCamSession != null");
                mClientCallbackHandler.post( () -> mCallback.onSurfaceBound(true) );
                return;
            }

            int previewWidth = mPreviewStreamFrameSize.getWidth();
            int previewHeight = mPreviewStreamFrameSize.getHeight();
            int finderWidth = mTextureView.getWidth();
            int finderHeight = mTextureView.getHeight();

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
            mTextureView.setTransform(matrix);

            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(
                    mPreviewStreamFrameSize.getWidth(),
                    mPreviewStreamFrameSize.getHeight());
            mEvfSurface = new Surface(surfaceTexture);

            // Outputs.
            List<Surface> outputs = new ArrayList<>();
            outputs.add(mEvfSurface);
            outputs.add(mStillImgReader.getSurface());

            mCaptureSessionStateCallback = new CaptureSessionStateCallback();
            try {
                mCamDevice.createCaptureSession(
                        outputs,
                        mCaptureSessionStateCallback,
                        mCallbackHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Log.logError(TAG, "Failed to configure outputs.");
                mClientCallbackHandler.post( () -> mCallback.onSurfaceBound(false) );
            }

            boolean isSucceeded = mCaptureSessionStateCallback.waitForOpened();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Bind surface " + (isSucceeded ? "SUCCEEDED" : "FAILED"));
            mClientCallbackHandler.post( () -> mCallback.onSurfaceBound(isSucceeded) );

            startEvfStream();

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
        }
    }

    private class UnbindSurfaceTask implements Runnable {
        final String TAG = "UnbindSurfaceTask";

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");

            if (mCamSession == null) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Early Return : mCamSession == null");
                return;
            }

            stopEvfStream();

            mCamSession.close();

            boolean isSucceeded = mCaptureSessionStateCallback.waitForClosed();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Unbind surface " + (isSucceeded ? "SUCCEEDED" : "FAILED"));

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
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
                mCamSession.setRepeatingRequest(evfReq, mCaptureCallback, mCallbackHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            Log.logError(TAG, "Camera/Session is already released.");
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
            Log.logError(TAG, "Camera/Session is already released.");
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stopEvfStream() : X");
    }

    @Override
    public void requestScanAsync(ScanCallback scanCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScanAsync()");

        mRequestHandler.post(new ScanTask(scanCallback));
    }

    private class ScanTask implements Runnable {
        final String TAG = "ScanTask";

        private final ScanCallback mCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param callback ScanCallback.
         */
        ScanTask(ScanCallback callback) {
            mCallback = callback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");

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

            mCaptureCallback.requestDetectScanDone();
            try {
                mCamSession.capture(controlTriggerReq, mCaptureCallback, mCallbackHandler);
                mCamSession.setRepeatingRequest(repeatingReq, mCaptureCallback, mCallbackHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            boolean isSucceeded = mCaptureCallback.waitForScanDone();

            if (Log.IS_DEBUG) Log.logDebug(TAG, "Request scan " + (isSucceeded ? "SUCCEEDED" : "FAILED"));
            mClientCallbackHandler.post( () -> mCallback.onScanDone(isSucceeded) );

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
        }
    }

    @Override
    public void requestCancelScanAsync(CancelScanCallback cancelScanCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScanAsync()");

        mRequestHandler.post(new CancelScanTask(cancelScanCallback));
    }

    private class CancelScanTask implements Runnable {
        final String TAG = "CancelScanTask";

        private final CancelScanCallback mCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param callback CancelScanCallback
         */
        CancelScanTask(CancelScanCallback callback) {
            mCallback = callback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");

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

            mCaptureCallback.requestDetectCancelScanDone();
            try {
                mCamSession.capture(controlTriggerReq, mCaptureCallback, mCallbackHandler);
                mCamSession.setRepeatingRequest(repeatingReq, mCaptureCallback, mCallbackHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCaptureCallback.waitForCancelScanDone();

            mClientCallbackHandler.post( mCallback::onCancelScanDone );

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
        }
    }

    @Override
    public int requestStillCaptureAsync(StillCaptureCallback stillCaptureCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCaptureAsync()");

        ++mRequestId;

        mRequestHandler.post(new StillCaptureTask(mRequestId, stillCaptureCallback));

        return mRequestId;
    }

    private class StillCaptureTask implements Runnable {
        final String TAG = "StillCaptureTask";

        private final int mFixedReqId;
        private StillCaptureCallback mCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param requestId Capture request ID.
         */
        StillCaptureTask(int requestId, StillCaptureCallback callback) {
            mFixedReqId = requestId;
            mCallback = callback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E");
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
                    if (Log.IS_DEBUG) Log.logError(TAG, "Early Return : Failed to create capture request.");
                    e.printStackTrace();

                    // Notify.
                    mClientCallbackHandler.post( () -> mCallback.onShutterDone(mFixedReqId) );
                    mClientCallbackHandler.post( () -> mCallback.onCaptureDone(mFixedReqId) );
                    mClientCallbackHandler.post( () -> mCallback.onPhotoStoreReady(mFixedReqId, null) );

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

            mCaptureCallback.requestDetectShutterDone();
            mCaptureCallback.requestDetectCaptureDone();
            mCaptureCallback.requestHandlePhotoStoreReadyCallback(mCallback);
            try {
                mCamSession.capture(jpegReq, mCaptureCallback, mCallbackHandler);
            } catch (CameraAccessException e) {
                Log.logError(TAG, "Early Return : Failed to capture.");
                e.printStackTrace();

                // Notify.
                mClientCallbackHandler.post( () -> mCallback.onShutterDone(mFixedReqId) );
                mClientCallbackHandler.post( () -> mCallback.onCaptureDone(mFixedReqId) );
                mClientCallbackHandler.post( () -> mCallback.onPhotoStoreReady(mFixedReqId, null) );

                return;
            }
            final int onShutterRequestId = mCaptureCallback.waitForShutterDone();
            mClientCallbackHandler.post( () -> mCallback.onShutterDone(onShutterRequestId) );
            final int onCaptureRequestId = mCaptureCallback.waitForCaptureDone();
            mClientCallbackHandler.post( () -> mCallback.onCaptureDone(onCaptureRequestId) );

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X");
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
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "  Request ID = " + mReqTag.getRequestId());
                Log.logDebug(TAG, "  Rotation = " + mReqTag.getRotation());
            }

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

            mClientCallbackHandler.post( () -> mCallback.onPhotoStoreReady(mReqTag.getRequestId(), resultJpeg) );

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

        private final CountDownLatch mOpenLatch = new CountDownLatch(1);
        private final CountDownLatch mCloseLatch = new CountDownLatch(1);

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOpened()");

            mCamDevice = camera;

            mOpenLatch.countDown();
        }

        boolean waitForOpened() {
            return waitForLatch(mOpenLatch);
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClosed()");

            mCamDevice = null;

            mCloseLatch.countDown();
        }

        boolean waitForClosed() {
            return waitForLatch(mCloseLatch);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.logError(TAG, "onDisconnected()");

            // Already closed.
            mCloseLatch.countDown();

            // TODO: Handle disconnected error.

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.logError(TAG, "onError() : error=" + error);

            // Already closed.
            mCloseLatch.countDown();

            // TODO: Handle error.

        }
    }

    private class CaptureSessionStateCallback extends CameraCaptureSession.StateCallback {
        public final String TAG = "CaptureSessionStateCallback";

        private final CountDownLatch mOpenLatch = new CountDownLatch(1);
        private final CountDownLatch mCloseLatch = new CountDownLatch(1);

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

            mCamSession = null;

            mCloseLatch.countDown();
        }

        boolean waitForClosed() {
            return waitForLatch(mCloseLatch);
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigured()");

            mCamSession = session;

            mOpenLatch.countDown();
        }

        boolean waitForOpened() {
            return waitForLatch(mOpenLatch);
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigureFailed()");

            mCamSession = null;

            mOpenLatch.countDown();
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
        final String TAG = "CaptureCallback";

        private CaptureRequest mLatestRequest;
        private TotalCaptureResult mLatestResult;

        private CountDownLatch mScanDoneLatch = null;
        private CountDownLatch mCancelScanDoneLatch = null;

        private CountDownLatch mShutterDoneLatch = null;
        private CountDownLatch mCaptureDoneLatch = null;

        private StillCaptureCallback mCallback = null;

        @Override
        public void onCaptureStarted(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                long timestamp,
                long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureStarted()");

            mLatestRequest = request;

            Integer intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT);

            if (intent == null) {
                if (Log.IS_DEBUG) Log.logError(TAG, "CaptureIntent is null.");
            } else if (intent == CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_STILL_CAPTURE");

                // Shutter sound.
//                mShutterSound.play(MediaActionSound.SHUTTER_CLICK);

                mShutterDoneLatch.countDown();
            }
        }

        @Override
        public void onCaptureCompleted(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureCompleted()");

            mLatestResult = result;

            Integer intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT);

            if (intent == null) {
                Log.logError(TAG, "CaptureIntent is null.");
            } else switch (intent) {
                case CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW:
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_PREVIEW");
                    handlePreviewIntentCaptureCompleted(result);
                    break;

                case CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE:
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_STILL_CAPTURE");
                    handleStillIntentCaptureCompleted(request);
                    break;

                default:
                    Log.logError(TAG, "  handle unexpected INTENT");
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

            // AF.
            boolean isAfAvailable = PDR2.isAfAvailable(result);
            boolean isAfLocked = PDR2.isAfLocked(result);
            // AE.
            boolean isAeAvailable = PDR2.isAeAvailable(result);
            boolean isAeLocked = PDR2.isAeLocked(result);

            // Scan.
            if (mScanDoneLatch != null) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  Scan required.");

                if ((isAfLocked || !isAfAvailable) && (isAeLocked || !isAeAvailable)) {
                    mScanDoneLatch.countDown();
                }
            }

            // Cancel scan.
            if (mCancelScanDoneLatch != null) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  Cancel scan required.");

                if ((!isAfLocked || !isAfAvailable) && (!isAeLocked || !isAeAvailable)) {
                    mCancelScanDoneLatch.countDown();
                }
            }
        }

        void requestDetectScanDone() {
            mScanDoneLatch = new CountDownLatch(1);
        }

        boolean waitForScanDone() {
            waitForLatch(mScanDoneLatch);
            mScanDoneLatch = null;
            return PDR2.isAfSucceeded(mLatestResult);
        }

        void requestDetectCancelScanDone() {
            mCancelScanDoneLatch = new CountDownLatch(1);
        }

        void waitForCancelScanDone() {
            waitForLatch(mCancelScanDoneLatch);
            mCancelScanDoneLatch = null;
        }

        private void handleStillIntentCaptureCompleted(CaptureRequest request) {
            RequestTag reqTag = (RequestTag) request.getTag();

            // Release scan lock.
            mRequestHandler.post(new CancelScanTask( () -> {} ));

            // Restart preview.
            startEvfStream();

            // Handle result in background.
            HandleStillCaptureResultTask task = new HandleStillCaptureResultTask(
                    mCallback,
                    reqTag);
            mBackWorker.execute(task);

            mCaptureDoneLatch.countDown();
        }

        void requestDetectShutterDone() {
            mShutterDoneLatch = new CountDownLatch(1);
        }

        int waitForShutterDone() {
            waitForLatch(mShutterDoneLatch);
            mShutterDoneLatch = null;

            RequestTag reqTag = (RequestTag) Objects.requireNonNull(mLatestRequest.getTag());
            return reqTag.getRequestId();
        }

        void requestDetectCaptureDone() {
            mCaptureDoneLatch = new CountDownLatch(1);
        }

        int waitForCaptureDone() {
            waitForLatch(mCaptureDoneLatch);
            mCaptureDoneLatch = null;

            RequestTag reqTag = (RequestTag) Objects.requireNonNull(mLatestRequest.getTag());
            return reqTag.getRequestId();
        }

        void requestHandlePhotoStoreReadyCallback(StillCaptureCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onCaptureFailed(
                @NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureFailed()");

            // TODO: Handle capture error.

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
    }

    private class OnImageAvailableListenerImpl implements ImageReader.OnImageAvailableListener {
        public final String TAG = "OnImageAvailableListenerImpl";

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onImageAvailable()");
            // NOP.
        }
    }

    private boolean waitForLatch(CountDownLatch latch) {
        try {
            boolean isOk = latch.await(SHUTDOWN_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!isOk) {
                Log.logError(TAG, "waitForLatch() : TIMEOUT");
            }
            return isOk;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

}
