#ifndef VIEW_FINDER_ANYWHERE_VIEWFINDERANYWHERE_HPP
#define VIEW_FINDER_ANYWHERE_VIEWFINDERANYWHERE_HPP

#include <jni.h>
#include <cmath>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <android/native_window.h>
#include <android/native_window_jni.h>

#include "TraceLog.hpp"
#include "opengl/SurfaceTextureFrame.hpp"
#include "opengl/shader/ShaderGLSL.hpp"
#include "opengl/shader/ShaderProgramFactory.hpp"
#include "opengl/util/opengl_matrix.hpp"

namespace fezrestia {

//    static JavaVM* gVm;

    static ANativeWindow* gUiNativeWindow;
    static ANativeWindow* gEncoderNativeWindow;

    static EGLDisplay gAppEglDisplay;
    static EGLConfig gAppEglConfig;
    static EGLContext gAppEglContext;

    static EGLSurface gEglSurfaceUi;
    EGLint gSurfaceUiWidth;
    EGLint gSurfaceUiHeight;

    static EGLDisplay gEncoderEglDisplay;
    static EGLConfig gEncoderEglConfig;
    static EGLContext gEncoderEglContext;

    static EGLSurface gEglSurfaceEncoder;
    EGLint gSurfaceEncoderWidth;
    EGLint gSurfaceEncoderHeight;

    bool gIsVideoEncoding = false;

    float gCameraStreamAspectWH = 1.0;
    int gCameraStreamRotDeg = 0;

    GLuint gTextureCameraStreams[1];

    float gViewMatrix[16];
    float gProjectionMatrix[16];

    ShaderProgramFactory* gShaderProgramFactory = nullptr;
    SurfaceTextureFrame* gSurfaceTextureFrame = nullptr;

    // nativeOnCreated
    int initializeEgl();
    // nativeOnDestroyed
    int finalizeEgl();

    // nativeOnUiSurfaceInitialized
    int initializeEglUiSurface();
    int initializeGl();
    // nativeOnUiSurfaceDestroyed
    int finalizeGl();
    int finalizeEglUiSurface();

    int changeCurrentEglTo(
            EGLDisplay eglDisplay,
            EGLSurface eglDrawSurface,
            EGLSurface eglReadSurface,
            EGLContext eglContext);

    int returnEglToSystemDefault();

}

#endif // VIEW_FINDER_ANYWHERE_VIEWFINDERANYWHERE_HPP
