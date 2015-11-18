package com.fezrestia.android.viewfinderanywhere.control;

import android.content.Context;
import android.view.LayoutInflater;

import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.view.UserInteractionInterceptor;

public class InteractionInterceptorController {
    // Log tag.
    private static final String TAG = InteractionInterceptorController.class.getSimpleName();

    // Master context.
    private  Context mContext;

    // Singleton instance
    private static final InteractionInterceptorController INSTANCE
             = new InteractionInterceptorController();

    // Touch action interceptor.
    private UserInteractionInterceptor mUserInteractionInterceptor = null;

    // Flags.
    private boolean mIsAlreadyStarted = false;

    /**
     * CONSTRUCTOR.
     */
    private InteractionInterceptorController() {
        // NOP.
    }

    /**
     * Get singleton controller instance.
     *
     * @return
     */
    public static synchronized InteractionInterceptorController getInstance() {
        return INSTANCE;
    }

    /**
     * Start overlay view finder.
     *
     * @param context
     */
    public void start(Context context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : E");

        // Check.
        if (mIsAlreadyStarted) {
            // NOP. Already started.
            Log.logError(TAG, "Error. Already started.");
            return;
        }
        mIsAlreadyStarted = true;

        // Cache master context.
        mContext = context;

        // Create overlay view.
        mUserInteractionInterceptor = (UserInteractionInterceptor)
                LayoutInflater.from(context).inflate(
                R.layout.overlay_user_interaction_interceptor_root, null);

        // Add to window.
        mUserInteractionInterceptor.addToOverlayWindow();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "start() : X");
    }

    /**
     * Stop overlay view finder.
     */
    public void stop(){
        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : E");

        if (!mIsAlreadyStarted) {
            // NOP. Already stopped.
            Log.logError(TAG, "Error. Already stopped.");
            return;
        }
        mIsAlreadyStarted = false;

        // Release references.
        mContext = null;
        if (mUserInteractionInterceptor != null) {
            mUserInteractionInterceptor.removeFromOverlayWindow();
            mUserInteractionInterceptor.release();
            mUserInteractionInterceptor = null;
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "stop() : X");
    }

    /**
     * This service is already started.
     *
     * @return
     */
    public boolean isAlreadyStarted() {
        return mIsAlreadyStarted;
    }
}
