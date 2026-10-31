Meridian Android **1.3.6** — first installable APK on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases) page.

Install the `Meridian-1.3.6-arm64.apk` on an **arm64** phone (Unihertz Titan 2 and most current Android devices). Enable “install from this source” when the system asks.

## What’s in this build
- **Listen 1.3.6** — same three-section intro/mid/late analysis as the Linux app
- **Smooth lens** — the mood-map ring tracks your finger at display refresh; the queue updates when you lift or pause
- Lockscreen / notification playback, ~3s crossfade, scan + Listen on device

## Notes
- Minimum **Android 8.0 (API 26)**
- arm64-v8a only (Titan 2 / modern phones). Rebuild locally for x86_64 emulator.
- Signed with the public sideload key in `android/keystore/` so later GitHub APKs can update this install
- Listen prefers MediaCodec; bundled FFmpeg JNI is included when `libffmpegJNI.so` is present in the tree (this first package may rely on platform codecs)

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
