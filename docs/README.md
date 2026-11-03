# Documentation

Extra guides for how Meridian listens to your files and puts them on the mood map.

The [main README](../README.md) is the short “what is this / how do I run it” page.  
Release history is in [CHANGELOG.md](../CHANGELOG.md).

**Android APK + source** (this tree): [Meridian-Android releases](https://github.com/dark1ltg/Meridian-Android/releases) — current **[v1.3.6](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6)** (versionCode **147**, published 2026-11-03).  
**Linux AppImage** (desktop tree): [Meridian releases](https://github.com/dark1ltg/Meridian/releases).

## System requirements (Android release APK)

| | |
|---|---|
| **OS** | Android **8.0+** (API 26) |
| **CPU** | **arm64-v8a** (Unihertz Titan 2, Pixel, and most current phones) |
| **Install** | Sideload [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) (`adb install -r` or open the file) |
| **APK checksum** | SHA-256 `7b5a69d60169177aa346d29bd28ec529bb7f6a3efc3b07114cef43791d8562c8` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256)) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) (~0.4 MB; SHA-256 `f480516fa0976e8de9eb96471c7949162cf906e5d41e2170f4cb8e962682607a`) |
| **Signature** | Public sideload key in `android/keystore/` (not Play Store); cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9` |

## System requirements (release AppImage)

| | |
|---|---|
| **Baseline OS** | Ubuntu 24.04 LTS (x86_64), or equivalent |
| **glibc** | **2.38+** required (Ubuntu 24.04 has **2.39**) |
| **Host tools** | `ffmpeg` for mood analysis; `x264` / `libx264` for H.264 via Qt’s FFmpeg plugin |
| **Display** | Normal desktop GL when available; does **not** force desktop OpenGL by default |

Older glibc (e.g. AlmaLinux 9 / 2.34) will not run the release AppImage. AlmaLinux 10 (glibc 2.39) matches the baseline. Build with `packaging/build-appimage-ubuntu2404.sh` for that target.

If startup fails with `libEGL` / DRI errors, try software rendering:

```bash
LIBGL_ALWAYS_SOFTWARE=1 QT_OPENGL=software ./Meridian-x86_64.AppImage
MERIDIAN_NO_GL=1 ./Meridian-x86_64.AppImage   # skip OpenGL mood-map viewport
```

`MERIDIAN_GL=desktop` opts back into forced desktop OpenGL.

| Guide | In plain words |
|---|---|
| [Android 8.0+ port](android.md) | Build the phone app (Compose + Chaquopy) |
| [How Meridian listens](audio-analysis-pipeline.md) | How it takes a short listen of each song |
| [How stars get placed](placement-pipeline.md) | How that listen (plus tags) becomes a spot on the map |

Screenshots for the README are in [`screenshots/`](screenshots/).
