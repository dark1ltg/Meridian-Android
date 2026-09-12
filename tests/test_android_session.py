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
    set_path_probe(_Probe({"content://tree/doc/glow.mp3"}))
    session = AndroidSession()
    try:
        session.initialize(str(tmp_path / "data"), "")
        snap = session.add_uri_tracks(
            [
                {"path": "content://tree/doc/glow.mp3", "title": "Glow.mp3"},
                {"path": "content://tree/doc/missing.mp3", "title": "Missing.mp3"},
                {"path": "/not/a/uri.mp3", "title": "Nope"},
            ]
        )
        assert snap["ok"]
        assert snap["track_count"] == 1
        assert snap["current"] is None or snap["queue"]
        paths = [t["path"] for t in snap["stars"]]
        assert paths == ["content://tree/doc/glow.mp3"]
        assert snap["stars"][0]["title"] == "Glow"
    finally:
        set_path_probe(None)
        session.close()
