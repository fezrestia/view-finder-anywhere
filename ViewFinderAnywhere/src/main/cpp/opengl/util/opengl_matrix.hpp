#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"

#ifndef VIEW_FINDER_ANYWHERE_OPENGL_MATRIX_HPP
#define VIEW_FINDER_ANYWHERE_OPENGL_MATRIX_HPP

#include <cmath>
#include <cstdlib>

// Length of float[]
#define MATRIX_4x4_LENGTH 16
#define MATRIX_4x4_COLUMN 4
#define MATRIX_4x4_ROW 4

// 4x4 matrix identity.
void Matrix4x4_SetIdentity(float* matrix);

// Set view matrix.
void Matrix4x4_SetEyeView(
        float* matrix,
        float eyeX, float eyeY, float eyeZ,
        float cenX, float cenY, float cenZ,
        float  upX, float  upY, float  upZ);

// Set parallel projection matrix.
void Matrix4x4_SetParallelProjection(
        float* matrix,
        float left, float right, float bottom, float top, float near, float far);

// Set frustum projection matrix.
void Matrix4x4_SetFrustumProjection(
        float* matrix,
        float left, float right, float bottom, float top, float near, float far);

// Multiply matrix.
void Matrix4x4_Multiply(float* dstMat, const float* srcMatLeft, const float* srcMatRight);

// Translate matrix.
void Matrix4x4_Translate(float* matrix, float x, float y, float z);

// Rotate matrix.
void Matrix4x4_Rotate(float* matrix, float deg, float x, float y, float z);

// Scale matrix.
void Matrix4x4_Scale(float* matrix, float x, float y, float z);

#endif //VIEW_FINDER_ANYWHERE_OPENGL_MATRIX_HPP
