package com.fezrestia.android.viewfinderanywhere.device.api1;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.Surface;
import android.view.WindowManager;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAspect;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class PlatformDependencyResolver {
    // Log tag.
    private static final String TAG = "PlatformDependencyResolver";

    // Platform specifications.
    private static final Size PREFERRED_PREVIEW_SIZE_FOR_STILL = new Size(1280, 720);

    // Aspect ratio clearance.
    private static final float ASPECT_RATIO_CLEARANCE = 0.01f;

    //TODO:Replace this to android.util.Size after L.
    static class Size {
        private final int mWidth;
        private final int mHeight;

        /**
         * CONSTRUCTOR.
         *
         * @param width Frame width.
         * @param height Frame height.
         */
        Size(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        int getWidth() {
            return mWidth;
        }

        int getHeight() {
            return mHeight;
        }

        @SuppressWarnings("StringBufferReplaceableByString")
        @NotNull
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder()
                    .append(mWidth)
                    .append("x")
                    .append(mHeight);
            return builder.toString();
        }
    }

    /**
     * Get optimal preview size for still picture.
     *
     * @param requiredAspectRatioWH Required aspect ratio.
     * @param supportedPreviewSizeSet Supported preview sizes.
     *
     * @return Optimal preview size for picture.
     */
    static Size getOptimalPreviewSizeForStill(
            final float requiredAspectRatioWH,
            final Set<Size> supportedPreviewSizeSet) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPreviewSize() : E");

        // Check supported.
        if (supportedPreviewSizeSet.isEmpty()) {
            throw new IllegalArgumentException("Supported size set is empty.");
        }

        // Estimate aspect.
        final float estimatedAspectWH;
        final float diffTo43 = Math.abs(
                ViewFinderAspect.WH_4_3.getRatioWH() - requiredAspectRatioWH);
        final float diffTo169 = Math.abs(
                ViewFinderAspect.WH_16_9.getRatioWH() - requiredAspectRatioWH);
        if (diffTo43 < diffTo169) {
            // Near to 4:3.
            estimatedAspectWH = ViewFinderAspect.WH_4_3.getRatioWH();
        } else {
            // Near to 16:9.
            estimatedAspectWH = ViewFinderAspect.WH_16_9.getRatioWH();
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Estimated aspect = " + estimatedAspectWH);

        // Check aspect ratio.
        Set<Size> aspectAcceptable = new HashSet<>();
        for (Size eachSize : supportedPreviewSizeSet) {
            final float aspect = ((float) eachSize.getWidth()) / ((float) eachSize.getHeight());
            if (Math.abs(estimatedAspectWH - aspect) < ASPECT_RATIO_CLEARANCE) {
                // Valid.
                aspectAcceptable.add(eachSize);

                if (Log.IS_DEBUG) Log.logDebug(TAG, "Aspect acceptable : " + eachSize.toString());
            }
        }

        // Check MAX size.
        Size acceptableMaxSize = null;
        for (Size eachSize : aspectAcceptable) {
            if (PREFERRED_PREVIEW_SIZE_FOR_STILL.getWidth() < eachSize.getWidth()
                    || PREFERRED_PREVIEW_SIZE_FOR_STILL.getHeight() < eachSize.getHeight()) {
                // Too large.
                continue;
            }

            if (acceptableMaxSize == null) {
                acceptableMaxSize = eachSize;
            } else {
                if (acceptableMaxSize.getWidth() < eachSize.getWidth()) {
                    acceptableMaxSize = eachSize;
                }
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "Size acceptable : " + eachSize.toString());
        }

        // Check result.
        Size ret;
        if (acceptableMaxSize != null) {
            ret = acceptableMaxSize;
        } else {
            ret = (Size) Objects.requireNonNull(supportedPreviewSizeSet.toArray())[0];
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "Result : " + ret.toString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPreviewSize() : X");
        return ret;
    }

    /**
     * Get optimal picture size.
     *
     * @param requiredAspectRatioWH Required aspect ratio.
     * @param supportedPictureSizeSet Supported picture sizes.
     *
     * @return Optimal picture size.
     */
    static Size getOptimalPictureSize(
            final float requiredAspectRatioWH,
            final Set<Size> supportedPictureSizeSet) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPictureSize() : E");

        // Check supported.
        if (supportedPictureSizeSet.isEmpty()) {
            throw new IllegalArgumentException("Supported size set is empty.");
        }

        // Estimate aspect.
        final float estimatedAspectWH;
        final float diffTo43 = Math.abs(
                ViewFinderAspect.WH_4_3.getRatioWH() - requiredAspectRatioWH);
        final float diffTo169 = Math.abs(
                ViewFinderAspect.WH_16_9.getRatioWH() - requiredAspectRatioWH);
        if (diffTo43 < diffTo169) {
            // Near to 4:3.
            estimatedAspectWH = ViewFinderAspect.WH_4_3.getRatioWH();
        } else {
            // Near to 16:9.
            estimatedAspectWH = ViewFinderAspect.WH_16_9.getRatioWH();
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "###### Estimated aspect = " + estimatedAspectWH);

        // Check aspect ratio.
        Set<Size> aspectAcceptable = new HashSet<>();
        for (Size eachSize : supportedPictureSizeSet) {
            final float aspect = ((float) eachSize.getWidth()) / ((float) eachSize.getHeight());
            if (Math.abs(estimatedAspectWH - aspect) < ASPECT_RATIO_CLEARANCE) {
                // Valid.
                aspectAcceptable.add(eachSize);

                if (Log.IS_DEBUG) Log.logDebug(TAG, "Aspect acceptable : " + eachSize.toString());
            }
        }

        // Check xperia recommended.
        Size maxSize = new Size(0, 0);
        for (Size eachSize : aspectAcceptable) {
            if (eachSize.getWidth() == 3840 && eachSize.getHeight() == 2160) {
                // 8MP 16:9
                if (Log.IS_DEBUG) Log.logDebug(TAG, "######   Recommended 8MP 16:9");
                maxSize = eachSize;
                break;
            }
            if (eachSize.getWidth() == 3264 && eachSize.getHeight() == 2448) {
                // 8MP 4:3
                if (Log.IS_DEBUG) Log.logDebug(TAG, "######   Recommended 8MP 4:3");
                maxSize = eachSize;
                break;
            }

            // Larger is better.
            if (maxSize.getWidth() < eachSize.getWidth()) {
                maxSize = eachSize;
            }
        }

        // Check result.
        Size ret;
        if (maxSize.getWidth() == 0) {
            ret = (Size) Objects.requireNonNull(supportedPictureSizeSet.toArray())[0];
        } else {
            ret = maxSize;
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "Result : " + ret.toString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPictureSize() : X");
        return ret;
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
    static Matrix getTextureViewTransformMatrix(
            Context context,
            int bufferWidth,
            int bufferHeight,
            int finderWidth,
            int finderHeight) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getTextureViewTransformMatrix() : E");

        // Display rotation.
        WindowManager winMng = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = winMng.getDefaultDisplay().getRotation();
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## rotation = " + rotation);

        Matrix matrix = new Matrix();
        matrix.reset();

        RectF bufferRect = new RectF(0f, 0f, bufferWidth, bufferHeight);
        RectF finderRect = new RectF(0f, 0f, finderWidth, finderHeight);
        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## BufferRect = " + bufferRect.toShortString());
            Log.logDebug(TAG, "## FinderRect = " + finderRect.toShortString());
        }
        float centerX = finderRect.centerX();
        float centerY = finderRect.centerY();

        // Aspect consideration.
        float bufferAspect;
        switch (rotation) {
            case Surface.ROTATION_0:
                // Fall through.
            case Surface.ROTATION_180:
                bufferAspect = bufferRect.height() / bufferRect.width();
                break;

            case Surface.ROTATION_90:
                // Fall through.
            case Surface.ROTATION_270:
                bufferAspect = bufferRect.width() / bufferRect.height();
                break;

            default:
                throw new IllegalStateException("Rotation is not valid.");
        }
        float finderAspect = finderRect.width() / finderRect.height();
        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "## BufferAspect = " + bufferAspect);
            Log.logDebug(TAG, "## FinderAspect = " + finderAspect);
        }

        // Check aspect.
        if ((int) (bufferAspect * 100) != (int) (finderAspect * 100)) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "#### Aspect is not matched");
            // Not matched.

            if (bufferAspect < finderAspect) {
                // Black area is available on right and left based on buffer coordinates.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Fit buffer right and left to finder.");

                matrix.postScale(
                        1.0f,
                        finderAspect / bufferAspect,
                        centerX,
                        centerY);
            } else {
                // Black area is available on top and bottom based on buffer coordinates.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "## Fit buffer top and bottom to finder");

                matrix.postScale(
                        bufferAspect / finderAspect,
                        1.0f,
                        centerX,
                        centerY);
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "getTextureViewTransformMatrix() : X");
        return matrix;
    }
}
