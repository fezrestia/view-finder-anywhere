package com.fezrestia.android.util.log;

import android.os.SystemClock;

public class Log {
    // All area total log trigger.
    public static final boolean IS_DEBUG = false;

    /**
     * Debug log.
     *
     * @param tag
     * @param event
     */
    public static void logDebug(String tag, String event) {
        log("DEBUG", tag, event);
    }

    /**
     * Error log.
     *
     * @param tag
     * @param event
     */
    public static void logError(String tag, String event) {
        log("ERROR", tag, event);
    }

    private static void log(String globalTag, String localTag, String event) {
        StringBuilder builder = new StringBuilder().append("[").append(globalTag).append("] ")
                .append("[TIME = ").append(SystemClock.uptimeMillis()).append("] ")
                .append("[").append(localTag).append("]")
                .append("[").append(Thread.currentThread().getName()).append("] ")
                .append(": ").append(event);
        android.util.Log.e("TraceLog", builder.toString());
    }
}
