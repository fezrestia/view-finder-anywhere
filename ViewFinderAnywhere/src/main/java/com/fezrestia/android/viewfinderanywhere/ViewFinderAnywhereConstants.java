package com.fezrestia.android.viewfinderanywhere;

public class ViewFinderAnywhereConstants {
    /** Request start intent. */
    public static final String INTENT_ACTION_REQUEST_START_SERVICE
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_REQUEST_START_SERVICE";
    /** Request stop intent. */
    public static final String INTENT_ACTION_REQUEST_STOP_SERVICE
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_REQUEST_STOP_SERVICE";
    /** Intent constants. */
    public static final String INTENT_ACTION_REGISTER_UI_PLUG_IN
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_REGISTER_UI_PLUG_IN";
    /** Intent constants. */
    public static final String INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_TOGGLE_OVERLAY_VISIBILITY";
    /** Open storage selector. */
    public static final String INTENT_ACTION_OPEN_STORAGE_SELECTOR
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_OPEN_STORAGE_SELECTOR";
    /** Close storage selector. */
    public static final String INTENT_ACTION_CLOSE_STORAGE_SELECTOR
            = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_CLOSE_STORAGE_SELECTOR";



    /** SharedPreferences key. */
    public static final String SP_KEY_UI_PLUGIN_PACKAGE
            = "sp-key-ui-plugin-package";

    /** SharedPreferences key. */
    public static final String SP_KEY_STORAGE_SELECTOR_TARGET_DIRECTORY
            = "sp-key-storage-selector-target-directory";

    /** SharedPreferences key. */
    static final String KEY_IS_STORAGE_SELECTOR_ENABLED
            = "sp-key-is-storage-selector-enabled";

    /** SharedPreferences key. */
    public static final String SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY
            = "sp-key-storage-selector-selectable-directory";



    /** Font file name. */
    public static final String FONT_FILENAME_CODA = "Coda/Coda-Regular.ttf";



    /** Firebase analytics event. */
    public static final String FIREBASE_EVENT_ON_SHUTTER_DONE
            = "on_shutter_done";

    /** Firebase analytics value. */
    public static String getBooleanString(boolean bool) {
        return bool ? "true" : "false";
    }
}
