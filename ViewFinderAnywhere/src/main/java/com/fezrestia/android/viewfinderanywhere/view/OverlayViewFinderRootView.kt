package com.fezrestia.android.viewfinderanywhere.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout

import com.fezrestia.android.lib.interaction.InteractionEngine
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.lib.util.math.IntXY
import com.fezrestia.android.lib.util.math.IntWH
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication.CustomizableResourceContainer
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants.ViewFinderGripPosition
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants.ViewFinderGripSize
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
import com.fezrestia.android.viewfinderanywhere.control.OnOffTrigger
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController

/**
 * Window root view class.
 */
class OverlayViewFinderRootView : RelativeLayout {
    companion object {
        // Log tag.
        val TAG = "OverlayViewFinderRootView"

        // Window position correction animation interval.
        private val WINDOW_ANIMATION_INTERVAL = 16L

        // Hidden window position.
        private val WINDOW_HIDDEN_POS_X = -5000

        // Alpha definitions.
        private val SHOWN_ALPHA = 1.0f
        private val HIDDEN_ALPHA = 0.0f
    }

    // Core instances.
    private lateinit var controller: OverlayViewFinderController
    private lateinit var configManager: ConfigManager

    // UI thread handler.
    private val uiHandler = ViewFinderAnywhereApplication.getUiThreadHandler()

    // Layout root view.
    private lateinit var rootView: RelativeLayout

    // Display coordinates.
    private val displayWH = IntWH(0, 0)

    // Overlay window orientation.
    private var orientation = Configuration.ORIENTATION_UNDEFINED

    // View finder target size.
    private val viewfinderWH = IntWH(0, 0)

    // Pre-loaded preferences
    private var viewFinderScaleRatioAgainstToScreen
            = ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_SMALL

    // Window.
    private lateinit var windowManager: WindowManager
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    // Slider.
    private lateinit var overlaySlider: LinearLayout

    // Viewfinder related.
    private lateinit var viewFinderContainer: FrameLayout
    private lateinit var viewFinderBackground: ImageView
    private lateinit var viewFinder: TextureView
    private lateinit var shutterFeedback: View

    // Scan indicator.
    private lateinit var scanIndicatorContainer: ViewGroup

    // UI Plug-IN.
    // Viewfinder grip.
    private lateinit var viewFinderGripContainer: ViewGroup
    private lateinit var viewFinderGrip: ImageView
    private lateinit var viewFinderGripLabel: ImageView
    private lateinit var viewFinderGripLabelLandscapeBmp: Bitmap
    private lateinit var viewFinderGripLabelPortraitBmp: Bitmap
    // Viewfinder frame.
    private lateinit var viewFinderFrame: ImageView
    // Total cover.
    private lateinit var totalBackground: ImageView
    private lateinit var totalForeground: ImageView

    // Touch interaction engine.
    private lateinit var interactionEngine: InteractionEngine

    // Resident flag. Always on overlay window.
    private var isResidence = false

    // Last window position.
    private val lastWindowXY = IntXY(0, 0)

    // Limit position.
    private val windowEnabledXY = IntXY(0, 0)
    private var windowDisabledXY = IntXY(0, 0)

    // Already resumed or not.
    private var isResumed = true

    // Viewfinder grip size.
    private var viewFinderGripSize = 0

    // Resources.
    private lateinit var customResContainer: CustomizableResourceContainer

    // Grip preferences.
    private lateinit var gripSizeSetting: ViewFinderGripSize
    private lateinit var gripPositionSetting: ViewFinderGripPosition

    // Current window position correction task
    private var windowPositionCorrectionTask: Runnable? = null
    private inner class WindowPositionCorrectionTask(val targetWindowXY: IntXY) : Runnable {
        // Proportional gain.
        private val P_GAIN = 0.3f

        // Last delta.
        private val lastDelta = IntXY(0, 0)

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "WindowPositionCorrectionTask.run() : E")

            val dXY = IntXY(
                    targetWindowXY.x - windowLayoutParams.x,
                    targetWindowXY.y - windowLayoutParams.y)

            // Update layout.
            windowLayoutParams.x += (dXY.x * P_GAIN).toInt()
            windowLayoutParams.y += (dXY.y * P_GAIN).toInt()

