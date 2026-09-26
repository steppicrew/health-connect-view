# Roadmap

What exists, why it works the way it does, and what is still planned. Sections marked **built**
describe shipped behaviour and the decisions behind it; the rest is intent recorded so it
survives beyond the session it was discussed in.

## Next steps

The working plan, in order. Each step is one branch, merged fast-forward when it builds, passes
and has been seen on a device; tick it here in the same merge. Ranked on 26.09.2026 from the
open items below and `FEATURE-IDEAS.md`.

1. [x] **Cycle overview** -- section 12. Merged 26.09.2026.
2. [x] **See the cycle overview on the phone.** Done 26.09.2026 with the in-memory fixture
   (`-e route "'cycle?fixture=true'"`), since the phone has no cycle data and seeding would
   put fake periods in the owner's real store. Dark theme with dynamic colour showed light
   flow as the *darkest* day; fixed. The real read path shows the no-permission and empty
   states correctly. Not seen on the phone: light theme (it needs a tap) and real cycle data.
3. [x] **Tile comparison** -- merged 26.09.2026; see "Built: a trend arrow on every tile" in
   section 1.
4. [x] **Sleep stages** -- merged 26.09.2026; see "Built: sleep stages" in section 5.
5. [x] **CSV export** -- merged 26.09.2026, see section 13. Release shows it locked until a
   Play product exists; the privacy text changed and wants the owner's review before release.
6. [ ] **Dashboard configuration export/import** as local JSON. No health data involved.
7. [ ] **Blood pressure morning/evening split.** Needs a stated rule for where the day splits.
8. [ ] **Tile resize** (2x1, 2x2). Last: new gesture and layout geometry, and resizing cannot
   be driven from the host on the Xiaomi.

Also done on 26.09.2026, outside the numbered steps: swipe between windows in the detail
views, a grant button on locked tiles, body measurements carrying their last reading, the
trend explained with its averages in the day view, and "Weiter" for the permission screen's
leave button.

Not in Health Connect, checked on the phone 26.09.2026 after a manual Health Sync: Garmin's
timeline for 25.09 showed an auto-detected walk (~11:30) and a nap (~14:30); neither exists as
a session, and the 26th's nap is missing too. Recorded activities and night sleep all arrive.
Nothing marks a session as auto-detected. Not something this app can recover.

Decisions waiting on the owner, not on code:

- Whether the cycle overview's year window counts as `LONG_RANGE_HISTORY` (premium). It
  needs `READ_HEALTH_DATA_HISTORY` to show more than 30 days, which is one to two cycles.
- Whether a custom dashboard becomes the premium feature (section 1), and the default tile set.
- A dashboard strip for the cycle -- the overview has no tile. Worth building only if the
  overview proves useful on the phone.

Not ranked yet: overlaying two metrics on one timeline, a local reminder notification, goal
streaks.

Sleep stages, step 4, measured on the phone 26.09.2026: every Garmin night carries
12-37 stage segments of 3-4 kinds in `SleepSessionRecord.stages`, so a light/deep/REM/awake
view needs no inference. Garmin's sleep *score* is not in Health Connect. Health Sync writes a
copy of each night, once with one segment fewer than Garmin's own -- pick one writer per night,
as sessions already do, never merge their stages.

Not planned: an insights tile. Deciding what is "notable" is easy to overclaim and reads as
medical advice.

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
requested no matter which range was chosen. Every user-facing window now uses `Span`; the
catalog's type detail was the last screen to move across (section 7), and its controls are
shared with this one rather than copied.

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

### Built: the grid flows, within bounds

`GridCells.Fixed(2)` with square tiles gave a landscape phone two enormous tiles -- each half
the *long* edge wide and, through `aspectRatio(1f)`, equally tall.

Neither built-in cell type fits, and the reason is worth keeping: `Fixed` cannot adapt, and
`Adaptive` has no bounds, but both bounds are real here. Any tile minimum wide enough to keep a
tablet sensible (150dp and up) drops a 320dp phone to a *single* column, which is worse than
the bug being fixed; and unbounded, a tablet in landscape reaches eight columns, at which point
the tiles are too small to read a number off -- the one thing a tile exists to do.

`BoundedTileCells` fits as many columns of at least 160dp as the width allows, clamped to 2..5.
Portrait is unchanged; a landscape phone gets three or four columns at 168-197dp instead of two
at ~350dp; a large tablet stops at five and the tiles grow instead, which keeps the column
count predictable for the stored tile spans below.

Its arithmetic is tested rather than eyeballed -- it is the kind that looks obviously right and
is off by a spacing. The test pins the bounds, that columns and gaps fill the width exactly,
that widths differ by at most a pixel, and that a wider screen never yields *fewer* columns.

### Still open

- Tile resize (2x1, 2x2). The config already stores spans; what is missing is the layout
  geometry and a second gesture.
- Which stats form the default tile set on first run. Currently steps, heart rate, sleep,
  weight, total calories, floors.
- Whether "advanced dashboard" becomes the premium feature. `Feature.CUSTOM_DASHBOARD` is
  reserved; free would keep a fixed starter dashboard, paid unlocks arbitrary tiles.

