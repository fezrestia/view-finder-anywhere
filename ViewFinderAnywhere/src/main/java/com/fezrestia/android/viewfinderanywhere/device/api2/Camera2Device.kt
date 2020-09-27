@file:Suppress("PropertyName", "PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.device.api2

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaActionSound
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.lib.util.media.ImageProc
import com.fezrestia.android.viewfinderanywhere.device.CameraPlatformInterface
import com.fezrestia.android.viewfinderanywhere.device.codec.MediaCodecPDR
import java.nio.ByteBuffer

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Camera functions based on Camera API 2.0.
 *
 * TODO: Consider facing.
 *
 * @constructor
 *
 * @param context Context Master context.
 * @param clientCallbackHandler Handler All client callbacks will be invoked on it.
 */
class Camera2Device(
        private val context: Context,
        private val clientCallbackHandler: Handler) : CameraPlatformInterface {
    private var delegated: Camera2DeviceDelegated? = null

    override fun prepare() {
        delegated = Camera2DeviceDelegated(context, clientCallbackHandler)
    }

    override fun release() {
        delegated?.release()
        delegated = null
    }

    override fun openAsync(
            evfAspectWH: Float,
            openCallback: CameraPlatformInterface.OpenCallback) {
        val d = delegated ?: throw error()
        d.openAsync(evfAspectWH, openCallback)
    }

    override fun closeAsync(closeCallback: CameraPlatformInterface.CloseCallback) {
        val d = delegated ?: throw error()
        d.closeAsync(closeCallback)
    }

    override fun bindPreviewSurfaceAsync(
            textureView: TextureView,
            bindSurfaceCallback: CameraPlatformInterface.BindSurfaceCallback) {
        val d = delegated ?: throw error()
        d.bindPreviewSurfaceAsync(textureView, bindSurfaceCallback)
    }

    override fun requestScanAsync(scanCallback: CameraPlatformInterface.ScanCallback) {
        val d = delegated ?: throw error()
        d.requestScanAsync(scanCallback)
    }

    override fun requestCancelScanAsync(cancelScanCallback: CameraPlatformInterface.CancelScanCallback) {
        val d = delegated ?: throw error()
        d.requestCancelScanAsync(cancelScanCallback)
    }

    override fun requestStillCaptureAsync(stillCaptureCallback: CameraPlatformInterface.StillCaptureCallback): Int {
        val d = delegated ?: throw error()
        return d.requestStillCaptureAsync(stillCaptureCallback)
    }

    override fun requestStartRecAsync(recFileFullPath: String, recCallback: CameraPlatformInterface.RecCallback) {
        val d = delegated ?: throw error()
        return d.requestStartRecAsync(recFileFullPath, recCallback)
    }

    override fun requestStopRecAsync() {
        val d = delegated ?: throw error()
        return d.requestStopRecAsync()
    }

    private fun error(): RuntimeException = RuntimeException("delegated is null.")
}

/**
 * Camera2Device implementation.
 */
class Camera2DeviceDelegated(
        private val context: Context,
        private val clientCallbackHandler: Handler) : CameraPlatformInterface {
    private val TAG = "Camera2Device"

    private val SHUTDOWN_AWAIT_TIMEOUT_MILLIS = 5000L
    private val JPEG_QUALITY = 95

    // Camera API 2.0 related.
    private var camMng: CameraManager
    private lateinit var camCharacteristics: CameraCharacteristics
    private lateinit var stillImgReader: ImageReader

    fun <T : Any> ensure(nullable: T?): T = nullable ?: throw Exception("Ensure FAIL")

    private var camDevice: CameraDevice? = null
    private var evfSurface: Surface? = null
    private var camSession: CameraCaptureSession? = null
    private var evfReqBuilder: CaptureRequest.Builder? = null

    // Parameters.
    private var requestId = 0
    private lateinit var previewStreamFrameSize: Size
    private lateinit var cropRegionRect: Rect

    // Internal clientCallback.
    private var cameraAvailabilityCallback: CameraAvailabilityCallback
    private lateinit var cameraStateCallback: CameraStateCallback
    private var captureSessionStateCallback: CaptureSessionStateCallback? = null
    private var captureCallback: CaptureCallback? = null

    // Sounds.
    private var shutterSound: MediaActionSound

    // Camera thread handler.
    private var callbackHandlerThread: HandlerThread
    private var callbackHandler: Handler
    private var requestHandlerThread: HandlerThread
    private var requestHandler: Handler
    private var backHandlerThread: HandlerThread
    private var backHandler: Handler
    private var recHandlerThread: HandlerThread
    private var recHandler: Handler

    // Orientation.
    private var orientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN
    private var orientationEventListenerImpl: OrientationEventListenerImpl

    private inner class OrientationEventListenerImpl(context: Context?, rate: Int)
            : OrientationEventListener(context, rate) {
        override fun onOrientationChanged(orientation: Int) {
            orientationDegree = orientation
        }
    }

    /**
     * Request TAG.
     *
     * @constructor
     * @param requestId Int Request ID.
     * @param rotationDeg Int Degree of rotation. One of (0, 90, 180, 270).
     */
    private data class RequestTag(val requestId: Int, val rotationDeg: Int)

    init {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR() : E")

        // Camera manager.
        camMng = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Camera thread.
        callbackHandlerThread = HandlerThread("camera-clientCallback", Thread.NORM_PRIORITY)
        callbackHandlerThread.start()
        callbackHandler = Handler(callbackHandlerThread.looper)

        // API request handler.
        requestHandlerThread = HandlerThread("request", Thread.NORM_PRIORITY)
        requestHandlerThread.start()
        requestHandler = Handler(requestHandlerThread.looper)

        // Back worker thread.
        backHandlerThread = HandlerThread("backwork", Thread.NORM_PRIORITY)
        backHandlerThread.start()
        backHandler = Handler(backHandlerThread.looper)

        // Rec worker thread.
        recHandlerThread = HandlerThread("rec", Thread.NORM_PRIORITY)
        recHandlerThread.start()
        recHandler = Handler(backHandlerThread.looper)

        // Internal clientCallback.
        cameraAvailabilityCallback = CameraAvailabilityCallback()
        camMng.registerAvailabilityCallback(
                cameraAvailabilityCallback,
                callbackHandler)

        // Orientation.
        orientationEventListenerImpl = OrientationEventListenerImpl(
                context,
                SensorManager.SENSOR_DELAY_NORMAL)

        // Sound.
        shutterSound = MediaActionSound()
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR() : X")
    }

    override fun prepare() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "prepare()")
        // NOP. Replaced with CONSTRUCTOR.
    }

    override fun release() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : E")

        fun shutdown(handlerThread: HandlerThread) {
            handlerThread.quitSafely()
            try {
                handlerThread.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        // API request handler.
        shutdown(requestHandlerThread)
        // Camera thread.
        shutdown(callbackHandlerThread)
        // Back worker.
        shutdown(backHandlerThread)
        // Rec thread.
        shutdown(recHandlerThread)

        // Internal clientCallback.
        camMng.unregisterAvailabilityCallback(cameraAvailabilityCallback)
        captureSessionStateCallback = null
        captureCallback = null

        // Orientation.
        orientationEventListenerImpl.disable()

        // Camera2 related.
        camDevice = null
        evfSurface = null
        camSession = null
        evfReqBuilder = null

        // Sound.
        shutterSound.release()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "release() : X")
    }

    override fun openAsync(
            evfAspectWH: Float,
            openCallback: CameraPlatformInterface.OpenCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "openAsync()")

        requestHandler.post(OpenTask(evfAspectWH, openCallback))
    }

    /**
     * @param viewFinderAspectRatioWH Float Frame aspect ratio.
     * @param clientCallback OpenCallback OpenCallback.
     */
    private inner class OpenTask(
            val viewFinderAspectRatioWH: Float,
            val clientCallback: CameraPlatformInterface.OpenCallback) : Runnable {
        val TAG = "OpenTask"

        private fun earlyReturn(msg: String, isSuccess: Boolean) {
            Log.logError(TAG, msg)
            clientCallbackHandler.post { clientCallback.onOpened(isSuccess) }
        }

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            if (camDevice != null) {
                earlyReturn("Camera is already opened.", true)
                return
            }

            // Get static configurations.
            val camId: String
            val streamConfigMap: StreamConfigurationMap
            try {
                // ID.
                camId = PDR2.getBackCameraId(camMng) ?: run {
                    earlyReturn("Back camera is not available.", false)
                    return
                }
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera ID = $camId : DONE")

                // Characteristics.
                camCharacteristics = camMng.getCameraCharacteristics(camId)
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera Characteristics : DONE")

                // Stream configurations.
                streamConfigMap = camCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: run {
                    earlyReturn("Back streamConfigMap == null", false)
                    return
                }
                if (Log.IS_DEBUG) Log.logDebug(TAG, "get Camera stream config map : DONE")

                if (Log.IS_DEBUG) {
                    Log.logDebug(TAG, "## Output Image Formats")
                    PDR2.logImageFormats(streamConfigMap)
                    PDR2.logSurfaceTextureOutputSizes(streamConfigMap)
                }

            } catch (e: CameraAccessException) {
                earlyReturn("Failed to get back facing camera ID.", false)
                return
            }

            // Parameters.
            requestId = 0
            previewStreamFrameSize = PDR2.getPreviewStreamFrameSize(camCharacteristics)
            cropRegionRect = PDR2.getAspectConsideredScalerCropRegion(
                    camCharacteristics,
                    viewFinderAspectRatioWH)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Setup parameters : DONE")

            // Open.
            cameraStateCallback = CameraStateCallback()
            try {
                camMng.openCamera(camId, cameraStateCallback, callbackHandler)
            } catch (e: CameraAccessException) {
                earlyReturn("Failed to openCamera()", false)
                return
            } catch (e: SecurityException) {
                earlyReturn("Open camera is not permitted", false)
                return
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "Open request : DONE")

            // Create still capture request and image reader.
            // Size.
            val jpegSize = PDR2.getOptimalJpegFrameSize(
                    streamConfigMap,
                    previewStreamFrameSize)
            // Image reader.
            stillImgReader = ImageReader.newInstance(
                    jpegSize.width,
                    jpegSize.height,
                    ImageFormat.JPEG,
                    2)
            stillImgReader.setOnImageAvailableListener(
                    OnImageAvailableListenerImpl(),
                    callbackHandler)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Still ImageReader : DONE")

            // Video recorder.
            val videoSize: Size = PDR2.getVideoStreamFrameSize(camCharacteristics)
            recMediaFormat = MediaCodecPDR.getVideoMediaFormat(videoSize.width, videoSize.height)
            recEncoderName = MediaCodecPDR.getVideoEncoderName(recMediaFormat!!)
            recSurface = MediaCodec.createPersistentInputSurface()
            recMediaCodec = genVideoMediaCodec()

            // Orientation.
            orientationEventListenerImpl.enable()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Orientation : DONE")

            val isSucceeded = cameraStateCallback.waitForOpened()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera open $isSucceeded")
            clientCallbackHandler.post { clientCallback.onOpened(isSucceeded) }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    override fun closeAsync(closeCallback: CameraPlatformInterface.CloseCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "closeAsync()")

        requestHandler.post(UnbindSurfaceTask())
        requestHandler.post(CloseTask(closeCallback))
    }

    /**
     * @param clientCallback CloseCallback.
     */
    private inner class CloseTask(val clientCallback: CameraPlatformInterface.CloseCallback) : Runnable {
        val TAG = "CloseTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            val cam = camDevice ?: run {
                Log.logError(TAG, "Camera is already closed.")
                clientCallbackHandler.post { clientCallback.onClosed(true) }
                return
            }

            // Camera.
            cam.close()
            camDevice = null

            // Still image reader.
            stillImgReader.close()

            // Video surface.
            recSurface?.let { surface ->
                surface.release()
                recSurface = null
            }

            // Orientation.
            orientationEventListenerImpl.disable()

            val isSucceeded = cameraStateCallback.waitForClosed()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Camera close $isSucceeded")
            clientCallbackHandler.post { clientCallback.onClosed(isSucceeded) }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    override fun bindPreviewSurfaceAsync(
            textureView: TextureView,
            bindSurfaceCallback: CameraPlatformInterface.BindSurfaceCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "bindPreviewSurfaceAsync()")

        requestHandler.post(BindSurfaceTask(textureView, bindSurfaceCallback))
    }

    /**
     * @param textureView TextureView EVF.
     * @param clientCallback BindSurfaceCallback.
     */
    private inner class BindSurfaceTask(
            val textureView: TextureView,
            val clientCallback: CameraPlatformInterface.BindSurfaceCallback) : Runnable {
        val TAG = "BindSurfaceTask"

        private fun earlyReturn(msg: String, isSuccess: Boolean) {
            Log.logError(TAG, msg)
            clientCallbackHandler.post { clientCallback.onSurfaceBound(isSuccess) }
        }

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            if (camSession != null) {
                earlyReturn("Surface is already bound.", true)
                return
            }
            val cam = ensure(camDevice)

            val previewWidth = previewStreamFrameSize.width
            val previewHeight = previewStreamFrameSize.height
            val finderWidth = textureView.width
            val finderHeight = textureView.height

            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "  Preview Frame Size = $previewWidth x $previewHeight")
                Log.logDebug(TAG, "  Finder Size = $finderWidth x $finderHeight")
            }

            // Transform matrix.
            val matrix = PDR2.getTextureViewTransformMatrix(
                    context,
                    previewWidth,
                    previewHeight,
                    finderWidth,
                    finderHeight)
            textureView.setTransform(matrix)

            val surfaceTexture = textureView.surfaceTexture
            surfaceTexture.setDefaultBufferSize(
                    previewStreamFrameSize.width,
                    previewStreamFrameSize.height)
            val evf = Surface(surfaceTexture)
            evfSurface = evf

            // Outputs.
            val recSurface = ensure(recSurface)
            val sessionCallback = CaptureSessionStateCallback()
            captureSessionStateCallback = sessionCallback
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val evfOutputConfig = OutputConfiguration(evf)
                    val stillOutputConfig = OutputConfiguration(stillImgReader.surface)
                    val videoOutputConfig = OutputConfiguration(recSurface)

                    val sessionConfig = SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            listOf(evfOutputConfig, stillOutputConfig, videoOutputConfig),
                            { task -> callbackHandler.post(task) }, // Executor.
                            sessionCallback)
                    cam.createCaptureSession(sessionConfig)
                } else {
                    val outputs = listOf(evf, stillImgReader.surface, recSurface)
                    cam.createCaptureSession(
                            outputs,
                            sessionCallback,
                            callbackHandler)
                }
            } catch (e: CameraAccessException) {
                earlyReturn("Failed to configure outputs.", false)
                return
            }

            val isSucceeded = sessionCallback.waitForOpened()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Bind surface $isSucceeded")

            startEvfStream()

            clientCallbackHandler.post { clientCallback.onSurfaceBound(isSucceeded) }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    private inner class UnbindSurfaceTask : Runnable {
        val TAG = "UnbindSurfaceTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            val session = camSession ?: run {
                Log.logError(TAG, "Surface is already unbound.")
                return
            }

            stopEvfStream()

            session.close()

            val callback = ensure(captureSessionStateCallback)

            val isSucceeded = callback.waitForClosed()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Unbind surface $isSucceeded")

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    private fun startEvfStream() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "startEvfStream() : E")

        val cam = ensure(camDevice)
        val session = ensure(camSession)
        val surface = ensure(evfSurface)
        val callback = ensure(captureCallback)

        try {
            val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            evfReqBuilder = builder

            builder.addTarget(surface)

            // Parameters.
            // Mode.
            builder.set(
                    CaptureRequest.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_AUTO)
            // AF.
            builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            // AE.
            builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_OFF)
            builder.set(
                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
            // AWB.
            builder.set(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO)

            // Build request.
            val evfReq = builder.build()

            session.setRepeatingRequest(evfReq, callback, callbackHandler)

        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to startEvfStream()")
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "startEvfStream() : X")
    }

    private fun stopEvfStream() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stopEvfStream() : E")

        val session = camSession ?: run {
            Log.logError(TAG, "camSession == null")
            return
        }

        try {
            session.stopRepeating()
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to stopEvfStream()")
        }

        evfReqBuilder = null

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stopEvfStream() : X")
    }

    override fun requestScanAsync(scanCallback: CameraPlatformInterface.ScanCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestScanAsync()")

        requestHandler.post(ScanTask(scanCallback))
    }

    /**
     * @param clientCallback ScanCallback.
     */
    private inner class ScanTask(val clientCallback: CameraPlatformInterface.ScanCallback) : Runnable {
        val TAG = "ScanTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            val builder = ensure(evfReqBuilder)

            // Control trigger.
            // Setup.
            builder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START)
