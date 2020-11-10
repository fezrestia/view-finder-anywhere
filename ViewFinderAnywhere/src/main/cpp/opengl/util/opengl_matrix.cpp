#pragma ide diagnostic ignored "UnusedGlobalDeclarationInspection"

#include <opengl/util/opengl_matrix.hpp>

#define PI 3.14159f

#define NORMALIZE(x, y, z)                              \
{                                                       \
    float norm = 1.0f / sqrt(x * x + y * y + z * z);    \
    x *= norm;                                          \
    y *= norm;                                          \
    z *= norm;                                          \
}

// Get float[] index in 4x4 matrix.
#define INDEX(_i, _j) ((_j) + 4 * (_i))

// 4x4 matrix identity.
void Matrix4x4_SetIdentity(float* matrix) {
    int i;

    // Reset.
    for (i = 0; i < MATRIX_4x4_LENGTH; ++i) {
        matrix[i] = 0.0f;
    }

    // Set identity.
    matrix[ 0] = 1.0f;
    matrix[ 5] = 1.0f;
    matrix[10] = 1.0f;
    matrix[15] = 1.0f;
}

// Set view matrix.
void Matrix4x4_SetEyeView(
        float* matrix,
        float eyeX, float eyeY, float eyeZ,
        float cenX, float cenY, float cenZ,
        float  upX, float  upY, float  upZ) {
    float fx = cenX - eyeX;
    float fy = cenY - eyeY;
    float fz = cenZ - eyeZ;
    NORMALIZE(fx, fy, fz)

    float sx = fy * upZ - fz * upY;
    float sy = fz * upX - fx * upZ;
    float sz = fx * upY - fy * upX;
    NORMALIZE(sx, sy, sz)

    float ux = sy * fz - sz * fy;
    float uy = sz * fx - sx * fz;
    float uz = sx * fy - sy * fx;

    matrix[ 0] = sx;
    matrix[ 1] = ux;
    matrix[ 2] = -fx;
    matrix[ 3] = 0.0f;
    matrix[ 4] = sy;
    matrix[ 5] = uy;
    matrix[ 6] = -fy;
    matrix[ 7] = 0.0f;
    matrix[ 8] = sz;
    matrix[ 9] = uz;
    matrix[10] = -fz;
    matrix[11] = 0.0f;
    matrix[12] = 0.0f;
    matrix[13] = 0.0f;
    matrix[14] = 0.0f;
    matrix[15] = 1.0f;

    Matrix4x4_Translate(matrix, -eyeX, -eyeY, -eyeZ);
}

// Set parallel projection matrix.
void Matrix4x4_SetParallelProjection(
        float* matrix,
        float left, float right, float bottom, float top, float near, float far) {
    int i;

    float r_width  = 1.0f / (right - left);
    float r_height = 1.0f / (top - bottom);
    float r_depth  = 1.0f / (far - near);
    float x =  2.0f * (r_width);
    float y =  2.0f * (r_height);
    float z = -2.0f * (r_depth);
    float tx = -(right + left) * r_width;
    float ty = -(top + bottom) * r_height;
    float tz = -(far + near) * r_depth;

    // Clear.
    for (i = 0; i < MATRIX_4x4_LENGTH; ++i) {
        matrix[i] = 0.0f;
    }

    matrix[ 0] = x;
    matrix[ 5] = y;
    matrix[10] = z;
    matrix[12] = tx;
    matrix[13] = ty;
    matrix[14] = tz;
    matrix[15] = 1.0f;
}

// Set frustum projection matrix.
void Matrix4x4_SetFrustumProjection(
        float* matrix,
        float left, float right, float bottom, float top, float near, float far) {
    int i;

    float r_width  = 1.0f / (right - left);
    float r_height = 1.0f / (top - bottom);
    float r_depth  = 1.0f / (near - far);
    float x = 2.0f * (near * r_width);
    float y = 2.0f * (near * r_height);
    float A = 2.0f * ((right+left) * r_width);
    float B = (top + bottom) * r_height;
    float C = (far + near) * r_depth;
    float D = 2.0f * (far * near * r_depth);

    // Clear.
    for (i = 0; i < MATRIX_4x4_LENGTH; ++i) {
        matrix[i] = 0.0f;
    }

    // Set.
    matrix[ 0] = x;
    matrix[ 5] = y;
    matrix[ 8] = A;
    matrix[ 9] = B;
    matrix[10] = C;
    matrix[14] = D;
    matrix[11] = -1.0f;
}

