# How each type is drawn -- review for step 49

Inventory as of 07.10.2026 (read from `RecordRegistry` and `TileChartLoader`), the rules it is
checked against, and the decisions the owner made: all as recommended, 07.10.2026, and built
the same day.

Windows: **Day** (24 h), **Week** and **4 weeks** (one bucket per day), **Year** (one bucket
per week).

## Rules by kind of data

| Kind | Day | Week / 4 weeks | Year |
|------|-----|----------------|------|
| A. Summed quantity | running total through the day | bar per day | bar per week |
| B. Frequent reading | every reading as a line | daily mean, low-high band | weekly mean, band |
| C. Taken now and then | see decision 1 | dots joined by a line | weekly mean, dots |
| D. One value a day | headline only, no chart | dots, four-week mean | weekly mean |
| E. Sessions | timeline (and stages) | bar per day | bar per week (decision 4) |
| F. Events without a unit | list only | list only | list only |

## Inventory

`=` follows its rule; `≠` departs from it (numbered where a decision is needed).

| Type | Kind | Day now | Across days now | |
|------|------|---------|-----------------|---|
| Steps | A | running total, goal, session bands | bars; 4-week mean on request | = |
| Distance, Floors, Total calories | A | running total, goal | bars | = |
| Active calories, Elevation, Wheelchair pushes, Hydration | A | running total | bars | = |
| Nutrition (energy) | A | hourly totals as a **line** | daily totals as a **line** | ≠ 3 |
| Basal metabolic rate | D (derived) | headline only | dots and line | = |
| Heart rate | B | readings, coloured by zones, session bands | daily mean, min-max band | = |
| Power, Speed, Cycling cadence, Steps cadence | B | readings | daily mean, min-max band | = |
| Oxygen saturation, Respiratory rate | B | readings, dots | daily mean, band, 4-week mean | = |
| Skin temperature | B | readings | **every reading, thinned** | ≠ 2 |
| Blood glucose, Body temperature, Basal body temperature | B/C | readings, dots | **every reading, thinned** | ≠ 2 |
| Blood pressure | C | readings, graded colours | daily mean sys/dia, morning/evening split | ≠ 1 (a day of one reading) |
| Weight | C | **one dot** at the weigh-in time | daily mean, dots, band where several | ≠ 1 |
| Body fat, Body water, Bone mass, Lean mass, Height | C | **one dot** | every reading | ≠ 1 |
| VO2 max | C | **one dot** | every reading | ≠ 1 |
| Resting heart rate | D | headline only | dots, 4-week mean | = |
| HRV | D (nightly) | the night's readings | weekly mean, *usual range* band | = |
| Sleep | E | timeline with stages | bar per day (hours) | ≠ 4 in the year |
| Workouts | E | timeline | bar per day (count) | ≠ 4 in the year |
| Mindfulness | E | timeline | bar per day (minutes) | ≠ 4 in the year |
| Cycle types, Sexual activity, Planned workouts | F | list | list; cycle overview | = |

## Ranges today

- **Reference** (from outside the data): heart-rate zones (the user's own, or a default),
  blood-pressure grades. Labelled in the legend.
- **Usual** (the wearer's own history): HRV only -- the middle half of 28 nights.
- None: oxygen saturation, glucose, temperature, respiratory rate, resting heart rate.

## Decisions for the owner

Each with options rated Option | Pros | Cons | Risks | Score; the first row is the
recommendation.

### 1. A day of a type taken now and then

Weight, body composition, VO2 max, blood pressure on a day of one reading. Today a lone dot on
a 24-hour axis.

| Option | Pros | Cons | Risks | Score |
|--------|------|------|-------|-------|
| **Context strip:** no 24-hour axis; the day's value as headline, the change since the previous reading ("-0,4 kg seit 01.10."), and the last ~10 readings as dots with this day's highlighted; several readings that day shown as their range | Answers what a single reading is asked for -- up or down, against what; works for every type in the group | A day view that looks unlike the others | "Change" over an uneven gap (2 days vs 3 weeks) must say the dates, or it reads as a rate | **8/10** |
| Owner's idea: a bar for the day, the previous weigh-in as a shadow bar behind it, a range where the day had several | Before/after at a glance | A bar from zero buries a 0.4 kg change in an 80 kg bar; needs a cut axis, which misleads | Two bars read as two days | 6/10 |
| Headline only (as resting heart rate) | Simplest, consistent with type D | Drops the context the reading needs | Looks like missing data | 5/10 |
| Keep the dot | No work | The reported problem | -- | 2/10 |

### 2. Readings without an aggregate, across days

Glucose, body temperature, basal body temperature, skin temperature.

| Option | Pros | Cons | Risks | Score |
|--------|------|------|-------|-------|
| **Frequent ones (glucose, skin temperature) as daily means with the low-high band, as oxygen; occasional ones (body and basal temperature) as dots joined, as weight** | Same look for the same kind of data; a band keeps a glucose spike visible | Two treatments to explain | Daily mean of glucose hides meal peaks -- the band is what keeps them | **8/10** |
| All as daily means with band | One rule | A thermometer reading once a week becomes a "mean" of one | Band of zero width everywhere | 6/10 |
| Keep every reading thinned | No work | A year is an unreadable zigzag, unlike its neighbours | -- | 3/10 |

### 3. Nutrition

| Option | Pros | Cons | Risks | Score |
|--------|------|------|-------|-------|
| **Treat as a summed quantity: running total on a day, bars across days** | Matches steps and hydration; energy eaten adds up | None found | Writers posting one record per meal vs per day: the running total steps at meals, which is right | **9/10** |
| Keep the line | No work | An hourly "line" of meals implies eating between them | -- | 2/10 |

### 4. Sessions in the year

| Option | Pros | Cons | Risks | Score |
|--------|------|------|-------|-------|
| **One bar per week: sleep as mean hours a night, workouts as count a week, mindfulness as minutes a week** | 52 readable bars, like the summed types | Sleep's bar is a mean while workouts' is a count; captions must say which | A week with three nights recorded averages over three, not seven -- say "per recorded night" | **8/10** |
| Keep a bar per day | No work | 365 hairlines | -- | 4/10 |

### 5. Ranges

Reference ranges come from outside the data and name their source; usual ranges are the
wearer's own (middle half of 28 days) and say so. Never both on one chart.

| Type | Proposal | Score |
|------|----------|-------|
| Oxygen saturation | **Reference band 95-100 %**, labelled "üblicher Referenzbereich für Erwachsene" | 8/10 |
| Resting heart rate | **Usual range** instead of the dashed 4-week mean | 7/10 |
| Respiratory rate | **Usual range** instead of the dashed 4-week mean | 7/10 |
| Blood glucose | None for now: targets depend on fasting/meal and on the person; a user-set target like the heart-rate zones would be its own step | 6/10 |
| Body temperature | None: a fever line is a diagnosis | -- |
| Heart rate, blood pressure, HRV | Keep as they are | -- |
