Meridian Android **1.3.6** — APK + source on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) page (refreshed **2026-11-02**).

## Downloads

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) — ~35.3 MB, arm64-v8a, Android 8.0+ |
| **APK SHA-256** | `1b564878d5bb6b685d25b92804b864c022ff1c898e3f52cf66f451093d731a07` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256)) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) — ~0.4 MB, full tree at this release commit |
| **Source SHA-256** | `1f6df76829992fcbd2f3456ce3f0673664c8024d12cbaec8072ca8b047283f08` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256)) |

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

## What’s in this build
- **Listen 1.3.6** — intro/mid/late analysis, loudness-normalized Glow/Kinetic, placement honesty, shape/timbre/steer, kept-prior conclude
- **Smooth lens** — map HUD fades while aiming; ring tracks at display refresh; queue commits on lift / idle
- **UI** — empty-library actions on Map, Listen-primary library settings, Context matrix on the night sky, debounced search field, pin/love confirmation
- Lockscreen / notification playback, ~3s crossfade, scan + Listen on device
- Native **aubio** + Media3 **FFmpeg JNI** (`libffmpegJNI.so`) for Listen/playback fallback

## Notes
- Minimum **Android 8.0 (API 26)**; **versionCode 137** / versionName 1.3.6
- arm64-v8a only (Titan 2 / modern phones)
- Signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`)
- Listen prefers **MediaCodec**, then **Media3 FFmpeg** when the platform codec cannot open the file
- GPL-3.0-only; source tarball matches the tagged tree (see also `SOURCE_OFFER.txt`)

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
