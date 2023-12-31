cmake_minimum_required(VERSION 3.22.1)

project(view_finder_anywhere CXX)

file(
        GLOB_RECURSE
        sources
        *.c
        *.cpp
)

add_library(
        viewfinderanywhere
        SHARED
        ${sources}
)

target_include_directories(
        viewfinderanywhere
        PRIVATE
        include
)

find_library(lib-log        log)
find_library(lib-android    android)
find_library(lib-egl        egl)
find_library(lib-glesv2     glesv2)

target_link_libraries(
        viewfinderanywhere
        ${lib-log}
        ${lib-android}
        ${lib-egl}
        ${lib-glesv2}
)
