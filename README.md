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
transfer are disabled. The one exception is an export you start yourself: it is written
straight into the file you pick in the system's save dialog, and the app keeps no copy. A
report you view or print is built in memory and handed to the system's print preview.

**You choose what to share.** Permissions are requested per data type, nothing preselected.

**Totals are correct.** When several apps write the same metric, their records overlap.
Summing them double-counts, so all totals come from Health Connect's own deduplicating
aggregation, and the app tells you when more than one app contributed to a figure.

**It says what it cannot know.** Health Connect stores no link between a workout and the
readings taken during it, so a session's statistics are matched by time, and every screen
showing them says so. Nothing is predicted and nothing is scored.

## Features

Free:

- A dashboard of tiles for the figures you care about, one day at a time: numbers, rings
  against a goal, small charts. Arranged by dragging; backed up to a file and restored on
  another phone
- All 41 supported record types, grouped by category, each record with its exact time,
  value, unit and the app that wrote it
- Charts by day, week, four weeks or year, with sleep and workouts shaded behind the day
- Daily goals for steps, distance, floors and calories, fixed or -- for steps and distance --
  adaptive: raised after days above the goal and lowered after days below it
- Streaks, personal bests and trend arrows comparing the last week with the 30-day average
- Workouts with heart rate, speed and height in one chart, breaks left out, and the route
  if you allow it -- drawn without a map, since there is no network
- Sleep stages beside the night's heart rate, breath rate, SpO₂ and HRV
- A cycle overview: the cycles of a year side by side, only what was recorded
- How a reading was taken: blood glucose fasting or after a meal, blood pressure seated or
  at the wrist
- Filter any figure by the app that wrote it
- Metric or imperial units, blood glucose in mmol/L or mg/dL
- Light and dark theme, dynamic colour on Android 12+, charts described for screen readers
- Seventeen languages

Pro, a one-time purchase with no subscription:

- CSV export of any type, as raw records or deduplicated daily totals
- PDF reports, among them blood pressure, weight, resting heart rate and blood glucose for
  a doctor, alone or in one document
- Larger tiles showing a week, four weeks or a year, last night's stages or the latest
  workouts; several tiles per figure; tile colours
- The year as a calendar, what changed this week, this month beside the same month last
  year, sleep rhythm, one kind of workout across the year
- Body composition, two figures on one time axis, routes as GPX files

The purchase goes through Google Play Billing with its telemetry dependencies excluded, so
it adds no network permission to the app either.

## Building

Requires JDK 17+, the Android SDK with API 37, and a device or emulator on API 26+.

```bash
./gradlew assembleDebug          # build
./scripts/deploy.sh debug        # build, install and launch
./gradlew check                  # tests plus the privacy build checks
./scripts/make-avd.sh            # create the emulator this project tests against
```

The AVD it creates is 1080x2160 — exactly the 2:1 maximum aspect ratio Play accepts for
phone screenshots, so store assets need no cropping — with a 16 GB data partition, because
a full one makes installs fail while the previous build keeps running.

`local.properties` needs `sdk.dir` pointing at your Android SDK. No secrets are required to
build: release signing falls back to the debug key when `.env` is absent.

`./gradlew check` treats compiler and lint warnings as errors, with no baseline. That
includes lint's notices about newer dependency versions, so it can fail on a tree nobody
touched until the versions in `gradle/libs.versions.toml` are raised.

Debug builds install beside the release as `de.steppicrew.healthconnectview.debug` and add
what the release must never have: write permissions for a data seeder, probe activities for
checking aggregation on a device, and an intent extra that opens any screen directly. The
release source set has an empty twin of that last one, and a build check
(`verifyNoDebugBackdoor`) confirms it.

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

Publishing to Play is scripted as well:

```bash
./scripts/release.sh --dry-run   # everything except the upload
./scripts/release.sh             # build, then publish the bundle and changed listing assets
./scripts/release-status.sh      # what is actually live on each track
```

`release.sh` refuses a dirty tree or a version already published, and stops unless
`app/src/main/play/release-notes/<lang>/entries/<versionCode>.txt` exists for every store
language. The store listing lives beside them in `app/src/main/play/listings/`.

## Project layout

Under `app/src/main/java/de/steppicrew/healthconnectview/`:

| Path | What it holds |
|------|---------------|
| `registry/` | Every record type as data: name, category, units, extractors, permission, how its tile and chart behave |
| `health/` | Health Connect access; reads and aggregation are separate entry points. Goals, streaks and sessions are worked out here |
| `dashboard/` | The dashboard's configuration: tiles, their sizes, goals and colours, and its backup file |
| `ui/` | Compose screens: dashboard, type detail, sessions, cycle, compare, insights, settings |
| `export/` | CSV, PDF and GPX, written only into a file the user chose |
| `billing/` | Entitlement gate for the Pro features |
| `settings/` | Non-health UI state in DataStore |

Elsewhere:

| Path | What it holds |
|------|---------------|
| `app/src/debug/` | Seeder, probe activities and the navigation backdoor; never in a release |
| `app/src/main/play/` | Store listing texts and images, and per-version release notes, for every store language |
| `scripts/` | Building, signing, releasing, store assets, the privacy page |
| `docs/ROADMAP.md` | What was built and why, with the measurements behind the decisions |
| `docs/FEATURE-IDEAS.md` | Ideas not yet planned |
| `PRIVACY.md` | The privacy policy |
| `CLAUDE.md` | Working notes: the Health Connect behaviours that were expensive to find |

The registry is the heart of it: one generic `RecordTypeSpec` holding lambdas, keyed by
`KClass`, so a single code path serves all 41 types instead of 41 branches per screen.
Adding a type is a registry entry, not a UI change.

Permission strings are always resolved through `HealthPermission.getReadPermission()` rather
than derived from class names, because the mapping is not one-to-one — `SleepSessionRecord`
needs `READ_SLEEP`, and Steps and StepsCadence share a single permission. A unit test checks
every resolved string against the platform constants, since a permission the platform does
not define is never granted and looks exactly like missing data.

Numbers shown as totals never come from summing records. `read()` is for display,
`dailyTotals()` is the only source of figures, and the two are separate repository entry
points to keep that boundary visible.

## Translations

The app ships in seventeen languages: English, German, Arabic, Chinese (simplified and
traditional), Dutch, French, Hindi, Indonesian, Italian, Japanese, Korean, Polish,
Portuguese (Brazil), Russian, Spanish and Turkish. A new string goes into every
`res/values-*/strings.xml` in the same commit; a unit test fails a language that was
complete and drops a key. Corrections from native speakers are welcome.

## Licence

MIT. See [LICENSE](LICENSE).
