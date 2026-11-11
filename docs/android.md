# Android port

Meridian on **Android 8.0+ (API 26)**. Offline only. Same SQLite library, mood lens, NOW/DEEP/FILL/SHELF matrix, and context queue as the Linux app.

Playback uses AndroidX **Media3 / ExoPlayer**. The engine is CPython 3.12 via **Chaquopy**, with `mutagen` and `numpy`. Qt, AppImage, and the desktop window are not shipped in the APK.

Product overview (features first): **[README](../README.md)** — includes device screenshots under [`screenshots/`](screenshots/).

## Features (what ships in the APK)

| Area | What you get |
|---|---|
| **Mood map** | Nebula sky: pan empty space, drag the lens ring to aim, pinch inside to resize, pinch outside to dig in ~5×; tap a star to play, drag to pin. Titles appear when zoomed and stagger when packed. Smooth aiming on Titan 2–class (1440×1440). |
| **Listen** | After Allow (or Scan / Add folder / Index), Meridian indexes, scans, then waveform-listens automatically — same intro/mid/late honesty as desktop **1.3.6**. Play / skip / the map stay live during Analyze. |
| **Context queue** | **Renew** rebuilds from the listen matrix (not shuffle); **Play Context** syncs a stale queue to the lens without restarting the loaded track; modes Focus / Wander / Charge / Dim. |
| **Matrix** | Context tab: NOW / DEEP / FILL / SHELF on the night sky. |
| **Playback** | ~3s dual-deck crossfade; play continues in other apps (pauses for calls, alarms, other media; navigation prompts duck); play/skip during a call wait until hang-up; Pause during a call does not restart audio on the line; Play after another music app still works; Next during a fade credits the outgoing song; pause+Recents keeps the session; Now Playing / lockscreen / notification Previous–Next drive the queue; cold-start Play loads the restored current; device lock required to open the UI from media controls. |
| **Library** | MediaStore + SAF folders with persistable grants (folder-only libraries boot without broad audio permission); Remove drops folder, tracks, and grant; URI tracks keep artist/album/duration; unreadable files skip forward; import cap **50,000** with a truncation note; progress shows filename + **N of total**. |
| **Chrome** | Map / Queue / Context / More; love, volume, honest “why”, debounced search; Back backgrounds without splash replay. |

Native stack in the release APK: `libmeridian_aubio.so`, `libffmpegJNI.so`, `libmeridian_pcm.so`. UI type is Ubuntu Sans / Ubuntu Condensed (`resources/fonts/`, Ubuntu Font Licence 1.0).

On-device **Listen** uses the same Python mood math as Linux **1.3.6**: three 12s intro/mid/late windows on long tracks, loudness-normalized cues, placement honesty (dialogue drop, pitch Glow, slope, sub-bass), and shape/timbre/steer leftovers for the queue. PCM comes from **`libmeridian_pcm.so`** first, then **MediaCodec**, then the **Media3 FFmpeg decoder**. Rhythm uses **aubio**.

```bash
# rebuild FFmpeg JNI after changing enabled decoders (needs NDK r26+)
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
bash android/scripts/build-media3-ffmpeg.sh
```

Release CI builds `libffmpegJNI.so` via `android/scripts/build-media3-ffmpeg.sh` before packaging. Locally: `MERIDIAN_FFMPEG_ABIS=arm64-v8a bash android/scripts/build-media3-ffmpeg.sh` (or omit the env var for arm64 + x86_64).

## Install (release APK + source)

Published builds are on **[Releases](https://github.com/dark1ltg/Meridian-Android/releases)**. Current: **[v1.3.6](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6)** (**versionCode 158**, published **2026-11-11**).

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) (~36.2 MB) |
| **APK SHA-256** | sibling [`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256) on the GitHub Release (recorded after this build) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) (~5.5 MB) |
| **Source SHA-256** | sibling [`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256) on the GitHub Release (recorded after this build) |
| **ABI** | arm64-v8a |
| **Native** | `libmeridian_aubio.so` + `libffmpegJNI.so` + `libmeridian_pcm.so` |

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

Desktop shells out to host `ffmpeg` (`-ac 1 -ar 11025 -f f32le`). Android matches that contract on-device:

1. **`libmeridian_pcm.so`** (preferred) — FFmpeg avformat seek + swresample → mono float32 LE @ **11025 Hz** in one native pass; **one open per file** for intro/mid/late windows. Built by `android/scripts/build-media3-ffmpeg.sh` alongside Media3 JNI.
2. **MediaCodec session reuse** — if native PCM is missing, open the codec once per file and seek windows (still full-rate → Kotlin resample).
3. **Media3 FFmpeg decoder** — `FfmpegAudioDecoder` + `libffmpegJNI.so` when MediaCodec cannot open the file.
4. **Dual Listen workers** on Android (`analyze_pending(workers=2)`); same 12s intro/mid/late windows as desktop. MediaStore **DURATION** is stored for `content://` tracks so Listen can plan windows instead of a blind ~28s pass.
5. **aubio** — onset/tempo in `libmeridian_aubio.so` (aubio 0.4.9, GPL-3).

## Permissions

| API | Permission |
| --- | --- |
| 26–32 | `READ_EXTERNAL_STORAGE` |
| 33+ | `READ_MEDIA_AUDIO`; `POST_NOTIFICATIONS` is optional and asked once after onboarding (not over splash) |
| playback | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (lockscreen + background audio) |
| folders | persistable `OpenDocumentTree` read grant (SAF-only libraries do not need `READ_MEDIA_AUDIO`) |

Nothing leaves the phone. There is no account, telemetry, or store sync. Import walks at most **50,000** files per MediaStore/SAF pass and says so if the tree is larger.

## Layout

- `meridian/` — shared engine (`library`, `queue_engine`, `scan_core`, `android_session`, …)
- `meridian/ui/` — Linux PySide6 only
- `android/` — Gradle app (`io.github.dark1ltg.meridian`)
