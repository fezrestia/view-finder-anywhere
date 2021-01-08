@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.viewfinderanywhere.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
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
import android.widget.LinearLayout
import android.widget.RelativeLayout

import com.fezrestia.android.lib.interaction.InteractionEngine
import com.fezrestia.android.lib.util.currentDisplayRect
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.math.IntXY
import com.fezrestia.android.lib.util.math.IntWH
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
import com.fezrestia.android.viewfinderanywhere.config.options.ViewFinderAlign
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController
import com.fezrestia.android.viewfinderanywhere.plugin.ui.CustomizableResourceContainer
import kotlinx.android.synthetic.main.overlay_view_finder_root.view.*
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Window root view class.
 */
class OverlayViewFinderRootView : RelativeLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    // Core instances.
    private lateinit var controller: OverlayViewFinderController
    private lateinit var configManager: ConfigManager

    // Display coordinates.
    private val displayWH = IntWH(0, 0)

    // Overlay window orientation.
    private var orientation = Configuration.ORIENTATION_UNDEFINED

    // View finder target size.
    private val viewfinderWH = IntWH(0, 0)

    // Window.
    lateinit var windowManager: WindowManager
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    // UI Plug-IN.
    // Viewfinder grip.
    private lateinit var viewfinder_grip_labelLandscapeBmp: Bitmap
    private lateinit var viewfinder_grip_labelPortraitBmp: Bitmap

    // Touch interaction engine.
    private var interactionEngine: InteractionEngine? = null

    // Last window position.
    private val lastWindowXY = IntXY(0, 0)

    // Limit position.
    private val windowEnabledXY = IntXY(0, 0)
    private var windowDisabledXY = IntXY(0, 0)

    // Resources.
    private lateinit var customResContainer: CustomizableResourceContainer

    // Grip size ratio. Grip size is view finder size x ratio.
    private val GRIP_SIZE_RATIO = 0.5f

    // Current window position correction task
    private var windowPositionCorrectionTask: Runnable? = null
    private inner class WindowPositionCorrectionTask(val targetWindowXY: IntXY) : Runnable {
        // Proportional gain.
        private val P_GAIN = 0.3f

        // Last delta.
        private val lastDelta = IntXY(0, 0)

        override fun run() {
            if (IS_DEBUG) logD(TAG, "WindowPositionCorrectionTask.run() : E")

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
                if (IS_DEBUG) logD(TAG, "Already detached from window.")
                return
            }

            // Check next.
            if (lastDelta == dXY) {
                // Correction is already convergent.
                if (IS_DEBUG) logD(TAG, "Already position fixed.")

                // Fix position.
                windowLayoutParams.x = targetWindowXY.x
                windowLayoutParams.y = targetWindowXY.y

                windowManager.updateViewLayout(
                        this@OverlayViewFinderRootView,
                        windowLayoutParams)

                when (targetWindowXY) {
                    windowEnabledXY -> enableInteraction()
                    windowDisabledXY -> disableInteraction()
                    else -> throw RuntimeException("Invalid targetWindowXY = $targetWindowXY")
                }

                return
            }
            lastDelta.set(dXY)

            // Next.
            App.ui.postDelayed(
                    this,
                    WINDOW_ANIMATION_INTERVAL)

            if (IS_DEBUG) logD(TAG, "WindowPositionCorrectionTask.run() : X")
        }
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
        if (IS_DEBUG) logD(TAG, "initialize() : E")

        // Cache instance references.
        cacheInstances()

        // Window related.
        createWindowParameters()

        if (IS_DEBUG) logD(TAG, "initialize() : X")
    }

    private fun cacheInstances() {
        customResContainer = App.customResContainer
        val res = context.resources

        // Viewfinder related.
        viewfinder.surfaceTextureListener = surfaceTextureListenerImpl
        viewfinder_background.setBackgroundColor(customResContainer.colorVfBackground)

        // Scan indicator.
        scan_indicator_container.visibility = INVISIBLE

        // Shutter feedback.
        shutter_feedback.visibility = View.INVISIBLE

        // UI-Plug-IN.
        // Viewfinder grip.
        val paint = Paint()
        paint.textSize = res.getDimensionPixelSize(R.dimen.viewfinder_grip_label_font_size)
                .toFloat()
        paint.color = customResContainer.colorGripLabel
        paint.isAntiAlias = true
        paint.typeface = Typeface.createFromAsset(
                context.assets,
                Constants.FONT_FILENAME_CODA)
        val fontMetrics = paint.fontMetrics
        val textHeight = ceil(abs(fontMetrics.ascent).toDouble()
                + abs(fontMetrics.descent)
                + abs(fontMetrics.leading)).toInt()
        val textLabel = res.getString(R.string.viewfinder_grip_label)
        val textWidth = ceil(paint.measureText(textLabel).toDouble()).toInt()
        viewfinder_grip_labelLandscapeBmp = Bitmap.createBitmap(
                textWidth,
                textHeight,
                Bitmap.Config.ARGB_8888)
        val c = Canvas(viewfinder_grip_labelLandscapeBmp)
        c.drawText(textLabel, 0.0f, abs(fontMetrics.ascent), paint)
        val matrix = Matrix()
        matrix.postRotate(-90.0f)
        viewfinder_grip_labelPortraitBmp = Bitmap.createBitmap(
                viewfinder_grip_labelLandscapeBmp,
                0,
                0,
                viewfinder_grip_labelLandscapeBmp.width,
                viewfinder_grip_labelLandscapeBmp.height,
                matrix,
                true)
    }

    private fun createWindowParameters() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                INTERACTIVE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT)
    }

    private fun enableInteraction() {
        windowLayoutParams.flags = INTERACTIVE_WINDOW_FLAGS
        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    private fun disableInteraction() {
        windowLayoutParams.flags = NOT_INTERACTIVE_WINDOW_FLAGS
        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    /**
     * Release all resources.
     */
    fun release() {
        releaseWindowPositionCorrector()

        interactionEngine?.let {
            it.callback = null
            it.release()
            interactionEngine = null
        }
        viewfinder.surfaceTextureListener = null
        setOnTouchListener(null)

        // UI Plug-IN.
        viewfinder_grip.setImageDrawable(null)
        viewfinder_grip_label.setImageBitmap(null)
        viewfinder_frame.setImageDrawable(null)
        total_background.setImageDrawable(null)
        total_foreground.setImageDrawable(null)
    }

    private fun releaseWindowPositionCorrector() {
        windowPositionCorrectionTask?.let { task ->
            App.ui.removeCallbacks(task)
            windowPositionCorrectionTask = null
        }
    }

    /**
     * Add this view to WindowManager layer.
     */
    fun addToOverlayWindow() {
        // Update UI.
        updateTotalUserInterface()

        // Add to WindowManager.
        val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        winMng.addView(this, windowLayoutParams)
    }

    /**
     * Remove this view from WindowManager layer.
     */
    fun removeFromOverlayWindow() {
        // Remove from to WindowManager.
        val winMng = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        winMng.removeView(this)

        // Release drawing cache.
        App.ui.post(ReleaseDrawingResourceTask())
    }

    private inner class ReleaseDrawingResourceTask : Runnable {
        override fun run() {
            if (!viewfinder_grip_labelLandscapeBmp.isRecycled) {
                viewfinder_grip_labelLandscapeBmp.recycle()
            }
            if (!viewfinder_grip_labelPortraitBmp.isRecycled) {
                viewfinder_grip_labelPortraitBmp.recycle()
            }
        }
    }

    private fun updateTotalUserInterface() {
        // Screen configuration.
        calculateScreenConfiguration()
        // View finder size.
        calculateViewFinderSize()
        // Window layout.
        updateWindowParams()
        // Update layout.
        updateLayoutParams()
    }

    private fun calculateScreenConfiguration() {
        // Get display size.
        val rect = currentDisplayRect(windowManager)
        displayWH.set(rect.width(), rect.height())
        if (IS_DEBUG) logD(TAG, "displayWH = ${displayWH.w} x ${displayWH.h}")

        // Get display orientation.
        orientation = if (displayWH.w > displayWH.h) {
            if (IS_DEBUG) logD(TAG, "orientation = LANDSCAPE")
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            if (IS_DEBUG) logD(TAG, "orientation = PORTRAIT")
            Configuration.ORIENTATION_PORTRAIT
        }
    }

    private fun calculateViewFinderSize() {
        val gripSize = context.resources.getDimensionPixelSize(R.dimen.viewfinder_grip_size)

        // Define view finder size.
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                var checkW = (displayWH.w * configManager.evfSize.scaleRate).toInt()
                var checkH = (checkW / configManager.evfAspect.ratioWH).toInt()

                if (checkH > (displayWH.h - gripSize)) {
                    // Fit overlay window height to display height.
                    checkH = displayWH.h - gripSize
                    checkW = (checkH * configManager.evfAspect.ratioWH).toInt()
                }

                viewfinderWH.w = checkW
                viewfinderWH.h = checkH
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                var checkH = (displayWH.h * configManager.evfSize.scaleRate).toInt()
                var checkW = (checkH / configManager.evfAspect.ratioWH).toInt()

                if (checkW > (displayWH.w - gripSize)) {
                    // Fit overlay window width to display width.
                    checkW = displayWH.w - gripSize
                    checkH = (checkW * configManager.evfAspect.ratioWH).toInt()
                }

                viewfinderWH.w = checkW
                viewfinderWH.h = checkH
            }

            else -> throw IllegalStateException("Unexpected orientation.")
        }

        if (IS_DEBUG) logD(TAG, "viewfinderWH = ${viewfinderWH.w} x ${viewfinderWH.h}")
    }

    @SuppressLint("RtlHardcoded")
    private fun updateWindowParams() {
        if (IS_DEBUG) logD(TAG, "updateWindowParams() : E")

        releaseWindowPositionCorrector()
        val edgeClearance = context.resources.getDimensionPixelSize(
                R.dimen.viewfinder_edge_clearance)
        val gripSize = context.resources.getDimensionPixelSize(
                R.dimen.viewfinder_grip_size)
        if (IS_DEBUG) {
            logD(TAG, "edgeClearance = $edgeClearance")
            logD(TAG, "gripSize = $gripSize")
        }

        val winX: Int
        val winY: Int
        val winW: Int
        val winH: Int

        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        windowLayoutParams.gravity = Gravity.BOTTOM or Gravity.LEFT
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        windowLayoutParams.gravity = Gravity.BOTTOM or Gravity.RIGHT
                    }
                }

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

                // Size.
                windowLayoutParams.width = viewfinderWH.w
                windowLayoutParams.height = viewfinderWH.h + gripSize

                // Cache.
                when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        winX = windowLayoutParams.x
                        winY = displayWH.h - viewfinderWH.h - gripSize - windowLayoutParams.y
                        winW = windowLayoutParams.width
                        winH = windowLayoutParams.height
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        winX = displayWH.w - viewfinderWH.w - windowLayoutParams.x
                        winY = displayWH.h - viewfinderWH.h - gripSize - windowLayoutParams.y
                        winW = windowLayoutParams.width
                        winH = windowLayoutParams.height
                    }
                }
                OverlayViewFinderWindowConfig.update(winX, winY, winW, winH)
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        windowLayoutParams.gravity = Gravity.TOP or Gravity.RIGHT
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        windowLayoutParams.gravity = Gravity.BOTTOM or Gravity.RIGHT
                    }
                }

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

                // Size.
                windowLayoutParams.width = viewfinderWH.w + gripSize
                windowLayoutParams.height = viewfinderWH.h

                // Cache.
                when (configManager.evfAlign) {
                    ViewFinderAlign.TOP_OR_LEFT -> {
                        winX = displayWH.w - viewfinderWH.w - gripSize - windowLayoutParams.x
                        winY = windowLayoutParams.y
                        winW = windowLayoutParams.width
                        winH = windowLayoutParams.height
                    }
                    ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                        winX = displayWH.w - viewfinderWH.w - gripSize - windowLayoutParams.x
                        winY = displayWH.h - viewfinderWH.h - windowLayoutParams.y
                        winW = windowLayoutParams.width
                        winH = windowLayoutParams.height
                    }
                }
                OverlayViewFinderWindowConfig.update(winX, winY, winW, winH)
            }

            else -> {
                throw IllegalStateException("Unexpected orientation.")
            }
        }

        if (IS_DEBUG) {
            logD(TAG, "updateWindowParams() : X=$winX, Y=$winY, W=$winW, H=$winH")
        }

        // Check active.
        if (!controller.lifeCycle().isActive) {
            windowLayoutParams.x = windowDisabledXY.x
            windowLayoutParams.y = windowDisabledXY.y
        }

        if (isAttachedToWindow) {
            windowManager.updateViewLayout(this, windowLayoutParams)
        }

        if (IS_DEBUG) logD(TAG, "updateWindowParams() : X")
    }

    @SuppressLint("RtlHardcoded")
    private fun updateLayoutParams() {
        val gripSize = context.resources.getDimensionPixelSize(R.dimen.viewfinder_grip_size)

        // Viewfinder size.
        run {
            val params = viewfinder_container.layoutParams
            params.width = viewfinderWH.w
            params.height = viewfinderWH.h
            viewfinder_container.layoutParams = params
        }

        run {
            val params = total_background.layoutParams
            params.width = windowLayoutParams.width
            params.height = windowLayoutParams.height
            total_background.layoutParams = params

            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    total_background.setImageDrawable(customResContainer.drawableTotalBackLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    total_background.setImageDrawable(customResContainer.drawableTotalBackPort)
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }
        }

        run {
            val params = total_foreground.layoutParams
            params.width = windowLayoutParams.width
            params.height = windowLayoutParams.height
            total_foreground.layoutParams = params

            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    total_foreground.setImageDrawable(customResContainer.drawableTotalForeLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    total_foreground.setImageDrawable(customResContainer.drawableTotalForePort)
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }
        }

        // Viewfinder slider.
        run {
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    overlay_slider.orientation = LinearLayout.VERTICAL
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    overlay_slider.orientation = LinearLayout.HORIZONTAL
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }
        }

        // Grip container.
        run {
            val params = viewfinder_grip_container.layoutParams
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    params.width = viewfinderWH.w
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.height = viewfinderWH.h
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }
            viewfinder_grip_container.layoutParams = params
        }

        // Viewfinder grip.
        run {
            val params = viewfinder_grip.layoutParams as FrameLayout.LayoutParams
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    params.width = (viewfinderWH.w * GRIP_SIZE_RATIO).toInt()
                    params.height = gripSize
                    viewfinder_grip.setImageDrawable(customResContainer.drawableVfGripLand)

                    when (configManager.evfAlign) {
                        ViewFinderAlign.TOP_OR_LEFT -> {
                            params.gravity = Gravity.TOP or Gravity.LEFT
                        }
                        ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                            params.gravity = Gravity.TOP or Gravity.RIGHT
                        }
                    }
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    params.width = gripSize
                    params.height = (viewfinderWH.h * GRIP_SIZE_RATIO).toInt()
                    viewfinder_grip.setImageDrawable(customResContainer.drawableVfGripPort)

                    when (configManager.evfAlign) {
                        ViewFinderAlign.TOP_OR_LEFT -> {
                            params.gravity = Gravity.BOTTOM or Gravity.LEFT
                        }
                        ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                            params.gravity = Gravity.TOP or Gravity.LEFT
                        }
                    }
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }

            viewfinder_grip.layoutParams = params
        }

        run {
            val params = viewfinder_grip_label.layoutParams as FrameLayout.LayoutParams
            val horizontalPadding = context.resources.getDimensionPixelSize(
                    R.dimen.viewfinder_grip_label_horizontal_padding)
            val verticalPadding = context.resources.getDimensionPixelSize(
                    R.dimen.viewfinder_grip_label_vertical_padding)
            var visibility = View.VISIBLE
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    viewfinder_grip_label.setImageBitmap(viewfinder_grip_labelLandscapeBmp)
                    viewfinder_grip_label.setPadding(
                            horizontalPadding,
                            verticalPadding,
                            horizontalPadding,
                            0)
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.height = gripSize
                    if (viewfinderWH.w * GRIP_SIZE_RATIO < viewfinder_grip_labelLandscapeBmp.width) {
                        visibility = View.INVISIBLE
                    }

                    when (configManager.evfAlign) {
                        ViewFinderAlign.TOP_OR_LEFT -> {
                            params.gravity = Gravity.TOP or Gravity.LEFT or Gravity.CENTER_VERTICAL
                        }
                        ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                            params.gravity = Gravity.TOP or Gravity.RIGHT or Gravity.CENTER_VERTICAL
                        }
                    }
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    viewfinder_grip_label.setImageBitmap(viewfinder_grip_labelPortraitBmp)
                    viewfinder_grip_label.setPadding(
                            verticalPadding,
                            horizontalPadding,
                            0,
                            horizontalPadding)
                    params.width = gripSize
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    if (viewfinderWH.h * GRIP_SIZE_RATIO < viewfinder_grip_labelPortraitBmp.height) {
                        visibility = View.INVISIBLE
                    }

                    when (configManager.evfAlign) {
                        ViewFinderAlign.TOP_OR_LEFT -> {
                            params.gravity = Gravity.BOTTOM or Gravity.LEFT or Gravity.LEFT
                        }
                        ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                            params.gravity = Gravity.TOP or Gravity.LEFT or Gravity.LEFT
                        }
                    }
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }
            viewfinder_grip_label.visibility = visibility
            viewfinder_grip_label.layoutParams = params
        }

        // Viewfinder frame.
        run {
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    viewfinder_frame.setImageDrawable(customResContainer.drawableVfFrameLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    viewfinder_frame.setImageDrawable(customResContainer.drawableVfFramePort)
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }
        }
    }

    private val surfaceTextureListenerImpl = SurfaceTextureListenerImpl()
    private inner class SurfaceTextureListenerImpl : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (IS_DEBUG) logD(TAG,
                    "onSurfaceTextureAvailable() : [W=$width] [H=$height]")

            controller.fromView().onSurfaceCreated()

            checkViewFinderAspect(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            if (IS_DEBUG) logD(TAG,
                    "onSurfaceTextureSizeChanged() : [W=$width] [H=$height]")

            checkViewFinderAspect(width, height)
        }

        private fun checkViewFinderAspect(width: Int, height: Int) {
            if (width == viewfinderWH.w && height == viewfinderWH.h) {
                if (IS_DEBUG) logD(TAG, "checkViewFinderAspect() : Resize DONE")
                // Resize done.

                // Set touch interceptor.
                interactionEngine = InteractionEngine(
                        this@OverlayViewFinderRootView.context,
                        this@OverlayViewFinderRootView,
                        0,
                        0,//ViewConfiguration.get(getContext()).getScaledTouchSlop(),
                        App.ui).apply {
                    this.callback = InteractionCallbackImpl()
                }
                setOnTouchListener(OnTouchListenerImpl())

                // Notify to device.
                controller.fromView().onSurfaceReady()
            } else {
                if (IS_DEBUG) logD(TAG, "checkViewFinderAspect() : Now on resizing...")
                // NOP. Now on resizing.
            }
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            if (IS_DEBUG) logD(TAG, "onSurfaceTextureDestroyed()")
            controller.fromView().onSurfaceReleased()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
//            if (IS_DEBUG) logD(TAG, "onSurfaceTextureUpdated()")
            // NOP.
        }
    }

    /**
     * Get view finder target surface.
     *
     * @return Finder TextureView.
     */
    fun getViewFinderSurface(): TextureView = viewfinder

    private inner class OnTouchListenerImpl : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            // Use absolute position, because window position change affects view motion event.
            event.setLocation(event.rawX, event.rawY)

            interactionEngine?.onTouchEvent(event)
            return true
        }
    }

    private inner class InteractionCallbackImpl : InteractionEngine.InteractionCallback {
        override fun onSingleTouched(point: Point) {
            if (IS_DEBUG) logD(TAG,
                    "onSingleTouched() : [X=${point.x}] [Y=${point.y}]")

            // Pre-open.
            controller.fromView().onPreOpenRequested()

            // Request scan.
            controller.fromView().requestScan()

            // Stop animation.
            windowPositionCorrectionTask?.let { task ->
                App.ui.removeCallbacks(task)
                windowPositionCorrectionTask = null
            }

            // Cache current position.
            lastWindowXY.x = windowLayoutParams.x
            lastWindowXY.y = windowLayoutParams.y
        }

        override fun onSingleMoved(currentPoint: Point, lastPoint: Point, downPoint: Point) {
//            if (IS_DEBUG) logD(TAG,
//                    "onSingleMoved() : [X=${currentPoint.x}] [Y=${currentPoint.y}]")

            val diffX = currentPoint.x - downPoint.x
            val diffY = currentPoint.y - downPoint.y

            // Check moving.
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            if (touchSlop < abs(diffX) || touchSlop < abs(diffY)) {
                controller.fromView().requestCancelScan()
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

                else -> throw IllegalStateException("Unexpected orientation.")
            }

            if (windowLayoutParams.x != newX || windowLayoutParams.y != newY) {
                windowLayoutParams.x = newX
                windowLayoutParams.y = newY
                windowManager.updateViewLayout(this@OverlayViewFinderRootView, windowLayoutParams)
            }
        }

        override fun onSingleStopped(currentPoint: Point, lastPoint: Point, downPoint: Point) {
//            if (IS_DEBUG) logD(TAG,
//                    "onSingleStopped() : [X=${currentPoint.x}] [Y=${currentPoint.y}]")
            // NOP.
        }

        override fun onSingleReleased(point: Point) {
            if (IS_DEBUG) logD(TAG,
                    "onSingleReleased() : [X=${point.x}] [Y=${point.y}]")

            // Request still capture.
            controller.fromView().requestStillCapture()

            // Decide correction target position.
            val diffToEnable: Int
            val diffToDisable: Int
            val target: IntXY
            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    diffToEnable = abs(windowEnabledXY.y - windowLayoutParams.y)
                    diffToDisable = abs(windowDisabledXY.y - windowLayoutParams.y)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    diffToEnable = abs(windowEnabledXY.x - windowLayoutParams.x)
                    diffToDisable = abs(windowDisabledXY.x - windowLayoutParams.x)
                }

                else -> throw IllegalStateException("Unexpected orientation.")
            }
            if (diffToEnable < diffToDisable) {
                // To be resumed.

                target = windowEnabledXY

                controller.resume()
            } else {
                // To be paused.

                target = windowDisabledXY

                // Not resumed, cancel pre-open.
                controller.fromView().onPreOpenCanceled()

                controller.pause()
            }

            // Correct position start.
            WindowPositionCorrectionTask(target).let { task ->
                App.ui.postDelayed(task, WINDOW_ANIMATION_INTERVAL)
                windowPositionCorrectionTask = task
            }
        }

        override fun onSingleCanceled() {
            if (IS_DEBUG) logD(TAG, "onSingleCanceled()")
            // NOP.
        }

        override fun onDoubleTouched(point0: Point, point1: Point) {
            // NOP.
        }

        override fun onDoubleMoved(point0: Point, point1: Point) {
            // NOP.
        }

        override fun onDoubleScaled(currentLength: Float, previousLength: Float, originalLength: Float) {
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
            if (IS_DEBUG) logD(TAG, "onSingleTapUp()")
        }
    }

    /**
     * Overlay window is visible (OPEN or CLOSE state) or not.
     *
     * @return Visible or not
     */
    fun isVisible(): Boolean = windowLayoutParams.x != WINDOW_INVISIBLE_POS_X

