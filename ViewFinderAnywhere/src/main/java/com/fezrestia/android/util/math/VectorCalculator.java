package com.fezrestia.android.util.math;

import android.graphics.PointF;

/**
 * This class is used to calculate vector.
 */
public class VectorCalculator {
    // Direction threshold.
    private static final float RIGHT_ANGLE = (float) Math.PI / 2.0f;
    private static final float RIGHT_ANGLE_TOLERANCE = (float) Math.PI / 3.0f;
    private static final float PARALLEL_ANGLE_INVERSE_DIRECTION = (float) Math.PI;
    private static final float PARALLEL_ANGLE_FORWARD_DIRECTION = 0.0f;
    private static final float PARALLEL_ANGLE_TOLERANCE = (float) Math.PI / 3.0f;

    public static float getRadianFrom2Vector(PointF vec0, PointF vec1) {
        if ((0 < vec0.length()) && (0 < vec1.length())) {
            float difCos = (vec0.x * vec1.x + vec0.y * vec1.y) / vec0.length() / vec1.length();

            // Check limit.
            if (difCos < -1.0f) {
                difCos = -1.0f;
            } else if (1.0f < difCos) {
                difCos = 1.0f;
            }

            // Convert cos to radian
            return (float) Math.acos(difCos);
        }

        // If length is 0, can not calculate.
        return 0.0f;
    }

    public static boolean isSquare(PointF vec0, PointF vec1) {
        float rad = getRadianFrom2Vector(vec0, vec1);

        if ((RIGHT_ANGLE - RIGHT_ANGLE_TOLERANCE < rad)
                && rad < RIGHT_ANGLE + RIGHT_ANGLE_TOLERANCE) {
            return true;
        }

        return false;
    }

    public static boolean isParallel(PointF vec0, PointF vec1) {
        float rad = getRadianFrom2Vector(vec0, vec1);

        if (isNearlyEquals(
                PARALLEL_ANGLE_INVERSE_DIRECTION, rad, PARALLEL_ANGLE_TOLERANCE)) {
            // Inverse direction.
            return true;
        } else if (isNearlyEquals(
                PARALLEL_ANGLE_FORWARD_DIRECTION, rad, PARALLEL_ANGLE_TOLERANCE)) {
            // Forward direction.
            return true;
        }

        return false;
    }

    private static boolean isNearlyEquals(float target, float actual, float tolerance) {
        return (Math.abs(target - actual) < tolerance);
    }
}
