#include <TraceLog.hpp>

namespace fezrestia {

    const bool IS_ENABLED = false;

    void log(const std::string& level, const std::string& tag, const std::string& msg) {
        char name[32];
        pthread_getname_np(pthread_self(), name, sizeof(name));

        timespec ts{};
        clock_gettime(CLOCK_MONOTONIC, &ts);

        __android_log_print(
                ANDROID_LOG_ERROR,
                "TraceLog",
                "%s %ld [%s] %s : %s",
                level.c_str(),
                ts.tv_sec * 1000 + ts.tv_nsec / 1000 / 1000,
                name,
                tag.c_str(),
                msg.c_str());
    }

    #pragma clang diagnostic push
    #pragma ide diagnostic ignored "OCSimplifyInspection"
    #pragma ide diagnostic ignored "OCDFAInspection"
    #pragma ide diagnostic ignored "UnreachableCode"
    void TraceLog(const std::string& tag, const std::string& msg) {
        if (IS_ENABLED) {
            fezrestia::log("DBG", tag, msg);
        }
    }
    #pragma clang diagnostic pop

    void LogE(const std::string& tag, const std::string& msg) {
        fezrestia::log("ERR", tag, msg);
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
