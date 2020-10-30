#include "ViewFinderAnywhere.hpp"

namespace fezrestia {

    const std::string TAG = "VFA_Native";

    extern "C" jint JNI_OnLoad(JavaVM* vm, void* __unused reserved) {
        TraceLog(TAG, "JNI_OnLoad() : E");

//        gVm = vm;

        JNIEnv* env;
        vm->GetEnv((void**)&env, JNI_VERSION_1_6);

        TraceLog(TAG, "JNI_OnLoad() : X");
        return JNI_VERSION_1_6;
    }

    extern "C" void JNI_OnUnload(JavaVM* __unused vm, void* __unused reserved) {
        TraceLog(TAG, "JNI_OnUnload() : E");

//        gVm = nullptr;

        TraceLog(TAG, "JNI_OnUnload() : X");
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeOnCreated(
            JNIEnv __unused * jenv,
            jobject __unused instance) {
        return initializeEgl();
    }

    int initializeEgl() {
        TraceLog(TAG, "initializeEgl() : E");

        // Attributes.
        const EGLint eglConfigAttrs[] = {
                EGL_RENDERABLE_TYPE,    EGL_OPENGL_ES2_BIT,
                EGL_SURFACE_TYPE,       EGL_WINDOW_BIT,
                EGL_RED_SIZE,           8,
                EGL_GREEN_SIZE,         8,
                EGL_BLUE_SIZE,          8,
                EGL_ALPHA_SIZE,         8,
                EGL_NONE
        };
        const EGLint eglContextAttrs[] = {
                EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL_NONE
        };

        // Get display.
        EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (display == EGL_NO_DISPLAY) {
            LogE(TAG, "Default Display == EGL_NO_DISPLAY");
        }
        // Initialize display.
        switch (eglInitialize(display, nullptr, nullptr)) {
            case EGL_FALSE:
                LogE(TAG, "eglInitialize() == EGL_FALSE");
                break;
            case EGL_BAD_DISPLAY:
                LogE(TAG, "eglInitialize() == EGL_BAD_DISPLAY");
                break;
            case EGL_NOT_INITIALIZED:
                LogE(TAG, "eglInitialize() == EGL_NOT_INITIALIZED");
                break;
        }

        // Set config.
        EGLConfig config;
        EGLint numConfigs;
        switch (eglChooseConfig(display, eglConfigAttrs, &config, 1, &numConfigs)) {
            case EGL_FALSE:
                LogE(TAG, "eglInitialize() == EGL_FALSE");
                break;
            case EGL_BAD_DISPLAY:
                LogE(TAG, "eglInitialize() == EGL_BAD_DISPLAY");
                break;
            case EGL_BAD_ATTRIBUTE:
                LogE(TAG, "eglInitialize() == EGL_BAD_ATTRIBUTE");
                break;
            case EGL_NOT_INITIALIZED:
                LogE(TAG, "eglInitialize() == EGL_NOT_INITIALIZED");
                break;
            case EGL_BAD_PARAMETER:
                LogE(TAG, "eglInitialize() == EGL_BAD_PARAMETER");
                break;
        }

        // Get EGL frame buffer info.
        EGLint format;
        switch (eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format)) {
            case EGL_FALSE:
                LogE(TAG, "eglInitialize() == EGL_FALSE");
                break;
            case EGL_BAD_DISPLAY:
                LogE(TAG, "eglInitialize() == EGL_BAD_DISPLAY");
                break;
            case EGL_NOT_INITIALIZED:
                LogE(TAG, "eglInitialize() == EGL_NOT_INITIALIZED");
                break;
            case EGL_BAD_CONFIG:
                LogE(TAG, "eglInitialize() == EGL_BAD_CONFIG");
                break;
            case EGL_BAD_ATTRIBUTE:
                LogE(TAG, "eglInitialize() == EGL_BAD_ATTRIBUTE");
                break;
        }

        // Get EGL rendering context.
        EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, eglContextAttrs);
        if (context == EGL_NO_CONTEXT) {
            LogE(TAG, "eglCreateContext() == EGL_NO_CONTEXT");
        }

        // Cache Application EGL.
        gAppEglDisplay = display;
        gAppEglConfig = config;
        gAppEglContext = context;

        TraceLog(TAG, "initializeEgl() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeOnDestroyed(
            JNIEnv __unused * jenv,
            jobject __unused instance) {
        return finalizeEgl();
    }

