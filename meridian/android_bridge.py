"""JSON façade for Chaquopy (one Python module, string in / string out)."""

from __future__ import annotations

import json
import threading
import traceback

from meridian.android_session import AndroidSession, set_path_probe as _set_path_probe

_SESSION = AndroidSession()
_LOCK = threading.Lock()
_NO_LOCK = {"progress", "request_abort"}

_METHODS = {
    "initialize": lambda p: _SESSION.initialize(p["data_dir"], p.get("default_music") or ""),
    "snapshot": lambda _p: _SESSION.snapshot(),
    "set_lens": lambda p: _SESSION.set_lens(p["x"], p["y"], p.get("radius")),
    "set_mode": lambda p: _SESSION.set_mode(p["mode"]),
    "add_folder": lambda p: _SESSION.add_folder(p["path"]),
    "add_folders": lambda p: _SESSION.add_folders(p.get("paths") or []),
    "add_uri_tracks": lambda p: _SESSION.add_uri_tracks(p.get("tracks") or []),
    "remove_folder": lambda p: _SESSION.remove_folder(p["path"]),
    "scan": lambda p: _SESSION.scan(bool(p.get("force"))),
    "analyze": lambda _p: _SESSION.analyze(),
    "rescan": lambda _p: _SESSION.rescan(),
    "pin": lambda p: _SESSION.pin(int(p["id"]), p["valence"], p["energy"]),
    "love": lambda p: _SESSION.love(int(p["id"])),
    "search": lambda p: _SESSION.search(p.get("query") or ""),
    "play": lambda p: _SESSION.play(
        int(p["id"]),
        int(p.get("position_ms") or 0),
        bool(p.get("natural_advance")),
    ),
    "skip": lambda p: _SESSION.skip(int(p.get("position_ms") or 0)),
    "finished_current": lambda _p: _SESSION.finished_current(),
    "previous": lambda _p: _SESSION.previous(),
    "fade_settled": lambda _p: _SESSION.fade_settled(),
    "playback_failed": lambda p: _SESSION.playback_failed(int(p.get("id") or 0)),
    "request_abort": lambda _p: _SESSION.request_abort(),
    "progress": lambda _p: _SESSION.progress(),
}


def call(method: str, payload_json: str = "{}") -> str:
    try:
        payload = json.loads(payload_json or "{}")
        if not isinstance(payload, dict):
            raise TypeError("payload must be an object")
        fn = _METHODS.get(method)
        if fn is None:
            raise KeyError(f"unknown method {method!r}")
        if method in _NO_LOCK:
            result = fn(payload)
        else:
            with _LOCK:
                result = fn(payload)
        if not isinstance(result, dict):
            result = {"ok": True, "value": result}
        result.setdefault("ok", True)
        result.setdefault("error", "")
        return json.dumps(result)
    except Exception as exc:  # noqa: BLE001
        return json.dumps(
            {
                "ok": False,
                "error": str(exc),
                "trace": traceback.format_exc(),
                "status": str(exc),
            }
        )


def set_path_probe(probe: object | None) -> None:
    _set_path_probe(probe)
