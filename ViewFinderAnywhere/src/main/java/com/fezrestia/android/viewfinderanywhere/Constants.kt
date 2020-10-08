@file:Suppress("SpellCheckingInspection")

package com.fezrestia.android.viewfinderanywhere

class Constants {
    companion object {
        /** Request start intent. */
        const val INTENT_ACTION_REQUEST_START_SERVICE
                = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_REQUEST_START_SERVICE"
        /** Request stop intent. */
        const val INTENT_ACTION_REQUEST_STOP_SERVICE
                = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_REQUEST_STOP_SERVICE"
        /** Intent constants. */
        const val INTENT_ACTION_REGISTER_UI_PLUG_IN
                = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_REGISTER_UI_PLUG_IN"
        /** Intent constants. */
        const val INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY
                = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_TOGGLE_OVERLAY_VISIBILITY"
        /** Intent constants to toggle overlay enable/disable. */
        const val INTENT_ACTION_TOGGLE_OVERLAY_ENABLE_DISABLE
                = "com.fezrestia.android.viewfinderanywhere.intent.ACTION_TOGGLE_OVERLAY_ENABLE_DISABLE"


        /** SharedPreferences key. */
        const val SP_KEY_UI_PLUGIN_PACKAGE = "sp-key-ui-plugin-package"

        /** SharedPreferences key. */
        const val SP_KEY_IS_STORAGE_SELECTOR_ENABLED = "sp-key-is-storage-selector-enabled"

        /** SharedPreferences key. */
        const val SP_KEY_STORAGE_SELECTOR_TARGET_DIRECTORY = "sp-key-storage-selector-target-directory"

        /** SharedPreferences key. */
        const val SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY = "sp-key-storage-selector-selectable-directory"


        /** Font file name. */
        const val FONT_FILENAME_CODA = "Coda/Coda-Regular.ttf"


        /** Firebase analytics event. */
        const val FIREBASE_EVENT_ON_SHUTTER_DONE = "on_shutter_done"

        /** Firebase analytics value. */
        fun getBooleanString(bool: Boolean): String = if (bool) "true" else "false"
    }
}
