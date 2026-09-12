from __future__ import annotations

from PySide6.QtCore import QObject, Signal, QThread

from meridian.library import Library
from meridian.scan_core import analyze_pending, scan_library


class ScanWorker(QObject):
    progress = Signal(str)
    finished = Signal(int)
    failed = Signal(str)

    def __init__(self, library: Library, *, force: bool = False) -> None:
        super().__init__()
        self.library = library
        self.force = force
        self._abort = False

    def abort(self) -> None:
        self._abort = True

    def run(self) -> None:
        try:
            count = self._scan()
            self.finished.emit(count)
        except Exception as exc:  # noqa: BLE001
            self.failed.emit(str(exc))
            # Always close the finished channel so the UI can quit the QThread.
            # Negative count = failure (UI must not treat this as a successful scan).
            self.finished.emit(-1)

    def _scan(self) -> int:
        return scan_library(
            self.library,
            force=self.force,
            abort=lambda: self._abort,
            progress=lambda name: self.progress.emit(name),
        )


class AnalyzeWorker(QObject):
    progress = Signal(str, int, int)
    finished = Signal()

    def __init__(self, library: Library) -> None:
        super().__init__()
        self.library = library
        self._abort = False

    def abort(self) -> None:
        self._abort = True
        from meridian.features import request_decode_abort

        request_decode_abort()

    def run(self) -> None:
        try:
            analyze_pending(
                self.library,
                abort=lambda: self._abort,
                progress=lambda title, index, total: self.progress.emit(title, index, total),
            )
        finally:
            self.finished.emit()


def start_worker(worker: QObject, fn_name: str = "run") -> QThread:
    thread = QThread()
    worker.moveToThread(thread)
    thread.started.connect(getattr(worker, fn_name))
    # When run() returns via finished, leave the event loop so wait() can complete.
    # Use a lambda so Signal(int) workers (scan) do not pass args into quit().
    # UI handlers must connect with QueuedConnection — lambdas default to Direct
    # and would run on this worker thread (unsafe for widgets / QThread.wait).
    if hasattr(worker, "finished"):
        worker.finished.connect(lambda *_a: thread.quit())
    thread.start()
    return thread
