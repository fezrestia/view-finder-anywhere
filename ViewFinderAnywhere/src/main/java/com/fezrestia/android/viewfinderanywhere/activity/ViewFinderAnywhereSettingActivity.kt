@file:Suppress("PrivatePropertyName")

package com.fezrestia.android.viewfinderanywhere.activity

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*

import com.fezrestia.android.lib.firebase.FirebaseAnalyticsController
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.config.options.CameraApiLevel
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAspect
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderSize
import com.fezrestia.android.viewfinderanywhere.control.OnOffTrigger
import com.fezrestia.android.viewfinderanywhere.plugin.ui.loadCustomizedUiResources
import com.fezrestia.android.viewfinderanywhere.storage.DirFileUtil
import com.google.firebase.analytics.FirebaseAnalytics

import java.io.File

class ViewFinderAnywhereSettingActivity : AppCompatActivity() {
    companion object {
        // Log tag.
        const val TAG = "VFA:SettingActivity"

        // Runtime permission.
        private const val REQUEST_CODE_MANAGE_OVERLAY_PERMISSION = 100
        private const val REQUEST_CODE_MANAGE_PERMISSIONS = 200
    }

    /**
     * Fragment for Settings.
     */
    class SettingFragment : PreferenceFragmentCompat() {
        private val TAG = "SettingFragment"

        private val SP_KEY_OVERLAY_VIEW_FINDER_ENABLE_DISABLE = "sp-key-overlay-view-finder-enable-disable"
        private val SP_KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY = "sp-key-storage-selector-create-new-directory"

        private val RES_ID_STRING_PLUG_IN_TITLE = "plug_in_title"

        private val onChangeListenerImpl = OnChangeListenerImpl()

        // UI Plug-IN container class.
        private inner class UiPlugInPackage(
                internal val packageName: String,
                internal val plugInTitle: String)

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_view_finder_anywhere, rootKey)

            // Setup static preference options.

            // API Level.
            val apiLevelPref: ListPreference = findPreference(CameraApiLevel.key)!!
            val apiLevels: Array<String> = CameraApiLevel.values()
                    .map { it.toString() }.toTypedArray()
            apiLevelPref.entries = apiLevels
            apiLevelPref.entryValues = apiLevels
            apiLevelPref.onPreferenceChangeListener = onChangeListenerImpl

            // View finder size.
            val vfSizePref: ListPreference = findPreference(ViewFinderSize.key)!!
            val sizes: Array<String> = ViewFinderSize.values()
                    .map { it.toString() }.toTypedArray()
            vfSizePref.entries = sizes
            vfSizePref.entryValues = sizes
            vfSizePref.onPreferenceChangeListener = onChangeListenerImpl

            // View finder aspect.
            val vfAspectPref: ListPreference = findPreference(ViewFinderAspect.key)!!
            val aspects: Array<String> = ViewFinderAspect.values()
                    .map { it.toString() }.toTypedArray()
            vfAspectPref.entries = aspects
            vfAspectPref.entryValues = aspects
            vfAspectPref.onPreferenceChangeListener = onChangeListenerImpl

            // Storage selector En/Disable.
            val storagePref: SwitchPreference = findPreference(Constants.SP_KEY_IS_STORAGE_SELECTOR_ENABLED)!!
            storagePref.onPreferenceChangeListener = onChangeListenerImpl

