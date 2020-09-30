#include "ElementBase.hpp"

#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"

namespace fezrestia {

    const std::string TAG = "ElementBase";

    /**
     * Initialize.
     */
    void ElementBase::initialize(float screenNormWidth, float screenNormHeight) {
        mIsVisible = GL_TRUE;

        mScreenNormWidth = screenNormWidth;
        mScreenNormHeight = screenNormHeight;

        // Initialize matrix.
        Matrix4x4_SetIdentity(mGlobalMatrix);
        Matrix4x4_SetIdentity(mSequencedLocalMatrix);
    }

    /**
     * Finalize.
     */
    void ElementBase::finalize() {
        // NOP.
    }

    /**
     * Update global position / posture matrix
     */
    void ElementBase::setGlobalMatrix(const float *matrix) {
        // Copy.
        for (int i = 0; i < MATRIX_4x4_LENGTH; ++i) {
            mGlobalMatrix[i] = matrix[i];
        }

        // Reset local matrix.
        Matrix4x4_SetIdentity(mSequencedLocalMatrix);
    }

    /**
     * Set visibility.
     */
    void ElementBase::setVisibility(GLboolean isVisible) {
        mIsVisible = isVisible;
    }

    /**
     * This element is visible or not.
     */
    GLboolean ElementBase::isVisible() {
        return mIsVisible;
    }

    /**
     * Normalized screen width.
     */
    float ElementBase::getScreenNormWidth() {
        return mScreenNormWidth;
    }

    /**
     * Normalized screen height.
     */
    float ElementBase::getScreenNormHeight() {
        return mScreenNormHeight;
    }

    /**
     * Global matrix.
     */
    float *ElementBase::getGlobalMatrix() {
        return mGlobalMatrix;
    }

    /**
     * Local matrix.
     */
    float *ElementBase::getSequencedLocalMatrix() {
        return mSequencedLocalMatrix;
    }

    /**
     * Translate this element and all children.
     * Call this after setGlobalMatrix().
     */
    void ElementBase::translate(float transX, float transY, float transZ) {
        float translate[16];
        Matrix4x4_SetIdentity(translate);
        Matrix4x4_Translate(translate, transX, transY, transZ);

        Matrix4x4_Multiply(mSequencedLocalMatrix, translate, mSequencedLocalMatrix);
    }

    /**
     * Rotate this element and all children.
     * Call this after setGlobalMatrix().
     */
    void ElementBase::rotate(float rotDeg, float axisX, float axisY, float axisZ) {
        float rotate[16];
        Matrix4x4_SetIdentity(rotate);
        Matrix4x4_Rotate(rotate, rotDeg, axisX, axisY, axisZ);

        Matrix4x4_Multiply(mSequencedLocalMatrix, rotate, mSequencedLocalMatrix);
    }

    /**
     * Scale this element and all children.
     * Call this after setGlobalMatrix().
     */
    void ElementBase::scale(float scaleX, float scaleY, float scaleZ) {
        float scale[16];
        Matrix4x4_SetIdentity(scale);
        Matrix4x4_Scale(scale, scaleX, scaleY, scaleZ);

        Matrix4x4_Multiply(mSequencedLocalMatrix, scale, mSequencedLocalMatrix);
    }

} // namespace fezrestia
