#!/usr/bin/env bash
# Generates Play Store listing assets.
#
#   ./scripts/store-assets.sh icon      512x512 listing icon from assets/icon.svg
#   ./scripts/store-assets.sh feature   1024x500 feature graphic
#   ./scripts/store-assets.sh shots     screenshots from a connected device/emulator
#   ./scripts/store-assets.sh all       everything
#
# Screenshots are taken from SEEDED SYNTHETIC DATA, never from real health records.
# Run the debug seeder first (debug builds only):
#   adb shell am start -n <pkg>.debug/de.steppicrew.healthconnectview.debug.SeedActivity
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

OUT_DIR="build/store"
ICON_SVG="assets/icon.svg"
PACKAGE="de.steppicrew.healthconnectview.debug"
ACTIVITY="$PACKAGE/de.steppicrew.healthconnectview.MainActivity"
COMMAND="${1:-all}"

die() { echo "error: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null || die "$1 is required but not installed"; }

make_icon() {
    need rsvg-convert
    mkdir -p "$OUT_DIR"
    # Play requires exactly 512x512, 32-bit PNG.
    rsvg-convert -w 512 -h 512 "$ICON_SVG" -o "$OUT_DIR/icon-512.png"
    echo "$OUT_DIR/icon-512.png"
}

make_feature() {
    need magick
    mkdir -p "$OUT_DIR"
    # 1024x500 feature graphic. The mark is rendered without its background plate so it
    # sits on the gradient rather than showing a square edge.
    rsvg-convert -w 260 -h 260 assets/icon-mark.svg -o "$OUT_DIR/.mark.png"
    magick -size 1024x500 "gradient:#00696D-#00363A" \
        \( "$OUT_DIR/.mark.png" \) -geometry +0-40 -gravity center -composite \
        -gravity center -font DejaVu-Sans -pointsize 54 -fill "#E6FFFD" \
        -annotate +0+150 "Health Connect View" \
        -gravity center -font DejaVu-Sans -pointsize 26 -fill "#7FD8D4" \
        -annotate +0+200 "See your health data. It never leaves your phone." \
        "$OUT_DIR/feature-1024x500.png"
    rm -f "$OUT_DIR/.mark.png"
    echo "$OUT_DIR/feature-1024x500.png"
}

# Play rejects screenshots taller than 2:1. Most modern phones are taller than that,
# so pad to a compliant ratio rather than cropping content away.
normalise() {
    local file="$1"
    local w h
    w=$(magick identify -format "%w" "$file")
    h=$(magick identify -format "%h" "$file")
    local max_h=$(( w * 2 ))
    if [ "$h" -gt "$max_h" ]; then
        magick "$file" -gravity center -background "#00363A" -extent "$(( (h + 1) / 2 ))x${h}" "$file"
    fi
    # Play requires no alpha channel.
    magick "$file" -alpha remove -alpha off "$file"
}

take_shots() {
    need magick
    local device
    device="$(adb devices | awk '/\tdevice$/ {print $1}' | head -1)"
    [ -n "$device" ] || die "no device connected"

    mkdir -p "$OUT_DIR/screenshots"
    adb -s "$device" shell am force-stop "$PACKAGE" || true
    adb -s "$device" shell am start -n "$ACTIVITY" >/dev/null
    sleep 4

    local shot="$OUT_DIR/screenshots/1-catalog.png"
    adb -s "$device" exec-out screencap -p > "$shot"
    normalise "$shot"
    echo "$shot"

    echo "Further screens depend on which data types are granted;"
    echo "navigate manually and re-run to capture them."
}

case "$COMMAND" in
    icon)    make_icon ;;
    feature) make_feature ;;
    shots)   take_shots ;;
    all)     make_icon; make_feature; take_shots ;;
    *)       die "unknown command '$COMMAND' (expected: icon, feature, shots, all)" ;;
esac
