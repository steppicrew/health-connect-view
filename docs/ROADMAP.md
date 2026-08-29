# Roadmap

What exists, why it works the way it does, and what is still planned. Sections marked **built**
describe shipped behaviour and the decisions behind it; the rest is intent recorded so it
survives beyond the session it was discussed in.

## 1. Dashboard start screen — built

The launch destination is a configurable grid of tiles, each showing one data type in a form
that suits it. The catalog remains the complete index of all 40 types and the tile picker, and
sits behind a nav entry.

### Tiles

- **A grid of tiles**, 1x1 today. Tiles carry their span in the stored config from the start,
  so adding 2x1 and 2x2 later does not mean migrating every stored config and rewriting the
  layout geometry at the same time.
- **The form is per type and lives in the registry**, as `TileSpec`: a ring against a goal, a
  curve coloured across a fixed value range, or a plain number. One renderer per form serves
  every type that declares it; a `when (type)` in the dashboard would have reintroduced exactly
  the 40-way branch the registry exists to prevent. `NUMBER` is the default, so adding a type
  still needs no tile decision.
- **Colour scales are fixed, not window-relative.** Heart rate runs blue to red across
  50-160 bpm. A scale normalised to each window paints every day in the full sweep, so a calm
  day looks like an alarming one and two days cannot be compared.
- **Tap** opens the tile full screen. **Long-press or the toolbar button** enters edit mode:
  move, remove, add, and set a goal on ring tiles. Both a gesture and a button, because a
  gesture alone is undiscoverable and unreachable with accessibility services.
- Reordering is by single steps rather than drag-and-drop: nothing to discover, and a tile
  cannot land in an unintended slot.

### Full-screen view

Opens from a tile: day, week, four weeks, or year, with `<` `>` stepping one window back and
forth. Never steps past the current window.

`Span` is a separate concept from `TimeRange`, which means "the last N days from now" and
cannot be moved. Anchoring to calendar boundaries with an offset is what makes data older than
a year reachable at all — `TimeRange` topped out at 365 days, so nothing before that could be
requested no matter which range was chosen.

Windows tile exactly: each window's start is the previous one's end, asserted across every span
and six offsets. Steps use calendar periods, so a year step lands on the same date and survives
a leap day.

### Values

- **Totals come from `aggregate*()`, never from summing raw records.** A day tile is exactly
  where several apps writing the same metric would double-count.
- **A missing value renders as a dash, never as 0.** "Nothing was recorded" and "you took no
  steps" are different claims, and rendering the first as zero states the second.
- **Types with no aggregate metric show the day's latest reading**, which is a different
  statement — a weight, not a sum — and the only honest number available for them.
- Tiles load concurrently under a `Semaphore` cap, as the catalog probe already does.
- Tile configuration and goals are non-health UI state in DataStore. Health values are never
  persisted.

### Still open

- Tile resize (2x1, 2x2). The config already stores spans; what is missing is the layout
  geometry and a second gesture.
- Which stats form the default tile set on first run. Currently steps, heart rate, sleep,
  weight, total calories, floors.
- Whether a tile shows a comparison (vs. yesterday, vs. 7-day average). Useful, but it is a
  second aggregation per tile.
- Whether "advanced dashboard" becomes the premium feature. `Feature.CUSTOM_DASHBOARD` is
  reserved; free would keep a fixed starter dashboard, paid unlocks arbitrary tiles.

## 2. Settings screen — built

- **Theme** — Light / Dark / System, plus a toggle for wallpaper (dynamic) colours. Read at the
  top of the activity so a change repaints everything at once and the first frame is not a
  flash of the wrong palette.
- **Language** — a *link* into Android's own per-app language screen, not a control.
  `res/xml/locales_config.xml` declares the shipped locales, which is what makes the app appear
  there at all. A private override would drift from what the system's own settings show.
- **Choose data types** — this app's own permission picker. Listed first, because on a first
  run it is the only route to granting anything.
- **Manage access** and **App priority** — links into Health Connect. Priority is a link for
  the same reason as language: Health Connect owns that ordering and does not expose it to
  apps.
- **Withdraw all access** — calls `revokeAllPermissions()`, and says plainly that nothing is
  deleted. This app keeps no copy, so revoking only removes the ability to read; the phrase
  otherwise invites the fear that it erases the records too.
- Privacy policy, source link, version.

Every external intent has a fallback and cannot crash the app: each target belongs to another
app that may be absent, disabled, or renamed by an OEM.

All of this is non-health UI state in DataStore.

## 3. Source selection — built

Where several apps wrote a type, the full-screen view offers "All sources" plus one chip per
contributing app, filtering chart, total and record list through `dataOriginFilter`. Dashboard
tiles read the same per-type selection, so the two views agree.

