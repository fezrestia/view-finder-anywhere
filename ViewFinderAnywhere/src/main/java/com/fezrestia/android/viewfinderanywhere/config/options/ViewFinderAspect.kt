package com.fezrestia.android.viewfinderanywhere.config.options

enum class ViewFinderAspect(val ratioWH: Float) {
    WH_1_1(1.0f / 1.0f),
    WH_4_3(4.0f / 3.0f),
    WH_16_9(16.0f / 9.0f),
    ;
    companion object {
        const val key = "sp-key-view-finder-aspect"
    }
}