### Built: a trend arrow on every tile

Each tile with an aggregate metric shows up, flat or down: the 7 complete days before the shown
day against the 30-day average (`health/Trend.kt`). The shown day is excluded because today is
always partial -- a step count at ten in the morning would point down every day.

- **Flat is scale-free**: a difference under half the 30 days' standard deviation. One rule
  fits steps and weight: a kilo on a steady weight shows, normal step noise does not.
- **Unrecorded days are gaps, not zeros**, and too few recorded days (under 4 of 7 or 15 of 30)
  give no arrow rather than a confident one. Weight, weighed a few times a month, has none.
- **Neutral colour.** Up is good for steps and bad for resting heart rate.
- **Fetched after the values.** Read alongside them it took eight tiles from ~650 to ~1200 ms
  on the phone, because tiles publish together once the slowest finishes. The arrows now
  follow in a second pass (~800 ms), and the numbers do not wait.

Measuring this found a bug that predated it: `init` and the first `ON_RESUME` both started a
full load, so every cold start read every tile twice. One load now cancels a superseded one,
and the values arrive in ~480 ms. The debug log line `Dashboard: loaded N tiles in X ms`
carries timing only, for measuring from adb.

### Built: body measurements carry their last reading

Weight, body fat, body water, bone mass, lean mass and height show the latest earlier reading,
muted and dated ("Stand 23.09.26"), when the shown day has none (`TileSpec.carryLastReading`).
They describe a state rather than a day: Monday's weight still answers Wednesday's question,
Monday's steps do not. A reading on the day itself always wins. Looks back a year, which past 30
days needs the history permission.

A locked tile carries a button to the permission list; tapping the tile itself only reached a
detail screen repeating "not allowed".

## 2. Settings screen — built

- **Theme** — Light / Dark / System, plus a toggle for wallpaper (dynamic) colours. Read at the
  top of the activity so a change repaints everything at once and the first frame is not a
  flash of the wrong palette.

  **The system bars take their icon tint from this setting, not from the system's.** At
  `targetSdk` 35 and up the platform draws edge-to-edge regardless and ignores the legacy
  `statusBarColor`/`navigationBarColor` attributes, so the bars are transparent over whatever
  the app paints and only the tint is left to set. Nothing set it: neither `themes.xml` file
  carries a bar attribute and no code touched a `WindowInsetsController`, so back/home/recents
  were drawn nearly white on white in the light palette. The two notions of "dark" are what
  made it worst -- `values-night` follows the *system*, the palette follows the *app*, so
  choosing Light under a dark system was white glyphs on a white surface by construction.
  `isAppearanceLight*Bars` now follows the same `darkTheme` the palette does, from inside the
  theme composable so a change repaints the bars without an activity restart. Every screen is
  a `Scaffold` and consumes the padding it is handed, so edge-to-edge puts nothing under a bar.
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

## 4. Chart refinement — built

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

### Built since

- X-axis tick labels between the endpoints (`df6d30b`). Nothing in this section is open.

### Built: the y-axis lands on round numbers

The scale ran from the data's own minimum to its own maximum and cut that into four, so a heart
rate between 45 and 113 was labelled 45, 62, 79, 96, 113 -- five arbitrary values, none of which
helps place a sixth. `Formatting.number` then chose decimals by magnitude alone, so a step of
17 bpm arrived as "78,5" on a quantity nobody measures in halves.

`AxisScale` picks a step from 1, 2, 2.5 or 5 times a power of ten and pushes the ends outward
onto multiples of it. That is what makes the *intermediate* labels round too: a round bottom and
a round step cannot produce a ragged one in between. The reported case reads 40, 60, 80, 100,
120.

- **Decimals come off the step, not off each value.** On a scale stepping by 0.5 the old rule
  printed whole values bare and halves with a decimal, so neighbouring labels read as different
  kinds of number. `Formatting.axisLabel` takes the count explicitly.
- **`integralValues` marks the quantities counted in whole units** -- steps, floors, wheelchair
  pushes, heart rate, resting and respiratory rate. Off by default, because most types here are
  continuous: whole steps on a body weight moving inside one kilogram would leave a single
  gridline and no shape. Cadences are averaged rates and stay fractional.
- **The scale still contains the data, the goal and the range band**, so nothing that took part
  in the old range is clipped out of the new one -- and bars still get zero on the scale, which
  their baseline is drawn at.
- The interval count is a *target*, not a guarantee: rounding the ends can land on three or five
  gridlines, and a round axis is worth the variance.

2.5 earns its place in the step list: on a span of 9 the alternatives jump from nine gridlines
to five to two, and nothing else sits between. It is the reason a week of sleep reads 0,0 2,5
5,0 7,5 10,0 rather than being squeezed onto whole hours.

### Built: gaps break a count, not a measurement

Empty-day gaps are shown as gaps rather than interpolated straight through -- but only where
the gap means something. Relevant to the "unexplained 0" problem in `FEATURE-IDEAS.md`: a line
drawn through a day with no data claims a value that was never recorded.

