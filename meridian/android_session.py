"""Headless listening session for the Android UI (Chaquopy).

Keeps the same mood map, listen matrix, and context queue as desktop Meridian.
Playback itself is Android Media3; this module returns file paths and queue ids.
"""

from __future__ import annotations

import json
import os
import threading
import time
from pathlib import Path

from meridian.context import (
    LENS_RADIUS_DEFAULT,
    LENS_RADIUS_MAX,
    LENS_RADIUS_MIN,
    Mode,
    make_context,
    mode_bias,
)
from meridian.features import CONFIDENCE_HIGH, CONFIDENCE_LOW, request_decode_abort
from meridian.library import Library, Track
from meridian.queue_engine import (
    Quadrant,
    RankedTrack,
    RenewalContext,
    build_plan,
    classify,
)
from meridian.scan_core import analyze_pending, scan_library

_STATE_NAME = "android_ui.json"
# Mood-map units are 0–1; ignore sub-pixel lens jitter for streak / stale.
_LENS_MOVE_EPS = 0.015
_MAX_MAP_STARS = 900
_path_probe = None
# Shared with android_bridge: transport holds this for the whole call; Scan/Listen
# only take it around begin/end/rebuild so play/skip work during Analyze.
STATE_LOCK = threading.RLock()


def set_path_probe(probe: object | None) -> None:
    """Android PathProbe.reachable(path) — used when actually opening a file."""
    global _path_probe
    _path_probe = probe


def _path_listed(path: str) -> bool:
    """Fast catalog filter for snapshots — never opens content: descriptors."""
    raw = (path or "").strip()
    if not raw:
        return False
    if raw.startswith("content:"):
        return True
    try:
        return Path(raw).is_file()
    except OSError:
        return False


def _path_playable(path: str) -> bool:
    """Strict reachability (may open content:). Prefer Kotlin PathProbe at play time."""
    raw = (path or "").strip()
    if not raw:
        return False
    probe = _path_probe
    if probe is not None:
        fn = getattr(probe, "reachable", None)
        if callable(fn):
            try:
                return bool(fn(raw))
            except Exception:
                return False
    return _path_listed(raw)


def _dir_has_audio(folder: str) -> bool:
    """True if a shallow/os.walk finds at least one audio file (permission-gated)."""
    from meridian.features import AUDIO_EXTS

    root = Path(folder)
    try:
        for dirpath, dirnames, filenames in os.walk(root, followlinks=False):
            dirnames[:] = [n for n in dirnames if not n.startswith(".") and not (Path(dirpath) / n).is_symlink()]
            for name in filenames:
                if Path(name).suffix.lower() in AUDIO_EXTS:
                    return True
            # Cap the probe so first boot stays snappy on huge trees.
            if dirpath != str(root) and len(Path(dirpath).relative_to(root).parts) > 2:
                dirnames.clear()
    except OSError:
        return False
    return False


def _clip01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def _queue_why(
    track: Track,
    ranked: RankedTrack | None,
    *,
    band_label: str,
    mode: str,
) -> str:
    """Same intent as desktop `_queue_reason` — why this track is in the queue."""
    lines = [track.label]
    if ranked:
        q = ranked.quadrant
        fit_pct = f"{ranked.fit:.0%}"
        imp_pct = f"{ranked.importance:.0%}"
        if q == Quadrant.NOW:
            lines.append(f"NOW — closest to the lens ({fit_pct} fit), high importance ({imp_pct})")
        elif q == Quadrant.DEEP:
            lines.append(f"DEEP — important ({imp_pct}) but just outside the lens core")
        elif q == Quadrant.FILL:
            lines.append(f"FILL — near the lens ({fit_pct} fit) but lower importance ({imp_pct})")
        else:
            lines.append(f"SHELF — outside the lens area ({fit_pct} fit, {imp_pct} importance)")
        reasons: list[str] = []
        if track.loved:
            reasons.append("♥ loved — boosted importance")
        if track.pinned:
            reasons.append("pinned mood position on the map")
        if track.play_count >= 4:
            reasons.append(f"played {track.play_count}× — familiar pick")
        elif track.play_count == 0:
            reasons.append("never played — fresh discovery")
        if track.skip_count > track.play_count and track.skip_count >= 3:
            reasons.append(f"skipped often ({track.skip_count}×) — deprioritized")
        reasons.append(f"clock band: {band_label}")
        reasons.append(f"mode: {mode.title()}")
        reasons.append(f"mood: valence {track.valence:.2f}, energy {track.energy:.2f}")
        conf = float(track.mood_confidence or 0.5)
        if track.pinned:
            reasons.append("confidence 1.00 (pinned)")
        else:
            reasons.append(f"confidence {conf:.2f}")
            note = (track.confidence_note or "").strip()
            if note:
                reasons.append(note)
            if conf < CONFIDENCE_LOW:
                reasons.append("weak placement evidence (dimmed)")
            elif conf < CONFIDENCE_HIGH:
                reasons.append("partial placement evidence")
        if reasons:
            lines.append("Why: " + " · ".join(reasons))
    else:
        lines.append("Added to the queue directly")
    return "\n".join(lines)


