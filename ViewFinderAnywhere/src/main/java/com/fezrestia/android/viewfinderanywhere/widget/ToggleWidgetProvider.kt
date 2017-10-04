package com.fezrestia.android.viewfinderanywhere.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants

/**
 * Overlay view finder ON/OFF toggle widget provider.
 */
class ToggleWidgetProvider : AppWidgetProvider() {
    // Log tag.
    val TAG = "ToggleWidgetProvider"

    @Suppress("SimplifyBooleanWithConstants")
    val IS_DEBUG = false || Log.IS_DEBUG

    override fun onUpdate(
            context: Context?,
            appWidgetManager: AppWidgetManager?,
            appWidgetIds: IntArray?) {
        if(IS_DEBUG) Log.logDebug(TAG, "onUpdate() : E")
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        if (context != null) {
            updateWidget(context)
        }

        if(IS_DEBUG) Log.logDebug(TAG, "onUpdate() : X")
    }

    private fun updateWidget(context: Context) {
        if(IS_DEBUG) Log.logDebug(TAG, "updateWidget() : E")

        // View.
        val remViews = RemoteViews(
                context.packageName,
                R.layout.toggle_overlay_widget)

        // Click event.
        remViews.setOnClickPendingIntent(
                R.id.widget_toggle_button,
                getWidgetClickCallback(context))

        // Update.
        val component = ComponentName(context, ToggleWidgetProvider::class.java)
        val mng = AppWidgetManager.getInstance(context)
        mng.updateAppWidget(component, remViews)

        if(IS_DEBUG) Log.logDebug(TAG, "updateWidget() : X")
    }

    private fun getWidgetClickCallback(context: Context): PendingIntent {
        val intent = Intent()
        intent.action = ViewFinderAnywhereConstants.INTENT_ACTION_START_OVERLAY_VIEW_FINDER
        intent.`package` = context.packageName
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
