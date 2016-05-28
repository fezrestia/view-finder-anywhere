package com.fezrestia.android.viewfinderanywhere;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.fezrestia.android.util.log.Log;
import com.google.firebase.analytics.FirebaseAnalytics;

public class ViewFinderAnywhereApplication extends Application {
    // Log tag.
    private static final String TAG = "ViewFinderAnywhereApplication";

    // UI thread handler.
    private static final Handler mUiThreadHandler = new Handler();

    // Shared preference accessor.
    private static SharedPreferences mGlobalSharedPreferences = null;

    // Resource container.
    private static CustomizableResourceContainer mCustomResContainer = null;

    // SharedPreferences version key.
    private static final String KEY_SHARED_PREFERENCES_VERSION = "key-shared-preferences-version";
    private static final int VAL_SHARED_PREFERENCES_VERSION = 3;

    // Firebase analytics.
    private static FirebaseAnalytics mFirebaseAnalytics = null;

    @Override
    public void onCreate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : E");
        super.onCreate();

        // Create shared preferences accessor.
        mGlobalSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Check version.
        int curVersion = mGlobalSharedPreferences.getInt(KEY_SHARED_PREFERENCES_VERSION, 0);
        if (curVersion != VAL_SHARED_PREFERENCES_VERSION) {
            mGlobalSharedPreferences.edit().clear().commit();
            mGlobalSharedPreferences.edit().putInt(
                    KEY_SHARED_PREFERENCES_VERSION,
                    VAL_SHARED_PREFERENCES_VERSION)
                    .commit();
        }

        // Resource container.
        mCustomResContainer = new CustomizableResourceContainer();

