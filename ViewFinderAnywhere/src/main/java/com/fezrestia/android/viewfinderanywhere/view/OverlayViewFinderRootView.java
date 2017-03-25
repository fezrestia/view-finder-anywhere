package com.fezrestia.android.viewfinderanywhere.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.fezrestia.android.lib.interaction.InteractionEngine;
import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants.ViewFinderGripPosition;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereConstants.ViewFinderGripSize;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;


public class OverlayViewFinderRootView extends RelativeLayout {
    // Log tag.
    private static final String TAG = "OverlayViewFinderRootView";

    // Root view.
    private RelativeLayout mRootView = null;

    // Display coordinates.
    private int mDisplayLongLineLength = 0;
    private int mDisplayShortLineLength = 0;

    // Overlay window orientation.
    private int mOrientation = Configuration.ORIENTATION_UNDEFINED;

    // View finder target size.
    private int mViewFinderWidth = 0;
    private int mViewFinderHeight = 0;

    // Pre-loaded preferences
    private float mViewFinderScaleRatioAgainstToScreen
            = ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_SMALL;

    // Window.
    private WindowManager mWindowManager = null;
    private WindowManager.LayoutParams mWindowLayoutParams = null;

    // Slider.
    private LinearLayout mOverlaySlider = null;

    // Viewfinder related.
    private FrameLayout mViewFinderContainer = null;
    private ImageView mViewFinderBackground = null;
    private TextureView mViewFinder = null;
    private View mShutterFeedback = null;

    // Scan indicator.
    private ViewGroup mScanIndicatorContainer = null;

    // UI Plug-IN.
    // Viewfinder grip.
    private ViewGroup mViewFinderGripContainer = null;
    private ImageView mViewFinderGrip = null;
    private ImageView mViewFinderGripLabel = null;
    private Bitmap mViewFinderGripLabelLandscapeBitmap = null;
    private Bitmap mViewFinderGripLabelPortraitBitmap = null;
    // Viewfinder frame.
    private ImageView mViewFinderFrame = null;
    // Total cover.
    private ImageView mTotalBackground = null;
    private ImageView mTotalForeground = null;

    // Touch interaction engine.
    private InteractionEngine mInteractionEngine = null;

    // Resident flag. Always on overlay window.
    private boolean mIsResidence = false;

    // Last window position.
    private Point mLastWindowPosition = new Point();

    // Limit position.
    private Point mWindowEnabledPosit = new Point();
    private Point mWindowDisabledPosit = new Point();

    // Window position correction animation interval.
    private static final int WINDOW_ANIMATION_INTERVAL = 16;

    // Current window position correction task
    private WindowPositionCorrectionTask mWindowPositionCorrectionTask = null;
    private class WindowPositionCorrectionTask implements Runnable {
        private final Point mTargetWindowPosit;

        // Proportional gain.
        private static final float P_GAIN = 0.3f;

        // Last delta.
        private int mLastDeltaX = 0;
        private int mLastDeltaY = 0;

        /**
         * CONSTRUCTOR.
         *
         * @param target Position control target.
         */
        WindowPositionCorrectionTask(Point target) {
            mTargetWindowPosit = target;
        }

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "WindowPositionCorrectionTask.run() : E");

            final int dX = mTargetWindowPosit.x - mWindowLayoutParams.x;
            final int dY = mTargetWindowPosit.y - mWindowLayoutParams.y;

            // Update layout.
            mWindowLayoutParams.x += (int) (dX * P_GAIN);
            mWindowLayoutParams.y += (int) (dY * P_GAIN);

            if (OverlayViewFinderRootView.this.isAttachedToWindow()) {
                mWindowManager.updateViewLayout(
                        OverlayViewFinderRootView.this,
                        mWindowLayoutParams);
            } else {
                // Already detached from window.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already detached from window.");
                return;
            }

            // Check next.
            if (mLastDeltaX == dX && mLastDeltaY == dY) {
                // Correction is already convergent.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "Already position fixed.");

                // Remove or fix position.
                if (!mIsResidence && (mTargetWindowPosit.equals(mWindowDisabledPosit))) {
                    // Remove.
                    OverlayViewFinderController.LifeCycleTrigger.getInstance()
                                .requestStop(getContext());
                } else {
                    // Fix position.
                    mWindowLayoutParams.x = mTargetWindowPosit.x;
                    mWindowLayoutParams.y = mTargetWindowPosit.y;

                    mWindowManager.updateViewLayout(
                            OverlayViewFinderRootView.this,
                            mWindowLayoutParams);
                }
                return;
            }
            mLastDeltaX = dX;
            mLastDeltaY = dY;

            // Next.
            OverlayViewFinderRootView.this.getHandler().postDelayed(
                    this,
                    WINDOW_ANIMATION_INTERVAL);