class AndroidSession:
    def __init__(self) -> None:
        self.library: Library | None = None
        self.mode = Mode.WANDER
        self.lens_x = 0.5
        self.lens_y = 0.5
        self.lens_radius = LENS_RADIUS_DEFAULT
        self.skip_pressure = 0.0
        self.explicit_ids: list[int] = []
        self.queue_ids: list[int] = []
        self.queue_index = 0
        self.search_query = ""
        self.status = ""
        self.played_history: list[int] = []
        # Consecutive Renew Queue presses since last meaningful lens/mode change.
        self._renew_streak = 0
        # Lens used to build the live context queue; moves mark the queue stale.
        self._queue_lens: tuple[float, float, float] | None = None
        self._queue_stale = False
        self._data_dir: Path | None = None
        self._abort = threading.Event()
        self._job_kind = ""
        self._job_message = ""
        self._job_current = 0
        self._job_total = 0
        self._job_serial = 0
        self._xfade_out: int | None = None
        self._xfade_settle_finish = False
        self._pending_play: int | None = None
        self._playable_cache: list[Track] | None = None

    def initialize(self, data_dir: str, default_music: str = "") -> dict:
        self.close()
        root = Path(data_dir)
        root.mkdir(parents=True, exist_ok=True)
        os.environ["MERIDIAN_DATA_DIR"] = str(root)
        self._data_dir = root
        self.library = Library(root / "library.sqlite")
        self._load_ui_state()
        music = (default_music or "").strip()
        # Only auto-watch Music when it actually contains audio we can list —
        # otherwise Android shows an empty "Music" row before the user grants
        # access and Scan looks broken.
        if (
            music
            and Path(music).is_dir()
            and not self.library.folders()
            and _dir_has_audio(music)
        ):
            self.library.add_folder(music)
            self.status = f"Watching {music}"
        return self.snapshot()

    def ensure(self, data_dir: str) -> dict:
        """Open the session if needed without wiping an already-live library."""
        root = Path(data_dir)
        if self.library is not None and self._data_dir == root:
            return self.snapshot()
        return self.initialize(str(root), "")

    def close(self) -> None:
        if self.library:
            self.library.close()
            self.library = None

    def _require(self) -> Library:
        if self.library is None:
            raise RuntimeError("session is not initialized")
        return self.library

    def _state_path(self) -> Path:
        assert self._data_dir is not None
        return self._data_dir / _STATE_NAME

    def _load_ui_state(self) -> None:
        path = self._state_path()
        if not path.is_file():
            return
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return
        try:
            self.mode = Mode(str(data.get("mode") or "wander"))
        except ValueError:
            self.mode = Mode.WANDER
        self.lens_x = _clip01(data.get("lens_x", 0.5))
        self.lens_y = _clip01(data.get("lens_y", 0.5))
        self.lens_radius = max(
            LENS_RADIUS_MIN, min(LENS_RADIUS_MAX, float(data.get("lens_radius", LENS_RADIUS_DEFAULT)))
        )
        self.skip_pressure = _clip01(data.get("skip_pressure", 0.0))
        self._renew_streak = max(0, min(3, int(data.get("renew_streak", 0) or 0)))
        ql = data.get("queue_lens")
        if isinstance(ql, (list, tuple)) and len(ql) >= 3:
            self._queue_lens = (_clip01(ql[0]), _clip01(ql[1]), float(ql[2]))
        else:
            self._queue_lens = (self.lens_x, self.lens_y, self.lens_radius)
        self._queue_stale = bool(data.get("queue_stale", False))

    def _save_ui_state(self) -> None:
        if self._data_dir is None:
            return
        payload = {
            "mode": self.mode.value,
            "lens_x": self.lens_x,
            "lens_y": self.lens_y,
            "lens_radius": self.lens_radius,
            "skip_pressure": self.skip_pressure,
            "renew_streak": self._renew_streak,
            "queue_stale": self._queue_stale,
            "queue_lens": list(self._queue_lens)
            if self._queue_lens is not None
            else [self.lens_x, self.lens_y, self.lens_radius],
        }
        try:
            self._state_path().write_text(json.dumps(payload), encoding="utf-8")
        except OSError:
            pass

    def _lens_delta(
        self, other: tuple[float, float, float] | None
    ) -> tuple[float, float, float]:
        if other is None:
            return (1.0, 1.0, 1.0)
        return (
            abs(self.lens_x - other[0]),
            abs(self.lens_y - other[1]),
            abs(self.lens_radius - other[2]),
        )

    def _lens_moved(self, other: tuple[float, float, float] | None) -> bool:
        dx, dy, dr = self._lens_delta(other)
        return dx > _LENS_MOVE_EPS or dy > _LENS_MOVE_EPS or dr > _LENS_MOVE_EPS

    def _remember_queue_lens(self) -> None:
        self._queue_lens = (self.lens_x, self.lens_y, self.lens_radius)
        self._queue_stale = False

    def set_lens(self, x: float, y: float, radius: float | None = None) -> dict:
        self.lens_x = _clip01(x)
        self.lens_y = _clip01(y)
        if radius is not None:
            self.lens_radius = max(LENS_RADIUS_MIN, min(LENS_RADIUS_MAX, float(radius)))
        # Desktop: lens refreshes the matrix only. The context queue holds until
        # it runs low, Play Context syncs it, or the user taps Renew Queue.
        # Ignore sub-eps jitter so renew streak survives tiny wobbles.
        if self._lens_moved(self._queue_lens):
            self._queue_stale = True
            self._renew_streak = 0
        self._save_ui_state()
        # Lite: skip matrix "why" strings — map/matrix still reclassify for the new lens.
        return self.snapshot(lite=True)

    def set_mode(self, mode: str) -> dict:
        try:
            self.mode = Mode(mode.lower())
        except ValueError:
            self.mode = Mode.WANDER
        self._save_ui_state()
        self._renew_streak = 0
        self._rebuild_queue(keep_current=True)
        self.status = f"Mode {self.mode.value} · context queue refreshed"
        return self.snapshot()

    def renew_queue(self) -> dict:
        """Fresh context queue from the current listen matrix (desktop Renew Queue).

        Soft-demotes the previous queue via RenewalContext — not shuffle, not skips,
        not library Rescan. Current track stays up front if still playable.
        """
        lib = self._require()
        tracks = self._playable_tracks()
        prior = list(self.queue_ids)
        current = self._current_track()
        current_id = current.id if current else None
        exempt = set(self.explicit_ids)
        if current_id is not None:
            exempt.add(current_id)
        hard = {t.id for t in lib.all_tracks() if lib.is_playback_denied(t.id)}
        # Match normal rebuild / desktop replenish: soft-skip recent plays.
        exclude = set(self.played_history[-24:])
        renewal = RenewalContext(
            prior_queue_ids=frozenset(prior),
            renew_streak=self._renew_streak,
            exempt_ids=frozenset(exempt),
            heard_ids=frozenset(self.played_history),
        )
        plan = build_plan(
            tracks,
            self._context(),
            self.explicit_ids,
            length=18,
            exclude_ids=exclude,
            hard_exclude_ids=hard,
            renewal=renewal,
        )
        order = list(plan.order)
        playable_ids = {t.id for t in tracks}
        if current_id and current_id not in hard and current_id in playable_ids:
            if current_id in order:
                order.remove(current_id)
            # Keep playing at the front so earlier plan slots cannot strand.
            order.insert(0, current_id)
        self.queue_ids = order
        self.queue_index = 0 if self.queue_ids else 0
        self._renew_streak = min(3, self._renew_streak + 1)
        self._remember_queue_lens()
        self.status = "Context queue renewed from the current matrix."
        self._save_ui_state()
        return self.snapshot()

    def play_context(self) -> dict:
        """Sync the context queue to the current lens when stale, then ready to play."""
        if self._queue_stale or not self.queue_ids:
            self._rebuild_queue(keep_current=bool(self.queue_ids))
            self.status = "Context queue synced to the lens."
        return self.snapshot()

    def add_folder(self, path: str) -> dict:
        self._add_folder_path(path)
        return self.snapshot()

    def add_folders(self, paths: list | None = None) -> dict:
        added = 0
        for raw in paths or []:
            try:
                self._add_folder_path(str(raw))
                added += 1
            except FileNotFoundError:
                continue
        self.status = f"Watching {added} more folder{'s' if added != 1 else ''}" if added else self.status
        return self.snapshot()

    def add_uri_tracks(self, tracks: list | None = None) -> dict:
        """Index automat document URIs that have no readable filesystem path."""
        lib = self._require()
        now = time.time()
        added = 0
        for raw in tracks or []:
            if isinstance(raw, str):
                path, title = raw.strip(), ""
            elif isinstance(raw, dict):
                path = str(raw.get("path") or raw.get("uri") or "").strip()
                title = str(raw.get("title") or "").strip()
            else:
                continue
            if not path.startswith("content:"):
                continue
            # Trust MediaStore/SAF URIs at index time; play-time PathProbe verifies.
            name = title or path.rsplit("/", 1)[-1]
            duration_ms = 0
            artist = ""
            album = ""
            if isinstance(raw, dict):
                try:
                    duration_ms = int(float(raw.get("duration_ms") or 0))
                except (TypeError, ValueError):
                    duration_ms = 0
                artist = str(raw.get("artist") or "").strip()
                album = str(raw.get("album") or "").strip()
            lib.upsert_track(
                {
                    "path": path,
                    "title": Path(name).stem or name or "Unknown",
                    "artist": artist,
                    "album": album,
                    "albumartist": "",
                    "genre": "",
                    "duration_ms": max(0, duration_ms),
                    "year": None,
                    "bpm": None,
                    "valence": 0.5,
                    "energy": 0.5,
                    "mood_confidence": 0.35,
                    "confidence_note": "shared storage — listen for a firmer place",
                    "low_trust": 1,
                    "added_at": now,
                    "mtime": now,
                    "analyzed": 0,
                }
            )
            added += 1
        self.status = (
            f"Indexed {added} file{'s' if added != 1 else ''} from shared storage" if added else self.status
        )
        if added:
            self._invalidate_playable_cache()
            self._rebuild_queue(keep_current=True)
        return self.snapshot()

    def _add_folder_path(self, path: str) -> None:
        lib = self._require()
        raw = str(path or "").strip()
        if not raw:
            raise FileNotFoundError("empty folder path")
        # SAF tree URIs are registered as folder markers when Android cannot
        # expose a listable filesystem path. scan_library skips non-dirs;
        # tracks arrive via add_uri_tracks.
        if raw.startswith("content:"):
            lib.add_folder(raw)
            self.status = "Added shared folder"
            return
        folder = str(Path(raw))
        if not Path(folder).is_dir():
            raise FileNotFoundError(f"not a folder: {folder}")
        lib.add_folder(folder)
        self.status = f"Added {folder}"

    def remove_folder(self, path: str) -> dict:
        lib = self._require()
        raw = str(path or "").strip()
        lib.remove_folder(raw)
        # Drop tracks that lived under this folder (filesystem prefix or the
        # exact content: tree / document URIs from the same grant).
        dead: list[str] = []
        if raw.startswith("content:"):
            tree = raw.rstrip("/")
            for track in lib.all_tracks():
                p = str(track.path or "")
                if p == tree or p.startswith(tree + "/") or p.startswith(tree + "%"):
                    dead.append(p)
                elif "tree/" in tree and p.startswith("content:") and tree.split("/tree/")[-1] in p:
                    dead.append(p)
        else:
            root = raw.rstrip("/")
            prefix = root + "/"
            for track in lib.all_tracks():
                p = str(track.path or "")
                if p == root or p.startswith(prefix):
                    dead.append(p)
        if dead:
            lib.delete_paths(dead)
            self._invalidate_playable_cache()
            self._rebuild_queue(keep_current=True)
        self.status = "Folder removed"
        return self.snapshot()

    def request_abort(self, serial: int | None = 0) -> dict:
        """Abort only the job [serial] names. A late abort must not cancel a newer Scan."""
        try:
            want = int(serial or 0)
        except (TypeError, ValueError):
            want = 0
        if want <= 0 or want != self._job_serial or not self._job_kind:
            return self.progress()
        self._abort.set()
        request_decode_abort()
        self._job_message = "Stopping…"
        return self.progress()

    def progress(self) -> dict:
        return {
            "ok": True,
            "error": "",
            "status": self._job_message,
            "job": self._job_dict(),
        }

    def _job_dict(self) -> dict:
        return {
            "kind": self._job_kind,
            "message": self._job_message,
            "current": self._job_current,
            "total": self._job_total,
            "running": bool(self._job_kind),
            "serial": self._job_serial,
        }

    def _begin_job(self, kind: str, message: str) -> None:
        self._job_serial += 1
        self._abort.clear()
        self._job_kind = kind
        self._job_message = message
        self._job_current = 0
        self._job_total = 0

    def _end_job(self) -> None:
        self._job_kind = ""
        self._job_message = ""
        self._job_current = 0
        self._job_total = 0
        self._abort.clear()

    def scan(self, force: bool = False) -> dict:
        lib = self._require()
        with STATE_LOCK:
            self._begin_job("scan", "Looking through folders…")
        try:
            added = scan_library(
                lib,
                force=force,
                abort=self._abort.is_set,
                progress=self._on_scan_progress,
            )
            with STATE_LOCK:
                if self._abort.is_set():
                    self.status = "Scan stopped"
                else:
                    self.status = f"Indexed {added} new or changed files · context queue refreshed"
                self._invalidate_playable_cache()
                self._rebuild_queue(keep_current=True)
        finally:
            with STATE_LOCK:
                self._end_job()
        with STATE_LOCK:
            return self.snapshot()

    def analyze(self) -> dict:
        lib = self._require()
        with STATE_LOCK:
            self._begin_job("listen", "Listening…")
        try:
            # Two Listen workers: each thread keeps its own decoder session.
            # Same mood windows as desktop — parallel files only.
            # STATE_LOCK is released for the long decode so play/skip can run.
            analyze_pending(
                lib,
                abort=self._abort.is_set,
                progress=self._on_listen_progress,
                workers=2,
            )
            with STATE_LOCK:
                if self._abort.is_set():
                    self.status = "Listen stopped"
                else:
                    self.status = "Mood listen finished · context queue refreshed"
                # Analyze writes new moods into SQLite; drop the cached Track rows so
                # the map/queue show Listen results instead of pre-Listen guesses.
                self._invalidate_playable_cache()
                self._rebuild_queue(keep_current=True)
        finally:
            with STATE_LOCK:
                self._end_job()
        with STATE_LOCK:
            return self.snapshot()

    def rescan(self) -> dict:
        lib = self._require()
        with STATE_LOCK:
            self._begin_job("rescan", "Rescanning…")
        try:
            lib.mark_all_pending_analysis()
            added = scan_library(
                lib,
                force=True,
                abort=self._abort.is_set,
                progress=self._on_scan_progress,
            )
            if not self._abort.is_set():
                with STATE_LOCK:
                    self._job_kind = "listen"
                    self._job_message = "Listening…"
                analyze_pending(
                    lib,
                    abort=self._abort.is_set,
                    progress=self._on_listen_progress,
                    workers=2,
                )
            with STATE_LOCK:
                if self._abort.is_set():
                    self.status = "Rescan stopped"
                else:
                    self.status = f"Rescan complete ({added} files refreshed) · context queue refreshed"
                self._invalidate_playable_cache()
                self._rebuild_queue(keep_current=True)
        finally:
            with STATE_LOCK:
                self._end_job()
        with STATE_LOCK:
            return self.snapshot()

    def _on_scan_progress(self, name: str, index: int = 0, total: int = 0) -> None:
        self._job_current = int(index)
        self._job_total = int(total)
        if total > 0 and index > 0:
            self._job_message = f"{name} — {index} of {total}"
        elif name:
            self._job_message = name
        else:
            self._job_message = "Scanning…"

    def _on_listen_progress(self, title: str, index: int, total: int) -> None:
        self._job_current = index
        self._job_total = total
        if total > 0 and index > 0:
            self._job_message = f"{title} — {index} of {total}"
        else:
            self._job_message = title

    def pin(self, track_id: int, valence: float, energy: float) -> dict:
        # Desktop: pin refreshes the matrix only — hold the live context queue.
        self._require().set_mood(int(track_id), _clip01(valence), _clip01(energy), pinned=True)
        self._invalidate_playable_cache()
        self.status = "Mood pinned"
        return self.snapshot()

    def love(self, track_id: int) -> dict:
        loved = self._require().toggle_loved(int(track_id))
        self._invalidate_playable_cache()
        self.status = "Loved" if loved else "Unloved"
        return self.snapshot()

    def search(self, query: str) -> dict:
        self.search_query = query.strip()
        return self.snapshot()

    def play(self, track_id: int, position_ms: int = 0, natural_advance: bool = False) -> dict:
        lib = self._require()
        track = lib.get(int(track_id))
        if not track:
            raise KeyError(f"unknown track {track_id}")
        outgoing = self._current_track()
        outgoing_id = outgoing.id if outgoing is not None else None
        # Queue index alone is not live audio — only crossfade-credit when a play
        # was already committed or a fade is already armed (matches real transport).
        live_playback = self._pending_play is not None or bool(self.played_history)
        self._interrupt_crossfade_credit(int(track_id))
        if track.id not in self.queue_ids:
            self.explicit_ids = [track.id] + [i for i in self.explicit_ids if i != track.id]
            self._rebuild_queue(keep_current=False)
            if track.id in self.queue_ids:
                self.queue_index = self.queue_ids.index(track.id)
            else:
                self.queue_ids.insert(0, track.id)
                self.queue_index = 0
        else:
            self.queue_index = self.queue_ids.index(track.id)
        self.skip_pressure = max(0.0, self.skip_pressure - 0.18)
        self.status = track.label
        if (
            live_playback
            and outgoing_id is not None
            and outgoing_id != track.id
        ):
            self._pending_play = track.id
            self._xfade_out = outgoing_id
            if natural_advance:
                self._xfade_settle_finish = True
            else:
                self._xfade_settle_finish = False
                self._credit_listen(outgoing_id, int(position_ms or 0))
        else:
            self._clear_crossfade_credit()
            self._commit_play(track.id)
        self._save_ui_state()
        return self.snapshot()

    def fade_settled(self) -> dict:
        """Commit deferred play / finish-nudge after a crossfade lands or is cut."""
        if self._xfade_settle_finish and self._xfade_out is not None:
            self._listen_nudge(self._xfade_out, skipped=False)
        credit = self._pending_play
        current = self._current_track()
        self._clear_crossfade_credit()
        if credit is not None and current is not None and credit == current.id:
            self._commit_play(credit)
        self._save_ui_state()
        return self.snapshot()

    def skip(self, position_ms: int = 0, from_id: int = 0) -> dict:
        lib = self._require()
        current = self._current_track()
        # Near-end finished_current may already have advanced; don't skip the next song too.
        if from_id and current is not None and int(current.id) != int(from_id):
            return self.snapshot()
        if self._xfade_out is not None:
            outgoing = self._xfade_out
            settle = self._xfade_settle_finish
            pending = self._pending_play
            current = self._current_track()
            self._clear_crossfade_credit()
            if settle:
                self._listen_nudge(outgoing, skipped=False)
            if pending is not None and current is not None and pending == current.id:
                self._commit_play(pending)
            skipped = bool(current and int(position_ms or 0) < 8000)
            if current:
                if skipped:
                    lib.record_skip(current.id)
                    self.skip_pressure = min(1.0, self.skip_pressure + 0.22)
                self._listen_nudge(current.id, skipped=skipped)
            self._save_ui_state()
            return self._advance_queue(record_next=True)
        current = self._current_track()
        if current:
            # Same 8s rule as fade-interrupt / desktop — a long listen is not a skip.
            skipped = int(position_ms or 0) < 8000
            if skipped:
                lib.record_skip(current.id)
                self.skip_pressure = min(1.0, self.skip_pressure + 0.22)
            self._listen_nudge(current.id, skipped=skipped)
            self._save_ui_state()
        return self._advance_queue(record_next=True)

    def finished_current(self, ended_id: int = 0) -> dict:
        """Natural end — finish-nudge the outgoing track when the fade settles.

        [ended_id] is the media that actually reached the end. If the UI already
        skipped/played away from it, do not advance again (avoids double-step).
        """
        current = self._current_track()
        if ended_id and current is not None and int(current.id) != int(ended_id):
            return self.snapshot()
        outgoing_id = current.id if current else None
        self.skip_pressure = max(0.0, self.skip_pressure - 0.12)
        snap = self._advance_queue(record_next=False)
        nxt = self._current_track()
        if outgoing_id is not None and nxt is not None and nxt.id != outgoing_id:
            self._xfade_out = outgoing_id
            self._xfade_settle_finish = True
            self._pending_play = nxt.id
        elif nxt is not None:
            self._commit_play(nxt.id)
        self._save_ui_state()
        return snap if nxt is None else self.snapshot()

    def previous(self) -> dict:
        lib = self._require()
        if self._xfade_out is not None:
            outgoing = self._xfade_out
            settle = self._xfade_settle_finish
            self._clear_crossfade_credit()
            if settle:
                self._listen_nudge(outgoing, skipped=False)
            if outgoing in self.queue_ids:
                self.queue_index = self.queue_ids.index(outgoing)
            nxt = self._current_track()
            if nxt:
                self.status = nxt.label
            self._save_ui_state()
            return self.snapshot()
        if self.queue_index > 0:
            self.queue_index -= 1
        nxt = self._current_track()
        if nxt:
            lib.record_play(nxt.id, time.time())
            self.status = nxt.label
        return self.snapshot()

    def playback_failed(self, track_id: int = 0) -> dict:
        """Drop a file the player could not open. Do not skip a different track."""
        lib = self._require()
        current = self._current_track()
        tid = int(track_id or 0)
        if tid <= 0 and current is not None:
            tid = current.id
        if tid:
            lib.mark_playback_failed(tid)
            self._invalidate_playable_cache()
        self.status = "Skipped unreadable file"
        if current is not None and current.id == tid:
            return self._advance_queue(record_next=True)
        if tid:
            self.queue_ids = [i for i in self.queue_ids if i != tid]
            if self.queue_ids:
                self.queue_index = min(self.queue_index, len(self.queue_ids) - 1)
            else:
                self.queue_index = 0
        return self.snapshot()

    def _clear_crossfade_credit(self) -> None:
        self._xfade_out = None
        self._xfade_settle_finish = False
        self._pending_play = None

    def _interrupt_crossfade_credit(self, incoming_id: int) -> None:
        if self._xfade_out is None:
            return
        prior = self._xfade_out
        settle = self._xfade_settle_finish
        pending = self._pending_play
        current = self._current_track()
        self._clear_crossfade_credit()
        if prior != incoming_id and settle:
            self._listen_nudge(prior, skipped=False)
        if (
            pending is not None
            and pending != incoming_id
            and current is not None
            and pending == current.id
        ):
            self._commit_play(pending)

    def _listen_nudge(self, track_id: int, *, skipped: bool) -> None:
        self._require().nudge_mood_from_listen(
            track_id,
            lens_x=self.lens_x,
            lens_y=self.lens_y,
            skipped=skipped,
        )
        # Mood / skip counters live on Track rows used by Renew and the map.
        self._invalidate_playable_cache()

    def _credit_listen(self, track_id: int, position_ms: int) -> None:
        skipped = int(position_ms) < 8000
        if skipped:
            self._require().record_skip(track_id)
            self.skip_pressure = min(1.0, self.skip_pressure + 0.22)
        self._listen_nudge(track_id, skipped=skipped)

    def _commit_play(self, track_id: int) -> None:
        self._require().record_play(track_id, time.time())
        self.played_history.append(track_id)
        self.played_history = self.played_history[-48:]
        self._invalidate_playable_cache()

    def _advance_queue(self, *, record_next: bool) -> dict:
        lib = self._require()
        self.queue_index += 1
        if self.queue_index >= len(self.queue_ids):
            self._rebuild_queue(keep_current=False)
            self.queue_index = 0
        nxt = self._current_track()
        if nxt:
            if record_next:
                lib.record_play(nxt.id, time.time())
                self.played_history.append(nxt.id)
                self.played_history = self.played_history[-48:]
            self.status = nxt.label
        return self.snapshot()

    def _context(self):
        _, _, scale = mode_bias(self.mode)
        return make_context(
            self.mode,
            self.lens_x,
            self.lens_y,
            self.lens_radius * scale,
            self.skip_pressure,
        )

    def _current_track(self) -> Track | None:
        lib = self._require()
        if not self.queue_ids or self.queue_index >= len(self.queue_ids):
            return None
        return lib.get(self.queue_ids[self.queue_index])

    def _invalidate_playable_cache(self) -> None:
        self._playable_cache = None

    def _playable_tracks(self) -> list[Track]:
        if self._playable_cache is not None:
            return self._playable_cache
        lib = self._require()
        # Cheap path listing only — content: open happens at play time on Android.
        cached = [
            t
            for t in lib.all_tracks()
            if not lib.is_playback_denied(t.id) and _path_listed(t.path)
        ]
        self._playable_cache = cached
        return cached

    def _map_stars(
        self,
        tracks: list[Track],
        by_id: dict[int, RankedTrack],
        current: Track | None,
        band: str,
    ) -> list[dict]:
        budget = _MAX_MAP_STARS
        chosen: list[Track]
        if len(tracks) <= budget:
            chosen = tracks
        else:
            current_id = current.id if current else None
            scored = sorted(
                tracks,
                key=lambda t: (
                    2 if t.id == current_id else 0,
                    1 if t.pinned or t.loved else 0,
                    by_id[t.id].importance if t.id in by_id else 0.0,
                ),
                reverse=True,
            )
            chosen = scored[:budget]
        # Map dots never show "why" — skip the string build for up to N stars.
        return [
            self._track_dict(t, by_id.get(t.id), band_label=band, include_why=False)
            for t in chosen
        ]

    def _rebuild_queue(self, *, keep_current: bool) -> None:
        lib = self._require()
        tracks = self._playable_tracks()
        current_id = None
        if keep_current and self.queue_ids and 0 <= self.queue_index < len(self.queue_ids):
            current_id = self.queue_ids[self.queue_index]
        hard = {t.id for t in lib.all_tracks() if lib.is_playback_denied(t.id)}
        exclude = set(self.played_history[-24:])
        plan = build_plan(
            tracks,
            self._context(),
            self.explicit_ids,
            length=18,
            exclude_ids=exclude,
            hard_exclude_ids=hard,
        )
        order = list(plan.order)
        playable_ids = {t.id for t in tracks}
        if current_id and current_id not in hard and current_id in playable_ids:
            if current_id in order:
                order.remove(current_id)
            order.insert(0, current_id)
        self.queue_ids = order
        self.queue_index = 0 if self.queue_ids else 0
        self._remember_queue_lens()
        self._save_ui_state()

    def _track_dict(
        self,
        track: Track,
        ranked: RankedTrack | None = None,
        *,
        band_label: str = "",
        include_why: bool = True,
    ) -> dict:
        q = ranked.quadrant.value if ranked else ""
        return {
            "id": track.id,
            "path": track.path,
            "title": track.title or Path(track.path).stem,
            "artist": track.artist or track.albumartist or "Unknown",
            "album": track.album or "",
            "duration_ms": track.duration_ms,
            "valence": track.valence,
            "energy": track.energy,
            "confidence": track.mood_confidence,
            "low_trust": bool(track.low_trust),
            "pinned": bool(track.pinned),
            "loved": bool(track.loved),
            "quadrant": q,
            "fit": round(ranked.fit, 4) if ranked else 0.0,
            "importance": round(ranked.importance, 4) if ranked else 0.0,
            "play_count": track.play_count,
            "skip_count": track.skip_count,
            "confidence_note": track.confidence_note or "",
            "why": (
                _queue_why(track, ranked, band_label=band_label, mode=self.mode.value)
                if include_why
                else ""
            ),
        }

    def snapshot(self, *, lite: bool = False) -> dict:
        """Full UI state. ``lite`` skips matrix why-text (lens commits)."""
        lib = self._require()
        tracks = self._playable_tracks()
        catalog = lib.all_tracks()
        ctx = self._context()
        ranked = classify(tracks, ctx, set(self.explicit_ids))
        by_id: dict[int, RankedTrack] = {item.track.id: item for item in ranked}
        by_q: dict[str, list[dict]] = {q.value: [] for q in Quadrant}
        band = ctx.band_label
        include_matrix_why = not lite
        for item in ranked:
            row = self._track_dict(
                item.track, item, band_label=band, include_why=include_matrix_why
            )
            by_q[item.quadrant.value].append(row)
        for key in by_q:
            by_q[key] = by_q[key][:24]
        if not self.queue_ids and tracks:
            self._rebuild_queue(keep_current=False)
        queue = []
        for tid in self.queue_ids:
            tr = lib.get(tid)
            if tr:
                queue.append(self._track_dict(tr, by_id.get(tid), band_label=band))
        current = self._current_track()
        hits = lib.search(self.search_query) if self.search_query else []
        stars = self._map_stars(tracks, by_id, current, band)
        return {
            "ok": True,
            "error": "",
            "status": self.status,
            "track_count": len(catalog),
            "folders": lib.folders(),
            "mode": self.mode.value,
            "band": ctx.band.value,
            "band_label": ctx.band_label,
            "hour": ctx.hour,
            "skip_pressure": self.skip_pressure,
            "lens": {
                "x": self.lens_x,
                "y": self.lens_y,
                "radius": self.lens_radius,
            },
            "queue": queue,
            "queue_index": self.queue_index,
            "queue_stale": self._queue_stale,
            "now": by_q[Quadrant.NOW.value],
            "deep": by_q[Quadrant.DEEP.value],
            "fill": by_q[Quadrant.FILL.value],
            "shelf": by_q[Quadrant.SHELF.value],
            "stars": stars,
            "current": (
                self._track_dict(current, by_id.get(current.id), band_label=band) if current else None
            ),
            "search_query": self.search_query,
            "search": [
                self._track_dict(t, by_id.get(t.id), band_label=band, include_why=False)
                for t in hits
            ],
            "job": self._job_dict(),
        }
