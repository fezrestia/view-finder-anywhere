package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.content.Intent

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import com.fezrestia.android.viewfinderanywhere.service.OverlayViewFinderService

/**
 * ON/OFF trigger entry point.
 */
object OnOffTrigger {
    // Log tag.
    private const val TAG = "OnOffTrigger"

    private fun notifyToService(context: Context, action: String) {
        val service = Intent(action)
        val appContext = context.applicationContext
        service.setClass(appContext, OverlayViewFinderService::class.java)
        val component = appContext.startForegroundService(service)

        if (Log.IS_DEBUG) {
            if (component == null) {
                Log.logDebug(TAG, "notifyToService() : FAILED action=$action")
            }
        }
    }

    /**
     * Start.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun requestStart(context: Context) {
        notifyToService(context, ViewFinderAnywhereConstants.INTENT_ACTION_REQUEST_START_SERVICE)
    }

    /**
     * Stop.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun requestStop(context: Context) {
        notifyToService(context, ViewFinderAnywhereConstants.INTENT_ACTION_REQUEST_STOP_SERVICE)
    }

    /**
     * Request toggle overlay view finder visibility.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun requestToggleVisibility(context: Context) {
        notifyToService(
                context,
                ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
    }

    /**
     * Open StorageSelector.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun openStorageSelector(context: Context) {
        notifyToService(context, ViewFinderAnywhereConstants.INTENT_ACTION_OPEN_STORAGE_SELECTOR)
    }

    /**
     * Close StorageSelector.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun closeStorageSelector(context: Context) {
        notifyToService(context, ViewFinderAnywhereConstants.INTENT_ACTION_CLOSE_STORAGE_SELECTOR)
    }

    /**
     * Notify screen ON.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun onScreenOn(context: Context) {
        notifyToService(context, Intent.ACTION_SCREEN_ON)
    }

    /**
     * Notify screen OFF.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun onScreenOff(context: Context) {
        notifyToService(context, Intent.ACTION_SCREEN_OFF)
    }
}
