#!/usr/bin/env bash
# Creates the release signing keystore.
#
# Run this yourself: the password you type is never seen by any tool but keytool.
# The keystore is written OUTSIDE the repository, and losing it means you can never
# publish an update to an app already on the Play Store. Back it up somewhere safe.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Read KEYSTORE_PATH and KEY_ALIAS from .env so the keystore lands where the build expects
# it. Passwords are deliberately NOT read: keytool prompts for them, so the secret is typed
# straight into the tool that needs it and never passed through this script or its argv,
# where it could end up in a process listing or shell history.
if [ -f "$ROOT_DIR/.env" ]; then
    KEYSTORE_PATH="$(grep -E "^KEYSTORE_PATH=" "$ROOT_DIR/.env" | cut -d= -f2- | tr -d '"'"'"'"' || true)"
    KEY_ALIAS="$(grep -E "^KEY_ALIAS=" "$ROOT_DIR/.env" | cut -d= -f2- | tr -d '"'"'"'"' || true)"
fi

KEYSTORE_PATH="${KEYSTORE_PATH:-$HOME/.android-keys/health-connect-view.keystore}"
KEY_ALIAS="${KEY_ALIAS:-health-connect-view}"
KEYSTORE_DIR="$(dirname "$KEYSTORE_PATH")"

echo "Keystore path: $KEYSTORE_PATH"
echo "Key alias:     $KEY_ALIAS"
echo

if [ -e "$KEYSTORE_PATH" ]; then
    echo "Refusing to overwrite the existing keystore at:"
    echo "  $KEYSTORE_PATH"
    echo "Overwriting it would make Play Store updates impossible."
    exit 1
fi

mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR"

cat <<'EOF'
Creating a 25-year RSA key. keytool will prompt for a password.

Use the same password you put in .env as KEYSTORE_PASSWORD, or update .env
afterwards to match what you type here -- the build reads the password from
.env, while keytool asks for it directly so no script ever handles it.

EOF

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
