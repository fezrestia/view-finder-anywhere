package com.fezrestia.android.viewfinderanywhere.device;

import android.graphics.SurfaceTexture;
import android.view.Surface;
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
     * @param evfAspectWH must be always larger than or equals to 1.0.
     * @param openCallback
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
         * @param isSuccess
         */
        void onOpened(boolean isSuccess);
    }

    /**
     * Close camera in asynchronized.
     *
     * @param closeCallback
     */
    void closeAsync(CloseCallback closeCallback);

    /**
     * Camera close callback.
     */
    interface CloseCallback {
        /**
         * Camera close is done.
         *
         * @param isSuccess
         */
        void onClosed(boolean isSuccess);
    }

    /**
     * Bind TextureView as preview stream.
     *
     * @param textureView
     * @param bindSurfaceCallback
     */
    void bindPreviewSurfaceAsync(TextureView textureView, BindSurfaceCallback bindSurfaceCallback);

    /**
     * Callback for camera and surface binding.
     */
    interface BindSurfaceCallback {
        /**
         * Surface is bound to camera.
         *
         * @param isSuccess
         */
        void onSurfaceBound(boolean isSuccess);
    }

    /**
     * Request scan.
     *
     * @param scanCallback
     */
    void requestScanAsync(ScanCallback scanCallback);

    /**
     * Scan done callback.
     */
    interface ScanCallback {
        /**
         * Request scan is done.
         *
         * @param isSuccess
         */
        void onScanDone(boolean isSuccess);
    }

    /**
     * Request cancel scan.
     *
     * @param cancelScanCallback
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
     * @param stillCaptureCallback
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
         * @param requestId
         */
        void onShutterDone(int requestId);

        /**
         * Capturing sequence is done. After this callback, client app can requets next capture.
         *
         * @param requestId
         * @param picture
         */
        void onCaptureDone(int requestId);

        /**
         * Still capture photo data is ready to store.
         *
         * @param requestId
         * @param data
         */
        void onPhotoStoreReady(int requestId, byte[] data);
    }
}
