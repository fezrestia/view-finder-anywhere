package com.fezrestia.android.viewfinderanywhere.device;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaActionSound;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;
import com.fezrestia.android.viewfinderanywhere.storage.StorageController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Camera functions based on Camera API 2.0.
 */
public class Camera2Device implements CameraPlatformInterface {
    // Log tag.
    private static final String TAG = "Camera2Device";

    // Master context.
    private Context mContext = null;

    // Client callback handler.
    private Handler mClientCallbackHandler = null;

    // Camera API 2.0 related.
    private CameraManager mCamMng = null;
    private String mCamId = null;
    private CameraCharacteristics mCamCharacteristics = null;
    private StreamConfigurationMap mStreamConfigMap = null;
    private CameraDevice mCamDevice = null;
    private Surface mEvfSurface = null;
    private CameraCaptureSession mCamSession = null;
    private CaptureRequest.Builder mEvfReqBuilder = null;

    // Parameters.
    private Size mPreviewStreamFrameSize = null;
    private Rect mCropRegionRect = null;

    // Client callback.
    private OpenCallback mClientOpenCallback = null;
    private CloseCallback mClientCloseCallback = null;
    private BindSurfaceCallback mClientBindSurfaceCallback = null;

    // Internal callback.
    private CameraAvailabilityCallback mCameraAvailabilityCallback = null;
    private CameraStateCallback mCameraStateCallback = null;
    private CaptureSessionStateCallback mCaptureSessionStateCallback = null;
    private CaptureCallback mCaptureCallback = null;

    // Storage.
    private StorageController mStorageController = null;

    // Sounds.
    private MediaActionSound mScanSuccessSound = null;

    // Wake lock.
    private PowerManager.WakeLock mWakeLock = null;

    // Camera thread handler.
    private HandlerThread mCameraHandlerThread = null;
    private Handler mCameraHandler = null;

    // Orientation.
    private int mOrientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN;
    private OrientationEventListenerImpl mOrientationEventListenerImpl = null;
    private class OrientationEventListenerImpl extends OrientationEventListener {
        /**
         * CONSTRUCTOR.
         */
        public OrientationEventListenerImpl(Context context, int rate) {
            super(context, rate);
            // NOP.
        }

        @Override
        public void onOrientationChanged(int orientation) {
            mOrientationDegree = orientation;
        }
    }

    /**
     * CONSTRUCTOR.
     *
     * @param context
     * @param callbackHandler
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
        mEvfReqBuilder = null;

        // Context related.
        mContext = null;
        mClientCallbackHandler = null;
        mCameraHandlerThread = null;
        mCameraHandler = null;

        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : X");
    }

    @Override
    public void openAsync(float evfAspectWH, OpenCallback openCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "openAsync()");

        // Check.
        if (openCallback == null) throw new NullPointerException("openCallback == null");

        // Cache.
        mClientOpenCallback = openCallback;

        mCameraHandler.post(new OpenTask(evfAspectWH));
    }

    private class OpenTask implements Runnable {
        private final float mViewFinderAspectRatioWH;

        /**
         * CONSTRUCTOR.
         *
         * @param aspectRatioWH
         */
        public OpenTask(float aspectRatioWH) {
            mViewFinderAspectRatioWH = aspectRatioWH;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "OpenTask.run() : E");

            // Check.
            if (mCamDevice != null) {
                // Already opened.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera is already opened.");
                mClientCallbackHandler.post(new NotifyOpenCallback(true));
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

                // Log.
                PDR2.logOutputImageFormat(mStreamConfigMap);
                PDR2.logSurfaceTextureOutputSizes(mStreamConfigMap);

            } catch (CameraAccessException e) {
                if (Log.IS_DEBUG) Log.logError(TAG, "Failed to get back facing camera ID.");
                e.printStackTrace();
                mClientCallbackHandler.post(new NotifyOpenCallback(false));
                return;
            }