The split is by `markReadings`, the flag that already names the types a reading is *taken* of.
A day with no steps recorded is not a day of zero steps, so the line breaks. A day without a
weigh-in carries no information at all, so the line connects: weighing in on Monday and the
Monday after is a fortnight's trend, not two isolated facts. Breaking there put every point in
a segment of its own, and a one-point segment draws as a bare dot with no line -- a weekly
weigh-in produced a chart with no line anywhere.

The caption explaining a break is emitted from the same source, so it cannot appear on a chart
that has none. `RecordRegistryTest` pins `markReadings` and `cumulativeIntraday` as disjoint,
since the bar and gap rules read them as opposites.

### Built: a multi-day span has its own marks

Week, month and year charts reused the day's single line over daily buckets, which answers
"how did this move today" -- the wrong question once a point is a whole day. Each type now
gets the mark its data supports, declared in the registry rather than branched on by the UI.

- **Heart rate keeps the mean line with the day's range behind it**, via `rangeAggregates`
  (BPM_MIN/BPM_MAX). Measured over four weeks the mean sat between 70 and 100 while the days
  actually ran 44 to 201: the band is most of what the mean was hiding. Both ends come from
  the same bucketed request as the mean -- one call, not three -- so a band cannot drift from
  its point, and both take part in the vertical scale for the same reason a goal does.
- **Exercise draws sessions per day**, not each session's heart rate. Measured: 48 sessions
  over four weeks, one to five a day.
- **Sleep draws hours per night**, attributed by the rule in section 5 -- a night belongs to
  the day it ended on. This is why sleep attribution had to be settled first.
- **Calories stack basal under active**, via `stackComponents`. The basal floor is ~1,700 kcal
  every day, so unstacked a hard day and a lazy one differ by a fraction of a mostly-basal
  bar. Note `BasalMetabolicRate` stores no records at all, only aggregates, so this can only
  come from the aggregate.

Rules the bars follow, each for a reason that showed up on screen:

- **Bars start at zero, lines do not.** Bars are read by comparing heights, and on a floating
  baseline a 7-hour night beside a 9-hour one looked like a third of the sleep rather than a
  fifth less. A line has no such claim to make and keeps its tight scale, which is what lets a
  small movement in a resting heart rate stay visible.
- **Bar width comes from the gap to the nearest neighbour**, so a missing day leaves a space
  instead of widening its neighbours -- the same reason points are placed by timestamp.
- **Bars are drawn before the guide labels**, or the axis is unreadable over them.
- **Stack segments are one hue at increasing weight**, not separate colours: they are parts of
  one quantity, and separate colours read as unrelated series sharing a bar. A legend names
  them, since a weight alone is not nameable.
- **The caption says which operation produced the bar.** "Daily totals" is wrong for a mean
  with a spread, and wrong again for a count of sessions; each has its own string.

Bar rendering was dropped from this list once the intraday cumulative chart stepped at each
record's own interval -- within a day the line no longer implies continuity between counted
events. The multi-day marks put it back for a different reason: not to fix the day view, but
because a bucket that is a whole day is a count, not a moment.

That reason applies past sessions and stacks, and at first only those got it: `bars` was
derived from them alone, so steps and distance drew a line over week, four-week and year
buckets that were already daily and weekly totals. The buckets were right and only the mark
was wrong. It now follows `cumulativeIntraday`, the registry's existing flag for quantities
that add up, whose own KDoc already observed that across days each bucket is a daily total.
Means stay lines: a resting heart rate averaged over a day is still a reading, and a bar from
zero would bury the small movements that are the reason to watch it.

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

## 5. Sessions: what the data supports — built

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
appeared. Sessions are searched over a window widened by half a day either side, then selected
by the visible range -- kept or dropped whole, never trimmed to it. See the bar-start defect
below for why the distinction matters.

**Built: which day a night belongs to, and where its bar starts.** Widening the window
made sleep appear; it did not settle attribution. Two defects reported from the test build:

- A night should be shown on the day it **ends**, and only then. Widening by half a day either
  side catches both the night that ended this morning and the one starting this evening, so a
  day can show two nights and the following day shows the same one again.
- The bar is drawn from **00:00 rather than from the real start time**. Clipping to the visible
  range is right for keeping the bar inside the axis, but the clip is being used as the start
  value, so every night reads as beginning at midnight. The true start is on the record; the
  chart should keep it and let the bar run off the left edge instead.

Both are fixed. Sessions are kept by overlap for exercise and by *end* for sleep, and the
headline of a sessions tile now comes from the list it is showing rather than from a
calendar-day aggregate -- on 11.09 those disagreed openly, 2h 28m above a list summing to
16h 13m. Measured after: 11.09 reads 4h 48m, one session, and the 21:30-08:56 night moved to
the 12th keeping its real start time.

### Built

- Session bands shaded behind intraday charts, declared per type via `TileSpec.overlaySessions`
  (sleep and exercise behind heart rate, exercise behind steps).
- Sleep bands are a **fixed blue**, not a theme colour: under dynamic colour a themed hue drifts
  with the wallpaper until it stops reading as night.
- An icon per activity, falling back to a generic sports mark rather than to nothing.
- Tapping a session opens its assembled statistics.

