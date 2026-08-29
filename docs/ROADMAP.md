# Roadmap

Planned work, not yet built. Recorded here so the intent survives beyond the session it was
discussed in.

## 1. Dashboard start screen (next major feature)

Replace the catalog as the launch destination with a **configurable grid of tiles**, each
showing one data type in a form that suits it. The catalog stays as the complete index of all
40 types and as the tile picker, but moves behind a nav entry.

### Tiles

- **A grid of resizable tiles.** 1x1 only to begin with, but tiles carry their span in the
  stored config from the start -- retrofitting spans later would mean rewriting both the
  config schema and the drag geometry.
- **The form is per type, and lives in the registry.** Steps and floors show a ring against a
  goal; heart rate shows a short curve coloured from blue to red across a fixed 50-160 bpm
  scale. This is per-type presentation knowledge, so it belongs in `RecordTypeSpec` as a
  `TileSpec`, not in the dashboard. A `when (type)` in the UI would reintroduce exactly the 40
  branches the registry exists to prevent.
- **Fixed colour scales, not per-window normalisation.** A window-relative scale makes every
  day look dramatic and makes two days incomparable.
- **Tap** opens the tile full screen. **Long-press** enters edit mode: move, delete, add.

### Full-screen view

Opens from a tile and shows that type over a selectable span: **today so far, last 7 days,
last 4 weeks, last year**, with `<` / `>` stepping one span back and forth. Never steps past
today.

This is a different concept from the existing `TimeRange` enum, which means "the last N days
from now" and has no offset -- it needs a sibling type, not extra entries. It is also what
finally makes data older than a year reachable (section 6).

### Implementation notes

- **Values come from `aggregate()`**, never from summing raw records -- a day tile is exactly
  where multiple writers would double-count. Types with no aggregate metric cannot show a
  total at all: show the latest reading or a record count.
- **Intraday tiles need a new repository entry point.** `dailyTotals()` buckets by day; an
  hourly curve needs `aggregateGroupByDuration`. Instantaneous types (heart rate) can chart
  raw points safely, but any *interval* type shown intraday must aggregate. Verify on the
  phone: the emulator's duplicate-interval problem (section 5) makes its results untrustworthy
  for interval types.
- **Charts for types with no aggregate metric must use `readForChart()`**, not `read()`, or
  they will be cut off within days (section 6).
- Day boundaries are **local midnight**, via `TimeRange.localFilter()`'s alignment.
- Fetch tiles concurrently with a `Semaphore` cap, as the catalog probe already does.
- **Goals** are non-health UI state: DataStore, keyed by type, and only meaningful for types
  with an aggregate metric. A ring without a total has nothing to fill.
- Tile configuration is likewise DataStore. Health values are still never persisted.
- Empty vs. not-granted stays distinct per tile, as `UiState` already encodes.

### Build order

Each step leaves a working app.

1. `TileSpec` in the registry + `DashboardConfig` in DataStore; no UI yet.
2. Dashboard screen with a fixed starter set and the number renderer only; becomes the start
   destination, catalog demoted.
3. Ring renderer + goals; curve renderer + the intraday aggregation entry point.
4. Full-screen view with span + offset stepping (reuses the existing chart).
5. Edit mode: long-press to move, delete, add. Resize deferred -- it needs the span geometry
   and a second gesture, and move/delete/add is most of the value.
6. Settings screen (section 2), which is where goals and tile order need a home anyway.

### Open questions

- Which stats form the default tile set on first run?
- Should a tile show a comparison (vs. yesterday, vs. 7-day average)? Useful, but it is a
  second aggregation per tile.
- Does "advanced dashboard" (custom tiles beyond a free allowance) become the premium feature?
  `Feature.CUSTOM_DASHBOARD` is reserved for it. Free would keep a fixed starter dashboard;
  paid unlocks arbitrary tiles.
- **Check which types actually arrive before designing tiles around them.** Proprietary
  wearable metrics (Body Battery, Training Status) are often not Health Connect record types
  at all. Section 5 shows what one real device receives.

## 2. Settings screen

A single place for the preferences that currently have no UI at all.

- **Language** — default "System", plus an explicit list of the shipped translations. On
  Android 13+ this should drive the platform's own per-app language API
  (`AppCompatDelegate.setApplicationLocales` / `LocaleManager`), so the choice also shows up
  in Android's own per-app language settings rather than being a private override. A
  `res/xml/locales_config.xml` declaring the supported locales is required for that.
