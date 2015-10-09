package com.fezrestia.android.lib.interaction;

import java.util.Timer;
import java.util.TimerTask;

import android.graphics.Point;
import android.graphics.PointF;
import android.os.Handler;

import com.fezrestia.android.util.math.VectorCalculator;

/**
 * This class can detect primary touch position is moved or stopped.
 * And callback the result to listener.
 */
class TouchMoveAndStopDetector {
    // Timer for touch stop detection.
    private Timer mTouchStopDetectorTimer;

    // Timer task
    private TouchStopDetectorTimerTask mTouchStopDetectorTimerTask;

    // Touch stop timer interval.
    private int TOUCH_STOP_DETECTION_TIMER_INTERVAL = 200;

    // Threshold to detect direction is same or not.
    private static final float DIRECTION_TOLERANCE = (float) Math.PI / 3.0f;

    // Listener.
    private TouchStopDetectorListener mListener;

    // Handler for listener event.
    private Handler mCallbackHandler = null;

    // Position and Direction.
    private Point mDownPos = new Point(0, 0);
    private Point mTouchSlopAreaCenterPos = new Point(0, 0);
    private Point mCurrentTouchPos = new Point(0, 0);
    private Point mPreviousTouchPos = new Point(0, 0);
    private Point mLatestCheckedPos = new Point(0, 0);
    private Point mLatestCheckedTrackVec = new Point(0, 0);

    // Touch slop distance.
    private final int mTouchSlop;

    // Finger is already moved or not.
    private boolean mIsFingerAlreadyMoved = false;

    /**
     * CONSTRUCTOR.
     *
     * @param touchSlop
     * @param callbackHandler
     */
    TouchMoveAndStopDetector(int touchSlop, Handler callbackHandler) {
        mTouchSlop = touchSlop;
        mCallbackHandler = callbackHandler;
    }

    /**
     * Release all references, and stop detection timer task immediately.
     */
    void release() {
        killTimer();
        mListener = null;
        mCallbackHandler = null;
    }

    /**
     * Touch and stop detection callback.
     */
    interface TouchStopDetectorListener {
        /**
         * Single touch move is detected.
         *
         * @param currentPoint
         * @param lastPoint
         * @param downPoint
         */
        void onSingleTouchMoveDetected(
                final Point currentPoint,
                final Point lastPoint,
                final Point downPoint);

        /**
         * Single touch stop is detected.
         *
         * @param currentPoint
         * @param lastPoint
         * @param downPoint
         */
        void onSingleTouchStopDetected(
                final Point currentPoint,
                final Point lastPoint,
                final Point downPoint);
    }

    /**
     * Set callback.
     *
     * @param listener
     */
    void setTouchStopDetectorListener(TouchStopDetectorListener listener) {
        mListener = listener;
    }

    /**
     * Start detection.
     *
     * @param downX
     * @param downY
     */
    synchronized void startTouchStopDetection(int downX, int downY) {
        // Store down position.
        mDownPos.set(downX, downY);
        mPreviousTouchPos.set(downX, downY);

        // Update touch slop area center position.
        mTouchSlopAreaCenterPos.set(downX, downY);

        // Update flag.
        mIsFingerAlreadyMoved = false;

        // Create and start timer and task.
        killTimer();
        mTouchStopDetectorTimer = new Timer(true);
        mTouchStopDetectorTimerTask = new TouchStopDetectorTimerTask();

        // Start.
        mTouchStopDetectorTimer.scheduleAtFixedRate(
                mTouchStopDetectorTimerTask,
                TOUCH_STOP_DETECTION_TIMER_INTERVAL,
                TOUCH_STOP_DETECTION_TIMER_INTERVAL);
    }

