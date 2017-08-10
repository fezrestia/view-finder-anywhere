package com.fezrestia.android.util.math

/**
 * Indicates 2-Axis integer coordinate point.
 */
data class IntXY(var x: Int, var y: Int) {
    fun set(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun set(xy: IntXY) {
        this.x = xy.x
        this.y = xy.y
    }
}

/**
 * Indicates rectangle width and height.
 */
data class IntWH(var w: Int, var h: Int) {
    fun set(w: Int, h: Int) {
        this.w = w
        this.h = h
    }

    fun set(wh: IntWH) {
        this.w = wh.w
        this.h = wh.h
    }

    fun longlen(): Int = maxOf(w, h)

    fun shortlen(): Int = minOf(w, h)
}
