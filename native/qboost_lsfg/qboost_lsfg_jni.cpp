// SPDX-License-Identifier: GPL-3.0-or-later
// JNI for com.example.scaler.LsfgNative. The Lossless.dll entry points mirror DroidDeck's lsfg_jni.cpp.

#include <android/hardware_buffer_jni.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <mutex>
#include <string>

#include "lsfg/lsfg_dll.h"
#include "qboost_lsfg.h"

namespace {

std::string str(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

std::mutex g_errorLock;
std::string g_lastError;

void setError(const std::string& e) {
    std::lock_guard<std::mutex> lock(g_errorLock);
    g_lastError = e;
}

struct Session {
    qboost::Pipeline pipeline;
    ANativeWindow* window = nullptr;
};

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL Java_com_example_scaler_LsfgNative_nativeValidateDll(JNIEnv* env, jclass, jstring dll) {
    return (jint)lsfg::validateDll(str(env, dll));
}

JNIEXPORT jint JNICALL Java_com_example_scaler_LsfgNative_nativeBuildCache(JNIEnv* env, jclass, jstring dll,
                                                                         jstring cache, jboolean fp16) {
    return (jint)lsfg::buildCache(str(env, dll), str(env, cache), fp16 == JNI_TRUE);
}

JNIEXPORT jint JNICALL Java_com_example_scaler_LsfgNative_nativeCacheVariant(JNIEnv* env, jclass, jstring cache) {
    lsfg::Variant v = lsfg::Variant::None;
    if (lsfg::cacheVariant(str(env, cache), v) != lsfg::DllStatus::Ok) return (jint)lsfg::Variant::None;
    return (jint)v;
}

JNIEXPORT jstring JNICALL Java_com_example_scaler_LsfgNative_nativeStatusName(JNIEnv* env, jclass, jint status) {
    return env->NewStringUTF(lsfg::statusName((lsfg::DllStatus)status));
}

JNIEXPORT jstring JNICALL Java_com_example_scaler_LsfgNative_nativeVariantName(JNIEnv* env, jclass, jint variant) {
    return env->NewStringUTF(lsfg::variantName((lsfg::Variant)variant));
}

JNIEXPORT jstring JNICALL Java_com_example_scaler_LsfgNative_nativeProbe(JNIEnv* env, jclass) {
    return env->NewStringUTF(qboost::probe().c_str());
}

JNIEXPORT jstring JNICALL Java_com_example_scaler_LsfgNative_nativeLastError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_errorLock);
    return env->NewStringUTF(g_lastError.c_str());
}

JNIEXPORT jlong JNICALL Java_com_example_scaler_LsfgNative_nativeStart(JNIEnv* env, jclass, jobject surface,
                                                                     jstring cache, jint width, jint height,
                                                                     jint multiplier, jfloat flowScale,
                                                                     jfloat refreshHz) {
    if (!surface || width <= 0 || height <= 0) {
        setError("bad Scaler arguments");
        return 0;
    }
    auto* s = new Session();
    s->window = ANativeWindow_fromSurface(env, surface);
    if (!s->window) {
        delete s;
        setError("the overlay surface is gone");
        return 0;
    }
    const std::string path = str(env, cache);
    if (!s->pipeline.start(s->window, path.c_str(), (uint32_t)width, (uint32_t)height, (uint32_t)multiplier,
                           flowScale, refreshHz)) {
        setError(s->pipeline.error());
        ANativeWindow* w = s->window;
        delete s;
        ANativeWindow_release(w);
        return 0;
    }
    setError("");
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT jint JNICALL Java_com_example_scaler_LsfgNative_nativeFrame(JNIEnv* env, jclass, jlong handle,
                                                                    jobject hardwareBuffer) {
    auto* s = reinterpret_cast<Session*>(handle);
    if (!s || !hardwareBuffer) return -1;
    AHardwareBuffer* buffer = AHardwareBuffer_fromHardwareBuffer(env, hardwareBuffer);
    if (!buffer) return -1;
    const int r = s->pipeline.frame(buffer);
    if (r < 0) setError(s->pipeline.error());
    return r;
}

JNIEXPORT void JNICALL Java_com_example_scaler_LsfgNative_nativeConfigure(JNIEnv*, jclass, jlong handle,
                                                                        jint multiplier, jfloat flowScale) {
    auto* s = reinterpret_cast<Session*>(handle);
    if (s) s->pipeline.configure((uint32_t)multiplier, flowScale);
}

JNIEXPORT void JNICALL Java_com_example_scaler_LsfgNative_nativeStats(JNIEnv* env, jclass, jlong handle,
                                                                    jfloatArray out) {
    auto* s = reinterpret_cast<Session*>(handle);
    if (!s || !out || env->GetArrayLength(out) < 4) return;
    float values[4];
    s->pipeline.stats(values);
    env->SetFloatArrayRegion(out, 0, 4, values);
}

JNIEXPORT void JNICALL Java_com_example_scaler_LsfgNative_nativeStop(JNIEnv*, jclass, jlong handle) {
    auto* s = reinterpret_cast<Session*>(handle);
    if (!s) return;
    ANativeWindow* w = s->window;
    delete s;
    if (w) ANativeWindow_release(w);
}

}  // extern "C"
