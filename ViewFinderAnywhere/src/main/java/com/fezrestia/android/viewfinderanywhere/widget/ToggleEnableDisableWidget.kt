@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.receiver.OverlayViewFinderTriggerReceiver

class ToggleEnableDisableWidget : AppWidgetProvider() {

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // NOP.
    }

    override fun onDisabled(context: Context) {
        // NOP.
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateAppWidget()")
        if (Log.IS_DEBUG) Log.logDebug(TAG, "## widget ID = $appWidgetId")

        val endisToggleIntent = Intent(context, OverlayViewFinderTriggerReceiver::class.java)
        endisToggleIntent.action = Constants.INTENT_ACTION_TOGGLE_OVERLAY_ENABLE_DISABLE
        val pendIntent = PendingIntent.getBroadcast(
                context,
                0, // private request code. dummy.
                endisToggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        val views = RemoteViews(context.packageName, R.layout.widget_enable_disable_toggler)
        views.setOnClickPendingIntent(R.id.enable_disable_toggler_icon, pendIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        private const val TAG = "EnableDisableToggler"
    }
}