    int finalizeEgl() {
        TraceLog(TAG, "finalizeEgl() : E");

        // Release Application EGL.
        eglDestroyContext(gAppEglDisplay, gAppEglContext);
        eglTerminate(gAppEglDisplay);

        gAppEglDisplay = nullptr;
        gAppEglConfig = nullptr;
        gAppEglContext = nullptr;

        TraceLog(TAG, "finalizeEgl() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeOnUiSurfaceInitialized(
            JNIEnv* jenv,
            jobject __unused instance,
            jobject surface) {
        TraceLog(TAG, "nativeOnUiSurfaceInitialized() : E");

        gUiNativeWindow = ANativeWindow_fromSurface(jenv, surface);

        initializeEglUiSurface();

        initializeGl();

        TraceLog(TAG, "nativeOnUiSurfaceInitialized() : X");
        return 0;
    }

    int initializeEglUiSurface() {
        TraceLog(TAG, "initializeEglUiSurface() : E");

        // Create EGL surface.
        gEglSurfaceUi = eglCreateWindowSurface(
                gAppEglDisplay,
                gAppEglConfig,
                gUiNativeWindow,
                nullptr);

        // Enable UI EGL.
        changeCurrentEglTo(gAppEglDisplay, gEglSurfaceUi, gEglSurfaceUi, gAppEglContext);

        // Get resolution.
        EGLint width;
        EGLint height;
        eglQuerySurface(
                gAppEglDisplay,
                gEglSurfaceUi,
                EGL_WIDTH,
                &width);
        eglQuerySurface(
                gAppEglDisplay,
                gEglSurfaceUi,
                EGL_HEIGHT,
                &height);
        gSurfaceUiWidth = width;
        gSurfaceUiHeight = height;

        // Set GL view port.
        if (height < width) {
            // Landscape.
            EGLint verticalOffset = (width - height) / 2;
            glViewport(0, -1 * verticalOffset, width, width); // Square.
        } else {
            // This is Portrait.
            EGLint horizontalOffset = (height - width) / 2;
            glViewport(-1 * horizontalOffset, 0, height, height); // Square.
        }

        // Return EGL to system default.
        returnEglToSystemDefault();

        TraceLog(TAG, "initializeEglUiSurface() : X");
        return 0;
    }

    int initializeGl() {
        TraceLog(TAG, "initializeGl() : E");

        // Enable UI EGL.
        changeCurrentEglTo(gAppEglDisplay, gEglSurfaceUi, gEglSurfaceUi, gAppEglContext);

        // Initialize.
        Matrix4x4_SetIdentity(gViewMatrix);
        Matrix4x4_SetEyeView(
                gViewMatrix,
                0.0f,   0.0f,   100.0f,     // See from where.
                0.0f,   0.0f,     0.0f,     // Look at where.
                0.0f,   1.0f,     0.0f);    // Perpendicular axis.
        Matrix4x4_SetIdentity(gProjectionMatrix);
        Matrix4x4_SetParallelProjection(
                gProjectionMatrix,
                -1.0f,      // Left of near plane.
                1.0f,       // Right of near plane.
                -1.0f,      // Bottom of near plane.
                1.0f,       // Top of near plane.
                0.0f,       // Distance to near plane.
                200.0f);    // Distance to far plane.
//        Matrix4x4_SetFrustumProjection(
//                appContext->mProjectionMatrix,
//                -1.0f,
//                1.0f,
//                -1.0f,
//                1.0f,
//                50.0f,
//                150.0f);

        // Initialize ShaderProgramFactory.
        gShaderProgramFactory = new ShaderProgramFactory();
        gShaderProgramFactory->Initialize();

        // SurfaceTexture renderer.
        gSurfaceTextureFrame = new SurfaceTextureFrame();
        gSurfaceTextureFrame->initialize(
                GL_VIEW_PORT_NORM_WIDTH,
                GL_VIEW_PORT_NORM_HEIGHT);
        GLuint surfaceTextureShader = gShaderProgramFactory->CreateShaderProgram(
                ShaderProgramFactory::ShaderType_SURFACE_TEXTURE,
                GLSL::SURFACE_TEXTURE_FRAME_VERTEX.c_str(),
                GLSL::SURFACE_TEXTURE_FRAME_VERTEX.length(),
                GLSL::SURFACE_TEXTURE_FRAME_FRAGMENT.c_str(),
                GLSL::SURFACE_TEXTURE_FRAME_FRAGMENT.length());
        gSurfaceTextureFrame->setShaderProgram(surfaceTextureShader);

        // Return EGL to system default.
        returnEglToSystemDefault();

        TraceLog(TAG, "initializeGl() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeOnUiSurfaceFinalized(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeOnUiSurfaceFinalized() : E");

        finalizeGl();

        finalizeEglUiSurface();

        TraceLog(TAG, "nativeOnUiSurfaceFinalized() : X");
        return 0;
    }

    int finalizeGl() {
        TraceLog(TAG, "finalizeGl() : E");

        changeCurrentEglTo(gAppEglDisplay, gEglSurfaceUi, gEglSurfaceUi, gAppEglContext);

        if (gSurfaceTextureFrame != nullptr) {
            gSurfaceTextureFrame->finalize();
            delete gSurfaceTextureFrame;
            gSurfaceTextureFrame = nullptr;
        }

        if (gShaderProgramFactory != nullptr) {
            gShaderProgramFactory->Finalize();
            delete gShaderProgramFactory;
            gShaderProgramFactory = nullptr;
        }

        returnEglToSystemDefault();

        TraceLog(TAG, "finalizeGl() : X");
        return 0;
    }

    int finalizeEglUiSurface() {
        TraceLog(TAG, "finalizeEglUiSurface() : E");

        if (gEglSurfaceUi != nullptr) {
            eglDestroySurface(gAppEglDisplay, gEglSurfaceUi);
        }

        if (gUiNativeWindow != nullptr) {
            ANativeWindow_release(gUiNativeWindow);
        }

        TraceLog(TAG, "finalizeEglUiSurface() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativePrepareCameraStreamTexture(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativePrepareCameraStreamTextures() : E");

        changeCurrentEglTo(gAppEglDisplay, gEglSurfaceUi, gEglSurfaceUi, gAppEglContext);

        // Generate texture.
        glGenTextures(1, gTextureCameraStreams);
        GLuint texId = gTextureCameraStreams[0];

        // Link texture to target.
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, texId);

        // Magnification/Minification.
        glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_MAG_FILTER,
                GL_NEAREST);
        glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_MIN_FILTER,
                GL_NEAREST);

        // Clamp edge.
        glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_WRAP_S,
                GL_CLAMP_TO_EDGE);
        glTexParameterf(
                GL_TEXTURE_EXTERNAL_OES,
                GL_TEXTURE_WRAP_T,
                GL_CLAMP_TO_EDGE);

        // Un-link texture from target.
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

        returnEglToSystemDefault();

        TraceLog(TAG, "nativePrepareCameraStreamTextures() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeReleaseCameraStreamTexture(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeReleaseCameraStreamTexture() : E");

        changeCurrentEglTo(gAppEglDisplay, gEglSurfaceUi, gEglSurfaceUi, gAppEglContext);

        glDeleteTextures(1, gTextureCameraStreams);
        gTextureCameraStreams[0] = 0;

        returnEglToSystemDefault();

        TraceLog(TAG, "nativeReleaseCameraStreamTexture() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeSetCameraStreamAspectWH(
            JNIEnv* __unused jenv,
            jobject __unused instance,
            jfloat aspectWH) {
        TraceLog(TAG, "nativeSetCameraStreamAspectWH() : E");
        gCameraStreamAspectWH = aspectWH;
        TraceLog(TAG, "nativeSetCameraStreamAspectWH() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeGetCameraStreamTextureId(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeGetCameraStreamTextureId()");
        return gTextureCameraStreams[0];
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeSetCameraStreamTransformMatrix(
            JNIEnv* jenv,
            jobject __unused instance,
            jfloatArray textureTransformMatrix) {
        TraceLog(TAG, "nativeSetCameraStreamTransformMatrix() : E");

        // Load matrix from java and set it to renderer.
        auto* matrix = (jfloat*) jenv->GetPrimitiveArrayCritical(textureTransformMatrix, nullptr);
        gSurfaceTextureFrame->setTextureTransformMatrix(matrix);
        jenv->ReleasePrimitiveArrayCritical(textureTransformMatrix, matrix, JNI_ABORT);

        TraceLog(TAG, "nativeSetCameraStreamTransformMatrix() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeSetCameraStreamRotDeg(
            JNIEnv* __unused jenv,
            jobject __unused instance,
            jint rotDeg) {
        TraceLog(TAG, "nativeSetCameraStreamRotDeg() : E");
        gCameraStreamRotDeg = rotDeg;
        TraceLog(TAG, "nativeSetCameraStreamRotDeg() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeOnCameraStreamUpdated(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeOnCameraStreamUpdated() : E");

        // Clear color, (red, green, blue, alpha).
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        // Clear buffer bit.
        glClear(GL_COLOR_BUFFER_BIT); // NOLINT(hicpp-signed-bitwise)

        float globalMatrix[16];
        Matrix4x4_SetIdentity(globalMatrix);
        Matrix4x4_Multiply(globalMatrix, gViewMatrix, globalMatrix);
        Matrix4x4_Multiply(globalMatrix, gProjectionMatrix, globalMatrix);

        // Render.
        gSurfaceTextureFrame->setTextureId(gTextureCameraStreams[0]);
        gSurfaceTextureFrame->setAlpha(1.0f);
        gSurfaceTextureFrame->setGlobalMatrix(globalMatrix);
        gSurfaceTextureFrame->rotate((float) gCameraStreamRotDeg, 0, 0, 1);

        // Camera frame area ratio.
        float cameraW;
        float cameraH;
        if (gCameraStreamAspectWH < 1.0f) {
            // Square -> Portrait.
            cameraW = gCameraStreamAspectWH;
            cameraH = 1.0f;
        } else {
            // Square -> Landscape.
            cameraW = 1.0f;
            cameraH = 1.0f / gCameraStreamAspectWH;
        }
        TraceLog(TAG, "## cameraW =");
        TraceLog(TAG, std::to_string(cameraW));
        TraceLog(TAG, "## cameraH =");
        TraceLog(TAG, std::to_string(cameraH));
        gSurfaceTextureFrame->scale(cameraW, cameraH, 1.0f);

        // Finder area ratio.
        const float uiAspectWH = (float) gSurfaceUiWidth / (float) gSurfaceUiHeight;
        float finderW;
        float finderH;
        if (uiAspectWH < gCameraStreamAspectWH) {
            // Crop left/right.
            finderH = cameraH;
            finderW = finderH * uiAspectWH;
        } else {
            // Crop top/bottom.
            finderW = cameraW;
            finderH = finderW / uiAspectWH;
        }
        TraceLog(TAG, "## finderW =");
        TraceLog(TAG, std::to_string(finderW));
        TraceLog(TAG, "## finderH =");
        TraceLog(TAG, std::to_string(finderH));

        // UI surface ratio.
        float uiW;
        float uiH;
        if (uiAspectWH < 1.0f) {
            // UI portrait.
            uiH = 1.0f;
            uiW = uiH * uiAspectWH;
        } else {
            // UI landscape.
            uiW = 1.0f;
            uiH = uiW / uiAspectWH;
        }

        // Total scale.
        float scaleW = uiW / finderW;
        float scaleH = uiH / finderH;
        float totalScale = std::max(scaleW, scaleH);
        TraceLog(TAG, "## scaleW =");
        TraceLog(TAG, std::to_string(scaleW));
        TraceLog(TAG, "## scaleH =");
        TraceLog(TAG, std::to_string(scaleH));
        TraceLog(TAG, "## totalScale =");
        TraceLog(TAG, std::to_string(totalScale));
        gSurfaceTextureFrame->scale(totalScale, totalScale, 1.0f);

        gSurfaceTextureFrame->render();

        if (checkGlError(TAG) != GL_NO_ERROR) {
            LogE(TAG, "nativeOnCameraStreamUpdated() : GL ERROR");
        }

        glFlush();
        glFinish();

        // Swap buffer.
        eglSwapBuffers(gAppEglDisplay, gEglSurfaceUi);

        if (gIsVideoEncoding) {
            TraceLog(TAG, "## Render Video Encode Surface : E");

            // Enable video surface EGL context.
            changeCurrentEglTo(gEncoderEglDisplay, gEglSurfaceEncoder, gEglSurfaceEncoder, gEncoderEglContext);

            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT); // NOLINT(hicpp-signed-bitwise)

            gSurfaceTextureFrame->render();

            if (checkGlError(TAG) != GL_NO_ERROR) {
                LogE(TAG, "nativeOnCameraStreamUpdated() : VIDEO ENCODING : GL ERROR");
            }

            glFlush();
            glFinish();

            eglSwapBuffers(gAppEglDisplay, gEglSurfaceEncoder);

            // Return EGL context to UI surface.
            changeCurrentEglTo(gAppEglDisplay, gEglSurfaceUi, gEglSurfaceUi, gAppEglContext);

            TraceLog(TAG, "## Render Video Encode Surface : X");
        }

        TraceLog(TAG, "nativeOnCameraStreamUpdated() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeOnCameraStreamFinished(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeOnCameraStreamFinished() : E");

        // Clear color, (red, green, blue, alpha).
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // Clear buffer bit.
        glClear(GL_COLOR_BUFFER_BIT); // NOLINT(hicpp-signed-bitwise)

        glFlush();
        glFinish();

        // Swap buffer.
        eglSwapBuffers(gAppEglDisplay, gEglSurfaceUi);

        TraceLog(TAG, "nativeOnCameraStreamFinished() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeSetEncoderSurface(
            JNIEnv* jenv,
            jobject __unused instance,
            jobject surface) {
        TraceLog(TAG, "nativeSetEncoderSurface() : E");

        // Attributes.
        const EGLint eglConfigAttrs[] = {
                EGL_RENDERABLE_TYPE,    EGL_OPENGL_ES2_BIT,
                EGL_RED_SIZE,           8,
                EGL_GREEN_SIZE,         8,
                EGL_BLUE_SIZE,          8,
                EGL_RECORDABLE_ANDROID, 1,
                EGL_NONE
        };
        const EGLint eglContextAttrs[] = {
                EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL_NONE
        };

        // Get display.
        EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (display == EGL_NO_DISPLAY) {
            LogE(TAG, "Default Display == EGL_NO_DISPLAY");
        }
        // Initialize display.
        switch (eglInitialize(display, nullptr, nullptr)) {
            case EGL_FALSE:
                LogE(TAG, "eglInitialize() == EGL_FALSE");
                break;
            case EGL_BAD_DISPLAY:
                LogE(TAG, "eglInitialize() == EGL_BAD_DISPLAY");
                break;
            case EGL_NOT_INITIALIZED:
                LogE(TAG, "eglInitialize() == EGL_NOT_INITIALIZED");
                break;
        }

        // Set config.
        EGLConfig config;
        EGLint numConfigs;
        switch (eglChooseConfig(display, eglConfigAttrs, &config, 1, &numConfigs)) {
            case EGL_FALSE:
                LogE(TAG, "eglInitialize() == EGL_FALSE");
                break;
            case EGL_BAD_DISPLAY:
                LogE(TAG, "eglInitialize() == EGL_BAD_DISPLAY");
                break;
            case EGL_BAD_ATTRIBUTE:
                LogE(TAG, "eglInitialize() == EGL_BAD_ATTRIBUTE");
                break;
            case EGL_NOT_INITIALIZED:
                LogE(TAG, "eglInitialize() == EGL_NOT_INITIALIZED");
                break;
            case EGL_BAD_PARAMETER:
                LogE(TAG, "eglInitialize() == EGL_BAD_PARAMETER");
                break;
        }

        // Get EGL frame buffer info.
        EGLint format;
        switch (eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format)) {
            case EGL_FALSE:
                LogE(TAG, "eglInitialize() == EGL_FALSE");
                break;
            case EGL_BAD_DISPLAY:
                LogE(TAG, "eglInitialize() == EGL_BAD_DISPLAY");
                break;
            case EGL_NOT_INITIALIZED:
                LogE(TAG, "eglInitialize() == EGL_NOT_INITIALIZED");
                break;
            case EGL_BAD_CONFIG:
                LogE(TAG, "eglInitialize() == EGL_BAD_CONFIG");
                break;
            case EGL_BAD_ATTRIBUTE:
                LogE(TAG, "eglInitialize() == EGL_BAD_ATTRIBUTE");
                break;
        }

        // Get EGL rendering context.
        EGLContext context = eglCreateContext(display, config, gAppEglContext, eglContextAttrs);
        if (context == EGL_NO_CONTEXT) {
            LogE(TAG, "eglCreateContext() == EGL_NO_CONTEXT");
        }

        // Cache encoder EGL.
        gEncoderEglDisplay = display;
        gEncoderEglConfig = config;
        gEncoderEglContext = context;

        gEncoderNativeWindow = ANativeWindow_fromSurface(jenv, surface);

        // Create EGL surface.
        gEglSurfaceEncoder = eglCreateWindowSurface(
                gEncoderEglDisplay,
                gEncoderEglConfig,
                gEncoderNativeWindow,
                nullptr);

        // Enable encoder EGL.
        changeCurrentEglTo(gEncoderEglDisplay, gEglSurfaceEncoder, gEglSurfaceEncoder, gEncoderEglContext);

        // Get resolution.
        EGLint width;
        EGLint height;
        eglQuerySurface(
                gAppEglDisplay,
                gEglSurfaceEncoder,
                EGL_WIDTH,
                &width);
        eglQuerySurface(
                gAppEglDisplay,
                gEglSurfaceEncoder,
                EGL_HEIGHT,
                &height);
        gSurfaceEncoderWidth = width;
        gSurfaceEncoderHeight = height;
        TraceLog(TAG, "## gSurfaceEncoderWidth =");
        TraceLog(TAG, std::to_string(gSurfaceEncoderWidth));
        TraceLog(TAG, "## gSurfaceEncoderHeight =");
        TraceLog(TAG, std::to_string(gSurfaceEncoderHeight));

        // Set GL view port.
        if (height < width) {
            // Landscape.
            EGLint verticalOffset = (width - height) / 2;
            glViewport(0, -1 * verticalOffset, width, width); // Square.
        } else {
            // This is Portrait.
            EGLint horizontalOffset = (height - width) / 2;
            glViewport(-1 * horizontalOffset, 0, height, height); // Square.
        }

        // Return EGL to system default.
        returnEglToSystemDefault();

        TraceLog(TAG, "nativeSetEncoderSurface() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeStartVideoEncode(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeStartVideoEncode() : E");

        gIsVideoEncoding = true;

        TraceLog(TAG, "nativeStartVideoEncode() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeStopVideoEncode(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeStopVideoEncode() : E");

        gIsVideoEncoding = false;

        TraceLog(TAG, "nativeStopVideoEncode() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeReleaseEncoderSurface(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeReleaseEncoderSurface() : E");

        if (gEglSurfaceEncoder != nullptr) {
            eglDestroySurface(gEncoderEglDisplay, gEglSurfaceEncoder);
        }

        if (gEncoderNativeWindow != nullptr) {
            ANativeWindow_release(gEncoderNativeWindow);
        }

        // Release encoder EGL.
        eglDestroyContext(gEncoderEglDisplay, gEncoderEglContext);
        eglTerminate(gEncoderEglDisplay);

        gEncoderEglDisplay = nullptr;
        gEncoderEglConfig = nullptr;
        gEncoderEglContext = nullptr;

        TraceLog(TAG, "nativeReleaseEncoderSurface() : X");
        return 0;
    }

    int changeCurrentEglTo(
            EGLDisplay eglDisplay,
            EGLSurface eglDrawSurface,
            EGLSurface eglReadSurface,
            EGLContext eglContext) {
        TraceLog(TAG, "changeCurrentEglTo() : E");

        if (eglMakeCurrent(
                eglDisplay,
                eglDrawSurface,
                eglReadSurface,
                eglContext) == EGL_FALSE) {
            LogE(TAG, "Failed to enable client EGL.");
            return -1;
        }

        TraceLog(TAG, "changeCurrentEglTo() : X");
        return 0;
    }

    int returnEglToSystemDefault() {
        TraceLog(TAG, "returnEglToSystemDefault() : E");

        if (eglReleaseThread() == EGL_FALSE) {
            LogE(TAG, "Failed to release EGL context.");
        }

        TraceLog(TAG, "returnEglToSystemDefault() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeBindAppEglContext(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeBindAppEglContext() : E");

        changeCurrentEglTo(gAppEglDisplay, gEglSurfaceUi, gEglSurfaceUi, gAppEglContext);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, gTextureCameraStreams[0]);

        TraceLog(TAG, "nativeBindAppEglContext() : X");
        return 0;
    }

    extern "C" JNIEXPORT jint JNICALL Java_com_fezrestia_android_viewfinderanywhere_control_OverlayViewFinderController_nativeUnbindAppEglContext(
            JNIEnv* __unused jenv,
            jobject __unused instance) {
        TraceLog(TAG, "nativeUnbindAppEglContext() : E");

        glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

        returnEglToSystemDefault();

        TraceLog(TAG, "nativeUnbindAppEglContext() : X");
        return 0;
    }

}
