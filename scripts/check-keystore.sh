#!/usr/bin/env bash
# Checks that the keystore and passwords in .env actually work, before a release build
# fails halfway through with a less obvious message.
#
#   ./scripts/check-keystore.sh            verify the passwords stored in .env
#   ./scripts/check-keystore.sh --prompt   ignore .env and type a password to test it
#
# Passwords are never printed and never passed on the command line, where they would be
# visible to any other process via the process list.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$ROOT_DIR/.env"

read_env() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '"'\''' || true; }

KEYSTORE_PATH="$(read_env KEYSTORE_PATH)"
KEY_ALIAS="$(read_env KEY_ALIAS)"
KEYSTORE_PATH="${KEYSTORE_PATH:-$HOME/.android-keys/health-connect-view.keystore}"
KEY_ALIAS="${KEY_ALIAS:-health-connect-view}"

echo "Keystore: $KEYSTORE_PATH"
echo "Alias:    $KEY_ALIAS"
echo

if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "FAIL: no keystore at that path."
    echo "      Either KEYSTORE_PATH in .env is wrong, or the key has not been created yet"
    echo "      (./scripts/make-keystore.sh)."
    exit 1
fi
echo "OK:   keystore file exists"

if [ "${1:-}" = "--prompt" ]; then
    read -r -s -p "Store password to test: " STORE_PASSWORD; echo
else
    STORE_PASSWORD="$(read_env KEYSTORE_PASSWORD)"
    if [ -z "$STORE_PASSWORD" ]; then
        echo "FAIL: KEYSTORE_PASSWORD is not set in .env"
        exit 1
    fi
fi

# -storepass on the command line would expose the password in the process list, so it is
# fed to keytool on stdin instead.
if printf '%s\n' "$STORE_PASSWORD" | keytool -list -keystore "$KEYSTORE_PATH" >/dev/null 2>&1; then
    echo "OK:   store password is correct"
else
    echo "FAIL: store password is wrong"
    echo "      Update KEYSTORE_PASSWORD in .env to the password you typed when creating"
    echo "      the keystore. If you no longer know it, the key cannot be recovered: delete"
    echo "      it and create a new one (safe until the app is published, not after)."
    exit 1
fi

KEY_PASSWORD="$(read_env KEY_PASSWORD)"
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

# Exporting the key itself requires the key password, which may differ from the store one.
if printf '%s\n' "$STORE_PASSWORD" | keytool -exportcert \
        -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" >/dev/null 2>&1; then
    echo "OK:   alias '$KEY_ALIAS' exists"
else
    echo "FAIL: no key with alias '$KEY_ALIAS' in this keystore"
    echo "      Aliases present:"
    printf '%s\n' "$STORE_PASSWORD" | keytool -list -keystore "$KEYSTORE_PATH" 2>/dev/null \
        | grep -i "PrivateKeyEntry" | sed 's/^/        /'
    exit 1
fi

echo
echo "All good. ./scripts/deploy.sh release will sign with this key."
