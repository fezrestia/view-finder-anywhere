package com.fezrestia.android.viewfinderanywhere.device

import android.view.TextureView

/**
 * Interface for all of the camera function enabler.
 */
interface CameraPlatformInterface {
    /**
     * Prepare all of the binders, references, and resources.
     * This may block calling thread much time.
     */
    fun prepare()

    /**
     * Release all of the binders, references, and resources.
     * This may block calling thread much time.
     */
    fun release()

    /**
     * Open camera in asynchronized.
     *
     * @param evfAspectWH Aspect ratio must be always larger than or equals to 1.0.
     * @param openCallback Callback.
     */
    //TODO: consider set aspect timing.
    fun openAsync(evfAspectWH: Float, openCallback: OpenCallback)

    /**
     * Camera open callback.
     */
    interface OpenCallback {
        /**
         * Camera open is done.
         *
         * @param isSuccess Open is success or not.
         */
        fun onOpened(isSuccess: Boolean)
    }

    /**
     * Close camera in asynchronized.
     *
     * @param closeCallback Callback.
     */
    fun closeAsync(closeCallback: CloseCallback)

    /**
     * Camera close callback.
     */
    interface CloseCallback {
        /**
         * Camera close is done.
         *
         * @param isSuccess Close is success or not.
         */
        fun onClosed(isSuccess: Boolean)
    }

    /**
     * Bind TextureView as preview stream.
     *
     * @param textureView Finder texture.
     * @param bindSurfaceCallback Callback.
     */
    fun bindPreviewSurfaceAsync(textureView: TextureView, bindSurfaceCallback: BindSurfaceCallback)

    /**
     * Callback for camera and surface binding.
     */
    interface BindSurfaceCallback {
        /**
         * Surface is bound to camera.
         *
         * @param isSuccess Bind surface is success or not.
         */
        fun onSurfaceBound(isSuccess: Boolean)
    }

    /**
     * Request scan.
     *
     * @param scanCallback Callback.
     */
    fun requestScanAsync(scanCallback: ScanCallback)

    /**
     * Scan done callback.
     */
    interface ScanCallback {
        /**
         * Request scan is done.
         *
         * @param isSuccess Scan is success or not.
         */
        fun onScanDone(isSuccess: Boolean)
    }

    /**
     * Request cancel scan.
     *
     * @param cancelScanCallback Callback.
     */
    fun requestCancelScanAsync(cancelScanCallback: CancelScanCallback)

    /**
     * Cancel scan done callback.
     */
    interface CancelScanCallback {
        /**
         * Cancel scan is done.
         */
        fun onCancelScanDone()
    }

    /**
     * Request still capture.
     *
     * @param stillCaptureCallback Callback.
     * @return Request ID
     */
    fun requestStillCaptureAsync(stillCaptureCallback: StillCaptureCallback): Int

    /**
     * Still capture callback.
     */
    interface StillCaptureCallback {
        /**
         * Exposure is done.
         *
         * @param requestId Capture request ID.
         */
        fun onShutterDone(requestId: Int)

        /**
         * Capturing sequence is done. After this callback, client app can requests next capture.
         *
         * @param requestId Capture request ID.
         */
        fun onCaptureDone(requestId: Int)

        /**
         * Still capture photo data is ready to store.
         *
         * @param requestId Capture request ID.
         * @param data JPEG frame data.
         */
        fun onPhotoStoreReady(requestId: Int, data: ByteArray)
    }
}
