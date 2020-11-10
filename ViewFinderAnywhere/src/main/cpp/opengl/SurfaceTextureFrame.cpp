#include <opengl/SurfaceTextureFrame.hpp>

namespace fezrestia {

    const std::string TAG = "SurfaceTextureFrame";

    void SurfaceTextureFrame::initialize(float screenNormWidth, float screenNormHeight) {
        // Super.
        ElementBase::initialize(screenNormWidth, screenNormHeight);
    }

    void SurfaceTextureFrame::finalize() {
        // Super.
        ElementBase::finalize();

        // Delete shader programs.
        finalizeShaderProgram();
    }

    void SurfaceTextureFrame::setTextureId(GLuint textureId) {
        mTextureId[0] = textureId;
    }

    void SurfaceTextureFrame::setTextureTransformMatrix(const GLfloat* matrix4x4) {
        for (int i = 0; i < 16; ++i) {
            mTextureTransformMatrix[i] = matrix4x4[i];
        }
    }

    void SurfaceTextureFrame::setShaderProgram(GLuint shaderProgram) {
        // Setup shader.
        mShaderProgram = shaderProgram;

        // Create and initialize shader and program.
        initializeShaderProgram();
    }
    void SurfaceTextureFrame::setAlpha(GLfloat alpha) {
        mAlpha = alpha;
    }

    void SurfaceTextureFrame::render() {
        if (!isVisible()) {
            // Do not render.
            return;
        }

        if (!enableLocalFunctions()) {
            LogE(TAG, "render() : Enable functions failed.");
            return;
        }

        doRender();

        if (!disableLocalFunctions()) {
            LogE(TAG, "render() : Disable functions failed.");
            return;
        }
    }

    GLuint SurfaceTextureFrame::enableLocalFunctions() {
        // Vertex / Texture for background.
        glEnableVertexAttribArray(mGLSL_aVertex);
        glEnableVertexAttribArray(mGLSL_aTexCoord);

        // Enable shader.
        if (!enableShaderProgram()) {
            LogE(TAG, "enableFunctions() : Enable shader program failed.");
            return GL_FALSE;
        }

        // No error.
        return GL_TRUE;
    }

    GLuint SurfaceTextureFrame::enableShaderProgram() {
        // Install program object to GL renderer and validate.
        if (mShaderProgram == 0) {
            LogE(TAG, "enableShaderProgram() : Program is Invalid");
            return GL_FALSE;
        }
        glUseProgram(mShaderProgram);
        glValidateProgram(mShaderProgram);

        if (checkGlError(TAG) != GL_NO_ERROR) {
            LogE(TAG, "enableShaderProgram() : Program Error");
            return GL_FALSE;
        }

        return GL_TRUE;
    }

    GLuint SurfaceTextureFrame::disableLocalFunctions() {
        // Vertex / Texture for background.
        glDisableVertexAttribArray(mGLSL_aVertex);
        glDisableVertexAttribArray(mGLSL_aTexCoord);

        // Unset program.
        glUseProgram(0);

        // No error.
        return GL_TRUE;
    }

    void SurfaceTextureFrame::doRender() {
        TraceLog(TAG, "doRender() : E");

        // Register vertex.
        glBindBuffer(GL_ARRAY_BUFFER, mVertexBuffer);
        glVertexAttribPointer(
                mGLSL_aVertex,
                3,
                GL_FLOAT,
                GL_FALSE,
                0,
                nullptr);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Register tex-coord.
        glBindBuffer(GL_ARRAY_BUFFER, mTexCoordBuffer);
        glVertexAttribPointer(
                mGLSL_aTexCoord,
                2,
                GL_FLOAT,
                GL_FALSE,
                0,
                nullptr);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Activate and bind texture.
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureId[0]);

        if (checkGlError(TAG) != GL_NO_ERROR) {
            LogE(TAG, "doRender() : Bind textures Error");
            return;
        }

        // Link alpha channel.
        glUniform1f(mGLSL_uAlpha, mAlpha);

