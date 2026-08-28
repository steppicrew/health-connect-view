# Feature ideas

Possible directions for the app, roughly ordered by expected value. Nothing here is committed
work — see `ROADMAP.md` for what is actually planned, and open an issue if you want to pick
something up.

## Trends, not just raw data

- An "Insights" tile that automatically surfaces notable deviations (e.g. "Resting heart rate this week is 8% above your 30-day average") — saves the user from eyeballing charts to spot changes.
- Rolling comparison: current 7-day average vs. 30-day average per metric, shown as a small arrow indicator (↑/↓) directly on the tile.
- Overlay two metrics on the same timeline (e.g. weight + steps) to help spot patterns between them.

## Handling multiple sources

Especially relevant since more than one app can potentially write to the same category:

- Show which app/source contributed a given value per tile, and optionally let the user set a priority source per data type in case two apps write to the same category at once.
- Visually distinguish missing days from genuine zero values (grey/dashed instead of "0"). An unexplained "0" is easily read as "you took no steps" when it actually means nothing was recorded.

## Blood pressure specifics

Worth its own logic since it behaves differently from other metrics:

- Separate display for morning vs. evening readings (standard guidance is twice daily) instead of one blended average.
- A simple PDF/CSV export for a chosen date range — useful for doctor visits when they ask for a log.

## Everyday usability

- A local reminder if, say, no blood pressure reading has been logged by mid-afternoon (on-device notification, no server needed).
- Goal streaks ("12 days in a row hitting your step goal") as a small motivational nudge.
- Export/import the dashboard configuration as a local JSON file, so a phone switch does not lose the setup. Local file only — the app has no network access.

## Scope heads-up

Not everything Garmin tracks internally (VO2max, Body Battery, Training Status, etc.) is necessarily exposed as a standard Health Connect record type — some proprietary Garmin metrics never make it into Health Connect at all. Worth checking early (via Health Sync / Garmin Connect) which data types actually arrive before planning tiles around them.
