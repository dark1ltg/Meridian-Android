Meridian Android **1.3.6** — APK + source on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) page (refreshed **2026-11-11**, **versionCode 157**).

## Downloads

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) — arm64-v8a, Android 8.0+ |
| **APK SHA-256** | sibling [`Meridian-1.3.6-arm64.apk.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256) on this release |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) — full tree at this release commit |
| **Source SHA-256** | sibling [`Meridian-1.3.6-src.tar.gz.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256) on this release |

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

## What’s new in versionCode 157

- Play and skip during a **call or alarm** wait until it ends (no music over the call)
- Swiping Meridian away during a call still **resumes** after hang-up
- **Turn-by-turn** voice prompts duck volume instead of pausing the song
- Ringtones and alarms are ducked in the first moment they appear; stuck notification ducks time out
- A track that finishes during a call **advances** on resume instead of sitting silent
- Unplugging headphones during a call keeps the queued next track

## Product (stable across 1.3.6)

Mood map with dig-in zoom · on-device Listen (play stays live) · Renew / Play Context · NOW/DEEP/FILL/SHELF matrix · ~3s crossfade · play continues in other apps · Now Playing / lockscreen skip · SAF + MediaStore import (artist/album, 50k cap) · Focus/Wander/Charge/Dim · honest “why”

Full feature overview: [README](https://github.com/dark1ltg/Meridian-Android#highlights) · history: [CHANGELOG](https://github.com/dark1ltg/Meridian-Android/blob/main/CHANGELOG.md)

## Notes

- Minimum **Android 8.0 (API 26)**; **versionCode 157** / versionName 1.3.6
- arm64-v8a only (Titan 2 / modern phones)
- Signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`)
- Listen prefers **native PCM** (`libmeridian_pcm.so`), then **MediaCodec**, then **Media3 FFmpeg** when the platform codec cannot open the file
- GPL-3.0-only; source tarball matches the tagged tree (see also `SOURCE_OFFER.txt`)

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
