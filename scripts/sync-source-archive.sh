#!/usr/bin/env bash
set -euo pipefail

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)
cd "$root"

if (( $# > 1 )); then
  echo "Usage: $0 [archive-path]" >&2
  exit 2
fi

archive=${1:-keyapp-source.zip}
tmp=$(mktemp "${archive}.tmp.XXXXXX")
trap 'rm -f "$tmp"' EXIT

# Archive the index, so only tracked source is included.
tree=$(git write-tree)
git archive --format=zip --mtime=1980-01-01T00:00:00Z "$tree" -- . \
  ':(exclude)keyapp-source.zip' \
  ':(glob,exclude)**/.gradle/**' \
  ':(glob,exclude)**/build/**' \
  ':(glob,exclude)**/.cxx/**' \
  ':(glob,exclude)**/.externalNativeBuild/**' \
  ':(glob,exclude)**/.idea/**' \
  ':(glob,exclude)**/local.properties' \
  ':(glob,exclude)**/*.iml' \
  ':(glob,exclude)**/.DS_Store' > "$tmp"

mv "$tmp" "$archive"
trap - EXIT
echo "Wrote $archive"
