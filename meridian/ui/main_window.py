from __future__ import annotations

from pathlib import Path
from time import time

from PySide6.QtCore import QSettings, Qt, QThread, QTimer, Slot
from PySide6.QtGui import QAction, QColor, QKeySequence
from PySide6.QtWidgets import (
    QComboBox,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSplitter,
    QStatusBar,
    QVBoxLayout,
    QWidget,
)

from meridian import __version__
from meridian.features import CONFIDENCE_HIGH, CONFIDENCE_LOW
from meridian.context import (
    LENS_RADIUS_DEFAULT,
    MODE_HINTS,
    MODE_LABELS,
    Mode,
    make_context,
    mode_bias,
)
from meridian.host_deps import (
    libx264_missing_message,
    libx264_missing_status,
    should_warn_missing_libx264,
)
from meridian.library import Library
from meridian.player import Player
from meridian.queue_engine import Quadrant, QueuePlan, build_plan
from meridian.scanner import AnalyzeWorker, ScanWorker, start_worker
from meridian.ui.search import TrackSearch
from meridian.ui.fit_list import FitList
from meridian.ui.fonts import condensed
from meridian.ui.palette import PLAYLIST_HEX
from meridian.ui.matrix import EisenhowerMatrix
from meridian.ui.mood_map import MoodMap
from meridian.ui.transport import TransportBar


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle(f"Meridian {__version__}")
        self.resize(1440, 900)
        self.settings = QSettings()
        self.library = Library()
        self.player = Player(self)
        self.mode = Mode(self.settings.value("mode", Mode.WANDER.value))
        self.explicit: list[int] = []
        self.plan: QueuePlan | None = None
        self.session_queue: list[int] = []
        self.ephemeral: set[int] = set()
        self.played_history: list[int] = []
        self.queue_index = 0
        self.skips_window: list[float] = []
        self._scan_thread = None
        self._scan_worker = None
        self._analyze_thread = None
        self._analyze_worker = None
        self._analyze_gen = 0
        # Timed-out workers kept alive (signals disconnected) until QThread ends.
        self._lingering_workers: list[tuple] = []
        self._close_library_when_idle = False
        self._closing = False
        self._pending_play_credit: int | None = None
        # Outgoing track of the active crossfade (for Next/Prev / settle credit).
        self._crossfade_outgoing_id: int | None = None
        # When True, natural fade settle finish-nudges `_crossfade_outgoing_id`.
        # User jumps credit immediately instead (skip vs finish from position).
        self._outgoing_settle_finish: bool = False
        self._expect_natural_advance: bool = False
        self._host_codec_sticky = False
        self._duration = 0
        self._rebuild_lock = False
        # Listen-nudge / lens refresh requested while play_id holds the rebuild lock.
        self._pending_plan_refresh = False
        self._handling_playback_error = False
        # Hard-cut play_id credits immediately; cleared/undone if media errors.
        self._last_hard_play_credit: int | None = None
        self._lens_timer = QTimer(self)
        self._lens_timer.setSingleShot(True)
        self._lens_timer.setInterval(180)
        self._lens_timer.timeout.connect(lambda: self.refresh_plan(rebuild_queue=False))

        self._build()
        self._bind()
        self._restore_lens()
        if not self.library.folders():
            music = Path.home() / "Music"
            if music.is_dir():
                self.library.add_folder(str(music))
        self.refresh_plan()
        QTimer.singleShot(400, self.start_scan)
        QTimer.singleShot(900, self._maybe_warn_libx264)

    def _set_status(self, message: str) -> None:
        """Set the status bar, keeping a sticky libx264 tip when the host lacks it."""
        text = message or ""
        if self._host_codec_sticky and should_warn_missing_libx264():
            tip = libx264_missing_status()
            if tip and tip not in text:
                text = f"{text} · {tip}" if text else tip
        else:
            self._host_codec_sticky = False
        self.status_label.setText(text)

    def _maybe_warn_libx264(self) -> None:
        """AppImage keeps libx264 on the host — tell the user if playback may be broken."""
        if not should_warn_missing_libx264():
            self._host_codec_sticky = False
            return
        self._host_codec_sticky = True
        self._set_status(libx264_missing_status())
        if self.settings.value("host/skip_libx264_warning", False, type=bool):
            return
        box = QMessageBox(self)
        box.setIcon(QMessageBox.Icon.Warning)
        box.setWindowTitle("System library needed for playback")
        box.setTextFormat(Qt.TextFormat.RichText)
        box.setText(libx264_missing_message())
        box.setStandardButtons(QMessageBox.StandardButton.Ok)
        dont = box.addButton("Don’t show again", QMessageBox.ButtonRole.AcceptRole)
        box.exec()
        if box.clickedButton() is dont:
            self.settings.setValue("host/skip_libx264_warning", True)
        # Re-assert after the dialog so scan/analyze progress cannot bury the tip forever.
        self._set_status(libx264_missing_status())
