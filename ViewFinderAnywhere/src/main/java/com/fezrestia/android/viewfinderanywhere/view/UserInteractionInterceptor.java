package com.fezrestia.android.viewfinderanywhere.view;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import com.fezrestia.android.lib.interaction.InteractionEngine;
import com.fezrestia.android.util.log.Log;
import com.fezrestia.android.viewfinderanywhere.R;
import com.fezrestia.android.viewfinderanywhere.ViewFinderAnywhereApplication;
import com.fezrestia.android.viewfinderanywhere.control.OverlayViewFinderController;

public class UserInteractionInterceptor extends RelativeLayout {
    // Log tag.
    private static final String TAG = UserInteractionInterceptor.class.getSimpleName();

    // Display coordinates.
    private int mDisplayLongLineLength = 0;
    private int mDisplayShortLineLength = 0;

    // Overlay window orientation.
    private int mOrientation = Configuration.ORIENTATION_UNDEFINED;

    // View finder target size.
    private int mInterceptorWidth = 0;
    private int mInterceptorHeight = 0;

    // Window.
    private WindowManager mWindowManager = null;
    private WindowManager.LayoutParams mWindowLayoutParams = null;

    // Touch interceptor.
    private View mInterceptor = null;

    // Touch interaction engine.
    private InteractionEngine mInteractionEngine = null;

    // CONSTRUCTOR.
    public UserInteractionInterceptor(final Context context) {
        this(context, null);
        // NOP.
    }

    // CONSTRUCTOR.
    public UserInteractionInterceptor(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        // NOP.
    }

