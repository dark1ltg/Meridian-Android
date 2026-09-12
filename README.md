# Meridian

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**Your library is a night sky. Navigate by feel.**

Meridian is a local, offline music player for **Android 8.0+**. Every track becomes a star on a mood map — **Shadow → Glow**, **Still → Kinetic**. Aim a lens where you want to be, and a context queue builds from that neighborhood. No accounts. No streaming. Your files stay on the phone.

This repository is the Android tree: Kotlin / Jetpack Compose UI, Media3 playback, bundled FFmpeg JNI, native aubio, and the shared Python engine (Chaquopy) used by the [Linux desktop app](https://github.com/dark1ltg/Meridian).

## Features

- **Mood map** — pan empty sky, drag near the lens to aim it, pinch inside the lens to resize, pinch outside to dive-zoom; star titles appear when zoomed
- **Listen matrix** — Context tab shows NOW / DEEP / FILL / SHELF
- **Context queue** — replenishes from the lens, time of day, and listening mode (Focus, Wander, Charge, Dim)
- **Library folders** — add a real directory via the system picker; Scan / Listen show progress and can be cancelled
- **Honest why** — queue reasons use fit, importance, love, pins, play/skip counts, clock band, and mode
- **Background playback** — lockscreen and notification controls; audio keeps going when you switch apps
- **Crossfade** — ~3s overlap between tracks (always-on dual-deck)
- **FFmpeg** — bundled Media3 JNI decoder for Listen and playback when the platform codec cannot open a file
- **Local analysis** — tags plus a short mid-track waveform; bundled **aubio** for tempo/onset cues
- **Pins & search** — drag a star to lock its mood; search by title, artist, album, or path

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
