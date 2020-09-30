LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := viewfinderanywhere

LOCAL_C_FLAGS := \
        -Wall \
        -Werror \

LOCAL_SRC_FILES := \
        TraceLog.cpp \
        ViewFinderAnywhere.cpp \
        opengl/ElementBase.cpp \
        opengl/SurfaceTextureFrame.cpp \
        opengl/shader/ShaderProgramFactory.cpp \
        opengl/util/opengl_matrix.cpp \

LOCAL_LDLIBS := \
        -llog \
        -landroid \
        -lEGL \
        -lGLESv2 \

include $(BUILD_SHARED_LIBRARY)