Bundled native libraries for the Android APK.

Built by the app / Release workflow:
  libmeridian_aubio.so  — aubio 0.4.9 onset/tempo (GPL-3), via Gradle/CMake
  libffmpegJNI.so       — Media3 FFmpeg JNI (FFmpeg n6.1.1), via build script
  libmeridian_pcm.so    — Listen seek → mono f32le @ 11025 (avformat+swresample)

  export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
  # release CI uses arm64 only:
  MERIDIAN_FFMPEG_ABIS=arm64-v8a bash android/scripts/build-media3-ffmpeg.sh
  # force rebuild (e.g. after enabling meridian_pcm):
  MERIDIAN_FFMPEG_FORCE=1 MERIDIAN_FFMPEG_ABIS=arm64-v8a bash android/scripts/build-media3-ffmpeg.sh
  # or both ABIs:
  bash android/scripts/build-media3-ffmpeg.sh

Sources: FFmpeg n6.1.1 + AndroidX Media 1.4.1 ffmpeg_jni.cc + meridian_pcm_jni.c.
ABIs: arm64-v8a, x86_64. Decoders/demuxers are statically linked.
Listen prefers libmeridian_pcm.so (desktop-shaped), then MediaCodec session
reuse, then Media3 FFmpeg JNI.
