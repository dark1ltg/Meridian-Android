# Android port

Meridian on **Android 8.0+ (API 26)**. Offline only. Same SQLite library, mood lens, NOW/DEEP/FILL/SHELF matrix, and context queue as the Linux app.

Playback uses AndroidX **Media3 / ExoPlayer**. The engine is CPython 3.12 via **Chaquopy**, with `mutagen` and `numpy`. Qt, AppImage, and the desktop window are not shipped in the APK.

## What you get

- Mood map as a nebula: drag empty sky to pan, drag near the lens to aim it, pinch inside the lens to resize, pinch outside to zoom; tap a star to play, drag a star to pin. Titles show when zoomed in.
- Bottom navigation: Map, Queue, Context (listen matrix), More (folders, scan / Listen / rescan)
- Onboarding, music-folder permission, now playing (love + volume), and engine-backed “why this track”
- Play Context starts the neighborhood queue from the lens
- Modes: Focus, Wander, Charge, Dim (under More → Audio)
- Search, love, skip / previous
- Add extra folders with the system directory picker. SD / USB trees are walked when they have a real path, or copied into app storage when they do not. **Index all audio on this phone** pulls directories from the system music catalog (MediaStore).

- Scan and Listen report progress and can be cancelled
- **Lockscreen / background playback** — a foreground `MediaSessionService` keeps audio going when the screen is locked or you switch apps. Notification and lockscreen show title, artist, play/pause, skip, and previous (no album art). Skip/previous still go through the context queue. Opening Meridian from those controls still requires the device lock (PIN, password, pattern, biometric, or face — whatever the phone uses). The app itself is never drawn over a locked keyguard.
- **Crossfade** — two ExoPlayer decks overlap for ~3 seconds (InOutQuad), the same always-on rule as desktop. Skip, pause, and seek cut the fade short. Natural advance starts about a fade-length before the end of a long track.
- **FFmpeg JNI** — `libffmpegJNI.so` is shipped in `android/app/src/main/jniLibs` for `arm64-v8a` and `x86_64`. ExoPlayer prefers the Media3 FFmpeg audio renderer when that library loads; Listen uses it when MediaCodec cannot decode the file.

On-device **Listen** uses the same Python mood math as Linux. PCM comes from **MediaCodec** first (MP3/AAC/M4A and other platform codecs), then the **Media3 FFmpeg decoder** bundled in the APK. Rhythm uses **aubio** compiled into the APK (`libmeridian_aubio.so`). UI type is the same **Ubuntu Sans** / **Ubuntu Condensed** files as desktop (`resources/fonts/`, Ubuntu Font Licence 1.0).

```bash
# rebuild FFmpeg JNI after changing enabled decoders (needs NDK r26+)
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
bash android/scripts/build-media3-ffmpeg.sh
```

## Build

Needs JDK 17+, Android SDK (platform **34**, build-tools, NDK as pulled by Gradle), and network the first time (Chaquopy + pip wheels).

```bash
cp android/local.properties.example android/local.properties
# edit sdk.dir=

cd android
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install on a device or emulator (API 26 or newer):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Python sources under `android/app/src/main/python/meridian/` are copied from the repo `meridian/` package at **preBuild** (desktop UI modules are excluded).

## Listen analysis (not desktop `ffmpeg`)

Desktop still shells out to host `ffmpeg`. Android does not:

1. **MediaCodec** — common library formats (MP3, AAC/M4A, platform FLAC, …). Decodes a mid-track window, downmixes to mono, resamples to **11025 Hz** float32 LE (same contract as `_decode_pcm` on Linux).
2. **Media3 FFmpeg decoder** — `FfmpegAudioDecoder` / `FfmpegAudioRenderer` (Apache 2.0 Java from AndroidX Media) plus bundled `libffmpegJNI.so` (FFmpeg n6.1.1, static libavcodec / libavutil / libswresample). Rebuild with `android/scripts/build-media3-ffmpeg.sh`. Enabled decoders: vorbis, opus, flac, alac, mp3, aac, pcm_s16le, pcm_s24le, pcm_f32le, wmav2.
3. **aubio** — onset/tempo in `libmeridian_aubio.so` (aubio 0.4.9, GPL-3). Python `onset_stats` calls `AubioBridge` over JNI when the `aubio` pip module is absent.

## Permissions

| API | Permission |
| --- | --- |
| 26–32 | `READ_EXTERNAL_STORAGE` |
| 33+ | `READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS` |
| playback | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (lockscreen + background audio) |
| folders | persistable `OpenDocumentTree` read grant |

Nothing leaves the phone. There is no account, telemetry, or store sync.

## Layout

- `meridian/` — shared engine (`library`, `queue_engine`, `scan_core`, `android_session`, …)
- `meridian/ui/` — Linux PySide6 only
- `android/` — Gradle app (`io.github.dark1ltg.meridian`)
