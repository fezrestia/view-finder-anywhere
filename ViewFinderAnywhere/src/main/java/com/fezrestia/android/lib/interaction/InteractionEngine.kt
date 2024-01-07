@file:Suppress("unused")

package com.fezrestia.android.lib.interaction

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * This class is used to detect interaction between user and target view.
 *
 * @constructor
 * Hit area is same as target view with margin.
 * Touch stop is detected within touch slop. (Android default is used in default)
 * @param context Master context.
 * @param targetView Touch target view.
 * @param margin Touch target area margin.
 * @touchSlop Touch slop size in pixel.
 * @callbackHandler Handler.
 */
class InteractionEngine(
        context: Context,
        private var targetView: View,
        private val margin: Int,
        touchSlop: Int,
        private var callbackHandler: Handler?) :
                TouchMoveAndStopDetector.TouchStopDetectorCallback,
                TouchScaleAndRotateDetector.ScaleAndRotateDetectorCallback,
                GestureDetector.OnGestureListener {

    constructor(context: Context, targetView: View, callbackHandler: Handler) :
            this(context, targetView, 0, callbackHandler)
    constructor(context: Context, targetView: View, margin: Int, callbackHandler: Handler) :
            this(context, targetView, margin, ViewConfiguration.get(context).scaledTouchSlop, callbackHandler)

    // Touch events are in target area or not.
    private var isAllTouchEventInTargetArea = true

    // Touch stop detector.
    private val singleTouchMoveAndStopDetector = TouchMoveAndStopDetector(
            touchSlop,
            callbackHandler)

    // Scale and Rotate detector.
    private val doubleTouchScaleAndRotateDetector = TouchScaleAndRotateDetector()

    // Android standard gesture detector.
    private val androidGestureDetector = GestureDetector(
            context,
            this,
            callbackHandler,
            true)

    // Interaction callback.
    var callback: InteractionCallback? = null

    // Interaction State.
    private var currentInteractionState: InteractionState = Idle()

    init {
        singleTouchMoveAndStopDetector.callback = this
        doubleTouchScaleAndRotateDetector.callback = this
    }

    /**
     * Interaction callback.
     */
    interface InteractionCallback {
        // Single touch interaction.
        fun onSingleTouched(point: Point)

        fun onSingleMoved(currentPoint: Point, lastPoint: Point, downPoint: Point)
        fun onSingleStopped(currentPoint: Point, lastPoint: Point, downPoint: Point)
        fun onSingleReleased(point: Point)
        fun onSingleCanceled(point: Point)

        // Double touch interaction.
        fun onDoubleTouched(point0: Point, point1: Point)

        fun onDoubleMoved(point0: Point, point1: Point)
        fun onDoubleScaled(currentLength: Float, previousLength: Float, originalLength: Float)
        fun onDoubleRotated(degreeVsOrigin: Float, degreeVsLast: Float)
        fun onSingleReleasedInDouble(release: Point, remain: Point)
        fun onDoubleCanceled()

        // Over triple touch interaction.
        fun onOverTripleCanceled()

        // Android gestures.
        fun onFling(event1: MotionEvent, event2: MotionEvent, velocX: Float, velocY: Float)

        fun onLongPress(event: MotionEvent)
        fun onShowPress(event: MotionEvent)
        fun onSingleTapUp(event: MotionEvent)
    }

    /**
     * Release all references.
     */
    @Synchronized
    fun release() {
        callbackHandler = null

        singleTouchMoveAndStopDetector.release()
        doubleTouchScaleAndRotateDetector.release()

        callback = null
    }

    // Interaction state interface.
    private interface InteractionState {
        fun handleMotionEvent(motion: MotionEvent)
        fun handleSingleTouchMoveEvent(currentPoint: Point, lastPoint: Point, downPoint: Point)
        fun handleSingleTouchStopEvent(currentPoint: Point, lastPoint: Point, downPoint: Point)
        fun handleTouchScaleEvent(currentLength: Float, previousLength: Float, originalLength: Float)
        fun handleTouchRotateEvent(degreeVsOrigin: Float, degreeVsLast: Float)
    }

    @Synchronized
    private fun changeTo(next: InteractionState) {
        currentInteractionState = next
    }

    private inner class Idle : InteractionState {
        override fun handleMotionEvent(motion: MotionEvent) {
            if (motion.actionMasked == MotionEvent.ACTION_DOWN) {
                callback?.onSingleTouched(
                        Point(motion.getX(0).toInt(), motion.getY(0).toInt()))
                changeTo(SingleDown())
            }
        }

        override fun handleSingleTouchMoveEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleSingleTouchStopEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleTouchScaleEvent(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float) {
            // NOP.
        }

        override fun handleTouchRotateEvent(degreeVsOrigin: Float, degreeVsLast: Float) {
            // NOP.
        }
    }

    private inner class SingleDown : InteractionState {
        override fun handleMotionEvent(motion: MotionEvent) {
            when (motion.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    // NOP. Move is detected in TouchMoveAndStopDetector.
                    changeTo(SingleMove())
                }

                MotionEvent.ACTION_UP -> {
                    callback?.onSingleReleased(Point(motion.getX(0).toInt(), motion.getY(0).toInt()))
                    changeTo(Idle())
                }

                MotionEvent.ACTION_CANCEL -> {
                    callback?.onSingleCanceled(Point(motion.getX(0).toInt(), motion.getY(0).toInt()))
                    changeTo(Idle())
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (motion.pointerCount != 1) {
                        callback?.onDoubleTouched(
                                Point(motion.getX(0).toInt(), motion.getY(0).toInt()),
                                Point(motion.getX(1).toInt(), motion.getY(1).toInt()))
                        changeTo(DoubleDown())
                    }
                }

                else -> {
                    // NOP.
                }
            }
        }

        override fun handleSingleTouchMoveEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleSingleTouchStopEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleTouchScaleEvent(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float) {
            // NOP.
        }

        override fun handleTouchRotateEvent(
                degreeVsOrigin: Float,
                degreeVsLast: Float) {
            // NOP.
        }
    }

    private inner class SingleMove : InteractionState {
        override fun handleMotionEvent(motion: MotionEvent) {
            when (motion.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    callback?.onSingleReleased(Point(motion.getX(0).toInt(), motion.getY(0).toInt()))
                    changeTo(Idle())
                }

                MotionEvent.ACTION_CANCEL -> {
                    callback?.onSingleCanceled(Point(motion.getX(0).toInt(), motion.getY(0).toInt()))
                    changeTo(Idle())
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (motion.pointerCount != 1) {
                        callback?.onDoubleTouched(
                                Point(motion.getX(0).toInt(), motion.getY(0).toInt()),
                                Point(motion.getX(1).toInt(), motion.getY(1).toInt()))
                        changeTo(DoubleDown())
                    }
                }

                else -> {
                    // NOP.
                }
            }
        }

        override fun handleSingleTouchMoveEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            callback?.onSingleMoved(currentPoint, lastPoint, downPoint)
        }

        override fun handleSingleTouchStopEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            callback?.onSingleStopped(currentPoint, lastPoint, downPoint)
            changeTo(SingleStop())
        }

        override fun handleTouchScaleEvent(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float) {
            // NOP.
        }

        override fun handleTouchRotateEvent(degreeVsOrigin: Float, degreeVsLast: Float) {
            // NOP.
        }
    }

    private inner class SingleStop : InteractionState {
        override fun handleMotionEvent(motion: MotionEvent) {
            when (motion.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    callback?.onSingleReleased(Point(motion.getX(0).toInt(), motion.getY(0).toInt()))
                    changeTo(Idle())
                }

                MotionEvent.ACTION_CANCEL -> {
                    callback?.onSingleCanceled(Point(motion.getX(0).toInt(), motion.getY(0).toInt()))
                    changeTo(Idle())
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (motion.pointerCount != 1) {
                        callback?.onDoubleTouched(
                                Point(motion.getX(0).toInt(), motion.getY(0).toInt()),
                                Point(motion.getX(1).toInt(), motion.getY(1).toInt()))
                        changeTo(DoubleDown())
                    }
                }

                else -> {
                    // NOP.
                }
            }
        }

        override fun handleSingleTouchMoveEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            callback?.onSingleMoved(currentPoint, lastPoint, downPoint)
            changeTo(SingleMove())
        }

        override fun handleSingleTouchStopEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleTouchScaleEvent(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float) {
            // NOP.
        }

        override fun handleTouchRotateEvent(degreeVsOrigin: Float, degreeVsLast: Float) {
            // NOP.
        }
    }

    private inner class DoubleDown : InteractionState {
        override fun handleMotionEvent(motion: MotionEvent) {
            when (motion.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (motion.pointerCount == 2) {
                        val index0 = Point(motion.getX(0).toInt(), motion.getY(0).toInt())
                        val index1 = Point(motion.getX(1).toInt(), motion.getY(1).toInt())
                        callback?.onDoubleMoved(index0, index1)
                        changeTo(DoubleMove(index0, index1))
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    callback?.onDoubleCanceled()
                    changeTo(Idle())
                }

                MotionEvent.ACTION_POINTER_DOWN -> changeTo(OverTriple())

                MotionEvent.ACTION_POINTER_UP -> {
                    if (motion.pointerCount != 1) {
                        val release = motion.actionIndex
                        val remain: Int
                        remain = if (release == 0) {
                            1
                        } else {
                            0
                        }
                        callback?.onSingleReleasedInDouble(
                                Point(motion.getX(release).toInt(), motion.getY(release).toInt()),
                                Point(motion.getX(remain).toInt(), motion.getY(remain).toInt()))

                        // Reset internal fields.
                        singleTouchMoveAndStopDetector.updateCurrentAndLastPosition(
                                motion.getX(remain).toInt(),
                                motion.getY(remain).toInt())

                        changeTo(SingleMove())
                    }
                }

                else -> {
                    // NOP.
                }
            }
        }

        override fun handleSingleTouchMoveEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleSingleTouchStopEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleTouchScaleEvent(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float) {
            // NOP.
        }

        override fun handleTouchRotateEvent(degreeVsOrigin: Float, degreeVsLast: Float) {
            // NOP.
        }
    }

    private inner class DoubleMove(point0: Point, point1: Point) : InteractionState {
        init {
            // Start scale and rotate detection.
            doubleTouchScaleAndRotateDetector.startScaleAndRotateDetection(
                    PointF(point0),
                    PointF(point1))
        }

        override fun handleMotionEvent(motion: MotionEvent) {
            when (motion.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (motion.pointerCount == 2) {
                        // Update scale and rotate detection.
                        doubleTouchScaleAndRotateDetector.updateCurrentPosition(
                                PointF(motion.getX(0), motion.getY(0)),
                                PointF(motion.getX(1), motion.getY(1)))

                        callback?.onDoubleMoved(
                                Point(motion.getX(0).toInt(), motion.getY(0).toInt()),
                                Point(motion.getX(1).toInt(), motion.getY(1).toInt()))
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    callback?.onDoubleCanceled()
                    changeTo(Idle())
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Stop touch scale and rotate detection.
                    doubleTouchScaleAndRotateDetector.stopScaleAndRotateDetection()
                    changeTo(OverTriple())
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    // Stop touch scale and rotate detection.
                    doubleTouchScaleAndRotateDetector.stopScaleAndRotateDetection()

                    val release = motion.actionIndex
                    val remain: Int
                    remain = if (release == 0) {
                        1
                    } else {
                        0
                    }
                    callback?.onSingleReleasedInDouble(
                            Point(motion.getX(release).toInt(), motion.getY(release).toInt()),
                            Point(motion.getX(remain).toInt(), motion.getY(remain).toInt()))

                    // Reset internal fields.
                    singleTouchMoveAndStopDetector.updateCurrentAndLastPosition(
                            motion.getX(remain).toInt(),
                            motion.getY(remain).toInt())

                    changeTo(SingleMove())
                }
            }
        }

        override fun handleSingleTouchMoveEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleSingleTouchStopEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleTouchScaleEvent(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float) {
            callback?.onDoubleScaled(currentLength, previousLength, originalLength)
        }

        override fun handleTouchRotateEvent(degreeVsOrigin: Float, degreeVsLast: Float) {
            callback?.onDoubleRotated(degreeVsOrigin, degreeVsLast)
        }
    }

    private inner class OverTriple : InteractionState {
        override fun handleMotionEvent(motion: MotionEvent) {
            when (motion.actionMasked) {
                MotionEvent.ACTION_CANCEL -> {
                    callback?.onOverTripleCanceled()
                    changeTo(Idle())
                }

                MotionEvent.ACTION_POINTER_UP -> if (motion.pointerCount == 3) {
                    changeTo(DoubleDown())
                }

                else -> {
                    // NOP.
                }
            }
        }

        override fun handleSingleTouchMoveEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleSingleTouchStopEvent(
                currentPoint: Point,
                lastPoint: Point,
                downPoint: Point) {
            // NOP.
        }

        override fun handleTouchScaleEvent(
                currentLength: Float,
                previousLength: Float,
                originalLength: Float) {
            // NOP.
        }

        override fun handleTouchRotateEvent(degreeVsOrigin: Float, degreeVsLast: Float) {
            // NOP.
        }
    }

    /**
     * Input touch event to interaction engine.
     *
     * @param motion Motion event.
     * @return Touch event is occupied or not.
     */
    @Synchronized
    fun onTouchEvent(motion: MotionEvent): Boolean {
        // Check touch area.
        for (i in 0 until motion.pointerCount) {
            if (hitTest(targetView, margin, motion.getX(i).toInt(), motion.getY(i).toInt())) {
                isAllTouchEventInTargetArea = true
            } else {
                // Touch point is out of target area.
                isAllTouchEventInTargetArea = false
                break
            }
        }

        // Switch.
        when (motion.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Start touch stop detection.
                singleTouchMoveAndStopDetector.startTouchStopDetection(
                        motion.x.toInt(), motion.y.toInt())
            }

            MotionEvent.ACTION_MOVE -> {
                if (motion.pointerCount == 1) {
                    // Update touch stop detector.
                    singleTouchMoveAndStopDetector.updateCurrentPosition(motion.x.toInt(), motion.y.toInt())
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Stop touch stop detection.
                singleTouchMoveAndStopDetector.stopTouchStopDetection()
            }

            else -> {
                // NOP.
            }
        }

        // Handle.
        currentInteractionState.handleMotionEvent(motion)

        // Android gesture detector.
        androidGestureDetector.onTouchEvent(motion)

        return isAllTouchEventInTargetArea
    }

    /**
     * Cancel interaction detection.
     */
    fun cancel() {
        changeTo(Idle())
    }

    private fun hitTest(targetView: View, margin: Int, xOnView: Int, yOnView: Int): Boolean {
        // Hit area is inside the target view without touch area margin.
        val hitRect = Rect(
                margin,
                margin,
                targetView.width - margin,
                targetView.height - margin)

        return hitRect.contains(xOnView, yOnView)
    }

    @Synchronized
    override fun onSingleTouchMoveDetected(
            currentPoint: Point,
            lastPoint: Point,
            downPoint: Point) {
        currentInteractionState.handleSingleTouchMoveEvent(currentPoint, lastPoint, downPoint)
    }

    @Synchronized
    override fun onSingleTouchStopDetected(
            currentPoint: Point,
            lastPoint: Point,
            downPoint: Point) {
        currentInteractionState.handleSingleTouchStopEvent(currentPoint, lastPoint, downPoint)
    }

    @Synchronized
    override fun onDoubleTouchScaleDetected(
            currentLength: Float,
            previousLength: Float,
            originalLength: Float) {
        currentInteractionState.handleTouchScaleEvent(currentLength, previousLength, originalLength)
    }

    @Synchronized
    override fun onDoubleTouchRotateDetected(
            degreeVsOrigin: Float,
            degreeVsPrevious: Float) {
        currentInteractionState.handleTouchRotateEvent(degreeVsOrigin, degreeVsPrevious)
    }

    @Synchronized
    override fun onDown(event: MotionEvent): Boolean = true

    @Synchronized
    override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocX: Float,
            velocY: Float): Boolean {
        // TODO: Handle nullable one.
        callback?.onFling(event1 as MotionEvent, event2, velocX, velocY)
        return true
    }

    @Synchronized
    override fun onLongPress(event: MotionEvent) {
        callback?.onLongPress(event)
    }

    @Synchronized
    override fun onScroll(
            event1: MotionEvent?,
            event2: MotionEvent,
            distanceX: Float,
            distanceY: Float): Boolean = true

    @Synchronized
    override fun onShowPress(event: MotionEvent) {
        callback?.onShowPress(event)
    }

    @Synchronized
    override fun onSingleTapUp(event: MotionEvent): Boolean {
        callback?.onSingleTapUp(event)
        return true
    }

    companion object {
        private const val TAG = "TouchActionTranslator"
    }
}