//            builder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            val controlTriggerReq = builder.build()

            // Reset.
            builder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
//            builder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            // Repeating request.
            // Setup.
            builder.set(CaptureRequest.CONTROL_AE_LOCK, true)

            val repeatingReq = builder.build()

            // Reset.
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)

            val callback = ensure(captureCallback)
            val session = ensure(camSession)

            callback.requestDetectScanDone()
            try {
                session.capture(controlTriggerReq, callback, callbackHandler)
                session.setRepeatingRequest(repeatingReq, callback, callbackHandler)
            } catch (e: CameraAccessException) {
                throw RuntimeException("Failed to start scan.")
            }
            val isSucceeded = callback.waitForScanDone()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Request scan $isSucceeded")
            clientCallbackHandler.post { clientCallback.onScanDone(isSucceeded) }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    override fun requestCancelScanAsync(cancelScanCallback: CameraPlatformInterface.CancelScanCallback) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestCancelScanAsync()")

        requestHandler.post(CancelScanTask(cancelScanCallback))
    }

    /**
     * @param clientCallback CancelScanCallback
     */
    private inner class CancelScanTask(val clientCallback: CameraPlatformInterface.CancelScanCallback) : Runnable {
        val TAG = "CancelScanTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")

            val builder = ensure(evfReqBuilder)

            // Control trigger.
            // Setup.
            builder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