### Built: sleep stages

Each night in the sleep view carries a hypnogram -- awake, REM, light and deep in lanes across
the night's own span -- with the time in each stage as its legend (`health/SleepStages.kt`,
`ui/components/Hypnogram.kt`). On the phone the 26th read Wach 49m, REM 1h 13m, Leicht 6h 42m,
Tief 1h 10m, which sums to the 9h 54m headline.

- **Three waking codes are one lane**; unclassified sleep stays apart from light sleep, and
  unknown is a gap rather than a stage.
- **The fuller copy of a night wins.** Garmin and Health Sync write identical untitled nights;
  among those, the one with more stages is kept, since a re-sync only loses detail.
- **No score.** Garmin's sleep score is not in Health Connect, and inventing one would be this
  app's own claim about how well someone slept.
- **Fixed colours**, so dynamic colour cannot turn deep sleep and REM into one wallpaper shade.

Found on the way: a night starting 23:29 drew its heart rate from 00:00. The session curve took
the writer with the most samples, and Health Sync's copy has more -- but none before midnight,
while Garmin's own ran every two minutes from 23:30. The writer covering most of the session in
5-minute slots is drawn now; the curve's first sample was checked at 23:30 on the phone.

### Built: session curves load with their row

The sessions view read every session's heart-rate curve before showing anything. A year of
Trainings on the phone is 728 sessions at about 66 ms each -- near 48 s. Each row now reads its
own curve when it comes on screen (four at a time, memory only), so the list, bars and totals
appear at once: measured 0.7-0.95 s for the year, and the week from 1.2 s to 0.6 s. A row shows
nothing until its curve arrives, so it never says "no heart rate recorded" for one still loading.

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

### Built: a night is shown from when it began

The day's chart drew a sleep band from 00:00 for a night that started at 22:18, while the
headline above it read the true 10h 40m -- 8h 58m of shading against a 10h 40m number, one
screen giving two answers to the same question. That is the defect the headline itself was
fixed for earlier in this section.

The session was never wrong; it keeps its real start. The loss was in the plot, which pins the
day midnight-to-midnight and clamps anything outside onto the edge, so the pre-midnight stretch
collapsed onto x = 0.

A day's extent now widens **backwards** to contain a session that began before it, and only
then -- a day whose sessions sit inside it keeps the fixed axis below. Never forwards: a
session past the end belongs to the next day (sleep is selected by its end, and an exercise
crossing midnight is shown on the day it began), so widening forward would pull tomorrow's
evening onto today's axis.

`TimeAxis` needed the matching change and is the trap worth remembering: it labelled clock
times only up to *exactly* 24 hours, so a widened night at 25.7 hours fell through to the
multi-day date format and drew one repeated date, which on the phone read as the axis losing
its labels altogether. The threshold is now a day plus the evening a night can reach back into.

Measured on the phone: the band covers 0.415 of the track, against 0.415 predicted for the
widened extent and 0.374 for the old one, and every axis tick lands within 0.003 of its
computed position.

### Built: the day pinned to 24 hours, and icons on the axis

A day's chart ended at the last recorded point, so midday sat wherever the data happened to
stop — the axis meant something different at 09:00 than it would at 21:00, and the hour you
were looking for slid across the screen as the day filled in.

The plot now takes an explicit **extent**, fixed midnight-to-midnight for the day span, and
every horizontal position derives from it: fractions, bands, the goal marker, the axis ticks.
Nothing can drift away from the line it describes. The *line* still stops at its last real
point — the empty remainder is the honest picture of a day in progress, and drawing to the edge
would invent readings that have not been taken. Points outside a fixed extent are clamped,
since a sleep session running past midnight is the normal case rather than the exception.

**An icon per session sits on the axis** at the band's midpoint, where the band is widest,
placed by the same extent the plot uses. The list below the chart named the sessions but not
their positions, and with two or three bands that was guesswork.

`horizontalFractions` is `internal` rather than private so `ChartExtentTest` can pin the extent
behaviour: it decides where every band, marker and label lands, and the phone is a slow place
to discover it is wrong.

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

### Built: the range ceiling is gone

`TimeRange.YEAR` was 365 days with no offset, so nothing older was reachable at all. The
measured data showed the wall directly -- BodyFat, BodyWaterMass, BoneMass and Height all
reported their oldest record as **exactly** `daysBack=365`, which is the request boundary
rather than the end of the data. Types with genuinely older data reach 472 days
(FloorsClimbed, RestingHeartRate, May 2025).

`Span` removed it for the dashboard's full-screen view first (section 1): a calendar-anchored
window plus an offset, which is what makes "the week before last" -- and so April 2025 --
expressible at all. The catalog's type detail kept `TimeRange` for a while longer and kept the
ceiling with it; it now uses `Span` too, so every screen that lets the user pick a window can
step past a year.

Two things fell out of that move:

- **The span decides the bucket, not the screen.** Type detail aggregated through
  `dailyTotals()`, which fixes the slicer at a day. A year window that way is 365 points on a
  phone-width chart, so it goes through `bucketedTotals()` with `Span.bucket` -- a week for the
  year span. `Span.DAY` is not offered there at all: it has no Period-expressible bucket, and a
  day sliced by a day-wide bucket is one point rather than a chart.