// Multiply matrix.
void Matrix4x4_Multiply(float* dstMat, const float* srcMatLeft, const float* srcMatRight) {
    int i, j;
    float t[16];

    for (i = 0; i < MATRIX_4x4_ROW; ++i) {
        const float srcMatRight_i0 = srcMatRight[INDEX(i, 0)];
        float ri0 = srcMatLeft[INDEX(0,0)] * srcMatRight_i0;
        float ri1 = srcMatLeft[INDEX(0,1)] * srcMatRight_i0;
        float ri2 = srcMatLeft[INDEX(0,2)] * srcMatRight_i0;
        float ri3 = srcMatLeft[INDEX(0,3)] * srcMatRight_i0;
        for (j = 1; j < MATRIX_4x4_COLUMN; j++) {
            const float srcMatRight_ij = srcMatRight[INDEX(i,j)];
            ri0 += srcMatLeft[INDEX(j,0)] * srcMatRight_ij;
            ri1 += srcMatLeft[INDEX(j,1)] * srcMatRight_ij;
            ri2 += srcMatLeft[INDEX(j,2)] * srcMatRight_ij;
            ri3 += srcMatLeft[INDEX(j,3)] * srcMatRight_ij;
        }
        t[INDEX(i,0)] = ri0;
        t[INDEX(i,1)] = ri1;
        t[INDEX(i,2)] = ri2;
        t[INDEX(i,3)] = ri3;
    }

    // Copy to destination.
    for (i = 0; i < MATRIX_4x4_LENGTH; ++i) {
        dstMat[i] = t[i];
    }
}

// Translate matrix.
void Matrix4x4_Translate(float* matrix, float x, float y, float z) {
    int i;

    for (i = 0; i < MATRIX_4x4_ROW; ++i) {
        matrix[12 + i] += matrix[i] * x + matrix[4 + i] * y + matrix[8 + i] * z;
    }
}

// Rotate matrix.
void Matrix4x4_Rotate(float* matrix, float deg, float x, float y, float z) {
    matrix[3] = 0;
    matrix[7] = 0;
    matrix[11]= 0;
    matrix[12]= 0;
    matrix[13]= 0;
    matrix[14]= 0;
    matrix[15]= 1;
    float rad =  deg * PI / 180.0f;
    float s = sin(rad);
    float c = cos(rad);
    if (x == 1.0f && y == 0.0f && z == 0.0f) {
        matrix[5] = c;   matrix[10]= c;
        matrix[6] = s;   matrix[9] = -s;
        matrix[1] = 0;   matrix[2] = 0;
        matrix[4] = 0;   matrix[8] = 0;
        matrix[0] = 1;
    } else if (x == 0.0f && y == 1.0f && z == 0.0f) {
        matrix[0] = c;   matrix[10]= c;
        matrix[8] = s;   matrix[2] = -s;
        matrix[1] = 0;   matrix[4] = 0;
        matrix[6] = 0;   matrix[9] = 0;
        matrix[5] = 1;
    } else if (x == 0.0f && y == 0.0f && z == 1.0f) {
        matrix[0] = c;   matrix[5] = c;
        matrix[1] = s;   matrix[4] = -s;
        matrix[2] = 0;   matrix[6] = 0;
        matrix[8] = 0;   matrix[9] = 0;
        matrix[10]= 1;
    } else {
        float len = sqrt(x * x + y * y + z * z);
        if (len != 1.0f) {
            float recipLen = 1.0f / len;
            x *= recipLen;
            y *= recipLen;
            z *= recipLen;
        }
        float nc = 1.0f - c;
        float xy = x * y;
        float yz = y * z;
        float zx = z * x;
        float xs = x * s;
        float ys = y * s;
        float zs = z * s;
        matrix[ 0] = x*x*nc +  c;
        matrix[ 4] =  xy*nc - zs;
        matrix[ 8] =  zx*nc + ys;
        matrix[ 1] =  xy*nc + zs;
        matrix[ 5] = y*y*nc +  c;
        matrix[ 9] =  yz*nc - xs;
        matrix[ 2] =  zx*nc - ys;
        matrix[ 6] =  yz*nc + xs;
        matrix[10] = z*z*nc +  c;
    }
}

// Scale matrix.
void Matrix4x4_Scale(float* matrix, float x, float y, float z) {
    int i;

    for (i = 0; i < MATRIX_4x4_ROW; ++i) {
        matrix[    i] *= x;
        matrix[4 + i] *= y;
        matrix[8 + i] *= z;
    }
}
