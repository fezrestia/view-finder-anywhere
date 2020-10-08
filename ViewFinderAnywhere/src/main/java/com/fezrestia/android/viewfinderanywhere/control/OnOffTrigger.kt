@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.content.Intent

import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.viewfinderanywhere.Constants
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

        if (IS_DEBUG) {
            if (component == null) {
                logD(TAG, "notifyToService() : FAILED action=$action")
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
        notifyToService(context, Constants.INTENT_ACTION_REQUEST_START_SERVICE)
    }

    /**
     * Stop.
     *
     * @param context Master context.
     */
    @JvmStatic
    fun requestStop(context: Context) {
        notifyToService(context, Constants.INTENT_ACTION_REQUEST_STOP_SERVICE)
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
                Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
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
