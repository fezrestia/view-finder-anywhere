package com.fezrestia.android.viewfinderanywhere.plugin.ui

import android.content.Context
import android.graphics.drawable.Drawable
import com.fezrestia.android.viewfinderanywhere.R

class CustomizableResourceContainer {
    // Package.
    var customPackage: String? = null

    // Drawables.
    var drawableNotificationOnGoing: Drawable? = null
    var drawableVfGripLand: Drawable? = null
    var drawableVfGripPort: Drawable? = null
    var drawableVfFrameLand: Drawable? = null
    var drawableVfFramePort: Drawable? = null
    var drawableTotalBackLand: Drawable? = null
    var drawableTotalBackPort: Drawable? = null
    var drawableTotalForeLand: Drawable? = null
    var drawableTotalForePort: Drawable? = null
    var drawableStorageItemBgNormal: Drawable? = null
    var drawableStorageItemBgPressed: Drawable? = null
    var drawableStorageItemBgSelected: Drawable? = null

    // Color.
    var colorVfBackground = 0
    var colorGripLabel = 0
    var colorScanOnGoing = 0
    var colorScanSuccess = 0
    var colorScanFailure = 0
    var colorRec = 0

    /**
     * Do reset all resource references.
     *
     * @param context Master context.
     */
    fun resetResources(context: Context) {
        val res = context.resources

        // Drawable.
        drawableNotificationOnGoing = res.getDrawable(R.drawable.overlay_view_finder_ongoing, null)
        drawableVfGripLand = null
        drawableVfGripPort = null
        drawableVfFrameLand = null
        drawableVfFramePort = null
        drawableTotalBackLand = null
        drawableTotalBackPort = null
        drawableTotalForeLand = null
        drawableTotalForePort = null
        drawableStorageItemBgNormal = res.getDrawable(R.drawable.storage_selector_item_background_normal, null)
        drawableStorageItemBgPressed = res.getDrawable(R.drawable.storage_selector_item_background_pressed, null)
        drawableStorageItemBgSelected = res.getDrawable(R.drawable.storage_selector_item_background_selected, null)

        // Color.
        colorVfBackground = res.getColor(R.color.viewfinder_background_color, null)
        colorGripLabel = res.getColor(R.color.viewfinder_grip_label_color, null)
        colorScanOnGoing = res.getColor(R.color.viewfinder_scan_indicator_ongoing, null)
        colorScanSuccess = res.getColor(R.color.viewfinder_scan_indicator_success, null)
        colorScanFailure = res.getColor(R.color.viewfinder_scan_indicator_failure, null)
        colorRec = res.getColor(R.color.viewfinder_rec_indicator, null)
    }
}
