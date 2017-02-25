package com.fezrestia.android.viewfinderanywhere.control;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.LayoutInflater;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.service.StorageSelectorService;
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
     * Life cycle trigger interface.
     */
    static class LifeCycleTrigger {
        private static final String TAG = LifeCycleTrigger.class.getSimpleName();
        private static final LifeCycleTrigger INSTANCE = new LifeCycleTrigger();

        // CONSTRUCTOR.
        private LifeCycleTrigger() {
            // NOP.
        }

        /**
         * Get accessor.
         *
         * @return Life cycle instance.
         */
        public static LifeCycleTrigger getInstance() {
            return INSTANCE;
        }

        /**
         * Start.
         *
         * @param context Master context.
         */
        void requestStart(Context context) {
            Intent service = new Intent(context, StorageSelectorService.class);
            ComponentName component = context.startService(service);

            if (Log.IS_DEBUG) {
                if (component != null) {
                    Log.logDebug(TAG, "requestStart() : Component = " + component.toString());
                } else {
                    Log.logDebug(TAG, "requestStart() : Component = NULL");
                }
            }
        }

        /**
         * Stop.
         *
         * @param context Master context.
         */
        void requestStop(Context context) {
            Intent service = new Intent(context, StorageSelectorService.class);
            boolean isSuccess = context.stopService(service);

            if (Log.IS_DEBUG) Log.logDebug(TAG, "requestStop() : isSuccess = " + isSuccess);
        }
    }

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

        // Load preferences.
        loadPreferences();

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

    private void loadPreferences() {





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