- **Theme** — Light / Dark / System (default System). Dynamic colour is on by default on
  Android 12+; worth a toggle for people who prefer the app's own palette.
- **Shown stats** — which types appear on the dashboard (see section 1) and in what order.
  This is the same configuration the dashboard tiles read, so build it alongside them.
- **Manage access** — a shortcut into Health Connect's own permission screen, and a
  "revoke all" that calls `PermissionController.revokeAllPermissions()`.
- Link to the privacy policy and the source repository.

All of this is non-health UI state and belongs in the existing DataStore. Health values
themselves are still never persisted.

## 3. Source selection

When several apps write the same metric, let the user see the combined view by default but
switch to a single source.

### Behaviour

- Default stays the **combined, deduplicated** view. That is what Health Connect's own
  aggregation produces, and it is the correct answer for "how many steps did I take" — it is
  not the same as any one app's figure, which is the point.
- Where more than one app contributed, offer a source picker: "All sources (deduplicated)"
  plus one entry per contributing app, resolved to its display name.
- Selecting one source filters both the raw list and the chart to that app alone.

### Implementation notes

- Verified as directly supported: `ReadRecordsRequest`, `AggregateRequest` and the grouped
  aggregate requests all take a `dataOriginFilter: Set<DataOrigin>`. Passing a single origin
  scopes everything to that app; passing an empty set is the current all-sources behaviour.
- Contributing apps are already known — `AggregationResult.dataOrigins` is what the existing
  "N apps wrote this data" note reads, so the picker needs no extra query.
- **"Primary source" is not ours to define.** Health Connect keeps a user-configured app
  priority list, used to decide which record wins when two overlap, and it is not exposed to
  apps through the Jetpack client. So the honest options are the deduplicated view (which
  already respects that priority) or an explicit per-app view. Do not invent a "primary" by
  picking the app with the most records — that would silently disagree with the platform.
- A per-app view must never be presented as a total for the day. It is "what this app
  recorded", and the label should say so.

## 4. Chart refinement

The current chart is a deliberately plain Compose Canvas line: axis min/max, date endpoints,
guide lines. Known gaps, in rough priority order:

- Y-axis gridline labels at intermediate values, not just min/max.
- X-axis tick labels between the endpoints.
- Touch a point to read its exact value and timestamp.
- Bar rendering for count-like types (steps, floors) where a line implies false continuity.
- Empty-day gaps shown as gaps rather than interpolated straight through.

Vico was the original choice and was dropped because Vico 3.x's Compose Multiplatform rewrite
changed the axis API surface. Revisit if the chart requirements grow beyond what is
comfortable to hand-draw; everything renders through one `LineChart(points, modifier)`
signature, so it stays a single-file swap.

## 5. Aggregation: verified working, with one caveat

**Verified on a real device, across every aggregatable type.** Aggregation returned values for
all 16 types that hold data, and **12 of those 16 have more than one writing app** -- Garmin
Connect, Health Sync, Life Fitness and the phone's own step counter, in combinations that vary
per type:

| Type | Writers | Raw records (30d) |
|---|---|---|
| Steps, Distance, HeartRate | 3 | 5000+ each (paging cap) |
| ActiveCaloriesBurned | 3 | 1012 |
| ExerciseSession, Speed | 3 | 126 / 50 |
| TotalCaloriesBurned, FloorsClimbed, RestingHeartRate, SleepSession, Weight, Power | 2 | 10-188 |

Multi-writer data is therefore the normal case here, not an edge case, which is why every
total goes through `aggregate*()` and never through arithmetic on raw records. Over seven
days, 1708 raw step records aggregate to plausible daily totals of 4,560-18,014.

**Caveat found while testing: identical overlapping intervals aggregate to nothing.** Seeding
the emulator twice produced two byte-identical `StepsRecord` entries per time slot; every
daily bucket then came back with a null value while `readRecords` still returned all 180
records. Instantaneous types (Weight, HeartRate) were unaffected -- 30 of 30 buckets had
values -- because a point in time cannot overlap ambiguously the way an interval can.

This is a data problem rather than an app bug, and it does not arise from normal multi-writer
data, where records differ. It is worth knowing because it looks exactly like broken
aggregation: the call succeeds, the buckets are correctly bounded, and every value is null.

