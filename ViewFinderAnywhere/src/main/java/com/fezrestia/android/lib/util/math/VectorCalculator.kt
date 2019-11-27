package com.fezrestia.android.lib.util.math

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.acos

/**
 * This class is used to calculate vector.
 */
object VectorCalculator {
    // Direction threshold.
    private const val RIGHT_ANGLE = Math.PI.toFloat() / 2.0f
    private const val RIGHT_ANGLE_TOLERANCE = Math.PI.toFloat() / 3.0f
    private const val PARALLEL_ANGLE_INVERSE_DIRECTION = Math.PI.toFloat()
    private const val PARALLEL_ANGLE_FORWARD_DIRECTION = 0.0f
    private const val PARALLEL_ANGLE_TOLERANCE = Math.PI.toFloat() / 3.0f

    fun getRadianFrom2Vector(vec0: PointF, vec1: PointF): Float {
        if (0 < vec0.length() && 0 < vec1.length()) {
            var difCos = (vec0.x * vec1.x + vec0.y * vec1.y) / vec0.length() / vec1.length()

            // Check limit.
            if (difCos < -1.0f) {
                difCos = -1.0f
            } else if (1.0f < difCos) {
                difCos = 1.0f
            }

            // Convert cos to radian
            return acos(difCos.toDouble()).toFloat()
        }

        // If length is 0, can not calculate.
        return 0.0f
    }

    fun isSquare(vec0: PointF, vec1: PointF): Boolean {
        val rad = getRadianFrom2Vector(vec0, vec1)

        return RIGHT_ANGLE - RIGHT_ANGLE_TOLERANCE < rad && rad < RIGHT_ANGLE + RIGHT_ANGLE_TOLERANCE
    }

    fun isParallel(vec0: PointF, vec1: PointF): Boolean {
        val rad = getRadianFrom2Vector(vec0, vec1)

        val isInverseParallel = isNearlyEquals(PARALLEL_ANGLE_INVERSE_DIRECTION, rad)
        val isForwardParallel = isNearlyEquals(PARALLEL_ANGLE_FORWARD_DIRECTION, rad)

        return isInverseParallel || isForwardParallel
    }

    private fun isNearlyEquals(target: Float, actual: Float): Boolean
            = abs(target - actual) < PARALLEL_ANGLE_TOLERANCE
}
