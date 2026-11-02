#!/bin/bash
# Copy Ubuntu Sans / Condensed into res/font for R.font.ubuntu_*.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RES="$ROOT/android/app/src/main/res/font"
SRC="$ROOT/resources/fonts"
mkdir -p "$RES"
BASE='https://raw.githubusercontent.com/dark1ltg/Meridian/b48f89d2469f691dcbad804a6e48f0bf08ce0bfa/resources/fonts'
if [ ! -f "$SRC/UbuntuCondensed-Regular.ttf" ]; then
  mkdir -p "$SRC"
  curl -fsSL -o "$SRC/UbuntuCondensed-Regular.ttf" "$BASE/UbuntuCondensed-Regular.ttf"
  curl -fsSL -o "$SRC/UbuntuSans[wdth,wght].ttf" "$BASE/UbuntuSans%5Bwdth%2Cwght%5D.ttf"
fi
cp -f "$SRC/UbuntuCondensed-Regular.ttf" "$RES/ubuntu_condensed.ttf"
cp -f "$SRC/UbuntuSans[wdth,wght].ttf" "$RES/ubuntu_sans.ttf"
echo "fonts ready in $RES"
