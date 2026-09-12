Bundled Media3 FFmpeg JNI (libffmpegJNI.so). Rebuild with:

  export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
  bash android/scripts/build-media3-ffmpeg.sh

Sources: FFmpeg n6.1.1 + AndroidX Media 1.4.1 ffmpeg_jni.cc.
ABIs: arm64-v8a, x86_64. Decoders are statically linked.
