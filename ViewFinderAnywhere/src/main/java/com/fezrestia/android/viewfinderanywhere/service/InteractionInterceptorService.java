package com.fezrestia.android.viewfinderanywhere.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.fezrestia.android.viewfinderanywhere.control.InteractionInterceptorController;
import com.fezrestia.android.viewfinderanywhere.util.Log;

public class InteractionInterceptorService extends Service {
    // Log tag.
    private static final String TAG = InteractionInterceptorService.class.getSimpleName();

    @Override
    public IBinder onBind(Intent intent) {
        // NOP.
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : E");

        // Start overlay view finder.
        InteractionInterceptorController.getInstance().start(this);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStartCommand() : X");
        return START_STICKY_COMPATIBILITY;
    }
}
