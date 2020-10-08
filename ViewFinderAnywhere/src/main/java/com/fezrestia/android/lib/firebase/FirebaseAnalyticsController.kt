@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.lib.firebase

import android.content.Context
import android.os.Bundle

import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * This is library interface.
 *
 * @constructor
 * @param context Master context.
 */
class FirebaseAnalyticsController(context: Context) {

    // Firebase instance.
    private val firebaseAnalytics: FirebaseAnalytics

    /**
     * Log request class. 1 instance trigger 1 log submission.
     *
     * @constructor
     * @param firebaseAnalytics
     */
    class LogRequest internal constructor(private val firebaseAnalytics: FirebaseAnalytics) {
        // Log event.
        private var event: String? = null

        // Event parameters.
        private val bundle = Bundle()

        /**
         * Set event string.
         *
         * @param event Log event.
         * @return Self.
         */
        fun setEvent(event: String): LogRequest {
            this.event = event
            return this
        }

        /**
         * Set parameter value.
         *
         * @param key Parameter key.
         * @param value Parameter value.
         * @return Self.
         */
        fun setParam(key: String, value: String): LogRequest {
            bundle.putString(key, value)
            return this
        }

        /**
         * Request log with event and params.
         */
        fun done() {
            event?.let { ev ->
                firebaseAnalytics.logEvent(ev, bundle)
            } ?: throw RuntimeException("## Error: event is null.")
        }
    }

    init {
        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR : E")

        firebaseAnalytics = FirebaseAnalytics.getInstance(context)

        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR : X")
    }

    /**
     * Create new LogRequest instance.
     *
     * @return Log request.
     */
    fun createNewLogRequest(): LogRequest = LogRequest(firebaseAnalytics)

    companion object {
        const val TAG = "FirebaseAnalyticsController"

        /**
         * Get last package name for Firebase param value.
         *
         * @param packageFullName Package full name.
         * @return Package name value.
         */
        fun getPkgNameValue(packageFullName: String): String {
            val segments = packageFullName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var retVal = segments[segments.size - 1]
            if (36 < retVal.length) {
                retVal = retVal.substring(retVal.length - 36)
            }

            return retVal
        }
    }
}
