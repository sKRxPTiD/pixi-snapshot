#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
PIXI_SNAPSHOT_SIGNING_PROPERTIES="${PIXI_SNAPSHOT_SIGNING_PROPERTIES:-$HOME/.local/share/pixi-snapshot-signing/keystore.properties}"
export ANDROID_HOME PIXI_SNAPSHOT_SIGNING_PROPERTIES
if [[ ! -f "$PIXI_SNAPSHOT_SIGNING_PROPERTIES" ]]; then
  echo "Release signing key settings were not found at the configured private path." >&2
  exit 1
fi
exec "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" --no-daemon :app:assembleRelease
