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
#
# The status bar is cut off first ($2, its height in pixels): the clock, the emulator's
# network icons and its notifications say nothing about the app and date every screenshot.
normalise() {
    local file="$1" top="${2:-0}"
    if [ "$top" -gt 0 ]; then
        magick "$file" -chop "0x${top}" +repage "$file"
    fi
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

# A screenshot of a Pro feature says so: a band in the feature graphic's colour, so nobody
# installs for a feature and then finds it behind a purchase. It covers the app's top bar --
# the date and its buttons, which the free shots show anyway -- rather than pushing the screen
# down, which cut the same height off the bottom: the owner's choice, 12.10.2026. [$2] is the
# bar's height in pixels. "Pro" is the feature's name in every language.
label_pro() {
    local file="$1" bar="$2"
    local width
    width=$(magick identify -format "%w" "$file")
    magick "$file" -fill "#00696D" -draw "rectangle 0,0 $((width - 1)),$((bar - 1))" \
        -gravity north -font DejaVu-Sans-Bold -pointsize 56 -fill "#E6FFFD" \
        -annotate "+0+$(( (bar - 56) / 2 - 4 ))" "Pro" "$file"
}

# Screens worth showing, as nav routes. The debug build reads `route` from its launch
# intent, so the whole set is captured without a single tap -- which matters because the
# test phone refuses adb input injection, and because a screenshot set that needs manual
# navigation drifts out of date the moment the UI changes.
#
# {name}:{route}:{layout}; an empty route means the dashboard. A layout, where given, is set
# first by the debug seeder (`default`, `showcase` or `night`, see SeedActivity) and stays
# for the shots after it. The two Pro shots are the owner's own dashboard, side by side (their
# choice, 12.10.2026): its top -- the week's changes, rings past their goals, the day's steps
# and workouts -- and further down -- the night, a year of sleep, heart rate and HRV -- with
# the last tile of each cut off by the screen's edge, as on a phone. Eight at most, Play's
# limit, and numbered in order: release.sh uploads them sorted by name.
#
# STRENGTH stands for the newest seeded strength session, whose sets and rests show under its
# heart rate (1.9.0, in place of the workouts list it is reached from): a session is opened
# by the id Health Connect gave it, which changes with every seeding, so it is looked up on
# the device just before the shot.
SHOT_ROUTES=(
    "1-dashboard::default"
    "2-dashboard-pro::showcase"
    "3-night-pro::night"
    "4-steps:tile/StepsRecord?date=DATE:"
    "5-strength:session/EXERCISE/STRENGTH:"
    "6-sleep:tile/SleepSessionRecord?date=DATE:"
    "7-heart-rate:tile/HeartRateRecord?date=DATE:"
    "8-catalog:catalog:"
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
# Interrupted, put the label back and stop: a TERM trap that only restored it let the run go
# on capturing with the label in place.
trap restore_label EXIT
trap 'restore_label; exit 130' INT TERM

# The record id of the newest seeded workout of exercise type [$2] with segments (70 is
# strength training), from the debug build's LatestSessionActivity.
latest_session() {
    local device="$1" type="$2" id
    adb -s "$device" logcat -c
    adb -s "$device" shell am start -W -n "$PACKAGE/de.steppicrew.healthconnectview.debug.LatestSessionActivity" \
        --ei type "$type" >/dev/null
    for _ in $(seq 1 20); do
        id="$(adb -s "$device" logcat -d -s LatestSession:I | sed -n 's/.*id=\([^ ]*\).*/\1/p' | tail -1 | tr -d '\r')"
        [ -n "$id" ] && break
        sleep 0.5
    done
    [ -n "$id" ] || die "no seeded workout of type $type with segments; seed the emulator first"
    echo "$id"
}

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

    # The status bar's height as the device reports it, so the crop follows the device.
    local status_bar
    status_bar="$(adb -s "$device" shell dumpsys window \
        | sed -n 's/.*type=statusBars frame=\[0,0\]\[[0-9]*,\([0-9]*\)\].*/\1/p' | head -1)"
    status_bar="${status_bar:-0}"
    # The app's top bar, 64 dp, which a Pro shot's band covers.
    local density app_bar
    density="$(adb -s "$device" shell wm density | awk '{print $NF}' | tail -1 | tr -d '\r')"
    app_bar=$(( 64 * ${density:-420} / 160 ))

    local out="$OUT_DIR/screenshots/$lang"
    mkdir -p "$out"
    # A renamed or dropped shot must not linger and be uploaded with the new set -- unless
    # only some are retaken (SHOT_ONLY), when the others are kept as they are.
    [ -n "${SHOT_ONLY:-}" ] || rm -f "$out"/*.png

    local entry name route layout rest
    for entry in "${SHOT_ROUTES[@]}"; do
        name="${entry%%:*}"
        rest="${entry#*:}"
        route="${rest%%:*}"
        layout="${rest#*:}"
        route="${route//DATE/$day}"
        if [[ "$route" == *STRENGTH* ]]; then
            local strength
            strength="$(latest_session "$device" 70)" || exit 1
            route="${route//STRENGTH/$strength}"
        fi
        # SHOT_ONLY="5-sleep 3-steps" retakes those alone; the layouts still apply in order.
        if [ -n "${SHOT_ONLY:-}" ] && [[ " $SHOT_ONLY " != *" $name "* ]]; then
            [ -n "$layout" ] && adb -s "$device" shell am start -W \
                -n "$PACKAGE/de.steppicrew.healthconnectview.debug.SeedActivity" -e dashboard "$layout" >/dev/null
            continue
        fi

        if [ -n "$layout" ]; then
            adb -s "$device" shell am start -W -n "$PACKAGE/de.steppicrew.healthconnectview.debug.SeedActivity" \
                -e dashboard "$layout" >/dev/null
            sleep 2
        fi

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
            # The splash screen holds still too, so two matching frames of it also passed as
            # settled -- right after the script's own reinstall, when cold starts are slowest,
            # four store screenshots showed nothing but the launcher icon. Wait it out first.
            if adb -s "$device" shell dumpsys window windows | grep -q "Splash Screen $PACKAGE"; then
                previous=""
                continue
            fi
            adb -s "$device" exec-out screencap -p > "$shot"
            current="$(md5sum < "$shot")"
            if [ -n "$previous" ] && [ "$current" = "$previous" ]; then settled=1; break; fi
            previous="$current"
        done
        [ "$settled" -eq 1 ] || echo "warning: $name did not settle; check it" >&2

        normalise "$shot" "$status_bar"
        case "$name" in *-pro) label_pro "$shot" "$app_bar" ;; esac
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
