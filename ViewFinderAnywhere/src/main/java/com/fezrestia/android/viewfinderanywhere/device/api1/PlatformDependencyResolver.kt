@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.device.api1

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.view.Surface
import android.view.WindowManager

import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAspect

import java.util.HashSet
import java.util.Objects
import kotlin.math.abs

internal object PlatformDependencyResolver {
    // Log tag.
    private const val TAG = "PlatformDependencyResolver"

    // Platform specifications.
    private val PREFERRED_PREVIEW_SIZE_FOR_STILL = Size(1280, 720)

    // Aspect ratio clearance.
    private const val ASPECT_RATIO_CLEARANCE = 0.01f

    /**
     * TODO:Replace this to android.util.Size after L.
     *
     * @constructor
     * @param width Frame width.
     * @param height Frame height.
     */
    internal class Size(val width: Int, val height: Int) {
        override fun toString(): String {
            val builder = StringBuilder()
                    .append(width)
                    .append("x")
                    .append(height)
            return builder.toString()
        }
    }

    /**
     * Get optimal preview size for still picture.
     *
     * @param requiredAspectRatioWH Required aspect ratio.
     * @param supportedPreviewSizeSet Supported preview sizes.
     * @return Optimal preview size for picture.
     */
    fun getOptimalPreviewSizeForStill(
            requiredAspectRatioWH: Float,
            supportedPreviewSizeSet: Set<Size>): Size {
        if (IS_DEBUG) logD(TAG, "getOptimalPreviewSize() : E")

        // Check supported.
        require(supportedPreviewSizeSet.isNotEmpty()) { "Supported size set is empty." }

        // Estimate aspect.
        val estimatedAspectWH: Float
        val diffTo43 = abs(ViewFinderAspect.WH_4_3.ratioWH - requiredAspectRatioWH)
        val diffTo169 = abs(ViewFinderAspect.WH_16_9.ratioWH - requiredAspectRatioWH)
        estimatedAspectWH = if (diffTo43 < diffTo169) {
            // Near to 4:3.
            ViewFinderAspect.WH_4_3.ratioWH
        } else {
            // Near to 16:9.
            ViewFinderAspect.WH_16_9.ratioWH
        }
        if (IS_DEBUG) logD(TAG, "###### Estimated aspect = $estimatedAspectWH")

        // Check aspect ratio.
        val aspectAcceptable = HashSet<Size>()
        for (eachSize in supportedPreviewSizeSet) {
            val aspect = eachSize.width.toFloat() / eachSize.height.toFloat()
            if (abs(estimatedAspectWH - aspect) < ASPECT_RATIO_CLEARANCE) {
                // Valid.
                aspectAcceptable.add(eachSize)

                if (IS_DEBUG) logD(TAG, "Aspect acceptable : $eachSize")
            }
        }

        // Check MAX size.
        var acceptableMaxSize: Size? = null
        for (eachSize in aspectAcceptable) {
            if (PREFERRED_PREVIEW_SIZE_FOR_STILL.width < eachSize.width
                    || PREFERRED_PREVIEW_SIZE_FOR_STILL.height < eachSize.height) {
                // Too large.
                continue
            }

            if (acceptableMaxSize == null) {
                acceptableMaxSize = eachSize
            } else {
                if (acceptableMaxSize.width < eachSize.width) {
                    acceptableMaxSize = eachSize
                }
            }

            if (IS_DEBUG) logD(TAG, "Size acceptable : $eachSize")
        }

        // Check result.
        val ret = acceptableMaxSize ?: supportedPreviewSizeSet.single()

        if (IS_DEBUG) {
            logD(TAG, "Result : $ret")
            logD(TAG, "getOptimalPreviewSize() : X")
        }
        return ret
    }

