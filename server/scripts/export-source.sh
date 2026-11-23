#!/usr/bin/env bash
# Builds downloadable source-code archives for /antivirus/admin/source.
# Secrets (release keystore, local.properties, .env, database) are excluded
# on purpose — fetch those over SSH only.
# Usage: ./scripts/export-source.sh [android_project_dir]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(dirname "$SCRIPT_DIR")"
OUT="$SERVER_DIR/private-exports"
ANDROID_DIR="${1:-$HOME/antivirus/android}"
STAMP=$(date +%Y%m%d-%H%M)

mkdir -p "$OUT"
chmod 700 "$OUT"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

if [[ -d "$ANDROID_DIR" ]]; then
  VERSION=$(grep -oP 'versionName = "\K[^"]+' "$ANDROID_DIR/app/build.gradle.kts" || echo dev)
  (cd "$(dirname "$ANDROID_DIR")" && zip -qr "$TMP/android.zip" "$(basename "$ANDROID_DIR")" \
    -x '*/app/build/*' '*/build/*' '*/.gradle/*' '*/.kotlin/*' '*/.idea/*' '*.iml' \
       '*/local.properties' '*.keystore' '*.jks')
  rm -f "$OUT"/whitesecurity-android-src-*.zip
  mv "$TMP/android.zip" "$OUT/whitesecurity-android-src-v${VERSION}-${STAMP}.zip"
fi

(cd "$(dirname "$SERVER_DIR")" && zip -qr "$TMP/server.zip" "$(basename "$SERVER_DIR")" \
  -x '*/node_modules/*' '*/data/*' '*/.env' '*/downloads/*' '*/backup-*' '*/private-exports/*' '*.log' '*.pid')
rm -f "$OUT"/whitesecurity-server-src-*.zip
mv "$TMP/server.zip" "$OUT/whitesecurity-server-src-${STAMP}.zip"

ls -1 "$OUT"
