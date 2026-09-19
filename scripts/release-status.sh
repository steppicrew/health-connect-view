#!/usr/bin/env bash
# What is actually live on each Play track, read from the Play API.
#
#   ./scripts/release-status.sh
#
# The build log is not evidence. gradle-play-publisher reports the track *it* used, and
# release.sh reports the track it was *asked* for; when the two disagreed -- the track was
# hardcoded in the gradle play block, so --track production uploaded to internal -- both
# printed something reassuring and neither was the store. This asks the store.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

die() { echo "error: $*" >&2; exit 1; }

[ -f .env ] || die ".env not found; copy .env.example and fill it in"
set -a; . ./.env; set +a
[ -n "${PLAY_SERVICE_ACCOUNT_JSON:-}" ] || die "PLAY_SERVICE_ACCOUNT_JSON is not set in .env"
[ -f "$PLAY_SERVICE_ACCOUNT_JSON" ] || die "service account file not found"

PACKAGE="$(grep -oP 'applicationId\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1)"
[ -n "$PACKAGE" ] || die "could not read applicationId"

PACKAGE="$PACKAGE" python3 - <<'PY'
import base64, json, os, time, urllib.parse, urllib.request

sa = json.load(open(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]))
package = os.environ["PACKAGE"]


def b64(data: bytes) -> bytes:
    return base64.urlsafe_b64encode(data).rstrip(b"=")


try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
except ImportError:
    raise SystemExit("error: needs python3 'cryptography' (pip install cryptography)")

now = int(time.time())
header = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
claims = b64(json.dumps({
    "iss": sa["client_email"],
    "scope": "https://www.googleapis.com/auth/androidpublisher",
    "aud": "https://oauth2.googleapis.com/token",
    "iat": now,
    "exp": now + 3600,
}).encode())
signing_input = header + b"." + claims
key = serialization.load_pem_private_key(sa["private_key"].encode(), password=None)
jwt = signing_input + b"." + b64(key.sign(signing_input, padding.PKCS1v15(), hashes.SHA256()))

token = json.load(urllib.request.urlopen(
    "https://oauth2.googleapis.com/token",
    urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": jwt.decode(),
    }).encode(),
))["access_token"]

base = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}"


def api(url: str, method: str = "GET", body=None):
    request = urllib.request.Request(
        url, method=method, headers={"Authorization": f"Bearer {token}"}
    )
    if body is not None:
        request.add_header("Content-Type", "application/json")
        request.data = json.dumps(body).encode()
    return json.load(urllib.request.urlopen(request))


# A read needs an edit like any other operation; it is simply never committed.
edit = api(f"{base}/edits", method="POST", body={})["id"]

print(f"{package}\n")
for track in ("production", "beta", "alpha", "internal"):
    try:
        releases = api(f"{base}/edits/{edit}/tracks/{track}").get("releases", [])
    except Exception:
        print(f"  {track:<12} (no such track)")
        continue
    if not releases:
        print(f"  {track:<12} —")
        continue
    for release in releases:
        codes = ", ".join(release.get("versionCodes", []) or ["—"])
        name = release.get("name", "?")
        status = release.get("status", "?")
        fraction = release.get("userFraction")
        rollout = f"  rollout {fraction:.0%}" if fraction else ""
        print(f"  {track:<12} {name} (code {codes}) — {status}{rollout}")
PY
