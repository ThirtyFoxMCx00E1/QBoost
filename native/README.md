# native/

`qboost_hw/qboost_hw.c` is the hardware probe behind the Scaler's "Auto" quality and its hardware line. It is
plain C11 (no STL, no third-party code) and loads Vulkan with `dlopen`, so it works on phones without Vulkan.

The four prebuilt libraries are committed in `app/src/main/jniLibs/<abi>/libqboost_hw.so` (about 10 KB each).
To rebuild them with your own NDK:

    ANDROID_NDK_HOME=/path/to/android-ndk-r30 native/build-native.sh

They are built for minSdk 24 and aligned to 16 KB pages. The app works without them (it then estimates the
tier from RAM only).
