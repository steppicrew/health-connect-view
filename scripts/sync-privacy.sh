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
# Set PRIVACY_SYNC_DIR in .env to where the website is published: a local directory, or a
# remote target in rsync's own form, `user@host:path/`, reached over ssh. It is left blank in
# .env.example because it is machine-specific.
#
# Run automatically by .githooks/pre-push when main is pushed with changed privacy strings, so
# the live page follows what is published rather than what is drafted on a branch.
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

# rsync's rule: a colon before any slash makes it host:path. A local directory must exist; a
# remote one is left to rsync, which reports a missing path itself.
# --recursive: without it rsync skips the source directory outright and copies nothing.
RSYNC_ARGS=(--recursive --checksum --itemize-changes --human-readable)
if [[ "$PRIVACY_SYNC_DIR" =~ ^[^/]*: ]]; then
    # BatchMode: from a hook there is nobody to answer a password prompt, and a hang would
    # stall the push. Key authentication must work unattended.
    RSYNC_ARGS+=(--rsh "ssh -o BatchMode=yes -o ConnectTimeout=10")
else
    [ -d "$PRIVACY_SYNC_DIR" ] || die "PRIVACY_SYNC_DIR does not exist: $PRIVACY_SYNC_DIR"
fi

# --checksum, not timestamps: the generator rewrites every file each run, so mtimes always
# differ and a timestamp comparison would report changes that are not there.
# Deliberately no --delete: the target is a website that may hold unrelated files, and this
# script owns only the pages it generates.
[ "$MODE" = "--check" ] && RSYNC_ARGS+=(--dry-run)

TARGET="${PRIVACY_SYNC_DIR%/}/"
echo "Syncing $BUILD_DIR/ -> $TARGET"
changes="$(rsync "${RSYNC_ARGS[@]}" "$BUILD_DIR"/ "$TARGET")"

if [ -z "$changes" ]; then
    echo "Already up to date."
else
    echo "$changes"
    # An if, not `[ ] && echo`: as the script's last command a false test is its exit status,
    # which reported every successful sync as a failure.
    if [ "$MODE" = "--check" ]; then
        echo "(dry run; nothing was written)"
    fi
fi
