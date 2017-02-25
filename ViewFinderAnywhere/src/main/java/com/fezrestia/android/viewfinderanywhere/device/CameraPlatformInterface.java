package com.fezrestia.android.viewfinderanywhere.device;

import android.view.TextureView;

/**
 * Abstracted interface for all of the camera function enabler.
 */
public interface CameraPlatformInterface {
    /**
     * Release all of the binders, references, and resources.
     * This may block calling thread much time.
     */
    void release();

    /**
     * Open camera in asynchronized.
     *
     * @param evfAspectWH Aspect ratio must be always larger than or equals to 1.0.
     * @param openCallback Callback.
     */
    //TODO: consider set aspect timing.
    void openAsync(float evfAspectWH, OpenCallback openCallback);

    /**
     * Camera open callback.
     */
    interface OpenCallback {
        /**
         * Camera open is done.
         *
         * @param isSuccess Open is success or not.
         */
        void onOpened(boolean isSuccess);
    }

    /**
     * Close camera in asynchronized.
     *
     * @param closeCallback Callback.
     */
    void closeAsync(CloseCallback closeCallback);

    /**
     * Camera close callback.
     */
    interface CloseCallback {
        /**
         * Camera close is done.
         *
         * @param isSuccess Close is success or not.
         */
        void onClosed(boolean isSuccess);
    }

    /**
     * Bind TextureView as preview stream.
     *
     * @param textureView Finder texture.
     * @param bindSurfaceCallback Callback.
     */
    void bindPreviewSurfaceAsync(TextureView textureView, BindSurfaceCallback bindSurfaceCallback);

    /**
     * Callback for camera and surface binding.
     */
    interface BindSurfaceCallback {
        /**
         * Surface is bound to camera.
         *
         * @param isSuccess Bind surface is success or not.
         */
        void onSurfaceBound(boolean isSuccess);
    }

    /**
     * Request scan.
     *
     * @param scanCallback Callback.
     */
    void requestScanAsync(ScanCallback scanCallback);

    /**
     * Scan done callback.
     */
    interface ScanCallback {
        /**
         * Request scan is done.
         *
         * @param isSuccess Scan is success or not.
         */
        void onScanDone(boolean isSuccess);
    }

    /**
     * Request cancel scan.
     *
     * @param cancelScanCallback Callback.
     */
    void requestCancelScanAsync(CancelScanCallback cancelScanCallback);

    /**
     * Cancel scan done callback.
     */
    interface CancelScanCallback {
        /**
         * Cancel scan is done.
         */
        void onCancelScanDone();
    }

    /**
     * Request still capture.
     *
     * @param stillCaptureCallback Callback.
     * @return Request ID
     */
    int requestStillCaptureAsync(StillCaptureCallback stillCaptureCallback);

    /**
     * Still capture callback.
     */
    interface StillCaptureCallback {
        /**
         * Exposure is done.
         *
         * @param requestId Capture request ID.
         */
        void onShutterDone(int requestId);

        /**
         * Capturing sequence is done. After this callback, client app can requets next capture.
         *
         * @param requestId Capture request ID.
         */
        void onCaptureDone(int requestId);

        /**
         * Still capture photo data is ready to store.
         *
         * @param requestId Capture request ID.
         * @param data JPEG frame data.
         */
        void onPhotoStoreReady(int requestId, byte[] data);
    }
}
