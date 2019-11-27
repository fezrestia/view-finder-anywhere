package com.fezrestia.android.lib.interaction

import java.util.Timer
import java.util.TimerTask

import android.graphics.Point
import android.graphics.PointF
import android.os.Handler

import com.fezrestia.android.lib.util.math.VectorCalculator
import kotlin.math.abs

/**
 * This class can detect primary touch position is moved or stopped.
 * And callback the result to listener.
 *
 * @constructor
 * @param touchSlop Touch slop size in pixel.
 * @param callbackHandler Handler.
 */
internal class TouchMoveAndStopDetector(
        private val touchSlop: Int,
        private var callbackHandler: Handler?) {

    // Timer for touch stop detection.
    private var touchStopDetectorTimer: Timer? = null

    // Timer task
    private var touchStopDetectorTimerTask: TouchStopDetectorTimerTask? = null

    // Listener.
    var callback: TouchStopDetectorCallback? = null

    // Position and Direction.
    private val downPos = Point(0, 0)
    private val touchSlopAreaCenterPos = Point(0, 0)
    private val currentTouchPos = Point(0, 0)
    private val previousTouchPos = Point(0, 0)
    private val latestCheckedPos = Point(0, 0)
    private val latestCheckedTrackVec = Point(0, 0)

    // Finger is already moved or not.
    private var isFingerAlreadyMoved = false

    /**
     * Release all references, and stop detection timer task immediately.
     */
    fun release() {
        killTimer()
        callback = null
        callbackHandler = null
    }

    /**
     * Touch and stop detection callback.
     */
    internal interface TouchStopDetectorCallback {
        /**
         * Single touch move is detected.
         *
         * @param currentPoint Current touch point.
         * @param lastPoint Last touched point.
         * @param downPoint Touch start point.
         */
        fun onSingleTouchMoveDetected(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point)

        /**
         * Single touch stop is detected.
         *
         * @param currentPoint Current touch point.
         * @param lastPoint Last touched point.
         * @param downPoint Touch start point.
         */
        fun onSingleTouchStopDetected(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point)
    }

    /**
     * Start detection.
     *
     * @param downX Touch stop detection start position X.
     * @param downY Touch stop detection start position Y.
     */
    @Synchronized
    fun startTouchStopDetection(downX: Int, downY: Int) {
        // Store down position.
        downPos.set(downX, downY)
        previousTouchPos.set(downX, downY)

        // Update touch slop area center position.
        touchSlopAreaCenterPos.set(downX, downY)

        // Update flag.
        isFingerAlreadyMoved = false

        // Create and start timer and task.
        killTimer()
        touchStopDetectorTimer = Timer(true)
        touchStopDetectorTimerTask = TouchStopDetectorTimerTask()

        // Start.
        touchStopDetectorTimer!!.scheduleAtFixedRate(
                touchStopDetectorTimerTask,
                TOUCH_STOP_DETECTION_TIMER_INTERVAL,
                TOUCH_STOP_DETECTION_TIMER_INTERVAL)
    }

    /**
     * Update current touch position.
     *
     * @param curX Current position X.
     * @param curY Current position Y.
     */
    fun updateCurrentPosition(curX: Int, curY: Int) {
        // Store last position.
        previousTouchPos.set(currentTouchPos.x, currentTouchPos.y)
        // Set current position.
        currentTouchPos.set(curX, curY)

        // Check finger is moved or not.
        val difX = currentTouchPos.x - touchSlopAreaCenterPos.x
        val difY = currentTouchPos.y - touchSlopAreaCenterPos.y
        if (touchSlop * touchSlop < difX * difX + difY * difY) {
            // Finger is moved.
            isFingerAlreadyMoved = true

            // Send event.
            callback?.onSingleTouchMoveDetected(currentTouchPos, previousTouchPos, downPos)
        }
    }

    /**
     * Update current and last position.
     *
     * @param curX Current position X.
     * @param curY Current position Y.
     */
    fun updateCurrentAndLastPosition(curX: Int, curY: Int) {
        previousTouchPos.set(curX, curY)
        currentTouchPos.set(curX, curY)
    }

    /**
     * Stop detection.
     */
    @Synchronized
    fun stopTouchStopDetection() {
        // Release timer and task.
        killTimer()

        // Reset position.
        currentTouchPos.set(0, 0)
        previousTouchPos.set(0, 0)
        latestCheckedPos.set(0, 0)
        latestCheckedTrackVec.set(0, 0)
    }

    private fun killTimer() {
        // Release timer and task.
        touchStopDetectorTimer?.let { timer ->
            timer.cancel()
            timer.purge()
            touchStopDetectorTimer = null
        }
        touchStopDetectorTimerTask?.let { task ->
            task.cancel()
            touchStopDetectorTimerTask = null
        }
    }

    private inner class TouchStopDetectorTimerTask : TimerTask() {
        override fun run() {
            // Calculate difference and radiant.
            val difX = currentTouchPos.x - latestCheckedPos.x
            val difY = currentTouchPos.y - latestCheckedPos.y
            val currentTrack = Point(difX, difY)
            val difRad = VectorCalculator.getRadianFrom2Vector(
                    PointF(currentTrack),
                    PointF(latestCheckedTrackVec))

            // Update cached values.
            updateLastCheckedParameters(currentTouchPos.x, currentTouchPos.y, currentTrack)

            // Check finger is not moved yet, or not.
            if (!isFingerAlreadyMoved) {
                // This means, this task is executed after ACTION_DOWN or onTouchStop.
                // Store current parameters.
                // NOP.
                return
            }

            // Check finger is moved or not.
            if (difX == 0 && difY == 0) {
                // Finger is stopped.
                onTouchStopDetected()
                return
            }

            // Check touch event is in touch slop area or not.
            if (difX * difX + difY * difY < touchSlop * touchSlop) {

                // Calculate vector direction.
                if (abs(difRad) < DIRECTION_TOLERANCE) {
                    // Consider finger is moved very slowly. Try next.
                    // NOP.
                    return
                }

                // Detect touch stop.
                onTouchStopDetected()
            }
        }
    }

    private fun updateLastCheckedParameters(previousX: Int, previousY: Int, currentTrack: Point) {
        latestCheckedPos.set(previousX, previousY)
        latestCheckedTrackVec.set(currentTrack.x, currentTrack.y)
    }

    private fun onTouchStopDetected() {
        // Get down flag, to reset touch slop area.
        isFingerAlreadyMoved = false
        touchSlopAreaCenterPos.set(currentTouchPos.x, currentTouchPos.y)

        // Post to UI thread.
        callbackHandler?.post(NotifyOnSingleTouchStopDetectedTask())
    }

    private inner class NotifyOnSingleTouchStopDetectedTask : Runnable {
        override fun run() {
            callback?.onSingleTouchStopDetected(currentTouchPos, previousTouchPos, downPos)
        }
    }

    companion object {
        // Touch stop timer interval.
        private const val TOUCH_STOP_DETECTION_TIMER_INTERVAL = 200L

        // Threshold to detect direction is same or not.
        private const val DIRECTION_TOLERANCE = Math.PI.toFloat() / 3.0f
    }
}
