# libqboost_lsfg.so: the Scaler's real frame generation (LSFG, from DroidDeck) + Qboost's capture glue.
# Built by native/build-lsfg.sh into app/src/main/jniLibs (Qboost ships prebuilt native libraries).
cmake_minimum_required(VERSION 3.18)
project(qboost_lsfg CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# DXVK's DXBC -> SPIR-V translator (zlib licence), used when Lossless.dll only has DXBC shaders.
add_subdirectory(thirdparty/dxbc)

set(LSFG ${CMAKE_CURRENT_SOURCE_DIR}/lsfg)
add_library(qboost_lsfg SHARED
    qboost_lsfg.cpp
    qboost_lsfg_jni.cpp
    ${LSFG}/lsfg_probe.cpp
    ${LSFG}/lsfg_dll.cpp
    ${LSFG}/lsfg_dxbc.cpp
    ${LSFG}/lsfg_vkd.cpp
    ${LSFG}/lsfg_common.cpp
    ${LSFG}/lsfg_shaders.cpp
    ${LSFG}/lsfg_mipmaps.cpp
    ${LSFG}/lsfg_alpha.cpp
    ${LSFG}/lsfg_beta.cpp
    ${LSFG}/lsfg_gamma.cpp
    ${LSFG}/lsfg_delta.cpp
    ${LSFG}/lsfg_generate.cpp
    ${LSFG}/lsfg_chain.cpp
    ${LSFG}/lsfg_pacer.cpp
    ${LSFG}/lsfg_engine.cpp
    ${LSFG}/lsfg_governor.cpp)
target_include_directories(qboost_lsfg PRIVATE ${CMAKE_CURRENT_SOURCE_DIR} ${LSFG})
target_compile_definitions(qboost_lsfg PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_compile_options(qboost_lsfg PRIVATE -O2 -fvisibility=hidden -Wall -Wno-unused-parameter)
target_link_options(qboost_lsfg PRIVATE
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,--gc-sections -Wl,--exclude-libs,ALL)
target_link_libraries(qboost_lsfg PRIVATE dxbc vulkan android nativewindow log atomic)
