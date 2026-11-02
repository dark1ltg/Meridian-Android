# Android port

Meridian on **Android 8.0+ (API 26)**. Offline only. Same SQLite library, mood lens, NOW/DEEP/FILL/SHELF matrix, and context queue as the Linux app.

Playback uses AndroidX **Media3 / ExoPlayer**. The engine is CPython 3.12 via **Chaquopy**, with `mutagen` and `numpy`. Qt, AppImage, and the desktop window are not shipped in the APK.

## What you get

- Mood map as a nebula: drag empty sky to pan, drag near the lens to aim it, pinch inside the lens to resize, pinch outside to zoom; tap a star to play, drag a star to pin. Titles show when zoomed in. The ring itself is a cheap overlay so aiming stays smooth on a Unihertz Titan 2 (1440×1440, Dimensity 7300 / Mali-G615); HUD mood words fade while aiming; the neighborhood queue updates when you lift or pause.
- Empty library surfaces Index / Folder / Scan / Listen on the map; pin and love flash a short confirmation
- Bottom navigation: Map, Queue, Context (listen matrix on the night sky), More (folders, Listen primary, Scan / Rescan under Advanced)
- Onboarding, music-folder permission, now playing (love + volume), and engine-backed “why this track”
- Play Context starts the neighborhood queue from the lens
- Modes: Focus, Wander, Charge, Dim (mode chip in the top bar; also under More → Audio)
- Debounced search, love, skip / previous
- Add extra folders with the system directory picker. SD / USB trees are walked when they have a real path, or copied into app storage when they do not. **Index all audio on this phone** pulls directories from the system music catalog (MediaStore).

- Scan and Listen report progress once per surface and can be cancelled
- **Lockscreen / background playback** — a foreground `MediaSessionService` keeps audio going when the screen is locked or you switch apps. Notification and lockscreen show title, artist, play/pause, skip, and previous (no album art). Skip/previous still go through the context queue. Opening Meridian from those controls still requires the device lock (PIN, password, pattern, biometric, or face — whatever the phone uses). The app itself is never drawn over a locked keyguard.
- **Crossfade** — two ExoPlayer decks overlap for ~3 seconds (InOutQuad), the same always-on rule as desktop. Skip, pause, and seek cut the fade short. Natural advance starts about a fade-length before the end of a long track.
- **FFmpeg JNI** — Release CI builds `libffmpegJNI.so` into `android/app/src/main/jniLibs/arm64-v8a` via `android/scripts/build-media3-ffmpeg.sh` before packaging. Locally: `MERIDIAN_FFMPEG_ABIS=arm64-v8a bash android/scripts/build-media3-ffmpeg.sh` (or omit the env var for arm64 + x86_64). When present, ExoPlayer can prefer the Media3 FFmpeg renderer and Listen falls back to it when MediaCodec cannot decode the file.

On-device **Listen** uses the same Python mood math as Linux **1.3.6**: three 12s intro/mid/late windows on long tracks, loudness-normalized cues, placement honesty (dialogue drop, pitch Glow, slope, sub-bass), and shape/timbre/steer leftovers for the queue. PCM comes from **MediaCodec** first (MP3/AAC/M4A and other platform codecs), then the **Media3 FFmpeg decoder**. Rhythm uses **aubio** (`libmeridian_aubio.so`). UI type is the same **Ubuntu Sans** / **Ubuntu Condensed** files as desktop (`resources/fonts/`, Ubuntu Font Licence 1.0).

```bash
# rebuild FFmpeg JNI after changing enabled decoders (needs NDK r26+)
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
bash android/scripts/build-media3-ffmpeg.sh
```

## Install (release APK + source)

Published builds are on **[Releases](https://github.com/dark1ltg/Meridian-Android/releases)**. Current: **[v1.3.6](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6)** (2026-11-02, **versionCode 137**).

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) |
| **APK checksum** | [`Meridian-1.3.6-arm64.apk.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) |
| **Source checksum** | [`Meridian-1.3.6-src.tar.gz.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256) |
| **ABI** | arm64-v8a |
| **Native** | `libmeridian_aubio.so` + `libffmpegJNI.so` |

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

The release APK is signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`) so a later GitHub APK can replace this install. Play Store is not used. CI recipe: `.github/workflows/release-apk.yml` (`assembleRelease -Pabi=arm64-v8a` + `git archive` source tarball).

## Build

Needs JDK 17+, Android SDK (platform **34**, build-tools, NDK **26.3.11579264** as pulled by Gradle), and network the first time (Chaquopy + pip wheels).

```bash
cp android/local.properties.example android/local.properties
# edit sdk.dir=

cd android
# phone / Titan 2 (arm64), same recipe as the GitHub release
./gradlew :app:assembleRelease -Pabi=arm64-v8a
# APK: app/build/outputs/apk/release/app-release.apk

# debug build (all configured ABIs)
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install on a device or emulator (API 26 or newer):

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Python sources under `android/app/src/main/python/meridian/` are copied from the repo `meridian/` package at **preBuild** (desktop UI modules are excluded).

## Listen analysis (not desktop `ffmpeg`)

Desktop still shells out to host `ffmpeg`. Android does not:

1. **MediaCodec** — common library formats (MP3, AAC/M4A, platform FLAC, …). Decodes each planned section window (intro / mid / late), downmixes to mono, resamples to **11025 Hz** float32 LE (same contract as `_decode_pcm` on Linux).
2. **Media3 FFmpeg decoder** — `FfmpegAudioDecoder` / `FfmpegAudioRenderer` (Apache 2.0 Java from AndroidX Media) plus `libffmpegJNI.so` (FFmpeg n6.1.1, static libavcodec / libavutil / libswresample). Release CI / `android/scripts/build-media3-ffmpeg.sh`. Enabled decoders: vorbis, opus, flac, alac, mp3, aac, pcm_s16le, pcm_s24le, pcm_f32le, wmav2.
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
