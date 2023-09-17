@file:Suppress("PropertyName", "PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.device.api2

import android.content.Context
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
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
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView

import com.fezrestia.android.lib.util.ensure
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import com.fezrestia.android.lib.util.media.ImageProc
import com.fezrestia.android.viewfinderanywhere.device.CameraPlatformInterface

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

    override fun bindPreviewSurfaceTextureAsync(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int,
            bindSurfaceCallback: CameraPlatformInterface.BindSurfaceCallback) {
        val d = delegated ?: throw error()
        d.bindPreviewSurfaceTextureAsync(surfaceTexture, width, height, bindSurfaceCallback)
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

    override fun requestStartVideoStreamAsync(callback: CameraPlatformInterface.VideoCallback) {
        val d = delegated ?: throw error()
        return d.requestStartVideoStreamAsync(callback)
    }

    override fun requestStopVideoStreamAsync() {
        val d = delegated ?: throw error()
        return d.requestStopVideoStreamAsync()
    }

    override fun getPreviewStreamSize(): Size {
        val d = delegated ?: throw error()
        return d.getPreviewStreamSize()
    }

    override fun getSensorOrientation(): Int {
        val d = delegated ?: throw error()
        return d.getSensorOrientation()
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

    private var camDevice: CameraDevice? = null
    private var evfSurface: Surface? = null
    private var evfAspectWH: Float = 1.0f
    private var camSession: CameraCaptureSession? = null
    private var evfReqBuilder: CaptureRequest.Builder? = null

    // Parameters.
    private var requestId = 0
    private lateinit var previewStreamFrameSize: Size
    private lateinit var cropRegionRect: Rect
    private var sensorOrientation: Int = -1

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
        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR() : E")

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

        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR() : X")
    }

    override fun prepare() {
        if (IS_DEBUG) logD(TAG, "prepare()")
        // NOP. Replaced with CONSTRUCTOR.
    }

    override fun release() {
        if (IS_DEBUG) logD(TAG, "release() : E")

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

        if (IS_DEBUG) logD(TAG, "release() : X")
    }

    override fun openAsync(
            evfAspectWH: Float,
            openCallback: CameraPlatformInterface.OpenCallback) {
        if (IS_DEBUG) logD(TAG, "openAsync()")

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
            logE(TAG, msg)
            clientCallbackHandler.post { clientCallback.onOpened(isSuccess) }
        }

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

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
                if (IS_DEBUG) logD(TAG, "get Camera ID = $camId : DONE")

                // Characteristics.
                camCharacteristics = camMng.getCameraCharacteristics(camId)
                if (IS_DEBUG) logD(TAG, "get Camera Characteristics : DONE")

                // Stream configurations.
                streamConfigMap = camCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: run {
                    earlyReturn("Back streamConfigMap == null", false)
                    return
                }
                if (IS_DEBUG) logD(TAG, "get Camera stream config map : DONE")

                sensorOrientation = camCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                if (IS_DEBUG) logD(TAG, "sensorOrientation = $sensorOrientation")

                if (IS_DEBUG) {
                    logD(TAG, "## Output Image Formats")
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
            if (IS_DEBUG) logD(TAG, "Setup parameters : DONE")

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

            if (IS_DEBUG) logD(TAG, "Open request : DONE")

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
            if (IS_DEBUG) logD(TAG, "Still ImageReader : DONE")

            // Orientation.
            orientationEventListenerImpl.enable()
            if (IS_DEBUG) logD(TAG, "Orientation : DONE")

            val isSucceeded = cameraStateCallback.waitForOpened()
            if (IS_DEBUG) logD(TAG, "Camera open $isSucceeded")
            clientCallbackHandler.post { clientCallback.onOpened(isSucceeded) }

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    override fun closeAsync(closeCallback: CameraPlatformInterface.CloseCallback) {
        if (IS_DEBUG) logD(TAG, "closeAsync()")

        requestHandler.post(UnbindSurfaceTask())
        requestHandler.post(CloseTask(closeCallback))
    }

    /**
     * @param clientCallback CloseCallback.
     */
    private inner class CloseTask(val clientCallback: CameraPlatformInterface.CloseCallback) : Runnable {
        val TAG = "CloseTask"

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            val cam = camDevice ?: run {
                logE(TAG, "Camera is already closed.")
                clientCallbackHandler.post { clientCallback.onClosed(true) }
                return
            }

            // Camera.
            cam.close()
            camDevice = null

            // Still image reader.
            stillImgReader.close()

            // Orientation.
            orientationEventListenerImpl.disable()

            val isSucceeded = cameraStateCallback.waitForClosed()
            if (IS_DEBUG) logD(TAG, "Camera close $isSucceeded")
            clientCallbackHandler.post { clientCallback.onClosed(isSucceeded) }

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    override fun bindPreviewSurfaceAsync(
            textureView: TextureView,
            bindSurfaceCallback: CameraPlatformInterface.BindSurfaceCallback) {
        if (IS_DEBUG) logD(TAG, "bindPreviewSurfaceAsync()")

        evfAspectWH = textureView.width.toFloat() / textureView.height.toFloat()

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
            logE(TAG, msg)
            clientCallbackHandler.post { clientCallback.onSurfaceBound(isSuccess) }
        }

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            if (camSession != null) {
                earlyReturn("Surface is already bound.", true)
                return
            }
            val cam = ensure(camDevice)

            val previewWidth = previewStreamFrameSize.width
            val previewHeight = previewStreamFrameSize.height
            val finderWidth = textureView.width
            val finderHeight = textureView.height

            if (IS_DEBUG) {
                logD(TAG, "  Preview Frame Size = $previewWidth x $previewHeight")
                logD(TAG, "  Finder Size = $finderWidth x $finderHeight")
            }

            // Transform matrix.
            val matrix = PDR2.getTextureViewTransformMatrix(
                    context,
                    previewWidth,
                    previewHeight,
                    finderWidth,
                    finderHeight)
            textureView.setTransform(matrix)

            val surfaceTexture = ensure(textureView.surfaceTexture)
            surfaceTexture.setDefaultBufferSize(
                    previewStreamFrameSize.width,
                    previewStreamFrameSize.height)
            val evf = Surface(surfaceTexture)
            evfSurface = evf

            val sessionCallback = CaptureSessionStateCallback()
            captureSessionStateCallback = sessionCallback

            try {
                configureOutputs(cam, evf, sessionCallback)
            } catch (e: CameraAccessException) {
                earlyReturn("Failed to configure outputs.", false)
                return
            }

            val isSucceeded = sessionCallback.waitForOpened()
            if (IS_DEBUG) logD(TAG, "Bind surface $isSucceeded")

            startEvfStream()

            clientCallbackHandler.post { clientCallback.onSurfaceBound(isSucceeded) }

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    override fun bindPreviewSurfaceTextureAsync(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int,
            bindSurfaceCallback: CameraPlatformInterface.BindSurfaceCallback) {
        if (IS_DEBUG) logD(TAG, "bindPreviewSurfaceTextureAsync()")

        evfAspectWH = width.toFloat() / height.toFloat()

        requestHandler.post(BindSurfaceTextureTask(surfaceTexture, bindSurfaceCallback))
    }

    private inner class BindSurfaceTextureTask(
            val surfaceTexture: SurfaceTexture,
            val clientCallback: CameraPlatformInterface.BindSurfaceCallback) : Runnable {
        val TAG = "BindSurfaceTextureTask"

        private fun earlyReturn(msg: String, isSuccess: Boolean) {
            logE(TAG, msg)
            clientCallbackHandler.post { clientCallback.onSurfaceBound(isSuccess) }
        }

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            if (camSession != null) {
                earlyReturn("Surface is already bound.", true)
                return
            }
            val cam = ensure(camDevice)

            val previewWidth = previewStreamFrameSize.width
            val previewHeight = previewStreamFrameSize.height

            if (IS_DEBUG) {
                logD(TAG, "  Preview Frame Size = $previewWidth x $previewHeight")
            }

            surfaceTexture.setDefaultBufferSize(
                    previewStreamFrameSize.width,
                    previewStreamFrameSize.height)
            val evf = Surface(surfaceTexture)
            evfSurface = evf

            val sessionCallback = CaptureSessionStateCallback()
            captureSessionStateCallback = sessionCallback

            try {
                configureOutputs(cam, evf, sessionCallback)
            } catch (e: CameraAccessException) {
                earlyReturn("Failed to configure outputs.", false)
                return
            }

            val isSucceeded = sessionCallback.waitForOpened()
            if (IS_DEBUG) logD(TAG, "Bind surface $isSucceeded")

            startEvfStream()

            clientCallbackHandler.post { clientCallback.onSurfaceBound(isSucceeded) }

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    private fun configureOutputs(
            cam: CameraDevice,
            evf: Surface,
            sessionCallback: CaptureSessionStateCallback) {
            val evfOutputConfig = OutputConfiguration(evf)
        val stillOutputConfig = OutputConfiguration(stillImgReader.surface)

        val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(evfOutputConfig, stillOutputConfig),
                { task -> callbackHandler.post(task) }, // Executor.
                sessionCallback)
        cam.createCaptureSession(sessionConfig)
    }

    private inner class UnbindSurfaceTask : Runnable {
        val TAG = "UnbindSurfaceTask"

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

            val session = camSession ?: run {
                logE(TAG, "Surface is already unbound.")
                return
            }

            stopEvfStream()

            session.close()

            val callback = ensure(captureSessionStateCallback)

            val isSucceeded = callback.waitForClosed()
            if (IS_DEBUG) logD(TAG, "Unbind surface $isSucceeded")

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    private fun startEvfStream() {
        if (IS_DEBUG) logD(TAG, "startEvfStream() : E")

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

        if (IS_DEBUG) logD(TAG, "startEvfStream() : X")
    }

    private fun stopEvfStream() {
        if (IS_DEBUG) logD(TAG, "stopEvfStream() : E")

        val session = camSession ?: run {
            logE(TAG, "camSession == null")
            return
        }

        try {
            session.stopRepeating()
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to stopEvfStream()")
        }

        evfReqBuilder = null

        if (IS_DEBUG) logD(TAG, "stopEvfStream() : X")
    }

    override fun requestScanAsync(scanCallback: CameraPlatformInterface.ScanCallback) {
        if (IS_DEBUG) logD(TAG, "requestScanAsync()")

        requestHandler.post(ScanTask(scanCallback))
    }

    /**
     * @param clientCallback ScanCallback.
     */
    private inner class ScanTask(val clientCallback: CameraPlatformInterface.ScanCallback) : Runnable {
        val TAG = "ScanTask"

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

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
            if (IS_DEBUG) logD(TAG, "Request scan $isSucceeded")
            clientCallbackHandler.post { clientCallback.onScanDone(isSucceeded) }

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    override fun requestCancelScanAsync(cancelScanCallback: CameraPlatformInterface.CancelScanCallback) {
        if (IS_DEBUG) logD(TAG, "requestCancelScanAsync()")

        requestHandler.post(CancelScanTask(cancelScanCallback))
    }

    /**
     * @param clientCallback CancelScanCallback
     */
    private inner class CancelScanTask(val clientCallback: CameraPlatformInterface.CancelScanCallback) : Runnable {
        val TAG = "CancelScanTask"

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run() : E")

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

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    override fun requestStillCaptureAsync(stillCaptureCallback: CameraPlatformInterface.StillCaptureCallback): Int {
        if (IS_DEBUG) logD(TAG, "requestStillCaptureAsync()")

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
            if (IS_DEBUG) logD(TAG, "run() : E")
            if (IS_DEBUG) logD(TAG, "  fixedReqId = $fixedReqId")

            val cam = ensure(camDevice)

            try {
                stopEvfStream()

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
                if (IS_DEBUG) logD(TAG, "  JPEG Rot = $jpegRot")

                // Tag.
                val reqTag = RequestTag(fixedReqId, jpegRot)
                builder.setTag(reqTag)

                val jpegReq = builder.build()

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

            if (IS_DEBUG) logD(TAG, "run() : X")
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
            if (IS_DEBUG) logD(TAG, "run() : E")
            if (IS_DEBUG) {
                logD(TAG, "  Request ID = ${reqTag.requestId}")
                logD(TAG, "  Rotation = ${reqTag.rotationDeg}")
            }

            // Get image.
            val img = stillImgReader.acquireNextImage()
            if (IS_DEBUG) logD(TAG, "    acquireLatestImage() : DONE")
            if (IS_DEBUG) {
                logD(TAG, "    WIDTH  = ${img.width}")
                logD(TAG, "    HEIGHT = ${img.height}")
                logD(TAG, "    CROP   = ${img.cropRect.toShortString()}")
            }

            val planes = img.planes
            if (IS_DEBUG) logD(TAG, "    getPlanes() : DONE")
            if (IS_DEBUG) {
                logD(TAG, "    Plane x ${planes.size}")
            }

            val buffer = planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            if (IS_DEBUG) logD(TAG, "    buffer.get() : DONE")

            img.close()

            // Process JPEG.
            val resultJpeg = ImageProc.doCropRotJpeg(
                    data,
                    reqTag.rotationDeg,
                    evfAspectWH,
                    JPEG_QUALITY)

            clientCallbackHandler.post { clientCallback.onPhotoStoreReady(reqTag.requestId, resultJpeg) }

            if (IS_DEBUG) logD(TAG, "run() : X")
        }
    }

    ////// INTERNAL CALLBACKS /////////////////////////////////////////////////////////////////////

    private inner class CameraAvailabilityCallback : CameraManager.AvailabilityCallback() {
        val TAG = "CameraAvailabilityCallback"

        override fun onCameraAvailable(cameraId: String) {
            if (IS_DEBUG) logD(TAG, "onCameraAvailable() : ID=$cameraId")
            // NOP.
        }

        override fun onCameraUnavailable(cameraId: String) {
            if (IS_DEBUG) logD(TAG, "onCameraUnavailable() : ID=$cameraId")
            // NOP.
        }
    }

    private inner class CameraStateCallback : CameraDevice.StateCallback() {
        val TAG = "CameraStateCallback"

        private val openLatch = CountDownLatch(1)
        private val closeLatch = CountDownLatch(1)

        override fun onOpened(camera: CameraDevice) {
            if (IS_DEBUG) logD(TAG, "onOpened()")

            camDevice = camera

            openLatch.countDown()
        }

        fun waitForOpened(): Boolean = waitForLatch(openLatch)

        override fun onClosed(camera: CameraDevice) {
            if (IS_DEBUG) logD(TAG, "onClosed()")

            camDevice = null

            closeLatch.countDown()
        }

        fun waitForClosed(): Boolean = waitForLatch(closeLatch)

        override fun onDisconnected(camera: CameraDevice) {
            logE(TAG, "onDisconnected()")

            // Already closed.
            closeLatch.countDown()

            // TODO: Handle disconnected error.

        }

        override fun onError(camera: CameraDevice, error: Int) {
            logE(TAG, "onError() : error=$error")

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
            if (IS_DEBUG) logD(TAG, "onActive()")
            // NOP.
        }

        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            if (IS_DEBUG) logD(TAG, "onClosed()")

            camSession = null
            captureCallback = null

            closeLatch.countDown()
        }

        fun waitForClosed(): Boolean = waitForLatch(closeLatch)

        override fun onConfigured(session: CameraCaptureSession) {
            if (IS_DEBUG) logD(TAG, "onConfigured()")

            camSession = session
            captureCallback = CaptureCallback()

            openLatch.countDown()
        }

        fun waitForOpened(): Boolean = waitForLatch(openLatch)

        override fun onConfigureFailed(session: CameraCaptureSession) {
            if (IS_DEBUG) logD(TAG, "onConfigureFailed()")

            camSession = null

            openLatch.countDown()
        }

        override fun onReady(session: CameraCaptureSession) {
            super.onReady(session)
            if (IS_DEBUG) logD(TAG, "onReady()")
            // NOP.
        }

        override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface) {
            super.onSurfacePrepared(session, surface)
            if (IS_DEBUG) logD(TAG, "onSurfacePrepared()")
            // NOP.
        }
    }

    private inner class CaptureCallback : CameraCaptureSession.CaptureCallback() {
        private val TAG = "CaptureCallback"

        private lateinit var latestPreviewRequest: CaptureRequest
        private lateinit var latestStillCaptureRequest: CaptureRequest
        private lateinit var latestVideoCaptureRequest: CaptureRequest

        private lateinit var latestPreviewResult: TotalCaptureResult
        private lateinit var latestStillCaptureResult: TotalCaptureResult
        private lateinit var latestVideoCaptureResult: TotalCaptureResult

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
            if (IS_DEBUG) logD(TAG, "onCaptureStarted()")

            val intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT)

            if (intent == null) {
                if (IS_DEBUG) logE(TAG, "CaptureIntent == null.")
            } else when (intent) {
                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_PREVIEW")

                    latestPreviewRequest = request
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_STILL_CAPTURE")

                    latestStillCaptureRequest = request

                    // Shutter sound.
//                    shutterSound.play(MediaActionSound.SHUTTER_CLICK);

                    val latch = ensure(shutterDoneLatch)
                    latch.countDown()
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_VIDEO_RECORD")

                    latestVideoCaptureRequest = request

                    if (!isVideoStreamStarted) {
                        isVideoStreamStarted = true
                        clientCallbackHandler.post { videoCallback?.onVideoStreamStarted() }
                    }
                }

                // NOT used intent.

                CaptureRequest.CONTROL_CAPTURE_INTENT_CUSTOM -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_CUSTOM")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MANUAL -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_MANUAL")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MOTION_TRACKING -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_MOTION_TRACKING")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_VIDEO_SNAPSHOT")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_ZERO_SHUTTER_LAG")
                    // NOP.
                }

                else -> logE(TAG, "  handle unexpected INTENT")
            }
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            if (IS_DEBUG) logD(TAG, "onCaptureCompleted()")

            val intent = request.get(CaptureRequest.CONTROL_CAPTURE_INTENT)

            if (intent == null) {
                if (IS_DEBUG) logE(TAG, "CaptureIntent == null.")
            } else when (intent) {
                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_PREVIEW")

                    latestPreviewResult = result
                    handlePreviewIntentCaptureCompleted(result)
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_STILL_CAPTURE")

                    latestStillCaptureResult = result
                    handleStillIntentCaptureCompleted(request)
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_VIDEO_RECORD")

                    latestVideoCaptureResult = result
                    handleVideoIntentCaptureCompleted(request)
                }

                // NOT used intent.

                CaptureRequest.CONTROL_CAPTURE_INTENT_CUSTOM -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_CUSTOM")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MANUAL -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_MANUAL")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_MOTION_TRACKING -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_MOTION_TRACKING")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_VIDEO_SNAPSHOT")
                    // NOP.
                }

                CaptureRequest.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG -> {
                    if (IS_DEBUG) logD(TAG, "  handle INTENT_ZERO_SHUTTER_LAG")
                    // NOP.
                }

                else -> logE(TAG, "  handle unexpected INTENT")
            }
        }

        private fun handlePreviewIntentCaptureCompleted(result: TotalCaptureResult) {
            // Parameters.
            val afTrigger = result.get(CaptureResult.CONTROL_AF_TRIGGER)
            val aeTrigger = result.get(CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER)
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            if (IS_DEBUG) {
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
                    if (IS_DEBUG) logD(TAG, "  Scan required.")

                    if ((isAfLocked || !isAfAvailable) && (isAeLocked || !isAeAvailable)) {
                        latch.countDown()
                    }
                }
            }

            // Cancel scan.
            run {
                val latch = cancelScanDoneLatch
                if (latch != null) {
                    if (IS_DEBUG) logD(TAG, "  Cancel scan required.")

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
            return PDR2.isAfSucceeded(latestPreviewResult)
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

            val reqTag= latestStillCaptureRequest.tag as RequestTag
            return reqTag.requestId
        }

        fun requestDetectCaptureDone() {
            captureDoneLatch = CountDownLatch(1)
        }

        fun waitForCaptureDone(): Int {
            val latch = ensure(captureDoneLatch)
            waitForLatch(latch)
            captureDoneLatch = null

            val reqTag= latestStillCaptureRequest.tag as RequestTag
            return reqTag.requestId
        }

        fun requestHandlePhotoStoreReadyCallback(callback: CameraPlatformInterface.StillCaptureCallback) {
            this.clientStillCaptureCallback = callback
        }

        private fun handleVideoIntentCaptureCompleted(request: CaptureRequest) {
            if (IS_DEBUG) logD(TAG, "handleVideoIntentCaptureCompleted()")
            if (IS_DEBUG) logD(TAG, "## request = $request")
            // NOP.
        }

        override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure) {
            super.onCaptureFailed(session, request, failure)
            if (IS_DEBUG) logD(TAG, "onCaptureFailed()")

            // TODO: Handle capture error.

        }

        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult) {
            super.onCaptureProgressed(session, request, partialResult)
            if (IS_DEBUG) logD(TAG, "onCaptureProgressed()")
            // NOP.
        }

        override fun onCaptureSequenceCompleted(
                session: CameraCaptureSession,
                sequenceId: Int,
                frameNumber: Long) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
            if (IS_DEBUG) logD(TAG, "onCaptureSequenceCompleted()")

            if (sequenceId == videoSequenceId) {
                clientCallbackHandler.post { videoCallback?.onVideoStreamStopped() }
            }
        }

        override fun onCaptureSequenceAborted(
                session: CameraCaptureSession,
                sequenceId: Int) {
            super.onCaptureSequenceAborted(session, sequenceId)
            if (IS_DEBUG) logD(TAG, "onCaptureSequenceAborted()")
            // NOP.
        }
    }

    private inner class OnImageAvailableListenerImpl : ImageReader.OnImageAvailableListener {
        val TAG = "OnImageAvailableListenerImpl"

        override fun onImageAvailable(reader: ImageReader) {
            if (IS_DEBUG) logD(TAG, "onImageAvailable()")
            // NOP.
        }
    }

    private var videoCallback: CameraPlatformInterface.VideoCallback? = null
    private var videoSequenceId: Int = -1
    private var isVideoStreamStarted = false

    override fun requestStartVideoStreamAsync(callback: CameraPlatformInterface.VideoCallback) {
        if (IS_DEBUG) logD(TAG, "startVideoStream()")

        videoCallback = callback
        isVideoStreamStarted = false
        requestHandler.post(StartVideoStreamTask())
    }

    private inner class StartVideoStreamTask : Runnable {
        private val TAG = "StartVideoStreamTask"

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run()")

            val cam = ensure(camDevice)
            val session = ensure(camSession)
            val surface = ensure(evfSurface)
            val callback = ensure(captureCallback)

            try {
                val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

                builder.addTarget(surface)

                // Parameters.
                // Mode.
                builder.set(
                        CaptureRequest.CONTROL_MODE,
                        CaptureRequest.CONTROL_MODE_AUTO)
                // AF.
                builder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
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
                builder.set(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(30, 30))
                // AWB.
                builder.set(
                        CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_AUTO)

                // Build request.
                val videoReq = builder.build()

                videoSequenceId = session.setRepeatingRequest(videoReq, callback, callbackHandler)

            } catch (e: CameraAccessException) {
                throw RuntimeException("Failed to startEvfStream()")
            }
        }
    }

    override fun requestStopVideoStreamAsync() {
        if (IS_DEBUG) logD(TAG, "stopVideoStream()")

        requestHandler.post(StopVideoStreamTask())
    }

    private inner class StopVideoStreamTask : Runnable {
        val TAG = "StopVideoStreamTask"

        override fun run() {
            if (IS_DEBUG) logD(TAG, "run()")

            val session = ensure(camSession)
            session.stopRepeating()

            // Restart normal preview.
            startEvfStream()
        }
    }

    override fun getPreviewStreamSize(): Size {
        when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Screen orientation and imager orientation is matched.
                return previewStreamFrameSize
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                // Screen orientation and imager orientation is against.
                return Size(previewStreamFrameSize.height, previewStreamFrameSize.width)
            }
            else -> {
                val w = context.resources.displayMetrics.widthPixels
                val h = context.resources.displayMetrics.heightPixels
                return if (w > h) {
                    // Landscape.
                    previewStreamFrameSize
                } else {
                    // Portrait.
                    Size(previewStreamFrameSize.height, previewStreamFrameSize.width)
                }
            }
        }
    }

    override fun getSensorOrientation(): Int = sensorOrientation

    private fun waitForLatch(latch: CountDownLatch): Boolean {
        try {
            val isOk = latch.await(SHUTDOWN_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!isOk) {
                logE(TAG, "waitForLatch() : TIMEOUT")
            }
            return isOk
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return false
    }

}
