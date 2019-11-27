package com.fezrestia.android.lib.interaction

import android.graphics.PointF

import com.fezrestia.android.lib.util.math.VectorCalculator
import kotlin.math.abs

/**
 * This class can detect double touch events.(rotating and scaling).
 * And callback the result to callback.
 */
internal class TouchScaleAndRotateDetector {

    // Touch point.
    private val currentTouchPos0 = PointF(0.0f, 0.0f)
    private val currentTouchPos1 = PointF(0.0f, 0.0f)
    private var previousTouchPos0 = PointF(0.0f, 0.0f)
    private var previousTouchPos1 = PointF(0.0f, 0.0f)

    // Touch event vector.
    private val touchVec0 = PointF(0.0f, 0.0f)
    private val touchVec1 = PointF(0.0f, 0.0f)

    // Axis vector between 2 touch point.
    private var previousAxisVec = PointF(0.0f, 0.0f)
    private val currentAxisVec = PointF(0.0f, 0.0f)

    // Axis rotation difference.
    private var axisRotateDeg = 0.0f

    // Axis length.
    private var originalAxisLen = 0.0f

    var callback: ScaleAndRotateDetectorCallback? = null

    /**
     * Scale and rotate detection callback.
     */
    internal interface ScaleAndRotateDetectorCallback {
        /**
         * 2-finger scaling gesture is detected.
         *
         * @param currentLength Current 2 point length.
         * @param previousLength Previous length.
         * @param originalLength Original length.
         */
        fun onDoubleTouchScaleDetected(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float)

        /**
         * 2-finger rotating gesture is detected.
         *
         * @param degreeVsOrigin Degree diff from origin.
         * @param degreeVsPrevious Degree diff from previous.
         */
        fun onDoubleTouchRotateDetected(
                degreeVsOrigin: Float,
                degreeVsPrevious: Float)
    }

    /**
     * Release all references.
     */
    fun release() {
        callback = null
    }

    /**
     * Start detection.
     *
     * @param point0 Point 0.
     * @param point1 Point 1.
     */
    fun startScaleAndRotateDetection(point0: PointF, point1: PointF) {
        previousTouchPos0 = PointF(point0.x, point0.y)
        previousTouchPos1 = PointF(point1.x, point1.y)
        previousAxisVec = PointF(point1.x - point0.x, point1.y - point0.y)
        originalAxisLen = previousAxisVec.length()
    }

    /**
     * Update current position.
     *
     * @param point0 Point 0.
     * @param point1 Point 1.
     */
    fun updateCurrentPosition(point0: PointF, point1: PointF) {
        // Cache touch position.
        currentTouchPos0.set(point0)
        currentTouchPos1.set(point1)

        // Create touch vector.
        touchVec0.set(
                currentTouchPos0.x - previousTouchPos0.x,
                currentTouchPos0.y - previousTouchPos0.y)
        touchVec1.set(
                currentTouchPos1.x - previousTouchPos1.x,
                currentTouchPos1.y - previousTouchPos1.y)

        // Create axis vector.
        currentAxisVec.set(
                currentTouchPos1.x - currentTouchPos0.x,
                currentTouchPos1.y - currentTouchPos0.y)

        // Rotation.
        if (VectorCalculator.isSquare(currentAxisVec, touchVec0)
                && VectorCalculator.isSquare(currentAxisVec, touchVec1)) {
            // Difference of rotation.
            val difRad = VectorCalculator.getRadianFrom2Vector(
                    previousAxisVec, currentAxisVec)

            // Direction of rotation.
            val direction: Float
            val outerProduct = previousAxisVec.x * currentAxisVec.y - currentAxisVec.x * previousAxisVec.y
            direction = if (0 <= outerProduct) 1.0f else -1.0f

            // Notify threshold.
            val difDeg = ((difRad * 360.0f).toDouble() / 2.0 / Math.PI * direction).toFloat()
            val previousDeg = axisRotateDeg
            axisRotateDeg += difDeg
            if (ROTATE_DETECTION_THRESHOLD_DEGREE <= abs(axisRotateDeg - previousDeg)) {
                // Notify degree difference to callback.
                callback?.onDoubleTouchRotateDetected(axisRotateDeg, axisRotateDeg - previousDeg)
            }
        }

        // Scaling.
        if (VectorCalculator.isParallel(currentAxisVec, touchVec0)
                && VectorCalculator.isParallel(currentAxisVec, touchVec1)) {
            // Get current axis length.
            val currentLen = currentAxisVec.length()
            val previousLen = previousAxisVec.length()

            // Notify callback.
            callback?.onDoubleTouchScaleDetected(currentLen, previousLen, originalAxisLen)
        }

        // Update previous data.
        previousAxisVec.set(currentAxisVec)
        previousTouchPos0.set(currentTouchPos0)
        previousTouchPos1.set(currentTouchPos1)
    }

    /**
     * Stop detection.
     */
    fun stopScaleAndRotateDetection() {
        // Reset all fields.
        currentTouchPos0.set(0.0f, 0.0f)
        currentTouchPos1.set(0.0f, 0.0f)
        previousTouchPos0.set(0.0f, 0.0f)
        previousTouchPos1.set(0.0f, 0.0f)

        touchVec0.set(0.0f, 0.0f)
        touchVec1.set(0.0f, 0.0f)

        previousAxisVec.set(0.0f, 0.0f)
        currentAxisVec.set(0.0f, 0.0f)

        axisRotateDeg = 0.0f

        originalAxisLen = 0.0f
    }

    companion object {
        // Thresholds definitions.
        private const val ROTATE_DETECTION_THRESHOLD_DEGREE = 1
    }
}
