package com.fezrestia.android.viewfinderanywhere.plugin.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.App

/**
 * Load customized Plug-IN UI resources.
 *
 * @param context Master context.
 * @param customPackage Customized UI res package.
 */
fun loadCustomizedUiResources(context: Context, customPackage: String?) {
    if (Log.IS_DEBUG) Log.logDebug(App.TAG, "loadCustomizedUiResources() : E")

    App.customResContainer.resetResources(context)

    if (customPackage == null) {
        // UI plug in is not selected. Use default.
        return
    }

    App.customResContainer.customPackage = customPackage

    val remoteContext: Context = try {
        context.createPackageContext(
                customPackage,
                Context.CONTEXT_RESTRICTED)
    } catch (e: PackageManager.NameNotFoundException) {
        if (Log.IS_DEBUG) Log.logDebug(App.TAG, "Plug-IN package can not be accessed.")
        return
    }

    val res = remoteContext.resources

    // Drawable.
    val resIdOnGoing = res.getIdentifier(
            "overlay_view_finder_ongoing",
            "drawable",
            customPackage)
    val resIdGripLand = res.getIdentifier(
            "overlay_view_finder_grip_landscape",
            "drawable",
            customPackage)
    val resIdGripPort = res.getIdentifier(
            "overlay_view_finder_grip_portrait",
            "drawable",
            customPackage)
    val resIdFrameLand = res.getIdentifier(
            "overlay_view_finder_frame_landscape",
            "drawable",
            customPackage)
    val resIdFramePort = res.getIdentifier(
            "overlay_view_finder_frame_portrait",
            "drawable",
            customPackage)
    val resIdTotalBackLand = res.getIdentifier(
            "overlay_view_finder_total_background_landscape",
            "drawable",
            customPackage)
    val resIdTotalBackPort = res.getIdentifier(
            "overlay_view_finder_total_background_portrait",
            "drawable",
            customPackage)
    val resIdTotalForeLand = res.getIdentifier(
            "overlay_view_finder_total_foreground_landscape",
            "drawable",
            customPackage)
    val resIdTotalForePort = res.getIdentifier(
            "overlay_view_finder_total_foreground_portrait",
            "drawable",
            customPackage)
    val resIdStorageItemBgNormal = res.getIdentifier(
            "storage_selector_item_background_normal",
            "drawable",
            customPackage)
    val resIdStorageItemBgPressed = res.getIdentifier(
            "storage_selector_item_background_pressed",
            "drawable",
            customPackage)
    val resIdStorageItemBgSelected = res.getIdentifier(
            "storage_selector_item_background_selected",
            "drawable",
            customPackage)

    // Color.
    val resIdVfBackgroundColor = res.getIdentifier(
            "viewfinder_background_color",
            "color",
            customPackage)
    val resIdGripLabelColor = res.getIdentifier(
            "viewfinder_grip_label_color",
            "color",
            customPackage)
    val resIdScanIndicatorOnGoingColor = res.getIdentifier(
            "viewfinder_scan_indicator_ongoing",
            "color",
            customPackage)
    val resIdScanIndicatorSuccessColor = res.getIdentifier(
            "viewfinder_scan_indicator_success",
            "color",
            customPackage)
    val resIdScanIndicatorFailureColor = res.getIdentifier(
            "viewfinder_scan_indicator_failure",
            "color",
            customPackage)

    try {
        App.customResContainer.drawableNotificationOnGoing = res.getDrawable(resIdOnGoing, null)
        App.customResContainer.drawableVfGripLand = res.getDrawable(resIdGripLand, null)
        App.customResContainer.drawableVfGripPort = res.getDrawable(resIdGripPort, null)
        App.customResContainer.drawableVfFrameLand = res.getDrawable(resIdFrameLand, null)
        App.customResContainer.drawableVfFramePort = res.getDrawable(resIdFramePort, null)
        App.customResContainer.drawableTotalBackLand = res.getDrawable(resIdTotalBackLand, null)
        App.customResContainer.drawableTotalBackPort = res.getDrawable(resIdTotalBackPort, null)
        App.customResContainer.drawableTotalForeLand = res.getDrawable(resIdTotalForeLand, null)
        App.customResContainer.drawableTotalForePort = res.getDrawable(resIdTotalForePort, null)
        App.customResContainer.drawableStorageItemBgNormal = res.getDrawable(resIdStorageItemBgNormal, null)
        App.customResContainer.drawableStorageItemBgPressed = res.getDrawable(resIdStorageItemBgPressed, null)
        App.customResContainer.drawableStorageItemBgSelected = res.getDrawable(resIdStorageItemBgSelected, null)
        App.customResContainer.colorVfBackground = res.getColor(resIdVfBackgroundColor, null)
        App.customResContainer.colorGripLabel = res.getColor(resIdGripLabelColor, null)
        App.customResContainer.colorScanOnGoing = res.getColor(resIdScanIndicatorOnGoingColor, null)
        App.customResContainer.colorScanSuccess = res.getColor(resIdScanIndicatorSuccessColor, null)
        App.customResContainer.colorScanFailure = res.getColor(resIdScanIndicatorFailureColor, null)

    } catch (e: Resources.NotFoundException) {
        e.printStackTrace()
        if (Log.IS_DEBUG) Log.logDebug(App.TAG, "UI Plug-IN version conflicted.")
    }

    if (Log.IS_DEBUG) Log.logDebug(App.TAG, "loadCustomizedUiResources() : X")
}
