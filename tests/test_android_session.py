from __future__ import annotations

from pathlib import Path

from meridian.android_session import AndroidSession, _path_playable, set_path_probe
from meridian.library import Library


def test_session_indexes_and_queues(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "glow.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    snap = session.initialize(str(tmp_path / "data"), str(music))
    assert music.as_posix() in snap["folders"] or str(music) in snap["folders"]
    snap = session.scan()
    assert snap["track_count"] >= 1
    snap = session.set_lens(0.7, 0.6, 0.2)
    assert snap["lens"]["x"] == 0.7
    assert snap["ok"]
    track_id = snap["stars"][0]["id"]
    snap = session.play(track_id)
    assert snap["current"]["id"] == track_id
    assert Path(snap["current"]["path"]).exists()
    snap = session.set_mode("focus")
    assert snap["mode"] == "focus"
    session.close()


def test_skip_nudges_unpinned_mood(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "glow.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    track = session.library.all_tracks()[0]
    tid = track.id
    with session.library.lock:
        session.library.conn.execute(
            "UPDATE tracks SET valence = 0.20, energy = 0.20, pinned = 0, mood_confidence = 0.6 WHERE id = ?",
            (tid,),
        )
        session.library.conn.commit()
    session.set_lens(0.90, 0.90, 0.22)
    session.play(tid)
    before = session.library.get(tid)
    session.skip()
    after = session.library.get(tid)
    assert after is not None and before is not None
    assert after.valence < before.valence or after.energy < before.energy
    session.close()


def test_crossfade_credit_defers_play_and_finish_nudge(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "a.mp3").write_bytes(b"ID3")
    (music / "b.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    a, b = session.library.all_tracks()[0].id, session.library.all_tracks()[1].id
    session.play(a)
    session.play(b, position_ms=2_000)
    assert session._xfade_out == a
    assert session.library.get(b).play_count == 0
    assert session.library.get(a).skip_count >= 1
    session.fade_settled()
    assert session.library.get(b).play_count == 1
    assert session._xfade_out is None
    session.close()


def test_previous_during_fade_restores_outgoing(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "a.mp3").write_bytes(b"ID3")
    (music / "b.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    a, b = session.library.all_tracks()[0].id, session.library.all_tracks()[1].id
    session.play(a)
    plays = session.library.get(a).play_count
    session.play(b, position_ms=1_000)
    snap = session.previous()
    assert snap["current"]["id"] == a
    assert session.library.get(a).play_count == plays
    session.close()


def test_why_and_previous(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "a.mp3").write_bytes(b"ID3")
    (music / "b.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    snap = session.scan()
    assert snap["job"]["running"] is False
    first = snap["stars"][0]["id"]
    snap = session.play(first)
    assert "why" in snap["current"]
    assert snap["current"]["why"]
    assert any(snap[band] for band in ("now", "deep", "fill", "shelf"))
    if len(snap["queue"]) >= 2:
        session.skip()
        idx = session.queue_index
        snap = session.previous()
        assert snap["queue_index"] <= idx
    prog = session.progress()
    assert "job" in prog
    session.close()


def test_bridge_json_roundtrip(tmp_path: Path) -> None:
    from meridian.android_bridge import call

    data = tmp_path / "data"
    music = tmp_path / "Music"
    music.mkdir()
    (music / "a.flac").write_bytes(b"fLaC")
    out = call(
        "initialize",
        __import__("json").dumps({"data_dir": str(data), "default_music": str(music)}),
    )
    payload = __import__("json").loads(out)
    assert payload["ok"]
    scanned = __import__("json").loads(call("scan", "{}"))
    assert scanned["ok"]
    assert scanned["track_count"] >= 1
    bad = __import__("json").loads(call("nope", "{}"))
    assert not bad["ok"]


def test_reinitialize_closes_previous_library(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "glow.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    first = session.library
    session.initialize(str(tmp_path / "data"), str(music))
    assert session.library is not first
    snap = session.add_folders([str(music)])
    assert snap["ok"]
    session.close()


def test_add_folders_requires_initialize(tmp_path: Path) -> None:
    session = AndroidSession()
    try:
        session.add_folders([str(tmp_path)])
        raise AssertionError("expected uninitialized session to fail")
    except RuntimeError as exc:
        assert "not initialized" in str(exc)


def test_playback_failed_skips_only_broken_track(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "a.mp3").write_bytes(b"ID3")
    (music / "b.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    tracks = session.library.all_tracks()
    a, b = tracks[0].id, tracks[1].id
    session.play(a)
    session.queue_ids = [a, b]
    session.queue_index = 0
    snap = session.playback_failed(a)
    assert session.library.is_playback_denied(a)
    assert snap["current"]["id"] == b

    session2 = AndroidSession()
    session2.initialize(str(tmp_path / "data2"), str(music))
    session2.scan()
    t2 = session2.library.all_tracks()
    x, y = t2[0].id, t2[1].id
    session2.play(y)
    session2.queue_ids = [y, x]
    session2.queue_index = 0
    session2.playback_failed(x)
    assert session2.library.is_playback_denied(x)
    assert session2.queue_ids[session2.queue_index] == y
    assert x not in session2.queue_ids
    empty_id = session2.playback_failed(0)
    assert session2.library.is_playback_denied(y)
    session.close()
    session2.close()
    assert empty_id["ok"]


def test_rebuild_queue_drops_denied_current(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "a.mp3").write_bytes(b"ID3")
    (music / "b.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    a, b = session.library.all_tracks()[0].id, session.library.all_tracks()[1].id
    session.queue_ids = [a, b]
    session.queue_index = 0
    session.library.mark_playback_failed(a)
    session._rebuild_queue(keep_current=True)
    assert a not in session.queue_ids
    session.close()


def test_scan_core_used_by_library_wipe_guards(tmp_path: Path) -> None:
    from meridian.scan_core import scan_library

    db = tmp_path / "lib.sqlite"
    lib = Library(db)
    try:
        lib.upsert_track(
            {
                "path": "/m/keep.mp3",
                "title": "k",
                "artist": "A",
                "albumartist": "A",
                "album": "LP",
                "genre": "Pop",
                "duration_ms": 1,
                "year": None,
                "bpm": 100,
                "valence": 0.5,
                "energy": 0.5,
                "mood_confidence": 0.8,
                "confidence_note": "seed",
                "low_trust": 0,
                "added_at": 0,
                "mtime": 0,
                "analyzed": 1,
            }
        )
        lib.add_folder(str(tmp_path / "missing-root"))
        added = scan_library(lib, force=False)
        assert added == 0
        assert len(lib.all_tracks()) == 1
    finally:
        lib.close()


class _Probe:
    def __init__(self, ok: set[str]) -> None:
        self.ok = ok

    def reachable(self, path: str) -> bool:
        return path in self.ok


def test_dir_has_audio_and_skips_empty_default_music(tmp_path: Path) -> None:
    from meridian.android_session import _dir_has_audio

    empty = tmp_path / "Music"
    empty.mkdir()
    assert not _dir_has_audio(str(empty))
    (empty / "glow.mp3").write_bytes(b"ID3")
    assert _dir_has_audio(str(empty))

    blank = tmp_path / "empty-music"
    blank.mkdir()
    session = AndroidSession()
    snap = session.initialize(str(tmp_path / "data-empty"), str(blank))
    assert snap["folders"] == []
    session.close()


def test_ensure_reuses_live_library(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    (music / "glow.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    data = tmp_path / "data"
    session.initialize(str(data), str(music))
    session.scan()
    lib = session.library
    count = session.snapshot()["track_count"]
    snap = session.ensure(str(data))
    assert session.library is lib
    assert snap["track_count"] == count
    # Different root still opens a fresh session without a Music default.
    other = tmp_path / "other"
    snap2 = session.ensure(str(other))
    assert session.library is not lib
    assert snap2["folders"] == []
    session.close()


def test_bridge_ensure_method(tmp_path: Path) -> None:
    from meridian.android_bridge import call

    data = tmp_path / "data"
    first = __import__("json").loads(call("ensure", __import__("json").dumps({"data_dir": str(data)})))
    assert first["ok"]
    second = __import__("json").loads(call("ensure", __import__("json").dumps({"data_dir": str(data)})))
    assert second["ok"]
    assert second.get("track_count", 0) == first.get("track_count", 0)


def test_path_probe_rejects_dead_content_uris() -> None:
    set_path_probe(_Probe({"content://ok/a.mp3"}))
    try:
        assert _path_playable("content://ok/a.mp3")
        assert not _path_playable("content://dead/a.mp3")
        assert not _path_playable("")
    finally:
        set_path_probe(None)
    assert _path_playable("content://unprobed/a.mp3")


def test_add_uri_tracks_indexes_playable_saf(tmp_path: Path) -> None:
    # Index trusts content: URIs (MediaStore/SAF); reachability is checked at play.
    session = AndroidSession()
    try:
        session.initialize(str(tmp_path / "data"), "")
        snap = session.add_uri_tracks(
            [
                {
                    "path": "content://tree/doc/glow.mp3",
                    "title": "Glow.mp3",
                    "duration_ms": 185_000,
                },
                {"path": "content://tree/doc/missing.mp3", "title": "Missing.mp3"},
                {"path": "/not/a/uri.mp3", "title": "Nope"},
            ]
        )
        assert snap["ok"]
        assert snap["track_count"] == 2
        paths = {t.path for t in session.library.all_tracks()}
        assert paths == {
            "content://tree/doc/glow.mp3",
            "content://tree/doc/missing.mp3",
        }
        glow = next(t for t in session.library.all_tracks() if "glow" in t.path)
        assert glow.duration_ms == 185_000
        assert glow.title == "Glow"
    finally:
        session.close()


def test_snapshot_lists_without_opening_content_uris(tmp_path: Path) -> None:
    """Catalog snapshots must not call PathProbe (AFD open) for every content: row."""

    class CountingProbe:
        def __init__(self) -> None:
            self.hits = 0

        def reachable(self, path: str) -> bool:
            self.hits += 1
            return True

    probe = CountingProbe()
    set_path_probe(probe)
    session = AndroidSession()
    try:
        session.initialize(str(tmp_path / "data"), "")
        session.add_uri_tracks(
            [{"path": f"content://tree/doc/t{i}.mp3", "title": f"T{i}"} for i in range(5)]
        )
        probe.hits = 0
        snap = session.snapshot()
        assert snap["track_count"] == 5
        assert probe.hits == 0
        lite = session.snapshot(lite=True)
        assert lite["now"] or lite["stars"]
        # Lite matrix rows omit why text.
        if lite["now"]:
            assert lite["now"][0].get("why", "") == ""
    finally:
        set_path_probe(None)
        session.close()


def test_renew_queue_from_matrix_keeps_playing_and_lens_holds(tmp_path: Path) -> None:
    music = tmp_path / "Music"
    music.mkdir()
    for name in ("a.mp3", "b.mp3", "c.mp3"):
        (music / name).write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    for track in session.library.all_tracks():
        with session.library.lock:
            session.library.conn.execute(
                "UPDATE tracks SET valence = ?, energy = ?, mood_confidence = 0.8, "
                "analyzed = 1 WHERE id = ?",
                (0.2 + (track.id % 3) * 0.25, 0.3 + (track.id % 2) * 0.3, track.id),
            )
            session.library.conn.commit()
    session.set_lens(0.55, 0.55, 0.25)
    assert session.queue_ids
    playing = session.queue_ids[0]
    session.play(playing)
    prior = list(session.queue_ids)
    snap = session.renew_queue()
    assert snap["ok"]
    assert "renewed" in snap["status"].lower()
    assert session.queue_ids
    assert session._current_track() is not None
    assert session._current_track().id == playing
    # Playing track stays at the front — no stranded prefix.
    assert session.queue_index == 0
    assert session.queue_ids[0] == playing
    assert session._renew_streak == 1
    assert snap["queue_stale"] is False
    # Second renew soft-demotes; streak advances.
    session.renew_queue()
    assert session._renew_streak == 2
    held = list(session.queue_ids)
    # Sub-eps wobble must not zero the streak or mark stale.
    session.set_lens(0.55 + 0.005, 0.55, 0.25)
    assert list(session.queue_ids) == held
    assert session._renew_streak == 2
    assert session._queue_stale is False
    session.set_lens(0.9, 0.2, 0.2)
    # Meaningful lens move holds the queue but marks it stale.
    assert list(session.queue_ids) == held
    assert session._renew_streak == 0
    assert session._queue_stale is True
    assert prior
    synced = session.play_context()
    assert synced["queue_stale"] is False
    assert session.queue_ids
    # Pin holds the live queue (desktop parity).
    before_pin = list(session.queue_ids)
    session.pin(playing, 0.8, 0.8)
    assert list(session.queue_ids) == before_pin
    # Streak survives process-style reload via ui state.
    session._renew_streak = 3
    session._save_ui_state()
    session2 = AndroidSession()
    session2.initialize(str(tmp_path / "data"), str(music))
    assert session2._renew_streak == 3
    from meridian.android_bridge import _METHODS

    assert "renew_queue" in _METHODS
    assert "play_context" in _METHODS
    session.close()
    session2.close()


def test_analyze_pending_accepts_two_workers(tmp_path: Path) -> None:
    from meridian.scan_core import analyze_pending

    music = tmp_path / "Music"
    music.mkdir()
    (music / "a.mp3").write_bytes(b"ID3")
    (music / "b.mp3").write_bytes(b"ID3")
    session = AndroidSession()
    session.initialize(str(tmp_path / "data"), str(music))
    session.scan()
    seen: list[str] = []

    def on_progress(name: str, index: int, total: int) -> None:
        seen.append(f"{index}/{total}:{name}")

    analyze_pending(session.library, progress=on_progress, workers=2)
    assert len(seen) >= 2
    session.close()


def test_scan_progress_reports_file_and_counts(tmp_path: Path) -> None:
    from meridian.scan_core import scan_library

    music = tmp_path / "Music"
    music.mkdir()
    for name in ("a.mp3", "b.flac", "c.ogg"):
        (music / name).write_bytes(b"x")
    lib = Library(tmp_path / "lib.sqlite")
    lib.add_folder(str(music))
    seen: list[tuple[str, int, int]] = []

    def on_progress(name: str, index: int, total: int) -> None:
        seen.append((name, index, total))

    added = scan_library(lib, force=True, progress=on_progress)
    assert added == 3
    numbered = [row for row in seen if row[2] > 0]
    assert len(numbered) == 3
    assert numbered[0] == ("a.mp3", 1, 3) or numbered[0][1:] == (1, 3)
    assert numbered[-1][1:] == (3, 3)
    assert {row[0] for row in numbered} == {"a.mp3", "b.flac", "c.ogg"}
    lib.close()


def test_content_tree_folder_add_and_remove(tmp_path: Path) -> None:
    tree = "content://com.android.externalstorage.documents/tree/primary%3APodcasts"
    track = f"{tree}/document/primary%3APodcasts%2Fglow.mp3"
    set_path_probe(_Probe({track}))
    session = AndroidSession()
    try:
        session.initialize(str(tmp_path / "data"), "")
        snap = session.add_folders([tree])
        assert tree in snap["folders"]
        session.add_uri_tracks([{"path": track, "title": "Glow.mp3"}])
        assert session.library.get(session.library.all_tracks()[0].id) is not None
        snap = session.remove_folder(tree)
        assert tree not in snap["folders"]
        assert snap["track_count"] == 0
    finally:
        set_path_probe(None)
        session.close()
