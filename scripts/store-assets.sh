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
# A screen is settled once two consecutive frames match; generous enough for a cold start.
SETTLE_TRIES=12
SETTLE_INTERVAL=2
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

# Screens worth showing, as nav routes. The debug build reads `route` from its launch
# intent, so the whole set is captured without a single tap -- which matters because the
# test phone refuses adb input injection, and because a screenshot set that needs manual
# navigation drifts out of date the moment the UI changes.
#
# {name}:{route}; an empty route means the dashboard.
SHOT_ROUTES=(
    "1-dashboard:"
    "2-steps:tile/StepsRecord?date=DATE"
    "3-activities:tile/ExerciseSessionRecord?date=DATE"
    "4-sleep:tile/SleepSessionRecord?date=DATE"
    "5-heart-rate:tile/HeartRateRecord?date=DATE"
    "6-catalog:catalog"
)

# The debug build labels itself "✻ Health Connect View" so it can be told apart from the Play
# build in the launcher. That marker is right on a device and wrong in a store listing, where
# it appears in "Written by ..." lines. Screenshots are therefore taken with the override
# temporarily removed, and it is restored however this script exits.
DEBUG_LABEL="app/src/debug/res/values/strings.xml"
STASHED_LABEL=""

restore_label() {
    if [ -n "$STASHED_LABEL" ] && [ -f "$STASHED_LABEL" ]; then
        mv "$STASHED_LABEL" "$DEBUG_LABEL"
        STASHED_LABEL=""
    fi
}
trap restore_label EXIT INT TERM

take_shots() {
    need magick
    local device lang
    device="${SHOT_DEVICE:-$(adb devices | awk '/\tdevice$/ {print $1}' | head -1)}"
    [ -n "$device" ] || die "no device connected"
    lang="${SHOT_LANG:-en-US}"

    # Screenshots come from SEEDED SYNTHETIC DATA. Seeding writes to the real Health Connect
    # store, so this refuses to run against a physical device: nobody's actual records should
    # be polluted with fixtures, and no real reading should end up in a store listing.
    case "$device" in
        emulator-*) ;;
        *) die "refusing to seed a physical device ($device); run screenshots on an emulator" ;;
    esac

    # The seeder starts from yesterday, because Health Connect rejects future-dated records
    # and a day's fixture spans the full 24 hours. Today is therefore empty by design.
    local day
    day="$(date -d yesterday +%Y-%m-%d)"

    # Rebuild without the debug launcher label, so no frame shows the "✻" marker.
    if [ -f "$DEBUG_LABEL" ]; then
        STASHED_LABEL="$(mktemp)"
        mv "$DEBUG_LABEL" "$STASHED_LABEL"
        ./gradlew assembleDebug -q >/dev/null
        adb -s "$device" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
    fi

    adb -s "$device" shell cmd locale set-app-locales "$PACKAGE" --locales "$lang" >/dev/null 2>&1 || true

    local out="$OUT_DIR/screenshots/$lang"
    mkdir -p "$out"

    local entry name route
    for entry in "${SHOT_ROUTES[@]}"; do
        name="${entry%%:*}"
        route="${entry#*:}"
        route="${route//DATE/$day}"

        adb -s "$device" shell am force-stop "$PACKAGE"
        sleep 1
        if [ -n "$route" ]; then
            adb -s "$device" shell "am start -n $ACTIVITY --es route '$route'" >/dev/null
        else
            adb -s "$device" shell "am start -n $ACTIVITY" >/dev/null
        fi
        # Wait for the screen to settle rather than guessing a delay. A cold start with a
        # dense session took longer than a fixed sleep allowed, and the set silently captured
        # a loading spinner -- which is exactly the sort of thing that reaches a store listing
        # unnoticed. Two identical frames in a row means the screen has stopped changing.
        local shot="$out/$name.png"
        local previous="" current="" settled=0
        for _ in $(seq 1 "$SETTLE_TRIES"); do
            sleep "$SETTLE_INTERVAL"
            adb -s "$device" exec-out screencap -p > "$shot"
            current="$(md5sum < "$shot")"
            if [ -n "$previous" ] && [ "$current" = "$previous" ]; then settled=1; break; fi
            previous="$current"
        done
        [ "$settled" -eq 1 ] || echo "warning: $name did not settle; check it" >&2

        normalise "$shot"
        echo "$shot"
    done
}

case "$COMMAND" in
    icon)    make_icon ;;
    feature) make_feature ;;
    shots)   take_shots ;;
    all)     make_icon; make_feature; take_shots ;;
    *)       die "unknown command '$COMMAND' (expected: icon, feature, shots, all)" ;;
esac
