#!/usr/bin/env bash
# Builds Play release notes for each translated language, newest entry first.
#
#   ./scripts/release-notes.sh            write notes for the current versionCode
#   ./scripts/release-notes.sh --check    verify every language fits, change nothing
#
# Play caps a changelog at 500 characters. Notes roll: the newest version's entry goes on
# top and older entries are dropped from the bottom until the whole thing fits, so the file
# always shows as much recent history as the limit allows rather than being truncated
# mid-sentence by the Console.
#
# Per-version entries live in <lang>/entries/<versionCode>.txt. This script assembles them
# into the <lang>/default.txt that the Play publisher plugin actually uploads, so editing
# history means editing one small file rather than a running text.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

METADATA="app/src/main/play/release-notes"
# Play's hard limit. Exceeding it is rejected at upload, not silently trimmed.
LIMIT=500
MODE="${1:-write}"

die() { echo "error: $*" >&2; exit 1; }

version_code() {
    grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts | head -1
}

# Languages are whatever has a release-notes directory; a new locale needs no change here.
languages() {
    find "$METADATA" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort
}

# Assemble newest-first, dropping the oldest entries until the result fits.
build_notes() {
    local lang="$1" dir="$METADATA/$lang/entries"
    [ -d "$dir" ] || return 0

    # Numeric sort, highest version first: newest release leads.
    local versions
    versions="$(find "$dir" -name '*.txt' -printf '%f\n' | sed 's/\.txt$//' | sort -rn)"

    local out="" kept=0
    for v in $versions; do
        local entry candidate
        entry="$(cat "$dir/$v.txt")"
        if [ -z "$out" ]; then candidate="$entry"; else candidate="$out"$'\n\n'"$entry"; fi
        # One entry alone over the limit is a writing problem, not a rolling problem, so it
        # is reported rather than silently cut in half.
        if [ "$kept" -eq 0 ] && [ "${#candidate}" -gt "$LIMIT" ]; then
            die "$lang: entry for version $v is ${#candidate} chars, over the $LIMIT limit on its own"
        fi
        [ "${#candidate}" -gt "$LIMIT" ] && break
        out="$candidate"
        kept=$((kept + 1))
    done

    printf '%s' "$out"
}

main() {
    local code failed=0
    code="$(version_code)"
    [ -n "$code" ] || die "could not read versionCode from app/build.gradle.kts"

    for lang in $(languages); do
        local dir="$METADATA/$lang/entries"
        [ -d "$dir" ] || { echo "$lang: no entries/, skipped"; continue; }
        [ -f "$dir/$code.txt" ] || { echo "$lang: MISSING entry for version $code"; failed=1; continue; }

        # default.txt is what the plugin uploads for every release; the entries beside it
        # are the history it is assembled from.
        local notes target
        notes="$(build_notes "$lang")"
        target="$METADATA/$lang/default.txt"

        if [ "$MODE" = "--check" ]; then
            printf '%-6s %3s chars\n' "$lang" "${#notes}"
        else
            mkdir -p "$(dirname "$target")"
            printf '%s' "$notes" > "$target"
            printf '%-6s %3s chars -> %s\n' "$lang" "${#notes}" "$target"
        fi
    done
    return $failed
}

main
