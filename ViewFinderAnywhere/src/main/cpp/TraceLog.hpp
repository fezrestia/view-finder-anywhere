#ifndef VIEW_FINDER_ANYWHERE_TRACELOG_HPP
#define VIEW_FINDER_ANYWHERE_TRACELOG_HPP

#include <ctime>
#include <string>

#include <GLES2/gl2.h>

#include <android/log.h>

namespace fezrestia {

    void TraceLog(const std::string& tag, const std::string& msg);

    void LogE(const std::string& tag, const std::string& msg);

    GLenum checkGlError(const std::string& tag);

} // namespace fezrestia

#endif // VIEW_FINDER_ANYWHERE_TRACELOG_HPP
