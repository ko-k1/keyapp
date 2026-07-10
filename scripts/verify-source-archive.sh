#!/usr/bin/env bash
set -euo pipefail

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)
archive="$root/keyapp-source.zip"

if [[ ! -f "$archive" ]]; then
  echo "Missing keyapp-source.zip; run scripts/sync-source-archive.sh" >&2
  exit 1
fi

tmp=$(mktemp "$root/.keyapp-source-verify.XXXXXX")
trap 'rm -f "$tmp"' EXIT
"$root/scripts/sync-source-archive.sh" "$tmp" >/dev/null

if ! cmp -s "$archive" "$tmp"; then
  echo "keyapp-source.zip is out of date; run scripts/sync-source-archive.sh" >&2
  exit 1
fi

echo "keyapp-source.zip matches the tracked source."
