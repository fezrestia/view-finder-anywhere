package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.content.Intent

import com.fezrestia.android.util.log.Log
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import com.fezrestia.android.viewfinderanywhere.service.OverlayViewFinderService

/**
 * ON/OFF trigger entry point.
 */
object OnOffTrigger {
    // Log tag.
    private val TAG = "OnOffTrigger"

    /**
     * Start.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun requestStart(context: Context) {
        val service = Intent(ViewFinderAnywhereConstants.INTENT_ACTION_REQUEST_START_SERVICE);
        service.setClass(context, OverlayViewFinderService::class.java)
        val component = context.startService(service)

        if (Log.IS_DEBUG) {
            if (component != null) {
                Log.logDebug(TAG, "requestStart() : Component = " + component.toString())
            } else {
                Log.logDebug(TAG, "requestStart() : Component = NULL")
            }
        }
    }

    /**
     * Stop.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun requestStop(context: Context) {
        val service = Intent(context, OverlayViewFinderService::class.java)
        val isSuccess = context.stopService(service)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStop() : isSuccess = " + isSuccess)
    }

    /**
     * Request toggle overlay view finder visibility.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun requestToggleVisibility(context: Context) {
        val service = Intent(ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY);
        service.setClass(context, OverlayViewFinderService::class.java)
        context.startService(service)
    }

    /**
     * Open StorageSelector.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun openStorageSelector(context: Context) {
        val service = Intent(ViewFinderAnywhereConstants.INTENT_ACTION_OPEN_STORAGE_SELECTOR);
        service.setClass(context, OverlayViewFinderService::class.java)
        val component = context.startService(service)
    }

    /**
     * Close StorageSelector.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun closeStorageSelector(context: Context) {
        val service = Intent(ViewFinderAnywhereConstants.INTENT_ACTION_CLOSE_STORAGE_SELECTOR);
        service.setClass(context, OverlayViewFinderService::class.java)
        val component = context.startService(service)
    }
}
