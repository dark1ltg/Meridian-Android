Meridian Android **1.3.6** — APK + source on this repo’s [Releases](https://github.com/dark1ltg/Meridian-Android/releases/tag/v1.3.6) page (refreshed **2026-11-03**, **versionCode 146**).

## Downloads

| Asset | |
|---|---|
| **APK** | [`Meridian-1.3.6-arm64.apk`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk) — ~36.2 MB, arm64-v8a, Android 8.0+ |
| **APK SHA-256** | `b6751f460341426482bc01690bae013654d16b53aa25ceb9347433958028e69b` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-arm64.apk.sha256)) |
| **Source** | [`Meridian-1.3.6-src.tar.gz`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz) — ~0.4 MB, full tree at this release commit |
| **Source SHA-256** | `7a84d1e7c777ad81b0ae91947eead9b0196c196d4a6da91dfbd41b7fd898b6b3` ([`.sha256`](https://github.com/dark1ltg/Meridian-Android/releases/download/v1.3.6/Meridian-1.3.6-src.tar.gz.sha256)) |

```bash
sha256sum -c Meridian-1.3.6-arm64.apk.sha256
adb install -r Meridian-1.3.6-arm64.apk
```

## What’s in this build
- **Now Playing** — title / artist / art / queue highlight follow DualDeck advances (skip and natural next) without a full engine classify per transition
- **Crossfade** — first play after scan commits immediately (queue “current” is not live audio); no phantom fade / deferred play credit until real playback has started
- **UI responsiveness** — cheaper snapshots (no content-URI open per track; skip map why-text); optimistic tap-to-play; deferred Python/cold splash; separate playback clock; throttled nearby count; lighter map galaxy; async cover art; seek commits on release
- **Renew Queue** — Queue / Context **Renew** rebuilds the context queue from the current listen matrix; **Play Context** syncs a stale queue to the lens; Renew keeps what’s playing up front; recent plays stay soft-skipped
- **Lens hold** — aiming updates the matrix without wiping the live queue; stale-queue hint on Queue / Context; tiny lens jitter no longer resets Renew variety
- **Listen speed** — native seek→mono @ 11025 (`libmeridian_pcm.so`), one open per track, MediaStore durations, dual Listen workers; same intro/mid/late windows as desktop
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
- Minimum **Android 8.0 (API 26)**; **versionCode 146** / versionName 1.3.6
- arm64-v8a only (Titan 2 / modern phones)
- Signed with the public sideload key in `android/keystore/` (cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`)
- Listen prefers **MediaCodec**, then **Media3 FFmpeg** when the platform codec cannot open the file
- GPL-3.0-only; source tarball matches the tagged tree (see also `SOURCE_OFFER.txt`)

Linux AppImage stays on [dark1ltg/Meridian releases](https://github.com/dark1ltg/Meridian/releases).
