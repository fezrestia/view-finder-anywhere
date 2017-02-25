package com.fezrestia.android.viewfinderanywhere;

import android.annotation.SuppressLint;
import android.view.Gravity;

@SuppressWarnings("WeakerAccess") // This is common constants.
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
    /** View finder trigger key. Up to 36 chars for Firebase. */
    public static final String KEY_OVERLAY_TRIGGER_FROM_FOCUS_KEY_DOUBLE_CLICK
            = "is_trigger_focus_key_double_click";
    public static final String KEY_OVERLAY_TRIGGER_FROM_NOTIFICATION
            = "is_trigger_notification_tap";
    public static final String KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE
            = "is_trigger_screen_edge";

    /** View finder size key. */
    public static final String KEY_VIEW_FINDER_SIZE
            = "key_view_finder_size";
    /** View finder size value.*/
    public static final String VAL_VIEW_FINDER_SIZE_XLARGE
            = "x-large";
    /** View finder size value.*/
    public static final String VAL_VIEW_FINDER_SIZE_LARGE
            = "large";
    /** View finder size value.*/
    public static final String VAL_VIEW_FINDER_SIZE_SMALL
            = "small";

    /** View finder aspect key. */
    public static final String KEY_VIEW_FINDER_ASPECT
            = "key_view_finder_aspect";
    /** View finder aspect value.*/
    public static final String VAL_VIEW_FINDER_ASPECT_1_1
            = "1-1";
    /** View finder aspect value.*/
    public static final String VAL_VIEW_FINDER_ASPECT_4_3
            = "4-3";
    /** View finder aspect value.*/
    public static final String VAL_VIEW_FINDER_ASPECT_16_9
            = "16-9";

    /** View finder grip size key. */
    public static final String KEY_VIEW_FINDER_GRIP_SIZE
            = "key_view_finder_grip_size";
    /** View finder grip size value. */
    public static final String VAL_VIEW_FINDER_GRIP_SIZE_X_LARGE
            = "x-large";
    /** View finder grip size value. */
    public static final String VAL_VIEW_FINDER_GRIP_SIZE_LARGE
            = "large";
    /** View finder grip size value. */
    public static final String VAL_VIEW_FINDER_GRIP_SIZE_SMALL
            = "small";

    /** View finder grip position key. */
    public static final String KEY_VIEW_FINDER_GRIP_POSITION
            = "key_view_finder_grip_pos";
    /** View finder grip position value. */
    public static final String VAL_VIEW_FINDER_GRIP_POSITION_TOP_LEFT
            = "top-left";
    /** View finder grip position value. */
    public static final String VAL_VIEW_FINDER_GRIP_POSITION_MIDDLE
            = "middle";
    /** View finder grip position value. */
    public static final String VAL_VIEW_FINDER_GRIP_POSITION_BOTTOM_RIGHT
            = "bottom-right";

    /** View finder UI plug-in key. */
    public static final String KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE
            = "key_ui_plugin_package";

    /** Font file name. */
    public static final String FONT_FILENAME_CODA = "Coda/Coda-Regular.ttf";

    /** Resource type. */
    static final String RES_TYPE_DRAWABLE = "drawable";
    /** Resource type. */
    static final String RES_TYPE_COLOR = "color";
    /** Resource type. */
    public static final String RES_TYPE_STRING = "string";

    /** Resource ID. */
    public static final String RES_ID_STRING_PLUG_IN_TITLE = "plug_in_title";

    /**
     * View finder grip size.
     */
    @SuppressWarnings({"UnnecessaryEnumModifier", "unused"})
    public enum ViewFinderGripSize {
        X_LARGE(VAL_VIEW_FINDER_GRIP_SIZE_X_LARGE, 1.0f),
        LARGE(VAL_VIEW_FINDER_GRIP_SIZE_LARGE, 0.5f),
        SMALL(VAL_VIEW_FINDER_GRIP_SIZE_SMALL, 0.333f),
        ;

        // Preference key.
        public static final String KEY = KEY_VIEW_FINDER_GRIP_SIZE;

        // Preference value.
        private final String mValue;

        // Size scale rate.
        private final float mScaleRate;

        /**
         * CONSTRUCTOR
         *
         * @param value Size
         * @param scaleRate Scale rate.
         */
        private ViewFinderGripSize(String value, float scaleRate) {
            mValue = value;
            mScaleRate = scaleRate;
        }

        /**
         * Get default value.
         *
         * @return Default grip size.
         */
        public static ViewFinderGripSize getDefault() {
            return X_LARGE;
        }

        /**
         * Get grip scale rate.
         *
         * @return Scale rate.
         */
        public float getScaleRate() {
            return mScaleRate;
        }
    }

    /**
     * View finder grip position.
     */
    @SuppressWarnings({"UnnecessaryEnumModifier", "unused"})
    @SuppressLint("RtlHardcoded")
    public enum ViewFinderGripPosition {
        TOP_LEFT(VAL_VIEW_FINDER_GRIP_POSITION_TOP_LEFT, Gravity.TOP | Gravity.LEFT),
        MIDDLE(VAL_VIEW_FINDER_GRIP_POSITION_MIDDLE, Gravity.CENTER),
        BOTTOM_RIGHT(VAL_VIEW_FINDER_GRIP_POSITION_BOTTOM_RIGHT, Gravity.BOTTOM | Gravity.RIGHT),
        ;

        // Preference key.
        public static final String KEY = KEY_VIEW_FINDER_GRIP_POSITION;

        // Preference value.
        private final String mValue;

        // Layout gravity.
        private final int mLayoutGravity;

        /**
         * CONSTRUCTOR
         *
         * @param value Grip position.
         * @param layoutGravity Layout gravity.
         */
        private ViewFinderGripPosition(String value, int layoutGravity) {
            mValue = value;
            mLayoutGravity = layoutGravity;
        }

        /**
         * Get default value.
         *
         * @return Default grip position.
         */
        public static ViewFinderGripPosition getDefault() {
            return BOTTOM_RIGHT;
        }

        /**
         * Get layout gravity.
         *
         * @return Layout gravity.
         */
        public int getLayoutGravity() {
            return mLayoutGravity;
        }
    }

    /** Storage selector is enabled or not. */
    public static final String KEY_IS_STORAGE_SELECTOR_ENABLED
            = "is_storage_selector_enabled";

    /** Storage selector creates new directory. */
    public static final String KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY
            = "key_storage_create_new_dir";

    /** Storage selector target. */
    public static final String KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY
            = "key_storage_selectable_dir";

    /** Storage selector store target. */
    public static final String KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY
            = "key_storage_store_target_dir";

    /**
     * Used camera API level.
     */
    public enum CameraApiLevel {
        CAMERA_API_1,
        CAMERA_API_2,
    }

    public static final String KEY_CAMERA_FUNCTION_API_LEVEL
            = "key_camera_api_level";

    // Firebase analytics events.
    public static final String FIREBASE_EVENT_ON_SHUTTER_DONE
            = "on_shutter_done";

    // Firebase values.
    public static String getBooleanString(boolean bool) {
        return bool ? "true" : "false";
    }

    // Animation parameters.
    public static final int STORAGE_SELECTOR_TRIGGER_DELAY_MILLIS = 1000;
}
