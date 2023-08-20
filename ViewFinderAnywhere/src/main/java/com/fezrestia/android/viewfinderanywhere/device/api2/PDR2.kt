@file:Suppress("MayBeConstant", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.device.api2

import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.WindowManager

import com.fezrestia.android.lib.util.currentDisplayRot
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE

import java.util.ArrayList

/**
 * Platform Dependency Resolver based on Camera API 2.0.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal object PDR2 {
    // Log tag.
    private val TAG = "PDR2"

    /**
     * Get back facing camera ID.
     *
     * @param camMng CameraManager instance.
     * @return Back camera ID.
     * @throws CameraAccessException Camera can not be accessed.
     */
    @Throws(CameraAccessException::class)
    fun getBackCameraId(camMng: CameraManager): String? {
        camMng.cameraIdList.forEach { id ->
            if (IS_DEBUG) logD(TAG, "ID = $id")

            val charas = camMng.getCameraCharacteristics(id)

            if (IS_DEBUG) {
                logD(TAG, "#### AVAILABLE KEYS")
                charas.keys.forEach { key ->
                    logD(TAG, "## KEY = $key")
                }
            }

            // Check facing.
            if (charas.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                // Valid.
                return id
            }
        }

        // Not available.
        return null
    }

    fun logImageFormats(streamConfigMap: StreamConfigurationMap) {
        logD(TAG, "#### SUPPORTED IMAGE FORMATS")
        val formats = streamConfigMap.outputFormats
        for (format in formats) {
            when (format) {
                ImageFormat.DEPTH16             -> logD(TAG, "## FORMAT = DEPTH16")
                ImageFormat.DEPTH_POINT_CLOUD   -> logD(TAG, "## FORMAT = DEPTH_POINT_CLOUD")
                ImageFormat.FLEX_RGBA_8888      -> logD(TAG, "## FORMAT = FLEX_RGBA_8888")
                ImageFormat.FLEX_RGB_888        -> logD(TAG, "## FORMAT = FLEX_RGB_888")
                ImageFormat.JPEG                -> logD(TAG, "## FORMAT = JPEG")
                ImageFormat.NV16                -> logD(TAG, "## FORMAT = NV16")
                ImageFormat.NV21                -> logD(TAG, "## FORMAT = NV21")
                ImageFormat.PRIVATE             -> logD(TAG, "## FORMAT = PRIVATE")
                ImageFormat.RAW10               -> logD(TAG, "## FORMAT = RAW10")
                ImageFormat.RAW12               -> logD(TAG, "## FORMAT = RAW12")
                ImageFormat.RAW_SENSOR          -> logD(TAG, "## FORMAT = RAW_SENSOR")
                ImageFormat.RGB_565             -> logD(TAG, "## FORMAT = RGB_565")
                ImageFormat.UNKNOWN             -> logD(TAG, "## FORMAT = UNKNOWN")
                ImageFormat.YUV_420_888         -> logD(TAG, "## FORMAT = YUV_420_888")
                ImageFormat.YUV_422_888         -> logD(TAG, "## FORMAT = YUV_422_888")
                ImageFormat.YUV_444_888         -> logD(TAG, "## FORMAT = YUV_444_888")
                ImageFormat.YUY2                -> logD(TAG, "## FORMAT = YUY2")
                ImageFormat.YV12                -> logD(TAG, "## FORMAT = YV12")
                else                            -> logD(TAG, "## FORMAT = default : $format")
            }

            val outputSizes = streamConfigMap.getOutputSizes(format)
            outputSizes.forEach { size ->
                logD(TAG, "## SIZE = $size")
            }
        }
    }

    fun logSurfaceTextureOutputSizes(configMap: StreamConfigurationMap) {
        logD(TAG, "#### SurfaceTexture OUTPUT SIZES")
        configMap.getOutputSizes(SurfaceTexture::class.java).forEach { size ->
            logD(TAG, "## SIZE = $size")
        }
    }

    fun logAfTrigger(afTrigger: Int?) {
        if (afTrigger == null) {
            logD(TAG, "#### AF Trigger == NULL")
        } else when (afTrigger) {
            CaptureResult.CONTROL_AF_TRIGGER_CANCEL -> logD(TAG, "## AF Trigger == CANCEL")
            CaptureResult.CONTROL_AF_TRIGGER_IDLE   -> logD(TAG, "## AF Trigger == IDLE")
            CaptureResult.CONTROL_AF_TRIGGER_START  -> logD(TAG, "## AF Trigger == START")
            else                                    -> logD(TAG, "## AF Trigger == default")
        }
    }

    fun logAfState(afState: Int?) {
        if (afState == null) {
            logD(TAG, "#### AF State == NULL")
        } else when (afState) {
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN          -> logD(TAG, "## AF State == ACTIVE_SCAN")
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED       -> logD(TAG, "## AF State == FOCUSED_LOCKED")
            CaptureResult.CONTROL_AF_STATE_INACTIVE             -> logD(TAG, "## AF State == INACTIVE")
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED   -> logD(TAG, "## AF State == NOT_FOCUSED_LOCKED")
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED      -> logD(TAG, "## AF State == PASSIVE_FOCUSED")
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN         -> logD(TAG, "## AF State == PASSIVE_SCAN")
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED    -> logD(TAG, "## AF State == PASSIVE_UNFOCUSED")
            else                                                -> logD(TAG, "## AF State == default")
        }
    }

    fun logAeTrigger(aeTrigger: Int?) {
        if (aeTrigger == null) {
            logD(TAG, "#### AE Trigger == NULL")
        } else when (aeTrigger) {
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL  -> logD(TAG, "## AE Trigger == CANCEL")
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE    -> logD(TAG, "## AE Trigger == IDLE")
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_START   -> logD(TAG, "## AE Trigger == START")
            else                                                -> logD(TAG, "## AE Trigger == default")
        }
    }

    fun logAeState(aeState: Int?) {
        if (aeState == null) {
            logD(TAG, "#### AE State == NULL")
        } else when (aeState) {
            CaptureResult.CONTROL_AE_STATE_CONVERGED        -> logD(TAG, "## AE State == CONVERGED")
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED   -> logD(TAG, "## AE State == FLASH_REQUIRED")
            CaptureResult.CONTROL_AE_STATE_INACTIVE         -> logD(TAG, "## AE State == INACTIVE")
            CaptureResult.CONTROL_AE_STATE_LOCKED           -> logD(TAG, "## AE State == LOCKED")
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE       -> logD(TAG, "## AE State == PRECAPTURE")
            CaptureResult.CONTROL_AE_STATE_SEARCHING        -> logD(TAG, "## AE State == SEARCHING")
            else                                            -> logD(TAG, "## AE State == default")
        }
    }

    /**
     * Get frame orientation.
     *
     * @param charas Camera characteristics.
     * @param deviceOrientation Camera orientation degree.
     * @return Frame orientation degree.
     */
    fun getFrameOrientation(
            charas: CameraCharacteristics,
            deviceOrientation: Int): Int {
        var orientation = deviceOrientation

        // Check.
        if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) {
            return 90
        }

        // Round.
        orientation = (orientation + 45) / 90 * 90

        // Consider front facing.
        val isFacingFront = charas.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        if (isFacingFront) orientation *= -1

        val sensorOrientation = charas.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

        return (sensorOrientation + orientation + 360) % 360
    }

    /**
     * Get transform matrix for TextureView preview stream.
     *
     * @param context Master context.
     * @param bufferWidth Frame buffer width.
     * @param bufferHeight Frame buffer height.
     * @param finderWidth Finder width.
     * @param finderHeight Finder height.
     * @return Transform matrix.
     */
    @JvmStatic
    fun getTextureViewTransformMatrix(
            context: Context,
            bufferWidth: Int,
            bufferHeight: Int,
            finderWidth: Int,
            finderHeight: Int): Matrix {
        if (IS_DEBUG) logD(TAG, "getTextureViewTransformMatrix() : E")

        // Display rotation.
        val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = currentDisplayRot(context, winMng)

        val matrix = Matrix()
        matrix.reset()
        val bufferRect = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
        val finderRect = RectF(0f, 0f, finderWidth.toFloat(), finderHeight.toFloat())
        if (IS_DEBUG) {
            logD(TAG, "## BufferRect = ${bufferRect.toShortString()}")
            logD(TAG, "## FinderRect = ${finderRect.toShortString()}")
        }
        val centerX = finderRect.centerX()
        val centerY = finderRect.centerY()

        // Aspect consideration.
        val bufferAspect = if (bufferRect.height() < bufferRect.width()) {
            bufferRect.width() / bufferRect.height()
        } else {
            bufferRect.height() / bufferRect.width()
        }
        val finderAspect = if (finderRect.height() < finderRect.width()) {
            finderRect.width() / finderRect.height()
        } else {
            finderRect.height() / finderRect.width()
        }
        if (IS_DEBUG) {
            logD(TAG, "## BufferAspect = $bufferAspect")
            logD(TAG, "## FinderAspect = $finderAspect")
        }

        when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (IS_DEBUG) logD(TAG, "#### ROTATION_0,180")

                // Align aspect.
                if (bufferAspect < finderAspect) {
                    if (IS_DEBUG) logD(TAG, "## Scale vertical.")
                    matrix.postScale(
                            1.0f,
                            bufferAspect / finderAspect,
                            centerX,
                            centerY)

                } else {
                    if (IS_DEBUG) logD(TAG, "## Scale horizontal.")
                    matrix.postScale(
                            finderAspect / bufferAspect,
                            1.0f,
                            centerX,
                            centerY)
                }

                matrix.postRotate(90f * rotation, centerX, centerY)
            }

            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (IS_DEBUG) logD(TAG, "#### ROTATION_90,270")

                // Convert landscape/portrait.
                val finderLand = RectF(0f, 0f, finderRect.width(), finderRect.height())
                val finderPort = RectF(0f, 0f, finderRect.height(), finderRect.width())
                finderPort.offset(centerX - finderPort.centerX(), centerY - finderPort.centerY())
                matrix.setRectToRect(finderLand, finderPort, Matrix.ScaleToFit.FILL)

                // Align aspect.
                if (bufferAspect < finderAspect) {
                    if (IS_DEBUG) logD(TAG, "## Scale vertical.")
                    matrix.postScale(
                            1.0f,
                            bufferAspect / finderAspect,
                            centerX,
                            centerY)

                } else {
                    if (IS_DEBUG) logD(TAG, "## Scale portrait.")
                    matrix.postScale(
                            finderAspect / bufferAspect,
                            1.0f,
                            centerX,
                            centerY)
                }

                // Correct rotation.
                matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }

            else -> throw IllegalStateException("Rotation is not valid.")
        }

        // Check aspect.
        if ((bufferAspect * 100).toInt() != (finderAspect * 100).toInt()) {
            if (IS_DEBUG) logD(TAG, "#### Aspect is not matched")
            // Not matched.

            if (bufferAspect < finderAspect) {
                // Black area is available on right and left based on buffer coordinates.
                if (IS_DEBUG) logD(TAG, "## Fit buffer right and left to finder.")

                matrix.postScale(
                        finderAspect / bufferAspect,
                        finderAspect / bufferAspect,
                        centerX,
                        centerY)
            } else {
                // Black area is available on top and bottom based on buffer coordinates.
                if (IS_DEBUG) logD(TAG, "## Fit buffer top and bottom to finder")

                matrix.postScale(
                        bufferAspect / finderAspect,
                        bufferAspect / finderAspect,
                        centerX,
                        centerY)
            }
        }

        if (IS_DEBUG) logD(TAG, "getTextureViewTransformMatrix() : X")
        return matrix
    }

    /**
     * Get optimal preview stream frame size.
     *
     * @param charas Camera characteristics.
     * @return Preview stream frame buffer size.
     */
    fun getPreviewStreamFrameSize(charas: CameraCharacteristics): Size {
        if (IS_DEBUG) logD(TAG, "getPreviewStreamFrameSize()")

        // Sensor aspect.
        val fullSize = charas.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)!!
        val fullAspectWH = fullSize.width.toFloat() / fullSize.height.toFloat()
        val activeSize = charas.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
        val activeAspectWH = activeSize.width().toFloat() / activeSize.height().toFloat()
        if (IS_DEBUG) {
            logD(TAG, "## PixelArraySize = $fullSize")
            logD(TAG, "## Full Size Aspect = $fullAspectWH")
            logD(TAG, "## ActiveArraySize = $activeSize")
            logD(TAG, "## Active Size Aspect = $activeAspectWH")
        }

        // Supported size.
        val configMap = charas.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val sizes = configMap.getOutputSizes(SurfaceTexture::class.java)

        // Match aspect.
        var optimalSize = Size(Integer.MAX_VALUE, Integer.MAX_VALUE)
        sizes.forEach { size ->
            if (IS_DEBUG) logD(TAG, "#### EachSize = $size")

            // Minimum resolution is FHD.
            if (size.height < 1080) {
                logD(TAG, "## Skip this size, too small.")
                return@forEach
            }

            // Smaller size is better for performance.
            // More square shape is more wide view port.
            if ( (size.height < optimalSize.height)
                    || (size.height == optimalSize.height && size.width < optimalSize.width) ){
                if (IS_DEBUG) logD(TAG, "## Update optimal size")
                optimalSize = size
            }
        }

        if (IS_DEBUG) logD(TAG, "#### PreviewFrameSize = $optimalSize")
        return optimalSize
    }

    /**
     * Get maximum JPEG frame size.
     *
     * @param map Stream configuration map.
     * @param devicePreviewSize Camera device preview size.
     * @return Optimal JPEG frame size.
     */
    fun getOptimalJpegFrameSize(map: StreamConfigurationMap, devicePreviewSize: Size): Size {
        if (IS_DEBUG) logD(TAG, "getOptimalJpegFrameSize()")

        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)

        val previewAspectRatioWHx100 = (devicePreviewSize.width.toFloat() / devicePreviewSize.height.toFloat() * 100.0f).toInt()
        if (IS_DEBUG) logD(TAG, "## PreviewAspect = $previewAspectRatioWHx100")

        val capableSizes = ArrayList<Size>()
        jpegSizes.forEach { jpegSize ->
            val aspectWHx100 = (jpegSize.width.toFloat() / jpegSize.height.toFloat() * 100.0f).toInt()
            if (IS_DEBUG) logD(TAG, "## EachAspect = $aspectWHx100")

            if (previewAspectRatioWHx100 == aspectWHx100) {
                // Aspect matched.
                if (IS_DEBUG) logD(TAG, "## Capable = $jpegSize")
                capableSizes.add(jpegSize)
            }
        }

        // Check xperia recommended.
        var maxSize = Size(0, 0)
        capableSizes.forEach { capableSize ->
            if (capableSize.width == 3840 && capableSize.height == 2160) {
                // 8MP 16:9
                if (IS_DEBUG) logD(TAG, "## Recommended 8MP 16:9")
                maxSize = capableSize
                return@forEach
            }
            if (capableSize.width == 3264 && capableSize.height == 2448) {
                // 8MP 4:3
                if (IS_DEBUG) logD(TAG, "## Recommended 8MP 4:3")
                maxSize = capableSize
                return@forEach
            }

            // Larger is better.
            if (maxSize.width < capableSize.width) {
                maxSize = capableSize
            }
        }

        if (IS_DEBUG) logD(TAG, "## JPEG Size = $maxSize")
        return maxSize
    }

    /**
     * Get crop size based on active array size.
     *
     * @param charas Camera characteristics.
     * @param aspectWH Aspect ratio.
     * @return Aspect considered scaler crop region.
     */
    fun getAspectConsideredScalerCropRegion(
            charas: CameraCharacteristics,
            aspectWH: Float): Rect {
        // Full resolution size.
        val fullRect = charas.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
        val fullAspectWH = fullRect.width().toFloat() / fullRect.height().toFloat()
        // Max zoom.
        val maxZoom = charas.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!

        if (IS_DEBUG) {
            logD(TAG, "## ActiveArraySize = ${fullRect}, w=${fullRect.width()}, h=${fullRect.height()}")
            logD(TAG, "## MAX Zoom = $maxZoom")
        }

        // Crop rect.
        val cropWidth: Int
        val cropHeight: Int

        if (aspectWH < fullAspectWH) {
            // Full resolution is wider than requested aspect.

            cropHeight = fullRect.height()
            cropWidth = (cropHeight * aspectWH).toInt()
        } else {
            // Full resolution is thinner than request aspect.

            cropWidth = fullRect.width()
            cropHeight = (cropWidth / aspectWH).toInt()
        }

        if (IS_DEBUG) logD(TAG, "## CropSize = $cropWidth x $cropHeight")

        val cropRect = Rect(
                (fullRect.width() - cropWidth) / 2,
                (fullRect.height() - cropHeight) / 2,
                (fullRect.width() - cropWidth) / 2 + cropWidth,
                (fullRect.height() - cropHeight) / 2 + cropHeight)
        cropRect.offset(
                fullRect.centerX() - cropRect.centerX(),
                fullRect.centerY() - cropRect.centerY())

        if (IS_DEBUG) logD(TAG, "## CropRect = $cropRect, w=${cropRect.width()}, h=${cropRect.height()}")

        return cropRect
    }

    /**
     * AF function is available or not.
     *
     * @param result Capture result.
     * @return AF is available or not.
     */
    fun isAfAvailable(result: CaptureResult): Boolean {
        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        return afState != null
    }

    /**
     * AF is locked or not.
     *
     * @param result Capture result.
     * @return AF locked or not.
     */
    fun isAfLocked(result: CaptureResult): Boolean {
        val afState = result.get(CaptureResult.CONTROL_AF_STATE)!!

        return afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureRequest.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
    }

    /**
     * AF is successfully focused or not.
     *
     * @param result Capture result.
     * @return AF is success or not.
     */
    fun isAfSucceeded(result: CaptureResult): Boolean {
        val afState = result.get(CaptureResult.CONTROL_AF_STATE)!!
        return afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED
    }

    /**
     * AE function is available or not.
     *
     * @param result Capture result.
     * @return AE is available or not.
     */
    fun isAeAvailable(result: CaptureResult): Boolean {
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        return aeState != null
    }

    /**
     * AE is locked or not.
     *
     * @param result Capture result.
     * @return AE is locked or not.
     */
    fun isAeLocked(result: CaptureResult): Boolean {
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)!!
        return aeState == CaptureRequest.CONTROL_AE_STATE_LOCKED
    }
}
