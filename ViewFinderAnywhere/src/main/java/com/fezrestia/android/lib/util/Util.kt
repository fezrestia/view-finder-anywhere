package com.fezrestia.android.lib.util

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.WindowManager

fun <T : Any> ensure(nullable: T?): T = nullable ?: throw Exception("Ensure FAIL")

/**
 * Get current display rect.
 *
 * @param windowManager
 * @return
 */
fun currentDisplayRect(windowManager: WindowManager): Rect {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay

        val screenSize = Point()
        @Suppress("DEPRECATION")
        display.getSize(screenSize)

        Rect(0, 0, screenSize.x, screenSize.y)
    } else {
        windowManager.currentWindowMetrics.bounds
    }
}

/**
 * Get current display rotation.
 *
 * @context
 * @windowManager
 * @return One of Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, or Surface.ROTATION_270.
 */
fun currentDisplayRot(context: Context, windowManager: WindowManager): Int {
    val display: Display = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay
    } else {
        context.display ?: throw RuntimeException("Context.getDisplay() return null")
    }

    return display.rotation
}
