package com.fezrestia.android.viewfinderanywhere.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;

import com.fezrestia.android.lib.firebase.FirebaseAnalyticsController;
import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;
import com.fezrestia.android.viewfinderanywhere.storage.StorageController;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ViewFinderAnywhereSettingActivity extends PreferenceActivity {
    // Log tag.
    private static final String TAG = "ViewFinderAnywhereSettingActivity";

    // Notification ID.
    private static final int OVERLAY_TRIGGER_NOTIFICATION_ID = 1000;

    // UI-Plug-IN.
    private List<UiPlugInPackage> mUiPlugInPackageList = new ArrayList<UiPlugInPackage>();

    // Customized resources.
    private ViewFinderAnywhereApplication.CustomizableResourceContainer mCustomResContainer = null;

    private class UiPlugInPackage {
        public final String packageName;
        public final String plugInTitle;

        /**
         * CONSTRUCTOR.
         *
         * @param packageName
         * @param plugInTitle
         */
        public UiPlugInPackage(String packageName, String plugInTitle) {
            this.packageName = packageName;
            this.plugInTitle = plugInTitle;
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate()");
        super.onCreate(null);

        // Add view finder anywhere preferences.
        addPreferencesFromResource(R.xml.preferences_view_finder_anywhere);
    }

    @Override
    public void onResume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()");
        super.onResume();

        // Mandatory permission check.
        if (isFinishing()) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "App is in finishing sequence.");
            return;
        }
        if (checkMandatoryPermissions()) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Return immediately for permission.");
            return;
        }

        // Reset.
        mUiPlugInPackageList.clear();

        // Custom resource container.
        mCustomResContainer = ViewFinderAnywhereApplication.getCustomResContainer();

        // Query package manager.
        queryPackageManager();

        // Load current selected resources.
        ViewFinderAnywhereApplication.loadCustomizedUiResources(this);

        // Update preferences.
        updatePreferences();
        applyCurrentPreferences();

        // Update StorageSelector preferences.
        updateStorageSelectorRelatedPreferences();

        // Firebase analytics.
        ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController().createNewLogRequest()
                .setEvent(FirebaseAnalytics.Event.APP_OPEN)
                .setParam(FirebaseAnalytics.Param.ITEM_ID, TAG)
                .done();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        // NOP.
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        // NOP.
    }

    @Override
    public void onPause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()");
        super.onPause();
        // NOP.
    }

    @Override
    public void onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy()");
        super.onDestroy();
        // NOP.
    }

    private void queryPackageManager() {
        PackageManager pm = getPackageManager();

        // UI Plug-IN.
        Intent intent = new Intent(ViewFinderAnywhereConstants.INTENT_ACTION_REGISTER_UI_PLUG_IN);
        List<ResolveInfo> results = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo result : results) {
            ApplicationInfo info = result.activityInfo.applicationInfo;
            Context context;
            try {
                context = createPackageContext(
                        info.packageName,
                        Context.CONTEXT_RESTRICTED);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                continue;
            }

            final int titleResId = context.getResources().getIdentifier(
                    ViewFinderAnywhereConstants.RES_ID_STRING_PLUG_IN_TITLE,
                    ViewFinderAnywhereConstants.RES_TYPE_STRING,
                    info.packageName);

            UiPlugInPackage plugIn = null;
            try {
                plugIn = new UiPlugInPackage(
                    info.packageName,
                    context.getResources().getString(titleResId));
            } catch (Resources.NotFoundException e) {
                e.printStackTrace();
                continue;
            }

            mUiPlugInPackageList.add(plugIn);

            if (Log.IS_DEBUG) Log.logDebug(TAG, "UI-PLUG-IN Package = " + plugIn.packageName);
            if (Log.IS_DEBUG) Log.logDebug(TAG, "UI-PLUG-IN Package = " + plugIn.plugInTitle);
        }
    }

    private void updatePreferences() {
        ListPreference uiPlugInPackagePref = (ListPreference)
                findPreference(ViewFinderAnywhereConstants.KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE);

        // UI Plug-IN.
        String currentUiPlugInTitle = null;
        // Values.
        List<String> entries = new ArrayList<String>();
        List<String> entryValues = new ArrayList<String>();
        for (UiPlugInPackage eachPackage : mUiPlugInPackageList) {
            entries.add(eachPackage.plugInTitle);
            entryValues.add(eachPackage.packageName);

            if (eachPackage.packageName.equals(mCustomResContainer.customPackage)) {
                currentUiPlugInTitle = eachPackage.plugInTitle;
            }
        }
        // Update.
        uiPlugInPackagePref.setSummary(currentUiPlugInTitle);
        uiPlugInPackagePref.setEntries(entries.toArray(new String[]{}));
        uiPlugInPackagePref.setEntryValues(entryValues.toArray(new String[]{}));
    }

    private void applyCurrentPreferences() {
        // Update summary binding.
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_FOCUS_KEY_DOUBLE_CLICK));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_NOTIFICATION));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_VIEW_FINDER_SIZE));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_VIEW_FINDER_ASPECT));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.ViewFinderGripSize.KEY));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.ViewFinderGripPosition.KEY));
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_CAMERA_FUNCTION_API_LEVEL));

        findPreference(ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED)
                .setOnPreferenceChangeListener(mOnStorageSelectorPreferenceChangedListener);
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY)
                .setOnPreferenceChangeListener(mOnStorageSelectorPreferenceChangedListener);
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)
                .setOnPreferenceChangeListener(mOnStorageSelectorPreferenceChangedListener);
    }

    private final OnPreferenceChangeListenerImpl mOnPreferenceChangeListener
            = new OnPreferenceChangeListenerImpl();
    private class OnPreferenceChangeListenerImpl
            implements  Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String key = preference.getKey();
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                if (0 <= index) {
                    preference.setSummary(listPreference.getEntries()[index]);
                } else {
                    preference.setSummary(null);
                }

                // Ui Plug-IN.
                if (ViewFinderAnywhereConstants.KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE
                        .equals(preference.getKey())) {
                    mCustomResContainer.customPackage = (String) value;
                    // Reload resources.
                    ViewFinderAnywhereApplication.loadCustomizedUiResources(
                            ViewFinderAnywhereSettingActivity.this);

                    // For Firebase.
                    value = FirebaseAnalyticsController.getPkgNameValue((String) value);
                }

                // Firebase analytics.
                ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                        .createNewLogRequest()
                        .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                        .setParam(FirebaseAnalytics.Param.ITEM_ID, preference.getKey())
                        .setParam(FirebaseAnalytics.Param.ITEM_NAME, (String) value)
                        .done();
            } else if (preference instanceof CheckBoxPreference) {
                final boolean isChecked = ((Boolean) value).booleanValue();

                if (key == null) {
                    // NOP.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "CheckBox key == null");
                } else if (
                        ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_FOCUS_KEY_DOUBLE_CLICK
                        .equals(key)) {
                    // NOP.

                    // Firebase analytics.
                    ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                            .createNewLogRequest()
                            .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                            .setParam(FirebaseAnalytics.Param.ITEM_ID, key)
                            .setParam(
                                    FirebaseAnalytics.Param.ITEM_NAME,
                                    ViewFinderAnywhereConstants.getBooleanString(isChecked))
                            .done();
                } else if (
                        ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_NOTIFICATION
                        .equals(key)) {

                    NotificationManager notificationManager = (NotificationManager)
                            getSystemService(NOTIFICATION_SERVICE);
                    if (isChecked) {
                        // Intent.
                        Intent intent = new Intent(ViewFinderAnywhereConstants
                                .INTENT_ACTION_TRIGGER_OVERLAY_VIEW_FINDER);
                        intent.setPackage(getApplicationContext().getPackageName());
                        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

                        // Pending intent.
                        PendingIntent pendIntent = PendingIntent.getBroadcast(
                                getApplicationContext(),
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT);

                        // Notification.
                        Notification notification
                                = new Notification.Builder(getApplicationContext())
                                .setContentTitle(getString(R.string.notification_overlay_trigger))
                                .setSmallIcon(R.drawable.application_icon)
                                .setContentIntent(pendIntent)
                                .build();

                        // Notify.
                        notificationManager.notify(OVERLAY_TRIGGER_NOTIFICATION_ID, notification);
                    } else {
                        // Cancel.
                        notificationManager.cancel(OVERLAY_TRIGGER_NOTIFICATION_ID);
                    }

                    // Firebase analytics.
                    ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                            .createNewLogRequest()
                            .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                            .setParam(FirebaseAnalytics.Param.ITEM_ID, key)
                            .setParam(
                                    FirebaseAnalytics.Param.ITEM_NAME,
                                    ViewFinderAnywhereConstants.getBooleanString(isChecked))
                            .done();
                } else if (
                        ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE
                        .equals(key)) {
                    if (isChecked) {
                        // Start.
                        OverlayViewFinderController.LifeCycleTrigger.getInstance()
                                .requestStart(getApplicationContext());
                    } else {
                        // Remove.
                        OverlayViewFinderController.LifeCycleTrigger.getInstance()
                                .requestStop(getApplicationContext());
                    }

                    // Firebase analytics.
                    ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                            .createNewLogRequest()
                            .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                            .setParam(FirebaseAnalytics.Param.ITEM_ID, key)
                            .setParam(
                                    FirebaseAnalytics.Param.ITEM_NAME,
                                    ViewFinderAnywhereConstants.getBooleanString(isChecked))
                            .done();
                } else {
                    // NOP.
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "Unexpected CheckBox preference.");
                }
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);

                // Firebase analytics.
                ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                        .createNewLogRequest()
                        .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                        .setParam(FirebaseAnalytics.Param.ITEM_ID, key)
                        .setParam(FirebaseAnalytics.Param.ITEM_NAME, (String) value)
                        .done();
            }
            return true;
        }
    }

    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(mOnPreferenceChangeListener);

        if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            Object value = ((ListPreference) preference).getValue();

            int index = -1;
            if (value != null) {
                index = listPreference.findIndexOfValue((String) value);
            }

            // Set the summary to reflect the new value.
            if (0 <= index) {
                preference.setSummary(listPreference.getEntries()[index]);
            } else {
                preference.setSummary(null);
            }
        }
    }



    private final OnStorageSelectorPreferenceChangedListener
            mOnStorageSelectorPreferenceChangedListener
             = new OnStorageSelectorPreferenceChangedListener();
    private class OnStorageSelectorPreferenceChangedListener
            implements  Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StorageSelector.onPreferenceChange() : E");

            String key = preference.getKey();
            if (Log.IS_DEBUG) Log.logDebug(TAG, "[key = " + key + "]");

            if (key == null) {
                // NOP.
            } else if (ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED
                    .equals(key)) {
                final boolean isEnabled = ((Boolean) newValue).booleanValue();

                if (!isEnabled) {
                    // Reset all storage settings.
                    ViewFinderAnywhereApplication.getGlobalSharedPreferences().edit().putStringSet(
                          ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                          null).commit();
                    ViewFinderAnywhereApplication.getGlobalSharedPreferences().edit().putStringSet(
                          ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY,
                          null).commit();

                    // Disable.
                    setEnabledStorageSelectorRelatedPreferences(false);
                } else {
                    // Enable.
                    setEnabledStorageSelectorRelatedPreferences(true);
                }
            } else if (ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY
                    .equals(key)) {
                String newDir = (String) newValue;
                if (Log.IS_DEBUG) Log.logDebug(TAG, "[NewDirectory = " + newDir + "]");

                // Validation.
                if (!newDir.isEmpty()) {
                    // Create new directory.
                    boolean isSuccess = StorageController.createNewContentsDirectory(newDir);

                    if (isSuccess) {
                        // Update available list.
                        updateStorageSelectorTargetDirectoryList();
                    } else {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "New dir creation FAILED");
                    }
                }

                // Do not update setting.
                return false;
            } else if (ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY
                    .equals(key)) {
                Set<String> validDir = (Set<String>) newValue;

                // Add default storage.
                validDir.add(StorageController.DEFAULT_STORAGE_DIR_NAME);

                if (Log.IS_DEBUG) {
                    for (String eachDir : validDir) {
                        Log.logDebug(TAG, "[ValidDirectory = " + eachDir + "]");
                    }
                }

                // Store.
                ViewFinderAnywhereApplication.getGlobalSharedPreferences().edit().putStringSet(
                        ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                        validDir)
                        .commit();
            } else {
                // NOP.
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StorageSelector.onPreferenceChange() : X");
            return true;
        }
    }

    private void updateStorageSelectorRelatedPreferences() {
        // Check enabled.
        boolean isEnabled = ViewFinderAnywhereApplication.getGlobalSharedPreferences().getBoolean(
                ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED,
                false);
        setEnabledStorageSelectorRelatedPreferences(isEnabled);

        updateStorageSelectorTargetDirectoryList();
    }

    //TODO:This function takes much time.
    private void updateStorageSelectorTargetDirectoryList() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateStorageSelectorTargetDirectoryList() : E");

        // Create root dir.
        StorageController.createContentsRootDirectory();

        // Scan directory list.
        File contentsRoot = new File(StorageController.getApplicationStorageRootPath());
        File[] fileList = contentsRoot.listFiles();
        // Scan files.
        if (Log.IS_DEBUG) Log.logDebug(TAG, "listFiles() DONE");
        if (Log.IS_DEBUG) {
            if (fileList != null) {
                for (File eachFile : fileList) {
                    Log.logDebug(TAG, "path = " + eachFile.getPath());
                }
            } else {
                Log.logDebug(TAG, "File List is NULL");
            }
        }
        // Scan directories.
        List<File> dirList = new ArrayList<>();
        if (fileList != null) {
            for (File eachFile : fileList) {
                if (!eachFile.isFile()) {
                    // Directory.
                    dirList.add(eachFile);
                }
            }
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "!isFile() DONE");
        if (Log.IS_DEBUG) {
            for (File eachDir : dirList) {
                Log.logDebug(TAG, "dir = " + eachDir.getName());
            }
        }

        // Dir list.
        List<String> dirNameList = new ArrayList<>();
        for (File eachDir : dirList) {
            dirNameList.add(eachDir.getName());
        }

        // Checked list.
        Set<String> selectableSet = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getStringSet(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY, null);
        Set<String> validValues = new HashSet<>();
        if (selectableSet != null) {
            for (String eachDir : selectableSet) {
                if (dirNameList.contains(eachDir)) {
                    validValues.add(eachDir);
                }
            }
        }

        // Apply to preferences.
        MultiSelectListPreference pref = (MultiSelectListPreference) findPreference(
                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY);

        String[] entries = dirNameList.toArray(new String[]{});

        pref.setEntries(entries);
        pref.setEntryValues(entries);
        pref.setValues(validValues);

        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateStorageSelectorTargetDirectoryList() : X");
    }

    private void setEnabledStorageSelectorRelatedPreferences(boolean isEnabled) {
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY)
                .setEnabled(isEnabled);
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)
                .setEnabled(isEnabled);
    }

    //// RUNTIME PERMISSION ///////////////////////////////////////////////////////////////////////

    private static final int REQUEST_CODE_MANAGE_OVERLAY_PERMISSION = 100;
    private static final int REQUEST_CODE_MANAGE_PERMISSIONS = 200;

    private boolean isRuntimePermissionRequired() {
        return Build.VERSION_CODES.M <= Build.VERSION.SDK_INT;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean isSystemAlertWindowPermissionGranted() {
        return Settings.canDrawOverlays(this);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean isCameraPermissionGranted() {
        return checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean isWriteStoragePermissionGranted() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean isReadStoragePermissionGranted() {
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check permission.
     *
     * @return immediateReturnRequired
     */
    @TargetApi(Build.VERSION_CODES.M)
    private boolean checkMandatoryPermissions() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "checkMandatoryPermissions()");

        if (isRuntimePermissionRequired()) {
            if (!isSystemAlertWindowPermissionGranted()) {
                // Start permission setting.
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_MANAGE_OVERLAY_PERMISSION);

                return true;
            }

            List<String> permissions = new ArrayList<String>();

            if (!isCameraPermissionGranted()) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (!isWriteStoragePermissionGranted()) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (!isReadStoragePermissionGranted()) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }

            if (!permissions.isEmpty()) {
                requestPermissions(
                        permissions.toArray(new String[]{}),
                        REQUEST_CODE_MANAGE_PERMISSIONS);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onActivityResult()");

        if (requestCode == REQUEST_CODE_MANAGE_OVERLAY_PERMISSION) {
            if (!isSystemAlertWindowPermissionGranted()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "  Overlay permission is not granted yet.");
                finish();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int  requestCode,
            String[] permissions,
            int[] grantResults) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onRequestPermissionsResult()");

        if (requestCode == REQUEST_CODE_MANAGE_PERMISSIONS) {
            if (!isCameraPermissionGranted()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG,
                        "  Camera permission is not granted yet.");
                finish();
            }
            if (!isWriteStoragePermissionGranted()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG,
                        "  Write storage permission is not granted yet.");
                finish();
            }
            if (!isReadStoragePermissionGranted()) {
                if (Log.IS_DEBUG) Log.logDebug(TAG,
                        "  Read storage permission is not granted yet.");
                finish();
            }
        }
    }



}