            // Parameters.
            mPreviewStreamFrameSize = PDR2.getPreviewStreamFrameSize(mCamCharacteristics);
            mCropRegionRect = PDR2.getAspectConsideredScalerCropRegion(
                    mCamCharacteristics,
                    mViewFinderAspectRatioWH);

            // Open.
            mCameraStateCallback = new CameraStateCallback();
            try {
                mCamMng.openCamera(mCamId, mCameraStateCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                if (Log.IS_DEBUG) Log.logError(TAG, "Failed to request open camera.");
                mClientCallbackHandler.post(new NotifyOpenCallback(false));
                return;
            }

            // Orientation.
            mOrientationEventListenerImpl.enable();

            if (Log.IS_DEBUG) Log.logDebug(TAG, "OpenTask.run() : X");
        }
    }

    @Override
    public void closeAsync(CloseCallback closeCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "closeAsync()");

        // Check.
        if (closeCallback == null) throw new NullPointerException("closeCallback == null");

        // Cache.
        mClientCloseCallback = closeCallback;

        mCameraHandler.post(new UnbindPreviewSurfaceTask());
        mCameraHandler.post(new CloseTask());
    }

    private class CloseTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : E");

            // Check.
            if (mCamDevice == null) {
                // Already closed.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera is already closed.");
                mClientCallbackHandler.post(new NotifyCloseCallback(true));
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

        int previewWidth = mPreviewStreamFrameSize.getWidth();
        int previewHeight = mPreviewStreamFrameSize.getHeight();
        int finderWidth = textureView.getWidth();
        int finderHeight = textureView.getHeight();

        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "  Preview Frame Size = " + previewWidth + "x" + previewHeight);
        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "  Finder Size = " + finderWidth + "x" + finderHeight);

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

        mCameraHandler.post(new UnbindPreviewSurfaceTask());
        mCameraHandler.post(new BindPreviewSurfaceTask(mEvfSurface));
    }

    private class BindPreviewSurfaceTask implements Runnable {
        private final Surface mSurface;

        /**
         * CONSTRUCTOR.
         *
         * @param surface
         */
        BindPreviewSurfaceTask(Surface surface) {
            mSurface = surface;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "BindPreviewSurfaceTask.run() : E");

            if (mCamDevice != null) {
                // Outputs.
                List<Surface> outputs = new ArrayList<Surface>();
                outputs.add(mSurface);
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

            if (Log.IS_DEBUG) Log.logDebug(TAG, "BindPreviewSurfaceTask.run() : X");
        }
    }

    private class UnbindPreviewSurfaceTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "UnbindPreviewSurfaceTask.run() : E");

            if (mCamDevice != null && mCamSession != null) {
                // Stop previous streaming.
                stopEvfStream();

                // Close.
                mCamSession.close();
                mCamSession = null;
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "UnbindPreviewSurfaceTask.run() : X");
        }
    }

    private void startEvfStream() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "startEvfStream() : E");

        if (mCamDevice != null && mCamSession != null) {
            // Create builder.
            try {
                mEvfReqBuilder = mCamDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mEvfReqBuilder.addTarget(mEvfSurface);

                //TODO: Consider use crop region for preview. Currently, 1x1 frame is a bit zoomed.
//                mEvfReqBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCropRegionRect);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            // Build request.
            CaptureRequest evfReq = mEvfReqBuilder.build();

            // Requets.
            mCaptureCallback = new CaptureCallback();
            try {
                mCamSession.setRepeatingRequest(evfReq, mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera/Session is already released.");
        }


        if (Log.IS_DEBUG) Log.logDebug(TAG, "startEvfStream() : X");
    }

    private void stopEvfStream() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stopEvfStream() : E");

        if (mCamDevice != null && mCamSession != null) {
            try {
                mCamSession.stopRepeating();
                mCamSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera/Session is already released.");
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stopEvfStream() : X");
    }



    /**
     * Start scan.
     */
    public void requestScanAsync() {
        requestScanAsync(null);
    }

    @Override
    public void requestScanAsync(ScanCallback scanCallback) {
        mCameraHandler.post(mScanTask);
    }

    private class ScanCallbackImpl implements ScanCallback {
        @Override
        public void onScanDone(boolean isSuccess) {
            OverlayViewFinderController.getInstance().getCurrentState().onScanDone(isSuccess);
        }
    }

    private final ScanTask mScanTask = new ScanTask(new ScanCallbackImpl());
    private class ScanTask implements Runnable {
        private CountDownLatch mLatch = null;
        private boolean isCanceled = false;
        private ScanCallback mScanCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param scanCallback
         */
        public ScanTask(ScanCallback scanCallback) {
            mScanCallback = scanCallback;
        }

        public void cancel() {
            isCanceled = true;
            if (mLatch != null) {
                mLatch.countDown();
            }
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ScanTask.run() : E");


/*

            if (mCamera != null) {
                isCanceled = false;
                mLatch = new CountDownLatch(1);
                FocusCallbackImpl focusCallbackImpl = new FocusCallbackImpl(mLatch);

                // Do scan.
                mCamera.autoFocus(focusCallbackImpl);

                try {
                    mLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new UnsupportedOperationException("Why thread is interrupted ?");
                }

                if (!isCanceled) {
                    // Sound.
                    if (focusCallbackImpl.isSuccess()) {
                        mScanSuccessSound.play(MediaActionSound.FOCUS_COMPLETE);
                    }

                    // Lock AE and AWB.
                    Camera.Parameters params = mCamera.getParameters();
                    if (params.isAutoExposureLockSupported()) {
                        params.setAutoExposureLock(true);
                    }
                    if (params.isAutoWhiteBalanceLockSupported()) {
                        params.setAutoWhiteBalanceLock(true);
                    }
                    mCamera.setParameters(params);

                    mScanCallback.onScanDone(focusCallbackImpl.isSuccess());
                }
            } else {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.");
            }



*/


            if (Log.IS_DEBUG) Log.logDebug(TAG, "ScanTask.run() : X");
        }
    }

    private class FocusCallbackImpl implements Camera.AutoFocusCallback {
        private final CountDownLatch mLatch;
        private boolean mIsSuccess = false;

        /**
         * CONSTRUCTOR.
         *
         * @param latch
         */
        public FocusCallbackImpl(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onAutoFocus(boolean isSuccess, Camera camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onAutoFocus() : [isSuccess=" + isSuccess + "]");

            mIsSuccess = isSuccess;
            mLatch.countDown();
        }

        public boolean isSuccess() {
            return mIsSuccess;
        }
    }

    /**
     * Cancel scan.
     */
    public void requestCancelScanAsync() {
        requestCancelScanAsync(null);
    }

    @Override
    public void requestCancelScanAsync(CancelScanCallback cancelScanCallback) {
        // Cancel scan task.
        mScanTask.cancel();

        mCameraHandler.post(mCancelScanTask);
    }

    private class CancelScanCallbackImpl implements CancelScanCallback {
        @Override
        public void onCancelScanDone() {
            // NOP.
        }
    }

    private final CancelScanTask mCancelScanTask = new CancelScanTask(new CancelScanCallbackImpl());
    private class CancelScanTask implements Runnable {
        private CancelScanCallback mCancelScanCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param cancelScanCallback
         */
        public CancelScanTask(CancelScanCallback cancelScanCallback) {
            mCancelScanCallback = cancelScanCallback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : E");

/*

            if (mCamera != null) {
                mCamera.cancelAutoFocus();

                // Unlock AE and AWB.
                Camera.Parameters params = mCamera.getParameters();
                if (params.isAutoExposureLockSupported()) {
                    params.setAutoExposureLock(false);
                }
                if (params.isAutoWhiteBalanceLockSupported()) {
                    params.setAutoWhiteBalanceLock(false);
                }
                mCamera.setParameters(params);
            }

            mCancelScanCallback.onCancelScanDone();


*/


            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : X");
        }
    }

    /**
     * Request still capture.
     */
    public void requestStillCaptureAsync() {
        requestStillCaptureAsync(new StillCaptureCallbackImpl());
    }

    private class StillCaptureCallbackImpl implements StillCaptureCallback {
        @Override
        public void onShutterDone(int requestId) {
            OverlayViewFinderController.getInstance().getCurrentState().onShutterDone();
        }

        @Override
        public void onCaptureDone(int requestId, byte[] data) {
            OverlayViewFinderController.getInstance().getCurrentState().onStillCaptureDone(data);
        }
    }

    @Override
    public int requestStillCaptureAsync(StillCaptureCallback stillCaptureCallback) {
        Runnable stillCaptureTask = new StillCaptureTask(stillCaptureCallback);
        mCameraHandler.post(stillCaptureTask);

        return 0;
    }

    private class StillCaptureTask implements Runnable {
        private StillCaptureCallback mStillCaptureCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param stillCaptureCallback
         */
        public StillCaptureTask(StillCaptureCallback stillCaptureCallback) {
            mStillCaptureCallback = stillCaptureCallback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : E");


/*

            if (mCamera != null) {
                final CountDownLatch latch = new CountDownLatch(1);
                PictureCallbackImpl pictureCallbackImpl = new PictureCallbackImpl(latch);

                // Parameters.
                Camera.Parameters params = mCamera.getParameters();
                // Orientation.
                Camera.CameraInfo camInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(mCameraId, camInfo);
                final int orientation = (mOrientationDegree + 45) / 90 * 90;
                int rotation = 0;
                switch (camInfo.facing) {
                    case Camera.CameraInfo.CAMERA_FACING_BACK:
                        rotation = (camInfo.orientation + orientation) % 360;
                        break;

                    case Camera.CameraInfo.CAMERA_FACING_FRONT:
                        rotation = (camInfo.orientation - orientation + 360) % 360;
                        break;

                    default:
                        // Unexpected facing.
                        throw new IllegalArgumentException("Unexpected facing.");
                }
                params.setRotation(rotation);
                mCamera.setParameters(params);

                // Do capture.
                mCamera.takePicture(new ShutterCallbackImpl(), null, pictureCallbackImpl);

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new UnsupportedOperationException("Why thread is interrupted ?");
                }

                // Restart preview.
                mCamera.startPreview();

                // Reset lock state.
                mCancelScanTask.run();

                // Notify to controller.
                mStillCaptureCallback.onCaptureDone(0, pictureCallbackImpl.getJpegBuffer());

                // Request store.
                mStorageController.storePicture(pictureCallbackImpl.getJpegBuffer());
            } else {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.");
            }


*/

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : X");
        }

        private class ShutterCallbackImpl implements Camera.ShutterCallback {
            @Override
            public void onShutter() {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutter()");

                // Notify to controller.
                mStillCaptureCallback.onShutterDone(0);
            }
        }

        private class PictureCallbackImpl implements Camera.PictureCallback {
            private final CountDownLatch mLatch;
            private byte[] mJpegBuffer = null;

            /**
             * CONSTRUCTOR.
             *
              * @param latch
             */
            public PictureCallbackImpl(CountDownLatch latch) {
                mLatch = latch;
            }

            @Override
            public void onPictureTaken(byte[] jpegBuffer, Camera camera) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onPictureTaken()");

                mJpegBuffer = jpegBuffer;

                mLatch.countDown();
            }

            public byte[] getJpegBuffer() {
                return mJpegBuffer;
            }
        }
    }



    ////// INTERNAL CALLBACKAS ////////////////////////////////////////////////////////////////////

    private class CameraAvailabilityCallback extends CameraManager.AvailabilityCallback {
        public final String TAG = "CameraAvailabilityCallback";

        @Override
        public void onCameraAvailable(String cameraId) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraAvailable() : ID=" + cameraId);



        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraUnavailable() : ID=" + cameraId);



        }
    }

    private class CameraStateCallback extends CameraDevice.StateCallback {
        public final String TAG = "CameraStateCallback";

        @Override
        public void onOpened(CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOpened()");

            mClientCallbackHandler.post(new NotifyOpenCallback(true));

            // Cache instance.
            mCamDevice = camera;
        }

        @Override
        public void onClosed(CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClosed()");
            super.onClosed(camera);

            mClientCallbackHandler.post(new NotifyCloseCallback(true));
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onDisconnected()");

            mClientCallbackHandler.post(new NotifyOpenCallback(false));
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onError()");

            mClientCallbackHandler.post(new NotifyOpenCallback(false));
        }
    }

    private class CaptureSessionStateCallback extends CameraCaptureSession.StateCallback {
        public final String TAG = "CaptureSessionStateCallback";

        @Override
        public void onActive(CameraCaptureSession session) {
            super.onActive(session);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onActive()");



        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            super.onClosed(session);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClosed()");

            // Stop EVF.
            stopEvfStream();

            // Release.
            mCamSession = null;
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigured()");

            // Cache.
            mCamSession = session;

            // Notify.
            mClientCallbackHandler.post(new NotifyBindSurfaceCallback(true));

            // Start EVF.
            startEvfStream();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigureFailed()");

            // Cache.
            mCamSession = null;

            // Notify.
            mClientCallbackHandler.post(new NotifyBindSurfaceCallback(false));
        }

        @Override
        public void onReady(CameraCaptureSession session) {
            super.onReady(session);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReady()");



        }

        @Override
        public void onSurfacePrepared(CameraCaptureSession session, Surface surface) {
            super.onSurfacePrepared(session, surface);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfacePrepared()");



        }
    }

    private class CaptureCallback extends CameraCaptureSession.CaptureCallback {
        public final String TAG = "CaptureCallback";

        @Override
        public void onCaptureCompleted(
                CameraCaptureSession session,
                CaptureRequest request,
                TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureCompleted()");

            Rect cropRegion = result.get(CaptureResult.SCALER_CROP_REGION);

            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "  CropRegion = " + cropRegion.toShortString());
        }

        @Override
        public void onCaptureFailed(
                CameraCaptureSession session,
                CaptureRequest request,
                CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureFailed()");



        }

        @Override
        public void onCaptureProgressed(
                CameraCaptureSession session,
                CaptureRequest request,
                CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureProgressed()");



        }

        @Override
        public void onCaptureSequenceCompleted(
                CameraCaptureSession session,
                int sequenceId,
                long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureSequenceCompleted()");



        }

        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureSequenceAborted()");



        }

        @Override
        public void onCaptureStarted(
                CameraCaptureSession session,
                CaptureRequest request,
                long timestamp,
                long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureStarted()");



        }
    }





    ////// CLIENT CALLBACKAS //////////////////////////////////////////////////////////////////////

    private class NotifyOpenCallback implements Runnable {
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param isSuccess
         */
        public NotifyOpenCallback(boolean isSuccess) {
            mIsSuccess = isSuccess;
        }

        @Override
        public void run (){
            if (mClientOpenCallback != null) {
                mClientOpenCallback.onOpened(mIsSuccess);
            }

            // Callback only once.
            mClientOpenCallback = null;
        }
    }

    private class NotifyCloseCallback implements Runnable {
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param isSuccess
         */
        public NotifyCloseCallback(boolean isSuccess) {
            mIsSuccess = isSuccess;
        }

        @Override
        public void run (){
            if (mClientCloseCallback != null) {
                mClientCloseCallback.onClosed(mIsSuccess);
            }

            // Callback only once.
            mClientCloseCallback = null;
        }
    }

    private class NotifyBindSurfaceCallback implements Runnable {
        private final boolean mIsSuccess;

        /**
         * CONSTRUCTOR.
         *
         * @param isSuccess
         */
        public NotifyBindSurfaceCallback(boolean isSuccess) {
            mIsSuccess = isSuccess;
        }

        @Override
        public void run (){
            if (mClientBindSurfaceCallback != null) {
                mClientBindSurfaceCallback.onSurfaceBound(mIsSuccess);
            }

            // Callback only once.
            mClientBindSurfaceCallback = null;
        }
    }





}
