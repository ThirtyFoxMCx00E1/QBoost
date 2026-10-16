# native/

## qboost_hw (MIT)

`qboost_hw/qboost_hw.c` is the hardware probe behind the Scaler's "Auto" quality and its hardware line. It is
plain C11 (no STL, no third-party code) and loads Vulkan with `dlopen`, so it works on phones without Vulkan.
The prebuilt libraries are in `app/src/main/jniLibs/<abi>/libqboost_hw.so` (about 10 KB each, for 4 ABIs).

    ANDROID_NDK_HOME=/path/to/android-ndk native/build-native.sh

## qboost_lsfg (GPL-3.0-or-later)

`qboost_lsfg/` is the Scaler's real frame generation. It is the LSFG engine from DroidDeck (`lsfg/`, plus
DXVK's `thirdparty/dxbc`, zlib), plus Qboost's capture and present glue (`qboost_lsfg.cpp`) and JNI for
`com.example.scaler.LsfgNative`. It needs the user's own `Lossless.dll` at run time. See
`docs/SCALER_SOURCES.md`.

The prebuilt libraries are in `app/src/main/jniLibs/{arm64-v8a,x86_64}/libqboost_lsfg.so` (about 0.8 MB each).
They are built for API 29+ with the static libc++ and aligned to 16 KB pages. They depend only on
`libvulkan`, `libandroid`, `libnativewindow`, `liblog`, `libm`, `libdl` and `libc`. To rebuild them you need
CMake 3.18+ and Ninja:

    ANDROID_NDK_HOME=/path/to/android-ndk native/build-lsfg.sh

Both libraries are optional at run time. Without `libqboost_lsfg.so` (32-bit phones) the Scaler explains that
frame gen is not available.
