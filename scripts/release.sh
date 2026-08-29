#!/usr/bin/env bash
# One command from a clean tree to a published internal-testing release.
#
#   ./scripts/release.sh                    build, publish bundle + changed listing assets
#   ./scripts/release.sh --shots            also recapture screenshots first (needs an emulator)
#   ./scripts/release.sh --track beta       publish to another track
#   ./scripts/release.sh --dry-run          do everything except upload
#
# Steps, in order:
#   1. refuse to release a dirty tree or a version already published
#   2. regenerate rolling release notes from the per-version entries
#   3. optionally recapture screenshots from seeded data on an emulator
#   4. copy only the assets that actually differ into the play listing
#   5. build the signed bundle, which runs the privacy and backdoor gates
#   6. upload the bundle, and the listing only if something changed
#
# Uploading unchanged images is slow and pointless -- Play re-processes every file it
# receives -- so step 4 compares byte-for-byte and step 6 skips the listing task entirely
# when nothing differs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

PLAY_DIR="app/src/main/play"
SHOT_DIR="build/store/screenshots"
TRACK="internal"
WITH_SHOTS=0
DRY_RUN=0

die() { echo "error: $*" >&2; exit 1; }
step() { echo; echo "=== $* ==="; }

while [ $# -gt 0 ]; do
    case "$1" in
        --shots)   WITH_SHOTS=1; shift ;;
        --track)   TRACK="${2:?--track needs a value}"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        *)         die "unknown option '$1'" ;;
    esac
done

[ -f .env ] || die ".env not found; copy .env.example and fill it in"
set -a; . ./.env; set +a
[ -n "${PLAY_SERVICE_ACCOUNT_JSON:-}" ] || die "PLAY_SERVICE_ACCOUNT_JSON is not set in .env"
[ -f "$PLAY_SERVICE_ACCOUNT_JSON" ] || die "service account file not found: $PLAY_SERVICE_ACCOUNT_JSON"

version_code() { grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts | head -1; }
version_name() { grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1; }

CODE="$(version_code)"
NAME="$(version_name)"
[ -n "$CODE" ] || die "could not read versionCode from app/build.gradle.kts"

step "Releasing $NAME (versionCode $CODE) to '$TRACK'"

# A release is a claim about a specific commit. Building from a dirty tree produces an
# artefact that matches no revision, which cannot be reproduced or bisected later.
if [ -n "$(git status --porcelain)" ]; then
    die "working tree is dirty; commit or stash before releasing"
fi

# Every version's entry must exist before its notes can be assembled.
for lang_dir in "$PLAY_DIR"/release-notes/*/; do
    lang="$(basename "$lang_dir")"
    [ -f "$lang_dir/entries/$CODE.txt" ] || die "missing release note: $lang/entries/$CODE.txt"
done

step "Release notes"
./scripts/release-notes.sh

if [ "$WITH_SHOTS" -eq 1 ]; then
    step "Screenshots"
    # Screenshots come from seeded synthetic data on an emulator; store-assets.sh refuses to
    # seed a physical device.
    device="$(adb devices | awk '/^emulator-[0-9]+\tdevice$/ {print $1}' | head -1)"
    [ -n "$device" ] || die "--shots needs a running emulator"
    for lang_dir in "$PLAY_DIR"/listings/*/; do
        lang="$(basename "$lang_dir")"
        SHOT_DEVICE="$device" SHOT_LANG="$lang" ./scripts/store-assets.sh shots
    done
fi

step "Syncing changed listing assets"
changed=0
for lang_dir in "$PLAY_DIR"/listings/*/; do
    lang="$(basename "$lang_dir")"
    target="$lang_dir/graphics/phone-screenshots"
    [ -d "$SHOT_DIR/$lang" ] || continue
    mkdir -p "$target"
    index=1
    for shot in "$SHOT_DIR/$lang"/*.png; do
        [ -f "$shot" ] || continue
        dest="$target/$(printf '%02d' "$index").png"
        if ! cmp -s "$shot" "$dest"; then
            cp "$shot" "$dest"
            echo "  changed: $lang/$(basename "$dest")"
            changed=$((changed + 1))
        fi
        index=$((index + 1))
    done
done

# Text and graphics tracked in git: anything uncommitted here is a real change to publish.
if [ -n "$(git status --porcelain -- "$PLAY_DIR")" ]; then
    changed=$((changed + 1))
fi
[ "$changed" -eq 0 ] && echo "  listing unchanged; skipping its upload"

step "Building signed bundle"
# Runs verifyNoNetworkPermission and verifyNoDebugBackdoor as part of the release build.
./gradlew bundleRelease -q

BUNDLE="app/build/outputs/bundle/release/app-release.aab"
[ -f "$BUNDLE" ] || die "bundle was not produced"

# Independent of the build's own gates: read the permissions out of the artefact that will
# actually be uploaded, so a regression in the gates cannot ship silently.
step "Auditing the artefact"
offending="$(unzip -p "$BUNDLE" base/manifest/AndroidManifest.xml 2>/dev/null \
    | strings \
    | grep -icE 'permission\.INTERNET|ACCESS_NETWORK_STATE|permission\.health\.WRITE' || true)"
[ "$offending" -eq 0 ] || die "bundle requests network or write permissions ($offending matches)"
echo "  no network permission, no write permission"

if [ "$DRY_RUN" -eq 1 ]; then
    step "Dry run: nothing uploaded"
    echo "  bundle: $BUNDLE"
    exit 0
fi

step "Uploading bundle to '$TRACK'"
./gradlew publishReleaseBundle -Pplay.track="$TRACK" -q

if [ "$changed" -gt 0 ]; then
    step "Uploading listing"
    ./gradlew publishReleaseListing -q
fi

step "Done"
echo "  $NAME (versionCode $CODE) is on the '$TRACK' track"
echo "  Remember to commit the refreshed listing assets if screenshots changed."
