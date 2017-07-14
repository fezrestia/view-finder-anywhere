package com.fezrestia.android.viewfinderanywhere.control;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.LayoutInflater;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.view.StorageSelectorRootView;

public class StorageSelectorController {
    // Log tag.
    private static final String TAG = "StorageSelectorController";

    // Master context.
    private  Context mContext;

    // Singleton instance
    // TODO: Consider to change singleton.
    @SuppressLint("StaticFieldLeak")
    private static final StorageSelectorController INSTANCE = new StorageSelectorController();

    // Overlay view.
    private StorageSelectorRootView mRootView = null;

    // Receiver.
    private ScreenOffReceiver mScreenOffReceiver = null;

    // Active flag.
    private boolean mIsActive = false;

    /**
     * CONSTRUCTOR.
     */
    private StorageSelectorController() {
        // NOP.
    }

    /**
     * Get singleton controller instance.
     *
     * @return Controller instance.
     */
    public static synchronized StorageSelectorController getInstance() {
        return INSTANCE;
    }

    /**
     * Start overlay view finder.
     *
     * @param context Master context.
     */
    @SuppressLint("InflateParams")
    public void start(Context context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E");

        if (mRootView != null) {
            // NOP. Already started.
            Log.logError(TAG, "Error. Already started.");
            return;
        }

        // Cache master context.
        mContext = context;

        // Create overlay view.
        mRootView = (StorageSelectorRootView)
                LayoutInflater.from(context).inflate(
                R.layout.storage_selector_root, null);
        mRootView.initialize();

        // Add to window.
        mRootView.addToOverlayWindow();

        // Receiver.
        mScreenOffReceiver = new ScreenOffReceiver();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X");
    }

    /**
     * Resume overlay view finder.
     */
    public void resume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : E");

        // Receiver.
        mScreenOffReceiver.enable(mContext);

        // Active.
        mIsActive = true;

        if (Log.IS_DEBUG) Log.logDebug(TAG, "resume() : X");
    }

    /**
     * Pause overlay view finder.
     */
    public void pause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : E");

        // Inactive.
        mIsActive = false;

        // Receiver.
        mScreenOffReceiver.disable(mContext);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "pause() : X");
    }

    /**
     * Now on active or not.
     *
     * @return Overlay is active or not.
     */
    public boolean isActive() {
        return mIsActive;
    }

    /**
     * Stop overlay view finder.
     */
    public void stop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E");

        if (mRootView == null) {
            // NOP. Already stopped.
            Log.logError(TAG, "Error. Already stopped.");
            return;
        }

        // Release receiver.
        if (mScreenOffReceiver != null) {
            mScreenOffReceiver.disable(mContext);
            mScreenOffReceiver = null;
        }

        // Release references.
        mContext = null;
        if (mRootView != null) {
            mRootView.release();
            mRootView.removeFromOverlayWindow();
            mRootView = null;
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X");
    }

    private static class ScreenOffReceiver extends BroadcastReceiver {
        // Log tag.
        private static final String TAG = ScreenOffReceiver.class.getSimpleName();

        // Screen OFF receiver filter.
        private IntentFilter mScreenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);

        // This receiver is enabled or not.
        private boolean mIsEnabled = false;

        /**
         * Enable screen off receiver.
         *
         * @param context Master context.
         */
        public void enable(Context context) {
            if (!mIsEnabled) {
                mIsEnabled = true;
                context.registerReceiver(this, mScreenOffFilter);
            }
        }

        /**
         * Disable screen off receiver.
         *
         * @param context Master context.
         */
        public void disable(Context context) {
            if (mIsEnabled) {
               context.unregisterReceiver(this);
               mIsEnabled = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onReceive() : ACTION = " + intent.getAction());

            StorageSelectorController.getInstance().pause();
            StorageSelectorController.getInstance().stop();
        }
    }
}
