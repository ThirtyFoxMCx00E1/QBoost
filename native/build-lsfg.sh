#!/usr/bin/env bash
# Builds libqboost_lsfg.so (Scaler frame generation: LSFG + capture/present glue) for arm64-v8a and
# x86_64 into app/src/main/jniLibs/. LSFG needs a 64-bit Vulkan 1.3 GPU, so 32-bit ABIs are not built;
# on those the Scaler reports that frame generation is unavailable.
#
#   ANDROID_NDK_HOME=/path/to/android-ndk native/build-lsfg.sh
set -euo pipefail

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${1:-}}}"
if [ -z "$NDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
  echo "Set ANDROID_NDK_HOME (or pass the NDK folder as the first argument)." >&2
  exit 1
fi
CMAKE="${CMAKE:-cmake}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../app/src/main/jniLibs"
case "$(uname -s)" in
  Linux)  HOST_TAG="linux-x86_64" ;;
  Darwin) HOST_TAG="darwin-x86_64" ;;
  *)      echo "Unsupported host" >&2; exit 1 ;;
esac
STRIP="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/llvm-strip"

for ABI in arm64-v8a x86_64; do
  BUILD="$HERE/.build-lsfg/$ABI"
  "$CMAKE" -S "$HERE/qboost_lsfg" -B "$BUILD" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release >/dev/null
  "$CMAKE" --build "$BUILD" -j"$(nproc 2>/dev/null || echo 4)"
  mkdir -p "$OUT/$ABI"
  cp "$BUILD/libqboost_lsfg.so" "$OUT/$ABI/libqboost_lsfg.so"
  "$STRIP" --strip-unneeded "$OUT/$ABI/libqboost_lsfg.so"
  printf '%-12s %8s bytes\n' "$ABI" "$(wc -c < "$OUT/$ABI/libqboost_lsfg.so")"
done
