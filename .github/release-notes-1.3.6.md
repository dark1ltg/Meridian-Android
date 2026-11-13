Meridian Android **1.3.6** — APK + source on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) page (refreshed **2026-11-12**, **versionCode 159**).

## Downloads

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) — ~36.2 MB, arm64-v8a, Android 8.0+ |
| **APK SHA-256** | `c108050b73187d36924e8d32c3859f0c3985e016410e1b3ec36e23f345309e16` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256)) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) — ~5.5 MB, full tree at this release commit |
| **Source SHA-256** | `a471282f2f1765aa2e039a8cdadd0ba78805e0948e2e735051adcf952e6bafdb` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256)) |

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

## What’s new in versionCode 159

- Mini-player on Map and Queue has Previous / Pause / Next; long titles wrap to two lines
- Listen progress is a compact chip instead of a full-width banner; Search is labeled next to the icon
- Queue says “From where you aimed · evening” with an outlined **Renew queue** control
- Empty matrix cells say “Nothing parked yet”; Why this track is plain English (no raw valence/confidence floats)

## What’s new in versionCode 158

- Pause during a call no longer lets Play put music **on the call**; Play after another music app works again
- Next uses the song you are still hearing during a crossfade (position and skip credit)
- Pause then swipe Meridian from Recents keeps the player so you can resume
- Seek/start position survive a call; long notification ducks stay quiet; app-switch no longer dips volume

## Product (stable across 1.3.6)

Mood map with dig-in zoom · on-device Listen (play stays live) · Renew / Play Context · NOW/DEEP/FILL/SHELF matrix · ~3s crossfade · play continues in other apps · Now Playing / lockscreen skip · SAF + MediaStore import (artist/album, 50k cap) · Focus/Wander/Charge/Dim · honest “why”

Full feature overview: [README](https://github.com/dark1ltg/Meridian-Android#highlights) · history: [CHANGELOG](https://github.com/dark1ltg/Meridian-Android/blob/main/CHANGELOG.md)

## Notes

- Minimum **Android 8.0 (API 26)**; **versionCode 159** / versionName 1.3.6
- arm64-v8a only (Titan 2 / modern phones)
- Signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`)
- Listen prefers **native PCM** (`libmeridian_pcm.so`), then **MediaCodec**, then **Media3 FFmpeg** when the platform codec cannot open the file
- GPL-3.0-only; source tarball matches the tagged tree (see also `SOURCE_OFFER.txt`)

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
