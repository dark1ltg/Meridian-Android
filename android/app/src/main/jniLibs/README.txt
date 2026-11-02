Bundled native libraries for the Android APK.

Included in the v1.3.6 GitHub release (built by Gradle/CMake):
  lib/arm64-v8a/libmeridian_aubio.so  — aubio 0.4.9 onset/tempo (GPL-3)

Optional Media3 FFmpeg JNI (not in the first v1.3.6 GitHub package).
Rebuild and place under jniLibs before assembleRelease:

  export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
  bash android/scripts/build-media3-ffmpeg.sh

Sources: FFmpeg n6.1.1 + AndroidX Media 1.4.1 ffmpeg_jni.cc.
ABIs: arm64-v8a, x86_64. Decoders are statically linked.
Without libffmpegJNI.so, Listen uses platform MediaCodec only.
