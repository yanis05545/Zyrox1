#!/bin/sh
# Gabungkan engine/module + APK hasil build menjadi satu zip modul (flash di Magisk/KernelSU/APatch).
# Butuh: zip  (Termux: pkg install zip)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APK=$(ls "$ROOT"/app/build/outputs/apk/release/*.apk 2>/dev/null | head -n1)
[ -f "$APK" ] || { echo "APK belum ada. Jalankan: gradle :app:assembleRelease"; exit 1; }
OUT="$ROOT/dist"; STAGE="$OUT/stage"
rm -rf "$OUT"; mkdir -p "$STAGE"
cp -r "$ROOT/engine/module/." "$STAGE/"
mkdir -p "$STAGE/app"
cp "$APK" "$STAGE/app/AzenithAI-Companion.apk"
VER=$(sed -n 's/^version=//p' "$STAGE/module.prop" | head -n1)
(cd "$STAGE" && zip -qr "$OUT/Azenith-AI-$VER-native.zip" .)
rm -rf "$STAGE"
echo "Selesai: $OUT/Azenith-AI-$VER-native.zip"
