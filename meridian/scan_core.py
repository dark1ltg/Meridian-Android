"""Library walk + mood analysis without Qt.

Desktop ScanWorker / AnalyzeWorker and the Android session both call these helpers
so prune / symlink / sparse-mount behaviour stays in one place.
"""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Callable

from meridian.features import (
    AUDIO_EXTS,
    analyze_audio,
    confidence_low_trust,
    genre_seed,
    mood_confidence,
    read_tags,
)
from meridian.library import Library

AbortFn = Callable[[], bool]
# name / title, 1-based index, total count
ProgressFn = Callable[[str, int, int], None]
AnalyzeProgressFn = ProgressFn


def _resolve(path: Path) -> Path | None:
    try:
        return path.resolve()
    except OSError:
        return None


def _is_under(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def scan_library(
    library: Library,
    *,
    force: bool = False,
    abort: AbortFn | None = None,
    progress: ProgressFn | None = None,
) -> int:
    should_stop = abort or (lambda: False)
    folders = library.folders()
    found: list[str] = []
    prune_roots: list[Path] = []
    # Discover every audio path first so progress can show "12 of 9900".
    discovered: list[tuple[str, Path]] = []
    if progress:
        progress("Looking through folders…", 0, 0)
    for folder in folders:
        if should_stop():
            break
        root = Path(folder)
        if not root.is_dir():
            continue
        root_resolved = _resolve(root)
        if root_resolved is None:
            continue
        root_found: list[str] = []
        walk_ok = True

        def on_walk_error(_err: OSError) -> None:
            nonlocal walk_ok
            walk_ok = False

        for dirpath, dirnames, filenames in os.walk(
            root,
            onerror=on_walk_error,
            followlinks=False,
        ):
            if should_stop():
                break
            keep_dirs: list[str] = []
            for name in dirnames:
                if name.startswith("."):
                    continue
                child = Path(dirpath) / name
                if child.is_symlink():
                    continue
                keep_dirs.append(name)
            dirnames[:] = keep_dirs
            for name in filenames:
                path = Path(dirpath) / name
                if path.suffix.lower() not in AUDIO_EXTS:
                    continue
                if path.is_symlink():
                    resolved = _resolve(path)
                    if resolved is None or not _is_under(resolved, root_resolved):
                        continue
                path_str = str(path)
                root_found.append(path_str)
                discovered.append((path_str, path))
        found.extend(root_found)
        if walk_ok and not should_stop() and root_found:
            known = library.count_tracks_under(root_resolved)
            if known == 0 or len(root_found) * 2 >= known:
                prune_roots.append(root_resolved)

    if should_stop():
        return 0

    total = len(discovered)
    added = 0
    now = time.time()
    if progress and total == 0:
        progress("No audio files found", 0, 0)
    for index, (path_str, path) in enumerate(discovered, start=1):
        if should_stop():
            break
        name = path.name
        if progress:
            progress(name, index, total)
        try:
            st = os.stat(path_str)
        except OSError:
            continue
        mtime = library.existing_mtime(path_str)
        if not force and mtime is not None and abs(mtime - st.st_mtime) < 0.5:
            continue
        tags = read_tags(path_str)
        seed = genre_seed(
            tags["genre"],
            tags["title"],
            tags["artist"],
            path=path_str,
            year=tags.get("year"),
            extra_text=tags.get("extra_text") or "",
            albumartist=tags.get("albumartist") or "",
            composer=tags.get("composer") or "",
            replaygain_db=tags.get("replaygain_db"),
        )
        bpm = tags.get("bpm")
        conf, note = mood_confidence(
            tag_key=seed.tag_key,
            path_key=seed.path_key,
            pcm_ok=False,
            bpm_ok=bpm is not None,
            replaygain=tags.get("replaygain_db") is not None,
            keyword_hit=seed.keyword_hit,
            weak_tags=path.suffix.lower() in {".wav", ".aiff", ".aif"},
        )
        library.upsert_track(
            {
                "path": path_str,
                "title": tags["title"],
                "artist": tags["artist"],
                "album": tags["album"],
                "albumartist": tags.get("albumartist") or "",
                "genre": tags["genre"],
                "duration_ms": tags["duration_ms"],
                "year": tags["year"],
                "bpm": tags["bpm"],
                "valence": seed.valence,
                "energy": seed.energy,
                "mood_confidence": conf,
                "confidence_note": note or "pre-analyze",
                "low_trust": int(confidence_low_trust(conf)),
                "added_at": now,
                "mtime": st.st_mtime,
                "analyzed": 0,
            }
        )
        added += 1
    if found and prune_roots and not should_stop():
        library.delete_missing(found, only_under=prune_roots)
    return added



def _analyze_one_track(library: Library, track_id: int) -> None:
    """Analyze a single track id into *library* (thread-safe via library.lock)."""
    track = library.get(track_id)
    if not track:
        return
    try:
        tags = read_tags(track.path)
        result = analyze_audio(
            track.path,
            track.genre or tags.get("genre") or "",
            track.title,
            track.artist,
            track.bpm if track.bpm is not None else tags.get("bpm"),
            year=track.year if track.year is not None else tags.get("year"),
            extra_text=tags.get("extra_text") or "",
            albumartist=track.albumartist or tags.get("albumartist") or "",
            composer=tags.get("composer") or "",
            replaygain_db=tags.get("replaygain_db"),
            duration_ms=track.duration_ms or int(tags.get("duration_ms") or 0),
        )
        if not result.pcm_ok:
            # No waveform this pass. Prior PCM → keep placement and conclude.
            # Seed-only → still show on the map. Otherwise defer for retry.
            if library.had_pcm_acoustics(track.id):
                library.conclude_kept_prior(track.id)
            elif result.seed_evidence:
                library.set_analyzed_mood(
                    track.id,
                    result.valence,
                    result.energy,
                    result.bpm,
                    confidence=result.confidence,
                    low_trust=result.low_trust,
                    confidence_note=result.confidence_note,
                    onset_consistency=result.onset_consistency,
                    acoustic_flux=result.acoustic_flux,
                    brightness=result.brightness,
                    shape_note=result.shape_note,
                    timbre_flag=result.timbre_flag,
                    timbre_source=result.timbre_source,
                    steer_weight=result.steer_weight,
                    from_pcm=False,
                )
            else:
                library.defer_analyze(track.id)
        else:
            library.set_analyzed_mood(
                track.id,
                result.valence,
                result.energy,
                result.bpm,
                confidence=result.confidence,
                low_trust=result.low_trust,
                confidence_note=result.confidence_note,
                onset_consistency=result.onset_consistency,
                acoustic_flux=result.acoustic_flux,
                brightness=result.brightness,
                shape_note=result.shape_note,
                timbre_flag=result.timbre_flag,
                timbre_source=result.timbre_source,
                steer_weight=result.steer_weight,
            )
    except Exception:
        library.mark_analyze_failed(track_id)


def analyze_pending(
    library: Library,
    *,
    abort: AbortFn | None = None,
    progress: AnalyzeProgressFn | None = None,
    workers: int = 1,
) -> None:
    from meridian.features import clear_decode_abort

    should_stop = abort or (lambda: False)
    clear_decode_abort()
    ids = library.unanalyzed_ids()
    total = len(ids)
    worker_count = max(1, min(int(workers or 1), 2))

    if worker_count == 1 or total <= 1:
        for index, track_id in enumerate(ids, start=1):
            if should_stop():
                break
            track = library.get(track_id)
            if not track:
                continue
            if progress:
                progress(track.short_title, index, total)
            _analyze_one_track(library, track_id)
    else:
        import threading
        from concurrent.futures import ThreadPoolExecutor, as_completed

        progress_lock = threading.Lock()
        done = 0

        def _job(track_id: int) -> None:
            nonlocal done
            if should_stop():
                return
            track = library.get(track_id)
            title = track.short_title if track else str(track_id)
            _analyze_one_track(library, track_id)
            with progress_lock:
                done += 1
                if progress:
                    progress(title, done, total)

        with ThreadPoolExecutor(max_workers=worker_count) as pool:
            futures = [pool.submit(_job, tid) for tid in ids]
            for fut in as_completed(futures):
                if should_stop():
                    for pending in futures:
                        pending.cancel()
                    break
                # Surface worker exceptions instead of hanging the job.
                try:
                    fut.result()
                except Exception:
                    pass

    if not should_stop():
        library.smooth_album_moods()
        library.smooth_artist_moods()
        library.spread_album_acoustics()
        library.rescale_moods_by_percentile()