            // Add new directory.
            val addNewDirPref: EditTextPreference = findPreference(SP_KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY)!!
            addNewDirPref.onPreferenceChangeListener = onChangeListenerImpl
        }

        override fun onStart() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStart()")
            super.onStart()

            // Setup dynamic preference options.

            // En/Disable.
            val enDisPref: SwitchPreference = findPreference(SP_KEY_OVERLAY_VIEW_FINDER_ENABLE_DISABLE)!!
            enDisPref.isChecked = App.isOverlayViewFinderEnabled
            enDisPref.onPreferenceChangeListener = onChangeListenerImpl

            // UI PlugIN Packages.
            val pluginPref: ListPreference = findPreference(
                    Constants.SP_KEY_UI_PLUGIN_PACKAGE)!!
            val plugins = queryUiPlugInPackages()
            val pluginEntries = plugins.map { it.plugInTitle }.toTypedArray()
            val pluginValues = plugins.map { it.packageName }.toTypedArray()
            pluginPref.entries = pluginEntries
            pluginPref.entryValues = pluginValues
            pluginPref.onPreferenceChangeListener = onChangeListenerImpl

            // Update storage related pref state.
            val storagePref: SwitchPreference = findPreference(Constants.SP_KEY_IS_STORAGE_SELECTOR_ENABLED)!!
            setEnabledStorageSelectorRelatedPreferences(storagePref.isChecked)

            // Storage selector selectables.
            updateStorageSelectorSelectableDirectoryList()

        }

        override fun onStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStop()")
            super.onStop()

            // NOP.

        }

        private inner class OnChangeListenerImpl : Preference.OnPreferenceChangeListener {
            override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
                var firebaseValue: String? = null

                when (preference?.key) {
                    SP_KEY_OVERLAY_VIEW_FINDER_ENABLE_DISABLE -> {
                        val isChecked: Boolean = newValue as Boolean

                        if (isChecked) {
                            OnOffTrigger.requestStart(requireContext())
                        } else {
                            OnOffTrigger.requestStop(requireContext())
                        }

                        firebaseValue = Constants.getBooleanString(isChecked)
                    }

                    CameraApiLevel.key -> {
                        firebaseValue = newValue as String
                    }

                    Constants.SP_KEY_UI_PLUGIN_PACKAGE -> {
                        val customPackage: String = newValue as String

                        // Reload resources.
                        loadCustomizedUiResources(requireContext(), customPackage)

                        // For Firebase.
                        firebaseValue = FirebaseAnalyticsController.getPkgNameValue(customPackage)
                    }

                    ViewFinderSize.key -> {
                        firebaseValue = newValue as String
                    }

                    ViewFinderAspect.key -> {
                        firebaseValue = newValue as String
                    }

                    Constants.SP_KEY_IS_STORAGE_SELECTOR_ENABLED -> {
                        val isEnabled: Boolean = newValue as Boolean

                        if (!isEnabled) {
                            // Reset all storage settings.
                            App.sp.edit().putStringSet(
                                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                                    null)
                                    .apply()
                            App.sp.edit().putStringSet(
                                    Constants.SP_KEY_STORAGE_SELECTOR_TARGET_DIRECTORY,
                                    null)
                                    .apply()

                            setEnabledStorageSelectorRelatedPreferences(false)
                        } else {
                            setEnabledStorageSelectorRelatedPreferences(true)
                        }
                    }

                    SP_KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY -> {
                        val newDir: String = newValue as String

                        if (Log.IS_DEBUG) Log.logDebug(TAG, "NewDirectory = $newDir")

                        // Validation.
                        if (newDir.isNotEmpty()) {
                            // Create new directory.
                            val isSuccess = DirFileUtil.createNewContentsDirectory(
                                    requireContext(),
                                    newDir)

                            if (isSuccess) {
                                // Update available list.
                                updateStorageSelectorSelectableDirectoryList()
                            } else {
                                if (Log.IS_DEBUG) Log.logDebug(TAG, "New dir creation FAILED")
                            }
                        }
                    }

                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY -> {
                        @Suppress("UNCHECKED_CAST")
                        val validDirSet: MutableSet<String> = newValue as MutableSet<String>

                        // Add default storage.
                        validDirSet.add(DirFileUtil.DEFAULT_STORAGE_DIR_NAME)

                        if (Log.IS_DEBUG) validDirSet.forEach { dir -> Log.logDebug(TAG, "ValidDir = $dir") }

                        // Store.
                        App.sp.edit().putStringSet(
                                Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                                validDirSet)
                                .apply()
                    }

                    else -> {
                        throw RuntimeException("UnSupported Preference : key=${preference?.key}")
                    }
                }

                if (firebaseValue != null) {
                    // Firebase analytics.
                    App.firebase.createNewLogRequest()
                            .setEvent(FirebaseAnalytics.Event.SELECT_CONTENT)
                            .setParam(FirebaseAnalytics.Param.ITEM_ID, preference.key)
                            .setParam(FirebaseAnalytics.Param.ITEM_NAME, firebaseValue)
                            .done()
                }

                return true
            }
        }

        private fun setEnabledStorageSelectorRelatedPreferences(isEnabled: Boolean) {
            val createPref: EditTextPreference = findPreference(
                    SP_KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY)!!
            createPref.isEnabled = isEnabled

            val selectablePref: MultiSelectListPreference = findPreference(
                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)!!
            selectablePref.isEnabled = isEnabled
        }

        private fun queryUiPlugInPackages(): MutableList<UiPlugInPackage> {
            val pm = this.requireContext().packageManager

            val plugins: MutableList<UiPlugInPackage> = mutableListOf()

            val intent = Intent(Constants.INTENT_ACTION_REGISTER_UI_PLUG_IN)
            val results = pm.queryIntentActivities(intent, 0)
            results.forEach { result ->
                // Create plug-in package context.
                val info = result.activityInfo.applicationInfo
                val context: Context
                try {
                    context = this.requireContext().createPackageContext(
                            info.packageName,
                            Context.CONTEXT_RESTRICTED)
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                    return@forEach
                }

                // Get plug-in title resource ID.
                val titleResId = context.resources.getIdentifier(
                        RES_ID_STRING_PLUG_IN_TITLE,
                        "string",
                        info.packageName)

                // Get title resource.
                val titleRes = context.resources.getString(titleResId)

                // Create container.
                val plugIn = UiPlugInPackage(info.packageName, titleRes)

                plugins.add(plugIn)

                if (Log.IS_DEBUG) {
                    Log.logDebug(TAG, "UI-PLUG-IN Package = " + plugIn.packageName)
                    Log.logDebug(TAG, "UI-PLUG-IN Title   = " + plugIn.plugInTitle)
                }
            }

            return plugins.toMutableList()
        }

        //TODO:This function takes much time.
        fun updateStorageSelectorSelectableDirectoryList() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "updateStorageSelectorSelectableDirectoryList() : E")

            // Create root dir.
            DirFileUtil.createContentsRootDirectory(requireContext())

            // Scan directory list.
            val contentsRoot = File(DirFileUtil.getApplicationStorageRootPath(requireContext()))
            val fileList: Array<out File>? = contentsRoot.listFiles()

            if (fileList == null) {
                // If before runtime permission process done, fileList is null.
                // In that case, return here and retry after onActivityResults.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "contentsRoot.listFiles() == null")
                return
            }

            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "contentsRoot = ${contentsRoot.path}")
                Log.logDebug(TAG, "listFiles() DONE")
