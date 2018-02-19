package com.fezrestia.android.viewfinderanywhere.device;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;

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
@SuppressWarnings("deprecation")
public class Camera1Device implements CameraPlatformInterface {
    // Log tag.
    private static final String TAG = "Camera1Device";

    // Master context.
    private Context mContext = null;

    // UI thread handler.
    private Handler mUiWorker = null;

    // Target device ID.
    private final int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    // Camera API 1.0 related.
    private Camera mCamera = null;
    private float mEvfAspectWH = 1.0f;
    private ScanTask mScanTask = null;
    private PlatformDependencyResolver.Size mPreviewSize = null;
    private static final int JPEG_QUALITY = 95;

    // Snapshot request ID.
    private int mRequestId = 0;

    // Back worker.
    private ExecutorService mBackWorker = null;
    // Back worker thread factory.
    private static final BackWorkerThreadFactoryImpl mBackWorkerThreadFactoryImpl
            = new BackWorkerThreadFactoryImpl();
    private static class BackWorkerThreadFactoryImpl implements ThreadFactory {
        @Override
        public Thread newThread(@NonNull Runnable r) {
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
     * CONSTRUCTOR.
     *
     * @param context Master context.
     */
    public Camera1Device(Context context) {
        mContext = context;
        mUiWorker = ViewFinderAnywhereApplication.getUiThreadHandler();

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
        mBackWorker = null;
    }

    @Override
    public void openAsync(float evfAspectWH, OpenCallback openCallback) {
        mEvfAspectWH = evfAspectWH;
        Runnable task = new OpenTask(openCallback);
        mBackWorker.execute(task);
    }

    private class OpenTask implements Runnable {
        private final OpenCallback mOpenCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param openCallback Callback.
         */
        OpenTask(OpenCallback openCallback) {
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

                    Set<PlatformDependencyResolver.Size> supportedSizes = new HashSet<>();

                    // Preview size.
                    supportedSizes.clear();
                    for (Camera.Size eachSize : params.getSupportedPreviewSizes()) {
                        PlatformDependencyResolver.Size supported
                                = new PlatformDependencyResolver.Size(
                                        eachSize.width,
                                        eachSize.height);
                        supportedSizes.add(supported);
                    }
                    mPreviewSize = PlatformDependencyResolver.getOptimalPreviewSizeForStill(
                            mEvfAspectWH,
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
                                    mEvfAspectWH,
                                    supportedSizes);

                    // Parameters.
                    params.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
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

                    // Request ID.
                    mRequestId = 0;

                    // Orientation.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Create OrientationListenerImpl : E");
                    mOrientationEventListenerImpl = new OrientationEventListenerImpl(
                            mContext,
                            SensorManager.SENSOR_DELAY_NORMAL);
                    mOrientationEventListenerImpl.enable();
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Create OrientationListenerImpl : X");

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
         * @param closeCallback Callback.
         */
        CloseTask(CloseCallback closeCallback) {
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

            // Notify.
            mCloseCallback.onClosed(true);

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CloseTask.run() : X");
        }
    }

    @Override
    public void bindPreviewSurfaceAsync(
            TextureView textureView,
            BindSurfaceCallback bindSurfaceCallback) {

        int previewWidth = mPreviewSize.getWidth();
        int previewHeight = mPreviewSize.getHeight();
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

        Runnable uiTask = new SetTextureViewTransformTask(textureView, matrix);
        mUiWorker.post(uiTask);

        Runnable task = new BindSurfaceTask(textureView.getSurfaceTexture(), bindSurfaceCallback);
        mBackWorker.execute(task);
    }

    private class SetTextureViewTransformTask implements Runnable {
        private TextureView mTexView;
        private Matrix mMatrix;

        /**
         * CONSTRUCTOR.
         *
         * @param texView View finder texture.
         * @param matrix Transform matrix from preview frame to finder.
         */
        SetTextureViewTransformTask(TextureView texView, Matrix matrix) {
            mTexView = texView;
            mMatrix = matrix;
        }

        @Override
        public void run() {
            mTexView.setTransform(mMatrix);
        }
    }

    private class BindSurfaceTask implements Runnable {
        private final SurfaceTexture mSurface;
        private final BindSurfaceCallback mBindSurfaceCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param surface Finder surface.
         * @param bindSurfaceCallback Callback.
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
                    if (winMng == null) throw new RuntimeException("WindowManager is null.");
                    final int rotation = winMng.getDefaultDisplay().getRotation();
                    int degrees;
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

    @Override
    public void requestScanAsync(ScanCallback scanCallback) {
        mScanTask = new ScanTask(scanCallback);
        mBackWorker.execute(mScanTask);
    }

    private class ScanTask implements Runnable {
        private CountDownLatch mLatch = null;
        private boolean isCanceled = false;
        private ScanCallback mScanCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param scanCallback Callback.
         */
        ScanTask(ScanCallback scanCallback) {
            mScanCallback = scanCallback;
        }

        void cancel() {
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
         * @param latch Latch waiting for auto focus done.
         */
        FocusCallbackImpl(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onAutoFocus(boolean isSuccess, Camera camera) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onAutoFocus() : [isSuccess=" + isSuccess + "]");

            mIsSuccess = isSuccess;
            mLatch.countDown();
        }

        boolean isSuccess() {
            return mIsSuccess;
        }
    }

    @Override
    public void requestCancelScanAsync(CancelScanCallback cancelScanCallback) {
        // Cancel scan task.
        mScanTask.cancel();

        mBackWorker.execute(new CancelScanTask(cancelScanCallback));
    }

    private class CancelScanTask implements Runnable {
        private CancelScanCallback mCancelScanCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param cancelScanCallback Callback.
         */
        CancelScanTask(CancelScanCallback cancelScanCallback) {
            mCancelScanCallback = cancelScanCallback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : E");

            if (mCamera != null) {
                doCancelScan();
            }

            mCancelScanCallback.onCancelScanDone();

            if (Log.IS_DEBUG) Log.logDebug(TAG, "CancelScanTask.run() : X");
        }
    }

    private void doCancelScan() {
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

    @Override
    public int requestStillCaptureAsync(StillCaptureCallback stillCaptureCallback) {
        ++mRequestId;
        Runnable stillCaptureTask = new StillCaptureTask(mRequestId, stillCaptureCallback);
        mBackWorker.execute(stillCaptureTask);
        return mRequestId;
    }

    private class StillCaptureTask implements Runnable {
        private final int mRequestId;
        private StillCaptureCallback mStillCaptureCallback;

        /**
         * CONSTRUCTOR.
         *
         * @param requestId Capture request ID.
         * @param stillCaptureCallback Callback.
         */
        StillCaptureTask(int requestId, StillCaptureCallback stillCaptureCallback) {
            mRequestId = requestId;
            mStillCaptureCallback = stillCaptureCallback;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StillCaptureTask.run() : E");

            if (mCamera != null) {
                // Parameters.
                Camera.Parameters params = mCamera.getParameters();
                // Orientation.
                Camera.CameraInfo camInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(mCameraId, camInfo);
                final int orientation = (mOrientationDegree + 45) / 90 * 90;
                int rotationDeg;
                switch (camInfo.facing) {
                    case Camera.CameraInfo.CAMERA_FACING_BACK:
                        rotationDeg = (camInfo.orientation + orientation) % 360;
                        break;

                    case Camera.CameraInfo.CAMERA_FACING_FRONT:
                        rotationDeg = (camInfo.orientation - orientation + 360) % 360;
                        break;

                    default:
                        // Unexpected facing.
                        throw new IllegalArgumentException("Unexpected facing.");
                }
                params.setRotation(rotationDeg);
                mCamera.setParameters(params);

                // Do capture.
                final CountDownLatch latch = new CountDownLatch(1);
                PictureCallbackImpl pictCallback = new PictureCallbackImpl(latch);
                mCamera.takePicture(
                        null,
//                        new ShutterCallbackImpl(),
                        null,
                        pictCallback);

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new UnsupportedOperationException("Why thread is interrupted ?");
                }

                // Restart preview.
                mCamera.startPreview();

                // Reset lock state.
                doCancelScan();

                // Process JPEG and notify.
                HandleJpegTask task = new HandleJpegTask(
                        pictCallback.getJpegBuffer(),
                        rotationDeg,
                        mEvfAspectWH,
                        JPEG_QUALITY);
                mBackWorker.execute(task);
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
                mStillCaptureCallback.onShutterDone(mRequestId);
            }
        }

        private class PictureCallbackImpl implements Camera.PictureCallback {
            private final CountDownLatch mLatch;
            private byte[] mJpegBuffer = null;

            /**
             * CONSTRUCTOR.
             *
             * @param latch Latch waiting for picture done.
             */
            PictureCallbackImpl(CountDownLatch latch) {
                mLatch = latch;
            }

            @Override
            public void onPictureTaken(byte[] jpegBuffer, Camera camera) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "onPictureTaken()");

                // Notify to controller.
                mStillCaptureCallback.onShutterDone(mRequestId);

                mJpegBuffer = jpegBuffer;

                mLatch.countDown();

                // Notify to controller.
                mStillCaptureCallback.onCaptureDone(mRequestId);
            }

            byte[] getJpegBuffer() {
                return mJpegBuffer;
            }
        }

        private class HandleJpegTask implements Runnable {
            private final byte[] mSrcJpeg;
            private final int mRotationDeg;
            private final float mCropAspectWH;
            private final int mJpegQuality;
            private byte[] mResultJpeg = null;

            /**
             * CONSTRUCTOR.
             *
             * @param srcJpeg Source JPEG frame buffer.
             * @param rotationDeg Frame rotation degree.
             * @param cropAspectWH Result aspect ratio.
             * @param jpegQuality JPEG quality.
             */
            HandleJpegTask(
                    byte[] srcJpeg,
                    int rotationDeg,
                    float cropAspectWH,
                    int jpegQuality) {
                mSrcJpeg = srcJpeg;
                mRotationDeg = rotationDeg;
                mCropAspectWH = cropAspectWH;
                mJpegQuality = jpegQuality;
            }

            @Override
            public void run() {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "HandleJpegTask.run() : E");

                mResultJpeg = PDR2.doCropRotJpeg(
                        mSrcJpeg,
                        mRotationDeg,
                        mCropAspectWH,
                        mJpegQuality);

                mUiWorker.post(new NotifyResultJpegTask());

                if (Log.IS_DEBUG) Log.logDebug(TAG, "HandleJpegTask.run() : X");
            }

            private class NotifyResultJpegTask implements Runnable {
                @Override
                public void run (){
                    // Notify to controller.
                    mStillCaptureCallback.onPhotoStoreReady(mRequestId, mResultJpeg);
                }
            }
        }
    }
}
