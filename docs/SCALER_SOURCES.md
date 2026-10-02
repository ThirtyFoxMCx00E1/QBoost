# Scaler: what it is built from, and what was reviewed

Qboost's **Scaler** (one tile in the in-game panel) combines two things on a screen capture of your game:
**Upscale** (capture at a hardware-tuned size, scale back up, sharpen) and **Frame gen** (an in-between frame
after every captured frame). Both run in the built-in OpenGL ES engine (`ScalerRenderer`), which works on any
phone with Android 14+ screen sharing, including low-end ones.

## What is in the built-in engine

| Part | Origin |
| --- | --- |
| Sharpening pass | Algorithm: **AMD FidelityFX Contrast Adaptive Sharpening (CAS)**, MIT license. Written from the published algorithm for GLES 2. |
| Frame interpolation | Qboost's own: block-matching motion estimation on a 1/8-size copy, then both frames are warped half-way and blended. |
| Hardware probe (`native/qboost_hw`) | Qboost's own plain C, built with the Android NDK for arm64-v8a, armeabi-v7a, x86, x86_64. No third-party code. |

## Projects that were reviewed and are NOT integrated (and why)

| Project | License | What it really is | Why it is not in the app |
| --- | --- | --- | --- |
| **Arm Accuracy Super Resolution** (Arm ASR) | MIT | A *temporal* upscaler (FSR 2 family). | Needs per-frame **motion vectors, depth and jitter from the game engine**, which a screen capture of another app does not have. The uploaded zip is also only an index repo (README + license); the real libraries are git submodules that were not included. |
| **LSFG-Android** (wrapper) | MIT | Pointer repo for the Android port of lsfg-vk. | The zip contains only README/LICENSE/.gitmodules; the code is in submodules that were not included. |
| **lsfg-vk-android** (`framegen/`) | MIT | Vulkan compute frame generation (Lossless Scaling's "LSFG 3.1" technique). | Its shaders are produced at run time from **`Lossless.dll`, which you must own** (paid Lossless Scaling on Steam); nothing can be bundled. It also needs three submodules (`dxbc`, `pe-parse`, `volk`) that are empty in the upload, and targets **arm64-v8a / x86_64 only**, Android 10+, and realistically **Adreno 7xx-class GPUs or better**. |
| **LSFG-Android-Application** | GPL-3.0 (its README adds "no Play Store / no commercial use") | The Android app around lsfg-vk. | Copying its code would put Qboost under those terms. Qboost would need its own glue code against the MIT `framegen` library instead. |
| **OpenFG** | **Proprietary EULA, "all rights reserved", binary only** | A root (KernelSU/Magisk + Zygisk) module that hooks games. | Not open source and not redistributable; the upload contains no code. Needs root. |
| **RealSR-NCNN-Android** | MIT | Offline *image* upscaler (Real-ESRGAN, Waifu2x, Anime4K models via ncnn + Vulkan). | Neural models take seconds per image; they cannot run on game frames in real time on a phone. Useful for photos/screenshots only. |
| **Bannerlator** | GPL-3.0 | A Wine/Box64 emulator (Winlator fork) that loads lsfg-vk inside its own Wine process. | It controls the game process, which Qboost cannot do for ordinary Android games. Reference only. |

## What would be needed to add LSFG frame generation later

1. The full sources with submodules: `git clone --recurse-submodules` of **lsfg-vk-android** (and zip the result).
2. A decision on licensing: use only the MIT `framegen` library plus Qboost's own glue (keeps Qboost free of GPL),
   or accept GPL-3 and reuse the app code.
3. Your own `Lossless.dll` imported on the phone (Qboost can never ship it).
4. A phone that can run it (Adreno 7xx-class or better). The hardware probe already reports whether a phone
   qualifies (`HardwareProfile.heavyVulkanCandidate`), and the built-in engine would stay as the fallback.
