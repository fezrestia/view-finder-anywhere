package com.fezrestia.android.viewfinderanywhere

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import androidx.preference.PreferenceManager

import com.fezrestia.android.lib.firebase.FirebaseAnalyticsController
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.plugin.ui.CustomizableResourceContainer

class App : Application() {

    override fun onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : E")
        super.onCreate()

        // Total UI thread handler.
        ui = Handler()

        // Create shared preferences accessor.
        sp = PreferenceManager.getDefaultSharedPreferences(this)

        // Firebase analytics.
        firebase = FirebaseAnalyticsController(this)

        // Check version.
        val curVersion = sp.getInt(KEY_SHARED_PREFERENCES_VERSION, 0)
        if (curVersion != VAL_SHARED_PREFERENCES_VERSION) {
            sp.edit().clear().apply()
            sp.edit().putInt(
                    KEY_SHARED_PREFERENCES_VERSION,
                    VAL_SHARED_PREFERENCES_VERSION)
                    .apply()
        }

        // Resource container.
        customResContainer = CustomizableResourceContainer()


        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : X")
    }

    override fun onTerminate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : E")
        super.onTerminate()

        // NOP.

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : X")
    }


    companion object {
        // Log tag.
        const val TAG = "App"

        lateinit var ui: Handler
            private set

        lateinit var sp: SharedPreferences
            private set

        // Resource container.
        lateinit var customResContainer: CustomizableResourceContainer
            private set

        // SharedPreferences version key.
        private const val KEY_SHARED_PREFERENCES_VERSION = "key-shared-preferences-version"
        private const val VAL_SHARED_PREFERENCES_VERSION = 4

        lateinit var firebase: FirebaseAnalyticsController
            private set

        // Overlay view finder is enabled or not.
        var isOverlayViewFinderEnabled = false

        /**
         * Currently, StorageSelector is enabled or not.
         *
         * @return Storage selector is enabled or not.
         */
        val isStorageSelectorEnabled: Boolean
            get() = sp.getBoolean(
                    Constants.SP_KEY_IS_STORAGE_SELECTOR_ENABLED,
                    false)
    }
}