**All sources stays the default.** Health Connect's deduplicated total is the correct answer
for the metric and is deliberately not the same as any single app's figure. The caption states
which question is being answered, because a changed number with no explanation is how this gets
misread.

**Every printed value names its source** — each record row, and the writers behind a chart and
total. With two apps describing the same activity, the source is what makes a legitimate
duplicate legible instead of looking like an error. Resolving those names needs `<queries>` to
match Health Connect clients; matching only the rationale intent left writers that do not
handle it showing as raw package names.

**There is no "primary source" setting, deliberately.** Health Connect keeps a user-ordered app
priority list that decides which record wins on overlap, and it is not readable or writable
through the Jetpack client. Inventing a ranking here — picking the app with the most records,
say — would disagree with the platform and produce totals matching nothing. The settings screen
points at where the real setting lives.

### Two traps this surfaced

- **A bucket-wide interval aggregates to nothing.** An app posting one 00:00-23:59 summary per
  day yields null buckets while `readRecords` returns the record, so filtering to that app alone
  produced an empty chart with a visible record beneath it. Where a *single* source is selected
  its own records may be summed directly, since one writer cannot overlap itself; never for the
  combined view.
- **The contributor list is read unfiltered.** Scoping it to the current selection collapses the
  picker to one app and strands the user with no way back.

## 4. Chart refinement — partly built

One hand-drawn Compose Canvas line, no charting dependency. Everything renders through a single
`LineChart` signature, so swapping the implementation stays a single-file change.

### Built

- **Points placed by timestamp**, not by list position.
- **Smoothing**, clamped so a segment cannot leave the range of the two values it joins.
  Discrete types (sleep, exercise, menstruation) stay angular.
- **Cumulative day charts** for additive types, stepping at each record's own interval and
  rescaled to finish on the deduplicated daily total.
- **A dashed goal line**, participating in the vertical scale, with a **badge at the crossing**
  and the interpolated time stated in words.
- **Y-axis gridline labels** at five levels, drawn on their own lines and backed so they stay
  readable where the grid or goal line crosses them.
- **Touch or drag to read** the nearest point's value and time. The readout occupies a fixed
  row whether or not anything is selected, so touching does not shift the layout out from under
  the finger.

### Still open

- X-axis tick labels between the endpoints.
- Empty-day gaps shown as gaps rather than interpolated straight through. Relevant to the
  "unexplained 0" problem in `FEATURE-IDEAS.md`: a line drawn through a day with no data claims
  a value that was never recorded.

Bar rendering for count-like types was on this list and is no longer needed: an intraday
cumulative chart steps at each record's own interval, so the line no longer implies continuity
between counted events.

Vico was the original choice and was dropped because Vico 3.x's Compose Multiplatform rewrite
changed the axis API surface. Revisit if the chart requirements grow beyond what is comfortable
to hand-draw.

### Chart invariants worth not breaking

Each of these was a real defect found on a device, not a hypothetical:

- **Points are placed by timestamp, never by list position.** Even spacing put a 12:45 event
  at seven eighths of the width because it was the seventh of eight points.
- **A cumulative series ends on the deduplicated daily total.** Sub-day buckets do not
  deduplicate, so a whole-day summary record from a second writer inflated a running total to
  24.6 against an authoritative 12.
- **A cumulative series never moves backwards in either axis.** Records commonly overlap, so a
  naive ramp per record sends the line back in time and draws a zigzag.
- **Smoothing is clamped inside each segment, horizontally and vertically.** Otherwise a curve
  overshoots below zero between two counts, or doubles back on itself where neighbours are far
  apart in time.
- **A goal takes part in the vertical scale.** Clipped off the top, "not reached" looks
  identical to "reached".
- **Point positions are computed once** and shared by the drawing and the touch handler.
  Computing them twice invites drift, and a highlight beside the line it names is worse than
  none.

## 5. Sessions: what the data supports — built, two refinements open

### The association is by time, not by identity

Health Connect stores **no link between a session and the readings taken during it**. There is
no session id on a heart rate sample. `ExerciseSessionRecord` carries only its type, title,
notes, segments, laps and route — no distance, power or calories. Those are separate record
types written over the same window.

So a session's statistics exist but must be *assembled* by overlapping time ranges. Measured on
a real device, one 53-minute indoor bike session ("Heimtrainer", Life Fitness) yielded:

| Metric | Value |
|--------|-------|
| ActiveCaloriesBurned | 647 kcal |
| TotalCaloriesBurned | 710 kcal |
| Distance | 25.5 km |
| HeartRate | 138 bpm mean, 54 records (~1500 samples) |
| RespiratoryRate | 51 records |
| Power, Speed | present as series |

Every surface that shows this must say the matching is by time. "These readings were taken
during this session" is true; "these readings belong to this session" is not.

