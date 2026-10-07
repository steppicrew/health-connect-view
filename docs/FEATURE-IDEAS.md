# Feature ideas

Possible directions for the app, roughly ordered by expected value. Nothing here is committed
work — see `ROADMAP.md` for what is actually planned, and open an issue if you want to pick
something up. The order they are being worked in is under "Next steps" in `ROADMAP.md`.

## Trends, not just raw data

- An "Insights" tile that automatically surfaces notable deviations (e.g. "Resting heart rate this week is 8% above your 30-day average") — saves the user from eyeballing charts to spot changes.
- Rolling comparison: current 7-day average vs. 30-day average per metric, shown as a small arrow indicator (↑/↓) directly on the tile.
- Overlay two metrics on the same timeline (e.g. weight + steps) to help spot patterns between them.

## Handling multiple sources

Especially relevant since more than one app can potentially write to the same category:

- *Built (ROADMAP §3):* show which app/source contributed a given value per tile, and optionally let the user set a priority source per data type in case two apps write to the same category at once.
- *Built (ROADMAP §1, "a missing value renders as a dash"):* visually distinguish missing days from genuine zero values (grey/dashed instead of "0"). An unexplained "0" is easily read as "you took no steps" when it actually means nothing was recorded.

## Blood pressure specifics

Worth its own logic since it behaves differently from other metrics:

- *Built (ROADMAP §16):* separate display for morning vs. evening readings (standard guidance is twice daily) instead of one blended average.
- *Built (ROADMAP §17):* a PDF report for a chosen date range — useful for doctor visits when they ask for a log.

## Motivation

Picked by the owner on 07.10.2026 from a list of ideas, alongside goal streaks (built):

- Personal records: the best step day, the longest workout, the lowest resting heart rate,
  each with its date, as a tile and on the type's screen. A max/min over the daily totals
  already read; a record that comes from overlapping writers (one 90,000-step day from a
  duplicated sync) must be ruled out by the same one-writer-per-day logic the charts use, or
  the best day is a data fault.

## Handling multiple sources, continued

- Suggest which source suits each data type, the owner's idea of 07.10.2026. For every type
  with more than one writer: how many days each covers, how many readings a day, how far back
  it goes, and which details it fills (sleep stages, device, measurement context). From that,
  name the one that covers most and offer it as the preferred app in one tap. Worded as what
  the data shows ("schreibt an 98 % der Tage, mit Schlafphasen"), never as a verdict on a
  device or app, and computed only on request, since it probes every type.

## From the list of 07.10.2026, not picked first

Ideas from the same list; recorded so they are not lost, not ranked. Scores are from that list
(out of 10), which weighed value against cost and risk.

Free:

- Year heatmap per metric (8): a calendar grid coloured by daily value, from the daily totals
  already read. Shows habits and gaps at a glance; needs a colour scale per type.
- Sources overview (7): which app writes which types, since when, and where they overlap.
  The groundwork for the suggested source above.
- Jump to a date (7): a date picker on the dashboard and the detail screens, instead of many
  swipes.
- Charts described for screen readers (6): "Schritte, 4 Wochen, Mittel 8.200, steigend" from
  the trend numbers that already exist.
- Home-screen widget (4): very visible, but it needs reading in the background
  (`READ_HEALTH_DATA_IN_BACKGROUND`), which changes what the privacy page promises.

Pro:

- One report for the doctor (9): blood pressure, weight, resting heart rate and glucose for one
  period in a single PDF, from the four reports that exist.
- Compare periods (8): this month over the same month last year on one chart. Answers "is it
  better?" without implying a cause; anything older than 30 days needs the history permission.
- Time in heart-rate zones (7): per workout and per week, from the zones set on the tile and
  the heart rate matched to the session by time -- and saying it is matched by time.
- Sleep regularity (7): the spread of bedtimes and wake times and the usual bedtime, from the
  sessions alone. A description, never a score.
- The same workout over time (6): pace and heart rate for one activity type, or one route,
  across weeks. "The same route" is fuzzy, and writers disagree about activity types.
- Monthly summary PDF (5): any metric for a month; overlaps with the CSV export.

## Everyday usability

- A local reminder if, say, no blood pressure reading has been logged by mid-afternoon (on-device notification, no server needed).
- Goal streaks ("12 days in a row hitting your step goal") as a small motivational nudge.
- Export/import the dashboard configuration as a local JSON file, so a phone switch does not lose the setup. Local file only — the app has no network access.

## Scope heads-up

Not everything Garmin tracks internally (VO2max, Body Battery, Training Status, etc.) is necessarily exposed as a standard Health Connect record type — some proprietary Garmin metrics never make it into Health Connect at all. Worth checking early (via Health Sync / Garmin Connect) which data types actually arrive before planning tiles around them.
