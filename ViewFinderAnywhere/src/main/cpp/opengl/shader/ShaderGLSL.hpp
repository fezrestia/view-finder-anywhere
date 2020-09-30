#ifndef VIEW_FINDER_ANYWHERE_SHADERGLSL_CPP
#define VIEW_FINDER_ANYWHERE_SHADERGLSL_CPP

#include <string>

namespace fezrestia {

    namespace GLSL {

        const std::string SURFACE_TEXTURE_FRAME_FRAGMENT = R"(
            #extension GL_OES_EGL_image_external : require

            precision mediump float;

            uniform samplerExternalOES uTextureOes;

            varying vec2 vTexCoordHandler;

            uniform float uAlpha;

            void main()
            {
                vec4 color = texture2D(uTextureOes, vTexCoordHandler);

                color.a = uAlpha;

                gl_FragColor = color;
            }
        )";

        const std::string SURFACE_TEXTURE_FRAME_VERTEX = R"(
            attribute vec4 aVertex;
            attribute vec4 aTexCoord;

            uniform mat4 uMvpMatrix;
            uniform mat4 uOesTexMatrix;

            varying vec2 vTexCoordHandler;

            void main()
            {
                gl_Position = uMvpMatrix * aVertex;

                vec4 totalMatrix = uOesTexMatrix * aTexCoord;
                vTexCoordHandler = totalMatrix.xy;
            }
        )";

    } // namespace GLSL

} // namespace fezrestia

#endif //VIEW_FINDER_ANYWHERE_SHADERGLSL_CPP
