# Health Connect View

A native Android app that shows you the health data already stored on your phone by
[Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) —
steps, sleep, heart rate, weight, blood pressure and every other type it supports.

Health Connect collects what your watch, scale and fitness apps write, but offers no good way
to actually look at it. This is that missing viewer: a read-only browser for your own data.

## Why it is different

**It has no internet permission.** Not a promise — a property of the build. Android prevents
the app from opening any network connection, so your health data cannot leave your device
even in principle. There is no account, no server, no analytics, no ads, no tracking.

A build check fails if `INTERNET` or `ACCESS_NETWORK_STATE` ever appears in the merged
manifest, including transitively from a dependency.

**Read-only.** Only `READ_*` permissions are requested, so the app cannot modify or delete
your records. Another check fails the build if a release ever requests write access.

**Nothing is stored.** Records live in memory only while displayed. Backups and device
transfer are disabled.

**You choose what to share.** Permissions are requested per data type, nothing preselected.

**Totals are correct.** When several apps write the same metric, their records overlap.
Summing them double-counts, so all totals come from Health Connect's own deduplicating
aggregation, and the app tells you when more than one app contributed to a figure.

## Features

- All 40 supported record types, grouped by category
- Raw record list with exact timestamps, values, units and the writing app
- Trend charts over 7 / 30 / 90 / 365 days
- Light and dark theme, dynamic colour on Android 12+
- English and German

## Building

Requires JDK 17+, the Android SDK with API 37, and a device or emulator on API 26+.

```bash
./gradlew assembleDebug          # build
./scripts/deploy.sh debug        # build, install and launch
./gradlew check                  # tests plus the privacy build checks
```

`local.properties` needs `sdk.dir` pointing at your Android SDK. No secrets are required to
build: release signing falls back to the debug key when `.env` is absent.

### Release builds

```bash
cp .env.example .env             # then fill it in
./scripts/make-keystore.sh       # creates the signing key (run this yourself)
./scripts/check-keystore.sh      # verifies the path, password and alias in .env
./scripts/deploy.sh bundle       # signed .aab for the Play Store
```

`check-keystore.sh` is worth running whenever signing misbehaves: it reports which of the
three — file, password, alias — is wrong, instead of letting the build fail deep inside R8.
Add `--prompt` to test a password interactively without writing it to `.env`. A release
build refuses to start unless these checks pass, so it can never silently fall back to the
debug key.

`.env` holds the keystore path and passwords and is gitignored. `make-keystore.sh` reads the
path and alias from it but leaves passwords to `keytool`'s own prompt, so no script ever
handles the secret.

## Project layout

| Path | What it holds |
|------|---------------|
| `registry/` | Every record type as data: name, category, units, extractors, permission |
| `health/` | Health Connect access; reads and aggregation are separate entry points |
| `ui/` | Compose screens, one state model shared across them |
| `billing/` | Entitlement gate for optional paid features |
| `docs/ROADMAP.md` | Planned work, including the dashboard start screen |

The registry is the heart of it: one generic `RecordTypeSpec` holding lambdas, keyed by
`KClass`, so a single code path serves all 40 types instead of 40 branches per screen.

Permission strings are always resolved through `HealthPermission.getReadPermission()` rather
than derived from class names, because the mapping is not one-to-one — `SleepSessionRecord`
needs `READ_SLEEP`, and Steps and StepsCadence share a single permission. A unit test checks
every resolved string against the platform constants, since a permission the platform does
not define is never granted and looks exactly like missing data.

## Translations

English and German are complete. The app is fully externalised for 16 further locales;
untranslated strings fall back to English per string. Contributions welcome — copy
`res/values/strings.xml` to `res/values-<locale>/strings.xml` and translate the values.

## Licence

MIT. See [LICENSE](LICENSE).