- **The window controls are shared, not copied.** `SpanSelector`, `WindowStepper` and
  `windowLabel` moved to `ui/components`. They were private to the tile screen while it was the
  only one with an offset; a second copy would have been a second place for "which window is
  this" to drift, which is precisely the arithmetic this section exists to protect.

`TimeRange` survives as a fixed internal window for the catalog probe and the settings summary,
where nothing is user-selectable. It lost `labelRes` -- nothing renders it as a chip any more --
and the four `range_*` strings went with it.

**Confirmed on the phone, 20.09.2026.** The tile view reaches 15.06.2025 -- 462 days back --
and `HistoryReachActivity` reports chart reads running to 494 days with the history permission
granted, so the old 365-day wall is genuinely gone rather than merely un-asserted. The type
detail screen shows three span chips with the day absent, and charts seven daily buckets for a
week. The year span's weekly bucketing was at first unverified on hardware, because the debug
backdoor seemed unable to switch spans (see section 11). It can: on 26.09.2026 the emulator
opened `tile/StepsRecord?span=YEAR` on the year span with weekly bars, so a phone check needs
no tap either.

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
`am start` and file push all work regardless. **`adb install -r` does work** -- the block is
specifically on *input injection*, not on installing -- so the install/screenshot loop can be
driven entirely from the host; only gestures need a person.

So on such a device, drive the UI by hand and read the result from screenshots and logs. The
debug-only `AggregationCheckActivity` exists for exactly this: it is startable with `am start`
and reports raw-versus-aggregated counts per type without needing a single tap.

## 10. Known issues, not yet fixed

Everything reported from the internal-testing build has been fixed and, except where noted,
confirmed on the phone against real two-writer data.

A second round reported on 19.09.2026 is fixed in code but not yet seen on the device, below.
Everything from that round is now fixed and confirmed on the phone.

### Fixed and confirmed on the device

Measured on the phone, 13.09.2026, against Garmin Connect and Health Sync.

- **The source picker listed only writers that reached the aggregate.** Confirmed by building
  the commit before the fix and opening the same day: it read "Geschrieben von Garmin Connect"
  with no chips, while Health Sync had written that day's only floors record. After the fix
  the same screen offers "Alle Quellen" plus both writers. Steps, with three writers, offers
  four chips.
- **The Floors day chart drew a straight line.** Two causes, not one. The picker fix above was
  the first. The second was the choice of *shape* writer: it was picked by summed contribution
  alone, and on 11.09 Garmin's two climbs (05:30, 07:15) and Health Sync's single 00:00-24:00
  summary both totalled 8 floors, so the tie handed the shape to the summary, which can only
  draw a ramp. Writers with itemised records now outrank whole-day summaries. The same day
  draws its real staircase, the total is unchanged, and two captions corrected themselves: the
  chart dropped "die Punkte dazwischen sind aufgeteilt" because the shape is measured again,
  and on 10.09 the goal-crossing badge returned ("Ziel von 10 um 14:00 erreicht") because a
  crossing time can now be read off real steps.

  Note the earlier plan here -- "select a single source and sum its own records" -- turned out
  to be unnecessary. The combined view is correct once the shape comes from a writer that has
  timing; no source selection is required.

### Fixed and confirmed on the device

Reported 19.09.2026, verified on the phone the same day against real Garmin data.

- **The system bars were nearly invisible.** See §2 — the tint followed the system's night
  mode while the palette followed the app's setting. Confirmed on the Xiaomi with 3-button
  nav, the reported case: dark glyphs on the light palette, white on the dark one. Still
  unverified is the crossed case, in-app Light under a dark system, which needs a tap in
  Settings: the choice lives in DataStore and cannot be set from the host.
- **The source marker sat bottom-left on Sleep and Activities.** One footer row, not two code
  paths: `Arrangement.SpaceBetween` with a conditional first child. The unit label is
  suppressed for every `SESSIONS` tile — for sleep deliberately, since "h" labels neither a
  count nor a duration — which left the marker as the only child, and `SpaceBetween` puts a
  lone child at the start. A weighted spacer makes the gap unconditional. Confirmed on the
  Sleep tile in both palettes.
- **Steps and distance drew lines over multi-day buckets.** See §4. Confirmed: a steps week
  draws seven daily bars, a steps year fifty-two weekly ones, distance the same.
- **Weight drew dots with no line.** See §4. Confirmed over a year of real weigh-ins: one
  connected line, dots on the measurements.
- **The y-axis was labelled with the data's own extremes.** See §4. Confirmed on the reported
  case — a day of heart rate now reads 40, 60, 80, 100, 120.
- **A night was drawn from midnight rather than from when it began.** See §5.
- **The tile grid gave two enormous tiles in landscape.** See §1. Confirmed with the phone
  turned by hand: four columns at a size close to a portrait tile, eight tiles visible at
  once, portrait unchanged. The host cannot rotate the screen -- HyperOS refuses both
  `settings put system user_rotation` and `wm size` -- so this one needed a person.

  It also confirmed the insets: with the app drawing edge-to-edge (§2) the grid clears the
  status bar and the right-hand navigation strip rather than sliding under them, which was
  the risk in opting in.