            if (Log.IS_DEBUG) Log.logDebug(TAG, "WindowPositionCorrectionTask.run() : X");
        }
    }

    // Already resumed or not.
    private boolean mIsResumed = true;

    // View finder aspect.
    private float mViewFinderAspectWH = ViewFinderAnywhereConstants.ASPECT_RATIO_1_1;

    // Viewfinder grip size.
    private int mViewFinderGripSize = 0;

    // Alpha definitions.
    private static final float SHOWN_ALPHA = 1.0f;
    private static final float HIDDEN_ALPHA = 0.0f;

    // Resources.
    private ViewFinderAnywhereApplication.CustomizableResourceContainer mCustomResContainer = null;

    // Grip preferences.
    private ViewFinderAnywhereConstants.ViewFinderGripSize mGripSizeSetting = null;
    private ViewFinderGripPosition mGripPositionSetting = null;

    // Hidden window position.
    private static final int WINDOW_HIDDEN_POS_X = -5000;

    // CONSTRUCTOR.
    public OverlayViewFinderRootView(final Context context) {
        this(context, null);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR");
        // NOP.
    }

    // CONSTRUCTOR.
    public OverlayViewFinderRootView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR");
        // NOP.
    }

    // CONSTRUCTOR.
    public OverlayViewFinderRootView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR");
        // NOP.
    }

    /**
     * Initialize all of configurations.
     */
    public void initialize(float aspectRatioWH) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : E");

        mViewFinderAspectWH = aspectRatioWH;

        // Cache instance references.
        cacheInstances();

        // Load setting.
        loadPreferences();

        // Window related.
        createWindowParameters();

        // Update UI.
        updateTotalUserInterface();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "initialize() : X");
    }

    private void cacheInstances() {
        mCustomResContainer = ViewFinderAnywhereApplication.getCustomResContainer();
        Resources res = getContext().getResources();

        // Root.
        mRootView = (RelativeLayout) findViewById(R.id.root);
        // Slider.
        mOverlaySlider = (LinearLayout) findViewById(R.id.overlay_slider);
        // Viewfinder related.
        mViewFinderContainer = (FrameLayout) findViewById(R.id.viewfinder_container);
        mViewFinder = (TextureView) findViewById(R.id.viewfinder);
        mViewFinder.setSurfaceTextureListener(mSurfaceTextureListenerImpl);
        mViewFinder.setAlpha(HIDDEN_ALPHA);
        mViewFinderBackground = (ImageView) findViewById(R.id.viewfinder_background);
        mViewFinderBackground.setBackgroundColor(mCustomResContainer.colorVfBackground);
        // Scan indicator.
        mScanIndicatorContainer = (ViewGroup) findViewById(R.id.scan_indicator_container);
        mScanIndicatorContainer.setVisibility(INVISIBLE);
        // Shutter feedback.
        mShutterFeedback = findViewById(R.id.shutter_feedback);
        mShutterFeedback.setVisibility(View.INVISIBLE);

        // UI-Plug-IN.
        // Viewfinder grip.
        mViewFinderGripContainer = (ViewGroup) findViewById(R.id.viewfinder_grip_container);
        mViewFinderGrip = (ImageView) findViewById(R.id.viewfinder_grip);
        mViewFinderGripLabel = (ImageView) findViewById(R.id.viewfinder_grip_label);
        final Paint paint = new Paint();
        paint.setTextSize(res.getDimensionPixelSize(R.dimen.viewfinder_grip_label_font_size));
        paint.setColor(mCustomResContainer.colorGripLabel);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.createFromAsset(
                getContext().getAssets(),
                ViewFinderAnywhereConstants.FONT_FILENAME_CODA));
        final Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        final int textHeight = (int) Math.ceil(Math.abs(fontMetrics.ascent)
                + Math.abs(fontMetrics.descent)
                + Math.abs(fontMetrics.leading));
        final String textLabel = res.getString(R.string.viewfinder_grip_label);
        final int textWidth = (int) Math.ceil(paint.measureText(textLabel));
        mViewFinderGripLabelLandscapeBitmap = Bitmap.createBitmap(
                textWidth,
                textHeight,
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mViewFinderGripLabelLandscapeBitmap);
        c.drawText(textLabel, 0, Math.abs(fontMetrics.ascent), paint);
        Matrix matrix = new Matrix();
        matrix.postRotate(-90.0f);
        mViewFinderGripLabelPortraitBitmap = Bitmap.createBitmap(
                mViewFinderGripLabelLandscapeBitmap,
                0,
                0,
                mViewFinderGripLabelLandscapeBitmap.getWidth(),
                mViewFinderGripLabelLandscapeBitmap.getHeight(),
                matrix,
                true);
        // Viewfinder frame.
        mViewFinderFrame = (ImageView) findViewById(R.id.viewfinder_frame);
        // Total cover.
        mTotalBackground = (ImageView) findViewById(R.id.total_background);
        mTotalForeground = (ImageView) findViewById(R.id.total_foreground);
    }

    private void loadPreferences() {
        SharedPreferences sp = ViewFinderAnywhereApplication.getGlobalSharedPreferences();

        // Size.
        String size = sp.getString(ViewFinderAnywhereConstants.KEY_VIEW_FINDER_SIZE, null);
        if (size == null) {
            // Unexpected or not initialized yet. Use default.
            mViewFinderScaleRatioAgainstToScreen
                    = ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_LARGE;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_SIZE_XLARGE.equals(size)) {
            mViewFinderScaleRatioAgainstToScreen
                    = ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_XLARGE;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_SIZE_LARGE.equals(size)) {
            mViewFinderScaleRatioAgainstToScreen
                    = ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_LARGE;
        } else if (ViewFinderAnywhereConstants.VAL_VIEW_FINDER_SIZE_SMALL.equals(size)) {
            mViewFinderScaleRatioAgainstToScreen
                    = ViewFinderAnywhereConstants.VIEW_FINDER_SCALE_SMALL;
        } else {
            // NOP. Unexpected.
            throw new IllegalArgumentException("Unexpected Size.");
        }

        // Residence flag.
        mIsResidence = sp.getBoolean(
                ViewFinderAnywhereConstants.KEY_OVERLAY_TRIGGER_FROM_SCREEN_EDGE,
                false);

        // Grip.
        mViewFinderGripSize = getContext().getResources().getDimensionPixelSize(
                R.dimen.viewfinder_grip_size);
        // Grip size.
        String gripSizeValue = sp.getString(ViewFinderGripSize.KEY, null);
        if (gripSizeValue != null) {
            mGripSizeSetting = ViewFinderGripSize.valueOf(gripSizeValue);
        } else {
            mGripSizeSetting = ViewFinderGripSize.getDefault();
        }
        // Grip position.
        String positionSizeValue = sp.getString(ViewFinderGripPosition.KEY, null);
        if (gripSizeValue != null) {
            mGripPositionSetting = ViewFinderGripPosition.valueOf(positionSizeValue);
        } else {
            mGripPositionSetting = ViewFinderGripPosition.getDefault();
        }
    }

    private void createWindowParameters() {
        mWindowManager = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);

        mWindowLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
    }

    /**
     * Release all resources.
     */
    public void release() {
        releaseWindowPositionCorrector();

        if (mInteractionEngine != null) {
            mInteractionEngine.setInteractionCallback(null);
            mInteractionEngine.release();
            mInteractionEngine = null;
        }

        if (mViewFinder != null) {
            mViewFinder.setSurfaceTextureListener(null);
            mViewFinder = null;
        }
        if (mRootView != null) {
            mRootView.setOnTouchListener(null);
            mRootView = null;
        }
        mViewFinderBackground = null;
        mViewFinderContainer = null;
        mShutterFeedback = null;

        mScanIndicatorContainer = null;

        // UI Plug-IN.
        mViewFinderGripContainer = null;
        if (mViewFinderGrip != null) {
            mViewFinderGrip.setImageDrawable(null);
            mViewFinderGrip = null;
        }
        if (mViewFinderGripLabel != null) {
            mViewFinderGripLabel.setImageBitmap(null);
            mViewFinderGripLabel = null;
        }
        if (mViewFinderFrame != null) {
            mViewFinderFrame.setImageDrawable(null);
            mViewFinderFrame = null;
        }
        if (mTotalBackground != null) {
            mTotalBackground.setImageDrawable(null);
            mTotalBackground = null;
        }
        if (mTotalForeground != null) {
            mTotalForeground.setImageDrawable(null);
            mTotalForeground = null;
        }

        mOverlaySlider = null;

        mWindowManager = null;
        mWindowLayoutParams = null;
    }

    private void releaseWindowPositionCorrector() {
        if (mWindowPositionCorrectionTask != null) {
            getHandler().removeCallbacks(mWindowPositionCorrectionTask);
            mWindowPositionCorrectionTask = null;
        }
    }

    /**
     * Add this view to WindowManager layer.
     */
    public void addToOverlayWindow() {
        // Window parameters.
        updateWindowParams(true);

        // Add to WindowManager.
        WindowManager winMng = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);
        winMng.addView(this, mWindowLayoutParams);
    }

    @SuppressLint("RtlHardcoded")
    private void updateWindowParams(boolean isInitialSetup) {
        releaseWindowPositionCorrector();
        final int edgeClearance = getContext().getResources().getDimensionPixelSize(
                R.dimen.viewfinder_edge_clearance);

        switch (mOrientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
            {
                mWindowLayoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;

                // Window offset on enabled.
                mWindowLayoutParams.x = edgeClearance;
                mWindowLayoutParams.y = 0;

                // Position limit.
                mWindowDisabledPosit.set(
                        mWindowLayoutParams.x,
                        mWindowLayoutParams.y - mViewFinderHeight);
                mWindowEnabledPosit.set(
                        mWindowLayoutParams.x,
                        mWindowLayoutParams.y);

                // Cache.
                int winX = mDisplayLongLineLength - mViewFinderWidth - mWindowLayoutParams.x;
                int winY = mDisplayShortLineLength - mViewFinderHeight - mViewFinderGripSize
                        - mWindowLayoutParams.y;
                int winW = mViewFinderWidth;
                int winH = mViewFinderHeight + mViewFinderGripSize;
                ViewFinderAnywhereApplication.TotalWindowConfiguration
                        .OverlayViewFinderWindowConfig.update(winX, winY, winW, winH);
                if (Log.IS_DEBUG) Log.logDebug(TAG,
                        "updateWindowParams() : [X=" + winX + "] [Y=" + winY + "] [W="
                         + winW + "] [H=" + winH +"]");
                break;
            }

            case Configuration.ORIENTATION_PORTRAIT:
            {
                mWindowLayoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;

                // Window offset on enabled.
                mWindowLayoutParams.x = 0;
                mWindowLayoutParams.y = edgeClearance;

                // Position limit.
                mWindowDisabledPosit.set(
                        mWindowLayoutParams.x - mViewFinderWidth,
                        mWindowLayoutParams.y);
                mWindowEnabledPosit.set(
                        mWindowLayoutParams.x,
                        mWindowLayoutParams.y);

                // Cache.
                int winX = mDisplayShortLineLength - mViewFinderWidth - mViewFinderGripSize
                        - mWindowLayoutParams.x;
                int winY = mDisplayLongLineLength - mViewFinderHeight - mWindowLayoutParams.y;
                int winW = mViewFinderWidth + mViewFinderGripSize;
                int winH = mViewFinderHeight;
                ViewFinderAnywhereApplication.TotalWindowConfiguration
                        .OverlayViewFinderWindowConfig.update(winX, winY, winW, winH);
                if (Log.IS_DEBUG) Log.logDebug(TAG,
                        "updateWindowParams() : [X=" + winX + "] [Y=" + winY + "] [W="
                         + winW + "] [H=" + winH +"]");
                break;
            }
            default:
                // Unexpected orientation.
                throw new IllegalStateException("Unexpected orientation.");
        }

        // Check active.
        if (mIsResidence && !isInitialSetup
                 && !OverlayViewFinderController.getInstance().getCurrentState().isActive()) {
            mWindowLayoutParams.x = mWindowDisabledPosit.x;
            mWindowLayoutParams.y = mWindowDisabledPosit.y;
        }

        if (isAttachedToWindow()) {
            mWindowManager.updateViewLayout(this, mWindowLayoutParams);
        }
    }

    /**
     * Remove this view from WindowManager layer.
     */
    public void removeFromOverlayWindow() {
        // Remove from to WindowManager.
        WindowManager winMng = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);
        winMng.removeView(this);

        // Release drawing cache.
        getHandler().post(new ReleaseDrawingResourceTask());
    }

    private class ReleaseDrawingResourceTask implements Runnable {
        @Override
        public void run() {
            if (mViewFinderGripLabelLandscapeBitmap != null
                    && !mViewFinderGripLabelLandscapeBitmap.isRecycled()) {
                mViewFinderGripLabelLandscapeBitmap.recycle();
                mViewFinderGripLabelLandscapeBitmap = null;
            }
            if (mViewFinderGripLabelPortraitBitmap != null
                    && !mViewFinderGripLabelPortraitBitmap.isRecycled()) {
                mViewFinderGripLabelPortraitBitmap.recycle();
                mViewFinderGripLabelPortraitBitmap = null;
            }
        }
    }

    private void updateTotalUserInterface() {
        // Screen configuration.
        calculateScreenConfiguration();
        // View finder size.
        calculateViewFinderSize();
        // Window layout.
        updateWindowParams(false);
        // Update layout.
        updateLayoutParams();
    }

    private void calculateScreenConfiguration() {
        // Get display size.
        Display display = mWindowManager.getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);
        final int width = screenSize.x;
        final int height = screenSize.y;
        mDisplayLongLineLength = Math.max(width, height);
        mDisplayShortLineLength = Math.min(width, height);

        // Get display orientation.
        if (height < width) {
            mOrientation = Configuration.ORIENTATION_LANDSCAPE;
        } else {
            mOrientation = Configuration.ORIENTATION_PORTRAIT;
        }
    }

    private void calculateViewFinderSize() {
        // Define view finder size.
        switch (mOrientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                mViewFinderWidth = (int)
                        (mDisplayLongLineLength * mViewFinderScaleRatioAgainstToScreen);
                mViewFinderHeight = (int)
                        (mViewFinderWidth / mViewFinderAspectWH);
                break;

            case Configuration.ORIENTATION_PORTRAIT:
                mViewFinderHeight = (int)
                        (mDisplayLongLineLength * mViewFinderScaleRatioAgainstToScreen);
                mViewFinderWidth = (int)
                        (mViewFinderHeight / mViewFinderAspectWH);
                break;

            default:
                // Unexpected orientation.
                throw new IllegalStateException("Unexpected orientation.");
        }
    }

    private void updateLayoutParams() {
        // Viewfinder size.
        if (mViewFinderContainer != null) {
            ViewGroup.LayoutParams params = mViewFinderContainer.getLayoutParams();
            params.width = mViewFinderWidth;
            params.height = mViewFinderHeight;
            mViewFinderContainer.setLayoutParams(params);
        }
        int totalWidth;
        int totalHeight;
        switch (mOrientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                totalWidth = mViewFinderWidth;
                totalHeight = mViewFinderHeight + mViewFinderGripSize;
                break;

            case Configuration.ORIENTATION_PORTRAIT:
                totalWidth = mViewFinderWidth + mViewFinderGripSize;
                totalHeight = mViewFinderHeight;
                break;

            default:
                // Unexpected orientation.
                throw new IllegalStateException("Unexpected orientation.");
        }
        if (mTotalBackground != null) {
            ViewGroup.LayoutParams params = mTotalBackground.getLayoutParams();
            params.width = totalWidth;
            params.height = totalHeight;
            mTotalBackground.setLayoutParams(params);

            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    mTotalBackground.setImageDrawable(mCustomResContainer.drawableTotalBackLand);
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    mTotalBackground.setImageDrawable(mCustomResContainer.drawableTotalBackPort);
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
        }
        if (mTotalForeground != null) {
            ViewGroup.LayoutParams params = mTotalForeground.getLayoutParams();
            params.width = totalWidth;
            params.height = totalHeight;
            mTotalForeground.setLayoutParams(params);

            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    mTotalForeground.setImageDrawable(mCustomResContainer.drawableTotalForeLand);
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    mTotalForeground.setImageDrawable(mCustomResContainer.drawableTotalForePort);
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
        }

        // Viewfinder slider.
        if (mOverlaySlider != null) {
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    mOverlaySlider.setOrientation(LinearLayout.VERTICAL);
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    mOverlaySlider.setOrientation(LinearLayout.HORIZONTAL);
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
        }

        // Grip container.
        if (mViewFinderGripContainer != null) {
            ViewGroup.LayoutParams params = mViewFinderGripContainer.getLayoutParams();
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    params.width = mViewFinderWidth;
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.height = mViewFinderHeight;
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
            mViewFinderGripContainer.setLayoutParams(params);
        }

        // Viewfinder grip.
        if (mViewFinderGrip != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)
                    mViewFinderGrip.getLayoutParams();
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    params.width = (int) (mViewFinderWidth * mGripSizeSetting.getScaleRate());
                    params.height = mViewFinderGripSize;
                    mViewFinderGrip.setImageDrawable(mCustomResContainer.drawableVfGripLand);
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    params.width = mViewFinderGripSize;
                    params.height = (int) (mViewFinderHeight * mGripSizeSetting.getScaleRate());
                    mViewFinderGrip.setImageDrawable(mCustomResContainer.drawableVfGripPort);
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
            params.gravity = mGripPositionSetting.getLayoutGravity();
            mViewFinderGrip.setLayoutParams(params);
        }
        if (mViewFinderGripLabel != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)
                    mViewFinderGripLabel.getLayoutParams();
            final int horizontalPadding = getResources().getDimensionPixelSize(
                    R.dimen.viewfinder_grip_label_horizontal_padding);
            final int verticalPadding = getResources().getDimensionPixelSize(
                    R.dimen.viewfinder_grip_label_vertical_padding);
            int visibility = View.VISIBLE;
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    mViewFinderGripLabel.setImageBitmap(mViewFinderGripLabelLandscapeBitmap);
                    mViewFinderGripLabel.setPadding(
                            horizontalPadding,
                            verticalPadding,
                            horizontalPadding,
                            0);
                    params.gravity
                            = mGripPositionSetting.getLayoutGravity() | Gravity.CENTER_VERTICAL;
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    params.height = mViewFinderGripSize;
                    if (mViewFinderWidth * mGripSizeSetting.getScaleRate()
                            < mViewFinderGripLabelLandscapeBitmap.getWidth()) {
                        visibility = View.INVISIBLE;
                    }
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    mViewFinderGripLabel.setImageBitmap(mViewFinderGripLabelPortraitBitmap);
                    mViewFinderGripLabel.setPadding(
                            verticalPadding,
                            horizontalPadding,
                            0,
                            horizontalPadding);
                    params.gravity
                            = Gravity.CENTER_HORIZONTAL | mGripPositionSetting.getLayoutGravity();
                    params.width = mViewFinderGripSize;
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    if (mViewFinderHeight * mGripSizeSetting.getScaleRate()
                            < mViewFinderGripLabelPortraitBitmap.getHeight()) {
                        visibility = View.INVISIBLE;
                    }
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
            mViewFinderGripLabel.setVisibility(visibility);
            mViewFinderGripLabel.setLayoutParams(params);
        }

        // Viewfinder frame.
        if (mViewFinderFrame != null) {
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    mViewFinderFrame.setImageDrawable(mCustomResContainer.drawableVfFrameLand);
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    mViewFinderFrame.setImageDrawable(mCustomResContainer.drawableVfFramePort);
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
        }
    }

    private final SurfaceTextureListenerImpl mSurfaceTextureListenerImpl
            = new SurfaceTextureListenerImpl();
    private class SurfaceTextureListenerImpl implements TextureView.SurfaceTextureListener {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSurfaceTextureAvailable() : [W=" + width + "] [H=" + height +"]");

            checkViewFinderAspect(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSurfaceTextureSizeChanged() : [W=" + width + "] [H=" + height +"]");

            checkViewFinderAspect(width, height);
        }

        @Override
        public String toString() {
            return super.toString();
        }

        private void checkViewFinderAspect(int width, int height) {
            if (width == mViewFinderWidth && height == mViewFinderHeight) {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "checkViewFinderAspect() : Resize DONE");
                // Resize done.

                // Set touch interceptor.
                mInteractionEngine = new InteractionEngine(
                        mRootView.getContext(),
                        mRootView,
                        0,
                        0,//ViewConfiguration.get(getContext()).getScaledTouchSlop(),
                        ViewFinderAnywhereApplication.getUiThreadHandler());
                mInteractionEngine.setInteractionCallback(mInteractionCallbackImpl);
                mRootView.setOnTouchListener(mOnTouchListenerImpl);

                // Notify to device.
                OverlayViewFinderController.getInstance().getCurrentState().onSurfaceReady();
            } else {
                if (Log.IS_DEBUG) Log.logDebug(TAG, "checkViewFinderAspect() : Now on resizing...");
                // NOP. Now on resizing.
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceTextureDestroyed()");
            // NOP.
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSurfaceTextureUpdated()");

            if (mIsResumed && mViewFinder.getAlpha() == HIDDEN_ALPHA) {
                mShowSurfaceTask.reset();
                mRootView.getHandler().post(mShowSurfaceTask);
            }
        }
    }

    private void clearSurface() {
        mRootView.getHandler().removeCallbacks(mShowSurfaceTask);
        mHideSurfaceTask.reset();
        mRootView.getHandler().post(mHideSurfaceTask);
    }

    private abstract class SurfaceVisibilityControlTask implements Runnable {
        // Log tag.
        private final String TAG = SurfaceVisibilityControlTask.class.getSimpleName();

        // Actual view finder alpha.
        private float mActualAlpha = SHOWN_ALPHA;

        // Alpha stride.
        private static final float ALPHA_DELTA = 0.2f;

        /**
         * Reset state.
         */
        public void reset() {
            mActualAlpha = mViewFinder.getAlpha();
        }

        abstract protected boolean isFadeIn();

        @Override
        public void run() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "run()");

            if (mRootView == null || mViewFinder == null) {
                // Already released.
                return;
            }

            boolean isNextTaskRequired = true;

            if (isFadeIn()) {
                mActualAlpha += ALPHA_DELTA;
                if (SHOWN_ALPHA < mActualAlpha) {
                    mActualAlpha = SHOWN_ALPHA;
                    isNextTaskRequired = false;
                }
            } else {
                mActualAlpha -= ALPHA_DELTA;
                if (mActualAlpha < HIDDEN_ALPHA) {
                    mActualAlpha = HIDDEN_ALPHA;
                    isNextTaskRequired = false;
                }
            }

            mViewFinder.setAlpha(mActualAlpha);

            if (isNextTaskRequired) {
                // NOTICE:
                //   On Android N, invalidate() is called in setAlpha().
                //   So, this task takes 1 frame V-Sync millis (about 16[ms])
                //   Not to delay fade in/out, post next control task immediately.
                mRootView.getHandler().post(this);
            }
        }
    }

    private final ShowSurfaceTask mShowSurfaceTask = new ShowSurfaceTask();
    private class ShowSurfaceTask extends SurfaceVisibilityControlTask {
        @Override
        protected boolean isFadeIn() {
            return true;
        }
    }


    private final HideSurfaceTask mHideSurfaceTask = new HideSurfaceTask();
    private class HideSurfaceTask extends SurfaceVisibilityControlTask {
        @Override
        protected boolean isFadeIn() {
            return false;
        }
    }

    /**
     * Get view finder target surface.
     *
     * @return Finder TextureView.
     */
    public TextureView getViewFinderSurface() {
        return mViewFinder;
    }

    private final OnTouchListenerImpl mOnTouchListenerImpl = new OnTouchListenerImpl();
    private class OnTouchListenerImpl implements OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // Use absolute position, because window position change affects view motion event.
            event.setLocation(event.getRawX(), event.getRawY());

            mInteractionEngine.onTouchEvent(event);
            return true;
        }
    }

    private final InteractionEngine.InteractionCallback mInteractionCallbackImpl
            = new InteractionCallbackImpl();
    private class InteractionCallbackImpl implements InteractionEngine.InteractionCallback {
        @Override
        public void onSingleTouched(Point point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleTouched() : [X=" + point.x + "] [Y=" + point.y + "]");

            // Pre-open.
            if (!mIsResumed) {
                OverlayViewFinderController.getInstance().getCurrentState().onPreOpenRequested();
            }

            // Request scan.
            OverlayViewFinderController.getInstance().getCurrentState().requestScan();

            // Stop animation.
            if (mWindowPositionCorrectionTask != null) {
                getHandler().removeCallbacks(mWindowPositionCorrectionTask);
                mWindowPositionCorrectionTask = null;
            }

            // Cache current position.
            mLastWindowPosition.x = mWindowLayoutParams.x;
            mLastWindowPosition.y = mWindowLayoutParams.y;
        }

        @Override
        public void onSingleMoved(Point currentPoint, Point lastPoint, Point downPoint) {
//            if (Log.IS_DEBUG) Log.logDebug(TAG,
//                    "onSingleMoved() : [X=" + currentPoint.x + "] [Y=" + currentPoint.y + "]");

            final int diffX = currentPoint.x - downPoint.x;
            final int diffY = currentPoint.y - downPoint.y;

            // Check moving.
            final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (touchSlop < Math.abs(diffX) || touchSlop < Math.abs(diffY)) {
                OverlayViewFinderController.getInstance().getCurrentState().requestCancelScan();
            }

            int newX = mWindowLayoutParams.x;
            int newY = mWindowLayoutParams.y;
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
//                    newX = mLastWindowPosition.x - diffX;
                    newY = mLastWindowPosition.y - diffY;

                    // Limit check.
                    if (newY < mWindowDisabledPosit.y) {
                        newY = mWindowDisabledPosit.y;
                    } else if (mWindowEnabledPosit.y < newY) {
                        newY = mWindowEnabledPosit.y;
                    }
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    newX = mLastWindowPosition.x - diffX;
//                    newY = mLastWindowPosition.y - diffY;

                    // Limit check.
                    if (newX < mWindowDisabledPosit.x) {
                        newX = mWindowDisabledPosit.x;
                    } else if (mWindowEnabledPosit.x < newX) {
                        newX = mWindowEnabledPosit.x;
                    }
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }

            if (mWindowLayoutParams.x != newX || mWindowLayoutParams.y != newY) {
                mWindowLayoutParams.x = newX;
                mWindowLayoutParams.y = newY;
                mWindowManager.updateViewLayout(OverlayViewFinderRootView.this, mWindowLayoutParams);
            }
        }

        @Override
        public void onSingleStopped(Point currentPoint, Point lastPoint, Point downPoint) {
//            if (Log.IS_DEBUG) Log.logDebug(TAG,
//                    "onSingleStopped() : [X=" + currentPoint.x + "] [Y=" + currentPoint.y + "]");
            // NOP.
        }

        @Override
        public void onSingleReleased(Point point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleReleased() : [X=" + point.x + "] [Y=" + point.y + "]");

            // Request still capture.
            OverlayViewFinderController.getInstance().getCurrentState().requestStillCapture();

            // Decide correction target position.
            final int diffToEnable;
            final int diffToDisable;
            final Point mTarget;
            switch (mOrientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    diffToEnable = Math.abs(mWindowEnabledPosit.y - mWindowLayoutParams.y);
                    diffToDisable = Math.abs(mWindowDisabledPosit.y - mWindowLayoutParams.y);
                    break;

                case Configuration.ORIENTATION_PORTRAIT:
                    diffToEnable = Math.abs(mWindowEnabledPosit.x - mWindowLayoutParams.x);
                    diffToDisable = Math.abs(mWindowDisabledPosit.x - mWindowLayoutParams.x);
                    break;

                default:
                    // Unexpected orientation.
                    throw new IllegalStateException("Unexpected orientation.");
            }
            if (diffToEnable < diffToDisable) {
                mTarget = mWindowEnabledPosit;

                // Resume controller.
                if (!mIsResumed) {
                    mIsResumed = true;
                    OverlayViewFinderController.getInstance().resume();
                    if (mViewFinder.isAvailable()) {
                        OverlayViewFinderController.getInstance().getCurrentState()
                                .onSurfaceReady();
                    }
                }
            } else {
                mTarget = mWindowDisabledPosit;

                // Pause controller.
                if (mIsResumed) {
                    mIsResumed = false;

                    // Hide surface.
                    clearSurface();

                    OverlayViewFinderController.getInstance().pause();
                }
            }

            if (!mIsResumed) {
                OverlayViewFinderController.getInstance().getCurrentState().onPreOpenCanceled();
            }

            // Correct position start.
            mWindowPositionCorrectionTask = new WindowPositionCorrectionTask(mTarget);
            getHandler().postDelayed(mWindowPositionCorrectionTask, WINDOW_ANIMATION_INTERVAL);
        }

        @Override
        public void onSingleCanceled() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSingleCanceled()");
            // NOP.
        }

        @Override
        public void onDoubleTouched(Point point0, Point point1) {
            // NOP.
        }

        @Override
        public void onDoubleMoved(Point point0, Point point1) {
            // NOP.
        }

        @Override
        public void onDoubleScaled(float currentLength, float previousLength, float originalLength) {
            // NOP.
        }

        @Override
        public void onDoubleRotated(float degreeVsOrigin, float degreeVsLast) {
            // NOP.
        }

        @Override
        public void onSingleReleasedInDouble(Point release, Point remain) {
            // NOP.
        }

        @Override
        public void onDoubleCanceled() {
            // NOP.
        }

        @Override
        public void onOverTripleCanceled() {
            // NOP.
        }

        @Override
        public void onFling(MotionEvent event1, MotionEvent event2, float velocX, float velocY) {
//            if (Log.IS_DEBUG) Log.logDebug(TAG,
//                    "onFling() : [VelocX=" + velocX + "] [VelocY=" + velocY + "]");

            // Finish.
//            switch (mOrientation) {
//                case Configuration.ORIENTATION_LANDSCAPE:
//                    if (velocY < -1000.0f) {
//                        OverlayViewFinderController.getInstance().getCurrentState()
//                                .onFinishRequested();
//                    }
//                    break;
//
//                case Configuration.ORIENTATION_PORTRAIT:
//                    if (1000.0f < velocX) {
//                        OverlayViewFinderController.getInstance().getCurrentState()
//                                .onFinishRequested();
//                    }
//                    break;
//
//                default:
//                    // Unexpected orientation.
//                    throw new IllegalStateException("Unexpected orientation.");
//            }
        }

        @Override
        public void onLongPress(MotionEvent event) {
            // NOP.
        }

        @Override
        public void onShowPress(MotionEvent event) {
            // NOP.
        }

        @Override
        public void onSingleTapUp(MotionEvent event) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onSingleTapUp()");

            // Capture.
