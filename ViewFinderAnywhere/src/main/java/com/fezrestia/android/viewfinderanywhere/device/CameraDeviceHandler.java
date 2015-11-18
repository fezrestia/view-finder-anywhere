package com.fezrestia.android.viewfinderanywhere.device;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.MediaActionSound;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.PowerManager;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;
import com.fezrestia.android.viewfinderanywhere.storage.StorageController;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class CameraDeviceHandler {
    // Log tag.
    private static final String TAG = CameraDeviceHandler.class.getSimpleName();

    // Master context.
    private Context mContext = null;

    // UI thread handler.
    private Handler mUiWorker = null;

    // Target device ID.
    private final int mCameraId;

    // Camera API 1.0 instance.
    private Camera mCamera = null;

    // Storage.
    private StorageController mStorageController = null;

    // Sounds.
    private MediaActionSound mScanSuccessSound = null;

    // Wake lock.
    private PowerManager.WakeLock mWakeLock = null;

    // Back worker.
    private ExecutorService mBackWorker = null;
    // Back worker thread factory.
    private static final BackWorkerThreadFactoryImpl mBackWorkerThreadFactoryImpl
            = new BackWorkerThreadFactoryImpl();
    private static class BackWorkerThreadFactoryImpl implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, TAG);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        }
    }

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
     * @param uiWorker
     * @param cameraId
     */
    public CameraDeviceHandler(Context context, Handler uiWorker, int cameraId) {
        mContext = context;
        mUiWorker = uiWorker;
        mCameraId = cameraId;

        generateBackWorker();
    }

    /**
     * Wait for finish of all of the tasks.
     */
    public void flush() {
        shutdownBackWorker();
        generateBackWorker();
    }

    private void generateBackWorker() {
        mBackWorker = Executors.newSingleThreadExecutor(mBackWorkerThreadFactoryImpl);
    }

    private void shutdownBackWorker() {
        if (mBackWorker != null) {
            mBackWorker.shutdown();
            try {
                mBackWorker.awaitTermination(3000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mBackWorker = null;
        }
    }

    /**
     * Destruct this instance. Do not use this instance again after this function is called.
     */
    public void destructor() {
        shutdownBackWorker();

        mContext = null;
        mUiWorker = null;
        mBackWorker = null;
    }

    /**
     * Open camera in synchronized.
     */
    public void openAsync(float aspectWH) {
        Runnable task = new OpenTask(aspectWH);
        mBackWorker.execute(task);
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

            if (mCamera == null) {
                // Open.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.open() : E");
                try {
                    mCamera = Camera.open(mCameraId);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    mCamera = null;
                }
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.open() : X");

                if (mCamera == null) {
                    // Failed to open.
                    // TODO:Re-Try ?

                    // Notify.
                    OverlayViewFinderController.getInstance().getCurrentState().onCameraBusy();
                } else {
                    // Parameters.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.getParameters() : E");
                    Camera.Parameters params = mCamera.getParameters();
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.getParameters() : X");

                    Set<PlatformDependencyResolver.Size> supportedSizes
                            = new HashSet<PlatformDependencyResolver.Size>();

                    // Preview size.
                    supportedSizes.clear();
                    for (Camera.Size eachSize : params.getSupportedPreviewSizes()) {
                        PlatformDependencyResolver.Size supported
                                = new PlatformDependencyResolver.Size(
                                        eachSize.width,
                                        eachSize.height);
                        supportedSizes.add(supported);
                    }
                    PlatformDependencyResolver.Size previewSize
                            = PlatformDependencyResolver.getOptimalPreviewSizeForStill(
                                    mViewFinderAspectRatioWH,
                                    supportedSizes);

                    // Picture size.
                    supportedSizes.clear();
                    for (Camera.Size eachSize : params.getSupportedPictureSizes()) {
                        PlatformDependencyResolver.Size supported
                                = new PlatformDependencyResolver.Size(
                                        eachSize.width,
                                        eachSize.height);
                        supportedSizes.add(supported);
                    }
                    PlatformDependencyResolver.Size pictureSize
                            = PlatformDependencyResolver.getOptimalPictureSize(
                                    mViewFinderAspectRatioWH,
                                    supportedSizes);

                    // Parameters.
                    params.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                    params.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
                    for (String eachFocusMode : params.getSupportedFocusModes()) {
                        if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                                .equals(eachFocusMode)) {
                            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                            break;
                        }
                    }

                    // Set.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.setParameters() : E");
                    doSetParameters(mCamera, params);
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.setParameters() : X");

                    // Start preview.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.startPreview() : E");
                    mCamera.startPreview();
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera.startPreview() : X");

                    // Orientation.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Create OrientationListenerImpl : E");
                    mOrientationEventListenerImpl = new OrientationEventListenerImpl(
                            mContext,
                            SensorManager.SENSOR_DELAY_NORMAL);
                    mOrientationEventListenerImpl.enable();
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Create OrientationListenerImpl : X");

                    // Sound.
                    if (mScanSuccessSound == null) {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "Create MediaActionSound : E");
                        mScanSuccessSound = new MediaActionSound();
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "Create MediaActionSound : X");
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "MediaActionSound.load() : E");
                        mScanSuccessSound.load(MediaActionSound.FOCUS_COMPLETE);
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "MediaActionSound.load() : X");
                    }

                    // Storage.
                    mStorageController = new StorageController(mContext, mUiWorker);

                    // Wake lock.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Acquire WakeLock : E");
                    PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                    mWakeLock = pm.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            mContext.getPackageName());
                    mWakeLock.acquire();
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Acquire WakeLock : X");

                    // Notify.
                    OverlayViewFinderController.getInstance().getCurrentState().onCameraReady();
                }
            } else {
                // Notify. Already opened.
                OverlayViewFinderController.getInstance().getCurrentState().onCameraReady();
            }
            if (Log.IS_DEBUG) Log.logDebug(TAG, "OpenTask.run() : X");
        }
    }

    private void doSetParameters(Camera camera, Camera.Parameters params) {
        if (Log.IS_DEBUG) {
            String[] splitParams = params.flatten().split(";");
            android.util.Log.e("TraceLog", "############ CameraParameters DEBUG");
            for (String str : splitParams) {
                android.util.Log.e("TraceLog", "###### CameraParameters : " + str);
            }
            android.util.Log.e("TraceLog", "############");
        }

        try {
            camera.setParameters(params);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Release camera in synchronized.
     */
    public void releaseAsync() {
//        Runnable removeSurfaceTask = new SetSurfaceTask(null);
//        mBackWorker.execute(removeSurfaceTask);
        Runnable releaseTask = new ReleaseTask();
        mBackWorker.execute(releaseTask);
    }

    private class ReleaseTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ReleaseTask.run() : E");

            // Camera.
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

            // Orientation.
            if (mOrientationEventListenerImpl != null) {
                mOrientationEventListenerImpl.disable();
                mOrientationEventListenerImpl = null;
            }

            // Sounds.
            if (mScanSuccessSound != null) {
                mScanSuccessSound.release();
                mScanSuccessSound = null;
            }

            // Storage.
            if (mStorageController != null) {
                mStorageController.release();
                mStorageController = null;
            }

            // Wake lock/
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "ReleaseTask.run() : X");
        }
    }

    /**
     * Set SurfaceTexture for preview stream.
     *
     * @param surface
     */
    public void setSurfaceTextureAsync(SurfaceTexture surface) {
        Runnable task = new SetSurfaceTask(surface);
        mBackWorker.execute(task);
    }

    private class SetSurfaceTask implements Runnable {
        private final SurfaceTexture mSurface;

        /**
         * CONSTRUCTOR.
         *
         * @param surface
         */
        SetSurfaceTask(SurfaceTexture surface) {
            mSurface = surface;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "SetSurfaceTask.run() : E");

            if (mCamera != null) {
                if (mSurface != null) {
                    // Orientation.
                    Camera.CameraInfo camInfo = new Camera.CameraInfo();
                    Camera.getCameraInfo(mCameraId, camInfo);
                    WindowManager winMng = (WindowManager)
                            mContext.getSystemService(Context.WINDOW_SERVICE);
                    final int rotation = winMng.getDefaultDisplay().getRotation();
                    int degrees = 0;
                    switch (rotation) {
                        case Surface.ROTATION_0:
                            degrees = 0;
                            break;

                        case Surface.ROTATION_90:
                            degrees = 90;
                            break;

                        case Surface.ROTATION_180:
                            degrees = 180;
                            break;

                        case Surface.ROTATION_270:
                            degrees = 270;
                            break;

                        default:
                            // Unexpected rotation.
                            throw new IllegalStateException("Unexpected rotation.");
                    }
                    int resultRotation;
                    switch (camInfo.facing) {
                        case Camera.CameraInfo.CAMERA_FACING_BACK:
                            resultRotation = (camInfo.orientation - degrees + 360) % 360;
                            break;

                        case Camera.CameraInfo.CAMERA_FACING_FRONT:
                            resultRotation = (camInfo.orientation + degrees) % 360;
                            resultRotation = (360 - resultRotation) % 360;
                            break;

                        default:
                            // Unexpected facing.
                            throw new IllegalStateException("Unexpected facing.");
                    }
                    mCamera.setDisplayOrientation(resultRotation);
                }

                try {
                    mCamera.setPreviewTexture(mSurface);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.");
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "SetSurfaceTask.run() : X");
        }
    }

    /**
     * Request scan.
     */
    public void requestScanAsync() {
        mBackWorker.execute(mScanTask);
    }

    private final ScanTask mScanTask = new ScanTask();
    private class ScanTask implements Runnable {
        private CountDownLatch mLatch = null;
        private boolean isCanceled = false;

        public void cancel() {
            isCanceled = true;
            if (mLatch != null) {
                mLatch.countDown();
            }
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ScanTask.run() : E");

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

                    // Notify to controller.
                    OverlayViewFinderController.getInstance().getCurrentState()
                            .onScanDone(focusCallbackImpl.isSuccess());
                }
            } else {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.");
            }

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
     * Request cancel scan.
     */
    public void requestCancelScanAsync() {
        // Cancel scan task.
        mScanTask.cancel();

        mBackWorker.execute(mCancelScanTask);
    }

    private final CancelScanTask mCancelScanTask = new CancelScanTask();
    private class CancelScanTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : E");

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

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : X");
        }
    }

    /**
     * Request still capture.
     */
    public void requestStillCaptureAsync() {
        Runnable stillCaptureTask = new StillCaptureTask();
        mBackWorker.execute(stillCaptureTask);
    }

    private class StillCaptureTask implements Runnable {
        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : E");

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
                OverlayViewFinderController.getInstance().getCurrentState()
                        .onStillCaptureDone(pictureCallbackImpl.getJpegBuffer());

                // Request store.
                mStorageController.storePicture(pictureCallbackImpl.getJpegBuffer());
            } else {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.");
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : X");
        }
    }

    private class ShutterCallbackImpl implements Camera.ShutterCallback {
        @Override
        public void onShutter() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onShutter()");

            // Notify to controller.
            OverlayViewFinderController.getInstance().getCurrentState().onShutterDone();
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
