package com.fezrestia.android.viewfinderanywhere.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.view.View;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;
import com.fezrestia.android.viewfinderanywhere.receiver.OverlayViewFinderTriggerReceiver;
import com.fezrestia.android.viewfinderanywhere.view.OverlayViewFinderRootView;
import com.fezrestia.android.viewfinderanywhere.view.StorageSelectorRootView;

import androidx.annotation.Nullable;

public class OverlayViewFinderService extends Service {
    // Log tag.
    private static final String TAG = "OverlayViewFinderService";

    // Notification channel.
    private static final String NOTIFICATION_CHANNEL_ONGOING = "ongoing";

    // On going notification ID.
    private static final int ONGOING_NOTIFICATION_ID = 100;

    // Core instances.
    private ConfigManager mConfigManager = null;
    private OverlayViewFinderController mController = null;
    private OverlayViewFinderRootView mRootView = null;
    private OverlayViewFinderTriggerReceiver mTriggerReceiver = null;
    private StorageSelectorRootView mStorageView = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : E");
        super.onCreate();

        // If service is restarted after process is killed by LMK for instance,
        // load current selected resources here.
        ViewFinderAnywhereApplication.loadCustomizedUiResources(this);

        // Create and dependency injection.
        setupCoreInstances();

        // Visibility toggle intent.
        Intent visibilityToggle = new Intent(
                ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY);
        visibilityToggle.setPackage(getApplicationContext().getPackageName());
        PendingIntent notificationContent = PendingIntent.getBroadcast(
                this,
                0,
                visibilityToggle,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Foreground notification.
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ONGOING,
                "On-Going notification",
                NotificationManager.IMPORTANCE_MIN);
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) throw new RuntimeException("NotificationManager is null");
        manager.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ONGOING)
                .setContentTitle(getText(R.string.overlay_view_finder_ongoing_notification))
                .setSmallIcon(R.drawable.overlay_view_finder_ongoing)
                .setContentIntent(notificationContent)
                .build();

        // On foreground.
        startForeground(
                ONGOING_NOTIFICATION_ID,
                notification);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X");
    }

    private void setupCoreInstances() {
        // Config.
        mConfigManager = new ConfigManager();

        // Controller.
        mController = new OverlayViewFinderController(this);

        // Root view.
        mRootView = (OverlayViewFinderRootView)
                View.inflate(this, R.layout.overlay_view_finder_root, null);

        // Receiver.
        mTriggerReceiver = new OverlayViewFinderTriggerReceiver();

        // Storage selector view.
        mStorageView = (StorageSelectorRootView)
                View.inflate(this, R.layout.storage_selector_root, null);

        // Set up dependency injection.
        mController.setCoreInstances(mRootView, mConfigManager);
        mRootView.setCoreInstances(mController, mConfigManager);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : E");

        String action = intent.getAction();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = " + action);

        if (action == null) {
            Log.logError(TAG, "ACTION = NULL");
        } else switch (action) {
            case ViewFinderAnywhereConstants.INTENT_ACTION_REQUEST_START_SERVICE: {
                // Start overlay view finder.
                mRootView.initialize();
                mRootView.addToOverlayWindow();
                mController.start();
                mController.resume();
                mTriggerReceiver.register(this);
            }
            break;

            case ViewFinderAnywhereConstants.INTENT_ACTION_REQUEST_STOP_SERVICE: {
                // Stop overlay view finder.
                mTriggerReceiver.unregister(this);
                mController.pause();
                mController.stop();
                mRootView.removeFromOverlayWindow();

                stopSelf();
            }
            break;

            case ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY: {
                mController.getCurrentState().onToggleShowHideRequired();
            }
            break;

            case ViewFinderAnywhereConstants.INTENT_ACTION_OPEN_STORAGE_SELECTOR: {
                // Add UI.
                mStorageView.initialize();
                mStorageView.addToOverlayWindow();
            }
            break;

            case ViewFinderAnywhereConstants.INTENT_ACTION_CLOSE_STORAGE_SELECTOR: {
                // Remove UI.
                mStorageView.removeFromOverlayWindow();
            }
            break;

            case Intent.ACTION_SCREEN_ON: {
                // NOP.
            }
            break;

            case Intent.ACTION_SCREEN_OFF: {
                // Close overlay window.
                mController.getCurrentState().requestForceStop();
            }
            break;

            default: {
                throw new RuntimeException("Unexpected ACTION");
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : X");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : E");
        super.onDestroy();

        // Stop foreground.
        stopForeground(true);

        releaseCoreInstances();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : X");
    }

    private void releaseCoreInstances() {
        mConfigManager.release();
        mConfigManager = null;

        mController.release();
        mController = null;

        mRootView.release();
        mRootView = null;

        mTriggerReceiver = null;

        mStorageView.release();
        mStorageView = null;
    }
}
