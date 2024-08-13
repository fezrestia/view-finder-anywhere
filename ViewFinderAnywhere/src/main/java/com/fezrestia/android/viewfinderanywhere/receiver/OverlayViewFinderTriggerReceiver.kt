@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
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
        filter.addAction(Constants.INTENT_ACTION_TOGGLE_OVERLAY_ENABLE_DISABLE)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // noinspection UnspecifiedRegisterReceiverFlag
            context.registerReceiver(this, filter)
        }
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
        if (IS_DEBUG) logD(TAG, "onReceive()")
        if (IS_DEBUG) logD(TAG, "ACTION = ${intent.action}")

        when (intent.action) {
            Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY -> {
                OnOffTrigger.requestToggleVisibility(context)
            }

            Constants.INTENT_ACTION_TOGGLE_OVERLAY_ENABLE_DISABLE -> {
                if (App.isOverlayServiceActive) {
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
