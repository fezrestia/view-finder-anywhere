@file:Suppress("PrivatePropertyName")

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

import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController
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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : E")
        super.onCreate()

        // If service is restarted after process is killed by LMK for instance,
        // load current selected resources here.
        val customPackage = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getString(ViewFinderAnywhereConstants.SP_KEY_UI_PLUGIN_PACKAGE, null)
        ViewFinderAnywhereApplication.loadCustomizedUiResources(this, customPackage)

        // Create and dependency injection.
        setupCoreInstances()

        // Visibility toggle intent.
        val visibilityToggle = Intent(ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY)
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

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X")
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
        controller.setCoreInstances(cameraView, configManager)
        cameraView.setCoreInstances(controller, configManager)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : E")

        val action = intent.action

        if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = $action")

        if (action == null) {
            Log.logError(TAG, "ACTION = NULL")
        } else when (action) {
            ViewFinderAnywhereConstants.INTENT_ACTION_REQUEST_START_SERVICE -> {
                ViewFinderAnywhereApplication.isOverlayViewFinderEnabled = true

                // Start overlay view finder.
                cameraView.initialize()
                cameraView.addToOverlayWindow()
                controller.start()
                controller.resume()
                triggerReceiver.register(this)
            }

            ViewFinderAnywhereConstants.INTENT_ACTION_REQUEST_STOP_SERVICE -> {
                // Stop overlay view finder.
                triggerReceiver.unregister(this)
                controller.pause()
                controller.stop()
                cameraView.removeFromOverlayWindow()

                stopSelf()

                ViewFinderAnywhereApplication.isOverlayViewFinderEnabled = false
            }

            ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY -> {
                controller.currentState.onToggleShowHideRequired()
            }

            ViewFinderAnywhereConstants.INTENT_ACTION_OPEN_STORAGE_SELECTOR -> {
                // Add UI.
                storageView.initialize()
                storageView.addToOverlayWindow()
            }

            ViewFinderAnywhereConstants.INTENT_ACTION_CLOSE_STORAGE_SELECTOR -> {
                // Remove UI.
                storageView.removeFromOverlayWindow()
            }

            Intent.ACTION_SCREEN_ON -> {
                // NOP.
            }

            Intent.ACTION_SCREEN_OFF -> {
                // Close overlay window.
                controller.currentState.requestForceStop()
            }

            else -> throw RuntimeException("Unexpected ACTION")
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : X")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : E")
        super.onDestroy()

        // Stop foreground.
        stopForeground(true)

        releaseCoreInstances()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : X")
    }

    private fun releaseCoreInstances() {
        configManager.release()
        controller.release()
        cameraView.release()
        storageView.release()

    }

}
