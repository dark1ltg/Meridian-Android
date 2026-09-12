#!/usr/bin/env bash
# Build Media3 1.4.1 FFmpeg JNI (libffmpegJNI.so) for Meridian's ABIs.
# Google does not ship those .so files on Maven.
#
#   export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
#   bash android/scripts/build-media3-ffmpeg.sh
#
# Writes android/app/src/main/jniLibs/<abi>/libffmpegJNI.so
# (avcodec/avutil/swresample are statically linked).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/26.3.11579264}"
API=26
HOST=linux-x86_64
WORK="${ROOT}/android/.ffmpeg-build"
JNI="$WORK/jni"
FFMPEG="$JNI/ffmpeg"
OUT_LIBS="${ROOT}/android/app/src/main/jniLibs"
ENABLED_DECODERS=(vorbis opus flac alac mp3 aac pcm_s16le pcm_s24le pcm_f32le wmav2)
ABIS=(arm64-v8a x86_64)
MEDIA3_JNI_BASE="https://raw.githubusercontent.com/androidx/media/1.4.1/libraries/decoder_ffmpeg/src/main/jni"

if [[ ! -d "$NDK" ]]; then
  echo "Set ANDROID_NDK_HOME to an NDK r26+ tree (missing: $NDK)." >&2
  exit 1
fi

mkdir -p "$JNI" "$OUT_LIBS"
if [[ ! -f "$JNI/ffmpeg_jni.cc" ]]; then
  curl -fsSL "$MEDIA3_JNI_BASE/ffmpeg_jni.cc" -o "$JNI/ffmpeg_jni.cc"
fi
if [[ ! -f "$JNI/CMakeLists.txt" ]]; then
  curl -fsSL "$MEDIA3_JNI_BASE/CMakeLists.txt" -o "$JNI/CMakeLists.txt"
fi
# Extra system libs FFmpeg typically needs when wrapping the static archives.
if ! grep -q 'find_library(android_z_lib z)' "$JNI/CMakeLists.txt"; then
  cat >> "$JNI/CMakeLists.txt" <<'EOF'

find_library(android_z_lib z)
find_library(android_m_lib m)
target_link_libraries(ffmpegJNI PRIVATE ${android_z_lib} ${android_m_lib})
EOF
fi

if [[ ! -d "$FFMPEG/.git" ]]; then
  git clone --depth 1 --branch n6.1.1 https://git.ffmpeg.org/ffmpeg.git "$FFMPEG"
fi

TOOLCHAIN_PREFIX="${NDK}/toolchains/llvm/prebuilt/${HOST}/bin"
JOBS="$(nproc 2>/dev/null || echo 4)"
COMMON_OPTIONS=(
  --target-os=android
  --enable-static
  --disable-shared
  --disable-doc
  --disable-programs
  --disable-everything
  --disable-avdevice
  --disable-avformat
  --disable-swscale
  --disable-postproc
  --disable-avfilter
  --disable-symver
  --enable-swresample
  --extra-ldexeflags=-pie
  --disable-v4l2-m2m
  --disable-vulkan
)
for decoder in "${ENABLED_DECODERS[@]}"; do
  COMMON_OPTIONS+=(--enable-decoder="${decoder}")
done

CMAKE_BIN="${ANDROID_HOME:-$HOME/Android/Sdk}/cmake/3.22.1/bin/cmake"
if [[ ! -x "$CMAKE_BIN" ]]; then
  CMAKE_BIN="$(command -v cmake)"
fi

link_jni() {
  local abi="$1"
  local dest="$OUT_LIBS/$abi/libffmpegJNI.so"
  local bdir="$WORK/cmake-$abi"
  rm -rf "$bdir"
  mkdir -p "$bdir" "$OUT_LIBS/$abi"
  "$CMAKE_BIN" -S "$JNI" -B "$bdir" \
    -DCMAKE_TOOLCHAIN_FILE="${NDK}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-${API}" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release
  "$CMAKE_BIN" --build "$bdir" --target ffmpegJNI -j"$JOBS"
  cp -f "$bdir/libffmpegJNI.so" "$dest"
  "${TOOLCHAIN_PREFIX}/llvm-strip" --strip-unneeded "$dest" || true
  echo "Wrote $dest"
}

build_ffmpeg_abi() {
  local abi="$1" arch="$2" cpu="$3" triple="$4" extra_cflags="${5:-}" extra_ldflags="${6:-}" extra_conf="${7:-}"
  local dest="$OUT_LIBS/$abi/libffmpegJNI.so"
  if [[ -f "$dest" ]]; then
    echo "Already have $dest"
    return 0
  fi
  cd "$FFMPEG"
  make distclean >/dev/null 2>&1 || true
  # shellcheck disable=SC2086
  ./configure \
    --libdir="android-libs/${abi}" \
    --arch="$arch" \
    --cpu="$cpu" \
    --cross-prefix="${TOOLCHAIN_PREFIX}/${triple}${API}-" \
    --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
    --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
    ${extra_cflags:+--extra-cflags="$extra_cflags"} \
    ${extra_ldflags:+--extra-ldflags="$extra_ldflags"} \
    ${extra_conf} \
    "${COMMON_OPTIONS[@]}"
  make -j"$JOBS"
  make install-libs
  # Generated headers (avconfig.h) vanish on distclean — link JNI first.
  link_jni "$abi"
  make distclean >/dev/null 2>&1 || true
}

build_ffmpeg_abi arm64-v8a aarch64 armv8-a aarch64-linux-android
build_ffmpeg_abi x86_64 x86_64 x86-64 x86_64-linux-android "" "" "--disable-asm"

echo "FFmpeg JNI ready under $OUT_LIBS"
