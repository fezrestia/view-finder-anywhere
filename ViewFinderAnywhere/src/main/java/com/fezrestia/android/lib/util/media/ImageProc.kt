@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.lib.util.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import java.io.ByteArrayOutputStream
import java.io.IOException

object ImageProc {
    private const val TAG = "ImageProc"

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
        if (IS_DEBUG) {
            logD(TAG, "doCropRotJpeg() : E")
            logD(TAG, "#### ARGs")
            logD(TAG, "## rotation = $rotation")
            logD(TAG, "## cropAspectWH = $cropAspectWH")
            logD(TAG, "## jpegQuality = $jpegQuality")
        }

        // Src JPEG size.
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(srcJpeg, 0, srcJpeg.size, options)
        val srcW = options.outWidth
        val srcH = options.outHeight
        val srcMime = options.outMimeType
        val srcColor = options.outColorSpace
        val srcConfig = options.outConfig
        val srcAspectWH = srcW.toFloat() / srcH.toFloat()
        if (IS_DEBUG) {
            logD(TAG, "#### SRC bitmap params")
            logD(TAG, "## srcW = $srcW")
            logD(TAG, "## srcH = $srcH")
            logD(TAG, "## srcMime = $srcMime")
            logD(TAG, "## srcColor = $srcColor")
            logD(TAG, "## srcConfig = $srcConfig")
            logD(TAG, "## srcAspectWH = $srcAspectWH")
        }

        // Create source bitmap.
        val srcBmp = BitmapFactory.decodeByteArray(srcJpeg, 0, srcJpeg.size)
        if (IS_DEBUG) logD(TAG, "#### Decode Src Bitmap : DONE")

        // Align aspect direction.
        val revisedCropAspectWH = if (
                (srcAspectWH < 1.0 && cropAspectWH < 1.0)
                || (1.0 < srcAspectWH && 1.0 < cropAspectWH)) {
            // Picture and crop is same direction.
            cropAspectWH
        } else {
            // Picture and crop is different direction. Align direction.
            1.0f / cropAspectWH
        }
        if (IS_DEBUG) logD(TAG, "## revisedCropAspectWH = $revisedCropAspectWH")

        // Calc crop rect.
        val cropRect: Rect? = if (isSameAspect(srcAspectWH, revisedCropAspectWH)) {
            // Crop not necessary.
            null
        } else {
            getCropRect(srcW, srcH, revisedCropAspectWH)
        }
        if (IS_DEBUG) logD(TAG, "## cropRect = $cropRect")

        // Create cropped bitmap.
        val cropBmp = if (cropRect != null) {
            Bitmap.createBitmap(
                    srcBmp,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width(),
                    cropRect.height())
        } else {
            // Crop not necessary.
            srcBmp
        }
        if (IS_DEBUG) {
            logD(TAG, "#### Create Cropped Bitmap : DONE")
            logD(TAG, "## cropBmp.width  = ${cropBmp.width}")
            logD(TAG, "## cropBmp.height = ${cropBmp.height}")
        }

        // Create rotated cropped bitmap, it is dst bitmap.
        val rotator = Matrix()
        rotator.postRotate(rotation.toFloat())
        val dstBmp = Bitmap.createBitmap(
                cropBmp,
                0,
                0,
                cropBmp.width,
                cropBmp.height,
                rotator,
                true)
        if (IS_DEBUG) {
            logD(TAG, "#### Create Cropped Rotated, Dst Bitmap : DONE")
            logD(TAG, "## dstBmp.width  = ${dstBmp.width}")
            logD(TAG, "## dstBmp.height = ${dstBmp.height}")
        }

        // JPEG Encode.
        val os = ByteArrayOutputStream()
        dstBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, os)
        val resultJpeg = os.toByteArray()
        if (IS_DEBUG) logD(TAG, "#### JPEG Encode : DONE")

        // Release.
        try {
            os.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (!srcBmp.isRecycled) {
            srcBmp.recycle()
        }
        if (!cropBmp.isRecycled) {
            cropBmp.recycle()
        }
        if (!dstBmp.isRecycled) {
            dstBmp.recycle()
        }

        if (IS_DEBUG) logD(TAG, "doCropRotJpeg() : X")
        return resultJpeg
    }

    private fun isSameAspect(aspectA: Float, aspectB: Float): Boolean
            = (aspectA * 100).toInt() == (aspectB * 100).toInt()

    @Suppress("UnnecessaryVariable")
    private fun getCropRect(srcW: Int, srcH: Int, cropAspectWH: Float): Rect {
        val srcAspectWH = srcW.toFloat() / srcH.toFloat()

        if (srcAspectWH < cropAspectWH) {
            // Crop top/bottom.

            val dstW = srcW
            val dstH = (dstW / cropAspectWH).toInt()
            val cropH = (srcH - dstH) / 2

            return Rect(0, cropH, dstW, cropH + dstH)
        } else {
            // Crop left/right.

            val dstH = srcH
            val dstW = (dstH * cropAspectWH).toInt()
            val cropW = (srcW - dstW) / 2

            return Rect(cropW, 0, cropW + dstW, dstH)
        }
    }
}
