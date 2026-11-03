Meridian Android **1.3.6** — APK + source on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) page (refreshed **2026-11-02**, **versionCode 140**).

## Downloads

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) — ~35.3 MB, arm64-v8a, Android 8.0+ |
| **APK SHA-256** | `2059c23bc2bb42f21ad7dfc780310ba92480fc84084ebbb193278901b8bd33af` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256)) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) — ~0.4 MB, full tree at this release commit |
| **Source SHA-256** | `756bd9d9b2b938f9619d30a2b9b860f1b5a8bd29431f49258a642f187bccec6c` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256)) |

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

## What’s in this build
- **Happy path** — Allow → import (MediaStore files/`content:` URIs) → scan → Listen → map → lens → queue/matrix → Play without wiping the library
- **Folders** — Add folder uses SAF + persistable grants; Remove drops the folder, tracks, and grant; **Allow music access** re-prompts after deny
- **Auto Listen** — after you allow file access (or Scan / Add folder / Index all), Meridian scans then starts waveform Listen automatically
- **Play** — PlaybackService `ensure` shares the UI session (no `initialize` wipe on media-session start); unreachable paths skip via `playback_failed`
- **Back / overview** — Back backgrounds the app instead of finishing it; overview resume skips the splash replay
- **Scan progress** — live filename plus **N of total** (and Listen) in the job strip
- **Listen 1.3.6** — intro/mid/late analysis, loudness-normalized Glow/Kinetic, placement honesty, shape/timbre/steer, kept-prior conclude
- **Smooth lens** — map HUD fades while aiming; ring tracks at display refresh; queue commits on lift / idle
- **UI** — empty-library Allow / Index / Folder / Listen on Map, Listen-primary library settings, Context matrix on the night sky, debounced search field, pin/love confirmation
- Lockscreen / notification playback, ~3s crossfade, scan + Listen on device
- Native **aubio** + Media3 **FFmpeg JNI** (`libffmpegJNI.so`) for Listen/playback fallback

## Notes
- Minimum **Android 8.0 (API 26)**; **versionCode 140** / versionName 1.3.6
- arm64-v8a only (Titan 2 / modern phones)
- Signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`)
- Listen prefers **MediaCodec**, then **Media3 FFmpeg** when the platform codec cannot open the file
- GPL-3.0-only; source tarball matches the tagged tree (see also `SOURCE_OFFER.txt`)

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
