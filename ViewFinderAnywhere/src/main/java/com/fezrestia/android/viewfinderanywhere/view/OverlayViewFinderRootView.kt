@file:Suppress("PrivatePropertyName")

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
import android.widget.LinearLayout
import android.widget.RelativeLayout

import com.fezrestia.android.lib.interaction.InteractionEngine
import com.fezrestia.android.lib.util.log.Log
import com.fezrestia.android.lib.util.math.IntXY
import com.fezrestia.android.lib.util.math.IntWH
import com.fezrestia.android.viewfinderanywhere.R
import com.fezrestia.android.viewfinderanywhere.App
import com.fezrestia.android.viewfinderanywhere.Constants
import com.fezrestia.android.viewfinderanywhere.config.ConfigManager
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
    private lateinit var windowManager: WindowManager
    private lateinit var windowLayoutParams: WindowManager.LayoutParams

    // UI Plug-IN.
    // Viewfinder grip.
    private lateinit var viewfinder_grip_labelLandscapeBmp: Bitmap
    private lateinit var viewfinder_grip_labelPortraitBmp: Bitmap

    // Touch interaction engine.
    private lateinit var interactionEngine: InteractionEngine

    // Last window position.
    private val lastWindowXY = IntXY(0, 0)

    // Limit position.
    private val windowEnabledXY = IntXY(0, 0)
    private var windowDisabledXY = IntXY(0, 0)

    // Already resumed or not.
    private var isResumed = true

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

                // Fix position.
                windowLayoutParams.x = targetWindowXY.x
                windowLayoutParams.y = targetWindowXY.y

                windowManager.updateViewLayout(
                        this@OverlayViewFinderRootView,
                        windowLayoutParams)

                return
            }
            lastDelta.set(dXY)

            // Next.
            App.ui.postDelayed(
                    this,
                    WINDOW_ANIMATION_INTERVAL)

            if (Log.IS_DEBUG) Log.logDebug(TAG, "WindowPositionCorrectionTask.run() : X")
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
        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : E")

        // Cache instance references.
        cacheInstances()

        // Window related.
        createWindowParameters()

        // Update UI.
        updateTotalUserInterface()

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X")
    }

    private fun cacheInstances() {
        customResContainer = App.customResContainer
        val res = context.resources

        // Viewfinder related.
        viewfinder.surfaceTextureListener = surfaceTextureListenerImpl
        viewfinder.alpha = HIDDEN_ALPHA
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
        val gripSize = context.resources.getDimensionPixelSize(
                R.dimen.viewfinder_grip_size)

        val winX: Int
        val winY: Int
        val winW: Int
        val winH: Int

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
                winX = displayWH.longLen() - viewfinderWH.w - windowLayoutParams.x
                winY = displayWH.shortLen() - viewfinderWH.h - gripSize - windowLayoutParams.y
                winW = viewfinderWH.w
                winH = viewfinderWH.h + gripSize
                OverlayViewFinderWindowConfig.update(winX, winY, winW, winH)
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
                winX = displayWH.shortLen() - viewfinderWH.w - gripSize - windowLayoutParams.x
                winY = displayWH.longLen() - viewfinderWH.h - windowLayoutParams.y
                winW = viewfinderWH.w + gripSize
                winH = viewfinderWH.h
                OverlayViewFinderWindowConfig.update(winX, winY, winW, winH)
            }

            else -> {
                throw IllegalStateException("Unexpected orientation.")
            }
        }

        if (Log.IS_DEBUG) {
            Log.logDebug(TAG, "updateWindowParams() : X=$winX, Y=$winY, W=$winW, H=$winH")
        }

        // Check active.
        if (!isInitialSetup && !controller.currentState.isActive) {
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
        orientation = if (height < width) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
    }

    private fun calculateViewFinderSize() {
        // Define view finder size.
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                viewfinderWH.w =
                        (displayWH.longLen() * configManager.evfSize.scaleRate).toInt()
                viewfinderWH.h =
                        (viewfinderWH.w / configManager.evfAspect.ratioWH).toInt()
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                viewfinderWH.h =
                        (displayWH.longLen() * configManager.evfSize.scaleRate).toInt()
                viewfinderWH.w =
                        (viewfinderWH.h / configManager.evfAspect.ratioWH).toInt()
            }

            else -> {
                // Unexpected orientation.
                throw IllegalStateException("Unexpected orientation.")
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun updateLayoutParams() {
        val gripSize = context.resources.getDimensionPixelSize(
                R.dimen.viewfinder_grip_size)

        // Viewfinder size.
        run {
            val params = viewfinder_container.layoutParams
            params.width = viewfinderWH.w
            params.height = viewfinderWH.h
            viewfinder_container.layoutParams = params
        }

        val totalWidth: Int
        val totalHeight: Int
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                totalWidth = viewfinderWH.w
                totalHeight = viewfinderWH.h + gripSize
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                totalWidth = viewfinderWH.w + gripSize
                totalHeight = viewfinderWH.h
            }

            else -> {
                // Unexpected orientation.
                throw IllegalStateException("Unexpected orientation.")
            }
        }

        run {
            val params = total_background.layoutParams
            params.width = totalWidth
            params.height = totalHeight
            total_background.layoutParams = params

            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    total_background.setImageDrawable(customResContainer.drawableTotalBackLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    total_background.setImageDrawable(customResContainer.drawableTotalBackPort)
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
        }

        run {
            val params = total_foreground.layoutParams
            params.width = totalWidth
            params.height = totalHeight
            total_foreground.layoutParams = params

            when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    total_foreground.setImageDrawable(customResContainer.drawableTotalForeLand)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    total_foreground.setImageDrawable(customResContainer.drawableTotalForePort)
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
                    overlay_slider.orientation = LinearLayout.VERTICAL
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    overlay_slider.orientation = LinearLayout.HORIZONTAL
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
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

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
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
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    params.width = gripSize
                    params.height = (viewfinderWH.h * GRIP_SIZE_RATIO).toInt()
                    viewfinder_grip.setImageDrawable(customResContainer.drawableVfGripPort)
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
            }
            params.gravity = Gravity.TOP or Gravity.LEFT
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
                    params.gravity =
                            Gravity.TOP or Gravity.LEFT or Gravity.CENTER_VERTICAL
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.height = gripSize
                    if (viewfinderWH.w * GRIP_SIZE_RATIO < viewfinder_grip_labelLandscapeBmp.width) {
                        visibility = View.INVISIBLE
                    }
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    viewfinder_grip_label.setImageBitmap(viewfinder_grip_labelPortraitBmp)
                    viewfinder_grip_label.setPadding(
                            verticalPadding,
                            horizontalPadding,
                            0,
                            horizontalPadding)
                    params.gravity =
                            Gravity.CENTER_HORIZONTAL or Gravity.TOP or Gravity.LEFT
                    params.width = gripSize
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    if (viewfinderWH.h * GRIP_SIZE_RATIO < viewfinder_grip_labelPortraitBmp.height) {
                        visibility = View.INVISIBLE
                    }
                }

                else -> {
                    // Unexpected orientation.
                    throw IllegalStateException("Unexpected orientation.")
                }
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
                        this@OverlayViewFinderRootView.context,
                        this@OverlayViewFinderRootView,
                        0,
                        0,//ViewConfiguration.get(getContext()).getScaledTouchSlop(),
                        App.ui)
                interactionEngine.setInteractionCallback(InteractionCallbackImpl())
                setOnTouchListener(OnTouchListenerImpl())

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

            if (isResumed && viewfinder.alpha == HIDDEN_ALPHA) {
                showSurfaceTask.reset()
                App.ui.post(showSurfaceTask)
            }
        }
    }

    private fun clearSurface() {
        App.ui.removeCallbacks(showSurfaceTask)
        hideSurfaceTask.reset()
        App.ui.post(hideSurfaceTask)
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
            actualAlpha = viewfinder.alpha
        }

        protected abstract fun isFadeIn(): Boolean

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

            viewfinder.alpha = actualAlpha

            if (isNextTaskRequired) {
                // NOTICE:
                //   On Android N, invalidate() is called in setAlpha().
                //   So, this task takes 1 frame V-Sync millis (about 16[ms])
                //   Not to delay fade in/out, post next control task immediately.
                App.ui.post(this)
            }
        }
    }

    private val showSurfaceTask = ShowSurfaceTask()
    private inner class ShowSurfaceTask : SurfaceVisibilityControlTask() {
        override fun isFadeIn(): Boolean = true
    }


    private val hideSurfaceTask = HideSurfaceTask()
    private inner class HideSurfaceTask : SurfaceVisibilityControlTask() {
        override fun isFadeIn() : Boolean = false
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

            interactionEngine.onTouchEvent(event)
            return true
        }
    }

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
            windowPositionCorrectionTask?.let { task ->
                App.ui.removeCallbacks(task)
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
            if (touchSlop < abs(diffX) || touchSlop < abs(diffY)) {
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
                    diffToEnable = abs(windowEnabledXY.y - windowLayoutParams.y)
                    diffToDisable = abs(windowDisabledXY.y - windowLayoutParams.y)
                }

                Configuration.ORIENTATION_PORTRAIT -> {
                    diffToEnable = abs(windowEnabledXY.x - windowLayoutParams.x)
                    diffToDisable = abs(windowDisabledXY.x - windowLayoutParams.x)
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
                    if (viewfinder.isAvailable) {
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
            WindowPositionCorrectionTask(target).let { task ->
                App.ui.postDelayed(task, WINDOW_ANIMATION_INTERVAL)
                windowPositionCorrectionTask = task
            }
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
            windowPositionCorrectionTask?.let { task ->
                App.ui.removeCallbacks(task)
                windowPositionCorrectionTask = null
            }

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
    fun isOverlayShown(): Boolean = windowLayoutParams.x != WINDOW_HIDDEN_POS_X

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

    companion object {
        // Log tag.
        const val TAG = "OverlayViewFinderRootView"

        // Window position correction animation interval.
        private const val WINDOW_ANIMATION_INTERVAL = 16L

        // Hidden window position.
        private const val WINDOW_HIDDEN_POS_X = -5000

        // Alpha definitions.
        private const val SHOWN_ALPHA = 1.0f
        private const val HIDDEN_ALPHA = 0.0f
    }
}
