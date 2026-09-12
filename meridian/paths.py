"""App data directory without requiring a Qt application.

Desktop Meridian still prefers Qt AppDataLocation once a QApplication exists.
Android / headless paths use MERIDIAN_DATA_DIR, then XDG, then ~/.local/share.
"""

from __future__ import annotations

import os
from pathlib import Path


def data_dir() -> Path:
    env = (os.environ.get("MERIDIAN_DATA_DIR") or "").strip()
    if env:
        root = Path(env).expanduser()
        root.mkdir(parents=True, exist_ok=True)
        return root
    try:
        from PySide6.QtCore import QStandardPaths
        from PySide6.QtWidgets import QApplication

        if QApplication.instance() is not None:
            loc = QStandardPaths.writableLocation(
                QStandardPaths.StandardLocation.AppDataLocation
            )
            if loc:
                root = Path(loc)
                root.mkdir(parents=True, exist_ok=True)
                return root
    except Exception:
        pass
    xdg = (os.environ.get("XDG_DATA_HOME") or "").strip()
    root = Path(xdg).expanduser() / "Meridian" if xdg else Path.home() / ".local" / "share" / "Meridian"
    root.mkdir(parents=True, exist_ok=True)
    return root
