package com.fezrestia.android.lib.interaction;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * This class is used to detect interaction between user and target view.
 */
public class InteractionEngine
        implements
                TouchMoveAndStopDetector.TouchStopDetectorListener,
                TouchScaleAndRotateDetector.ScaleAndRotateDetectorListener,
                GestureDetector.OnGestureListener {
    // Log tag.
    public final static String TAG = "TouchActionTranslator";

    // Master Context.
    private Context mContext = null;

    // Target view.
    private View mTargetView = null;

    // Callback handler.
    private Handler mCallbackHandler = null;

    // Margin of target view around.
    private final int mMargin;
    private final int mTouchSlop;

    // Touch events are in target area or not.
    private boolean mIsAllTouchEventInTargetArea = true;

    // Touch stop detector.
    private TouchMoveAndStopDetector mSingleTouchMoveAndStopDetector = null;

    // Scale and Rotate detector.
    private TouchScaleAndRotateDetector mDoubleTouchScaleAndRotateDetector = null;

    // Android standard gesture detector.
    private GestureDetector mAndroidGestureDetector = null;

    // Dummy interaction callback.
    private static final InteractionCallback NULL_LISTENER = new NullInteractionListener();

    // Interaction callback.
    private InteractionCallback mInteractionCallback = NULL_LISTENER;

    /**
     * Set callback.
     *
     * @param callback
     */
    public void setInteractionCallback(InteractionCallback callback) {
        if (callback != null) {
            mInteractionCallback = callback;
        } else {
            mInteractionCallback = NULL_LISTENER;
        }
    }

    /**
     * Interaction callback.
     */
    public interface InteractionCallback {
        // Single touch interaction.
        void onSingleTouched(Point point);
        void onSingleMoved(Point currentPoint, Point lastPoint, Point downPoint);
        void onSingleStopped(Point currentPoint, Point lastPoint, Point downPoint);
        void onSingleReleased(Point point);
        void onSingleCanceled();

        // Double touch interaction.
        void onDoubleTouched(Point point0, Point point1);
        void onDoubleMoved(Point point0, Point point1);
        void onDoubleScaled(float currentLength, float previousLength, float originalLength);
        void onDoubleRotated(float degreeVsOrigin, float degreeVsLast);
        void onSingleReleasedInDouble(Point release, Point remain);
        void onDoubleCanceled();

        // Over triple touch interaction.
        void onOverTripleCanceled();

        // Android gestures.
        void onFling(MotionEvent event1, MotionEvent event2, float velocX, float velocY);
        void onLongPress(MotionEvent event);
        void onShowPress(MotionEvent event);
        void onSingleTapUp(MotionEvent event);
    }

    // This class is null object for InteractionCallback.
    private static final class NullInteractionListener implements InteractionCallback {
        // Single touch interaction.
        @Override
        public void onSingleTouched(Point point) {
            // NOP.
        }

        @Override
        public void onSingleMoved(Point currentPoint, Point lastPoint, Point downPoint) {
            // NOP.
        }

        @Override
        public void onSingleStopped(Point currentPoint, Point lastPoint, Point downPoint) {
            // NOP.
        }

        @Override
        public void onSingleReleased(Point point) {
            // NOP.
        }

        @Override
        public void onSingleCanceled() {
            // NOP.
        }

        // Double touch interaction.
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
                float currentLength, float previousLength, float originalLength) {
            // NOP.
        }

        @Override
        public void onDoubleRotated(float degreeVsOrigin, float degreeVsLast) {
            // NOP.
        }

        @Override
        public void onSingleReleasedInDouble(Point releasedPoint, Point remainedPoint) {
            // NOP.
        }

        @Override
        public void onDoubleCanceled() {
            // NOP.
        }

        // Over triple touch interaction.
        @Override
        public void onOverTripleCanceled() {
            // NOP.
        }

        // Android gestures.
        @Override
        public void onFling(MotionEvent event1, MotionEvent event2, float velocX, float velocY) {
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
            // NOP.
        }
    }

    /**
     * CONSTRUCTOR.
     *
     * Hit area is same as target view.
     *
     * @param context
     * @param targetView
     * @param callbackHandler
     */
    public InteractionEngine(Context context, View targetView, Handler callbackHandler) {
        this(context, targetView, 0, callbackHandler);
    }

    /**
     * CONSTRUCTOR.
     *
     * Hit area is same as target view with margin.
     *
     * @param context
     * @param targetView
     * @param margin
     * @param callbackHandler
     */
    public InteractionEngine(
            Context context,
            View targetView,
            int margin,
            Handler callbackHandler) {
        this(
                context,
                targetView,
                margin,
                ViewConfiguration.get(context).getScaledTouchSlop(),
                callbackHandler);
    }

    /**
     * CONSTRUCTOR.
     *
     * Hit area is same as target view with margin.
     * Touch stop is detected within touch slop. (Android default is used in default)
     *
     * @param context
     * @param targetView
     * @param margin
     * @param touchSlop
     * @param callbackHandler
     */
    public InteractionEngine(
            Context context,
            View targetView,
            int margin,
            int touchSlop,
            Handler callbackHandler) {
        // Set.
        mContext = context;
        mTargetView = targetView;
        mMargin = margin;
        mTouchSlop = touchSlop;
        mCallbackHandler = callbackHandler;

        // Default.
        setInteractionCallback(null);

        // Create touch scale and rotate detector.
        mDoubleTouchScaleAndRotateDetector = new TouchScaleAndRotateDetector();
        mDoubleTouchScaleAndRotateDetector.setScaleAndRotateDetectorListener(this);
    }

    /**
     * Release all references.
     */
    public synchronized void release() {
        mContext = null;
        mTargetView = null;
        mCallbackHandler = null;

        if (mSingleTouchMoveAndStopDetector != null) {
            mSingleTouchMoveAndStopDetector.release();
            mSingleTouchMoveAndStopDetector = null;
        }
        mDoubleTouchScaleAndRotateDetector.release();
        mDoubleTouchScaleAndRotateDetector = null;
        mAndroidGestureDetector = null;

        mInteractionCallback = NULL_LISTENER;
    }

    // Interaction state interface.
    private interface InteractionState {
        void handleMotionEvent(MotionEvent motion);
        void handleSingleTouchMoveEvent(Point currentPoint, Point lastPoint, Point downPoint);
        void handleSingleTouchStopEvent(Point currentPoint, Point lastPoint, Point downPoint);
        void handleTouchScaleEvent(float currentLength, float previousLength, float originalLength);
        void handleTouchRotateEvent(float degreeVsOrigin, float degreeVsLast);
    }

    // Interaction State.
    private InteractionState mCurrentInteractionState = new Idle();

    private synchronized void changeTo(InteractionState next) {
        mCurrentInteractionState = next;
    }

    private class Idle implements InteractionState {
        @Override
        public void handleMotionEvent(MotionEvent motion) {
            switch (motion.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mInteractionCallback.onSingleTouched(
                            new Point((int) motion.getX(0), (int) motion.getY(0)));
                    changeTo(new SingleDown());
                    return;

                default:
                    // NOP.
                    return;
            }
        }

        @Override
        public void handleSingleTouchMoveEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleSingleTouchStopEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleTouchScaleEvent(
                float currentLength,
                float previousLength,
                float originalLength) {
            // NOP.
        }

        @Override
        public void handleTouchRotateEvent(float degreeVsOrigin, float degreeVsLast) {
            // NOP.
        }
    }

    private class SingleDown implements InteractionState {
        @Override
        public void handleMotionEvent(MotionEvent motion) {
            switch (motion.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    // NOP. Move is detected in TouchMoveAndStopDetector.
                    changeTo(new SingleMove());
                    return;

                case MotionEvent.ACTION_UP:
                    mInteractionCallback.onSingleReleased(
                            new Point((int) motion.getX(0), (int) motion.getY(0)));
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_CANCEL:
                    mInteractionCallback.onSingleCanceled();
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (motion.getPointerCount() == 1) {
                        return;
                    }
                    mInteractionCallback.onDoubleTouched(
                            new Point((int) motion.getX(0), (int) motion.getY(0)),
                            new Point((int) motion.getX(1), (int) motion.getY(1)));
                    changeTo(new DoubleDown());
                    return;

                default:
                    // NOP.
                    return;
            }
        }

        @Override
        public void handleSingleTouchMoveEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleSingleTouchStopEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleTouchScaleEvent(
                float currentLength,
                float previousLength,
                float originalLength) {
            // NOP.
        }

        @Override
        public void handleTouchRotateEvent(
                float degreeVsOrigin,
                float degreeVsLast) {
            // NOP.
        }
    }

    private class SingleMove implements InteractionState {
        @Override
        public void handleMotionEvent(MotionEvent motion) {
            switch (motion.getActionMasked()) {
                case MotionEvent.ACTION_UP:
                    mInteractionCallback.onSingleReleased(
                            new Point((int) motion.getX(0), (int) motion.getY(0)));
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_CANCEL:
                    mInteractionCallback.onSingleCanceled();
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (motion.getPointerCount() == 1) {
                        return;
                    }
                    mInteractionCallback.onDoubleTouched(
                            new Point((int) motion.getX(0), (int) motion.getY(0)),
                            new Point((int) motion.getX(1), (int) motion.getY(1)));
                    changeTo(new DoubleDown());
                    return;

                default:
                    // NOP.
                    return;
            }
        }

        @Override
        public void handleSingleTouchMoveEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            mInteractionCallback.onSingleMoved(currentPoint, lastPoint, downPoint);
        }

        @Override
        public void handleSingleTouchStopEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            mInteractionCallback.onSingleStopped(currentPoint, lastPoint, downPoint);
            changeTo(new SingleStop());
        }

        @Override
        public void handleTouchScaleEvent(
                float currentLength,
                float previousLength,
                float originalLength) {
            // NOP.
        }

        @Override
        public void handleTouchRotateEvent(float degreeVsOrigin, float degreeVsLast) {
            // NOP.
        }
    }

    private class SingleStop implements InteractionState {
        @Override
        public void handleMotionEvent(MotionEvent motion) {
            switch (motion.getActionMasked()) {
                case MotionEvent.ACTION_UP:
                    mInteractionCallback.onSingleReleased(
                            new Point((int) motion.getX(0), (int) motion.getY(0)));
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_CANCEL:
                    mInteractionCallback.onSingleCanceled();
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (motion.getPointerCount() == 1) {
                        return;
                    }
                    mInteractionCallback.onDoubleTouched(
                            new Point((int) motion.getX(0), (int) motion.getY(0)),
                            new Point((int) motion.getX(1), (int) motion.getY(1)));
                    changeTo(new DoubleDown());
                    return;

                default:
                    // NOP.
                    return;
            }
        }

        @Override
        public void handleSingleTouchMoveEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            mInteractionCallback.onSingleMoved(currentPoint, lastPoint, downPoint);
            changeTo(new SingleMove());
        }

        @Override
        public void handleSingleTouchStopEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleTouchScaleEvent(
                float currentLength,
                float previousLength,
                float originalLength) {
            // NOP.
        }

        @Override
        public void handleTouchRotateEvent(float degreeVsOrigin, float degreeVsLast) {
            // NOP.
        }
    }

    private class DoubleDown implements InteractionState {
        @Override
        public void handleMotionEvent(MotionEvent motion) {
            switch (motion.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (motion.getPointerCount() != 2) {
                        return;
                    }
                    Point index0 = new Point((int) motion.getX(0), (int) motion.getY(0));
                    Point index1 = new Point((int) motion.getX(1), (int) motion.getY(1));
                    mInteractionCallback.onDoubleMoved(index0, index1);
                    changeTo(new DoubleMove(index0, index1));
                    return;

                case MotionEvent.ACTION_CANCEL:
                    mInteractionCallback.onDoubleCanceled();
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_POINTER_DOWN:
                    changeTo(new OverTriple());
                    return;

                case MotionEvent.ACTION_POINTER_UP:
                    if (motion.getPointerCount() == 1) {
                        return;
                    }
                    int release = motion.getActionIndex();
                    int remain;
                    if (release == 0) {
                        remain = 1;
                    } else {
                        remain = 0;
                    }
                    mInteractionCallback.onSingleReleasedInDouble(
                            new Point((int) motion.getX(release), (int) motion.getY(release)),
                            new Point((int) motion.getX(remain), (int) motion.getY(remain)));

                    // Reset internal fields.
                    getSingleTouchMoveAndStopDetector().updateCurrentAndLastPosition(
                            (int) motion.getX(remain),
                            (int) motion.getY(remain));

                    changeTo(new SingleMove());
                    return;

                default:
                    // NOP.
                    return;
            }
        }

        @Override
        public void handleSingleTouchMoveEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleSingleTouchStopEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleTouchScaleEvent(
                float currentLength,
                float previousLength,
                float originalLength) {
            // NOP.
        }

        @Override
        public void handleTouchRotateEvent(float degreeVsOrigin, float degreeVsLast) {
            // NOP.
        }
    }

    private class DoubleMove implements InteractionState {
        /**
         * CONSTRUCTOR.
         *
         * @param point0
         * @param point1
         */
        DoubleMove(Point point0, Point point1) {
            // Start scale and rotate detection.
            mDoubleTouchScaleAndRotateDetector.startScaleAndRotateDetection(
                    new PointF(point0),
                    new PointF(point1));
        }

        @Override
        public void handleMotionEvent(MotionEvent motion) {
            switch (motion.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (motion.getPointerCount() != 2) {
                        return;
                    }
                    // Update scale and rotate detection.
                    mDoubleTouchScaleAndRotateDetector.updateCurrentPosition(
                            new PointF(motion.getX(0), motion.getY(0)),
                            new PointF(motion.getX(1), motion.getY(1)));

                    mInteractionCallback.onDoubleMoved(
                            new Point((int) motion.getX(0), (int) motion.getY(0)),
                            new Point((int) motion.getX(1), (int) motion.getY(1)));
                    return;

                case MotionEvent.ACTION_CANCEL:
                    mInteractionCallback.onDoubleCanceled();
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_POINTER_DOWN:
                    // Stop touch scale and rotate detection.
                    mDoubleTouchScaleAndRotateDetector.stopScaleAndRotateDetection();
                    changeTo(new OverTriple());
                    return;

                case MotionEvent.ACTION_POINTER_UP:
                    // Stop touch scale and rotate detection.
                    mDoubleTouchScaleAndRotateDetector.stopScaleAndRotateDetection();

                    int release = motion.getActionIndex();
                    int remain;
                    if (release == 0) {
                        remain = 1;
                    } else {
                        remain = 0;
                    }
                    mInteractionCallback.onSingleReleasedInDouble(
                            new Point((int) motion.getX(release), (int) motion.getY(release)),
                            new Point((int) motion.getX(remain), (int) motion.getY(remain)));

                    // Reset internal fields.
                    getSingleTouchMoveAndStopDetector().updateCurrentAndLastPosition(
                            (int) motion.getX(remain),
                            (int) motion.getY(remain));

                    changeTo(new SingleMove());
                    return;
            }
        }

        @Override
        public void handleSingleTouchMoveEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleSingleTouchStopEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleTouchScaleEvent(
                float currentLength,
                float previousLength,
                float originalLength) {
            mInteractionCallback.onDoubleScaled(currentLength, previousLength, originalLength);
        }

        @Override
        public void handleTouchRotateEvent(float degreeVsOrigin, float degreeVsLast) {
            mInteractionCallback.onDoubleRotated(degreeVsOrigin, degreeVsLast);
        }
    }

    private class OverTriple implements InteractionState {
        @Override
        public void handleMotionEvent(MotionEvent motion) {
            switch (motion.getActionMasked()) {
                case MotionEvent.ACTION_CANCEL:
                    mInteractionCallback.onOverTripleCanceled();
                    changeTo(new Idle());
                    return;

                case MotionEvent.ACTION_POINTER_UP:
                    if (motion.getPointerCount() == 3) {
                        changeTo(new DoubleDown());
                    }
                    return;

                default:
                    // NOP.
                    return;
            }
        }

        @Override
        public void handleSingleTouchMoveEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleSingleTouchStopEvent(
                Point currentPoint,
                Point lastPoint,
                Point downPoint) {
            // NOP.
        }

        @Override
        public void handleTouchScaleEvent(
                float currentLength,
                float previousLength,
                float originalLength) {
            // NOP.
        }

        @Override
        public void handleTouchRotateEvent(float degreeVsOrigin, float degreeVsLast) {
            // NOP.
        }
    }

    /**
     * Input touch event to interaction engine.
     *
     * @param motion
     * @return
     */
    public synchronized boolean onTouchEvent(MotionEvent motion) {
        // Check touch area.
        if (mTargetView != null) {
            for (int i = 0; i < motion.getPointerCount(); ++i) {
                if (hitTest(mTargetView, mMargin, (int) motion.getX(i), (int) motion.getY(i))) {
                    mIsAllTouchEventInTargetArea = true;
                } else {
                    // Touch point is out of target area.
                    mIsAllTouchEventInTargetArea = false;
                    break;
                }
            }
        }

        // Switch.
        switch (motion.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Start touch stop detection.
                getSingleTouchMoveAndStopDetector().startTouchStopDetection(
                        (int) motion.getX(), (int) motion.getY());
                break;

            case MotionEvent.ACTION_MOVE:
                if (1 == motion.getPointerCount()) {
                    // Update touch stop detector.
                    getSingleTouchMoveAndStopDetector().updateCurrentPosition(
                            (int) motion.getX(), (int) motion.getY());
                }
                break;

            case MotionEvent.ACTION_UP:
                // fall-through.
            case MotionEvent.ACTION_CANCEL:
                // Stop touch stop detection.
                getSingleTouchMoveAndStopDetector().stopTouchStopDetection();
                break;

            default:
                // NOP.
                break;
        }

        // Handle.
        mCurrentInteractionState.handleMotionEvent(motion);

        // Android gesture detector.
        getAndroidGestureDetector().onTouchEvent(motion);

        return mIsAllTouchEventInTargetArea;
    }

    /**
     * Cancel interaction detection.
     */
    public void cancel() {
        changeTo(new Idle());
    }

    private boolean hitTest(View targetView, int margin, int xOnView, int yOnView) {
        // Hit area is inside the target view without touch area margin.
        Rect hitRect = new Rect(
                margin,
                margin,
                targetView.getWidth() - margin,
                targetView.getHeight() - margin);

        return hitRect.contains(xOnView, yOnView);
    }

    @Override
    public synchronized void onSingleTouchMoveDetected(
            final Point currentPoint,
            final Point lastPoint,
            final Point downPoint) {
        mCurrentInteractionState.handleSingleTouchMoveEvent(currentPoint, lastPoint, downPoint);
    }

    @Override
    public synchronized void onSingleTouchStopDetected(
            final Point currentPoint,
            final Point lastPoint,
            final Point downPoint) {
        mCurrentInteractionState.handleSingleTouchStopEvent(currentPoint, lastPoint, downPoint);
    }

    @Override
    public synchronized void onDoubleTouchScaleDetected(
            final float currentLength,
            final float previousLength,
            final float originalLength) {
        mCurrentInteractionState.handleTouchScaleEvent(currentLength, previousLength, originalLength);
    }

    @Override
    public synchronized void onDoubleTouchRotateDetected(
            final float degreeVsOrigin,
            final float degreeVsLast) {
        mCurrentInteractionState.handleTouchRotateEvent(degreeVsOrigin, degreeVsLast);
    }

    @Override
    public synchronized boolean onDown(MotionEvent event) {
        // NOP.
        return true;
    }

    @Override
    public synchronized boolean onFling(
            MotionEvent event1,
            MotionEvent event2,
            float velocX,
            float velocY) {
        mInteractionCallback.onFling(event1, event2, velocX, velocY);
        return true;
    }

    @Override
    public synchronized void onLongPress(MotionEvent event) {
        mInteractionCallback.onLongPress(event);
    }

    @Override
    public synchronized boolean onScroll(
            MotionEvent event1,
            MotionEvent event2,
            float distanceX,
            float distanceY) {
        // NOP.
        return true;
    }

    @Override
    public synchronized void onShowPress(MotionEvent event) {
        mInteractionCallback.onShowPress(event);
    }

    @Override
    public synchronized boolean onSingleTapUp(MotionEvent event) {
        mInteractionCallback.onSingleTapUp(event);
        return true;
    }

    private TouchMoveAndStopDetector getSingleTouchMoveAndStopDetector() {
        // Create touch stop detector.
        if (mSingleTouchMoveAndStopDetector == null) {
            mSingleTouchMoveAndStopDetector = new TouchMoveAndStopDetector(
                    mTouchSlop,
                    mCallbackHandler);
            mSingleTouchMoveAndStopDetector.setTouchStopDetectorListener(this);
        }
        return mSingleTouchMoveAndStopDetector;
    }

    private GestureDetector getAndroidGestureDetector() {
        // Create Android gesture detector.
        if (mAndroidGestureDetector == null) {
            mAndroidGestureDetector = new GestureDetector(
                    mContext,
                    this,
                    mCallbackHandler,
                    true);
        }
        return mAndroidGestureDetector;
    }
}
