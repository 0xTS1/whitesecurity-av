#!/usr/bin/env bash
# Convenience wrapper around the admin CLI for creating a premium license key.
#
# Usage:
#   ./scripts/gen-license.sh                       # 1 device, 1 year, plan=premium
#   ./scripts/gen-license.sh --devices 3 --days 30 --note "trial for customer X"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(dirname "$SCRIPT_DIR")"
cd "$SERVER_DIR"

DEFAULT_ARGS=(--plan premium --devices 1 --days 365)
node scripts/admin-cli.js gen-license "${DEFAULT_ARGS[@]}" "$@"
