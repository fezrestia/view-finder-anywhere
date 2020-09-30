#ifndef VIEW_FINDER_ANYWHERE_SHADERPROGRAMFACTORY_HPP
#define VIEW_FINDER_ANYWHERE_SHADERPROGRAMFACTORY_HPP

#include <string>
#include <sys/types.h>
#include <GLES2/gl2.h>

#include "TraceLog.hpp"

#define INVALID_PROGRAM 0

namespace fezrestia {

    class ShaderProgramFactory {

    public:
        // Shader program target.
        enum ShaderType {
            ShaderType_SURFACE_TEXTURE,

            ShaderType_MAX
        };

        // CONSTRUCTOR.
        ShaderProgramFactory() = default;

        // DESTRUCTOR.
        ~ShaderProgramFactory() = default;

        /**
         * Initialize.
         */
        void Initialize();

        /**
         * Finalize.
         */
        void Finalize();

        /**
         * Get shader program according to type.
         */
        GLuint CreateShaderProgram(
                ShaderType type,
                const char *vertexShaderCode,
                size_t vertexShaderCodeLen,
                const char *fragmentShaderCode,
                size_t fragmentShaderCodeLen);

    private:

        GLuint mShaderPrograms[ShaderType_MAX] {};

        static GLuint createProgram(
                GLchar *vertexSource,
                GLsizei vertexSourceLength,
                GLchar *fragmentSource,
                GLsizei fragmentSourceLength);

        static GLuint loadShader(
                GLenum shaderType,
                const GLchar *source,
                GLsizei sourceLength);

        static void destroyProgram(GLuint program);

    };

} // namespace fezrestia

#endif //VIEW_FINDER_ANYWHERE_SHADERPROGRAMFACTORY_HPP
