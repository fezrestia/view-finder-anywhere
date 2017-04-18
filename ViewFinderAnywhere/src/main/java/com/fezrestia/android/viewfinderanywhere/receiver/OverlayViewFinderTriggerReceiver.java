package com.fezrestia.android.viewfinderanywhere.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;

public class OverlayViewFinderTriggerReceiver extends BroadcastReceiver {
    // Log tag.
    private static final String TAG = "OverlayViewFinderTriggerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive()");

        String action = intent.getAction();
        if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = " + action);

        if (action == null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION is NULL.");
            // NOP.
        } else switch (action) {
            case ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY:
                {
                    OverlayViewFinderController.getInstance().getCurrentState()
                            .onToggleShowHideRequired();
                }
                break;

            default:
                // Unexpected Action.
                throw new IllegalArgumentException("Unexpected Action : " + action);
        }
    }
}
