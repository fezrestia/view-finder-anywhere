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
    log("DBG", tag, event)
}

/**
 * Error log.
 *
 * @param tag Log tag.
 * @param event Log event.
 */
fun logE(tag: String, event: String) {
    log("ERR", tag, event)
}

private fun log(globalTag: String, localTag: String, event: String) {
    val clock = System.nanoTime() / 1000 / 1000
    val thread = Thread.currentThread().name

    val msg = "$globalTag $clock [$thread] $localTag : $event"

    android.util.Log.e("TraceLog", msg)
}
