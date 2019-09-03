package com.fezrestia.android.viewfinderanywhere.device;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.config.ViewFinderAspect;

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
}