Note also that `aggregate*()` **requires a `LocalDateTime`-based `TimeRangeFilter`**. An
instant-based one throws `IllegalArgumentException: Either use TimeRangeFilter with
LocalDateTime or AggregateGroupByDurationRequest`. The app does this correctly, but it is easy
to reintroduce.

Where no aggregate is available the app degrades honestly, charting raw readings labelled as
individual measurements rather than presenting them as totals.

### Measured data shape (real device, 30 days)

Recorded so test fixtures can match reality. No health values here — record counts, cadence
and writer counts only.

| Type | Writers | Records | Per day | Gap |
|---|---|---|---|---|
| RespiratoryRate | 1 | 5000+ | 1132 | 1 min |
| HeartRate | 3 | 5000+ | 742 (×11 samples) | 2 min |
| OxygenSaturation | 1 | 5000+ | 439 | 1 min |
| Steps | 3 | 5000+ | 215 | 1 min |
| ElevationGained | 1 | 1788 | 123 | 1 min |
| ActiveCaloriesBurned | 3 | 1012 | 37 | 15 min |
| HeartRateVariability | 1 | 2713 | 91 | 5 min |
| SleepSession, RestingHeartRate | 2 | 60 | 2 | ~10 h |
| Weight, Height, BodyFat | 1-2 | 4-10 | 1-3 | days |
| Vo2Max, BloodPressure | 1 | 1-3 | — | — |

Two consequences: the 5000-record paging cap is reached routinely rather than rarely, and
charts must cope with anything from one point to tens of thousands.

### Three behaviours this uncovered

**Some types aggregate without storing records.** `BasalMetabolicRate` returned 0 raw records
but a value in all 30 daily buckets and no data origins: Health Connect derives it from height
and weight rather than storing it. Treating "no raw records" as empty hid a chart the platform
could draw, so both the catalog probe and the detail screen now consider aggregation as well.

**The 5000-record paging cap is reached in practice.** Steps, Distance, HeartRate,
OxygenSaturation and RespiratoryRate each exceed it within a month. Only the raw list is
affected; charts read aggregates that Health Connect computes over the full period, so trends
stay correct. The notice now says so.

**Skin temperature charted nothing.** The spec extracted no points and read only the nullable
`baseline`, while the measurements live in `deltas`. On a device that records deltas the app
listed thirty records as em-dashes with an empty chart. Now fixed — found only because the
shape measurement reported zero extractable values against thirty records, which is the kind
of contradiction worth looking at.

## 6. History reach: measured, and two bugs found

**Measured on a real device on 2026-08-29**, by asking for a 1000-day window per type and
logging the oldest record returned (`HistoryReachActivity`, debug-only, logs dates and counts
only). The trigger was a simple observation: Health Connect's own app showed data from April
2025, while this app's charts began in July 2026.

**The first hypothesis was wrong.** `READ_HEALTH_DATA_HISTORY` was missing from every
requested permission set, and a 30-day cap fit the observed July start almost exactly. It was
already granted -- the user had granted it in Health Connect's own UI -- so it was never the
cause. It was still a real bug (see below), just not this one. The lesson is the cheap one:
measure the floor before explaining it, because two different mechanisms produce the same
symptom.

### Bug A: the history permission could not be granted from inside the app

Declared in the manifest, never requested. Every permission set was built from
`RecordRegistry.allReadPermissions`, which holds only per-type read permissions; the history
permission belongs to no record type, so `selectAll()` could not select it and it never
reached the launcher. A declared-but-unrequested Health Connect permission is simply never
granted.

Anyone who had not granted it by hand in Health Connect was capped at 30 days, with the 90-day
and 1-year ranges silently returning a month of data. Fixed by tracking it separately from the
type permissions -- the granted/total counter counts *types*, and folding history in would
report 41 of 41 where 40 exist.

`TimeRange.needsHistoryPermission` existed but had no callers; it is now the gate, and a
capped range says so instead of just drawing a short chart.

### Bug B: charts were cut off by the record cap, not by the date range

This was the actual cause of the July start. `read()` returns records **newest-first** and
stops at `MAX_RECORDS`, so on a high-frequency type the cap lands within days:

