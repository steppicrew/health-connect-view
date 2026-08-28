#!/usr/bin/env bash
# Creates the release signing keystore.
#
# Run this yourself: the password you type is never seen by any tool but keytool.
# The keystore is written OUTSIDE the repository, and losing it means you can never
# publish an update to an app already on the Play Store. Back it up somewhere safe.
set -euo pipefail

KEYSTORE_DIR="${KEYSTORE_DIR:-$HOME/.android-keys}"
KEYSTORE_PATH="${KEYSTORE_PATH:-$KEYSTORE_DIR/health-connect-view.keystore}"
KEY_ALIAS="${KEY_ALIAS:-health-connect-view}"

if [ -e "$KEYSTORE_PATH" ]; then
    echo "Refusing to overwrite the existing keystore at:"
    echo "  $KEYSTORE_PATH"
    echo "Overwriting it would make Play Store updates impossible."
    exit 1
fi

mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR"

echo "Creating a 25-year RSA key. You will be asked for a password twice."
echo

keytool -genkeypair \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 9125

chmod 600 "$KEYSTORE_PATH"

cat <<EOF

Keystore created: $KEYSTORE_PATH

Add these lines to .env (which is gitignored):

  KEYSTORE_PATH=$KEYSTORE_PATH
  KEY_ALIAS=$KEY_ALIAS
  KEYSTORE_PASSWORD=<the password you just chose>
  KEY_PASSWORD=<the same password, unless you set a separate key password>

Back up the keystore file now. It cannot be regenerated, and without it you
cannot ship updates to a published app.
EOF
