package com.fezrestia.android.viewfinderanywhere.activity

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.MultiSelectListPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.provider.Settings

import com.fezrestia.android.lib.firebase.FirebaseAnalyticsController
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication.CustomizableResourceContainer
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.control.OnOffTrigger
import com.fezrestia.android.viewfinderanywhere.storage.DirFileUtil
import com.google.firebase.analytics.FirebaseAnalytics

import java.io.File

class ViewFinderAnywhereSettingActivity : PreferenceActivity() {
    companion object {
        // Log tag.
        const val TAG = "VFA:SettingActivity"

        // Runtime permission.
        private const val REQUEST_CODE_MANAGE_OVERLAY_PERMISSION = 100
        private const val REQUEST_CODE_MANAGE_PERMISSIONS = 200
    }

    // UI-Plug-IN.
    private val mUiPlugInPackageList = mutableListOf<UiPlugInPackage>()

    // Customized resources.
    private lateinit var mCustomResContainer: CustomizableResourceContainer

    // UI Plug-IN container class.
    private inner class UiPlugInPackage(
            internal val packageName: String,
            internal val plugInTitle: String)

    // This activity does not use fragment.
    public override fun isValidFragment(fragmentName: String): Boolean = false

    override fun onCreate(bundle: Bundle?) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate()")
        super.onCreate(null)

