package com.fezrestia.android.lib.util.log

import android.os.SystemClock

// Total log level.
const val IS_DEBUG = false

/**
 * Debug log.
 *
 * @param tag Log tag.
 * @param event Log event.
 */
fun logD(tag: String, event: String) {
    log("DEBUG", tag, event)
}

/**
 * Error log.
 *
 * @param tag Log tag.
 * @param event Log event.
 */
fun logE(tag: String, event: String) {
    log("ERROR", tag, event)
}

private fun log(globalTag: String, localTag: String, event: String) {
    val clock = SystemClock.uptimeMillis()
    val thread = Thread.currentThread().name

    val msg = "$globalTag/ CLK=$clock TH=$thread | $localTag : $event"

    android.util.Log.e("TraceLog", msg)
}
