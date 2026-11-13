# Documentation

Guides for how Meridian listens to your files and places them on the mood map.

- **Product overview:** [main README](../README.md)
- **Release history:** [CHANGELOG.md](../CHANGELOG.md)

**Android APK + source** (this tree): [Meridian-Android releases](https://github.com/dark1ltg/Meridian-Android/releases) — current **[v1.3.6](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6)** (**versionCode 159**).  
**Linux AppImage** (desktop tree): [Meridian releases](https://github.com/dark1ltg/Meridian/releases).

## System requirements (Android release APK)

| | |
|---|---|
| **OS** | Android **8.0+** (API 26) |
| **CPU** | **arm64-v8a** (Unihertz Titan 2, Pixel, and most current phones) |
| **Install** | Sideload [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) (`adb install -r` or open the file) |
| **APK checksum** | SHA-256 `37e6d069494ba34ad3096331be506aa4b1cb0531866a3f057791f9569efb2724` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256)) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) (~5.5 MB; SHA-256 `152ae21ba9ee4af05275fb3ca7b7bae47a979573f26d026f1a10566bdeb959f2`) |
| **Signature** | Public sideload key in `android/keystore/` (not Play Store); cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9` |

## System requirements (Linux AppImage, sibling desktop tree)

These apply to [dark1ltg/Meridian](https://github.com/dark1ltg/Meridian) AppImages, not to the Android APK. This tree’s current product version is **1.3.6** (`versionName`; Android **versionCode 159**).

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

README screenshots (Map, Context, Queue, Why, Library) live in [`screenshots/`](screenshots/).
