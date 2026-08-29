# Health Connect View — working notes

Read-only Android viewer for Health Connect data. Kotlin + Compose, no network access.

Roadmap and design decisions: `docs/ROADMAP.md`. Feature ideas: `docs/FEATURE-IDEAS.md`.
This file is the things that are easy to get wrong and expensive to rediscover.

`private/` is gitignored and holds commercial notes (pricing, market sizing). Never quote it
into a tracked file, and never publish its contents.

## Non-negotiables

These are enforced by build checks, not just convention. Breaking one fails `./gradlew check`.

1. **No network permission, ever.** Not `INTERNET`, not `ACCESS_NETWORK_STATE`, not
   transitively. `verifyNoNetworkPermission` scans the merged manifest and fails the build.
   The privacy policy states this as fact, so it must stay true.
   - Play Billing pulls in Google's datatransport/Firebase telemetry; it is excluded in
     `app/build.gradle.kts`, with `-dontwarn` rules in `proguard-rules.pro` for the
     unreachable code paths that reference it.
   - `profileinstaller` merges in `ACCESS_NETWORK_STATE`; removed via `tools:node="remove"`.
2. **Read-only.** Only `READ_*` permissions in release. The debug source set has `WRITE_*` for
   the seeder; the same build check fails if any release manifest requests write access.
3. **No health data on disk.** Records live in memory for the current screen only. DataStore
   holds non-health UI state. Backups and device transfer are disabled.
4. **No health values in logs**, including debug builds. The debug reporters log counts,
   package names and rounded percentiles — never a reading.

## Health Connect API traps

Each of these cost time to find.

**Permissions are not 1:1 with record types.** `SleepSessionRecord` → `READ_SLEEP`, not
`READ_SLEEP_SESSION`. `HeartRateVariabilityRmssdRecord` → `READ_HEART_RATE_VARIABILITY`.
Steps and StepsCadence *share* `READ_STEPS`; MenstruationFlow and MenstruationPeriod share
`READ_MENSTRUATION`; CyclingPedalingCadence has no permission of its own and rides on
`READ_EXERCISE`. Always resolve via `HealthPermission.getReadPermission(Type::class)`; a
permission the platform does not define is simply never granted and looks exactly like
missing data. `RecordRegistryTest` checks every resolved string against `android.jar`.

Track granted-ness **per permission, not per type**, or the shared ones leave rows wrongly
locked.

**MindfulnessSession is excluded.** The library emits `READ_MINDFULNESS_SESSION`; the platform
defines only `READ_MINDFULNESS`. Revisit when they converge.

**`read()` is capped and newest-first.** It stops at `MAX_RECORDS`, so on a high-frequency
type the cap lands within days: on a real device the oldest reachable `HeartRateRecord` was 6
days back and `RespiratoryRateRecord` 3, inside a one-year request. That is right for the
record list and wrong for a chart, where it looks like missing history rather than a truncated
read. Charts for the twelve chartable types with no aggregate metric go through
`readForChart()`, which pages the whole range and thins as it goes. Everything else charts
from `dailyTotals()` and is unaffected.

**Never sum raw records.** Several apps write the same metric — on a real device 12 of 16
populated types had 2-3 writers — so records overlap and adding them double-counts. All
totals come from `aggregate*()`, which applies the platform's data-origin priority. `read()`
is display-only; `dailyTotals()` is the only source of numbers. The two are separate
repository entry points to keep that boundary visible.

**`aggregate*()` requires a `LocalDateTime` filter.** An instant-based one throws
`IllegalArgumentException: Either use TimeRangeFilter with LocalDateTime or
AggregateGroupByDurationRequest`. Use `TimeRange.localFilter()`, never `filter()`.

**Daily buckets must start at local midnight.** Health Connect slices `Period.ofDays(1)` from
the filter's start, so an unaligned start yields buckets running 20:58→20:58, which straddle
two calendar days and return null values.

**A bucket-wide interval aggregates to nothing.** A record exactly as long as the aggregation
bucket -- an app posting one 00:00-23:59 summary per day -- yields null buckets while
`readRecords` returns it. Filtering aggregation to that app alone therefore produces an empty
chart with a visible record underneath. Where a *single* source is selected its own records
may be summed directly, since one writer cannot overlap itself; never do this for the combined
view.

**Identical overlapping intervals aggregate to nothing.** Two byte-identical `StepsRecord`
entries for the same slot produce null buckets while `readRecords` still returns both.
Instantaneous types are immune. Normal multi-writer data does not trigger it — repeated
seeding does, and it looks exactly like broken aggregation.

**Reads and aggregates require the foreground.** Without
`READ_HEALTH_DATA_IN_BACKGROUND`, Health Connect refuses both once the calling activity
backgrounds: reads throw `SecurityException: Caller does not have permission ... from other
applications`, aggregates throw `must be in foreground to call aggregate method`. The debug
activities `finish()` early and keep working in `lifecycleScope`, so a long sweep starts
succeeding and then fails partway through — and fails *further up the list* on each rerun,
which reads as data being progressively revoked. `getGrantedPermissions()` keeps reporting
every type as granted throughout. Keep adb-driven probes short, or expect the tail of the run
to be meaningless.