    /**
     * Get optimal picture size.
     *
     * @param requiredAspectRatioWH Required aspect ratio.
     * @param supportedPictureSizeSet Supported picture sizes.
     * @return Optimal picture size.
     */
    fun getOptimalPictureSize(
            requiredAspectRatioWH: Float,
            supportedPictureSizeSet: Set<Size>): Size {
        if (IS_DEBUG) logD(TAG, "getOptimalPictureSize() : E")

        // Check supported.
        require(supportedPictureSizeSet.isNotEmpty()) { "Supported size set is empty." }

        // Estimate aspect.
        val estimatedAspectWH: Float
        val diffTo43 = abs(ViewFinderAspect.WH_4_3.ratioWH - requiredAspectRatioWH)
        val diffTo169 = abs(ViewFinderAspect.WH_16_9.ratioWH - requiredAspectRatioWH)
        estimatedAspectWH = if (diffTo43 < diffTo169) {
            // Near to 4:3.
            ViewFinderAspect.WH_4_3.ratioWH
        } else {
            // Near to 16:9.
            ViewFinderAspect.WH_16_9.ratioWH
        }
        if (IS_DEBUG) logD(TAG, "###### Estimated aspect = $estimatedAspectWH")

        // Check aspect ratio.
        val aspectAcceptable = HashSet<Size>()
        for (eachSize in supportedPictureSizeSet) {
            val aspect = eachSize.width.toFloat() / eachSize.height.toFloat()
            if (abs(estimatedAspectWH - aspect) < ASPECT_RATIO_CLEARANCE) {
                // Valid.
                aspectAcceptable.add(eachSize)

                if (IS_DEBUG) logD(TAG, "Aspect acceptable : $eachSize")
            }
        }

        // Check xperia recommended.
        var maxSize = Size(0, 0)
        for (eachSize in aspectAcceptable) {
            if (eachSize.width == 3840 && eachSize.height == 2160) {
                // 8MP 16:9
                if (IS_DEBUG) logD(TAG, "######   Recommended 8MP 16:9")
                maxSize = eachSize
                break
            }
            if (eachSize.width == 3264 && eachSize.height == 2448) {
                // 8MP 4:3
                if (IS_DEBUG) logD(TAG, "######   Recommended 8MP 4:3")
                maxSize = eachSize
                break
            }

            // Larger is better.
            if (maxSize.width < eachSize.width) {
                maxSize = eachSize
            }
        }

        // Check result.
        val ret = if (maxSize.width != 0) maxSize else supportedPictureSizeSet.single()

        if (IS_DEBUG) {
            logD(TAG, "Result : $ret")
            logD(TAG, "getOptimalPictureSize() : X")
        }
        return ret
    }

    /**
     * Get transform matrix for TextureView preview stream.
     *
     * @param context Context.
     * @param bufferWidth Frame buffer width.
     * @param bufferHeight Frame buffer height.
     * @param finderWidth Finder width.
     * @param finderHeight Finder height.
     * @return Transform matrix.
     */
    fun getTextureViewTransformMatrix(
            context: Context,
            bufferWidth: Int,
            bufferHeight: Int,
            finderWidth: Int,
            finderHeight: Int): Matrix {
        if (IS_DEBUG) logD(TAG, "getTextureViewTransformMatrix() : E")

        // Display rotation.
        val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = Objects.requireNonNull(winMng).defaultDisplay.rotation
        if (IS_DEBUG) logD(TAG, "## rotation = $rotation")

        val matrix = Matrix()
        matrix.reset()

        val bufferRect = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
        val finderRect = RectF(0f, 0f, finderWidth.toFloat(), finderHeight.toFloat())
        if (IS_DEBUG) {
            logD(TAG, "## BufferRect = " + bufferRect.toShortString())
            logD(TAG, "## FinderRect = " + finderRect.toShortString())
        }
        val centerX = finderRect.centerX()
        val centerY = finderRect.centerY()

        // Aspect consideration.
        val bufferAspect: Float
        bufferAspect = when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                bufferRect.height() / bufferRect.width()
            }

            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                bufferRect.width() / bufferRect.height()
            }

            else -> throw IllegalStateException("Rotation is not valid.")
        }
        val finderAspect = finderRect.width() / finderRect.height()
        if (IS_DEBUG) {
            logD(TAG, "## BufferAspect = $bufferAspect")
            logD(TAG, "## FinderAspect = $finderAspect")
        }

        // Check aspect.
        if ((bufferAspect * 100).toInt() != (finderAspect * 100).toInt()) {
            if (IS_DEBUG) logD(TAG, "#### Aspect is not matched")
            // Not matched.

            if (bufferAspect < finderAspect) {
                // Black area is available on right and left based on buffer coordinates.
                if (IS_DEBUG) logD(TAG, "## Fit buffer right and left to finder.")

                matrix.postScale(
                        1.0f,
                        finderAspect / bufferAspect,
                        centerX,
                        centerY)
            } else {
                // Black area is available on top and bottom based on buffer coordinates.
                if (IS_DEBUG) logD(TAG, "## Fit buffer top and bottom to finder")

                matrix.postScale(
                        bufferAspect / finderAspect,
                        1.0f,
                        centerX,
                        centerY)
            }
        }

        if (IS_DEBUG) logD(TAG, "getTextureViewTransformMatrix() : X")
        return matrix
    }
}
