package com.fezrestia.android.viewfinderanywhere.device;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.MediaActionSound;
import android.os.Handler;
import android.os.PowerManager;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import com.fezrestia.android.util.log.Log;
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

/**
 * Camera functions based on Camera API 1.0.
 */
public class Camera1Device implements CameraPlatformInterface {
    // Log tag.
    private static final String TAG = "Camera1Device";

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
    public Camera1Device(Context context, Handler uiWorker, int cameraId) {
        mContext = context;
        mUiWorker = uiWorker;
        mCameraId = cameraId;

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

    @Override
    public void release() {
        shutdownBackWorker();

        mContext = null;
        mUiWorker = null;
        mBackWorker = null;
    }

    /**
     * Open camera.
     *
     * @param evfAspectWH
     */
    public void openAsync(float evfAspectWH) {
        openAsync(evfAspectWH, new OpenCallbackImpl());
    }

    private class OpenCallbackImpl implements OpenCallback {
        @Override
        public void onOpened(boolean isSuccess) {
            if (isSuccess) {
                OverlayViewFinderController.getInstance().getCurrentState().onCameraReady();
            } else {
                OverlayViewFinderController.getInstance().getCurrentState().onCameraBusy();
            }
        }
    }

    @Override
    public void openAsync(float evfAspectWH, OpenCallback openCallback) {
        Runnable task = new OpenTask(evfAspectWH, openCallback);
        mBackWorker.execute(task);
    }

    private class OpenTask implements Runnable {
        private final float mViewFinderAspectRatioWH;
        private final OpenCallback mOpenCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param aspectRatioWH
         * @param openCallback
         */
        public OpenTask(float aspectRatioWH, OpenCallback openCallback) {
            mViewFinderAspectRatioWH = aspectRatioWH;
            mOpenCallback = openCallback;
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
                    mOpenCallback.onOpened(false);
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
                    mOpenCallback.onOpened(true);
                }
            } else {
                // Notify. Already opened.
                mOpenCallback.onOpened(true);
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
     * Close camera.
     */
    public void closeAsync() {
        closeAsync(new CloseCallbackImpl());
    }

    private class CloseCallbackImpl implements CloseCallback {
        @Override
        public void onClosed(boolean isSuccess) {
            // NOP.
        }
    }

    @Override
    public void closeAsync(CloseCallback closeCallback) {
//        Runnable removeSurfaceTask = new SetSurfaceTask(null);
//        mBackWorker.execute(removeSurfaceTask);
        Runnable closeTask = new CloseTask(closeCallback);
        mBackWorker.execute(closeTask);
    }

    private class CloseTask implements Runnable {
        private final CloseCallback mCloseCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param closeCallback
         */
        public CloseTask(CloseCallback closeCallback) {
            mCloseCallback = closeCallback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : E");

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

            // Notify.
            mCloseCallback.onClosed(true);

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : X");
        }
    }

    /**
     * Bind surface as preview stream.
     *
     * @param textureView
     */
    public void bindPreviewSurfaceAsync(TextureView textureView) {
        bindPreviewSurfaceAsync(textureView, new BindSurfaceCallbackImpl());
    }

    private class BindSurfaceCallbackImpl implements BindSurfaceCallback {
        @Override
        public void onSurfaceBound(boolean isSuccess) {
            // NOP.
        }
    }

    @Override
    public void bindPreviewSurfaceAsync(
            TextureView textureView,
            BindSurfaceCallback bindSurfaceCallback) {
        Runnable task = new BindSurfaceTask(textureView.getSurfaceTexture(), bindSurfaceCallback);
        mBackWorker.execute(task);
    }

    private class BindSurfaceTask implements Runnable {
        private final SurfaceTexture mSurface;
        private final BindSurfaceCallback mBindSurfaceCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param surface
         * @param bindSurfaceCallback
         */
        BindSurfaceTask(SurfaceTexture surface, BindSurfaceCallback bindSurfaceCallback) {
            mSurface = surface;
            mBindSurfaceCallback = bindSurfaceCallback;
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

                    mBindSurfaceCallback.onSurfaceBound(false);
                }

                mBindSurfaceCallback.onSurfaceBound(true);
            } else {
                if (Log.IS_DEBUG) Log.logError(TAG, "Error. Camera is already released.");
                mBindSurfaceCallback.onSurfaceBound(false);
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "SetSurfaceTask.run() : X");
        }
    }

    /**
     * Start scan.
     */
    public void requestScanAsync() {
        requestScanAsync(null);
    }

    @Override
    public void requestScanAsync(ScanCallback scanCallback) {
        mBackWorker.execute(mScanTask);
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

        mBackWorker.execute(mCancelScanTask);
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
        mBackWorker.execute(stillCaptureTask);

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
}