### Found while verifying on the device

- **The first and last bar were clipped in half.** A bar is centred on its timestamp and the
  end buckets sit at fraction 0 and 1, so half of each fell outside the canvas. Pre-existing,
  but invisible until counted quantities started drawing as bars.

  Clamping the bar back inside fixed the clipping and broke the spacing: it moves one bar and
  not its neighbour, so that gap closed to nothing -- -8px against 50px elsewhere -- and the
  end pair read as one thick bar. The whole run is now laid out in a plot **inset by half a
  bar** at each end, which keeps every gap identical and the outer edges flush. Two reports
  for one defect; the second only existed because of the first fix.

### Built: the axis labels are haloed, and every mark is named

Two more from the same device round.

**Labels sat on a filled block**, hiding whatever the series did behind them -- a bar's left
edge, or the stretch of curve being followed. They are haloed now, which covers almost nothing.

The halo is **eight offset copies drawn under the fill, not a stroke on the glyph**. A stroke
is centred on the outline, so half its width eats *inward*: at 2dp on the test phone that is a
2.8px bite into a ~3px stem, and the first attempt came back from the device as "the numbers
simply look bold", with the counters of 6, 4 and 0 filled in. An offset copy only ever adds
pixels outside the glyph. Eight directions rather than four, or a diagonal stroke frays exactly
where it crosses a gridline.

**Nothing named the shaded bands.** The caption under a chart says which *operation* produced
the numbers; it never said what the *shapes* were, and the pale blue sleep ribbon was reported
as simply unexplained. A line has an axis and a caption to fall back on, a bar has both plus
its own label, but a rectangle behind them has nothing.

`ChartLegend` names whatever is actually drawn -- series, stack segments, range band, sleep and
exercise bands, goal, reading dots -- and only that: a legend naming a mark the chart does not
have sends the reader looking for something absent. It replaces `StackLegend` so a stacked
chart has one legend rather than two.

Two cases needed their own handling, both found on the phone rather than in the code:

- **A session type's day has no series at all**, only a `SessionTimeline`, so the legend never
  rendered on the very screen the bands were reported from.
- **A band names itself even when it is the only entry.** A lone entry is otherwise suppressed
  as clutter, since it merely repeats the caption -- but the sleep timeline *is* one band.

The swatch is deliberately stronger than the band it names. At the chart's own 0.16 alpha a
10dp square is (227,231,254) against a (253,251,255) surface: a swatch has to read as a colour
first and match exactly second.

### Fixed and confirmed by the reporter

- **Back in tile edit mode** left the dashboard instead of leaving the mode, and the day arrows
  kept stepping underneath the edit controls. Edit mode was a boolean with nothing bound to
  Back; it now has a `BackHandler` and the arrows are disabled while it is open. Confirmed by
  the reporter on 13.09.2026, which needed a person: the Xiaomi blocks `input keyevent` as
  well as `input tap`, so Back cannot be sent from the host at all.
- **The dashboard blanked on return.** Confirmed fixed by the reporter on 13.09.2026 --
  "returning to the tiles' page is smooth now". The tiles were replaced with empty
  placeholders before the reads began; the previous values are now carried into them. The
  roadmap's earlier guess -- a cache key invalidating itself -- was wrong: `loadedAt` is
  compared as a TTL, not for equality.
- **Back from a tile detail walking back through the days** is not reproducible from the code:
  the offset lives in `TileDetailViewModel` and the arrows mutate it without navigating, so one
  back-stack entry exists per tile. Most likely it was really the edit-mode bug above.
- **Seeded sleep looked too hectic.** The asleep branch set `spread` to 2 but kept the shared
  ceiling of `resting + 3 * spread`, so a +-2 step crossed an 8 bpm band and swung a mean of
  4.4 bpm inside a single 50-second record. Asleep now steps by 1 within `resting +- spread`:
  the overnight range halves to 4 bpm and the per-record swing drops to 2.4, while the waking
  series is untouched at 28. Synthetic data only.

### Built: a preferred app

Choosing one app per tile meant repeating the choice for every type. A preference in
Settings supplies the default; the per-type chips still override it.

It is a **view filter, not a priority**. Health Connect's own app-priority list decides which
record wins where two overlap, and is not writable through the Jetpack client -- so this
selects whose data is displayed, and a filtered tile shows that app's figure rather than the
deduplicated total. The row sits beside the existing App-Priorität link, which explains the
difference and deep-links to the system screen.

- A type the preferred app never wrote falls back to all sources. Filtering it to that app
  would empty the tile, which reads as missing data rather than as a filter matching nothing.
- A per-type choice is *not* filtered that way: it was made deliberately for that type, so it
  stands even where it comes up empty.
- The choices are the apps found writing any granted type in the last month. There is no way
  to ask the platform "who writes health data", and listing installed packages would be both
  useless and a needless breadth of query.
