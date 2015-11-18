package com.fezrestia.android.viewfinderanywhere.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.control.StorageSelectorController;

public class StorageSelectorService extends Service {
    // Log tag.
    private static final String TAG = StorageSelectorService.class.getSimpleName();

    // On going notification ID.
    private static final int ONGOING_NOTIFICATION_ID = 101;

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : E");
        // NOP.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onBind() : X");
        return null;
    }

    @Override
    public void onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : E");
        super.onCreate();
        // NOP.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate() : X");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : E");

        // If service is restarted after process is killed by LMK for instance,
        // load current selected resources here.
        ViewFinderAnywhereApplication.loadCustomizedUiResources(this);

        // Foreground notification.
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.storage_selector_ongoing_notification))
                .setSmallIcon(R.drawable.storage_selector_ongoing)
                .build();

        // On foreground.
        startForeground(
                ONGOING_NOTIFICATION_ID,
                notification);

        // Start overlay view finder.
        StorageSelectorController.getInstance().start(StorageSelectorService.this);
        StorageSelectorController.getInstance().resume();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : X");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : E");
        super.onDestroy();

        // Stop overlay view finder.
        StorageSelectorController.getInstance().pause();
        StorageSelectorController.getInstance().stop();

        // Stop foreground.
        stopForeground(true);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy() : X");
    }
}
