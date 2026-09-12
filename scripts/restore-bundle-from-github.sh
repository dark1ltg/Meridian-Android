#!/usr/bin/env bash
# Rebuild meridian_android.bundle from ASCII parts on GitHub, then clone it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PARTS="$ROOT/dist/github-bundle-parts"
OUT="${1:-$ROOT/meridian_android.bundle}"
DEST="${2:-$ROOT/../meridian-android-full}"

if [[ ! -d "$PARTS" ]]; then
  echo "Missing $PARTS — clone https://github.com/dark1ltg/Meridian-Android first." >&2
  exit 1
fi

python3 - "$PARTS" "$OUT" <<'PY'
import base64, glob, pathlib, sys
parts_dir, out = sys.argv[1], sys.argv[2]
chunks = []
for p in sorted(glob.glob(str(pathlib.Path(parts_dir) / "part-*.b64"))):
    chunks.append(base64.b64decode(pathlib.Path(p).read_text()))
pathlib.Path(out).write_bytes(b"".join(chunks))
print(f"wrote {out} ({sum(len(c) for c in chunks)} bytes)")
PY

git clone "$OUT" "$DEST"
echo "Full repo is in $DEST"
echo "To replace GitHub: cd $DEST && git remote add origin https://github.com/dark1ltg/Meridian-Android.git && git push -u origin main --force"
