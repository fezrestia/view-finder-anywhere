@file:Suppress("ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.fezrestia.android.lib.util.currentDisplayRect

import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.math.IntWH
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAlign
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController
import com.fezrestia.android.viewfinderanywhere.storage.MediaStoreUtil

class StorageSelectorRootView : RelativeLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    // Core instance.
    private lateinit var controller: OverlayViewFinderController
    private lateinit var configManager: ConfigManager

    // Display coordinates.
    private val displayWH = IntWH(0, 0)

    // UI.
    private lateinit var defaultItem: View

    // UI coordinates.
    private var windowEdgePadding = 0

    // Overlay window orientation.
    private var orientation = Configuration.ORIENTATION_UNDEFINED

    // Window.
    private lateinit var windowManager: WindowManager
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    // Limit position.
    private val windowEnabledPosit = Point()
    private val windowDisabledPosit = Point()

    // Storage list.
    private val availableStorageList = mutableListOf<String>()
    private val targetStorageSet = mutableSetOf<String>()

    private val onStorageItemTouchListenerImpl = OnStorageItemTouchListenerImpl()

    // View elements.
    private val item_list: LinearLayout
        get() {
            return this.findViewById(R.id.item_list)
        }
    private val total_container: RelativeLayout
        get() {
            return this.findViewById(R.id.total_container)
        }

    init {
        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR")
        // NOP.
    }

    /**
     * Set core instance dependency.
     *
     * @param controller Master controller
     * @param configManager
     */
    fun setCoreInstances(
            controller: OverlayViewFinderController,
            configManager: ConfigManager) {
        this.controller = controller
        this.configManager = configManager
    }

    /**
     * Initialize all of configurations.
     */
    fun initialize() {
        if (IS_DEBUG) logD(TAG, "initialize() : E")

        // Load setting.
        loadPreferences()

        // Window related.
        createWindowParameters()

        // Create dynamic layout.
        createUiLayout()

        // Update UI.
        updateTotalUserInterface()

        if (IS_DEBUG) logD(TAG, "initialize() : X")
    }

    private fun loadPreferences() {
        // Selectables.
        availableStorageList.clear()
        val totalSet = App.sp.getStringSet(
                Constants.SP_KEY_STORAGE_SELECTOR_SELECTABLE_DIRECTORY,
                emptySet()) as Set<String>
        totalSet.forEach {
            availableStorageList.add(it)
        }
        availableStorageList.sort()

        // Set default.
        availableStorageList.add(0, MediaStoreUtil.DEFAULT_STORAGE_DIR_NAME)

        // Selected.
        targetStorageSet.clear()
        val targetSet = App.sp.getStringSet(
                Constants.SP_KEY_STORAGE_SELECTOR_TARGET_DIRECTORY,
                emptySet()) as Set<String>
        for (eachDirName in targetSet) {
            if (availableStorageList.contains(eachDirName)) {
                targetStorageSet.add(eachDirName)
            }
        }
    }

    private fun createWindowParameters() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT)
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    private fun createUiLayout() {
        item_list.removeAllViews()

        val inflater = LayoutInflater.from(context)

        // Item params.
        val itemHeight = resources.getDimensionPixelSize(R.dimen.storage_selector_item_height)
        val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight)

        for (eachDir in availableStorageList) {
            val item = inflater.inflate(R.layout.storage_selector_list_item, null)
            item.setOnTouchListener(onStorageItemTouchListenerImpl)
            item.tag = eachDir
            item.isSelected = targetStorageSet.contains(eachDir)
            val label: TextView = item.findViewById(R.id.storage_selector_list_item_label)
            label.text = "/$eachDir"
            label.setShadowLayer(10.0f, 0.0f, 0.0f, Color.BLACK)
            onStorageItemTouchListenerImpl.updateStaticDrawable(item)
            item_list.addView(item, params)

            // Cache default.
            if (eachDir == MediaStoreUtil.DEFAULT_STORAGE_DIR_NAME) {
                defaultItem = item
            }
        }

        // Check selected state.
        onStorageItemTouchListenerImpl.checkSelectedStatus()

        // Load dimension.
        windowEdgePadding = resources.getDimensionPixelSize(R.dimen.storage_selector_top_padding)
    }

    private inner class OnStorageItemTouchListenerImpl : OnTouchListener {
        private val hitRect = Rect()

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    updatePressedState(view, event)
                }

                MotionEvent.ACTION_MOVE -> {
                    updatePressedState(view, event)
                }

                MotionEvent.ACTION_UP -> {
                    view.getLocalVisibleRect(hitRect)
                    if (hitRect.contains(event.x.toInt(), event.y.toInt())) {
                        // Update state.
                        view.isSelected = !view.isSelected

                        // Click sound.
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                        // Update target storage.
                        updateTargetStorage(view)
                    }
                    updateStaticDrawable(view)
                }

                // fall-through.
                MotionEvent.ACTION_CANCEL -> {
                    updateStaticDrawable(view)
                }

                else -> {
                    // NOP.
                }
            }

            return true
        }

        fun updatePressedState(view: View, event: MotionEvent) {
            view.getLocalVisibleRect(hitRect)
            if (hitRect.contains(event.x.toInt(), event.y.toInt())) {
                view.background = App.customResContainer.drawableStorageItemBgPressed
            } else {
                updateStaticDrawable(view)
            }
        }

        fun updateStaticDrawable(view: View) {
            if (view.isSelected) {
                view.background = App.customResContainer.drawableStorageItemBgSelected
            } else {
                view.background = App.customResContainer.drawableStorageItemBgNormal
            }
        }

        private fun updateTargetStorage(view: View) {
            if (IS_DEBUG) logD(TAG, "updateTargetStorage() : E")

            val targetStorage = view.tag as String

            if (view.isSelected) {
                if (IS_DEBUG) logD(TAG, "Selected : $targetStorage")
                targetStorageSet.add(targetStorage)
            } else {
                if (IS_DEBUG) logD(TAG, "De-Selected : $targetStorage")
                targetStorageSet.remove(targetStorage)
            }

            // If all item is unselected, force default item selected.
            checkSelectedStatus()

            // Store preferences.
            App.sp.edit().putStringSet(
                    Constants.SP_KEY_STORAGE_SELECTOR_TARGET_DIRECTORY,
                    targetStorageSet)
                    .apply()

            if (IS_DEBUG) logD(TAG, "updateTargetStorage() : X")
        }

        fun checkSelectedStatus() {
            if (targetStorageSet.isEmpty()) {
                // Select default.
                defaultItem.isSelected = true
                onStorageItemTouchListenerImpl.updateStaticDrawable(defaultItem)
                targetStorageSet.add(defaultItem.tag as String)
            }
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        // NOP.
    }

    /**
     * Add this view to WindowManager layer.
     */
    fun addToOverlayWindow() {
        if (isAttachedToWindow) {
            // Already attached.
            return
        }

        updateTotalUserInterface()

        // Add to WindowManager.
        windowManager.addView(this, windowLayoutParams)
    }

    /**
     * Remove this view from WindowManager layer.
     */
    fun removeFromOverlayWindow() {
        if (!isAttachedToWindow) {
            // Already detached.
            return
        }

        // Remove from to WindowManager.
        windowManager.removeView(this)
    }

    private fun updateTotalUserInterface() {
        // Screen configuration.
        calculateScreenConfiguration()
        // Window layout.
        updateWindowParams()
        // Update layout.
        updateLayoutParams()
    }

    private fun calculateScreenConfiguration() {
        val rect = currentDisplayRect(windowManager)
        displayWH.set(rect.width(), rect.height())

        // Get display orientation.
        orientation = if (rect.height() < rect.width()) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun updateWindowParams() {
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        windowLayoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        windowLayoutParams.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                    }
                }

                // Window offset on enabled.
                windowLayoutParams.x = windowEdgePadding
                windowLayoutParams.y = 0

                // Position limit.
                windowDisabledPosit.set(
                        windowLayoutParams.x - displayWH.h,
                        windowLayoutParams.y)
                windowEnabledPosit.set(
                        windowLayoutParams.x,
                        windowLayoutParams.y)
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        windowLayoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        windowLayoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    }
                }

                // Window offset on enabled.
                windowLayoutParams.x = 0
                windowLayoutParams.y = windowEdgePadding

                // Position limit.
                windowDisabledPosit.set(
                        windowLayoutParams.x,
                        windowLayoutParams.y - displayWH.h)
                windowEnabledPosit.set(
                        windowLayoutParams.x,
                        windowLayoutParams.y)
            }

            else -> {
                throw IllegalStateException("Unexpected orientation.")
            }
        }

        // Check active.
        if (!controller.lifeCycle().isActive) {
            windowLayoutParams.x = windowDisabledPosit.x
            windowLayoutParams.y = windowDisabledPosit.y
        }

        if (isAttachedToWindow) {
            windowManager.updateViewLayout(this, windowLayoutParams)
        }
    }

    private fun updateLayoutParams() {
        val params = total_container.layoutParams
        val itemHeight = resources.getDimensionPixelSize(R.dimen.storage_selector_item_height)
        val maxItemCount: Int
        val winX: Int
        val winY: Int
        val totalWidth: Int
        val totalHeight: Int
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                val overlayVfLeft = OverlayViewFinderWindowConfig.enabledWindowX
                val overlayVfW = OverlayViewFinderWindowConfig.enabledWindowW
                val overlayVfRight = overlayVfLeft + overlayVfW

                totalWidth = when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        ((displayWH.w - overlayVfRight) * 0.8f).toInt()
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        ((overlayVfLeft - windowEdgePadding) * 0.8f).toInt()
                    }
                }

                maxItemCount = (displayWH.h * 0.8f / itemHeight).toInt()
                totalHeight = if (item_list.childCount < maxItemCount) {
                    itemHeight * item_list.childCount
                } else {
                    itemHeight * maxItemCount
                }
                params.width = totalWidth
                params.height = totalHeight

                // Cache.
                winX = windowEdgePadding
                winY = (displayWH.h - totalHeight) / 2
                StorageSelectorWindowConfig.update(winX, winY, totalWidth, totalHeight)
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                totalWidth = (displayWH.w * 0.8f).toInt()

                val overlayVfTop = OverlayViewFinderWindowConfig.enabledWindowY
                val overlayVfH = OverlayViewFinderWindowConfig.enabledWindowH
                val overlayVfBottom = overlayVfTop + overlayVfH
                val maxHeight = when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        ((displayWH.h - windowEdgePadding - overlayVfBottom) * 0.8f).toInt()
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        ((overlayVfTop - windowEdgePadding) * 0.8f).toInt()
                    }
                }

                maxItemCount = maxHeight / itemHeight
                totalHeight = itemHeight * maxItemCount

                params.width = totalWidth
                params.height = totalHeight

                // Cache.
                winX = (displayWH.w - totalWidth) / 2
                winY = windowEdgePadding
                StorageSelectorWindowConfig.update(winX, winY, totalWidth, totalHeight)
            }

            else -> {
                throw IllegalStateException("Unexpected orientation.")
            }
        }

        if (IS_DEBUG) {
            logD(TAG, "updateLayoutParams() : X=$winX, Y=$winY, W=$totalWidth, H=$totalHeight")
        }

        total_container.layoutParams = params
    }

    public override fun onConfigurationChanged(newConfig: Configuration) {
        if (IS_DEBUG) logD(TAG, "onConfigurationChanged() : Config=$newConfig")
        super.onConfigurationChanged(newConfig)

        // Update UI.
        updateTotalUserInterface()
    }

    companion object {
        private const val TAG = "StorageSelectorRootView"
    }
}
