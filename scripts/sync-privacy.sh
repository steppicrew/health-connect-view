#!/usr/bin/env bash
# Regenerates the public privacy page and syncs it to the website directory.
#
#   ./scripts/sync-privacy.sh           build and sync
#   ./scripts/sync-privacy.sh --check   build and report what would change, sync nothing
#
# The page is generated from the app's own privacy strings, so the website and the in-app
# Privacy screen cannot drift apart -- the listing links to the page, the app shows the
# screen, and a discrepancy between them is a broken promise rather than a formatting slip.
#
# Set PRIVACY_SYNC_DIR in .env to the local directory that is published as the website. It
# is left blank in .env.example because it is machine-specific.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

BUILD_DIR="build/privacy"
MODE="${1:-sync}"

die() { echo "error: $*" >&2; exit 1; }

[ -f .env ] || die ".env not found; copy .env.example and fill it in"
set -a; . ./.env; set +a

./scripts/privacy-page.py "$BUILD_DIR" >/dev/null

if [ -z "${PRIVACY_SYNC_DIR:-}" ]; then
    echo "PRIVACY_SYNC_DIR is not set in .env; generated pages are in $BUILD_DIR"
    echo "Set it to the directory published as your website to enable syncing."
    exit 0
fi

[ -d "$PRIVACY_SYNC_DIR" ] || die "PRIVACY_SYNC_DIR does not exist: $PRIVACY_SYNC_DIR"

# --checksum, not timestamps: the generator rewrites every file each run, so mtimes always
# differ and a timestamp comparison would report changes that are not there.
# Deliberately no --delete: the target is a website that may hold unrelated files, and this
# script owns only the pages it generates.
RSYNC_ARGS=(--checksum --itemize-changes --human-readable)
[ "$MODE" = "--check" ] && RSYNC_ARGS+=(--dry-run)

echo "Syncing $BUILD_DIR/ -> $PRIVACY_SYNC_DIR/"
changes="$(rsync "${RSYNC_ARGS[@]}" "$BUILD_DIR"/ "$PRIVACY_SYNC_DIR"/)"

if [ -z "$changes" ]; then
    echo "Already up to date."
else
    echo "$changes"
    [ "$MODE" = "--check" ] && echo "(dry run; nothing was written)"
fi