//            builder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);

            val controlTriggerReq = builder.build()

            // Reset.
            builder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
//            builder.set(
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            // Repeating request.
            // Setup.
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)

            val repeatingReq = builder.build()

            // Reset.
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)

            val callback = ensure(captureCallback)
            val session = ensure(camSession)

            callback.requestDetectCancelScanDone()
            try {
                session.capture(controlTriggerReq, callback, callbackHandler)
                session.setRepeatingRequest(repeatingReq, callback, callbackHandler)
            } catch (e: CameraAccessException) {
                throw RuntimeException("Failed to cancel scan.")
            }

            callback.waitForCancelScanDone()

            clientCallbackHandler.post { clientCallback.onCancelScanDone() }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    override fun requestStillCaptureAsync(stillCaptureCallback: CameraPlatformInterface.StillCaptureCallback): Int {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStillCaptureAsync()")

        ++requestId

        requestHandler.post(StillCaptureTask(requestId, stillCaptureCallback))

        return requestId
    }

    /**
     * @param fixedReqId Int Capture request ID.
     * @param clientCallback StillCaptureCallback
     */
    private inner class StillCaptureTask(
            val fixedReqId: Int,
            val clientCallback: CameraPlatformInterface.StillCaptureCallback) : Runnable {
        val TAG = "StillCaptureTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "  fixedReqId = $fixedReqId")

            val cam = ensure(camDevice)

            try {
                val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

                // AF.
                builder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // AE.
                builder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF)
                builder.set(
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                // AWB.
                builder.set(
                        CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_AUTO)

                // JPEG Quality.
                builder.set(
                        CaptureRequest.JPEG_QUALITY,
                        JPEG_QUALITY.toByte())

                // Surface.
                builder.addTarget(stillImgReader.surface)

                // Orientation.
                val jpegRot = PDR2.getFrameOrientation(
                        camCharacteristics,
                        orientationDegree)
                builder.set(CaptureRequest.JPEG_ORIENTATION, jpegRot)
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  JPEG Rot = $jpegRot")

                // Tag.
                val reqTag = RequestTag(requestId, jpegRot)
                builder.setTag(reqTag)

                val jpegReq = builder.build()

                stopEvfStream()

                val session = ensure(camSession)
                val callback = ensure(captureCallback)

                callback.requestDetectShutterDone()
                callback.requestDetectCaptureDone()
                callback.requestHandlePhotoStoreReadyCallback(clientCallback)
                try {
                    session.capture(jpegReq, callback, callbackHandler)
                } catch (e: CameraAccessException) {
                    throw RuntimeException("Failed to capture.")
                }
                val onShutterRequestId = callback.waitForShutterDone()
                clientCallbackHandler.post { clientCallback.onShutterDone(onShutterRequestId) }
                val onCaptureRequestId = callback.waitForCaptureDone()
                clientCallbackHandler.post { clientCallback.onCaptureDone(onCaptureRequestId) }

                // HandlePhotoStoreReadyCallback is handled in HandleStillCaptureResultTask.

            } catch (e: CameraAccessException) {
                throw RuntimeException("Failed to capture still.")
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    /**
     * @param clientCallback Callback.
     * @param reqTag Request tag.
     */
    private inner class HandleStillCaptureResultTask(
            val clientCallback: CameraPlatformInterface.StillCaptureCallback,
            val reqTag: RequestTag) : Runnable {
        val TAG = "HandleStillCaptureResultTask"

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : E")
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "  Request ID = ${reqTag.requestId}")
                Log.logDebug(TAG, "  Rotation = ${reqTag.rotationDeg}")
            }

            // Get image.
            val img = stillImgReader.acquireNextImage()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "    acquireLatestImage() : DONE")
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "    WIDTH  = ${img.width}")
                Log.logDebug(TAG, "    HEIGHT = ${img.height}")
                Log.logDebug(TAG, "    CROP   = ${img.cropRect.toShortString()}")
            }

            val planes = img.planes
            if (Log.IS_DEBUG) Log.logDebug(TAG, "    getPlanes() : DONE")
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "    Plane x ${planes.size}")
            }

            val buffer = planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "    buffer.get() : DONE")

            img.close()

            // Process JPEG.
            val resultJpeg = ImageProc.doCropRotJpeg(
                    data,
                    reqTag.rotationDeg,
                    cropRegionRect.width().toFloat() / cropRegionRect.height().toFloat(),
                    JPEG_QUALITY)

            clientCallbackHandler.post { clientCallback.onPhotoStoreReady(reqTag.requestId, resultJpeg) }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "run() : X")
        }
    }

    ////// INTERNAL CALLBACKS /////////////////////////////////////////////////////////////////////

    private inner class CameraAvailabilityCallback : CameraManager.AvailabilityCallback() {
        val TAG = "CameraAvailabilityCallback"

        override fun onCameraAvailable(cameraId: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraAvailable() : ID=$cameraId")
            // NOP.
        }

        override fun onCameraUnavailable(cameraId: String) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCameraUnavailable() : ID=$cameraId")
            // NOP.
        }
    }

    private inner class CameraStateCallback : CameraDevice.StateCallback() {
        val TAG = "CameraStateCallback"

        private val openLatch = CountDownLatch(1)
        private val closeLatch = CountDownLatch(1)

        override fun onOpened(camera: CameraDevice) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOpened()")

            camDevice = camera

            openLatch.countDown()
        }

        fun waitForOpened(): Boolean = waitForLatch(openLatch)

        override fun onClosed(camera: CameraDevice) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClosed()")

            camDevice = null

            closeLatch.countDown()
        }

        fun waitForClosed(): Boolean = waitForLatch(closeLatch)

        override fun onDisconnected(camera: CameraDevice) {
            Log.logError(TAG, "onDisconnected()")

            // Already closed.
            closeLatch.countDown()

            // TODO: Handle disconnected error.

        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.logError(TAG, "onError() : error=$error")

            // Already closed.
            closeLatch.countDown()

            // TODO: Handle error.

        }
    }

    private inner class CaptureSessionStateCallback : CameraCaptureSession.StateCallback() {
        val TAG = "CaptureSessionStateCallback"

        private val openLatch = CountDownLatch(1)
        private val closeLatch = CountDownLatch(1)

        override fun onActive(session: CameraCaptureSession) {
            super.onActive(session)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onActive()")
            // NOP.
        }

        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onClosed()")

            camSession = null
            captureCallback = null

            closeLatch.countDown()
        }

        fun waitForClosed(): Boolean = waitForLatch(closeLatch)

        override fun onConfigured(session: CameraCaptureSession) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigured()")

            camSession = session
            captureCallback = CaptureCallback()

            openLatch.countDown()
        }

        fun waitForOpened(): Boolean = waitForLatch(openLatch)

        override fun onConfigureFailed(session: CameraCaptureSession) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onConfigureFailed()")

            camSession = null

            openLatch.countDown()
        }

        override fun onReady(session: CameraCaptureSession) {
            super.onReady(session)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReady()")
            // NOP.
        }

        override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface) {
            super.onSurfacePrepared(session, surface)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfacePrepared()")
            // NOP.
        }
    }

    private inner class CaptureCallback : CameraCaptureSession.CaptureCallback() {
        private val TAG = "CaptureCallback"

        private lateinit var latestRequest: CaptureRequest
        private lateinit var latestResult: TotalCaptureResult

        private var scanDoneLatch: CountDownLatch? = null
        private var cancelScanDoneLatch: CountDownLatch? = null

        private var shutterDoneLatch: CountDownLatch? = null
        private var captureDoneLatch: CountDownLatch? = null

        private var clientStillCaptureCallback: CameraPlatformInterface.StillCaptureCallback? = null

        override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureStarted()")

            latestRequest = request

            val intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT)

            if (intent == null) {
                if (Log.IS_DEBUG) Log.logError(TAG, "CaptureIntent == null.")
            } else when (intent) {
                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_PREVIEW")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_STILL_CAPTURE")

                    // Shutter sound.
