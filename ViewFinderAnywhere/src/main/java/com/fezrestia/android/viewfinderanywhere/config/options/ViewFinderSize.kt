package com.fezrestia.android.viewfinderanywhere.config.options

@Suppress("unused")
enum class ViewFinderSize(val scaleRate: Float) {
    L(1.0f / 2.0f),
    M(1.0f / 3.0f),
    S(1.0f / 4.0f),
    ;
    companion object {
        const val key = "sp-key-view-finder-size"
    }
}
