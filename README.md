# Meridian

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**Your library is a night sky. Navigate by feel.**

Meridian is a local, offline music player for **Android 8.0+**. Every track becomes a star on a mood map — **Shadow → Glow**, **Still → Kinetic**. Aim a lens where you want to be, and a context queue builds from that neighborhood. No accounts. No streaming. Your files stay on the phone.

This repository is the Android tree: Kotlin / Jetpack Compose UI, Media3 playback, bundled FFmpeg JNI, native aubio, and the shared Python engine (Chaquopy) used by the [Linux desktop app](https://github.com/dark1ltg/Meridian).

## Download

Get **Meridian 1.3.6** for Android from this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) page (**versionCode 146**):

- **APK:** [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) (~36.2 MB, arm64-v8a; aubio + Media3 FFmpeg JNI + meridian_pcm)
- **APK SHA-256:** `b6751f460341426482bc01690bae013654d16b53aa25ceb9347433958028e69b` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256))
- **Source:** [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) (~0.4 MB; full tree at the release commit)
- **Source SHA-256:** `7a84d1e7c777ad81b0ae91947eead9b0196c196d4a6da91dfbd41b7fd898b6b3` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256))

```bash
# verify, then install
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

On the phone: open the APK, allow install from that source if asked. Needs **Android 8.0+** and an **arm64** device (Titan 2, Pixel, and most current phones). Linux AppImages stay on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).

## Features

- **Mood map** — pan empty sky, drag near the lens to aim it, pinch inside the lens to resize, pinch outside to dive-zoom; star titles appear when zoomed. Lens motion is a local overlay (Titan 2 / 1440² class) so aiming stays at display refresh; HUD fades while aiming; queues follow on lift or idle
- **Empty library** — Index / Folder / Scan / Listen actions on the map when nothing is indexed yet
- **Listen matrix** — Context tab is matrix-only on the night sky (NOW / DEEP / FILL / SHELF)
- **Context queue** — replenishes from the lens, time of day, and listening mode (Focus, Wander, Charge, Dim)
- **Library** — add a real directory via the system picker (SAF grant kept across restarts; Remove drops folder, tracks, and grant); after you allow access (or Scan / Add folder / Index all), Meridian **scans then Listen**s automatically; Scan/Listen show the current filename and **N of total** and can be cancelled
- **Back** — leaves Meridian in overview without replaying splash on return
- **Honest why** — queue reasons use fit, importance, love, pins, play/skip counts, clock band, and mode
- **Background playback** — lockscreen and notification controls; audio keeps going when you switch apps
- **Crossfade** — ~3s overlap between tracks (always-on dual-deck)
- **Decode** — Listen prefers MediaCodec, then Media3 FFmpeg JNI (`libffmpegJNI.so` in the release APK) when the platform codec cannot open a file
- **Local analysis** — tags plus intro / mid / late 12s listens (same 1.3.6 honesty as desktop); bundled **aubio** (`libmeridian_aubio.so`) for tempo/onset cues
- **Pins, love & search** — drag a star to lock its mood; pin/love flash confirmation; debounced search by title, artist, album, or path

Minimum: **Android 8.0 (API 26)**. Default library folder: the device **Music** directory. Grant `READ_EXTERNAL_STORAGE` (Android 8–12) or `READ_MEDIA_AUDIO` (13+).

## Build

Needs **JDK 17+**, Android SDK (platform **34**, build-tools, NDK as pulled by Gradle), and network the first time (Chaquopy + pip wheels).

```bash
cp android/local.properties.example android/local.properties
# edit sdk.dir= to your Android SDK path

cd android
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install on a device or emulator (API 26 or newer):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Python sources under `android/app/src/main/python/meridian/` are copied from the repo `meridian/` package at **preBuild** (desktop UI modules are excluded).

Rebuild the bundled FFmpeg JNI after changing enabled decoders (needs NDK r26+):

```bash
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
bash android/scripts/build-media3-ffmpeg.sh
```

See **[docs/android.md](docs/android.md)** for the full Android port notes.

## Desktop (same engine)

The shared `meridian/` package also powers the Linux PySide6 app. From this tree:

```bash
/usr/bin/python3 -m venv --system-site-packages .venv
.venv/bin/python -m pip install mutagen numpy aubio
bash scripts/run.sh
```

## Documentation

- [Android 8.0+ port](docs/android.md) — SDK setup, permissions, Listen pipeline
- [How Meridian listens](docs/audio-analysis-pipeline.md) — the short listen of each track
- [How stars get placed](docs/placement-pipeline.md) — sticker + listen → mood map
- [Docs index](docs/README.md)
- [Changelog](CHANGELOG.md)

## License

Meridian is free software under the **GNU General Public License v3.0**.
See [LICENSE](LICENSE) / [COPYING](COPYING).

Third-party components: [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).
Ubuntu fonts ship under the Ubuntu Font Licence 1.0 in `resources/fonts/`.
