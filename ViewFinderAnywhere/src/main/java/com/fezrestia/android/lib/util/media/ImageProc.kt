@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.lib.util.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import com.fezrestia.android.lib.util.log.Log
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
