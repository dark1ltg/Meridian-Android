#!/usr/bin/env python3
"""Stamp current versionCode / date / SHA-256 into release docs.

Used by .github/workflows/release-apk.yml so README, changelog, release notes,
and related current-version pointers match the APK and source just built.
Does not rewrite historical \"### versionCode N\" changelog sections.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

CERT_SHA = "4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9"
HEX64 = re.compile(r"\b[a-f0-9]{64}\b")
ROOT = Path(__file__).resolve().parents[2]

FILES = [
    "README.md",
    "CHANGELOG.md",
    ".github/release-notes-1.3.6.md",
    "docs/android.md",
    "docs/README.md",
    "SOURCE_OFFER.txt",
    "THIRD_PARTY_NOTICES.txt",
    "android/keystore/README.md",
    "patches/README.md",
    "resources/metainfo/io.github.dark1ltg.Meridian.metainfo.xml",
]


def read_gradle_version() -> tuple[int, str]:
    gradle = (ROOT / "android/app/build.gradle.kts").read_text()
    code = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    if not code or not name:
        raise SystemExit("could not read versionCode / versionName from build.gradle.kts")
    return int(code.group(1)), name.group(1)


def replace_non_cert_hex(line: str, sha: str) -> str:
    def repl(m: re.Match[str]) -> str:
        if m.group(0) == CERT_SHA:
            return m.group(0)
        return sha

    return HEX64.sub(repl, line)


def stamp_hashes(text: str, apk_sha: str | None, src_sha: str | None) -> str:
    out: list[str] = []
    for line in text.splitlines(keepends=True):
        lower = line.lower()
        if "cert sha-256" in lower:
            out.append(line)
            continue
        is_apk = (
            "apk sha-256" in lower
            or "apk checksum" in lower
            or ("meridian-1.3.6-arm64.apk" in lower and "sha-256" in lower)
        )
        is_src = "source sha-256" in lower or (
            "src.tar.gz" in lower and "sha-256" in lower
        )
        if apk_sha and is_apk:
            line = replace_non_cert_hex(line, apk_sha)
        elif src_sha and is_src:
            line = replace_non_cert_hex(line, src_sha)
        out.append(line)
    return "".join(out)


def stamp_version(text: str, version_code: int, date: str) -> str:
    vc = str(version_code)
    text = re.sub(r"(release-v1\.3\.6%20%C2%B7%20)vc\d+", rf"\g<1>vc{vc}", text)
    text = re.sub(r"(v1\.3\.6%20·%20)vc\d+", rf"\g<1>vc{vc}", text)
    text = re.sub(
        r"(\[!\[Latest\].*?vc)\d+",
        rf"\g<1>{vc}",
        text,
        count=1,
    )
    text = re.sub(
        r"(Current release:.*?versionCode )\d+",
        rf"\g<1>{vc}",
        text,
        count=1,
    )
    text = re.sub(
        r"(Published on this repo’s Releases page \(\*\*versionCode )\d+",
        rf"\g<1>{vc}",
        text,
        count=1,
    )
    text = re.sub(
        r"(## \[1\.3\.6\].*— Android APK \+ source, )\d{4}-\d{2}-\d{2}",
        rf"\g<1>{date}",
        text,
        count=1,
    )
    text = re.sub(
        r"(refreshed \*\*)\d{4}-\d{2}-\d{2}(\*\*, \*\*versionCode )\d+",
        rf"\g<1>{date}\g<2>{vc}",
        text,
        count=1,
    )
    text = re.sub(
        r"(; \*\*versionCode )\d+( \*\* / versionName)",
        rf"\g<1>{vc}\g<2>",
        text,
        count=1,
    )
    text = re.sub(
        r"(\*\*\[v1\.3\.6\]\([^)]+\)\*\* \(\*\*versionCode )\d+(\*\*)",
        rf"\g<1>{vc}\g<2>",
        text,
    )
    text = re.sub(
        r"(published \*\*)\d{4}-\d{2}-\d{2}(\*\*)",
        rf"\g<1>{date}\g<2>",
        text,
        count=1,
    )
    text = re.sub(
        r"(This tree’s current product version is \*\*1\.3\.6\*\* \(`versionName`; Android \*\*versionCode )\d+(\*\*\))",
        rf"\g<1>{vc}\g<2>",
        text,
        count=1,
    )
    text = re.sub(
        r"(current v1\.3\.6, versionCode )\d+",
        rf"\g<1>{vc}",
        text,
        count=1,
    )
    text = re.sub(
        r"(v1\.3\.6 versionCode )\d+",
        rf"\g<1>{vc}",
        text,
        count=1,
    )
    text = re.sub(
        r"(APK \(\*\*versionCode )\d+(\*\*\))",
        rf"\g<1>{vc}\g<2>",
        text,
        count=1,
    )
    text = re.sub(
        r"(current \*\*versionCode )\d+(\*\*)",
        rf"\g<1>{vc}\g<2>",
        text,
        flags=re.IGNORECASE,
        count=1,
    )
    text = re.sub(
        r'(<release version="1\.3\.6" date=")\d{4}-\d{2}-\d{2}(">)',
        rf"\g<1>{date}\g<2>",
        text,
        count=1,
    )
    text = re.sub(
        r"(Android APK versionCode )\d+",
        rf"\g<1>{vc}",
        text,
        count=1,
    )
    return text


def stamp_path(
    path: Path,
    version_code: int,
    date: str,
    apk_sha: str | None,
    src_sha: str | None,
) -> bool:
    original = path.read_text()
    updated = stamp_version(original, version_code, date)
    updated = stamp_hashes(updated, apk_sha, src_sha)
    if updated != original:
        path.write_text(updated)
        return True
    return False


def assert_current(version_code: int) -> None:
    readme = (ROOT / "README.md").read_text()
    changelog = (ROOT / "CHANGELOG.md").read_text()
    notes = (ROOT / ".github/release-notes-1.3.6.md").read_text()
    android = (ROOT / "docs/android.md").read_text()
    vc = str(version_code)
    checks = [
        (f"vc{vc}" in readme or f"versionCode {vc}" in readme, "README.md current versionCode"),
        (f"**versionCode {vc}**" in changelog.split("### versionCode")[0], "CHANGELOG.md header versionCode"),
        (f"versionCode {vc}" in notes.split("## What’s new in versionCode")[0], "release-notes current versionCode"),
        (f"versionCode {vc}" in android, "docs/android.md current versionCode"),
    ]
    failed = [name for ok, name in checks if not ok]
    if failed:
        raise SystemExit("stamp-release-docs: still stale: " + ", ".join(failed))


def _self_test() -> None:
    sample = (
        "page (refreshed **2026-11-11**, **versionCode 158**).\n"
        "| **APK SHA-256** | `aa" + "bb" * 31 + "` |\n"
        "| **Source SHA-256** | `cc" + "dd" * 31 + "` |\n"
        "cert SHA-256 `4af7989ae094e7a5baa426613cdfa67cff09a6082fa18f4997144647b50c28e9`\n"
    )
    apk = "a" * 64
    src = "b" * 64
    out = stamp_version(sample, 159, "2026-11-12")
    out = stamp_hashes(out, apk, src)
    assert "refreshed **2026-11-12**, **versionCode 159**" in out, out.splitlines()[0]
    assert apk in out and src in out
    assert CERT_SHA in out
    assert "versionCode 158" not in out.split("What’s new", 1)[0] if "What’s new" in out else "versionCode 158" not in out.splitlines()[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk-sha", default=None)
    parser.add_argument("--src-sha", default=None)
    parser.add_argument("--date", default=None, help="YYYY-MM-DD (America/New_York)")
    parser.add_argument("--check", action="store_true", help="verify current pointers only")
    args = parser.parse_args()
    _self_test()
    version_code, version_name = read_gradle_version()
    if version_name != "1.3.6":
        raise SystemExit(f"unexpected versionName {version_name}")
    if args.check:
        assert_current(version_code)
        print(f"ok versionCode {version_code}")
        return 0
    if not args.date:
        raise SystemExit("--date YYYY-MM-DD is required unless --check")
    changed = []
    for rel in FILES:
        path = ROOT / rel
        if stamp_path(path, version_code, args.date, args.apk_sha, args.src_sha):
            changed.append(rel)
    assert_current(version_code)
    print(f"stamped versionCode {version_code} date {args.date}")
    if args.apk_sha:
        print(f"apk {args.apk_sha}")
    if args.src_sha:
        print(f"src {args.src_sha}")
    for rel in changed:
        print(f"updated {rel}")
    if not changed:
        print("no file changes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