        // MVP matrix.
        float mvpMatrix[16];
        Matrix4x4_SetIdentity(mvpMatrix);
        // Global x Local matrix.
        Matrix4x4_Multiply(mvpMatrix, getSequencedLocalMatrix(), mvpMatrix);
        Matrix4x4_Multiply(mvpMatrix, getGlobalMatrix(), mvpMatrix);
        glUniformMatrix4fv(
                mGLSL_uMvpMatrix, // Location
                1, // Length
                GL_FALSE,
                mvpMatrix);
        glUniformMatrix4fv(
                mGLSL_uOesTexMatrix,
                1,
                GL_FALSE,
                mTextureTransformMatrix);

        // Render.
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        // Release.
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

        checkGlError(TAG);

        TraceLog(TAG, "doRender() : X");
    }

    void SurfaceTextureFrame::initializeShaderProgram() {
        TraceLog(TAG, "initializeShaderProgram() : E");

        // Link vertex information with field of shader source codes.
        mGLSL_aVertex = glGetAttribLocation(mShaderProgram, "aVertex");
        // Link texture information with field of shader source codes.
        mGLSL_aTexCoord = glGetAttribLocation(mShaderProgram, "aTexCoord");
        // Link MVP matrix.
        mGLSL_uMvpMatrix = glGetUniformLocation(mShaderProgram, "uMvpMatrix");
        // Link OES texture matrix.
        mGLSL_uOesTexMatrix = glGetUniformLocation(mShaderProgram, "uOesTexMatrix");
        // Link alpha.
        mGLSL_uAlpha = glGetUniformLocation(mShaderProgram, "uAlpha");

        checkGlError(TAG);

        // Create vertex and texture coordinates buffer objects.
        initializeVertexAndTextureCoordinatesBuffer();

        TraceLog(TAG, "initializeShaderProgram() : X");
    }

    void SurfaceTextureFrame::initializeVertexAndTextureCoordinatesBuffer() {
        const GLuint vertexBufLen = 12;
        const GLuint texCoordBufLen = 8;

        float vertex[vertexBufLen] = {
                - getScreenNormWidth() / 2.0f,    getScreenNormHeight() / 2.0f, 0.0f, // Left-Top
                - getScreenNormWidth() / 2.0f,  - getScreenNormHeight() / 2.0f, 0.0f, // Left-Bottom
                getScreenNormWidth() / 2.0f,    getScreenNormHeight() / 2.0f, 0.0f, // Right-Top
                getScreenNormWidth() / 2.0f,  - getScreenNormHeight() / 2.0f, 0.0f, // Right-Bottom
        };
        float texCoord[texCoordBufLen] = {
                // Up side down.
                0.0f,   1.0f, // Left-Top
                0.0f,   0.0f, // Left-Bottom
                1.0f,   1.0f, // Right-Top
                1.0f,   0.0f, // Right-Bottom
        };

        // Create buffer object.
        glGenBuffers(1, &mVertexBuffer);
        glGenBuffers(1, &mTexCoordBuffer);

        // Bind and write vertex.
        glBindBuffer(GL_ARRAY_BUFFER, mVertexBuffer);
        glBufferData(
                GL_ARRAY_BUFFER,
                sizeof(GLfloat) * vertexBufLen,
                vertex,
                GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        // Bind and write tex-coord.
        glBindBuffer(GL_ARRAY_BUFFER, mTexCoordBuffer);
        glBufferData(
                GL_ARRAY_BUFFER,
                sizeof(GLfloat) * texCoordBufLen,
                texCoord,
                GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        checkGlError(TAG);
    }

    void SurfaceTextureFrame::finalizeShaderProgram() {
        mShaderProgram = 0;

        // Delete vertex and texture coordinates buffer objects.
        finalizeVertexAndTextureCoordinatesBuffer();
    }

    void SurfaceTextureFrame::finalizeVertexAndTextureCoordinatesBuffer() {
        // Delete buffer objects.
        glDeleteBuffers(
                1, // Size
                &mVertexBuffer); // Buffer
        glDeleteBuffers(
                1, // Size
                &mTexCoordBuffer); // Buffer
    }

} // namespace fezrestia
