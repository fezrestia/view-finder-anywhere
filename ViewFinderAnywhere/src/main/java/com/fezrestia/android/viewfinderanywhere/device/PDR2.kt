@file:Suppress("MayBeConstant")

package com.fezrestia.android.viewfinderanywhere.device

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.config.ViewFinderAspect

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.ArrayList
import kotlin.math.abs

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
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ID = $id")

            val charas = camMng.getCameraCharacteristics(id)

            if (Log.IS_DEBUG) {
                Log.logError(TAG, "#### AVAILABLE KEYS")
                charas.keys.forEach { key ->
                    Log.logError(TAG, "## KEY = $key")
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
        Log.logError(TAG, "#### SUPPORTED IMAGE FORMATS")
        val formats = streamConfigMap.outputFormats
        for (format in formats) {
            when (format) {
                ImageFormat.DEPTH16             -> Log.logError(TAG, "## FORMAT = DEPTH16")
                ImageFormat.DEPTH_POINT_CLOUD   -> Log.logError(TAG, "## FORMAT = DEPTH_POINT_CLOUD")
                ImageFormat.FLEX_RGBA_8888      -> Log.logError(TAG, "## FORMAT = FLEX_RGBA_8888")
                ImageFormat.FLEX_RGB_888        -> Log.logError(TAG, "## FORMAT = FLEX_RGB_888")
                ImageFormat.JPEG                -> Log.logError(TAG, "## FORMAT = JPEG")
                ImageFormat.NV16                -> Log.logError(TAG, "## FORMAT = NV16")
                ImageFormat.NV21                -> Log.logError(TAG, "## FORMAT = NV21")
                ImageFormat.PRIVATE             -> Log.logError(TAG, "## FORMAT = PRIVATE")
                ImageFormat.RAW10               -> Log.logError(TAG, "## FORMAT = RAW10")
                ImageFormat.RAW12               -> Log.logError(TAG, "## FORMAT = RAW12")
                ImageFormat.RAW_SENSOR          -> Log.logError(TAG, "## FORMAT = RAW_SENSOR")
                ImageFormat.RGB_565             -> Log.logError(TAG, "## FORMAT = RGB_565")
                ImageFormat.UNKNOWN             -> Log.logError(TAG, "## FORMAT = UNKNOWN")
                ImageFormat.YUV_420_888         -> Log.logError(TAG, "## FORMAT = YUV_420_888")
                ImageFormat.YUV_422_888         -> Log.logError(TAG, "## FORMAT = YUV_422_888")
                ImageFormat.YUV_444_888         -> Log.logError(TAG, "## FORMAT = YUV_444_888")
                ImageFormat.YUY2                -> Log.logError(TAG, "## FORMAT = YUY2")
                ImageFormat.YV12                -> Log.logError(TAG, "## FORMAT = YV12")
                else                            -> Log.logError(TAG, "## FORMAT = default : $format")
            }

            val outputSizes = streamConfigMap.getOutputSizes(format)
            outputSizes.forEach { size ->
                Log.logError(TAG, "## SIZE = $size")
            }
        }
    }

    fun logSurfaceTextureOutputSizes(configMap: StreamConfigurationMap) {
        Log.logError(TAG, "#### SurfaceTexture OUTPUT SIZES")
        configMap.getOutputSizes(SurfaceTexture::class.java).forEach { size ->
            Log.logError(TAG, "## SIZE = $size")
        }
    }

    fun logAfTrigger(afTrigger: Int?) {
        if (afTrigger == null) {
            Log.logError(TAG, "#### AF Trigger == NULL")
        } else when (afTrigger) {
            CaptureResult.CONTROL_AF_TRIGGER_CANCEL -> Log.logError(TAG, "## AF Trigger == CANCEL")
            CaptureResult.CONTROL_AF_TRIGGER_IDLE   -> Log.logError(TAG, "## AF Trigger == IDLE")
            CaptureResult.CONTROL_AF_TRIGGER_START  -> Log.logError(TAG, "## AF Trigger == START")
            else                                    -> Log.logError(TAG, "## AF Trigger == default")
        }
    }

    fun logAfState(afState: Int?) {
        if (afState == null) {
            Log.logError(TAG, "#### AF State == NULL")
        } else when (afState) {
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN          -> Log.logError(TAG, "## AF State == ACTIVE_SCAN")
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED       -> Log.logError(TAG, "## AF State == FOCUSED_LOCKED")
            CaptureResult.CONTROL_AF_STATE_INACTIVE             -> Log.logError(TAG, "## AF State == INACTIVE")
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED   -> Log.logError(TAG, "## AF State == NOT_FOCUSED_LOCKED")
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED      -> Log.logError(TAG, "## AF State == PASSIVE_FOCUSED")
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN         -> Log.logError(TAG, "## AF State == PASSIVE_SCAN")
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED    -> Log.logError(TAG, "## AF State == PASSIVE_UNFOCUSED")
            else                                                -> Log.logError(TAG, "## AF State == default")
        }
    }

    fun logAeTrigger(aeTrigger: Int?) {
        if (aeTrigger == null) {
            Log.logError(TAG, "#### AE Trigger == NULL")
        } else when (aeTrigger) {
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL  -> Log.logError(TAG, "## AE Trigger == CANCEL")
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE    -> Log.logError(TAG, "## AE Trigger == IDLE")
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_START   -> Log.logError(TAG, "## AE Trigger == START")
            else                                                -> Log.logError(TAG, "## AE Trigger == default")
        }
    }

    fun logAeState(aeState: Int?) {
        if (aeState == null) {
            Log.logError(TAG, "#### AE State == NULL")
        } else when (aeState) {
            CaptureResult.CONTROL_AE_STATE_CONVERGED        -> Log.logError(TAG, "## AE State == CONVERGED")
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED   -> Log.logError(TAG, "## AE State == FLASH_REQUIRED")
            CaptureResult.CONTROL_AE_STATE_INACTIVE         -> Log.logError(TAG, "## AE State == INACTIVE")
            CaptureResult.CONTROL_AE_STATE_LOCKED           -> Log.logError(TAG, "## AE State == LOCKED")
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE       -> Log.logError(TAG, "## AE State == PRECAPTURE")
            CaptureResult.CONTROL_AE_STATE_SEARCHING        -> Log.logError(TAG, "## AE State == SEARCHING")
            else                                            -> Log.logError(TAG, "## AE State == default")
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
            return 0
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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getTextureViewTransformMatrix() : E")

        // Display rotation.
        val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = winMng.defaultDisplay.rotation

        val matrix = Matrix()
        matrix.reset()
        val bufferRect = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
        val finderRect = RectF(0f, 0f, finderWidth.toFloat(), finderHeight.toFloat())
        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## BufferRect = ${bufferRect.toShortString()}")
            Log.logDebug(TAG, "## FinderRect = ${finderRect.toShortString()}")
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
        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## BufferAspect = $bufferAspect")
            Log.logDebug(TAG, "## FinderAspect = $finderAspect")
        }

        when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "#### ROTATION_0,180")

                // Align aspect.
                if (bufferAspect < finderAspect) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## Scale vertical.")
                    matrix.postScale(
                            1.0f,
                            bufferAspect / finderAspect,
                            centerX,
                            centerY)

                } else {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## Scale horizontal.")
                    matrix.postScale(
                            finderAspect / bufferAspect,
                            1.0f,
                            centerX,
                            centerY)
                }

                matrix.postRotate(90f * rotation, centerX, centerY)
            }

            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "#### ROTATION_90,270")

                // Convert landscape/portrait.
                val finderLand = RectF(0f, 0f, finderRect.width(), finderRect.height())
                val finderPort = RectF(0f, 0f, finderRect.height(), finderRect.width())
                finderPort.offset(centerX - finderPort.centerX(), centerY - finderPort.centerY())
                matrix.setRectToRect(finderLand, finderPort, Matrix.ScaleToFit.FILL)

                // Align aspect.
                if (bufferAspect < finderAspect) {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## Scale vertical.")
                    matrix.postScale(
                            1.0f,
                            bufferAspect / finderAspect,
                            centerX,
                            centerY)

                } else {
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "## Scale portrait.")
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
            if (Log.IS_DEBUG) Log.logDebug(TAG, "#### Aspect is not matched")
            // Not matched.

            if (bufferAspect < finderAspect) {
                // Black area is available on right and left based on buffer coordinates.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Fit buffer right and left to finder.")

                matrix.postScale(
                        finderAspect / bufferAspect,
                        finderAspect / bufferAspect,
                        centerX,
                        centerY)
            } else {
                // Black area is available on top and bottom based on buffer coordinates.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Fit buffer top and bottom to finder")

                matrix.postScale(
                        bufferAspect / finderAspect,
                        bufferAspect / finderAspect,
                        centerX,
                        centerY)
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "getTextureViewTransformMatrix() : X")
        return matrix
    }

    /**
     * Get optimal preview stream frame size.
     *
     * @param charas Camera characteristics.
     * @return Preview stream frame buffer size.
     */
    // TODO: Consider display size ?
    fun getPreviewStreamFrameSize(charas: CameraCharacteristics): Size {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getPreviewStreamFrameSize()")

        // Sensor aspect.
        val fullSize = charas.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)!!
        var fullAspectWH = fullSize.width.toFloat() / fullSize.height.toFloat()
        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## PixelArraySize = $fullSize")
            Log.logDebug(TAG, "## Full Size Aspect = $fullAspectWH")
        }

        // Estimate full aspect.
        val diffTo43 = abs(ViewFinderAspect.WH_4_3.ratioWH - fullAspectWH)
        val diffTo169 = abs(ViewFinderAspect.WH_16_9.ratioWH - fullAspectWH)
        fullAspectWH = if (diffTo43 < diffTo169) {
            // Near to 4:3.
            ViewFinderAspect.WH_4_3.ratioWH
        } else {
            // Near to 16:9.
            ViewFinderAspect.WH_16_9.ratioWH
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Estimated full Size Aspect = $fullAspectWH")

        // Supported size.
        val configMap = charas.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val sizes = configMap.getOutputSizes(SurfaceTexture::class.java)

        // Match aspect.
        var optimalSize = Size(0, 0)
        sizes.forEach { size ->
            if (Log.IS_DEBUG) Log.logDebug(TAG, "#### EachSize = $size")

            //TODO: Consider display size ?
            if (1920 < size.width) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Ignore. Over than 1920")
                // NOP.
            } else {
                // Target aspect.
                val eachAspectWH = size.width.toFloat() / size.height.toFloat()
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Each Aspect = $eachAspectWH")

                if ((fullAspectWH * 100).toInt() == (eachAspectWH * 100).toInt()) {
                    if (optimalSize.width < size.width) {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Update optimal size")
                        optimalSize = size
                    }
                }
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "#### PreviewFrameSize = $optimalSize")
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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalJpegFrameSize()")

        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)

        val previewAspectRatioWHx100 = (devicePreviewSize.width.toFloat() / devicePreviewSize.height.toFloat() * 100.0f).toInt()
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## PreviewAspect = $previewAspectRatioWHx100")

        val capableSizes = ArrayList<Size>()
        jpegSizes.forEach { jpegSize ->
            val aspectWHx100 = (jpegSize.width.toFloat() / jpegSize.height.toFloat() * 100.0f).toInt()
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## EachAspect = $aspectWHx100")

            if (previewAspectRatioWHx100 == aspectWHx100) {
                // Aspect matched.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Capable = $jpegSize")
                capableSizes.add(jpegSize)
            }
        }

        // Check xperia recommended.
        var maxSize = Size(0, 0)
        capableSizes.forEach { capableSize ->
            if (capableSize.width == 3840 && capableSize.height == 2160) {
                // 8MP 16:9
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Recommended 8MP 16:9")
                maxSize = capableSize
                return@forEach
            }
            if (capableSize.width == 3264 && capableSize.height == 2448) {
                // 8MP 4:3
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Recommended 8MP 4:3")
                maxSize = capableSize
                return@forEach
            }

            // Larger is better.
            if (maxSize.width < capableSize.width) {
                maxSize = capableSize
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "## JPEG Size = $maxSize")
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

        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## ActiveArraySize = ${fullRect.toShortString()}")
            Log.logDebug(TAG, "## MAX Zoom = $maxZoom")
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

        if (Log.IS_DEBUG) Log.logDebug(TAG, "## CropSize = $cropWidth x $cropHeight")

        val cropRect = Rect(
                (fullRect.width() - cropWidth) / 2,
                (fullRect.height() - cropHeight) / 2,
                (fullRect.width() - cropWidth) / 2 + cropWidth,
                (fullRect.height() - cropHeight) / 2 + cropHeight)
        cropRect.offset(
                fullRect.centerX() - cropRect.centerX(),
                fullRect.centerY() - cropRect.centerY())

        if (Log.IS_DEBUG) Log.logDebug(TAG, "## CropRect = ${cropRect.toShortString()}")

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

    /**
     * Crop and rotate JPEG frame.
     *
     * @param srcJpeg JPEG frame buffer.
     * @param rotation Get from getFrameOrientation()
     * @param cropAspectWH Must be greater than 1.0f.
     * @param jpegQuality ResultJPEG quality
     * @return Result JPEG frame buffer.
     */
    @JvmStatic
    fun doCropRotJpeg(
            srcJpeg: ByteArray,
            rotation: Int,
            cropAspectWH: Float,
            jpegQuality: Int): ByteArray {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "doCropRotJpeg() : E")

        // Create source bitmap.
        val srcBmp = BitmapFactory.decodeByteArray(srcJpeg, 0, srcJpeg.size)
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Decode Src Bitmap : DONE")

        // Create rotated source bitmap.
        val rotator = Matrix()
        rotator.postRotate(rotation.toFloat())
        val rotSrcBmp = Bitmap.createBitmap(
                srcBmp,
                0,
                0,
                srcBmp.width,
                srcBmp.height,
                rotator,
                true)
        if (srcBmp.hashCode() != rotSrcBmp.hashCode()) {
            srcBmp.recycle()
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Create Rot Src Bitmap : DONE")

        // Create aspect considered bitmap.
        val w = rotSrcBmp.width.toFloat()
        val h = rotSrcBmp.height.toFloat()
        val srcAspectWH = w / h
        val dstAspectWH: Float
        dstAspectWH = if (1.0f < srcAspectWH) {
            // Horizontal.
            cropAspectWH
        } else {
            // Vertical.
            1.0f / cropAspectWH
        }

        val dstRect: Rect
        if ((srcAspectWH * 100).toInt() == (dstAspectWH * 100).toInt()) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "## Src/Dst aspect is same.")
            // Already same aspect.

            dstRect = Rect(0, 0, w.toInt(), h.toInt())
        } else {
            if (srcAspectWH < dstAspectWH) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Cut off top and bottom.")
                // Cut off top and bottom.

                val dstH = w / dstAspectWH
                dstRect = Rect(
                        0,
                        ((h - dstH) / 2.0f).toInt(),
                        w.toInt(),
                        ((h - dstH) / 2.0f + dstH).toInt())
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Cut off left and right.")
                // Cut off left and right.

                val dstW = h * dstAspectWH
                dstRect = Rect(
                        ((w - dstW) / 2.0f).toInt(),
                        0,
                        ((w - dstW) / 2.0f + dstW).toInt(),
                        h.toInt())
            }
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Calculate DstRect : DONE")
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## DstRect = ${dstRect.toShortString()}")

        // Create destination bitmap.
        val dstBmp = Bitmap.createBitmap(
                rotSrcBmp,
                dstRect.left,
                dstRect.top,
                dstRect.width(),
                dstRect.height())
        if (rotSrcBmp.hashCode() != dstBmp.hashCode()) {
            rotSrcBmp.recycle()
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## Create Dst Bitmap : DONE")

        // JPEG Encode.
        val os = ByteArrayOutputStream()
        dstBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, os)
        val resultJpeg = os.toByteArray()
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## JPEG Encode : DONE")

        // Release.
        try {
            os.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (!srcBmp.isRecycled) {
            srcBmp.recycle()
        }
        if (!rotSrcBmp.isRecycled) {
            rotSrcBmp.recycle()
        }
        if (!dstBmp.isRecycled) {
            dstBmp.recycle()
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "doCropRotJpeg() : X")
        return resultJpeg
    }
}
