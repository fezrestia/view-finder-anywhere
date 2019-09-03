package com.fezrestia.android.viewfinderanywhere;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.fezrestia.android.lib.firebase.FirebaseAnalyticsController;
import com.fezrestia.android.lib.util.log.Log;

public class ViewFinderAnywhereApplication extends Application {
    // Log tag.
    private static final String TAG = "ViewFinderAnywhereApplication";

    // UI thread handler.
    private static final Handler mUiThreadHandler = new Handler();

    // Shared preference accessor.
    private static SharedPreferences mGlobalSharedPreferences = null;

    // Resource container.
    public static CustomizableResourceContainer customResContainer = null;

    // SharedPreferences version key.
    private static final String KEY_SHARED_PREFERENCES_VERSION = "key-shared-preferences-version";
    private static final int VAL_SHARED_PREFERENCES_VERSION = 4;

    // Firebase analytics.
    private static FirebaseAnalyticsController mFirebaseAnalyticsController = null;

    // Overlay view finder is enabled or not.
    public static boolean isOverlayViewFinderEnabled = false;

    @Override
    public void onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : E");
        super.onCreate();

        // Create shared preferences accessor.
        mGlobalSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Check version.
        int curVersion = mGlobalSharedPreferences.getInt(KEY_SHARED_PREFERENCES_VERSION, 0);
        if (curVersion != VAL_SHARED_PREFERENCES_VERSION) {
            mGlobalSharedPreferences.edit().clear().apply();
            mGlobalSharedPreferences.edit().putInt(
                    KEY_SHARED_PREFERENCES_VERSION,
                    VAL_SHARED_PREFERENCES_VERSION)
                    .apply();
        }

        // Resource container.
        customResContainer = new CustomizableResourceContainer();

