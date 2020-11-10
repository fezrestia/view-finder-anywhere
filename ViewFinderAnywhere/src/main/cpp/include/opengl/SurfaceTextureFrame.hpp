#ifndef VIEW_FINDER_ANYWHERE_SURFACETEXTUREFRAME_HPP
#define VIEW_FINDER_ANYWHERE_SURFACETEXTUREFRAME_HPP

#include <string>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include "TraceLog.hpp"
#include "opengl/ElementBase.hpp"
#include "opengl/util/opengl_matrix.hpp"

namespace fezrestia {

    class SurfaceTextureFrame : public ElementBase {

    public:
        /**
         * CONSTRUCTOR.
         */
        SurfaceTextureFrame() = default;

        /**
         * DESTRUCTOR.
         */
        ~SurfaceTextureFrame() = default;

        /**
         * Initialize.
         */
        void initialize(float screenNormWidth, float screenNormHeight);

        /**
         * Finalize.
         */
        void finalize();

        /**
         * Set texture ID.
         */
        void setTextureId(GLuint textureId);

        /**
         * Set texture transform matrix.
         */
        void setTextureTransformMatrix(const GLfloat* matrix4x4);

        /**
         * Set shader program index.
         */
        void setShaderProgram(GLuint shaderProgram);

        /**
         * Set alpha channel.
         */
        void setAlpha(GLfloat alpha);

        /**
         * GL call for render.
         */
        void render();

    private:
        //// MEMBER FIELDS ///////////////////////////////////////////////////////////////////////////

        // Shader.
        GLuint mShaderProgram;

        // Texture.
        GLuint mTextureId[1];
        GLfloat mTextureTransformMatrix[16];

        // GLSL index.
        GLuint mGLSL_aVertex;
        GLuint mGLSL_aTexCoord;
        GLuint mGLSL_uMvpMatrix;
        GLuint mGLSL_uOesTexMatrix;
        GLuint mGLSL_uAlpha;

        // Buffers.
        GLuint mVertexBuffer;
        GLuint mTexCoordBuffer;

        // Alpha.
        GLfloat mAlpha;

        //// FUNCTIONS ///////////////////////////////////////////////////////////////////////////////

        GLuint enableLocalFunctions();

        GLuint enableShaderProgram();

        GLuint disableLocalFunctions();

        void doRender();

        void initializeShaderProgram();

        void initializeVertexAndTextureCoordinatesBuffer();

        void finalizeShaderProgram();

        void finalizeVertexAndTextureCoordinatesBuffer();
    };

} // namespace fezrestia

#endif // VIEW_FINDER_ANYWHERE_SURFACETEXTUREFRAME_HPP
