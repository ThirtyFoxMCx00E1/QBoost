# Scaler: what it is built from

Qboost's **Scaler** (one tile in the in-game panel) works on a screen capture of your game (Android 14+,
"share a single app"):

- **Frame gen** is real **LSFG**, the frame generation of [Lossless Scaling](https://store.steampowered.com/app/993090/).
  The captured frames are imported straight into Vulkan (no CPU copy) and run through the LSFG engine on the
  GPU. The real and generated frames are then shown on a full-screen overlay that lets every touch through.
  You can pick **x2, x3 or x4**, like in Lossless Scaling.
- **Upscale** uses root to ask Android's Game Mode to render the game smaller (`cmd game set --downscale`). The
  capture is taken at that size, and the display hardware scales it back up to the screen, which costs no
  GPU time. Without root it says so instead of pretending.

The old OpenGL "Scaler" (sharpening plus a block-matching blend) is gone. It used up frame time without
making the game smoother.

## Your own Lossless.dll (never shipped)

The LSFG shaders come from **your** copy of Lossless Scaling. Qboost does not include them and never downloads them.

1. On your PC: Steam > Lossless Scaling > Manage > Browse local files, then copy `Lossless.dll` to the phone.
2. In a game: Qboost panel > Scaler > **Import Lossless.dll**, then pick the file.
3. Qboost checks that it is the real DLL with the LSFG shaders inside, translates them to SPIR-V once
   (`files/lsfg/shaders-<id>.cache`), and keeps the DLL inside the app's private storage.

## Requirements

- Android 14+ (single-app screen sharing). The frame gen library is for **arm64-v8a and x86_64**.
- A Vulkan GPU that passes LSFG's feature checks. Realistically that means Adreno 7xx-class or better. The
  Scaler card shows the driver's reason when a GPU cannot run it.
- Upscale (real lower render size) needs root.

## Where the code comes from

| Part | Origin | License |
| --- | --- | --- |
| `native/qboost_lsfg/lsfg/` (LSFG engine, DLL reader, shader translation, governor, pacer) | [DroidDeck](https://github.com/ThirtyFoxMCx00E1/DroidDeck) `app/src/main/cpp/framegen/lsfg`. That is the work of Camille LaVey and the [Eden](https://eden-emu.dev) emulator project, following [lsfg-vk](https://github.com/PancakeTAS/lsfg-vk) by PancakeTAS, ported to WinNative and DroidDeck by [@maxjivi05](https://github.com/maxjivi05). Copied unchanged, without DroidDeck's `lsfg_jni.cpp`. | GPL-3.0-or-later |
| `native/qboost_lsfg/thirdparty/dxbc/` (DXBC to SPIR-V) | DXVK's dxbc (Philip Rebohle, Joshua Ashton, Robin Kertels, Jeffrey Ellison), via DroidDeck | zlib |
| `native/qboost_lsfg/qboost_lsfg*.{h,cpp}` (Vulkan device and swapchain, AHardwareBuffer import, pacing, JNI) | Qboost, written against the files above | GPL-3.0-or-later |
| `app/src/main/java/com/example/scaler/Lossless.kt` | Adapted from DroidDeck's `gpu/Lossless.kt` (import, check and cache) | GPL-3.0-or-later |
| Hardware probe (`native/qboost_hw`) | Qboost's own plain C | MIT |

**License note:** the LSFG code is GPL-3.0-or-later. An APK that contains `libqboost_lsfg.so` must be
distributed under GPL-3.0-or-later, with the source available. The rest of the Qboost source stays under its
MIT license, which is GPL-compatible.

Lossless Scaling and its shaders belong to their developer. Qboost is not affiliated with Lossless Scaling or Valve.
