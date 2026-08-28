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

## 3. Chart refinement

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

## 4. Deferred

- **MindfulnessSession** — excluded from v1: the library requests
  `READ_MINDFULNESS_SESSION` while the platform defines only `READ_MINDFULNESS`, so the
  permission can never be granted. Add once those names converge.
- **Imperial units** — everything is metric today, matching what Health Connect returns
  natively. The registry's `unitRes` field is the seam for adding a conversion.
- **Play Billing products** — the entitlement gate is wired but reports no premium access
  until products exist in the Play Console.
