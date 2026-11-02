#!/usr/bin/env python3
"""Write valid night-navy launcher PNGs (AAPT rejects the UTF-8-damaged imports)."""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
RGB = (11, 16, 32)


def chunk(tag: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(tag + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)


def png(w: int, h: int) -> bytes:
    raw = b"".join(b"\x00" + (bytes(RGB) * w) for _ in range(h))
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def main() -> None:
    for name, size in (
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
    ):
        folder = ROOT / name
        folder.mkdir(parents=True, exist_ok=True)
        blob = png(size, size)
        (folder / "ic_launcher.png").write_bytes(blob)
        (folder / "ic_launcher_round.png").write_bytes(blob)


if __name__ == "__main__":
    main()