    // CONSTRUCTOR.
    public UserInteractionInterceptor(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : E");

        // Window related.
        createWindowParameters();

        // Update UI.
        updateTotalUserInterface();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "CONSTRUCTOR : X");
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
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
    }

    /**
     * Release all resources.
     */
    public void release() {
        if (mInteractionEngine != null) {
            mInteractionEngine.setInteractionCallback(null);
            mInteractionEngine.release();
            mInteractionEngine = null;
        }
        if (mInterceptor != null) {
            mInterceptor.setOnTouchListener(null);
            mInterceptor = null;
        }
        mWindowManager = null;
        mWindowLayoutParams = null;
    }

    /**
     * Add this view to WindowManager layer.
     */
    public void addToOverlayWindow() {
        // Window parameters.
        updateWindowParams();

        // Add to WindowManager.
        WindowManager winMng = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);
        winMng.addView(this, mWindowLayoutParams);
    }

    private void updateWindowParams() {
        switch (mOrientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                mWindowLayoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
                break;

            case Configuration.ORIENTATION_PORTRAIT:
                mWindowLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                break;

            default:
                // Unexpected orientation.
                throw new IllegalStateException("Unexpected orientation.");
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
        mWindowManager.removeView(this);
    }

    private void updateTotalUserInterface() {
        // Screen configuration.
        calculateScreenConfiguration();
        // View finder size.
        calculateViewFinderSize();
        // Window layout.
        updateWindowParams();
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
                mInterceptorWidth = 1;
                mInterceptorHeight = WindowManager.LayoutParams.MATCH_PARENT;
                break;

            case Configuration.ORIENTATION_PORTRAIT:
                mInterceptorWidth = WindowManager.LayoutParams.MATCH_PARENT;
                mInterceptorHeight = 1;
                break;

            default:
                // Unexpected orientation.
                throw new IllegalStateException("Unexpected orientation.");
        }
    }

    @Override
    public void onFinishInflate() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onFinishInflate() : E");
        super.onFinishInflate();

        // Cache instance references.
        mInterceptor = findViewById(R.id.interceptor);
        // Interaction engine.
        mInteractionEngine = new InteractionEngine(
                mInterceptor.getContext(),
                mInterceptor,
                0,
                0, ViewFinderAnywhereApplication.getUiThreadHandler());
        mInteractionEngine.setInteractionCallback(mInteractionCallbackImpl);
        mInterceptor.setOnTouchListener(mOnTouchListenerImpl);

        mInterceptor.setBackgroundColor(android.graphics.Color.RED); // for DEBUG

        // Update layout.
        updateLayoutParams();

        if (Log.IS_DEBUG) Log.logDebug(TAG, "onFinishInflate() : X");
    }

    private void updateLayoutParams() {
        // View finder.
        if (mInterceptor != null) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                    mInterceptor.getLayoutParams();
            params.width = mInterceptorWidth;
            params.height = mInterceptorHeight;
            mInterceptor.setLayoutParams(params);
        }
    }

    private final OnTouchListenerImpl mOnTouchListenerImpl = new OnTouchListenerImpl();
    private class OnTouchListenerImpl implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            mInteractionEngine.onTouchEvent(event);
            return false;
        }
    }

    private final InteractionEngine.InteractionCallback mInteractionCallbackImpl
            = new InteractionCallbackImpl();
    private class InteractionCallbackImpl implements InteractionEngine.InteractionCallback {
        @Override
        public void onSingleTouched(Point point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleTouched() : [X=" + point.x + "] [Y=" + point.y + "]");
            // NOP.
        }

        @Override
        public void onSingleMoved(Point currentPoint, Point lastPoint, Point downPoint) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleMoved() : [X=" + currentPoint.x + "] [Y=" + currentPoint.y + "]");
            // NOP.
        }

        @Override
        public void onSingleStopped(Point currentPoint, Point lastPoint, Point downPoint) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleStopped() : [X=" + currentPoint.x + "] [Y=" + currentPoint.y + "]");
            // NOP.
        }

        @Override
        public void onSingleReleased(Point point) {
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onSingleReleased() : [X=" + point.x + "] [Y=" + point.y + "]");
            // NOP.
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
        public void onDoubleScaled(
                float currentLength,
                float previousLength,
                float originalLength) {
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
            if (Log.IS_DEBUG) Log.logDebug(TAG,
                    "onFling() : [VelocX=" + velocX + "] [VelocY=" + velocY + "]");
            // NOP.
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
            // NOP.
        }
    }



    // Key double click interval.
    private static final int KEY_DOUBLE_CLICK_INTERVAL_MILLIS = 500;
    // Previous focus key down timestamp.
    private long mPreviousFocusKeyDownTimestamp = 0;

    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        switch (keyEvent.getKeyCode()) {
//            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (keyEvent.getRepeatCount() != 0) {
                    // Do not handle hold press.
//                    return true;
                    return false;
                }

                switch (keyEvent.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS DOWN]");

                        final long nowTime = android.os.SystemClock.elapsedRealtime();
                        final long diffTime = nowTime - mPreviousFocusKeyDownTimestamp;
                        mPreviousFocusKeyDownTimestamp = nowTime;
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "Key click diff time = " + diffTime);

                        if (diffTime < KEY_DOUBLE_CLICK_INTERVAL_MILLIS) {
                            // Reset.
                            mPreviousFocusKeyDownTimestamp = 0;

                            // Start overlay camera.
                            OverlayViewFinderController.LifeCycleTrigger.getInstance()
                                    .requestStart(getContext());
                        }
                        break;

                    case KeyEvent.ACTION_UP:
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS UP]");
                        break;

                    default:
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [FOCUS NO ACT]");
                        break;
                }
//                return true;
                return false;

            default:
                // Un-used key code.
                if (Log.IS_DEBUG) Log.logDebug(TAG, "dispatchKeyEvent() : [UNUSED KEY]");
                return false;
        }
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

        // Notify to device.
        OverlayViewFinderController.getInstance().getCurrentState().onSurfaceReady();
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "onLayout() : [Changed=" + changed + "] [Rect="
                 + left + ", " + top + ", " + right + ", " + bottom + "]");
        super.onLayout(changed, left, top, right, bottom);
        // NOP.
    }

    @Override
    public void onSizeChanged(int curW, int curH, int nxtW, int nxtH) {
        if (Log.IS_DEBUG) Log.logDebug(TAG,
                "onSizeChanged() : [CUR=" + curW + "x" + curH + "] [NXT=" +  nxtW + "x" + nxtH + "]");
        super.onSizeChanged(curW, curH, nxtW, nxtH);
        // NOP.
    }
}
