#!/usr/bin/env bash
# Creates the emulator this project tests against.
#
# Separate from any AVD you already have, because a shared one fills up: when the data
# partition is full an install fails but the previous build keeps running, which looks
# exactly like a change that did not take effect.
#
# The screen is 1080x2160 -- exactly 2:1, the maximum aspect ratio Play accepts for phone
# screenshots -- so store assets need no padding or cropping.
set -euo pipefail

AVD_NAME="${AVD_NAME:-HealthConnectView_API36}"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
IMAGE="system-images/android-36/google_apis_playstore/x86_64"

[ -d "$SDK/$IMAGE" ] || {
    echo "error: system image missing: $SDK/$IMAGE" >&2
    echo "install it with: sdkmanager \"${IMAGE//\//;}\"" >&2
    exit 1
}

if [ -e "$AVD_HOME/$AVD_NAME.avd" ]; then
    echo "AVD '$AVD_NAME' already exists at $AVD_HOME/$AVD_NAME.avd"
    exit 0
fi

mkdir -p "$AVD_HOME/$AVD_NAME.avd"

cat > "$AVD_HOME/$AVD_NAME.ini" <<EOF
avd.ini.encoding=UTF-8
path=$AVD_HOME/$AVD_NAME.avd
path.rel=avd/$AVD_NAME.avd
target=android-36
EOF

# No skin or hw.device entry: a device profile overrides the resolution set here.
cat > "$AVD_HOME/$AVD_NAME.avd/config.ini" <<EOF
AvdId=$AVD_NAME
avd.ini.displayname=Health Connect View API 36
abi.type=x86_64
image.sysdir.1=$IMAGE/
tag.id=google_apis_playstore
tag.display=Google Play
hw.cpu.arch=x86_64
hw.ramSize=4096
hw.lcd.width=1080
hw.lcd.height=2160
hw.lcd.density=420
disk.dataPartition.size=16G
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
EOF

cat <<EOF
Created '$AVD_NAME' (1080x2160, 16 GB data partition).

Start it with:
  \$ANDROID_HOME/emulator/emulator -avd $AVD_NAME
EOF
