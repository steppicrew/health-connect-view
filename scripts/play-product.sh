#!/usr/bin/env bash
# Creates or updates the one-time "pro" product in the Play Console, and activates it.
#
#   ./scripts/play-product.sh
#
# Safe to rerun: the product is patched in place, so changing a title or the price here and
# running it again is the whole procedure. The price is the German one *including* VAT; Play's
# conversion takes a net price, so it is converted back from that and every other region gets
# Play's own rounding. Germany itself is pinned, so it never drifts by a cent.
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
import base64, json, os, time, urllib.parse, urllib.request, urllib.error

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



PRODUCT = "pro"
OPTION = "buy"
PRICE_DE = 3.99   # what a buyer in Germany pays, VAT included
VAT_DE = 0.19
LISTINGS = [
    {"languageCode": "en-US", "title": "Pro",
     "description": "Unlocks CSV export and all future Pro features. One-time purchase, no subscription."},
    {"languageCode": "de-DE", "title": "Pro",
     "description": "Schaltet den CSV-Export und alle künftigen Pro-Funktionen frei. Einmalkauf, kein Abo."},
]


def money(currency: str, amount: float) -> dict:
    units = int(amount)
    return {"currencyCode": currency, "units": str(units), "nanos": round((amount - units) * 1e9)}


net = round(PRICE_DE / (1 + VAT_DE), 2)
converted = api(f"{base}/pricing:convertRegionPrices", method="POST",
                body={"price": money("EUR", net)})
version = converted["regionVersion"]["version"]
regions = []
for code, region in sorted(converted["convertedRegionPrices"].items()):
    price = money("EUR", PRICE_DE) if code == "DE" else region["price"]
    regions.append({"regionCode": code, "price": price, "availability": "AVAILABLE"})
others = converted["convertedOtherRegionsPrice"]

product = {
    "packageName": package,
    "productId": PRODUCT,
    "listings": LISTINGS,
    "purchaseOptions": [{
        "purchaseOptionId": OPTION,
        # Legacy-compatible so BillingClient's plain one-time flow can buy it.
        "buyOption": {"legacyCompatible": True, "multiQuantityEnabled": False},
        "regionalPricingAndAvailabilityConfigs": regions,
        "newRegionsConfig": {
            "usdPrice": others["usdPrice"],
            "eurPrice": others["eurPrice"],
            "availability": "AVAILABLE",
        },
    }],
}
query = urllib.parse.urlencode({
    "allowMissing": "true",
    "updateMask": "listings,purchaseOptions",
    "regionsVersion.version": version,
})
try:
    api(f"{base}/onetimeproducts/{PRODUCT}?{query}", method="PATCH", body=product)
    api(f"{base}/oneTimeProducts/{PRODUCT}/purchaseOptions:batchUpdateStates", method="POST", body={
        "requests": [{"activatePurchaseOptionRequest": {
            "packageName": package, "productId": PRODUCT, "purchaseOptionId": OPTION,
        }}],
    })
except urllib.error.HTTPError as error:
    raise SystemExit(f"error: {error.code} {error.read().decode()}")

# The API is inconsistent about the path: patch answers only on "onetimeproducts", while
# reads and state changes answer only on "oneTimeProducts". Each call uses the one that works.
state = api(f"{base}/oneTimeProducts/{PRODUCT}")
for option in state.get("purchaseOptions", []):
    print(f"{package}: '{PRODUCT}' / '{option['purchaseOptionId']}' -- {option.get('state', '?')}, "
          f"{len(option.get('regionalPricingAndAvailabilityConfigs', []))} regions, "
          f"DE {PRICE_DE:.2f} EUR")
PY