//    /**
//     * Change overlay window position to OPEN state.
//     */
//    fun open() {
//        windowLayoutParams.x = windowEnabledXY.x
//        windowLayoutParams.y = windowEnabledXY.y
//        windowManager.updateViewLayout(this, windowLayoutParams)
//    }

    /**
     * Change overlay window position to CLOSE state.
     */
    fun close() {
        windowLayoutParams.x = windowDisabledXY.x
        windowLayoutParams.y = windowDisabledXY.y
        windowManager.updateViewLayout(this, windowLayoutParams)

        controller.pause()

        disableInteraction()
    }

    /**
     * Change overlay window position to INVISIBLE state.
     */
    fun invisible() {
        windowLayoutParams.x = WINDOW_INVISIBLE_POS_X
        windowLayoutParams.y = windowDisabledXY.y
        windowManager.updateViewLayout(this, windowLayoutParams)

        controller.pause()

        disableInteraction()
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
                        if (IS_DEBUG) logD(TAG, "dispatchKeyEvent() : [FOCUS DOWN]")
                    }

                    KeyEvent.ACTION_UP -> {
                        if (IS_DEBUG) logD(TAG, "dispatchKeyEvent() : [FOCUS UP]")
                    }

                    else -> {
                        if (IS_DEBUG) logD(TAG, "dispatchKeyEvent() : [FOCUS NO ACT]")
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (keyEvent.repeatCount != 0) {
                    // Do not handle hold press.
                    return true
                }

                when (keyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (IS_DEBUG) logD(TAG, "dispatchKeyEvent() : [VOLUME- DOWN]")

                        controller.fromView().requestStartRec()
                    }

                    KeyEvent.ACTION_UP -> {
                        if (IS_DEBUG) logD(TAG, "dispatchKeyEvent() : [VOLUME- UP]")

                        controller.fromView().requestStopRec()
                    }

                    else -> {
                        if (IS_DEBUG) logD(TAG, "dispatchKeyEvent() : [VOLUME- NO ACT]")
                    }
                }

                return true
            }

            else -> {
                // Un-used key code.
                if (IS_DEBUG) logD(TAG, "dispatchKeyEvent() : [UNUSED KEY]")
                return false
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (IS_DEBUG) logD(TAG,
                "onConfigurationChanged() : [Config=$newConfig]")
        super.onConfigurationChanged(newConfig)

        controller.pause()
        controller.release()

        controller.ready()
        controller.resume()

        // TODO: Consider to open/close overlay window align to previous state.

    }

    /**
     * Visual feedback trigger.
     */
    @SuppressWarnings("WeakerAccess") // False positive.
    interface VisualFeedbackTrigger {
        fun onScanStarted()
        fun onScanDone(isSuccess: Boolean)
        fun onShutterDone()
        fun onRecStarted()
        fun onRecStopped()
        fun clear()
    }

    /**
     * Get visual feedback trigger interface.
     *
     * @return Visual feedback trigger accessor.
     */
    fun getVisualFeedbackTrigger(): VisualFeedbackTrigger = visualFeedbackTriggerImpl

    private val visualFeedbackTriggerImpl = VisualFeedbackTriggerImpl()
    private inner class VisualFeedbackTriggerImpl : VisualFeedbackTrigger {
        private val SHUTTER_FEEDBACK_DURATION_MILLIS = 100L

        override fun onScanStarted() {
            updateIndicatorColor(customResContainer.colorScanOnGoing)
            scan_indicator_container.visibility = View.VISIBLE
        }

        override fun onScanDone(isSuccess: Boolean) {
            val color: Int = if (isSuccess) {
                customResContainer.colorScanSuccess
            } else {
                customResContainer.colorScanFailure
            }
            updateIndicatorColor(color)
            scan_indicator_container.visibility = View.VISIBLE
        }

        override fun onShutterDone() {
            shutter_feedback.visibility = View.VISIBLE
            App.ui.postDelayed(
                    recoverShutterFeedbackTask,
                    SHUTTER_FEEDBACK_DURATION_MILLIS)
        }

        private val recoverShutterFeedbackTask = RecoverShutterFeedbackTask()
        private inner class RecoverShutterFeedbackTask : Runnable {
            override fun run() {
                shutter_feedback.visibility = View.INVISIBLE
            }
        }

        override fun onRecStarted() {
            updateIndicatorColor(customResContainer.colorRec)
            scan_indicator_container.visibility = View.VISIBLE
        }

        override fun onRecStopped() {
            scan_indicator_container.visibility = View.INVISIBLE
        }

        override fun clear() {
            scan_indicator_container.visibility = View.INVISIBLE
        }

        private fun updateIndicatorColor(color: Int) {
            (0 until scan_indicator_container.childCount)
                    .map { index -> scan_indicator_container.getChildAt(index) }
                    .forEach { view -> view.setBackgroundColor(color) }
            scan_indicator_container.invalidate()
        }
    }

    // To ignore global navigation gesture on side edge.
    private val navExclusionRect = Rect()
    private val NAV_EXCLUSION_MAX_DP = 200.0f
    private val NAV_EXCLUSION_MAX_PX = (NAV_EXCLUSION_MAX_DP * resources.displayMetrics.density).toInt()
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (bottom - top > NAV_EXCLUSION_MAX_PX) {
            // Exclude partial area.

            when (configManager.evfAlign) {
                ViewFinderAlign.TOP_OR_LEFT -> {
                    // Exclude bottom area. (near grip area)
                    navExclusionRect.set(left, bottom - NAV_EXCLUSION_MAX_PX, right, bottom)
                }
                ViewFinderAlign.BOTTOM_OR_RIGHT -> {
                    // Exclude top area. (near grip area)
                    navExclusionRect.set(left, top, right, top + NAV_EXCLUSION_MAX_PX)
                }
            }
        } else {
            // Exclude whole area.
            navExclusionRect.set(left, top, right, bottom)
        }

        val list = listOf(navExclusionRect)
        this.systemGestureExclusionRects = list
    }

    companion object {
        // Log tag.
        const val TAG = "OverlayViewFinderRootView"

        private const val INTERACTIVE_WINDOW_FLAGS = ( 0 // Dummy
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                )

        private const val NOT_INTERACTIVE_WINDOW_FLAGS = ( 0 // Dummy
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                )

        // Window position correction animation interval.
        private const val WINDOW_ANIMATION_INTERVAL = 16L

        // Hidden window position.
        private const val WINDOW_INVISIBLE_POS_X = -5000

    }
}
