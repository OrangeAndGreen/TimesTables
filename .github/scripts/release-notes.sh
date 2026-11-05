#!/usr/bin/env bash
# Prints release notes for the commit being released: the subject of every
# non-merge commit since the previous v* release tag, as a bulleted list, so a
# merge push shows what it brought in rather than just "Merge branch ...".
# With no previous tag (or nothing new since it), falls back to the last commit.
#
# Usage: release-notes.sh [ref]   (default HEAD; needs full history and tags)
set -euo pipefail

ref="${1:-HEAD}"

prev_tag="$(git describe --tags --abbrev=0 --match 'v*' "$ref" 2>/dev/null || true)"

notes=""
if [ -n "$prev_tag" ]; then
  notes="$(git log --no-merges --format='- %s (%h)' "$prev_tag..$ref")"
fi

if [ -z "$notes" ]; then
  notes="$(git log -1 --format='- %s (%h)' "$ref")"
fi

printf '%s\n' "$notes"