| Type | Oldest reachable via `read()` | Via `readForChart()` |
|------|------------------------------|----------------------|
| RespiratoryRate | 3 days | — |
| HeartRate | 6 days | **412 days** |
| OxygenSaturation | 10 days | — |
| Distance | 13 days | **421 days** |
| Steps | 19 days | **472 days** |
| HeartRateVariabilityRmssd | 57 days | **401 days** |
| ActiveCaloriesBurned | 161 days | **472 days** |

Right for the record list, wrong for a chart: a year-long request drew the last six days of
heart rate and looked like missing history. Only the twelve chartable types with **no**
aggregate metric were affected -- everything else charts from `dailyTotals()` and was never at
risk. `readForChart()` pages the whole range and thins as it goes, keeping an evenly spaced
sample bounded by `CHART_POINTS`, so the series spans the full period at reduced resolution.

The truncation notice claimed "Charts still cover the whole period", which was false for
exactly these types. It now describes the list.

### Still open: the range ceiling

`TimeRange.YEAR` is 365 days with no offset, so nothing older is reachable at all. The measured
data shows the wall directly -- BodyFat, BodyWaterMass, BoneMass and Height all report their
oldest record as **exactly** `daysBack=365`, which is the request boundary rather than the end
of the data. Types with genuinely older data reach 472 days (FloorsClimbed, RestingHeartRate,
May 2025).

Reaching April 2025 needs range + offset stepping, which is part of the dashboard's
full-screen view (section 1) rather than a fifth entry in the `TimeRange` enum.

### Trap: probes must stay in the foreground

Without `READ_HEALTH_DATA_IN_BACKGROUND`, Health Connect refuses reads and aggregates once the
calling activity backgrounds. The debug activities `finish()` early and continue in
`lifecycleScope`, so a long sweep succeeds at first and then fails partway through -- and fails
*further up the list* on each rerun, while `getGrantedPermissions()` keeps reporting every type
as granted. It reads convincingly as permissions being progressively revoked. It is not; it is
the foreground window closing. Keep adb-driven probes short.

## 7. Considered and rejected: a React/Vite UI in a WebView

Asked whether the UI would be easier as TypeScript/React talking to Kotlin, and whether that
is possible without the INTERNET permission.

**It is possible.** A WebView loading `file:///android_asset/` needs no INTERNET permission —
that is local file access, not networking — and `addJavascriptInterface` passes data from
Kotlin into JS over the JNI bridge with no HTTP involved. The privacy guarantee would survive
intact. (An HTTP API, even to localhost, would not be worth it: it needs a local server and
muddies the "cannot reach the network" story that the manifest currently makes obvious.)

**Not adopted, because "easier" does not hold for this app.** The hard problems here were the
record-to-permission mapping, deduplicating overlapping writers, and locale-correct
formatting. A rewrite re-solves none of them and adds a serialisation layer in front of each.
It would cost dynamic colour, the platform per-app language integration, and Compose's
built-in accessibility, and it would add a second build system and language for screens that
already work. The remaining chart gaps (section 4) are a few hundred lines of Canvas — much
less than a bridge plus a JS toolchain.

Worth revisiting if the UI grows into something genuinely interactive that a JS charting
library would do far better, or if the same UI is ever wanted on the web.

## 8. Testing note: adb input injection on Xiaomi/HyperOS

`adb shell input tap` fails on HyperOS with:

    SecurityException: Injecting input events requires the caller ... INJECT_EVENTS permission

Enabling it needs Developer options -> "USB debugging (Security settings)", which requires a
signed-in Mi account. Deliberately not done: tying a vendor account to a device that holds
personal health data is a poor trade for the convenience of scripted taps, in a project whose
whole point is that the data stays put. Screenshots (`adb exec-out screencap`), logcat,
`am start` and file push all work regardless, and `adb install` is blocked by the same
restriction -- push the APK to Downloads and install it by tapping instead.

So on such a device, drive the UI by hand and read the result from screenshots and logs. The
debug-only `AggregationCheckActivity` exists for exactly this: it is startable with `am start`
and reports raw-versus-aggregated counts per type without needing a single tap.

## 9. Deferred

- **MindfulnessSession** — excluded from v1: the library requests
  `READ_MINDFULNESS_SESSION` while the platform defines only `READ_MINDFULNESS`, so the
  permission can never be granted. Add once those names converge.
- **Imperial units** — everything is metric today, matching what Health Connect returns
  natively. The registry's `unitRes` field is the seam for adding a conversion.
- **Play Billing products** — the entitlement gate is wired but reports no premium access
  until products exist in the Play Console.