            if (isAttachedToWindow) {
                windowManager.updateViewLayout(
                        this@OverlayViewFinderRootView,
                        windowLayoutParams)
            } else {
                // Already detached from window.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already detached from window.")
                return
            }

            // Check next.
            if (lastDelta == dXY) {
                // Correction is already convergent.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already position fixed.")

                // Remove or fix position.
                if (!isResidence && (targetWindowXY == windowDisabledXY)) {
                    // Remove.
                    OnOffTrigger.requestStop(context)
                } else {
                    // Fix position.
                    windowLayoutParams.x = targetWindowXY.x
                    windowLayoutParams.y = targetWindowXY.y

                    windowManager.updateViewLayout(
                            this@OverlayViewFinderRootView,
                            windowLayoutParams)
                }
                return
            }
            lastDelta.set(dXY)

            // Next.
            uiHandler.postDelayed(
                    this,
                    WINDOW_ANIMATION_INTERVAL)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "WindowPositionCorrectionTask.run() : X")
        }
    }

    constructor(context: Context)
            : super(context) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR 1")
    }

    constructor(context: Context, attrs: AttributeSet)
            : super(context, attrs) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR 2")
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int)
            : super(context, attrs, defStyle) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR 3")
    }

    /**
     * Set core instance dependency.
     *
     * @param controller Master controller
     * @param configManager ConfigManager
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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : E")

        // Cache instance references.
        cacheInstances()

        // Load setting.
        loadPreferences()

        // Window related.
        createWindowParameters()

        // Update UI.
        updateTotalUserInterface()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X")
    }

    private fun cacheInstances() {
        customResContainer = ViewFinderAnywhereApplication.getCustomResContainer()
        val res = context.resources

        // Root.
        rootView = findViewById(R.id.root) as RelativeLayout
        // Slider.
        overlaySlider = findViewById(R.id.overlay_slider) as LinearLayout
        // Viewfinder related.
        viewFinderContainer = findViewById(R.id.viewfinder_container) as FrameLayout
        viewFinder = findViewById(R.id.viewfinder) as TextureView
        viewFinder.surfaceTextureListener = surfaceTextureListenerImpl
        viewFinder.alpha = HIDDEN_ALPHA
        viewFinderBackground = findViewById(R.id.viewfinder_background) as ImageView
        viewFinderBackground.setBackgroundColor(customResContainer.colorVfBackground)
        // Scan indicator.
        scanIndicatorContainer = findViewById(R.id.scan_indicator_container) as ViewGroup
        scanIndicatorContainer.visibility = INVISIBLE
        // Shutter feedback.
        shutterFeedback = findViewById(R.id.shutter_feedback)
        shutterFeedback.visibility = View.INVISIBLE

        // UI-Plug-IN.
        // Viewfinder grip.
        viewFinderGripContainer = findViewById(R.id.viewfinder_grip_container) as ViewGroup
        viewFinderGrip = findViewById(R.id.viewfinder_grip) as ImageView
        viewFinderGripLabel = findViewById(R.id.viewfinder_grip_label) as ImageView
        val paint = Paint()
        paint.textSize = res.getDimensionPixelSize(R.dimen.viewfinder_grip_label_font_size)
                .toFloat()
        paint.color = customResContainer.colorGripLabel
        paint.isAntiAlias = true
        paint.typeface = Typeface.createFromAsset(
                context.assets,
                ViewFinderAnywhereConstants.FONT_FILENAME_CODA)
        val fontMetrics = paint.fontMetrics
        val textHeight = Math.ceil(Math.abs(fontMetrics.ascent).toDouble()
                + Math.abs(fontMetrics.descent)
                + Math.abs(fontMetrics.leading)).toInt()
        val textLabel = res.getString(R.string.viewfinder_grip_label)
        val textWidth = Math.ceil(paint.measureText(textLabel).toDouble()).toInt()
        viewFinderGripLabelLandscapeBmp = Bitmap.createBitmap(
                textWidth,
                textHeight,
                Bitmap.Config.ARGB_8888)
        val c = Canvas(viewFinderGripLabelLandscapeBmp)
        c.drawText(textLabel, 0.0f, Math.abs(fontMetrics.ascent), paint)
        val matrix = Matrix()
        matrix.postRotate(-90.0f)
        viewFinderGripLabelPortraitBmp = Bitmap.createBitmap(
                viewFinderGripLabelLandscapeBmp,
                0,
                0,
                viewFinderGripLabelLandscapeBmp.width,
                viewFinderGripLabelLandscapeBmp.height,
                matrix,
                true)
        // Viewfinder frame.
        viewFinderFrame = findViewById(R.id.viewfinder_frame) as ImageView
        // Total cover.
        totalBackground = findViewById(R.id.total_background) as ImageView
        totalForeground = findViewById(R.id.total_foreground) as ImageView
    }

    private fun loadPreferences() {
        val sp = ViewFinderAnywhereApplication.getGlobalSharedPreferences()

        // Size.
        val size = sp.getString(ViewFinderAnywhereConstants.KEY_VIEW_FINDER_SIZE, null)
        if (size == null) {
            // Unexpected or not initialized yet. Use default.
            viewFinderScaleRatioAgainstToScreen =
                    ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_LARGE
        } else when (size) {
            ViewFinderAnywhereConstants.VAL_VIEW_FINDER_SIZE_XLARGE -> {
                viewFinderScaleRatioAgainstToScreen =
                        ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_XLARGE
            }

            ViewFinderAnywhereConstants.VAL_VIEW_FINDER_SIZE_LARGE -> {
                viewFinderScaleRatioAgainstToScreen =
                        ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_LARGE
            }

            ViewFinderAnywhereConstants.VAL_VIEW_FINDER_SIZE_SMALL -> {
                viewFinderScaleRatioAgainstToScreen =
                        ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_SMALL
            }

            else -> {
                // NOP. Unexpected.
                throw IllegalArgumentException("Unexpected Size.")
            }
        }

        // Residence flag.
        isResidence = sp.getBoolean(
                ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE,
                false)

        // Grip.
        viewFinderGripSize = context.resources.getDimensionPixelSize(R.dimen.viewfinder_grip_size)
        // Grip size.
        val gripSizeValue = sp.getString(ViewFinderGripSize.KEY, null)
        if (gripSizeValue != null) {
            gripSizeSetting = ViewFinderGripSize.valueOf(gripSizeValue)
        } else {
            gripSizeSetting = ViewFinderGripSize.getDefault()
        }
        // Grip position.
        val positionSizeValue = sp.getString(ViewFinderGripPosition.KEY, null)
        if (positionSizeValue != null) {
            gripPositionSetting = ViewFinderGripPosition.valueOf(positionSizeValue)
        } else {
            gripPositionSetting = ViewFinderGripPosition.getDefault()
        }
    }

    private fun createWindowParameters() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT)
    }

    /**
     * Release all resources.
     */
    fun release() {
        releaseWindowPositionCorrector()

        interactionEngine.setInteractionCallback(null)
        interactionEngine.release()
        viewFinder.surfaceTextureListener = null
        rootView.setOnTouchListener(null)

        // UI Plug-IN.
        viewFinderGrip.setImageDrawable(null)
        viewFinderGripLabel.setImageBitmap(null)
        viewFinderFrame.setImageDrawable(null)
        totalBackground.setImageDrawable(null)
        totalForeground.setImageDrawable(null)
    }

    private fun releaseWindowPositionCorrector() {
        if (windowPositionCorrectionTask != null) {
            uiHandler.removeCallbacks(windowPositionCorrectionTask)
            windowPositionCorrectionTask = null
        }
    }

    /**
     * Add this view to WindowManager layer.
     */
    fun addToOverlayWindow() {
        // Window parameters.
        updateWindowParams(true)

        // Add to WindowManager.
        val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        winMng.addView(this, windowLayoutParams)
    }

    @SuppressLint("RtlHardcoded")
    private fun updateWindowParams(isInitialSetup: Boolean) {
        releaseWindowPositionCorrector()
        val edgeClearance = context.resources.getDimensionPixelSize(
                R.dimen.viewfinder_edge_clearance)

        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                windowLayoutParams.gravity = Gravity.BOTTOM or Gravity.RIGHT

                // Window offset on enabled.
                windowLayoutParams.x = edgeClearance
                windowLayoutParams.y = 0

                // Position limit.
                windowDisabledXY.set(
                        windowLayoutParams.x,
                        windowLayoutParams.y - viewfinderWH.h)
                windowEnabledXY.set(
                        windowLayoutParams.x,
                        windowLayoutParams.y)

                // Cache.
                val winX = displayWH.longlen() - viewfinderWH.w - windowLayoutParams.x
                val winY = displayWH.shortlen() - viewfinderWH.h - viewFinderGripSize
                        - windowLayoutParams.y
                val winW = viewfinderWH.w
                val winH = viewfinderWH.h + viewFinderGripSize
                ViewFinderAnywhereApplication.TotalWindowConfiguration
                        .OverlayViewFinderWindowConfig.update(winX, winY, winW, winH)
                if (Log.IS_DEBUG) Log.logDebug(TAG,
                        "updateWindowParams() : [X=$winX] [Y=$winY] [W=$winW] [H=$winH]")
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                windowLayoutParams.gravity = Gravity.BOTTOM or Gravity.RIGHT

                // Window offset on enabled.
                windowLayoutParams.x = 0
                windowLayoutParams.y = edgeClearance

                // Position limit.
                windowDisabledXY.set(
                        windowLayoutParams.x - viewfinderWH.w,
                        windowLayoutParams.y)
                windowEnabledXY.set(
                        windowLayoutParams.x,
                        windowLayoutParams.y)

                // Cache.
                val winX = displayWH.shortlen() - viewfinderWH.w - viewFinderGripSize
                        - windowLayoutParams.x
                val winY = displayWH.longlen() - viewfinderWH.h - windowLayoutParams.y
                val winW = viewfinderWH.w + viewFinderGripSize
                val winH = viewfinderWH.h
                ViewFinderAnywhereApplication.TotalWindowConfiguration
                        .OverlayViewFinderWindowConfig.update(winX, winY, winW, winH)
                if (Log.IS_DEBUG) Log.logDebug(TAG,
                        "updateWindowParams() : [X=$winX] [Y=$winY] [W=$winW] [H=$winH]")
            }

            else -> {
                // Unexpected orientation.
                throw IllegalStateException("Unexpected orientation.")
            }
        }

        // Check active.
        if (isResidence && !isInitialSetup && !controller.currentState.isActive) {
            windowLayoutParams.x = windowDisabledXY.x
            windowLayoutParams.y = windowDisabledXY.y
        }

        if (isAttachedToWindow) {
            windowManager.updateViewLayout(this, windowLayoutParams)
        }
    }

    /**
     * Remove this view from WindowManager layer.
     */
    fun removeFromOverlayWindow() {
        // Remove from to WindowManager.
        val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        winMng.removeView(this)

        // Release drawing cache.
        uiHandler.post(ReleaseDrawingResourceTask())
    }

    private inner class ReleaseDrawingResourceTask : Runnable {
        override fun run() {
            if (!viewFinderGripLabelLandscapeBmp.isRecycled) {
                viewFinderGripLabelLandscapeBmp.recycle()
            }
            if (!viewFinderGripLabelPortraitBmp.isRecycled) {
                viewFinderGripLabelPortraitBmp.recycle()
            }
        }
    }

    private fun updateTotalUserInterface() {
        // Screen configuration.
        calculateScreenConfiguration()
        // View finder size.
        calculateViewFinderSize()
        // Window layout.
        updateWindowParams(false)
        // Update layout.
        updateLayoutParams()
    }

    private fun calculateScreenConfiguration() {
        // Get display size.
        val display = windowManager.defaultDisplay
        val screenSize = Point()
        display.getSize(screenSize)
        val width = screenSize.x
        val height = screenSize.y
        displayWH.set(width, height)

        // Get display orientation.
        if (height < width) {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        } else {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
    }

    private fun calculateViewFinderSize() {
        // Define view finder size.
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                viewfinderWH.w =
                        (displayWH.longlen() * viewFinderScaleRatioAgainstToScreen).toInt()
                viewfinderWH.h =
                        (viewfinderWH.w / configManager.evfAspectWH).toInt()
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                viewfinderWH.h =
                        (displayWH.longlen() * viewFinderScaleRatioAgainstToScreen).toInt()
                viewfinderWH.w =
                        (viewfinderWH.h / configManager.evfAspectWH).toInt()
            }

            else -> {
                // Unexpected orientation.
                throw IllegalStateException("Unexpected orientation.")
            }
        }
    }

    private fun updateLayoutParams() {
        // Viewfinder size.
        run {
            val params = viewFinderContainer.layoutParams
            params.width = viewfinderWH.w
            params.height = viewfinderWH.h
            viewFinderContainer.layoutParams = params
        }

        val totalWidth: Int
        val totalHeight: Int
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                totalWidth = viewfinderWH.w
                totalHeight = viewfinderWH.h + viewFinderGripSize
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                totalWidth = viewfinderWH.w + viewFinderGripSize
                totalHeight = viewfinderWH.h
            }

            else -> {
                // Unexpected orientation.
                throw IllegalStateException("Unexpected orientation.")
            }
        }

        run {
            val params = totalBackground.layoutParams
            params.width = totalWidth
            params.height = totalHeight
            totalBackground.layoutParams = params

            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    totalBackground.setImageDrawable(customResContainer.drawableTotalBackLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    totalBackground.setImageDrawable(customResContainer.drawableTotalBackPort)
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
        }

        run {
            val params = totalForeground.layoutParams
            params.width = totalWidth
            params.height = totalHeight
            totalForeground.layoutParams = params

            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    totalForeground.setImageDrawable(customResContainer.drawableTotalForeLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    totalForeground.setImageDrawable(customResContainer.drawableTotalForePort)
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
        }

        // Viewfinder slider.
        run {
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    overlaySlider.orientation = LinearLayout.VERTICAL
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    overlaySlider.orientation = LinearLayout.HORIZONTAL
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
        }

        // Grip container.
        run {
            val params = viewFinderGripContainer.layoutParams
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    params.width = viewfinderWH.w
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.height = viewfinderWH.h
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
            viewFinderGripContainer.layoutParams = params
        }

        // Viewfinder grip.
        run {
            val params = viewFinderGrip.layoutParams as FrameLayout.LayoutParams
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    params.width = (viewfinderWH.w * gripSizeSetting.scaleRate).toInt()
                    params.height = viewFinderGripSize
                    viewFinderGrip.setImageDrawable(customResContainer.drawableVfGripLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    params.width = viewFinderGripSize
                    params.height = (viewfinderWH.h * gripSizeSetting.scaleRate).toInt()
                    viewFinderGrip.setImageDrawable(customResContainer.drawableVfGripPort)
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
            params.gravity = gripPositionSetting.layoutGravity
            viewFinderGrip.layoutParams = params
        }

        run {
            val params = viewFinderGripLabel.layoutParams as FrameLayout.LayoutParams
            val horizontalPadding = context.resources.getDimensionPixelSize(
                    R.dimen.viewfinder_grip_label_horizontal_padding)
            val verticalPadding = context.resources.getDimensionPixelSize(
                    R.dimen.viewfinder_grip_label_vertical_padding)
            var visibility = View.VISIBLE
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    viewFinderGripLabel.setImageBitmap(viewFinderGripLabelLandscapeBmp)
                    viewFinderGripLabel.setPadding(
                            horizontalPadding,
                            verticalPadding,
                            horizontalPadding,
                            0)
                    params.gravity =
                            gripPositionSetting.layoutGravity or Gravity.CENTER_VERTICAL
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.height = viewFinderGripSize
                    if (viewfinderWH.w * gripSizeSetting.scaleRate
                            < viewFinderGripLabelLandscapeBmp.width) {
                        visibility = View.INVISIBLE
                    }
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    viewFinderGripLabel.setImageBitmap(viewFinderGripLabelPortraitBmp)
                    viewFinderGripLabel.setPadding(
                            verticalPadding,
                            horizontalPadding,
                            0,
                            horizontalPadding)
                    params.gravity =
                            Gravity.CENTER_HORIZONTAL or gripPositionSetting.layoutGravity
                    params.width = viewFinderGripSize
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    if (viewfinderWH.h * gripSizeSetting.scaleRate
                            < viewFinderGripLabelPortraitBmp.height) {
                        visibility = View.INVISIBLE
                    }
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
            viewFinderGripLabel.visibility = visibility
            viewFinderGripLabel.layoutParams = params
        }

        // Viewfinder frame.
        run {
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    viewFinderFrame.setImageDrawable(customResContainer.drawableVfFrameLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    viewFinderFrame.setImageDrawable(customResContainer.drawableVfFramePort)
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
        }
    }

    private val surfaceTextureListenerImpl = SurfaceTextureListenerImpl()
    private inner class SurfaceTextureListenerImpl : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSurfaceTextureAvailable() : [W=$width] [H=$height]")

            checkViewFinderAspect(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSurfaceTextureSizeChanged() : [W=$width] [H=$height]")

            checkViewFinderAspect(width, height)
        }

        private fun checkViewFinderAspect(width: Int, height: Int) {
            if (width == viewfinderWH.w && height == viewfinderWH.h) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "checkViewFinderAspect() : Resize DONE")
                // Resize done.

                // Set touch interceptor.
                interactionEngine = InteractionEngine(
                        rootView.context,
                        rootView,
                        0,
                        0,//ViewConfiguration.get(getContext()).getScaledTouchSlop(),
                        ViewFinderAnywhereApplication.getUiThreadHandler())
                interactionEngine.setInteractionCallback(interactionCallbackImpl)
                rootView.setOnTouchListener(onTouchListenerImpl)

                // Notify to device.
                controller.currentState.onSurfaceReady()
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "checkViewFinderAspect() : Now on resizing...")
                // NOP. Now on resizing.
            }
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceTextureDestroyed()")
            // NOP.
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
//            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceTextureUpdated()")

            if (isResumed && viewFinder.alpha == HIDDEN_ALPHA) {
                showSurfaceTask.reset()
                uiHandler.post(showSurfaceTask)
            }
        }
    }

    private fun clearSurface() {
        uiHandler.removeCallbacks(showSurfaceTask)
        hideSurfaceTask.reset()
        uiHandler.post(hideSurfaceTask)
    }

    private abstract inner class SurfaceVisibilityControlTask : Runnable {
        // Log tag.
        private val TAG = "SurfaceVisibilityControlTask"

        // Actual view finder alpha.
        private var actualAlpha = SHOWN_ALPHA

        // Alpha stride.
        private val ALPHA_DELTA = 0.2f

        /**
         * Reset state.
         */
        fun reset() {
            actualAlpha = viewFinder.alpha
        }

        abstract protected fun isFadeIn(): Boolean

        override fun run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run()")

            if (!isAttachedToWindow) {
                // Already released.
                return
            }

            var isNextTaskRequired = true

            if (isFadeIn()) {
                actualAlpha += ALPHA_DELTA
                if (SHOWN_ALPHA < actualAlpha) {
                    actualAlpha = SHOWN_ALPHA
                    isNextTaskRequired = false
                }
            } else {
                actualAlpha -= ALPHA_DELTA
                if (actualAlpha < HIDDEN_ALPHA) {
                    actualAlpha = HIDDEN_ALPHA
                    isNextTaskRequired = false
                }
            }

            viewFinder.alpha = actualAlpha

            if (isNextTaskRequired) {
                // NOTICE:
                //   On Android N, invalidate() is called in setAlpha().
                //   So, this task takes 1 frame V-Sync millis (about 16[ms])
                //   Not to delay fade in/out, post next control task immediately.
                uiHandler.post(this)
            }
        }
    }

    private val showSurfaceTask = ShowSurfaceTask()
    private inner class ShowSurfaceTask : SurfaceVisibilityControlTask() {
        override fun isFadeIn(): Boolean {
            return true
        }
    }


    private val hideSurfaceTask = HideSurfaceTask()
    private inner class HideSurfaceTask : SurfaceVisibilityControlTask() {
        override fun isFadeIn() : Boolean {
            return false
        }
    }

    /**
     * Get view finder target surface.
     *
     * @return Finder TextureView.
     */
    fun getViewFinderSurface(): TextureView {
        return viewFinder
    }

    private val onTouchListenerImpl = OnTouchListenerImpl()
    private inner class OnTouchListenerImpl : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            // Use absolute position, because window position change affects view motion event.
            event.setLocation(event.rawX, event.rawY)

            interactionEngine.onTouchEvent(event)
            return true
        }
    }

    private val interactionCallbackImpl = InteractionCallbackImpl()
    private inner class InteractionCallbackImpl : InteractionEngine.InteractionCallback {
        override fun onSingleTouched(point: Point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleTouched() : [X=${point.x}] [Y=${point.y}]")

            // Pre-open.
            if (!isResumed) {
                controller.currentState.onPreOpenRequested()
            }

            // Request scan.
            controller.currentState.requestScan()

            // Stop animation.
            if (windowPositionCorrectionTask != null) {
                uiHandler.removeCallbacks(windowPositionCorrectionTask)
                windowPositionCorrectionTask = null
            }

            // Cache current position.
            lastWindowXY.x = windowLayoutParams.x
            lastWindowXY.y = windowLayoutParams.y
        }

        override fun onSingleMoved(currentPoint: Point, lastPoint: Point, downPoint: Point) {
//            if (Log.IS_DEBUG) Log.logDebug(TAG,
//                    "onSingleMoved() : [X=${currentPoint.x}] [Y=${currentPoint.y}]")

            val diffX = currentPoint.x - downPoint.x
            val diffY = currentPoint.y - downPoint.y

            // Check moving.
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            if (touchSlop < Math.abs(diffX) || touchSlop < Math.abs(diffY)) {
                controller.currentState.requestCancelScan()
            }

            var newX = windowLayoutParams.x
            var newY = windowLayoutParams.y
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    newY = lastWindowXY.y - diffY

                    // Limit check.
                    if (newY < windowDisabledXY.y) {
                        newY = windowDisabledXY.y
                    } else if (windowEnabledXY.y < newY) {
                        newY = windowEnabledXY.y
                    }
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    newX = lastWindowXY.x - diffX

                    // Limit check.
                    if (newX < windowDisabledXY.x) {
                        newX = windowDisabledXY.x
                    } else if (windowEnabledXY.x < newX) {
                        newX = windowEnabledXY.x
                    }
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }

            if (windowLayoutParams.x != newX || windowLayoutParams.y != newY) {
                windowLayoutParams.x = newX
                windowLayoutParams.y = newY
                windowManager.updateViewLayout(this@OverlayViewFinderRootView, windowLayoutParams)
            }
        }

        override fun onSingleStopped(currentPoint: Point, lastPoint: Point, downPoint: Point) {
//            if (Log.IS_DEBUG) Log.logDebug(TAG,
//                    "onSingleStopped() : [X=${currentPoint.x}] [Y=${currentPoint.y}]")
            // NOP.
        }

        override fun onSingleReleased(point: Point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleReleased() : [X=${point.x}] [Y=${point.y}]")

            // Request still capture.
            controller.currentState.requestStillCapture()

            // Decide correction target position.
            val diffToEnable: Int
            val diffToDisable: Int
            val target: IntXY
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    diffToEnable = Math.abs(windowEnabledXY.y - windowLayoutParams.y)
                    diffToDisable = Math.abs(windowDisabledXY.y - windowLayoutParams.y)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    diffToEnable = Math.abs(windowEnabledXY.x - windowLayoutParams.x)
                    diffToDisable = Math.abs(windowDisabledXY.x - windowLayoutParams.x)
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
            if (diffToEnable < diffToDisable) {
                target = windowEnabledXY

                // Resume controller.
                if (!isResumed) {
                    isResumed = true
                    controller.resume()
                    if (viewFinder.isAvailable) {
                        controller.currentState.onSurfaceReady()
                    }
                }
            } else {
                target = windowDisabledXY

                // Pause controller.
                if (isResumed) {
                    isResumed = false

                    // Hide surface.
                    clearSurface()

                    controller.pause()
                }
            }

            if (!isResumed) {
                controller.currentState.onPreOpenCanceled()
            }

            // Correct position start.
            windowPositionCorrectionTask = WindowPositionCorrectionTask(target)
            uiHandler.postDelayed(windowPositionCorrectionTask, WINDOW_ANIMATION_INTERVAL)
        }

        override fun onSingleCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSingleCanceled()")
            // NOP.
        }

        override fun onDoubleTouched(point0: Point, point1: Point) {
            // NOP.
        }

        override fun onDoubleMoved(point0: Point, point1: Point) {
            // NOP.
        }

        override fun onDoubleScaled(currentLen: Float, previousLen: Float, originalLen: Float) {
            // NOP.
        }

        override fun onDoubleRotated(degreeVsOrigin: Float, degreeVsLast: Float) {
            // NOP.
        }

        override fun onSingleReleasedInDouble(release: Point, remain: Point) {
            // NOP.
        }

        override fun onDoubleCanceled() {
            // NOP.
        }

        override fun onOverTripleCanceled() {
            // NOP.
        }

        override fun onFling(
                event1: MotionEvent,
                event2: MotionEvent,
                velocX: Float,
                velocY: Float) {
            // NOP.
        }

        override fun onLongPress(event: MotionEvent) {
            // NOP.
        }

        override fun onShowPress(event: MotionEvent) {
            // NOP.
        }

        override fun onSingleTapUp(event: MotionEvent) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSingleTapUp()")
        }
    }

    /**
     * Stop immediately without animation.
     */
    fun forceStop() {
        // Pause controller.
        if (isResumed) {
            // Kill animation.
            uiHandler.removeCallbacks(windowPositionCorrectionTask)

            // Hide surface.
            clearSurface()

            // Pause controller.
            controller.pause()

            // Fix position.
            windowLayoutParams.x = windowDisabledXY.x
            windowLayoutParams.y = windowDisabledXY.y
            windowManager.updateViewLayout(this, windowLayoutParams)

            isResumed = false
        }
    }

    /**
     * Overlay window is shown or not.
     *
     * @return Overlay window is shown or not
     */
    fun isOverlayShown(): Boolean {
        return windowLayoutParams.x != WINDOW_HIDDEN_POS_X
    }

    /**
     * Show overlay window.
     */
    fun show() {
        windowLayoutParams.x = windowDisabledXY.x
        windowLayoutParams.y = windowDisabledXY.y
        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    /**
     * Hide overlay window.
     */
    fun hide() {
        windowLayoutParams.x = WINDOW_HIDDEN_POS_X
        windowLayoutParams.y = windowDisabledXY.y
        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    override fun dispatchKeyEvent(keyEvent: KeyEvent): Boolean {
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_FOCUS -> {
                if (keyEvent.repeatCount != 0) {
                    // Do not handle hold press.
                    return true
                }

                when (keyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS DOWN]")
                    }

                    KeyEvent.ACTION_UP -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS UP]")
                    }

                    else -> {
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS NO ACT]")
                    }
                }
                return true
            }

            else -> {
                // Un-used key code.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [UNUSED KEY]")
                return false
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "onConfigurationChanged() : [Config=$newConfig]")
        super.onConfigurationChanged(newConfig)

        // Cache.
        val lastOrientation = orientation

        // Update UI.
        updateTotalUserInterface()

        // Force stop.
        forceStop()

        // Hide grip when configuration is landscape.
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape always.
            hide()
        } else {
            // Changed from landscape to portrait.
            if (lastOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                hide()
            }
        }
    }

    /**
     * Visual feedback trigger.
     */
    @SuppressWarnings("WeakerAccess") // False positive.
    interface VisualFeedbackTrigger {
        fun onScanStarted()
        fun onScanDone(isSuccess: Boolean)
        fun onShutterDone()
        fun clear()
    }

    /**
     * Get visual feedback trigger interface.
     *
     * @return Visual feedback trigger accessor.
     */
    fun getVisualFeedbackTrigger(): VisualFeedbackTrigger {
        return visualFeedbackTriggerImpl
    }

    private val visualFeedbackTriggerImpl = VisualFeedbackTriggerImpl()
    private inner class VisualFeedbackTriggerImpl : VisualFeedbackTrigger {
        private val SHUTTER_FEEDBACK_DURATION_MILLIS = 100L

        override fun onScanStarted() {
            updateIndicatorColor(customResContainer.colorScanOnGoing)
            scanIndicatorContainer.visibility = View.VISIBLE
        }

        override fun onScanDone(isSuccess: Boolean) {
            val color: Int
            if (isSuccess) {
                color = customResContainer.colorScanSuccess
            } else {
                color = customResContainer.colorScanFailure
            }
            updateIndicatorColor(color)
            scanIndicatorContainer.visibility = View.VISIBLE
        }

        override fun onShutterDone() {
            shutterFeedback.visibility = View.VISIBLE
            uiHandler.postDelayed(
                    recoverShutterFeedbackTask,
                    SHUTTER_FEEDBACK_DURATION_MILLIS)
        }

        private val recoverShutterFeedbackTask = RecoverShutterFeedbackTask()
        private inner class RecoverShutterFeedbackTask : Runnable {
            override fun run() {
                shutterFeedback.visibility = View.INVISIBLE
            }
        }

        override fun clear() {
            scanIndicatorContainer.visibility = View.INVISIBLE
        }

        private fun updateIndicatorColor(color: Int) {
            (0..(scanIndicatorContainer.childCount - 1))
                    .map { index -> scanIndicatorContainer.getChildAt(index) }
                    .forEach { view -> view.setBackgroundColor(color) }
            scanIndicatorContainer.invalidate()
        }
    }
}
