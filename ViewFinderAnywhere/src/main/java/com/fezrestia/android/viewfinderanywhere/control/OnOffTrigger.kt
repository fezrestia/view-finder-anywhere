package com.fezrestia.android.viewfinderanywhere.control

import android.content.Context
import android.content.Intent

import com.fezrestia.android.util.log.Log
import com.fezrestia.android.viewfinderanywhere.service.OverlayViewFinderService

/**
 * ON/OFF trigger entry point.
 */
object OnOffTrigger {
    // Log tag.
    private val TAG = "OnOffTrigger"

    /**
     * Start.

     * @param context Master context.
     */
    @JvmStatic
    fun requestStart(context: Context) {
        val service = Intent(context, OverlayViewFinderService::class.java)
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

     * @param context Master context.
     */
    @JvmStatic
    fun requestStop(context: Context) {
        val service = Intent(context, OverlayViewFinderService::class.java)
        val isSuccess = context.stopService(service)

        if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStop() : isSuccess = " + isSuccess)
    }
}
