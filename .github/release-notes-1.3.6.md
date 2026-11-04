Meridian Android **1.3.6** — APK + source on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) page (refreshed **2026-11-03**, **versionCode 151**).

## Downloads

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) — arm64-v8a, Android 8.0+ |
| **APK SHA-256** | see [`Meridian-1.3.6-arm64.apk.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256) (versionCode 151) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) |
| **Source SHA-256** | see [`Meridian-1.3.6-src.tar.gz.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256) |

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

## What’s new in versionCode 151

- GitHub Releases publish date now refreshes when the APK is rebuilt (same tag, new release object)
- Carries forward versionCode **150** bugfixes (Listen cache, import, play races, dig-in lens, skip 8s, single-flight play, Renew lens flush, map nits)

## Product (stable across 1.3.6)

Mood map with dig-in zoom · on-device Listen · Renew / Play Context · NOW/DEEP/FILL/SHELF matrix · ~3s crossfade · lockscreen playback · SAF folders · MediaStore import · Focus/Wander/Charge/Dim · honest “why”

Full feature overview: [README](https://github.com/dark1ltg/Meridian-Android#highlights) · history: [CHANGELOG](https://github.com/dark1ltg/Meridian-Android/blob/main/CHANGELOG.md)

## Notes

- Minimum **Android 8.0 (API 26)**; **versionCode 151** / versionName 1.3.6
- arm64-v8a only (Titan 2 / modern phones)
- Signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`)
- Listen prefers **MediaCodec**, then **Media3 FFmpeg** when the platform codec cannot open the file
- GPL-3.0-only; source tarball matches the tagged tree (see also `SOURCE_OFFER.txt`)

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
