package com.fezrestia.android.lib.interaction;

import android.graphics.PointF;

import com.fezrestia.android.util.math.VectorCalculator;

/**
 * This class can detect double touch events.(rotating and scaling).
 * And callback the result to listener.
 */
class TouchScaleAndRotateDetector {
    // Thresholds definitions.
    private static final int ROTATE_DETECTION_THRESHOLD_DEGREE = 1;

    // Touch point.
    private PointF mCurrentTouchPos0 = new PointF(0.0f, 0.0f);
    private PointF mCurrentTouchPos1 = new PointF(0.0f, 0.0f);
    private PointF mPreviousTouchPos0;
    private PointF mPreviousTouchPos1;

    // Touch event vector.
    private PointF mTouchVec0 = new PointF(0.0f, 0.0f);
    private PointF mTouchVec1 = new PointF(0.0f, 0.0f);

    // Axis vector between 2 touch point.
    private PointF mPreviousAxisVec;
    private PointF mCurrentAxisVec = new PointF(0.0f, 0.0f);

    // Axis rotation difference.
    private float mAxisRotateDeg = 0.0f;

    // Axis length.
    private float mOriginalAxisLen = 0.0f;

    /**
     * Scale and rotate detection callback.
     */
    interface ScaleAndRotateDetectorListener {
        /**
         * 2-finger scaling gesture is detected.
         *
         * @param currentLength
         * @param previousLength
         * @param originalLength
         */
        void onDoubleTouchScaleDetected(
                float currentLength,
                float previousLength,
                float originalLength);

        /**
         * 2-finger rotating gesture is detected.
         *
         * @param degreeVsOrigin
         * @param degreeVsPrevious
         */
        void onDoubleTouchRotateDetected(
                float degreeVsOrigin,
                float degreeVsPrevious);
    }

    private ScaleAndRotateDetectorListener mListener;

    /**
     * CONSTRUCTOR.
     */
    TouchScaleAndRotateDetector() {
        // NOP.
    }

    /**
     * Release all references.
     */
    void release() {
        mListener = null;
    }

    /**
     * Set callback.
     *
     * @param listener
     */
    void setScaleAndRotateDetectorListener(ScaleAndRotateDetectorListener listener) {
        mListener = listener;
    }

    /**
     * Start detection.
     *
     * @param point0
     * @param point1
     */
    void startScaleAndRotateDetection(PointF point0, PointF point1) {
        mPreviousTouchPos0 = new PointF(point0.x, point0.y);
        mPreviousTouchPos1 = new PointF(point1.x, point1.y);
        mPreviousAxisVec = new PointF(point1.x - point0.x, point1.y - point0.y);
        mOriginalAxisLen = mPreviousAxisVec.length();
    }

    /**
     * Update current position.
     *
     * @param point0
     * @param point1
     */
    void updateCurrentPosition(PointF point0, PointF point1) {
        // Cache touch position.
        mCurrentTouchPos0.set(point0);
        mCurrentTouchPos1.set(point1);

        // Create touch vector.
        mTouchVec0.set(
                mCurrentTouchPos0.x - mPreviousTouchPos0.x,
                mCurrentTouchPos0.y - mPreviousTouchPos0.y);
        mTouchVec1.set(
                mCurrentTouchPos1.x - mPreviousTouchPos1.x,
                mCurrentTouchPos1.y - mPreviousTouchPos1.y);

        // Create axis vector.
        mCurrentAxisVec.set(
                mCurrentTouchPos1.x - mCurrentTouchPos0.x,
                mCurrentTouchPos1.y - mCurrentTouchPos0.y);

        // Rotation.
        if ((VectorCalculator.isSquare(mCurrentAxisVec, mTouchVec0)
                && (VectorCalculator.isSquare(mCurrentAxisVec, mTouchVec1)))) {
            // Difference of rotation.
            float difRad = VectorCalculator.getRadianFrom2Vector(
                    mPreviousAxisVec, mCurrentAxisVec);

            // Direction of rotation.
            float direction;
            float outerProduct = mPreviousAxisVec.x * mCurrentAxisVec.y
                    - mCurrentAxisVec.x * mPreviousAxisVec.y;
            if (0 <= outerProduct) {
                direction = 1.0f;
            } else {
                direction = -1.0f;
            }

            // Notify threshold.
            float difDeg = (float) (difRad * 360.0f / 2.0f / Math.PI * direction);
            float previousDeg = mAxisRotateDeg;
            mAxisRotateDeg += difDeg;
            if (ROTATE_DETECTION_THRESHOLD_DEGREE <= Math.abs(mAxisRotateDeg - previousDeg)) {
                // Notify degree difference to listener.
                mListener.onDoubleTouchRotateDetected(
                        mAxisRotateDeg, (mAxisRotateDeg - previousDeg));
            }
        }

        // Scaling.
        if (VectorCalculator.isParallel(mCurrentAxisVec, mTouchVec0)
                && VectorCalculator.isParallel(mCurrentAxisVec, mTouchVec1)) {
            // Get current axis length.
            float currentLen = mCurrentAxisVec.length();
            float previousLen = mPreviousAxisVec.length();

            // Notify listener.
            mListener.onDoubleTouchScaleDetected(currentLen, previousLen, mOriginalAxisLen);
        }

        // Update previous data.
        mPreviousAxisVec.set(mCurrentAxisVec);
        mPreviousTouchPos0.set(mCurrentTouchPos0);
        mPreviousTouchPos1.set(mCurrentTouchPos1);
    }

    /**
     * Stop detection.
     */
    void stopScaleAndRotateDetection() {
        // Reset all fields.
        mCurrentTouchPos0.set(0.0f, 0.0f);
        mCurrentTouchPos1.set(0.0f, 0.0f);
        mPreviousTouchPos0 = null;
        mPreviousTouchPos1 = null;

        mTouchVec0.set(0.0f, 0.0f);
        mTouchVec1.set(0.0f, 0.0f);

        mPreviousAxisVec = null;
        mCurrentAxisVec.set(0.0f, 0.0f);

        mAxisRotateDeg = 0.0f;

        mOriginalAxisLen = 0.0f;
    }
}