- The cache key includes the preference, and a carried-forward tile value is dropped when its
  source changes, so switching apps cannot leave one app's number under another's name.

### What the device run also established

- `FloorsClimbedRecord` on 12.09 genuinely has no records from either writer, so the app's
  "0 Datensätze" there is correct rather than a read failure. Empty days are real.
- The debug nav backdoor only re-reads the `date` extra on a cold start. A second `am start`
  at a running instance switches the type but keeps the old date, which reads as the date
  extra being ignored. Force-stop first when stepping through days from the host.

## 11. Released

**0.4.0 (versionCode 4) went to production on 19.09.2026** -- the app's first public release.
Before it only internal testing (0.2.0) and alpha had ever been used.

Two things about that flow are worth not rediscovering:

- **The production track needs country availability set in the Play Console by hand.** Until it
  exists, committing an edit that puts a release on production fails with
  `403 PERMISSION_DENIED: Release in track targeting no countries`. No script can set it, so a
  first public release always pauses here for a person.
- **The gradle publisher reported tracks it did not use.** `release.sh --track production`
  uploaded to internal and said production; `promoteReleaseArtifact -Pplay.promoteTrack` said
  BUILD SUCCESSFUL and promoted nothing. Both are fixed, and `scripts/release-status.sh` now
  reads the live per-track state from the Play API -- which is the only thing in this flow that
  has been reliably truthful. Verify there, never from a build log.

A versionCode is spent once uploaded anywhere: code 4 could not be re-uploaded to reach
production, only promoted. Chasing a failed upload with a version bump would have burned a
number for nothing.

**0.4.1 (versionCode 5) followed the same day**, and with the `--track` binding fixed,
`release.sh --track production` published it directly -- the first time that command did what
it said. It carries no user-visible change: the Play Console flagged `androidx.fragment` 1.1.0,
which Play Billing reaches through `play-services-base`, and a constraint raises it to 1.8.9.

The finding is worth understanding before acting on the next one like it. R8 strips the library
entirely -- a Compose app instantiates no fragments, and the release dex holds zero
`androidx/fragment` references, the only "fragment" strings in it being `android.app.Fragment`
from the framework. What the Console reads is `BUNDLE-METADATA/.../dependencies.pb`, which
records the *resolved* version whether or not any of it survives shrinking. So an outdated-SDK
warning here is a claim about what the bundle declares, not about what users receive, and its
security framing does not apply to code that is not there.

A constraint rather than an `exclude`: excluding is closer to the truth, but `play-services-base`
declares the dependency deliberately, and removing an API Billing might call at runtime trades a
warning for a crash on a payment path this app cannot yet exercise.

**0.4.2 (versionCode 6) went to internal testing on 20.09.2026**, carrying the history-reach
fix. Internal was two versions behind at the time -- it still held 0.4.0 while production ran
0.4.1, because 0.4.1 was published straight to production -- so this is also the track catching
up. Production stays on 0.4.1.

versionCode 6 rather than 5: a code is spent once uploaded *anywhere*, and 5 had already gone
to production. Even a different track cannot reuse it.

Verified on the phone before publishing, which the previous session could not do for lack of a
connected device: the type detail screen opens on three span chips (day correctly absent),
charts seven daily buckets through `bucketedTotals()`, and the tile view reaches 15.06.2025 --
462 days back, well past the old 365-day wall -- rendering the empty window honestly rather
than falling back to today.

One gap reported while checking was wrong: that the debug backdoor's `span` extra is never
applied. `Routes.tileDetail()` does build only `?date=`, but the backdoor passes a raw route
string and the graph declares `span` since `dc50488`. Checked on the emulator 26.09.2026:
`-e route "'tile/StepsRecord?span=YEAR'"` opens on the year span. Keep the inner quotes: the route
passes through the device shell, where `?` and `&` are special. Why the original check failed
was not established.

### Two edge-to-edge warnings that need no change

Reported by the Console against 0.4.x. Both are answered, and the answer is to do nothing --
recorded here so the next reader does not re-derive it, or worse, "fix" it.

**"Edge-to-edge may not work for all users."** This asks for `enableEdgeToEdge()`, which the
app has called since 0.4.0 -- it is the fix for the white-on-white system bars in section 2, and
0.3.0 genuinely lacked it. The substantive half of the warning, that an app be inset-compatible,
holds: all seven screens are `Scaffold`s that consume the padding they are handed, verified on
the phone in both orientations. Expect the warning to clear on a rescan.

**"Your app uses deprecated edge-to-edge APIs."** It names `setStatusBarColor`,
`setNavigationBarColor` and `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` at three obfuscated
sites. Resolved through `app/build/outputs/mapping/release/mapping.txt`:

| Reported | Actually |
|---|---|
| `r40.b` | `androidx.activity.EdgeToEdgeApi29.setUp()` |
| `t40.b` | `androidx.activity.EdgeToEdgeApi35.setUp()` |
| `g1.k`  | `AccessibilityNodeInfoCompat$$ExternalSyntheticApiModelOutline0.m(WindowManager.LayoutParams)` |

