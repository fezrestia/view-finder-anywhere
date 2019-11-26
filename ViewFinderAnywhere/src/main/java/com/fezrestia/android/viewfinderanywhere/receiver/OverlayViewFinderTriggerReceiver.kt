@file:Suppress("PrivatePropertyName")

package com.fezrestia.android.viewfinderanywhere.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.control.OnOffTrigger

class OverlayViewFinderTriggerReceiver : BroadcastReceiver() {
    /**
     * Register this receiver to system.
     *
     * @param context Master context
     */
    fun register(context: Context) {
        val filter = IntentFilter()
        filter.addAction(Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)

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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = ${intent.action}")

        when (intent.action) {
            Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY -> {
                OnOffTrigger.requestToggleVisibility(context)
            }

            Constants.INTENT_ACTION_TOGGLE_OVERLAY_ENABLE_DISABLE -> {
                if (App.isOverlayViewFinderEnabled) {
                    OnOffTrigger.requestStop(context)
                } else {
                    OnOffTrigger.requestStart(context)
                }
            }

            Intent.ACTION_SCREEN_ON -> {
                OnOffTrigger.onScreenOn(context)
            }

            Intent.ACTION_SCREEN_OFF -> {
                OnOffTrigger.onScreenOff(context)
            }

            else -> {
                // Unexpected Action.
                throw IllegalArgumentException("Unexpected Action = ${intent.action}")
            }
        }
    }

    companion object {
        private const val TAG = "OverlayViewFinderTriggerReceiver"
    }
}