//                fileList.forEach { file -> Log.logDebug(TAG, "path = ${file.path}") }
            }

            // Scan directories.
            val dirPathList = mutableListOf<String>()
            fileList.forEach { file ->
                if (file.isDirectory) {
                    dirPathList.add(file.name)
                }
            }
            if (Log.IS_DEBUG) {
                Log.logDebug(TAG, "dirList  DONE")
                dirPathList.forEach { dirPath -> Log.logDebug(TAG, "dir = $dirPath") }
            }

            // Existing path is valid for selected values.
            val selectableSet = App.sp.getStringSet(
                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                    setOf<String>()) as Set<String>
            val validValues = mutableSetOf<String>()
            selectableSet.forEach { selectable ->
                if (dirPathList.contains(selectable)) {
                    validValues.add(selectable)
                }
            }

            // Apply to preferences.
            val pref: MultiSelectListPreference = findPreference(
                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)!!
            val entries = dirPathList.toTypedArray()
            pref.entries = entries
            pref.entryValues = entries
            pref.values = validValues
            pref.onPreferenceChangeListener = onChangeListenerImpl

            if (Log.IS_DEBUG) Log.logDebug(TAG, "updateStorageSelectorSelectableDirectoryList() : X")
        }

    }

    //// MAIN ACTIVITY

    private lateinit var settingFragment: SettingFragment

    override fun onCreate(bundle: Bundle?) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate()")
        super.onCreate(null)

        setContentView(R.layout.setting_activity)

        settingFragment = SettingFragment()

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.preference_fragment_container, settingFragment)
                .commit()
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

        // Firebase analytics.
        App.firebase.createNewLogRequest()
                .setEvent(FirebaseAnalytics.Event.APP_OPEN)
                .setParam(FirebaseAnalytics.Param.ITEM_ID, TAG)
                .done()
    }

    override fun onPause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")
        super.onPause()
        // NOP.
    }

    override fun onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy()")
        super.onDestroy()
        // NOP.
    }

    //// RUNTIME PERMISSION RELATED

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
        super.onActivityResult(requestCode, resultCode, intent)

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

            // After WRITE_EXTERNAL_STORAGE permission is granted,
            // retry to update selectable directories here.
            settingFragment.updateStorageSelectorSelectableDirectoryList()
        }
    }
}
