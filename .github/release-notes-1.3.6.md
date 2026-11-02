Meridian Android **1.3.6** — installable APK on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases) page (published **2026-11-02**; rebuild includes Media3 FFmpeg JNI).

**Download:** [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk)  
Checksum file: [`Meridian-1.3.6-arm64.apk.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256)

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

Install on an **arm64** phone (Unihertz Titan 2 and most current Android devices). Enable “install from this source” when the system asks.

## What’s in this build
- **Listen 1.3.6** — same three-section intro/mid/late analysis as the Linux app
- **Smooth lens** — the mood-map ring tracks your finger at display refresh; the queue updates when you lift or pause
- Lockscreen / notification playback, ~3s crossfade, scan + Listen on device
- Native **aubio** (`libmeridian_aubio.so`) and Media3 **FFmpeg JNI** (`libffmpegJNI.so`) for Listen/playback fallback

## Notes
- Minimum **Android 8.0 (API 26)**
- arm64-v8a only (Titan 2 / modern phones). Rebuild locally for x86_64 emulator.
- Signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`) so later GitHub APKs can update this install
- Listen prefers **MediaCodec**, then **Media3 FFmpeg** when the platform codec cannot open the file

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
