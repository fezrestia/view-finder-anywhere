package com.fezrestia.android.viewfinderanywhere.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import com.fezrestia.android.viewfinderanywhere.control.OnOffTrigger

class OverlayViewFinderTriggerReceiver : BroadcastReceiver() {
    companion object {
        // Log tag.
        private val TAG = "OverlayViewFinderTriggerReceiver"
    }

    /**
     * Register this receiver to system.
     *
     * @param context Master context
     */
    fun register(context: Context) {
        val filter = IntentFilter()
        filter.addAction(ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(ViewFinderAnywhereConstants.INTENT_ACTION_START_OVERLAY_VIEW_FINDER)

        context.registerReceiver(this, filter)
    }

    /**
     * Unregister this receiver from system.
     *
     * @param context Master context
     */
    fun unregister(context: Context) {
        context.unregisterReceiver(this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive()")

        val action: String = intent.action
        if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = " + action)

        when (action) {
            ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY -> {
                OnOffTrigger.requestToggleVisibility(context)
            }

            ViewFinderAnywhereConstants.INTENT_ACTION_START_OVERLAY_VIEW_FINDER -> {
                val sp = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                val isActive = sp.getBoolean(
                        ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE,
                        false)
                if (!isActive) {
                    sp.edit().putBoolean(
                            ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE,
                            true)
                            .apply()
                    OnOffTrigger.requestStart(context)
                }
            }

            Intent.ACTION_SCREEN_ON -> {
                OnOffTrigger.onScreenOn(context)
            }

            Intent.ACTION_SCREEN_OFF -> {
                OnOffTrigger.onScreenOff(context)
            }

            else ->
                // Unexpected Action.
                throw IllegalArgumentException("Unexpected Action : " + action)
        }
    }
}
