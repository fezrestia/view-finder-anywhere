package com.fezrestia.android.viewfinderanywhere.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;
import com.fezrestia.android.viewfinderanywhere.util.Log;

public class OverlayViewFinderTriggerReceiver extends BroadcastReceiver {
    // Log tag.
    private static final String TAG = OverlayViewFinderTriggerReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive()");

        String action = intent.getAction();
        if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = " + action);

        if (action == null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION is NULL.");
            // NOP.
        } else if (ViewFinderAnywhereConstants.INTENT_ACTION_FOCUS_KEY_DOUBLE_CLICK
                .equals(action)) {
            if (isAlreadyTriggered()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already triggered.");
                return;
            }

            // En/Disable
            SharedPreferences sp = ViewFinderAnywhereApplication.getGlobalSharedPreferences();
            final boolean isEnabled = sp.getBoolean(
                    ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_FOCUS_KEY_DOUBLE_CLICK,
                    false);
            // Trigger.
            if (isEnabled) {
                // Start.
                OverlayViewFinderController.LifeCycleTrigger.getInstance().requestStart(context);
            }
        } else if (ViewFinderAnywhereConstants.INTENT_ACTION_TRIGGER_OVERLAY_VIEW_FINDER
                .equals(action)) {
            if (isAlreadyTriggered()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already triggered.");
                return;
            }

            // En/Disable
            SharedPreferences sp = ViewFinderAnywhereApplication.getGlobalSharedPreferences();
            final boolean isEnabled = sp.getBoolean(
                    ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_NOTIFICATION,
                    false);
            // Trigger.
            if (isEnabled) {
                // Start.
                OverlayViewFinderController.LifeCycleTrigger.getInstance().requestStart(context);
            }
        } else {
            // Unexpected Action.
            throw new IllegalArgumentException("Unexpected Action : " + action);
        }
    }

    private boolean isAlreadyTriggered() {
        return OverlayViewFinderController.getInstance().isOverlayActive();
    }
}