//                    shutterSound.play(MediaActionSound.SHUTTER_CLICK);

                    val latch = ensure(shutterDoneLatch)
                    latch.countDown()
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_VIDEO_RECORD")
                    // NOP.
                }

                // NOT used intent.

                CaptureRequest.CONTROL_CAPTURE_INTENT_CUSTOM -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_CUSTOM")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MANUAL -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_MANUAL")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MOTION_TRACKING -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_MOTION_TRACKING")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_VIDEO_SNAPSHOT")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_ZERO_SHUTTER_LAG")
                    // NOP.
                }

                else -> Log.logError(TAG, "  handle unexpected INTENT")
            }
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureCompleted()")

            latestResult = result

            val intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT)

            if (intent == null) {
                if (Log.IS_DEBUG) Log.logError(TAG, "CaptureIntent == null.")
            } else when (intent) {
                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_PREVIEW")
                    handlePreviewIntentCaptureCompleted(result)
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_STILL_CAPTURE")
                    handleStillIntentCaptureCompleted(request)
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_VIDEO_RECORD")
                    handleVideoIntentCaptureCompleted(request)
                }

                // NOT used intent.

                CaptureRequest.CONTROL_CAPTURE_INTENT_CUSTOM -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_CUSTOM")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MANUAL -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_MANUAL")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MOTION_TRACKING -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_MOTION_TRACKING")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_VIDEO_SNAPSHOT")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG -> {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  handle INTENT_ZERO_SHUTTER_LAG")
                    // NOP.
                }

                else -> Log.logError(TAG, "  handle unexpected INTENT")
            }
        }

        private fun handlePreviewIntentCaptureCompleted(result: TotalCaptureResult) {
            // Parameters.
            val afTrigger = result.get(CaptureResult.CONTROL_AF_TRIGGER)
            val aeTrigger = result.get(CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER)
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            if (Log.IS_DEBUG) {
                PDR2.logAfTrigger(afTrigger)
                PDR2.logAfState(afState)
                PDR2.logAeTrigger(aeTrigger)
                PDR2.logAeState(aeState)
            }

            // AF.
            val isAfAvailable = PDR2.isAfAvailable(result)
            val isAfLocked = PDR2.isAfLocked(result)
            // AE.
            val isAeAvailable = PDR2.isAeAvailable(result)
            val isAeLocked = PDR2.isAeLocked(result)

            // Scan.
            run {
                val latch = scanDoneLatch
                if (latch != null) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  Scan required.")

                    if ((isAfLocked || !isAfAvailable) && (isAeLocked || !isAeAvailable)) {
                        latch.countDown()
                    }
                }
            }

            // Cancel scan.
            run {
                val latch = cancelScanDoneLatch
                if (latch != null) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "  Cancel scan required.")

                    if ((!isAfLocked || !isAfAvailable) && (!isAeLocked || !isAeAvailable)) {
                        latch.countDown()
                    }
                }
            }
        }

        fun requestDetectScanDone() {
            scanDoneLatch = CountDownLatch(1)
        }

        fun waitForScanDone(): Boolean {
            val latch = ensure(scanDoneLatch)
            waitForLatch(latch)
            scanDoneLatch = null
            return PDR2.isAfSucceeded(latestResult)
        }

        fun requestDetectCancelScanDone() {
            cancelScanDoneLatch = CountDownLatch(1)
        }

        fun waitForCancelScanDone() {
            val latch = ensure(cancelScanDoneLatch)
            waitForLatch(latch)
            cancelScanDoneLatch = null
        }

        private inner class DummyCancelScanCallback : CameraPlatformInterface.CancelScanCallback {
            override fun onCancelScanDone() { }
        }

        private fun handleStillIntentCaptureCompleted(request: CaptureRequest) {
            val reqTag = request.tag as RequestTag

            // Release scan lock.
            requestHandler.post(CancelScanTask(DummyCancelScanCallback()))

            // Restart preview.
            startEvfStream()

            val callback = ensure(clientStillCaptureCallback)

            // Handle result in background.
            val task = HandleStillCaptureResultTask(callback, reqTag)
            backHandler.post(task)

            val latch = ensure(captureDoneLatch)
            latch.countDown()
        }

        fun requestDetectShutterDone() {
            shutterDoneLatch = CountDownLatch(1)
        }

        fun waitForShutterDone(): Int {
            val latch = ensure(shutterDoneLatch)
            waitForLatch(latch)
            shutterDoneLatch = null

            val reqTag= latestRequest.tag as RequestTag
            return reqTag.requestId
        }

        fun requestDetectCaptureDone() {
            captureDoneLatch = CountDownLatch(1)
        }

        fun waitForCaptureDone(): Int {
            val latch = ensure(captureDoneLatch)
            waitForLatch(latch)
            captureDoneLatch = null

            val reqTag= latestRequest.tag as RequestTag
            return reqTag.requestId
        }

        fun requestHandlePhotoStoreReadyCallback(callback: CameraPlatformInterface.StillCaptureCallback) {
            this.clientStillCaptureCallback = callback
        }

        private fun handleVideoIntentCaptureCompleted(request: CaptureRequest) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "handleVideoIntentCaptureCompleted()")
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## request = $request")
            // NOP.
        }

        override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure) {
            super.onCaptureFailed(session, request, failure)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureFailed()")

            // TODO: Handle capture error.

        }

        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult) {
            super.onCaptureProgressed(session, request, partialResult)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureProgressed()")
            // NOP.
        }

        override fun onCaptureSequenceCompleted(
                session: CameraCaptureSession,
                sequenceId: Int,
                frameNumber: Long) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureSequenceCompleted()")

            if (recSequenceId == sequenceId) {
                // Surface buffers for rec MediaCodec input are all produced.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Rec surface stream is finished.")

                recMediaCodec?.signalEndOfInputStream()
            }
        }

        override fun onCaptureSequenceAborted(
                session: CameraCaptureSession,
                sequenceId: Int) {
            super.onCaptureSequenceAborted(session, sequenceId)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onCaptureSequenceAborted()")
            // NOP.
        }
    }

    private inner class OnImageAvailableListenerImpl : ImageReader.OnImageAvailableListener {
        val TAG = "OnImageAvailableListenerImpl"

        override fun onImageAvailable(reader: ImageReader) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onImageAvailable()")
            // NOP.
        }
    }

    // Video rec.
    private var recMediaCodec: MediaCodec? = null
    private var recSurface: Surface? = null
    private var recMediaFormat: MediaFormat? = null
    private var recEncoderName: String? = null
    private var videoMuxer: MediaMuxer? = null
    private var recCallback: CameraPlatformInterface.RecCallback? = null
    private var recSequenceId: Int = 0
    private var recFileFullPath: String = ""

    private fun genVideoMediaCodec(): MediaCodec {
        val encoderName = ensure(recEncoderName)
        val mediaFormat = ensure(recMediaFormat)
        val recSurface = ensure(recSurface)

        return MediaCodec.createByCodecName(encoderName).apply {
            setCallback(MediaCodecCallbackImpl(), recHandler)
            configure(
                    mediaFormat,
                    null, // output surface.
                    null, // media crypto.
                    MediaCodec.CONFIGURE_FLAG_ENCODE)
            setInputSurface(recSurface)
        }
    }

    private inner class MediaCodecCallbackImpl : MediaCodec.Callback() {
        private val TAG = "MediaCodecCallbackImpl"

        private var videoTrackIndex: Int = 0

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onInputBufferAvailable() : index=$index")
            // NOP.
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputBufferAvailable() : index=$index, info=$info")

            val muxer = ensure(videoMuxer)

            val outBuf: ByteBuffer = ensure(codec.getOutputBuffer(index))
            if (Log.IS_DEBUG) Log.logDebug(TAG, "  outBuf.remaining = ${outBuf.remaining()}")

            muxer.writeSampleData(videoTrackIndex, outBuf, info)

            codec.releaseOutputBuffer(index, false)

            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "FLAG = BUFFER_FLAG_END_OF_STREAM")

                muxer.stop()
                muxer.release()
                videoMuxer = null

                codec.stop()
                codec.release()
                recMediaCodec = genVideoMediaCodec() // prepare next.

                startEvfStream()

                recCallback?.let { callback ->
                    clientCallbackHandler.post { callback.onRecStopped(recFileFullPath) }
                    recCallback = null
                }
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onError() : e=$e")

            // TODO: Handle error.

        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onOutputFormatChanged() : format=$format")

            // After first frame encoded, output format is changed to valid.
            // Add video track here with valid format.

            val muxer = ensure(videoMuxer)

            videoTrackIndex = muxer.addTrack(format)
            muxer.start()
        }
    }

    override fun requestStartRecAsync(
            recFileFullPath: String,
            recCallback: CameraPlatformInterface.RecCallback) {
        this.recFileFullPath = recFileFullPath
        this.recCallback = recCallback

        val startRecTask = StartRecTask(recCallback)
        requestHandler.post(startRecTask)
    }

    private inner class StartRecTask(
            private val callback: CameraPlatformInterface.RecCallback) : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StartRecTask.run() : E")

            val cam = ensure(camDevice)
            val session = ensure(camSession)
            val capCallback = ensure(captureCallback)
            val evfSurface = ensure(evfSurface)
            val recSurface = ensure(recSurface)

            val evfBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(evfSurface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }
            val evfReq = evfBuilder.build()

            val recBuilder = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(recSurface)
                set(CaptureRequest.SCALER_CROP_REGION, cropRegionRect)

            }
            val recReq = recBuilder.build()

            stopEvfStream()

            val recorder = ensure(recMediaCodec)

            recSequenceId = session.setRepeatingBurst(
                    listOf(evfReq, recReq),
                    capCallback,
                    callbackHandler)

            recorder.start()

            videoMuxer = MediaMuxer(
                    recFileFullPath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
                val videoRot = PDR2.getFrameOrientation(
                        camCharacteristics,
                        orientationDegree)
                setOrientationHint(videoRot)
            }

            clientCallbackHandler.post { callback.onRecStarted() }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StartRecTask.run() : X")
        }
    }

    override fun requestStopRecAsync() {
        val stopRecTask = StopRecTask()
        requestHandler.post(stopRecTask)
    }

    private inner class StopRecTask : Runnable {
        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StopRecTask.run() : E")

            camSession?.stopRepeating()

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StopRecTask.run() : X")
        }
    }

    private fun waitForLatch(latch: CountDownLatch): Boolean {
        try {
            val isOk = latch.await(SHUTDOWN_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!isOk) {
                Log.logError(TAG, "waitForLatch() : TIMEOUT")
            }
            return isOk
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return false
    }

}
