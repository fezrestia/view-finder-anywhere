package com.fezrestia.android.viewfinderanywhere.config

import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.config.options.CameraApiLevel
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAspect
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderSize

/**
 * Total configuration manager.
 */
class ConfigManager {
    // Default values.
    var camApiLv = CameraApiLevel.API_1
    var evfSize = ViewFinderSize.L
    var evfAspect = ViewFinderAspect.WH_1_1

    init {
        loadPreferences()
    }

    /**
     * Release all references.
     */
    fun release() {
        // NOP.
    }

    private fun loadPreferences() {
        // Target Camera API Level.
        val apiLevel = App.sp.getString(CameraApiLevel.key, null)
        camApiLv = if (apiLevel == null) {
            // Use default.
            CameraApiLevel.API_1
        } else {
            CameraApiLevel.valueOf(apiLevel)
        }

        // Size.
        val size = App.sp.getString(ViewFinderSize.key, null)
        evfSize = if (size == null) {
            // Unexpected or not initialized yet. Use default.
            ViewFinderSize.L
        } else {
            ViewFinderSize.valueOf(size)
        }

        // Aspect.
        val aspect = App.sp.getString(ViewFinderAspect.key, null)
        evfAspect = if (aspect == null) {
            // Unexpected or not initialized yet. Use default.
            ViewFinderAspect.WH_1_1
        } else {
            ViewFinderAspect.valueOf(aspect)
        }
    }
}