**Hourly buckets do not deduplicate.** `aggregateGroupByDuration` with a sub-day slicer
spreads a whole-day record evenly across every bucket instead of letting data-origin priority
drop it. Measured on the phone: one writer's 00:00-23:59 summary of 13 floors contributed
13/24 to each of 24 buckets while another writer's three itemised climbs (12 total) sat on
top, so a running total ended at **24.6 against an authoritative daily total of 12**. The
daily `aggregate()` is correct; only the intraday slicing double-counts. Intraday series are
therefore rescaled to finish on the daily aggregate -- buckets supply the timing, the platform
supplies the magnitude -- and the chart says so when it has done this.

**Some types aggregate without storing records.** `BasalMetabolicRate` is derived from height
and weight: zero records, a value in every bucket. Emptiness is judged on records *and*
aggregates.

**Record shape interfaces are internal in Kotlin.** `InstantaneousRecord`/`IntervalRecord` are
public in bytecode but `internal` to the library, so `is` checks do not compile. Each spec
carries its own `startTime` lambda instead.

**Unit-typed aggregates.** Aggregates come back as `Long`, `Double`, `Duration`, or a unit
type (`Mass`, `Length`, `Energy`, `Volume`, `Power`, `Velocity`, `Percentage`, `Pressure`,
`Temperature`). Missing one silently empties that type's chart.

## Architecture

`registry/` is the heart: one generic `RecordTypeSpec` holding lambdas, keyed by `KClass`, so
one code path serves all 40 types rather than 40 branches per screen. Adding a type is a
registry entry, not a UI change. The four unchecked casts in the spec bridges are safe by
construction — a spec is only ever applied to records read via `ReadRecordsRequest(spec.type)`
— and are the only suppressions in the codebase.

`UiState` keeps `Empty` and `NoPermission` distinct: "nothing here" and "not allowed to look"
need different explanations.

Permission state is always re-read from `getGrantedPermissions()` on `ON_RESUME`. It is
authoritative; the request result can be incomplete, and access can be revoked in system
settings while backgrounded.

## Build and tooling

- `compileSdk`/`targetSdk` 37, `minSdk` 26 (mandated by the connect-client AAR). Bytecode
  targets JVM 17 — do not set 25, AGP does not support it.
- AGP 9 **bundles the Kotlin plugin**; applying `org.jetbrains.kotlin.android` fails the build.
- `allWarningsAsError` and lint `warningsAsErrors` are on, with no baseline. Fix warnings
  rather than suppressing them; count strings need `<plurals>`, not `%d` in a string.
- The system Gradle on this machine is broken (missing `gradle-public-api-legacy`). Use
  `./gradlew`; the wrapper jar was assembled from the official 9.7.1 distribution.

## Testing on hardware

The Xiaomi test phone (HyperOS) blocks `adb install` **and** `adb shell input tap`
(`SecurityException: INJECT_EVENTS`). Both need a signed-in Mi account, deliberately not
created — see `docs/ROADMAP.md` §8. So:

- **Install:** `adb push` the APK to `/sdcard/Download/hcv.apk` -- always that exact name, so
  the user is never hunting for the newest of several files -- and ask the user to tap it.
- **Drive the UI:** ask the user to tap; read the result from `screencap` and `logcat`.
- **Avoid tapping entirely** where possible — the debug activities are startable with
  `am start`: `SeedActivity`, `AggregationCheckActivity`, `DataShapeActivity`.

`./scripts/make-avd.sh` creates the emulator: 1080×2160 (exactly Play's 2:1 screenshot
maximum) with a 16 GB data partition. A full partition makes installs fail while the old build
keeps running, which reads as "my change did nothing" — that cost real debugging time.
System UI crashes once on first boot with a software GPU and recovers; wait rather than
assuming the AVD is broken.

**The emulator's health database survives `pm clear` and even `pm uninstall`**, so seeded data
accumulates across runs and duplicate intervals build up. Aggregation results there are not
trustworthy for INTERVAL types; verify on the phone.

## Secrets and the public repo

`steppicrew` is the user's public handle and is fine in the repo. **Never commit** their real
name, their personal email address, or absolute `/home/...` paths — this file is public, so
the forbidden values are deliberately not written out here; read them from the machine's
global git config. Commit as `steppicrew <google@steppicrew.de>`, set repo-locally, and add no
`Co-Authored-By` trailers.

`.env` holds the keystore path and passwords and is gitignored; `.env.example` is the tracked
template. `./scripts/check-keystore.sh` verifies path, password and alias before a release
build, so signing cannot silently fall back to the debug key. `make-keystore.sh` reads the
path from `.env` but leaves passwords to `keytool`'s prompt — no script handles the secret.

Audit before any push, using the real values from the global git config:

    git grep -nIE "$(git config --global user.name | tr ' ' '|')|/home/" -- . ':!*.md'

The one legitimate `/home/` match is the `your-user` placeholder in `.env.example`.
