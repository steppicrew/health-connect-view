# Roadmap

Planned work, not yet built. Recorded here so the intent survives beyond the session it was
discussed in.

## 1. Dashboard start screen (next major feature)

Replace the catalog as the launch destination with a **configurable dashboard of tiles**
showing the selected stats for a single day.

### Behaviour

- **One day at a time.** Opens on today; `←` / `→` step to the previous/next day. Never step
  past today into empty future dates.
- **Tiles.** One per stat the user has chosen to pin — steps, sleep duration, resting heart
  rate, weight, and so on. Each shows the day's headline value, its unit, and ideally a small
  sparkline or delta against the previous day.
- **Configurable.** The user picks which tiles appear and in what order. Anything granted is
  eligible; nothing is pinned by default beyond a sensible starter set.
- **Tap a tile** to open the existing type-detail screen (chart plus raw record list) for
  that type, pre-scoped to the selected day.
- The catalog stays reachable — it is still the complete index of all 40 types — but moves
  behind a nav entry rather than being the front door.

### Implementation notes

- **Values must come from `aggregate()`**, never from summing raw records. A day tile is
  exactly the case where several apps writing the same metric would double-count. Types with
  no aggregate metric (`spec.aggregate == null`) cannot show a daily total at all: show the
  latest reading or a record count instead, never a computed sum.
- Use `aggregate()` for a single day rather than `aggregateGroupByPeriod`, unless the tile
  shows a sparkline, in which case one grouped call over the trailing week serves both.
- Day boundaries are **local midnight**, via the same alignment fix already in
  `TimeRange.localFilter()` — an unaligned window silently returns nothing.
- Tile configuration is non-health UI state, so it belongs in the existing DataStore. Health
  values themselves are still never persisted.
- Fetch tiles concurrently with a `Semaphore` cap, as the catalog probe already does.
- Empty vs. not-granted must stay distinct per tile, the same distinction `UiState` already
  encodes.

### Open questions

- Which stats form the default tile set for a first run?
- Should a tile show a comparison (vs. yesterday, vs. 7-day average)? Useful, but it is a
  second aggregation per tile.
- Does "advanced dashboard" (custom tiles beyond a free allowance) become a premium feature?
  `Feature.CUSTOM_DASHBOARD` is already reserved in the entitlement enum.

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

**Verified on a real device.** With three apps writing steps (Garmin Connect, Health Sync and
the phone itself), 1708 raw records over seven days aggregate to plausible daily totals of
4,560-18,014 steps. The UI correctly labels them "deduplicated daily totals" and reports that
three apps contributed. Summing those raw records would have multiplied the real count -- the
reason all totals go through `aggregate*()` and never through arithmetic on raw records.

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

## 6. Considered and rejected: a React/Vite UI in a WebView

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

## 7. Testing note: adb input injection on Xiaomi/HyperOS

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

## 8. Deferred

- **MindfulnessSession** — excluded from v1: the library requests
  `READ_MINDFULNESS_SESSION` while the platform defines only `READ_MINDFULNESS`, so the
  permission can never be granted. Add once those names converge.
- **Imperial units** — everything is metric today, matching what Health Connect returns
  natively. The registry's `unitRes` field is the seam for adding a conversion.
- **Play Billing products** — the entitlement gate is wired but reports no premium access
  until products exist in the Play Console.
