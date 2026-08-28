#!/usr/bin/env bash
# Build, install and launch Health Connect View.
#
#   ./scripts/deploy.sh debug            build + install + launch a debug build
#   ./scripts/deploy.sh release          build a signed release APK
#   ./scripts/deploy.sh bundle           build a signed .aab for the Play Store
#   ./scripts/deploy.sh play internal    upload the .aab to the internal testing track
#
# Some devices (notably Xiaomi/HyperOS) refuse `adb install` without a vendor account.
# When installation is refused the APK is pushed to the device's Download folder so it
# can be installed by tapping it in a file manager.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

APPLICATION_ID="de.steppicrew.healthconnectview"
MAIN_ACTIVITY="$APPLICATION_ID/de.steppicrew.healthconnectview.MainActivity"
COMMAND="${1:-debug}"

die() { echo "error: $*" >&2; exit 1; }

pick_device() {
    # Prefer a physical device over a running emulator.
    local devices
    devices="$(adb devices | awk '/\tdevice$/ {print $1}')"
    [ -n "$devices" ] || return 1
    local physical
    physical="$(echo "$devices" | grep -v '^emulator-' | head -1)"
    if [ -n "$physical" ]; then echo "$physical"; else echo "$devices" | head -1; fi
}

install_or_push() {
    local apk="$1" device="$2" package="$3"
    if adb -s "$device" install -r "$apk" 2>&1 | tee /dev/stderr | grep -q "^Success"; then
        adb -s "$device" shell am start -n "$package/de.steppicrew.healthconnectview.MainActivity" >/dev/null
        echo "Installed and launched on $device."
    else
        local target="/sdcard/Download/health-connect-view.apk"
        adb -s "$device" push "$apk" "$target" >/dev/null
        cat <<EOF

Direct installation was refused by the device, so the APK was copied to:
  Download/health-connect-view.apk

Open a file manager on the device and tap it to install.
EOF
    fi
}

case "$COMMAND" in
    debug)
        ./gradlew :app:assembleDebug
        APK="app/build/outputs/apk/debug/app-debug.apk"
        [ -f "$APK" ] || die "expected APK at $APK"

        if DEVICE="$(pick_device)"; then
            install_or_push "$APK" "$DEVICE" "$APPLICATION_ID.debug"
        else
            echo "No device connected. APK is at: $APK"
        fi
        ;;

    release)
        ./gradlew :app:assembleRelease
        echo "APK: app/build/outputs/apk/release/app-release.apk"
        ;;

    bundle)
        ./gradlew :app:bundleRelease
        echo "Bundle: app/build/outputs/bundle/release/app-release.aab"
        ;;

    play)
        TRACK="${2:-internal}"
        [ -f .env ] || die ".env not found; copy .env.example and fill it in"
        ./gradlew "publishReleaseBundle" -Pplay.track="$TRACK"
        ;;

    *)
        die "unknown command '$COMMAND' (expected: debug, release, bundle, play)"
        ;;
esac
