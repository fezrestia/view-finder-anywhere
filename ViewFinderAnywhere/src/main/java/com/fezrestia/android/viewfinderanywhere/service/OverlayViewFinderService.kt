@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.view.View

import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController
import com.fezrestia.android.viewfinderanywhere.plugin.ui.loadCustomizedUiResources
import com.fezrestia.android.viewfinderanywhere.receiver.OverlayViewFinderTriggerReceiver
import com.fezrestia.android.viewfinderanywhere.view.OverlayViewFinderRootView
import com.fezrestia.android.viewfinderanywhere.view.StorageSelectorRootView

/**
 * Fore-ground service for overlay view finder stand by.
 */
class OverlayViewFinderService : Service() {
    private val TAG = "OverlayViewFinderService"

    // Notification channel.
    private val NOTIFICATION_CHANNEL_ONGOING = "ongoing"

    // On going notification ID.
    private val ONGOING_NOTIFICATION_ID = 100

    // Core instances.
    private lateinit var configManager: ConfigManager
    private lateinit var controller: OverlayViewFinderController
    private lateinit var cameraView: OverlayViewFinderRootView
    private lateinit var triggerReceiver: OverlayViewFinderTriggerReceiver
    private lateinit var storageView: StorageSelectorRootView

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        if (IS_DEBUG) logD(TAG, "onCreate() : E")
        super.onCreate()

        App.isOverlayServiceActive = true

        // If service is restarted after process is killed by LMK for instance,
        // load current selected resources here.
        val customPackage = App.sp.getString(Constants.SP_KEY_UI_PLUGIN_PACKAGE, null)
        loadCustomizedUiResources(this, customPackage)

        // Create and dependency injection.
        setupCoreInstances()

        // Visibility toggle intent.
        val visibilityToggle = Intent(Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
        visibilityToggle.setPackage(applicationContext.packageName)
        val notificationContent = PendingIntent.getBroadcast(
                this,
                0,
                visibilityToggle,
                PendingIntent.FLAG_UPDATE_CURRENT)

        // Foreground notification.
        val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ONGOING,
                "On-Going notification",
                NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ONGOING)
                .setContentTitle(getText(R.string.overlay_view_finder_ongoing_notification))
                .setSmallIcon(R.drawable.overlay_view_finder_ongoing)
                .setContentIntent(notificationContent)
                .build()

        // On foreground.
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        if (IS_DEBUG) logD(TAG, "onCreate() : X")
    }

    private fun setupCoreInstances() {
        // Config.
        configManager = ConfigManager()

        // Controller.
        controller = OverlayViewFinderController(this)

        // Root view.
        cameraView = View.inflate(this, R.layout.overlay_view_finder_root, null) as OverlayViewFinderRootView

        // Receiver.
        triggerReceiver = OverlayViewFinderTriggerReceiver()

        // Storage selector view.
        storageView = View.inflate(this, R.layout.storage_selector_root, null) as StorageSelectorRootView

        // Set up dependency injection.
        controller.setCoreInstances(cameraView, storageView, configManager)
        cameraView.setCoreInstances(controller, configManager)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (IS_DEBUG) logD(TAG, "onStartCommand() : E")

        val action = intent.action

        if (IS_DEBUG) logD(TAG, "ACTION = $action")

        if (action == null) {
            logE(TAG, "ACTION = NULL")
        } else when (action) {
            Constants.INTENT_ACTION_REQUEST_START_SERVICE -> {
                controller.ready()
                triggerReceiver.register(this)
            }

            Constants.INTENT_ACTION_REQUEST_STOP_SERVICE -> {
                triggerReceiver.unregister(this)
                controller.release()

                stopSelf()
            }

            Constants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY -> {
                controller.currentState.onToggleShowHideRequired()
            }

            Intent.ACTION_SCREEN_ON -> {
                // NOP.
            }

            Intent.ACTION_SCREEN_OFF -> {
                // Close overlay window.
                controller.currentState.onPause()
                cameraView.hide()
            }

            else -> throw RuntimeException("Unexpected ACTION")
        }

        if (IS_DEBUG) logD(TAG, "onStartCommand() : X")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (IS_DEBUG) logD(TAG, "onDestroy() : E")

        // Stop foreground.
        stopForeground(true)

        releaseCoreInstances()

        App.isOverlayServiceActive = false

        super.onDestroy()
        if (IS_DEBUG) logD(TAG, "onDestroy() : X")
    }

    private fun releaseCoreInstances() {
        configManager.release()
        cameraView.release()
        storageView.release()

    }

}