//            OverlayViewFinderController.getInstance().getCurrentState().requestStillCapture();
        }
    }

    /**
     * Stop immediately without animation.
     */
    public void forceStop() {
        // Pause controller.
        if (mIsResumed) {
            // Kill animation.
            getHandler().removeCallbacks(mWindowPositionCorrectionTask);

            // Hide surface.
            clearSurface();

            // Pause controller.
            OverlayViewFinderController.getInstance().pause();

            // Fix position.
            mWindowLayoutParams.x = mWindowDisabledPosit.x;
            mWindowLayoutParams.y = mWindowDisabledPosit.y;
            mWindowManager.updateViewLayout(this, mWindowLayoutParams);

            mIsResumed = false;
        }
    }

    /**
     * Overlay window is shown or not.
     *
     * @return Overlay window is shown or not
     */
    public boolean isOverlayShown() {
        return mWindowLayoutParams.x != WINDOW_HIDDEN_POS_X;
    }

    /**
     * Show overlay window.
     */
    public void show() {
        mWindowLayoutParams.x = mWindowDisabledPosit.x;
        mWindowLayoutParams.y = mWindowDisabledPosit.y;
        mWindowManager.updateViewLayout(this, mWindowLayoutParams);
    }

    /**
     * Hide overlay window.
     */
    public void hide() {
        mWindowLayoutParams.x = WINDOW_HIDDEN_POS_X;
        mWindowLayoutParams.y = mWindowDisabledPosit.y;
        mWindowManager.updateViewLayout(this, mWindowLayoutParams);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        switch (keyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_FOCUS:
                if (keyEvent.getRepeatCount() != 0) {
                    // Do not handle hold press.
                    return true;
                }

                switch (keyEvent.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS DOWN]");
                        break;

                    case KeyEvent.ACTION_UP:
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS UP]");
                        break;

                    default:
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS NO ACT]");
                        break;
                }
                return true;

            default:
                // Un-used key code.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [UNUSED KEY]");
                return false;
        }
    }



    @Override
    public void onFinishInflate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onFinishInflate()");
        super.onFinishInflate();
        // NOP.
    }

    @Override
    public void onAttachedToWindow() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onAttachedToWindow()");
        super.onAttachedToWindow();
        // NOP.
    }

    @Override
    public void onDetachedFromWindow() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDetachedFromWindow()");
        super.onDetachedFromWindow();
        // NOP.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "onConfigurationChanged() : [Config=" + newConfig.toString());
        super.onConfigurationChanged(newConfig);

        // Update UI.
        updateTotalUserInterface();

        // Force stop.
        forceStop();
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
//        if (Log.IS_DEBUG) Log.logDebug(TAG,
//                "onLayout() : [Changed=" + changed + "] [Rect="
//                 + left + ", " + top + ", " + right + ", " + bottom + "]");
        super.onLayout(changed, left, top, right, bottom);
        // NOP.
    }

    @Override
    public void onSizeChanged(int curW, int curH, int nxtW, int nxtH) {
//        if (Log.IS_DEBUG) Log.logDebug(TAG,
//                "onSizeChanged() : [CUR=" + curW + "x" + curH + "] [NXT=" +  nxtW + "x" + nxtH + "]");
        super.onSizeChanged(curW, curH, nxtW, nxtH);
        // NOP.
    }



    /**
     * Visual feedback trigger.
     */
    @SuppressWarnings("WeakerAccess") // False positive.
    public interface VisualFeedbackTrigger {
        void onScanStarted();
        void onScanDone(boolean isSuccess);
        void onShutterDone();
        void clear();
    }

    /**
     * Get visual feedback trigger interface.
     *
     * @return Visual feedback trigger accessor.
     */
    public VisualFeedbackTrigger getVisualFeedbackTrigger() {
        return mVisualFeedbackTriggerImpl;
    }

    private final VisualFeedbackTriggerImpl mVisualFeedbackTriggerImpl
            = new VisualFeedbackTriggerImpl();
    private class VisualFeedbackTriggerImpl implements VisualFeedbackTrigger {
        private final int SHUTTER_FEEDBACK_DURATION_MILLIS = 100;

        @Override
        public void onScanStarted() {
            if (mScanIndicatorContainer != null) {
                updateIndicatorColor(mCustomResContainer.colorScanOnGoing);
                mScanIndicatorContainer.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onScanDone(boolean isSuccess) {
            if (mScanIndicatorContainer != null) {
                int color;
                if (isSuccess) {
                    color = mCustomResContainer.colorScanSuccess;
                } else {
                    color = mCustomResContainer.colorScanFailure;
                }
                updateIndicatorColor(color);
                mScanIndicatorContainer.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onShutterDone() {
            mShutterFeedback.setVisibility(View.VISIBLE);
            OverlayViewFinderRootView.this.getHandler().postDelayed(
                    mRecoverShutterFeedbackTask,
                    SHUTTER_FEEDBACK_DURATION_MILLIS);
        }

        private final RecoverShutterFeedbackTask mRecoverShutterFeedbackTask
                = new RecoverShutterFeedbackTask();
        private class RecoverShutterFeedbackTask implements Runnable {
            @Override
            public void run() {
                if (mShutterFeedback != null) {
                    mShutterFeedback.setVisibility(View.INVISIBLE);
                }
            }
        }

        @Override
        public void clear() {
            if (mScanIndicatorContainer != null) {
                mScanIndicatorContainer.setVisibility(View.INVISIBLE);
            }
        }

        private void updateIndicatorColor(int color) {
            for (int i = 0; i < mScanIndicatorContainer.getChildCount(); ++i) {
                View element = mScanIndicatorContainer.getChildAt(i);
                element.setBackgroundColor(color);
            }
            mScanIndicatorContainer.invalidate();
        }
    }
}
