package com.fezrestia.android.lib.firebase;

import android.content.Context;
import android.os.Bundle;

import com.fezrestia.android.util.log.Log;
import com.google.firebase.analytics.FirebaseAnalytics;

public class FirebaseAnalyticsController {
    public static final String TAG = "FirebaseAnalyticsController";

    // Firebase instance.
    private final FirebaseAnalytics mFirebaseAnalytics;

    /**
     * Log request class. 1 instance trigger 1 log submission.
     */
    public static class LogRequest {
        // Firebase.
        private final FirebaseAnalytics mFirebaseAnalytics;

        // Log event.
        private String mEvent = null;

        // Event parameters.
        private Bundle mBundle = new Bundle();

        // CONSTRUCTOR.
        private LogRequest(FirebaseAnalytics firebase) {
            mFirebaseAnalytics = firebase;
        }

        /**
         * Set event string.
         *
         * @param event
         * @return
         */
        public LogRequest setEvent(String event) {
            mEvent = event;
            return this;
        }

        /**
         * Set parameter value.
         *
         * @param key
         * @param value
         * @return
         */
        public LogRequest setParam(String key, String value) {
            mBundle.putString(key, value);
            return this;
        }

        /**
         * Request log with event and params.
         */
        public void done() {
            mFirebaseAnalytics.logEvent(mEvent, mBundle);
        }
    }

    /**
     * CONSTRUCTOR.
     *
     * @param context
     */
    public FirebaseAnalyticsController(Context context) {
        Log.logDebug(TAG, "CONSTRUCTOR : E");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        Log.logDebug(TAG, "CONSTRUCTOR : X");
    }

    /**
     * Create new LogRequest instance.
     *
     * @return
     */
    public LogRequest createNewLogRequest() {
        return new LogRequest(mFirebaseAnalytics);
    }


    /**
     * Get last package name for Firebase param value.
     *
     * @param packageFullName
     * @return
     */
    public static String getPkgNameValue(String packageFullName) {
        String[] segments = packageFullName.split("\\.");
        String retVal;
        if (1 <= segments.length) {
            retVal = segments[segments.length - 1];
        } else {
            retVal = segments[0];
        }
        if (36 < retVal.length()) {
            retVal = retVal.substring(retVal.length() - 36);
        }

        return retVal;
    }
}