        // Firebase.
        Log.logError(TAG, "FirebaseAnalytics.getInstance() : E");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        Log.logError(TAG, "FirebaseAnalytics.getInstance() : X");

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : X");
    }

    @Override
    public void onTerminate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : E");
        super.onTerminate();

        // Release.
        mGlobalSharedPreferences = null;
        mCustomResContainer = null;
        mFirebaseAnalytics = null;

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onTerminate() : X");
    }


    /**
     * Get UI thread handler.
     *
     * @return
     */
    public static Handler getUiThreadHandler() {
        return mUiThreadHandler;
    }

    /**
     * Get global shared preferences instance.
     *
     * @return
     */
    public static SharedPreferences getGlobalSharedPreferences() {
        return mGlobalSharedPreferences;
    }



    /**
     * Get current resource container.
     *
     * @return
     */
    public static CustomizableResourceContainer getCustomResContainer() {
        return mCustomResContainer;
    }

    /**
     * Global current used resources.
     */
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
         */
        public void resetResources() {
            // Drawable.
            drawableNotificationOnGoing = null;
            drawableVfGripLand = null;
            drawableVfGripPort = null;
            drawableVfFrameLand = null;
            drawableVfFramePort = null;
            drawableTotalBackLand = null;
            drawableTotalBackPort = null;
            drawableTotalForeLand = null;
            drawableTotalForePort = null;
            drawableStorageItemBgNormal = null;
            drawableStorageItemBgPressed = null;
            drawableStorageItemBgSelected = null;

            // Color.
            colorVfBackground = 0;
            colorGripLabel = 0;
            colorScanOnGoing = 0;
            colorScanSuccess = 0;
            colorScanFailure = 0;
        }
    }

    /**
     * Load customized Plug-IN UI resources.
     *
     * @param context
     */
    public static void loadCustomizedUiResources(Context context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "loadCustomizedUiResources() : E");

        // Load custom package.
        mCustomResContainer.customPackage = getGlobalSharedPreferences()
                .getString(ViewFinderAnywhereConstants.KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE, null);
        Context remoteContext = null;
        if (mCustomResContainer.customPackage != null) {
            try {
                remoteContext = context.createPackageContext(
                        mCustomResContainer.customPackage,
                        Context.CONTEXT_RESTRICTED);
            } catch (PackageManager.NameNotFoundException e) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Plug-IN package can not be accessed.");
                remoteContext = null;
            }
        }

        ViewFinderAnywhereApplication.CustomizableResourceContainer customResContainer
                = ViewFinderAnywhereApplication.getCustomResContainer();
        customResContainer.resetResources();

        boolean isResourceLoadSuccess = false;
        if (remoteContext != null) {
            Resources res = remoteContext.getResources();

            // Drawable.
            final int resIdOnGoing = res.getIdentifier(
                    "overlay_view_finder_ongoing",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdGripLand = res.getIdentifier(
                    "overlay_view_finder_grip_landscape",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdGripPort = res.getIdentifier(
                    "overlay_view_finder_grip_portrait",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdFrameLand = res.getIdentifier(
                    "overlay_view_finder_frame_landscape",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdFramePort = res.getIdentifier(
                    "overlay_view_finder_frame_portrait",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdTotalBackLand = res.getIdentifier(
                    "overlay_view_finder_total_background_landscape",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdTotalBackPort = res.getIdentifier(
                    "overlay_view_finder_total_background_portrait",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdTotalForeLand = res.getIdentifier(
                    "overlay_view_finder_total_foreground_landscape",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdTotalForePort = res.getIdentifier(
                    "overlay_view_finder_total_foreground_portrait",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdStorageItemBgNormal = res.getIdentifier(
                    "storage_selector_item_background_normal",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdStorageItemBgPressed = res.getIdentifier(
                    "storage_selector_item_background_pressed",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);
            final int resIdStorageItemBgSelected = res.getIdentifier(
                    "storage_selector_item_background_selected",
                    ViewFinderAnywhereConstants.RES_TYPE_DRAWABLE,
                    mCustomResContainer.customPackage);

            // Color.
            final int resIdVfBackgroundColor = res.getIdentifier(
                    "viewfinder_background_color",
                    ViewFinderAnywhereConstants.RES_TYPE_COLOR,
                    mCustomResContainer.customPackage);
            final int resIdGripLabelColor = res.getIdentifier(
                    "viewfinder_grip_label_color",
                    ViewFinderAnywhereConstants.RES_TYPE_COLOR,
                    mCustomResContainer.customPackage);
            final int resIdScanIndicatorOnGoingColor = res.getIdentifier(
                    "viewfinder_scan_indicator_ongoing",
                    ViewFinderAnywhereConstants.RES_TYPE_COLOR,
                    mCustomResContainer.customPackage);
            final int resIdScanIndicatorSuccessColor = res.getIdentifier(
                    "viewfinder_scan_indicator_success",
                    ViewFinderAnywhereConstants.RES_TYPE_COLOR,
                    mCustomResContainer.customPackage);
            final int resIdScanIndicatorFailureColor = res.getIdentifier(
                    "viewfinder_scan_indicator_failure",
                    ViewFinderAnywhereConstants.RES_TYPE_COLOR,
                    mCustomResContainer.customPackage);

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

                // OK.
                isResourceLoadSuccess = true;
            } catch (Resources.NotFoundException e) {
                e.printStackTrace();
                if (Log.IS_DEBUG) Log.logDebug(TAG, "UI Plug-IN version conflicted.");
            }
        }

        if (!isResourceLoadSuccess) {
            customResContainer.resetResources();

            // Use default.
            Resources res = context.getResources();
            customResContainer.drawableNotificationOnGoing
                    = res.getDrawable(R.drawable.overlay_view_finder_ongoing);
            customResContainer.drawableStorageItemBgNormal
                    = res.getDrawable(R.drawable.storage_selector_item_background_normal);
            customResContainer.drawableStorageItemBgPressed
                    = res.getDrawable(R.drawable.storage_selector_item_background_pressed);
            customResContainer.drawableStorageItemBgSelected
                    = res.getDrawable(R.drawable.storage_selector_item_background_selected);
            customResContainer.colorVfBackground
                    = res.getColor(R.color.viewfinder_background_color);
            customResContainer.colorGripLabel
                    = res.getColor(R.color.viewfinder_grip_label_color);
            customResContainer.colorScanOnGoing
                    = res.getColor(R.color.viewfinder_scan_indicator_ongoing);
            customResContainer.colorScanSuccess
                    = res.getColor(R.color.viewfinder_scan_indicator_success);
            customResContainer.colorScanFailure
                    = res.getColor(R.color.viewfinder_scan_indicator_failure);
        }

        if (Log.IS_DEBUG) Log.logDebug(TAG, "loadCustomizedUiResources() : X");
    }

    /**
     * Total window configuration.
     */
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
     * @return
     */
    public static boolean isStorageSelectorEnabled() {
        Boolean isEnabled = getGlobalSharedPreferences().getBoolean(
                ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED,
                false);
        return isEnabled.booleanValue();
    }


    /**
     * Get global Firebase analytics interface.
     *
     * @return
     */
    public static FirebaseAnalytics getGlobalFirebaseAnalytics() {
        return mFirebaseAnalytics;
    }



}
