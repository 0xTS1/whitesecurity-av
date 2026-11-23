#!/usr/bin/env bash
# Publishes a signed APK for download at /antivirus/download.
# Usage: ./scripts/publish-apk.sh path/to/app-release.apk 1.1.0 [versionCode]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOWNLOAD_DIR="$(dirname "$SCRIPT_DIR")/downloads"

APK="${1:-}"
VERSION="${2:-}"
VERSION_CODE="${3:-}"
if [[ -z "$APK" || -z "$VERSION" || ! -f "$APK" ]]; then
  echo "Usage: $0 <app-release.apk> <version> [versionCode]" >&2
  exit 1
fi
if [[ ! "$VERSION" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
  echo "Version must look like 1.2.3" >&2
  exit 1
fi

mkdir -p "$DOWNLOAD_DIR"
NAME="WhiteSecurity-v${VERSION}.apk"
cp "$APK" "$DOWNLOAD_DIR/$NAME.tmp"
mv "$DOWNLOAD_DIR/$NAME.tmp" "$DOWNLOAD_DIR/$NAME"

SHA=$(sha256sum "$DOWNLOAD_DIR/$NAME" | cut -d' ' -f1)
SIZE=$(stat -c %s "$DOWNLOAD_DIR/$NAME")
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

cat > "$DOWNLOAD_DIR/latest.json.tmp" <<EOF
{
  "version": "$VERSION",
  "versionCode": ${VERSION_CODE:-null},
  "file": "$NAME",
  "sha256": "$SHA",
  "size": $SIZE,
  "releasedAt": "$NOW",
  "minAndroid": "8.0"
}
EOF
mv "$DOWNLOAD_DIR/latest.json.tmp" "$DOWNLOAD_DIR/latest.json"

echo "Published $NAME ($SIZE bytes)"
echo "SHA-256: $SHA"
