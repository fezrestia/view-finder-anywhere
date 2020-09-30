#include "TraceLog.hpp"

namespace fezrestia {

    const bool IS_ENABLED = false;

    #pragma clang diagnostic push
    #pragma ide diagnostic ignored "OCSimplifyInspection"
    #pragma ide diagnostic ignored "OCDFAInspection"
    void TraceLog(const std::string& tag, const std::string& msg) {
        if (IS_ENABLED) {
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    "TraceLog",
                    "[CLK/1000=%d] [%s] %s",
                    (int32_t) clock() / 1000,
                    tag.c_str(),
                    msg.c_str());
        }
    }
    #pragma clang diagnostic pop

    void LogE(const std::string& tag, const std::string& msg) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                "TraceLog",
                "ERROR: [%s] %s",
                tag.c_str(),
                msg.c_str());
    }

    GLenum checkGlError(const std::string& tag) {
        GLenum error = glGetError();

        switch (error) {
            case GL_INVALID_ENUM:
                LogE(tag, "GL ERROR : GL_INVALID_ENUM");
                break;

            case GL_INVALID_VALUE:
                LogE(tag, "GL ERROR : GL_INVALID_VALUE");
                break;

            case GL_INVALID_OPERATION:
                LogE(tag, "GL ERROR : GL_INVALID_OPERATION");
                break;

            case GL_INVALID_FRAMEBUFFER_OPERATION:
                LogE(tag, "GL ERROR : GL_INVALID_FRAMEBUFFER_OPERATION");
                break;

            case GL_OUT_OF_MEMORY:
                LogE(tag, "GL ERROR : GL_OUT_OF_MEMORY");
                break;

            case GL_NO_ERROR:
                // NOP.
                break;

            default:
                LogE(tag, "GL ERROR : UNEXPECTED");
                break;
        }

        return error;
    }

} // namespace fezrestia