        // Add view finder anywhere preferences.
        @Suppress("DEPRECATION")
        addPreferencesFromResource(R.xml.preferences_view_finder_anywhere)
    }

    override fun onResume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")
        super.onResume()

        // Mandatory permission check.
        if (isFinishing) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "App is in finishing sequence.")
            return
        }
        if (checkMandatoryPermissions()) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Return immediately for permission.")
            return
        }

        // Reset.
        mUiPlugInPackageList.clear()

        // Custom resource container.
        mCustomResContainer = ViewFinderAnywhereApplication.getCustomResContainer()

        // Query package manager.
        queryPackageManager()

        // Load current selected resources.
        ViewFinderAnywhereApplication.loadCustomizedUiResources(this)

        // Update preferences.
        updatePreferences()
        applyCurrentPreferences()

        // Update StorageSelector preferences.
        updateStorageSelectorRelatedPreferences()

        // Firebase analytics.
        ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController().createNewLogRequest()
                .setEvent(FirebaseAnalytics.Event.APP_OPEN)
                .setParam(FirebaseAnalytics.Param.ITEM_ID, TAG)
                .done()
    }

    override fun onPause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")
        super.onPause()

        // WORKAROUND
        //   If this activity is not finished onPause(), shared preferences value updated
        //   by other components (e.g. Receiver) will not be applied to UI after onResume().
        //   So, forced finish this activity onPause() to ensure load latest shared preferences.
        finish()
    }

    override fun onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy()")
        super.onDestroy()
        // NOP.
    }

    private fun queryPackageManager() {
        val pm = packageManager

        // UI Plug-IN list.
        val intent = Intent(ViewFinderAnywhereConstants.INTENT_ACTION_REGISTER_UI_PLUG_IN)
        val results = pm.queryIntentActivities(intent, 0)


        // Get plug-in.
        results.forEach { result ->
            // Create plug-in package context.
            val info = result.activityInfo.applicationInfo
            val context: Context
            try {
                context = createPackageContext(
                        info.packageName,
                        Context.CONTEXT_RESTRICTED)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                return@forEach
            }

            // Get plug-in title resource ID.
            val titleResId = context.resources.getIdentifier(
                    ViewFinderAnywhereConstants.RES_ID_STRING_PLUG_IN_TITLE,
                    ViewFinderAnywhereConstants.RES_TYPE_STRING,
                    info.packageName)

            // Get title resource.
            val titleRes = context.resources.getString(titleResId)

            // Create container.
            val plugIn = UiPlugInPackage(info.packageName, titleRes)

            mUiPlugInPackageList.add(plugIn)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "UI-PLUG-IN Package = " + plugIn.packageName)
            if (Log.IS_DEBUG) Log.logDebug(TAG, "UI-PLUG-IN Title   = " + plugIn.plugInTitle)
        }
    }

    private fun updatePreferences() {
        @Suppress("DEPRECATION")
        val uiPlugInPref = findPreference(
                ViewFinderAnywhereConstants.KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE)
                as ListPreference

        // UI Plug-IN.
        var currentUiPlugInTitle: String? = null

        // Values.
        val entries = mutableListOf<String>()
        val entryValues = mutableListOf<String>()

        mUiPlugInPackageList.forEach { plugin ->
            entries.add(plugin.plugInTitle)
            entryValues.add(plugin.packageName)

            if (plugin.packageName == mCustomResContainer.customPackage) {
                // Current plug-in.
                currentUiPlugInTitle = plugin.plugInTitle
            }
        }

        // Update.
        uiPlugInPref.summary = currentUiPlugInTitle
        uiPlugInPref.entries = entries.toTypedArray()
        uiPlugInPref.entryValues = entryValues.toTypedArray()
    }

    @Suppress("DEPRECATION")
    private fun applyCurrentPreferences() {
        // Update summary binding.
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE))
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_VIEW_FINDER_SIZE))
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_VIEW_FINDER_ASPECT))
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE))
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.ViewFinderGripSize.KEY))
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.ViewFinderGripPosition.KEY))
        bindPreferenceSummaryToValue(findPreference(
                ViewFinderAnywhereConstants.KEY_CAMERA_FUNCTION_API_LEVEL))

        val storagePrefListener = OnStorageSelectorPreferenceChangedListener()
        findPreference(ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED)
                .onPreferenceChangeListener = storagePrefListener
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY)
                .onPreferenceChangeListener = storagePrefListener
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)
                .onPreferenceChangeListener = storagePrefListener
    }

    private inner class OnPreferenceChangeListenerImpl : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, value: Any): Boolean {
            val key = preference.key
            val stringValue = value.toString()
            val firebaseValue: String

            when (preference) {
                is ListPreference -> {
                    val index = preference.findIndexOfValue(stringValue)

                    // Set the summary to reflect the new value.
                    if (0 <= index) {
                        preference.setSummary(preference.entries[index])
                    } else {
                        preference.setSummary(null)
                    }

                    // Ui Plug-IN.
                    when (key) {
                        ViewFinderAnywhereConstants.KEY_VIEW_FINDER_UI_PLUG_IN_PACKAGE -> {
                            mCustomResContainer.customPackage = value as String

                            // Reload resources.
                            ViewFinderAnywhereApplication.loadCustomizedUiResources(
                                    this@ViewFinderAnywhereSettingActivity)

                            // For Firebase.
                            firebaseValue = FirebaseAnalyticsController.getPkgNameValue(value)
                        }

                        else -> {
                            firebaseValue = stringValue
                        }
                    }

                    // Firebase analytics.
                    ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                            .createNewLogRequest()
                            .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                            .setParam(FirebaseAnalytics.Param.ITEM_ID, preference.getKey())
                            .setParam(FirebaseAnalytics.Param.ITEM_NAME, firebaseValue)
                            .done()
                }

                is CheckBoxPreference -> {
                    val isChecked = value as Boolean

                    when (key) {
                        ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE -> {
                            if (isChecked) {
                                // Start.
                                OnOffTrigger.requestStart(applicationContext)
                            } else {
                                // Remove.
                                OnOffTrigger.requestStop(applicationContext)
                            }

                            // Firebase analytics.
                            ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                                    .createNewLogRequest()
                                    .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                                    .setParam(FirebaseAnalytics.Param.ITEM_ID, key)
                                    .setParam(
                                            FirebaseAnalytics.Param.ITEM_NAME,
                                            ViewFinderAnywhereConstants.getBooleanString(isChecked))
                                    .done()
                        }

                        else -> {
                            // NOP.
                        }
                    }
                }

                else -> {
                    // For all other preferences, set the summary to the value's
                    // simple string representation.
                    preference.summary = stringValue
                    firebaseValue = value as String

                    // Firebase analytics.
                    ViewFinderAnywhereApplication.getGlobalFirebaseAnalyticsController()
                            .createNewLogRequest()
                            .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                            .setParam(FirebaseAnalytics.Param.ITEM_ID, key)
                            .setParam(FirebaseAnalytics.Param.ITEM_NAME, firebaseValue)
                            .done()
                }
            }

            return true
        }
    }

    private fun bindPreferenceSummaryToValue(preference: Preference) {
        // Set the listener to watch for value changes.
        preference.onPreferenceChangeListener = OnPreferenceChangeListenerImpl()

        if (preference is ListPreference) {
            val value: String? = preference.value

            var index = -1
            if (value != null) {
                index = preference.findIndexOfValue(value)
            }

            // Set the summary to reflect the new value.
            if (0 <= index) {
                preference.setSummary(preference.entries[index])
            } else {
                preference.setSummary(null)
            }
        }
    }

    private inner class OnStorageSelectorPreferenceChangedListener
            : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "StorageSelector.onPreferenceChange() : E")

            val key = preference.key
            if (Log.IS_DEBUG) Log.logDebug(TAG, "key = $key")

            when (key) {
                ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED -> {
                    val isEnabled = newValue as Boolean

                    if (!isEnabled) {
                        // Reset all storage settings.
                        ViewFinderAnywhereApplication.getGlobalSharedPreferences().edit()
                                .putStringSet(
                                        ViewFinderAnywhereConstants
                                                .KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                                        null)
                                .apply()
                        ViewFinderAnywhereApplication.getGlobalSharedPreferences().edit()
                                .putStringSet(
                                        ViewFinderAnywhereConstants
                                                .KEY_STORAGE_SELECTOR_STORE_TARGET_DIRECTORY,
                                        null)
                                .apply()

                        // Disable.
                        setEnabledStorageSelectorRelatedPreferences(false)
                    } else {
                        // Enable.
                        setEnabledStorageSelectorRelatedPreferences(true)
                    }
                }

                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY -> {
                    val newDir = newValue as String
                    if (Log.IS_DEBUG) Log.logDebug(TAG, "NewDirectory = $newDir")

                    // Validation.
                    if (newDir.isNotEmpty()) {
                        // Create new directory.
                        val isSuccess = DirFileUtil.createNewContentsDirectory(newDir)

                        if (isSuccess) {
                            // Update available list.
                            updateStorageSelectorTargetDirectoryList()
                        } else {
                            if (Log.IS_DEBUG) Log.logDebug(TAG, "New dir creation FAILED")
                        }
                    }

                    // Do not update setting.
                    return false
                }

                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY -> {
                    @Suppress("UNCHECKED_CAST")
                    val validDirSet = newValue as MutableSet<String>

                    // Add default storage.
                    validDirSet.add(DirFileUtil.DEFAULT_STORAGE_DIR_NAME)

                    if (Log.IS_DEBUG) {
                        validDirSet.forEach { dir -> Log.logDebug(TAG, "ValidDir = $dir") }
                    }

                    // Store.
                    ViewFinderAnywhereApplication.getGlobalSharedPreferences().edit()
                            .putStringSet(
                                    ViewFinderAnywhereConstants
                                            .KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                                    validDirSet)
                            .apply()
                }

                else -> {
                    // NOP.
                }
            }

            if (Log.IS_DEBUG) Log.logDebug(TAG, "StorageSelector.onPreferenceChange() : X")
            return true
        }
    }

    private fun updateStorageSelectorRelatedPreferences() {
        // Check enabled.
        val isEnabled = ViewFinderAnywhereApplication.getGlobalSharedPreferences().getBoolean(
                ViewFinderAnywhereConstants.KEY_IS_STORAGE_SELECTOR_ENABLED,
                false)
        setEnabledStorageSelectorRelatedPreferences(isEnabled)

        updateStorageSelectorTargetDirectoryList()
    }

    //TODO:This function takes much time.
    private fun updateStorageSelectorTargetDirectoryList() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateStorageSelectorTargetDirectoryList() : E")

        // Create root dir.
        DirFileUtil.createContentsRootDirectory()

        // Scan directory list.
        val contentsRoot = File(DirFileUtil.getApplicationStorageRootPath())
        val fileList: Array<File> = contentsRoot.listFiles()
        if (Log.IS_DEBUG) Log.logDebug(TAG, "listFiles() DONE")
        if (Log.IS_DEBUG) fileList.forEach { file -> Log.logDebug(TAG, "path = " + file.path) }

        // Scan directories.
        val dirList = mutableListOf<File>()
        fileList.forEach { file ->
            if (file.isDirectory) {
                dirList.add(file)
            }
        }
        if (Log.IS_DEBUG) Log.logDebug(TAG, "dirList  DONE")
        if (Log.IS_DEBUG) dirList.forEach { dir -> Log.logDebug(TAG, "dir = " + dir.path) }

        // Dir path list.
        val dirPathList = mutableListOf<String>()
        dirList.forEach { dir -> dirPathList.add(dir.name) }

        // Checked list.
        val selectableSet: Set<String> = ViewFinderAnywhereApplication.getGlobalSharedPreferences()
                .getStringSet(
                        ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                        setOf<String>()) as Set<String>
        val validValues = mutableSetOf<String>()
        selectableSet.forEach { selectable ->
            if (dirPathList.contains(selectable)) {
                validValues.add(selectable)
            }
        }

        // Apply to preferences.
        @Suppress("DEPRECATION")
        val pref = findPreference(
                ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)
                as MultiSelectListPreference
        val entries = dirPathList.toTypedArray()
        pref.entries = entries
        pref.entryValues = entries
        pref.values = validValues

        if (Log.IS_DEBUG) Log.logDebug(TAG, "updateStorageSelectorTargetDirectoryList() : X")
    }

    @Suppress("DEPRECATION")
    private fun setEnabledStorageSelectorRelatedPreferences(isEnabled: Boolean) {
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY)
                .isEnabled = isEnabled
        findPreference(ViewFinderAnywhereConstants.KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)
                .isEnabled = isEnabled
    }

    private val isRuntimePermissionRequired: Boolean
        get() = Build.VERSION_CODES.M <= Build.VERSION.SDK_INT

    private val isSystemAlertWindowPermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = Settings.canDrawOverlays(this)

    private val isCameraPermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = (checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED)

    private val isWriteStoragePermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)

    private val isReadStoragePermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)

    /**
     * Check permission.

     * @return immediateReturnRequired
     */
    @TargetApi(Build.VERSION_CODES.M)
    private fun checkMandatoryPermissions(): Boolean {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "checkMandatoryPermissions()")

        if (isRuntimePermissionRequired) {
            if (!isSystemAlertWindowPermissionGranted) {
                // Start permission setting.
                val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQUEST_CODE_MANAGE_OVERLAY_PERMISSION)

                return true
            }

            val permissions = mutableListOf<String>()

            if (!isCameraPermissionGranted) {
                permissions.add(Manifest.permission.CAMERA)
            }
            if (!isWriteStoragePermissionGranted) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (!isReadStoragePermissionGranted) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            return if (permissions.isNotEmpty()) {
                requestPermissions(
                        permissions.toTypedArray(),
                        REQUEST_CODE_MANAGE_PERMISSIONS)
                true
            } else {
                false
            }
        } else {
            return false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onActivityResult()")

        if (requestCode == REQUEST_CODE_MANAGE_OVERLAY_PERMISSION) {
            if (!isSystemAlertWindowPermissionGranted) {
                Log.logError(TAG, "Overlay permission is not granted yet.")
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onRequestPermissionsResult()")

        if (requestCode == REQUEST_CODE_MANAGE_PERMISSIONS) {
            if (!isCameraPermissionGranted) {
                Log.logError(TAG, "Camera permission is not granted yet.")
                finish()
            }
            if (!isWriteStoragePermissionGranted) {
                Log.logError(TAG,"Write storage permission is not granted yet.")
                finish()
            }
            if (!isReadStoragePermissionGranted) {
                Log.logError(TAG,"  Read storage permission is not granted yet.")
                finish()
            }
        }
    }
}
