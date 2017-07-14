package com.fezrestia.android.viewfinderanywhere.config

import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants.CameraApiLevel

/**
 * Total configuration manager.
 */
class ConfigManager {
    // Log tag.
    private val TAG = "ConfigManager"

    var camApiLv: CameraApiLevel = CameraApiLevel.CAMERA_API_1
    var evfAspectWH: Float = ViewFinderAnywhereConstants.ASPECT_RATIO_1_1

    // CONSTRUCTOR.
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
        val apiLevel = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getString(ViewFinderAnywhereConstants.KEY_CAMERA_FUNCTION_API_LEVEL, null)
        if (apiLevel == null) {
            // Use default.
            camApiLv = CameraApiLevel.CAMERA_API_1
        } else when (apiLevel) {
            CameraApiLevel.CAMERA_API_1.name -> {
                camApiLv = ViewFinderAnywhereConstants.CameraApiLevel.CAMERA_API_1
            }
            CameraApiLevel.CAMERA_API_2.name -> {
                camApiLv = ViewFinderAnywhereConstants.CameraApiLevel.CAMERA_API_2
            }
            else -> {
                // NOP. Unexpected.
                throw IllegalArgumentException("Unexpected API level.")
            }
        }

        // Aspect.
        val aspect = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getString(ViewFinderAnywhereConstants.KEY_VIEW_FINDER_ASPECT, null)
        if (aspect == null) {
            // Unexpected or not initialized yet. Use default.
            evfAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_1_1
        } else when (aspect) {
            ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_16_9 -> {
                evfAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_16_9
            }
            ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_4_3 -> {
                evfAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_4_3
            }
            ViewFinderAnywhereConstants.VAL_VIEW_FINDER_ASPECT_1_1 -> {
                evfAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_1_1
            }
            else -> {
                // NOP. Unexpected.
                throw IllegalArgumentException("Unexpected Aspect.")
            }
        }
    }
}
