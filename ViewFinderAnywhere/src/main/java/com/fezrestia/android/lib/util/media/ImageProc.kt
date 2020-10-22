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

        // Create rotated bitmap.
        val rotator = Matrix()
        rotator.postRotate(rotation.toFloat())
        val rotBmp = Bitmap.createBitmap(
                srcBmp,
                0,
                0,
                srcBmp.width,
                srcBmp.height,
                rotator,
                true)
        val rotW = rotBmp.width
        val rotH = rotBmp.height
        val rotAspectWH = rotW.toFloat() / rotH.toFloat()
        if (IS_DEBUG) {
            logD(TAG, "#### Create Rotated Bitmap : DONE")
            logD(TAG, "## rotW = $rotW")
            logD(TAG, "## rotH = $rotH")
            logD(TAG, "## rotAspectWH = $rotAspectWH")
        }

        // Calc crop rect.
        val cropRect: Rect? = if (isSameAspect(rotAspectWH, cropAspectWH)) {
            // Crop not necessary.
            null
        } else {
            getCropRect(rotW, rotH, cropAspectWH)
        }
        if (IS_DEBUG) logD(TAG, "## cropRect = $cropRect")

        // Create cropped bitmap.
        val cropBmp = if (cropRect != null) {
            Bitmap.createBitmap(
                    rotBmp,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width(),
                    cropRect.height())
        } else {
            // Crop not necessary.
            rotBmp
        }
        if (IS_DEBUG) {
            logD(TAG, "#### Create Cropped Bitmap : DONE")
            logD(TAG, "## cropBmp.width  = ${cropBmp.width}")
            logD(TAG, "## cropBmp.height = ${cropBmp.height}")
        }

        // JPEG Encode.
        val os = ByteArrayOutputStream()
        cropBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, os)
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
        if (!rotBmp.isRecycled) {
            rotBmp.recycle()
        }
        if (!cropBmp.isRecycled) {
            cropBmp.recycle()
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