The first two are `EdgeToEdge.kt` -- the *implementation of `enableEdgeToEdge()` itself*. So one
warning asks for the call and the other flags that call's own backward-compatibility path, which
is how AndroidX supports Android 14 and below and is inert on 15+. This app calls none of those
APIs; the only mentions of the colour setters in the tree are a comment in `Theme.kt` explaining
why they are not used.

Nothing to do, and two things not to do: removing `enableEdgeToEdge()` would bring back the
white-on-white bars, trading a real accessibility defect for a cosmetic warning, and there is no
newer `androidx.activity` to move to -- 1.13.0 is current. It clears when AndroidX drops the
legacy path.

**The general rule both of these teach:** a Console warning names what the *bundle* contains or
declares, which is not the same as what this app *does*. Deobfuscate the reported sites against
the release mapping before believing a finding is yours -- twice now the answer has been that it
was not.

## 12. Cycle overview — built

The six cycle types were bare lists showing the library's integers ("flow 2", "result 1"),
and protection "unknown" rendered as "unprotected" -- stating something never recorded. Values
are now words via `RecordTypeSpec.summaryRes`, resolved at render time because a summary lambda
has no Context.

The overview (`ui/cycle`, logic in `health/Cycles.kt`) draws one row per cycle, aligned on day
1, for a year at a time. A calendar was rejected: dedicated trackers already do that better, and
it shows one month where the value of a viewer is the history across writers. Aligned rows put
the same cycle day of every month in one column, so drift in length, period and the basal
temperature shift show as shapes.

Decisions, each with its reason in the code:

- **Merged per local day, never counted.** The cycle types have no aggregate, so there is no
  platform deduplication. A day bled if any writer says so; the heaviest flow wins; the
  strongest ovulation result wins; basal temperature is the day's *earliest* reading, since a
  later one is by definition not basal.
- **Bleeding from period records or flow entries.** Some apps write one, some the other.
- **A cycle starts after 3 dry days** (`MIN_GAP_DAYS`), so one unlogged day inside a period
  does not split it. Spotting never starts a cycle. The screen states the rule.
- **Selected by start, kept whole.** Read 60 days either side of the year; a cycle is shown if
  it starts in the window and is never trimmed -- the sleep rule again.
- **Median and range, not mean**, so one missed period does not skew every number.
- **Nothing predicted.** No fertile window, no next period: a read-only viewer should not make
  a contraceptive claim, and it keeps the app out of medical-app policy.
- **Sexual activity is not drawn.** It is the most sensitive type and adds nothing to the
  alignment; its own list remains.
- **Basal body temperature moved from Vitals to Cycle** in the catalog.
- **Missing layers are named**, so an absent ovulation mark is not read as a negative test.

Found while verifying on the emulator: the first load ran while the screen was asleep and was
refused (the foreground trap in section 7). The screen loads in `OnResume` rather than once, so
it recovers on return and re-reads permissions as the architecture requires.

Seen on the phone on 26.09.2026 through `CycleFixture`, a debug-only in-memory copy of the
seeder's cycles: writing fixtures into the owner's store would let any cycle tracker read fake
periods as real. It showed the flow shading inverted in dark theme -- translucent rose reads
lighter on a dark background -- so flow is now blended from the empty cell towards the rose.

## 13. Export — built

An export action in the tile's detail view writes what is on screen -- type, window, source
filter -- into a file chosen in the system save dialog (`export/`).

- **Two files, both offered.** *Records*: one row per reading (start, end, time, value, unit,
  text, writing app), duplicates across writers kept and labelled -- it is what each app stored.
  *Daily totals*: Health Connect's deduplicated value per day, an empty field for a day with
  none, never zero.
- **Sessions get records only.** The platform's daily sleep total cuts a night at midnight,
  while the app credits it to the morning it ended; a file would give a second answer.
- **Nothing kept.** Pages are written straight into the stream (`HealthRepository.forEachPage`),
  so a year of heart rate never sits in memory whole; a failed export deletes its file. This is
  the one exception to "no health data on disk", written into CLAUDE.md and the privacy page.
- **Plain CSV.** RFC 4180, dot decimals without grouping, ISO 8601 times with offset, UTF-8 with
  a BOM so Excel reads umlauts. German Excel expects `;` -- import rather than double-click.
- **Premium.** `Feature.EXPORT_CSV`, now read through `AppEntitlements`: debug unlocks
  everything, release asks Play Billing, which owns nothing until a product exists -- so in
  release the entries show locked with "Premium" for everyone for now.

Verified on the emulator: a week of steps exported 840 record rows and 7 daily rows, today's
matching the tile. Not yet: the type detail screen (catalog path) has no export action, sleep
stages are not in the records file, and JSON or a PDF report would plug in beside `Csv`.

## 14. Deferred

- **MindfulnessSession** — excluded from v1: the library requests
  `READ_MINDFULNESS_SESSION` while the platform defines only `READ_MINDFULNESS`, so the
  permission can never be granted. Add once those names converge.
- **Imperial units** — everything is metric today, matching what Health Connect returns
  natively. The registry's `unitRes` field is the seam for adding a conversion.
- **Play Billing products** — the entitlement gate is wired but reports no premium access
  until products exist in the Play Console.