    /**
     * Update current touch position.
     *
     * @param curX
     * @param curY
     */
    void updateCurrentPosition(int curX, int curY) {
        // Store last position.
        mPreviousTouchPos.set(mCurrentTouchPos.x, mCurrentTouchPos.y);
        // Set current position.
        mCurrentTouchPos.set(curX, curY);

        // Check finger is moved or not.
        int difX = mCurrentTouchPos.x - mTouchSlopAreaCenterPos.x;
        int difY = mCurrentTouchPos.y - mTouchSlopAreaCenterPos.y;
        if ((mTouchSlop * mTouchSlop) < (difX * difX + difY * difY)) {
            // Finger is moved.
            mIsFingerAlreadyMoved = true;

            // Send event.
            if (mListener != null) {
                mListener.onSingleTouchMoveDetected(mCurrentTouchPos, mPreviousTouchPos, mDownPos);
            }
        }
    }

    /**
     * Update current and last position.
     *
     * @param curX
     * @param curY
     */
    void updateCurrentAndLastPosition(int curX, int curY) {
        mPreviousTouchPos.set(curX, curY);
        mCurrentTouchPos.set(curX, curY);
    }

    /**
     * Stop detection.
     */
    synchronized void stopTouchStopDetection() {
        // Release timer and task.
        killTimer();

        // Reset position.
        mCurrentTouchPos.set(0, 0);
        mPreviousTouchPos.set(0, 0);
        mLatestCheckedPos.set(0, 0);
        mLatestCheckedTrackVec.set(0, 0);
    }

    private void killTimer() {
        // Release timer and task.
        if (mTouchStopDetectorTimer != null) {
            mTouchStopDetectorTimer.cancel();
            mTouchStopDetectorTimer.purge();
            mTouchStopDetectorTimer = null;
        }
        if (mTouchStopDetectorTimerTask != null) {
            mTouchStopDetectorTimerTask.cancel();
            mTouchStopDetectorTimerTask = null;
        }
    }

    private class TouchStopDetectorTimerTask extends TimerTask {
        @Override
        public void run() {
            // Calculate difference and radiant.
            int difX = mCurrentTouchPos.x - mLatestCheckedPos.x;
            int difY = mCurrentTouchPos.y - mLatestCheckedPos.y;
            Point currentTrack = new Point(difX, difY);
            float difRad = VectorCalculator.getRadianFrom2Vector(
                    new PointF(currentTrack),
                    new PointF(mLatestCheckedTrackVec));

            // Update cached values.
            updateLastCheckedParameters(mCurrentTouchPos.x, mCurrentTouchPos.y, currentTrack);

            // Check finger is not moved yet, or not.
            if (!mIsFingerAlreadyMoved) {
                // This means, this task is executed after ACTION_DOWN or onTouchStop.
                // Store current parameters.
                // NOP.
                return;
            }

            // Check finger is moved or not.
            if ((difX == 0) && (difY == 0)) {
                // Finger is stopped.
                onTouchStopDetected();
                return;
            }

            // Check touch event is in touch slop area or not.
            if ((difX * difX + difY * difY) < (mTouchSlop * mTouchSlop)) {

                // Calculate vector direction.
                if (Math.abs(difRad) < DIRECTION_TOLERANCE) {
                    // Consider finger is moved very slowly. Try next.
                    // NOP.
                    return;
                }

                // Detect touch stop.
                onTouchStopDetected();
            }
        }
    }

    private void updateLastCheckedParameters(int previousX, int previousY, Point currentTrack) {
        mLatestCheckedPos.set(previousX, previousY);
        mLatestCheckedTrackVec.set(currentTrack.x, currentTrack.y);
    }

    private void onTouchStopDetected() {
        // Get down flag, to reset touch slop area.
        mIsFingerAlreadyMoved = false;
        mTouchSlopAreaCenterPos.set(mCurrentTouchPos.x, mCurrentTouchPos.y);

        // Post to UI thread.
        if (mCallbackHandler != null) {
            mCallbackHandler.post(new NotifyOnSingleTouchStopDetectedTask());
        }
    }

    private class NotifyOnSingleTouchStopDetectedTask implements Runnable {
        @Override
        public void run() {
            if (mListener != null) {
                mListener.onSingleTouchStopDetected(mCurrentTouchPos, mPreviousTouchPos, mDownPos);
            }
        }
    }
}
