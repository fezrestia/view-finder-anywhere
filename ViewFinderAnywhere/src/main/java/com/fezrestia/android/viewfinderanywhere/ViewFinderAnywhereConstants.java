package com.fezrestia.android.viewfinderanywhere;

import android.view.Gravity;

public class ViewFinderAnywhereConstants {
    /** Intent constants. */
    public static final String INTENT_ACTION_FOCUS_KEY_DOUBLE_CLICK
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_FOCUS_KEY_DOUBLE_CLICK";
    /** Intent constants. */
    public static final String INTENT_ACTION_TRIGGER_OVERLAY_VIEW_FINDER
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_TRIGGER_OVERLAY_VIEW_FINDER";
    /** Intent constants. */
    public static final String INTENT_ACTION_REGISTER_UI_PLUG_IN
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_REGISTER_UI_PLUG_IN";

    /** View finder size scale against screen short line length. */
    public static final float VIEW_FINDER_SCALE_XLARGE = 1.0f / 2.0f;
    public static final float VIEW_FINDER_SCALE_LARGE = 1.0f / 3.0f;
    public static final float VIEW_FINDER_SCALE_SMALL = 1.0f / 4.0f;

    /** Aspect ratio. */
    public static final float ASPECT_RATIO_1_1 = 1.0f / 1.0f;
    /** Aspect ratio. */
    public static final float ASPECT_RATIO_4_3 = 4.0f / 3.0f;
    /** Aspect ratio. */
    public static final float ASPECT_RATIO_16_9 = 16.0f / 9.0f;

    // SharedPreferences constants.
    /** View finder trigger key. */
    public static final String KEY_OVERLAY_TRIGGER_FROM_FOCUS_KEY_DOUBLE_CLICK
            = "is_overlay_view_finder_enabled_from_focus_key_double_click";
    public static final String KEY_OVERLAY_TRIGGER_FROM_NOTIFICATION
            = "is_overlay_view_finder_enabled_from_notification";
    public static final String KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE
            = "is_overlay_view_finder_enabled_from_screen_edge";

    /** View finder size key. */
    public static final String KEY_VIEW_FINDER_SIZE = "overlay_view_finder_size";
    /** View finder size value.*/
    public static final String VAL_VIEW_FINDER_SIZE_XLARGE = "x-large";
    /** View finder size value.*/
    public static final String VAL_VIEW_FINDER_SIZE_LARGE = "large";
    /** View finder size value.*/
    public static final String VAL_VIEW_FINDER_SIZE_SMALL = "small";

    /** View finder aspect key. */
    public static final String KEY_VIEW_FINDER_ASPECT = "overlay_view_finder_aspect";
    /** View finder aspect value.*/
    public static final String VAL_VIEW_FINDER_ASPECT_1_1 = "1-1";
    /** View finder aspect value.*/
    public static final String VAL_VIEW_FINDER_ASPECT_4_3 = "4-3";
    /** View finder aspect value.*/
    public static final String VAL_VIEW_FINDER_ASPECT_16_9 = "16-9";

    /** View finder UI plug-in key. */
    public static final String KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE
            = "overlay_view_finder_ui_plugin_package";



    /** Font file name. */
    public static final String FONT_FILENAME_CODA = "Coda/Coda-Regular.ttf";



    /** Resource type. */
    public static final String RES_TYPE_DRAWABLE = "drawable";
    /** Resource type. */
    public static final String RES_TYPE_COLOR = "color";
    /** Resource type. */
    public static final String RES_TYPE_STRING = "string";

    /** Resource ID. */
    public static final String RES_ID_STRING_PLUG_IN_TITLE = "plug_in_title";

    /**
     * View finder grip size.
     */
    public static enum ViewFinderGripSize {
        X_LARGE("x-large", 1.0f),
        LARGE("large", 0.5f),
        SMALL("small", 0.333f),
        ;

        // Preference key.
        public static final String KEY = "view_finder_grip_size";

        // Preference value.
        private final String mValue;

        // Size scale rate.
        private final float mScaleRate;

        /**
         * CONSTRUCTOR
         *
         * @param value
         * @param scaleRate
         */
        private ViewFinderGripSize(String value, float scaleRate) {
            mValue = value;
            mScaleRate = scaleRate;
        }

        /**
         * Get default value.
         *
         * @return
         */
        public static ViewFinderGripSize getDefault() {
            return X_LARGE;
        }

        /**
         * Get grip scale rate.
         *
         * @return
         */
        public float getScaleRate() {
            return mScaleRate;
        }
    }

    /**
     * View finder grip position.
     */
    public static enum ViewFinderGripPosition {
        TOP_LEFT("top-left", Gravity.TOP | Gravity.LEFT),
        MIDDLE("middle", Gravity.CENTER),
        BOTTOM_RIGHT("bottom-right", Gravity.BOTTOM | Gravity.RIGHT),
        ;

        // Preference key.
        public static final String KEY = "view_finder_grip_position";

        // Preference value.
        private final String mValue;

        // Layout gravity.
        private final int mLayoutGravity;

        /**
         * CONSTRUCTOR
         *
         * @param value
         * @param layoutGravity
         */
        private ViewFinderGripPosition(String value, int layoutGravity) {
            mValue = value;
            mLayoutGravity = layoutGravity;
        }

        /**
         * Get default value.
         *
         * @return
         */
        public static ViewFinderGripPosition getDefault() {
            return BOTTOM_RIGHT;
        }

        /**
         * Get layout gravity.
         *
         * @return
         */
        public int getLayoutGravity() {
            return mLayoutGravity;
        }
    }




    /** Storage selector is enabled or not. */
    public static final String KEY_IS_STORAGE_SELECTOR_ENABLED = "is_storage_selector_enabled";

    /** Storage selector creates new directory. */
    public static final String KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY
            = "storage_selector_create_new_directory";

    /** Storage selector target. */
    public static final String KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY
            = "storage_selector_selectable_directory";

    /** Storage selector store target. */
    public static final String KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY
            = "storage_selector_store_target_directory";


    /**
     * Used camera API level.
     */
    public enum CameraApiLevel {
        CAMERA_API_1,
        CAMERA_API_2,
    }

    public static final String KEY_CAMERA_FUNCTION_API_LEVEL
            = "sp-key-camera-function-api-level";

}
