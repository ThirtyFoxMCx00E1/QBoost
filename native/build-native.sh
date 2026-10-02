#!/usr/bin/env bash
# Builds libqboost_hw.so for the four ABIs Qboost ships (arm64-v8a, armeabi-v7a, x86, x86_64)
# straight into app/src/main/jniLibs/, using the clang from an Android NDK.
#
#   ANDROID_NDK_HOME=/path/to/android-ndk-r30 native/build-native.sh
#
# Plain C, no CMake needed. Libraries are aligned to 16 KB pages (required for current Android devices).
set -euo pipefail

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${1:-}}}"
if [ -z "$NDK" ] || [ ! -d "$NDK/toolchains/llvm/prebuilt" ]; then
  echo "Set ANDROID_NDK_HOME (or pass the NDK folder as the first argument)." >&2
  exit 1
fi

case "$(uname -s)" in
  Linux)  HOST_TAG="linux-x86_64" ;;
  Darwin) HOST_TAG="darwin-x86_64" ;;
  *)      echo "Unsupported host: $(uname -s)" >&2; exit 1 ;;
esac
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
API=24 # the app's minSdk

HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/qboost_hw/qboost_hw.c"
OUT="$HERE/../app/src/main/jniLibs"

# abi : clang target triple
for entry in "arm64-v8a:aarch64-linux-android" "armeabi-v7a:armv7a-linux-androideabi" \
             "x86:i686-linux-android" "x86_64:x86_64-linux-android"; do
  ABI="${entry%%:*}"
  TRIPLE="${entry##*:}"
  mkdir -p "$OUT/$ABI"
  "$TOOLCHAIN/bin/${TRIPLE}${API}-clang" \
    -std=c11 -O2 -fPIC -shared -fvisibility=hidden -Wall -Wextra \
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-z,noexecstack \
    -Wl,--build-id=none -Wl,--gc-sections \
    -o "$OUT/$ABI/libqboost_hw.so" "$SRC" -ldl
  "$TOOLCHAIN/bin/llvm-strip" --strip-unneeded "$OUT/$ABI/libqboost_hw.so"
  printf '%-12s %6s bytes\n' "$ABI" "$(wc -c < "$OUT/$ABI/libqboost_hw.so")"
done
