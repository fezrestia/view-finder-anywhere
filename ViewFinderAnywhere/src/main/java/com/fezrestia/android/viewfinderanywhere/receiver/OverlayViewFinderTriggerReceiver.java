package com.fezrestia.android.viewfinderanywhere.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.fezrestia.android.lib.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.control.OnOffTrigger;

public class OverlayViewFinderTriggerReceiver extends BroadcastReceiver {
    // Log tag.
    private static final String TAG = "OverlayViewFinderTriggerReceiver";

    /**
     * Register this receiver to system.
     *
     * @param context Master context
     */
    public void register(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        context.registerReceiver(this, filter);
    }

    /**
     * Unregister this receiver from system.
     *
     * @param context Master context
     */
    public void unregister(Context context) {
        context.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive()");

        String action = intent.getAction();
        if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION = " + action);

        if (action == null) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "ACTION is NULL.");
            // NOP.
        } else switch (action) {
            case ViewFinderAnywhereConstants.INTENT_ACTION_TOGGLE_OVERLAY_VISIBILITY: {
                OnOffTrigger.requestToggleVisibility(context);
            }
            break;

            case Intent.ACTION_SCREEN_ON: {
                OnOffTrigger.onScreenOn(context);
            }
            break;

            case Intent.ACTION_SCREEN_OFF: {
                OnOffTrigger.onScreenOff(context);
            }
            break;

            default:
                // Unexpected Action.
                throw new IllegalArgumentException("Unexpected Action : " + action);
        }
    }
}
