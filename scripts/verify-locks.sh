#!/usr/bin/env bash
# Fails if any locked file of any room has been modified.
set -euo pipefail
cd "$(dirname "$0")/.."
if command -v sha256sum >/dev/null 2>&1; then check="sha256sum -c"; else check="shasum -a 256 -c"; fi
for lock in rooms/*/LOCKED.sha256; do
  echo "== $lock"
  $check "$lock"
done
echo "All locks intact."
