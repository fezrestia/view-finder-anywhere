package com.fezrestia.android.viewfinderanywhere.view

@Suppress("MemberVisibilityCanBePrivate")
object OverlayViewFinderWindowConfig {
    var enabledWindowX = 0
    var enabledWindowY = 0
    var enabledWindowW = 0
    var enabledWindowH = 0

    fun update(x: Int, y: Int, w: Int, h: Int) {
        enabledWindowX = x
        enabledWindowY = y
        enabledWindowW = w
        enabledWindowH = h
    }
}

@Suppress("MemberVisibilityCanBePrivate")
object StorageSelectorWindowConfig {
    var enabledWindowX = 0
    var enabledWindowY = 0
    var enabledWindowW = 0
    var enabledWindowH = 0

    fun update(x: Int, y: Int, w: Int, h: Int) {
        enabledWindowX = x
        enabledWindowY = y
        enabledWindowW = w
        enabledWindowH = h
    }
}