### Writers disagree about what the activity was

The same workout arrives from several apps with different types: the 06:03 session above is
`EXERCISE_TYPE_BIKING` from a Garmin watch and `EXERCISE_TYPE_BIKING_STATIONARY` from the
machine's own app. The machine is right. Overlapping sessions are collapsed preferring the
writer that **set a title**, because only the specific apps name their sessions — Garmin writes
`title=null` throughout, while Life Fitness and Health Sync supply "Heimtrainer", "Berlin
Mountainbiken", "Stärke deinen Rücken".

### Sleep spans midnight

A night's sleep is credited to the morning it ends on but starts the previous evening —
measured, 22:48 to 05:15. A day-bounded read is therefore the wrong query, and sleep never
appeared. Sessions are searched over a window widened by half a day either side, then clipped
to the visible range.

### Built

- Session bands shaded behind intraday charts, declared per type via `TileSpec.overlaySessions`
  (sleep and exercise behind heart rate, exercise behind steps).
- Sleep bands are a **fixed blue**, not a theme colour: under dynamic colour a themed hue drifts
  with the wallpaper until it stops reading as night.
- An icon per activity, falling back to a generic sports mark rather than to nothing.
- Tapping a session opens its assembled statistics.

### Built: the Activities tile

`TileSpec.Form.SESSIONS` on the existing `ExerciseSessionRecord` and `SleepSessionRecord`
specs, rather than a tile concept outside the registry. The dashboard still never branches on
record type; it branches on form, as it already did for rings and curves.

- The tile's face is the **count** of the day's sessions, with their total duration beneath and
  an icon per activity as far as they fit. Zero is shown as "None" rather than as the
  missing-data dash: a day with no activities is a fact about the day, not a gap in what was
  recorded. That is the opposite of the reasoning for a measured type, where a null total does
  mean nothing was written.
- Its detail lists the sessions, each opening the statistics sheet, and each showing the
  **heart-rate curve for its own window** — read raw, which is safe here in a way it would not
  be for a total, because heart rate is instantaneous: two apps writing the same beat duplicate
  a point rather than inflating a sum.
- Sleep gets the same treatment as a separate tile, from the same form.

`isChartable` had been doing two jobs — gating what can be pinned and gating what can
contribute a number. `ExerciseSessionRecord` has no unit, because an activity is not measured
in anything, so pinning now asks `isPinnable` while `isChartable` keeps its narrower meaning
for the session statistics.

### Built: bands behind every movement chart

A band answers "why does the line do that", and the answer is the same for steps, floors,
distance, calories, elevation and wheelchair pushes. The set is named once as
`TileSpec.ACTIVITY_CONTEXT` rather than repeated per spec. Hydration stays without: a drink
during a ride is real, but a sleep band explains nothing about a hydration chart's shape.

### Built: the intraday line coloured by value

Heart rate was a coloured curve on its tile and a plain themed line on the screen that tile
opened — the same reading in two colours, which reads as two different measurements. The chart
now takes the colour scale from the type's own `TileSpec`, so it is the fixed clinical range
and never the window's extent.

Only within a day. Across days each point is a daily average rather than a reading, and
colouring one red would claim an alarming measurement where the data says an unremarkable mean.
A coloured line is stroked span by span, since a path takes one colour, which costs the
smoothing for those types — the right trade, as the colour says whether a reading was high and
rounded corners do not.

### Still open: two chart refinements

Requested, not yet built:

- **Icons on the time axis at each session's position**, so a band can be matched to the
  activity it represents. The list below the chart names the sessions but does not say which
  band is which, and with two or three bands that is guesswork. Place each icon at the band's
  midpoint on the axis, using the same fractions the plot uses.
- **The current day always spans the full 24 hours.** Today's chart currently ends at the last
  recorded point, so midday sits wherever the data happens to stop and the axis means something
  different at 09:00 than it will at 21:00. Fix the horizontal extent to midnight-to-midnight
  for the day span, while the *line* still ends at the last real point — the empty remainder is
  the honest picture of a day in progress, and it keeps 12:00 in the middle.

## 6. Aggregation: verified working, with one caveat

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

## 7. History reach: measured, and two bugs found

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

## 8. Considered and rejected: a React/Vite UI in a WebView

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

## 9. Testing note: adb input injection on Xiaomi/HyperOS

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

## 10. Deferred

- **MindfulnessSession** — excluded from v1: the library requests
  `READ_MINDFULNESS_SESSION` while the platform defines only `READ_MINDFULNESS`, so the
  permission can never be granted. Add once those names converge.
- **Imperial units** — everything is metric today, matching what Health Connect returns
  natively. The registry's `unitRes` field is the seam for adding a conversion.
- **Play Billing products** — the entitlement gate is wired but reports no premium access
  until products exist in the Play Console.
