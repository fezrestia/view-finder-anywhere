@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

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
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference

import com.fezrestia.android.lib.firebase.FirebaseAnalyticsController
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.config.options.CameraApiLevel
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAlign
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAspect
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderSize
import com.fezrestia.android.viewfinderanywhere.control.OnOffTrigger
import com.fezrestia.android.viewfinderanywhere.plugin.ui.loadCustomizedUiResources
import com.fezrestia.android.viewfinderanywhere.storage.MediaStoreUtil
import com.google.firebase.analytics.FirebaseAnalytics

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
                val packageName: String,
                val plugInTitle: String)

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

            // View finder align.
            val vfAlignPref: ListPreference = findPreference(ViewFinderAlign.key)!!
            val aligns: Array<String> = ViewFinderAlign.values()
                    .map { it.toString() }.toTypedArray()
            vfAlignPref.entries = aligns
            vfAlignPref.entryValues = aligns
            vfAlignPref.onPreferenceChangeListener = onChangeListenerImpl

            // Storage selector En/Disable.
            val storagePref: SwitchPreference = findPreference(Constants.SP_KEY_IS_STORAGE_SELECTOR_ENABLED)!!
            storagePref.onPreferenceChangeListener = onChangeListenerImpl

            // Add new directory.
            val addNewDirPref: EditTextPreference = findPreference(SP_KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY)!!
            addNewDirPref.onPreferenceChangeListener = onChangeListenerImpl
            addNewDirPref.onPreferenceClickListener = Preference.OnPreferenceClickListener { p: Preference ->
                (p as EditTextPreference).text = ""
                true
            }
        }

        override fun onStart() {
            if (IS_DEBUG) logD(TAG, "onStart()")
            super.onStart()

            // Setup dynamic preference options.

            // En/Disable.
            val enDisPref: SwitchPreference = findPreference(SP_KEY_OVERLAY_VIEW_FINDER_ENABLE_DISABLE)!!
            enDisPref.isChecked = App.isOverlayServiceActive
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
            if (IS_DEBUG) logD(TAG, "onStop()")
            super.onStop()

            // NOP.

        }

        private inner class OnChangeListenerImpl : Preference.OnPreferenceChangeListener {
            override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                var firebaseValue: String? = null

                when (preference?.key) {
                    SP_KEY_OVERLAY_VIEW_FINDER_ENABLE_DISABLE -> {
                        val isChecked: Boolean = newValue as Boolean

                        if (isChecked) {
                            if (!App.isOverlayServiceActive) {
                                OnOffTrigger.requestStart(requireContext())
                            }
                        } else {
                            if (App.isOverlayServiceActive) {
                                OnOffTrigger.requestStop(requireContext())
                            }
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

                    ViewFinderAlign.key -> {
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
                            updateStorageSelectorSelectableDirectoryList()
                        }
                    }

                    SP_KEY_STORAGE_SELECTOR_CREATE_NEW_DIRECTORY -> {
                        val newDir: String = newValue as String

                        if (IS_DEBUG) logD(TAG, "NewDirectory = $newDir")

                        // Validation.
                        if (newDir.isNotEmpty()) {
                            val selectableSet = App.sp.getStringSet(
                                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                                    mutableSetOf<String>()) as MutableSet<String>

                            if (!selectableSet.contains(newDir)) {
                                selectableSet.add(newDir)
                                App.sp.edit().putStringSet(
                                        Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                                        selectableSet)
                                        .apply()
                            }
                        }
                    }

                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY -> {
                        @Suppress("UNCHECKED_CAST")
                        val validDirSet: MutableSet<String> = newValue as MutableSet<String>

                        if (IS_DEBUG) {
                            validDirSet.forEach { dir ->
                                logD(TAG, "ValidDir = $dir")
                            }
                        }

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

                if (IS_DEBUG) {
                    logD(TAG, "UI-PLUG-IN Package = " + plugIn.packageName)
                    logD(TAG, "UI-PLUG-IN Title   = " + plugIn.plugInTitle)
                }
            }

            return plugins.toMutableList()
        }

        //TODO:This function takes much time.
        fun updateStorageSelectorSelectableDirectoryList() {
            if (IS_DEBUG) logD(TAG, "updateStorageSelectorSelectableDirectoryList() : E")

            val pref: MultiSelectListPreference = findPreference(
                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY)!!

            // Total.
            val totalDirs = MediaStoreUtil.getTagDirList(requireContext())
            if (totalDirs.isEmpty()) {
                pref.isEnabled = false
                logE(TAG, "No tag dirs.")
                return
            }

            // Current selectable.
            val selectableSet = App.sp.getStringSet(
                    Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                    setOf<String>()) as Set<String>
            val validSelectables = mutableSetOf<String>()
            selectableSet.forEach { selectable ->
                if (totalDirs.contains(selectable)) {
                    validSelectables.add(selectable)
                }
            }

            // Apply to preferences.
            val entries = totalDirs.toTypedArray()
            pref.entries = entries
            pref.entryValues = entries
            pref.values = validSelectables
            pref.onPreferenceChangeListener = onChangeListenerImpl

            if (IS_DEBUG) logD(TAG, "updateStorageSelectorSelectableDirectoryList() : X")
        }
    }

    //// MAIN ACTIVITY

    private lateinit var settingFragment: SettingFragment

    override fun onCreate(bundle: Bundle?) {
        if (IS_DEBUG) logD(TAG, "onCreate()")
        super.onCreate(null)

        setContentView(R.layout.setting_activity)

        settingFragment = SettingFragment()

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.preference_fragment_container, settingFragment)
                .commit()
    }

    override fun onResume() {
        if (IS_DEBUG) logD(TAG, "onResume()")
        super.onResume()

        // Mandatory permission check.
        if (isFinishing) {
            if (IS_DEBUG) logD(TAG, "App is in finishing sequence.")
            return
        }
        if (checkMandatoryPermissions()) {
            if (IS_DEBUG) logD(TAG, "Return immediately for permission.")
            return
        }

        // Firebase analytics.
        App.firebase.createNewLogRequest()
                .setEvent(FirebaseAnalytics.Event.APP_OPEN)
                .setParam(FirebaseAnalytics.Param.ITEM_ID, TAG)
                .done()
    }

    override fun onPause() {
        if (IS_DEBUG) logD(TAG, "onPause()")
        super.onPause()
        // NOP.
    }

    override fun onDestroy() {
        if (IS_DEBUG) logD(TAG, "onDestroy()")
        super.onDestroy()
        // NOP.
    }

    //// RUNTIME PERMISSION RELATED

    private val isRuntimePermissionRequired: Boolean
        get() = Build.VERSION_CODES.M <= Build.VERSION.SDK_INT

    private val isSystemAlertWindowPermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = Settings.canDrawOverlays(this)

    private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
    )

    @TargetApi(Build.VERSION_CODES.M)
    private fun isPermissionGranted(permission: String): Boolean =
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Check permission.

     * @return immediateReturnRequired
     */
    @TargetApi(Build.VERSION_CODES.M)
    private fun checkMandatoryPermissions(): Boolean {
        if (IS_DEBUG) logD(TAG, "checkMandatoryPermissions()")

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

            REQUIRED_PERMISSIONS.forEach { permission ->
                if(!isPermissionGranted(permission)) {
                    permissions.add(permission)
                }
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
        if (IS_DEBUG) logD(TAG, "onActivityResult()")
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == REQUEST_CODE_MANAGE_OVERLAY_PERMISSION) {
            if (!isSystemAlertWindowPermissionGranted) {
                logE(TAG, "Overlay permission is not granted yet.")
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        if (IS_DEBUG) logD(TAG, "onRequestPermissionsResult()")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_MANAGE_PERMISSIONS) {
            REQUIRED_PERMISSIONS.forEach { permission ->
                if (!isPermissionGranted(permission)) {
                    logE(TAG, "Permission: $permission is NOT granted yet.")
                    finish()
                }
            }

            // After WRITE_EXTERNAL_STORAGE permission is granted,
            // retry to update selectable directories here.
            settingFragment.updateStorageSelectorSelectableDirectoryList()
        }
    }
}
