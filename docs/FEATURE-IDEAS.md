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

*Moved to the plan on 10.10.2026* (`ROADMAP.md`, steps 73-82); the reminder and the widget are
deferred (`ROADMAP.md` §19). Kept here for the scores.

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

## Distance by activity (Pro)

The owner, 13.10.2026, over the adaptive distance goal: walking and cycling produce the same
kilometres but are not the same thing, so each activity -- cycling, walking, canoeing ... --
should be collected on its own, with its own total, chart and goal. Not scored yet. What it
runs into: distance records carry no activity, so a day's kilometres have to be assigned by
time overlap with the exercise sessions and what is left counted as on foot; writers disagree
about the activity type (`BIKING` against `BIKING_STATIONARY` for the same ride); and a
whole-day summary record cannot be split at all. Every surface showing it would have to say it
is matched by time.

## Adaptive goal on active calories

Noted 13.10.2026 in the same discussion, as the other answer to "walking and cycling should
weigh differently": active calories are effort the device has already weighed across every
activity, with no factor of ours. An adaptive goal there (`AdaptiveGoalRule`, as for steps and
distance) would follow effort where the distance goal follows kilometres. Not scored yet. It
needs a ring tile for active calories, which has none (rings: steps, distance, floors, total
calories), a cap and a rounding step chosen without a watch record to read them from, and a
device that writes the type at all. Today's calorie totals are also the hardest figures in the
app to get right (the running tally in `CLAUDE.md`), so the day's own value must be checked on
the phone before a ring is drawn against it. Total calories would not do instead: most of it
is basal energy that is the same every day, which leaves the goal little to follow.

## Travel days

The owner, 07.11.2026, after a 20-hour flight to a place six hours behind: no workout was
possible that day, and the short walk taken to keep the streak was filed on the day before.
That filing was a bug, fixed by giving each session the day it happened on by its own UTC
offset (`Session.startDay`/`endDay`). What remains is the idea: recognise a travel day and
treat it as the streak already treats a day without data -- neither counted nor breaking the
run. Not scored yet.

- **Signal:** a change of UTC offset between one writer's records. Measured on the phone:
  heart rate showed `+01:00` and `-05:00` on the three days around the flight and one offset
  on every other day, so the change marks the trip to within a day. Cheap (offsets of
  records already read) and exact, unlike guessing from a raised pulse without steps, which
  also fits a long car ride or a day at a desk.
- **Where it shows:** a mark on the day in the workout streak and its calendar, and on the
  active-minutes screen, where a travel day's left-out minutes are expected.
- **Wording:** "Reisetag" as a fact about the data ("Zeitzone gewechselt"), never as an excuse
  the app makes for the user. A streak bridged over a travel day must say so, or it reads as
  a day with a workout.
- **Open:** a day trip across a border with no zone change is not a travel day by this
  signal; a phone set to a zone by hand, or a writer that records no offset, gives none.

## Active minutes against the WHO guideline

Asked for by a Pro user, 07.11.2026, as the points one fitness app gives for the WHO
guideline. Health Connect has no type for such points or for active minutes, so no writer can
pass them on; the app would have to estimate them from heart rate. Not scored yet. The
guideline is 150 minutes of moderate activity a week, or 75 vigorous, a vigorous minute
counting twice. Since 2020 every minute counts, not only bouts of ten.

Sketch:

- **Source: all-day heart rate**, read with `forEachPage` and reduced page by page to one
  intensity per minute (none, moderate, vigorous), so a week is 10,080 small values in memory
  and no reading is kept. Not `readForChart`, which thins away the minutes, and not a
  per-minute `aggregateGroupByDuration`: the cost grows with the buckets (365 daily buckets
  took 55 s on the phone).
- **Thresholds from heart-rate reserve** (Karvonen): moderate from 40 %, vigorous from 60 % of
  the span between resting and maximum heart rate. Resting from `RestingHeartRateRecord`
  (median of the past 30 days); maximum from settings or `observedMaxHeartRate`, as the
  workout zones do. Where resting is missing, shares of the maximum instead (64 % and 77 %),
  and the screen says which was used.
- **One writer per minute, never added up.** A minute is classified from one writer's
  readings: the preferred source if one is set, otherwise the writer with most readings that
  day. Counting minutes on one timeline cannot double-count the way summing values does, but
  two writers disagreeing on the same minute must not make it count twice either.
- **Gaps count as nothing.** Each reading counts until the next for at most a cap, as
  `heartZones` does with `LONGEST_SAMPLE`; all-day sampling is sparser than in a workout
  (often one reading a minute or less), so the cap needs measuring on the phone. The screen
  shows how much of the waking day had readings, since a watch that samples every ten minutes
  will show far fewer active minutes than were done.
- **Shown as a week**, a ring against 150 (moderate plus twice vigorous) on a dashboard tile,
  with each day as a stacked bar of moderate and vigorous on the detail screen. Weeks and
  four weeks only: a year of readings is millions of samples.
- **Wording:** "estimated from your heart rate", with the thresholds behind an "i". Neutral
  name ("Aktive Minuten"), never the other app's term for its points.

Open questions to measure first: whether stress or caffeine at rest puts minutes over 40 %
of reserve (perhaps require two minutes in a row); how many readings an hour the common
writers leave outside workouts; how long a week's read takes on the phone. A later second
source: step cadence (100 steps a minute or more is moderate) for days without heart rate,
though minute-level steps run into the intraday double-counting in `CLAUDE.md`.

## Everyday usability

- A local reminder if, say, no blood pressure reading has been logged by mid-afternoon (on-device notification, no server needed).
- Goal streaks ("12 days in a row hitting your step goal") as a small motivational nudge.
- Export/import the dashboard configuration as a local JSON file, so a phone switch does not lose the setup. Local file only — the app has no network access.

## Scope heads-up

Not everything Garmin tracks internally (VO2max, Body Battery, Training Status, etc.) is necessarily exposed as a standard Health Connect record type — some proprietary Garmin metrics never make it into Health Connect at all. Worth checking early (via Health Sync / Garmin Connect) which data types actually arrive before planning tiles around them.
