from __future__ import annotations

import numpy as np

from meridian import features


class _FakeDecoder:
    def __init__(self) -> None:
        self.calls: list[tuple[str, float, float]] = []
        self.aborted = False

    def decodeToF32le(self, path: str, start_s: float, duration_s: float):
        self.calls.append((path, float(start_s), float(duration_s)))
        # ~3s of 11025 Hz noise so _pcm_signal_ok can accept it.
        rng = np.random.default_rng(0)
        pcm = (rng.standard_normal(11025 * 3).astype(np.float32) * 0.08)
        return pcm.tobytes()

    def requestAbort(self) -> None:
        self.aborted = True

    def clearAbort(self) -> None:
        self.aborted = False


def test_external_decoder_is_used_before_ffmpeg(tmp_path) -> None:
    fake = _FakeDecoder()
    features.set_external_pcm_decoder(fake)
    features.clear_decode_abort()
    try:
        wav = tmp_path / "x.wav"
        wav.write_bytes(b"RIFF")
        pcm = features._decode_pcm(str(wav), start_s=12.0, duration_s=14.0)
        assert pcm is not None
        assert pcm.dtype == np.float32
        assert pcm.size >= 2048
        assert fake.calls and fake.calls[0][0] == str(wav)
    finally:
        features.set_external_pcm_decoder(None)


def test_analyze_audio_records_pcm_when_decoder_hooked(tmp_path) -> None:
    fake = _FakeDecoder()
    features.set_external_pcm_decoder(fake)
    features.clear_decode_abort()
    try:
        path = str(tmp_path / "song.mp3")
        result = features.analyze_audio(path, "metal", "t", "a", 140.0, duration_ms=180_000)
        assert fake.calls, "Listen must decode PCM through the Android hook"
        assert result.confidence_note
        assert "PCM" in result.confidence_note or result.confidence >= 0
    finally:
        features.set_external_pcm_decoder(None)