        // Firebase.
        mFirebaseAnalyticsController = new FirebaseAnalyticsController(this);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : X");
    }

    @Override
    public void onTerminate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : E");
        super.onTerminate();

        // Release.
        mGlobalSharedPreferences = null;
        customResContainer = null;
        mFirebaseAnalyticsController = null;

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : X");
    }

    /**
     * Get UI thread handler.
     *
     * @return Global UI thread handler.
     */
    public static Handler getUiThreadHandler() {
        return mUiThreadHandler;
    }

    /**
     * Get global shared preferences instance.
     *
     * @return Global SharedPreferences accessor.
     */
    public static SharedPreferences getGlobalSharedPreferences() {
        return mGlobalSharedPreferences;
    }

    /**
     * Global current used resources.
     */
    @SuppressWarnings("WeakerAccess") // Common resources.
    public class CustomizableResourceContainer {
        // Package.
        public String customPackage = null;

        // Drawables.
        public Drawable drawableNotificationOnGoing = null;
        public Drawable drawableVfGripLand = null;
        public Drawable drawableVfGripPort = null;
        public Drawable drawableVfFrameLand = null;
        public Drawable drawableVfFramePort = null;
        public Drawable drawableTotalBackLand = null;
        public Drawable drawableTotalBackPort = null;
        public Drawable drawableTotalForeLand = null;
        public Drawable drawableTotalForePort = null;
        public Drawable drawableStorageItemBgNormal = null;
        public Drawable drawableStorageItemBgPressed = null;
        public Drawable drawableStorageItemBgSelected = null;

        // Color.
        public int colorVfBackground = 0;
        public int colorGripLabel = 0;
        public int colorScanOnGoing = 0;
        public int colorScanSuccess = 0;
        public int colorScanFailure = 0;

        /**
         * Do reset all resource references.
         *
         * @param context Master context.
         */
        public void resetResources(Context context) {
            Resources res = context.getResources();

            // Drawable.
            drawableNotificationOnGoing
                    = res.getDrawable(R.drawable.overlay_view_finder_ongoing, null);
            drawableVfGripLand = null;
            drawableVfGripPort = null;
            drawableVfFrameLand = null;
            drawableVfFramePort = null;
            drawableTotalBackLand = null;
            drawableTotalBackPort = null;
            drawableTotalForeLand = null;
            drawableTotalForePort = null;
            drawableStorageItemBgNormal
                    = res.getDrawable(R.drawable.storage_selector_item_background_normal, null);
            drawableStorageItemBgPressed
                    = res.getDrawable(R.drawable.storage_selector_item_background_pressed, null);
            drawableStorageItemBgSelected
                    = res.getDrawable(R.drawable.storage_selector_item_background_selected, null);

            // Color.
            colorVfBackground = res.getColor(R.color.viewfinder_background_color, null);
            colorGripLabel = res.getColor(R.color.viewfinder_grip_label_color, null);
            colorScanOnGoing = res.getColor(R.color.viewfinder_scan_indicator_ongoing, null);
            colorScanSuccess = res.getColor(R.color.viewfinder_scan_indicator_success, null);
            colorScanFailure = res.getColor(R.color.viewfinder_scan_indicator_failure, null);

        }
    }

    /**
     * Load customized Plug-IN UI resources.
     *
     * @param context Master context.
     * @param customPackage Customized UI res package.
     */
    @SuppressWarnings("deprecation")
    public static void loadCustomizedUiResources(Context context, String customPackage) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "loadCustomizedUiResources() : E");

        customResContainer.resetResources(context);

        if (customPackage == null) {
            // UI plug in is not selected. Use default.
            return;
        }

        customResContainer.customPackage = customPackage;

        Context remoteContext;
        try {
            remoteContext = context.createPackageContext(
                    customResContainer.customPackage,
                    Context.CONTEXT_RESTRICTED);
        } catch (PackageManager.NameNotFoundException e) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Plug-IN package can not be accessed.");
            remoteContext = null;
        }

        if (remoteContext != null) {
            Resources res = remoteContext.getResources();

            // Drawable.
            final int resIdOnGoing = res.getIdentifier(
                    "overlay_view_finder_ongoing",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdGripLand = res.getIdentifier(
                    "overlay_view_finder_grip_landscape",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdGripPort = res.getIdentifier(
                    "overlay_view_finder_grip_portrait",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdFrameLand = res.getIdentifier(
                    "overlay_view_finder_frame_landscape",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdFramePort = res.getIdentifier(
                    "overlay_view_finder_frame_portrait",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdTotalBackLand = res.getIdentifier(
                    "overlay_view_finder_total_background_landscape",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdTotalBackPort = res.getIdentifier(
                    "overlay_view_finder_total_background_portrait",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdTotalForeLand = res.getIdentifier(
                    "overlay_view_finder_total_foreground_landscape",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdTotalForePort = res.getIdentifier(
                    "overlay_view_finder_total_foreground_portrait",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdStorageItemBgNormal = res.getIdentifier(
                    "storage_selector_item_background_normal",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdStorageItemBgPressed = res.getIdentifier(
                    "storage_selector_item_background_pressed",
                    "drawable",
                    customResContainer.customPackage);
            final int resIdStorageItemBgSelected = res.getIdentifier(
                    "storage_selector_item_background_selected",
                    "drawable",
                    customResContainer.customPackage);

            // Color.
            final int resIdVfBackgroundColor = res.getIdentifier(
                    "viewfinder_background_color",
                    "color",
                    customResContainer.customPackage);
            final int resIdGripLabelColor = res.getIdentifier(
                    "viewfinder_grip_label_color",
                    "color",
                    customResContainer.customPackage);
            final int resIdScanIndicatorOnGoingColor = res.getIdentifier(
                    "viewfinder_scan_indicator_ongoing",
                    "color",
                    customResContainer.customPackage);
            final int resIdScanIndicatorSuccessColor = res.getIdentifier(
                    "viewfinder_scan_indicator_success",
                    "color",
                    customResContainer.customPackage);
            final int resIdScanIndicatorFailureColor = res.getIdentifier(
                    "viewfinder_scan_indicator_failure",
                    "color",
                    customResContainer.customPackage);

            try {
                customResContainer.drawableNotificationOnGoing = res.getDrawable(resIdOnGoing);
                customResContainer.drawableVfGripLand = res.getDrawable(resIdGripLand);
                customResContainer.drawableVfGripPort = res.getDrawable(resIdGripPort);
                customResContainer.drawableVfFrameLand = res.getDrawable(resIdFrameLand);
                customResContainer.drawableVfFramePort = res.getDrawable(resIdFramePort);
                customResContainer.drawableTotalBackLand = res.getDrawable(resIdTotalBackLand);
                customResContainer.drawableTotalBackPort = res.getDrawable(resIdTotalBackPort);
                customResContainer.drawableTotalForeLand = res.getDrawable(resIdTotalForeLand);
                customResContainer.drawableTotalForePort = res.getDrawable(resIdTotalForePort);
                customResContainer.drawableStorageItemBgNormal
                        = res.getDrawable(resIdStorageItemBgNormal);
                customResContainer.drawableStorageItemBgPressed
                        = res.getDrawable(resIdStorageItemBgPressed);
                customResContainer.drawableStorageItemBgSelected
                        = res.getDrawable(resIdStorageItemBgSelected);
                customResContainer.colorVfBackground = res.getColor(resIdVfBackgroundColor);
                customResContainer.colorGripLabel = res.getColor(resIdGripLabelColor);
                customResContainer.colorScanOnGoing = res.getColor(resIdScanIndicatorOnGoingColor);
                customResContainer.colorScanSuccess = res.getColor(resIdScanIndicatorSuccessColor);
                customResContainer.colorScanFailure = res.getColor(resIdScanIndicatorFailureColor);

            } catch (Resources.NotFoundException e) {
                e.printStackTrace();
                if (Log.IS_DEBUG) Log.logDebug(TAG, "UI Plug-IN version conflicted.");
            }
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "loadCustomizedUiResources() : X");
    }

    /**
     * Total window configuration.
     */
    @SuppressWarnings("WeakerAccess") // Common resources.
    public static class TotalWindowConfiguration {
        public static class OverlayViewFinderWindowConfig {
            public static int enabledWindowX = 0;
            public static int enabledWindowY = 0;
            public static int enabledWindowW = 0;
            public static int enabledWindowH = 0;

            public static void update(int x, int y, int w, int h) {
                enabledWindowX = x;
                enabledWindowY = y;
                enabledWindowW = w;
                enabledWindowH = h;
            }
        }

        public static class StorageSelectorWindowConfig {
            public static int enabledWindowX = 0;
            public static int enabledWindowY = 0;
            public static int enabledWindowW = 0;
            public static int enabledWindowH = 0;

            public static void update(int x, int y, int w, int h) {
                enabledWindowX = x;
                enabledWindowY = y;
                enabledWindowW = w;
                enabledWindowH = h;
            }
        }
    }

    /**
     * Currently, StorageSelector is enabled or not.
     *
     * @return Storage selector is enabled or not.
     */
    public static boolean isStorageSelectorEnabled() {
        return getGlobalSharedPreferences().getBoolean(
                ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED,
                false);
    }

    /**
     * Get global Firebase analytics interface.
     *
     * @return Global Firebase analytics controller.
     */
    public static FirebaseAnalyticsController getGlobalFirebaseAnalyticsController() {
        return mFirebaseAnalyticsController;
    }
}
