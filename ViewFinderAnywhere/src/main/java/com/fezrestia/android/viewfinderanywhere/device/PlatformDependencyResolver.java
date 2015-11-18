package com.fezrestia.android.viewfinderanywhere.device;

import com.fezrestia.android.util.log.Log;

import java.util.HashSet;
import java.util.Set;

public class PlatformDependencyResolver {
    // Log tag.
    private static final String TAG = PlatformDependencyResolver.class.getSimpleName();

    // Platform specifications.
    private static final Size PREFERRED_PREVIEW_SIZE_FOR_STILL = new Size(1280, 720);
    private static final int PREFERRED_PICTURE_SIZE_WIDTH = 3840;

    // Aspect ratio clearance.
    private static final float ASPECT_RATIO_CLEARANCE = 0.01f;

    //TODO:Replace this to android.util.Size after L.
    public static class Size {
        private final int mWidth;
        private final int mHeight;

        /**
         * CONSTRUCTOR.
         *
         * @param width
         * @param height
         */
        public Size(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }

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
     * @param requiredAspectRatioWH
     * @param supportedPreviewSizeSet
     *
     * @return
     */
    public static final Size getOptimalPreviewSizeForStill(
            final float requiredAspectRatioWH,
            final Set<Size> supportedPreviewSizeSet) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPreviewSize() : E");

        // Check supported.
        if (supportedPreviewSizeSet.isEmpty()) {
            throw new IllegalArgumentException("Supported size set is empty.");
        }

        // Check aspect ratio.
        Set<Size> aspectAcceptable = new HashSet<Size>();
        for (Size eachSize : supportedPreviewSizeSet) {
            final float aspect = ((float) eachSize.getWidth()) / ((float) eachSize.getHeight());
            if (Math.abs(requiredAspectRatioWH - aspect) < ASPECT_RATIO_CLEARANCE) {
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
            ret = (Size) supportedPreviewSizeSet.toArray()[0];
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "Result : " + ret.toString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPreviewSize() : X");
        return ret;
    }


    /**
     * Get optimal picture size.
     *
     * @param requiredAspectRatioWH
     * @param supportedPictureSizeSet
     *
     * @return
     */
    public static final Size getOptimalPictureSize(
            final float requiredAspectRatioWH,
            final Set<Size> supportedPictureSizeSet) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPictureSize() : E");

        // Check supported.
        if (supportedPictureSizeSet.isEmpty()) {
            throw new IllegalArgumentException("Supported size set is empty.");
        }

        // Check aspect ratio.
        Set<Size> aspectAcceptable = new HashSet<Size>();
        for (Size eachSize : supportedPictureSizeSet) {
            final float aspect = ((float) eachSize.getWidth()) / ((float) eachSize.getHeight());
            if (Math.abs(requiredAspectRatioWH - aspect) < ASPECT_RATIO_CLEARANCE) {
                // Valid.
                aspectAcceptable.add(eachSize);

                if (Log.IS_DEBUG) Log.logDebug(TAG, "Aspect acceptable : " + eachSize.toString());
            }
        }

        // Preferred size.
        //TODO:Resolve platform dependency.
        final int preferredWidth = PREFERRED_PICTURE_SIZE_WIDTH;

        // Check size.
        Size acceptableSize = null;
        for (Size eachSize : aspectAcceptable) {
            if (preferredWidth < eachSize.getWidth()) {
                // Too large.
                continue;
            }

            if (acceptableSize == null) {
                acceptableSize = eachSize;
            } else {
                if (acceptableSize.getWidth() < eachSize.getWidth()) {
                    acceptableSize = eachSize;
                }
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "Size acceptable : " + eachSize.toString());
        }

        // Check result.
        Size ret;
        if (acceptableSize != null) {
            ret = acceptableSize;
        } else {
            ret = (Size) supportedPictureSizeSet.toArray()[0];
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "Result : " + ret.toString());
        if (Log.IS_DEBUG) Log.logDebug(TAG, "getOptimalPictureSize() : X");
        return ret;
    }
}
