package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.components.HeatmapLegend
import de.steppicrew.healthconnectview.ui.components.heatmapColors
import de.steppicrew.healthconnectview.ui.components.heatmapValue
import de.steppicrew.healthconnectview.ui.components.YearHeatmapGrid
import de.steppicrew.healthconnectview.health.YearHeatmap
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import de.steppicrew.healthconnectview.health.LongestStreak
import de.steppicrew.healthconnectview.health.MIN_GROUP_NIGHTS
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.health.TrainingNights
import de.steppicrew.healthconnectview.health.nightsByMorning
import de.steppicrew.healthconnectview.health.sleepRegularity
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.PersonalRecord
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.StreakSummary
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.registry.readingGap
import de.steppicrew.healthconnectview.registry.segmentAtGaps
import de.steppicrew.healthconnectview.settings.SettingsStore
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.InfoTitle
import de.steppicrew.healthconnectview.ui.components.InfoToggle
import de.steppicrew.healthconnectview.ui.components.SessionTimeline
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.components.rememberExplanation
import de.steppicrew.healthconnectview.util.appLabelFor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// The detail screen's figures under its chart: the headline, the boxes that follow it, and
// the places reserved for boxes that arrive late. Split from TileDetailScreen.kt.

/**
 * A window's figure as the headline writes it. A session type's total is a duration in hours,
 * and a quarter-hour session read as a bare "0,25" -- a fraction of nothing. Everything else
 * keeps the number, with its unit appended: "1.792" alone was equally unlabelled, just less
 * obviously so.
 */
@Composable
internal fun totalText(data: TileDetailData, total: Double, secondary: Double?): String =
    if (data.spec.tile.form == TileSpec.Form.SESSIONS) {
        Formatting.duration(Duration.ofMinutes((total * MINUTES_PER_HOUR).toLong()))
    } else {
        // A change is signed, as in the record list: "+0,01 °C" says above the
        // wearer's baseline where "0,01 ± °C" read like a tolerance.
        (if (data.spec.quantity == Quantity.TEMPERATURE_CHANGE && total > 0) "+" else "") +
            pressureText(total, secondary, data.spec.valueDecimals) +
            (data.spec.displayUnitRes?.let { Formatting.unitSpace(it) + stringResource(it) } ?: "")
    }

/** What the headline figure is. A mean is not a total: "Total 129 mmHg" read as blood pressures added up. */
@StringRes
private fun headlineLabel(data: TileDetailData): Int = when {
    // HRV's figure is computed, and says which: a night, or a week of them.
    data.hrv != null && data.extent == null -> R.string.hrv_week_label
    data.hrv != null -> R.string.hrv_night_label
    data.perNight -> R.string.span_per_night
    // One value a day: a day of it has nothing to average.
    data.spec.tile.dailyValue && data.extent != null -> R.string.span_daily_value
    data.spec.isAveraged || data.dailyFromReadings -> R.string.span_average
    // The readings' own mean, where the type has no aggregate; see the loader.
    data.spec.aggregate == null && data.spec.tile.occasional -> R.string.span_average
    else -> R.string.span_total
}

/**
 * The chart in words, for a screen reader (step 80): which type over which window, its figure,
 * the lowest and highest point, and the trend where the screen has one -- "Schritte, 4 Wochen.
 * Gesamt 231.000 Schritte. Niedrigster Wert 3.100 Schritte, höchster 14.800 Schritte." The
 * numbers the screen already shows, nothing worked out anew. No range for a pair of values,
 * whose points hold only the first of each.
 */
@Composable
internal fun chartDescription(data: TileDetailData, period: String): String {
    val parts = mutableListOf(stringResource(data.spec.displayNameRes) + ", " + period)
    data.total?.let { total -> parts += stringResource(headlineLabel(data)) + " " + totalText(data, total, data.secondaryTotal) }
    val values = data.points.map { it.value }
    if (values.isNotEmpty() && data.secondaryPoints.isEmpty()) {
        parts += stringResource(
            R.string.chart_description_range,
            totalText(data, values.min(), null),
            totalText(data, values.max(), null),
        )
    }
    data.trend?.let { trend ->
        parts += stringResource(
            when (trend.direction) {
                Trend.UP -> R.string.trend_title_up
                Trend.FLAT -> R.string.trend_title_flat
                Trend.DOWN -> R.string.trend_title_down
            },
        )
    }
    return parts.joinToString(". ")
}

/** "Steps_2026-09-20_2026-09-26": type and the window's first and last day. */
@Composable
internal fun SpanSummary(
    data: TileDetailData,
    period: String,
    onSelectSource: (String?) -> Unit,
    onOpenSession: (Session) -> Unit,
    onVisibleRange: (ClosedRange<Instant>?) -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        // First: it changes how a short chart should be read -- not missing data, but data
        // the app is not allowed to see.
        if (data.historyCapped) {
            Text(
                text = stringResource(R.string.detail_history_capped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = LocalHistoryAccess.current.onGrant) {
                Text(stringResource(R.string.history_grant))
            }
        }

        data.total?.let { total ->
            Text(
                text = stringResource(headlineLabel(data)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // A session type's total is a duration in hours, and a quarter-hour session
                // read as a bare "0,25" -- a fraction of nothing. Everything else keeps the
                // number, with its unit appended: "1.792" alone was equally unlabelled, just
                // less obviously so.
                text = totalText(data, total, data.secondaryTotal),
                style = MaterialTheme.typography.headlineMedium,
            )
            data.secondaryTotal?.let { second ->
                if (data.secondaryZones != null) CategoryBadge(pressureCategory(total, second))
            }
        }

        data.hrv?.let { HrvStatusLine(it, dayView = data.extent != null) }

        // Reading a dashed line against a curve is fiddly; say the answer in words too.
        data.goal?.let { goal ->
            val reached = (data.total ?: 0.0) >= goal
            Text(
                text = if (reached) {
                    // The time is the point of the badge: that the goal was met is already
                    // visible from the curve crossing the line.
                    data.goalCrossing?.let { crossing ->
                        stringResource(
                            R.string.chart_goal_reached_at,
                            Formatting.number(goal),
                            Formatting.time(crossing),
                        )
                    } ?: stringResource(R.string.chart_goal_reached, Formatting.number(goal))
                } else {
                    stringResource(
                        R.string.chart_goal_remaining,
                        Formatting.number(data.total ?: 0.0),
                        Formatting.number(goal),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (reached) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        data.dayParts?.let { DayPartsSection(it) }

        if (data.listPending || data.contributingApps.isNotEmpty()) {
            SourceSection(data = data, onSelectSource = onSelectSource)
        }

        // A session type draws its sessions rather than a series: see SessionTimeline for why
        // the aggregate makes no chart worth showing.
        if (data.spec.tile.form == TileSpec.Form.SESSIONS && data.extent != null) {
            SessionTimeline(
                sessions = data.sessions,
                extent = data.extent,
                modifier = Modifier.padding(top = 16.dp),
            )
            // The timeline is a chart with no series, so it falls outside the legend below --
            // which is drawn from `points`, and a session type's day has none. This is the
            // screen the shaded bands were actually reported on, so it is the last one that
            // should go unlabelled.
            ChartLegend(data = data)
        }

        data.readingContext?.let { ReadingContextStrip(data, it) }

        if (data.drawsChart) {
            // The single nights are optional: some want the week's line alone, some want to
            // see which nights moved it. Remembered, as a display choice, not per screen.
            val context = LocalContext.current
            val store = remember(context) { SettingsStore(context) }
            val scope = rememberCoroutineScope()
            val showNights by remember(store) { store.settings.map { it.showSingleNights } }
                .collectAsStateWithLifecycle(initialValue = true)
            // Likewise the four-week mean, off by default: a second line is clutter to anyone
            // not looking for their own level.
            val showMean by remember(store) { store.settings.map { it.showRollingMean } }
                .collectAsStateWithLifecycle(initialValue = false)
            val compare = LocalYearCompare.current
            val comparable = data.extent == null && !data.sessionCounts
            val compareUnlocked = unlocked(Feature.YEAR_COMPARE)
            val shown = data.copy(
                nightPoints = if (showNights) data.nightPoints else emptyList(),
                baseline = if (showMean) data.baseline else emptyList(),
                previousYear = data.previousYear.takeIf { compare.on && comparable && compareUnlocked },
                // The wearer's usual range replaces the day's spread where the type has one:
                // two bands behind one line would be two answers to "is this normal".
                rangeBand = data.usualBand.ifEmpty { data.rangeBand },
            )
            // Zoomed, the list below follows the stretch on screen.
            if (shown.sessionCounts && shown.points.size > 1 && shown.timePoints.size > 1) {
                WorkoutCountAndTime(shown, period, Modifier.padding(top = 16.dp))
            } else {
                ExpandableDataChart(shown, period, Modifier.padding(top = 16.dp), onVisibleRange = onVisibleRange)
            }
            Text(
                text = stringResource(
                    when {
                        // Counting sessions is not summing a metric, so "daily totals" would
                        // name the wrong operation.
                        // Bars built from the sessions, not from an aggregate: say what each is.
                        data.sessionCaption != null -> data.sessionCaption
                        shown.nightPoints.isNotEmpty() -> R.string.chart_source_hrv_nights
                        data.hrv != null && data.extent == null -> R.string.chart_source_hrv
                        data.approximated -> R.string.chart_source_cumulative_scaled
                        data.cumulative -> R.string.chart_source_cumulative
                        data.aggregated && data.weeklyBuckets && data.spec.isAveraged ->
                            R.string.chart_source_aggregated_weekly_mean
                        data.aggregated && data.weeklyBuckets ->
                            R.string.chart_source_aggregated_weekly
                        // "Totals" is wrong for a mean, and doubly so with a spread drawn
                        // behind it: the line is the day's average, not its sum.
                        data.aggregated && data.rangeBand.isNotEmpty() ->
                            R.string.chart_source_aggregated_range
                        data.aggregated && data.stack.isNotEmpty() ->
                            R.string.chart_source_aggregated_split
                        data.aggregated && data.spec.isAveraged -> R.string.chart_source_aggregated_mean
                        data.aggregated -> R.string.chart_source_aggregated
                        data.dailyFromReadings && data.weeklyBuckets -> R.string.chart_source_readings_weekly
                        data.dailyFromReadings -> R.string.chart_source_readings_daily
                        else -> R.string.chart_source_raw
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Everything drawn on the chart, named. The caption above says which *operation*
            // produced the series; this says which *mark* is which, which is a different
            // question and the one a shaded band raises -- the sleep ribbon behind a heart
            // rate was reported as simply unexplained.
            ChartLegend(data = shown)
            if (data.spec.stackRemainder != null) CaloriesNote()
            if (data.nightPoints.isNotEmpty()) {
                ChartToggle(showNights, R.string.hrv_show_nights) {
                    scope.launch { store.setShowSingleNights(it) }
                }
            }
            if (data.baseline.isNotEmpty()) {
                ChartToggle(showMean, R.string.chart_show_rolling_mean) {
                    scope.launch { store.setShowRollingMean(it) }
                }
            }
            if (comparable) {
                val activity = LocalActivity.current
                ChartToggle(compare.on && compareUnlocked, R.string.chart_compare_year, locked = !compareUnlocked) {
                    if (compareUnlocked) compare.onChange(it) else activity?.let(AppEntitlements.current::buy)
                }
                if (compare.on && compareUnlocked) YearEarlierLine(data)
            }

            // The bands are context; naming them is what turns a shaded region into
            // "that peak was the bike ride". Only where the sessions sit *behind* a chart --
            // on a session type's own screen they are the content, listed in full below.
            if (data.spec.tile.form != TileSpec.Form.SESSIONS) {
                data.sessions.forEach { session ->
                    SessionCaption(session = session, onClick = { onOpenSession(session) })
                }
            }

            // Naming the writer the shape came from: the total is everyone's, the path is
            // one device's, and leaving that unsaid would be a silent substitution.
            data.shapeSource?.let { writer ->
                Text(
                    text = stringResource(
                        R.string.chart_shape_from,
                        LocalContext.current.appLabelFor(writer),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A gap is only unambiguous once it is named; without this a dotted stretch reads as
            // a style, and a missing bar as a rendering artefact, rather than as no data.
            val readingGaps = remember(data.points, data.bars) {
                if (data.bars) 0 else segmentAtGaps(data.points, emptyList(), readingGap(data.points)).size - 1
            }
            when {
                data.emptyBuckets.isNotEmpty() -> Text(
                    text = pluralStringResource(
                        if (data.bars) R.plurals.chart_gaps else R.plurals.chart_gaps_dotted,
                        data.emptyBuckets.size,
                        data.emptyBuckets.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                readingGaps > 0 -> Text(
                    text = stringResource(R.string.chart_reading_gaps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Under the chart for the same reason: read with the list, after the chart is drawn.
        if (data.nutrients.isNotEmpty()) NutrientsSection(data.nutrients)

        // Under the chart, not beside the total: they are read after it (a streak reaching
        // back a year took 12.5 s), and arriving above it pushed the chart a quarter of the
        // screen down while it was being looked at.
        // Each box read after the chart holds its place while it is read, laid out with stand-in
        // values so the space is its own size: arriving, it no longer pushes the list down.
        val active = data.spec.tile.form == TileSpec.Form.SESSIONS
        val today = LocalDate.now()
        if (data.heatmapOffered) {
            val heatmap = data.heatmap
            when {
                heatmap != null -> HeatmapSection(heatmap, data.spec)
                data.heatmapPending -> Reserved { HeatmapSection(standInHeatmap(today), data.spec) }
                !unlocked(Feature.YEAR_HEATMAP) -> LockedSection(R.string.heatmap_title, R.string.heatmap_locked)
            }
        }
        // From the nights the chart already holds: nothing more to read.
        if (data.perNight) {
            val zone = HealthRepository.DEFAULT_ZONE
            val regularity = remember(data.sessions) { sleepRegularity(nightsByMorning(data.sessions, zone), zone) }
            when {
                regularity == null -> Unit
                unlocked(Feature.SLEEP_REGULARITY) -> SleepRegularitySection(regularity)
                else -> LockedSection(R.string.sleep_regularity_title, R.string.sleep_regularity_locked)
            }
        }
        val nights = data.trainingNights
        when {
            nights != null -> TrainingNightsSection(nights, data.spec.displayUnitRes)
            data.trainingNightsPending ->
                Reserved { TrainingNightsSection(TrainingNights(STAND_IN_GROUP, STAND_IN_GROUP), data.spec.displayUnitRes) }
        }
        val streak = data.streak
        when {
            streak != null -> StreakExplanation(streak, active)
            data.extrasPending && data.expectsStreak ->
                Reserved { StreakExplanation(StreakSummary(MIN_STREAK, LongestStreak(MIN_STREAK, today, today)), active) }
        }
        val trend = data.trend
        when {
            trend != null -> TrendExplanation(trend, data.spec.displayUnitRes, data.spec.valueDecimals)
            data.extrasPending && data.expectsTrend ->
                Reserved { TrendExplanation(TrendResult(Trend.FLAT, 0.0, 0.0), data.spec.displayUnitRes, data.spec.valueDecimals) }
        }
        val record = data.record
        val recordKind = data.spec.tile.personalRecord
        when {
            record != null -> RecordExplanation(record, data.spec)
            // Dated away from the shown day, so the "show this day" button is laid out too.
            data.extrasPending && recordKind != null ->
                Reserved { RecordExplanation(PersonalRecord(recordKind, 0.0, today.minusYears(1)), data.spec) }
        }
    }
}

/**
 * [content] laid out but not shown, with a small spinner over it: the place a box will take
 * once read. Not tappable and silent to accessibility, since nothing in it is real.
 */
@Composable
private fun Reserved(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(0f).clearAndSetSemantics {}) { content() }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { } },
        )
        CircularProgressIndicator(Modifier.size(RESERVED_SPINNER.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun unlocked(feature: Feature): Boolean {
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    return pro.allows(feature)
}

/** An empty year the size of a real one, for the place held while it is read. */
private fun standInHeatmap(today: LocalDate) =
    YearHeatmap(first = today.minusYears(1).plusDays(1), last = today, values = emptyMap(), low = 0.0, high = 1.0)

/**
 * The year as a calendar, one shade per day, with the tapped day's value beneath and a way
 * to it. Says what the shades mean, since the darkest is "the top 5 %" rather than the maximum.
 */
@Composable
private fun HeatmapSection(heatmap: YearHeatmap, spec: RecordTypeSpec<*>) {
    var selected by rememberSaveable(heatmap.first) { mutableStateOf<LocalDate?>(null) }
    val unit = spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty()
    val value = heatmapValue(spec)
    // Blood pressure is coloured by the grade of the day's averages, as every reading of it is
    // elsewhere; one shade of the systolic alone would hide half the reading.
    val graded = heatmap.secondValues.isNotEmpty()
    val colorOf = heatmapColors(heatmap)
    fun reading(day: LocalDate): String? {
        val first = heatmap.values[day] ?: return null
        val second = heatmap.secondValues[day]
        return if (graded && second != null) "${pressureText(first, second)}$unit"
        else value(first)
    }
    val zone = HealthRepository.DEFAULT_ZONE
    val title = stringResource(R.string.heatmap_title)
    val explanation = rememberExplanation("heatmap")
    InfoGroup {
        InfoTitle(title, explanation)
        YearHeatmapGrid(
            heatmap = heatmap,
            colorOf = colorOf,
            selected = selected,
            onSelect = { selected = it },
            description = title,
            modifier = Modifier.padding(top = 8.dp),
            showEmpty = false,
        )
        val day = selected
        if (day == null) {
            Text(
                text = stringResource(R.string.heatmap_tap),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Formatting.date(day.atStartOfDay(zone).toInstant(), zone) + ": " +
                        (reading(day) ?: stringResource(R.string.heatmap_nothing)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val jump = LocalDayJump.current
                TextButton(onClick = { jump.onShow(day) }) { Text(stringResource(R.string.record_show)) }
            }
        }
        if (graded) {
            // The grades the year holds, each named beside its colour.
            val grades = heatmap.values.mapNotNull { (day, first) -> heatmap.secondValues[day]?.let { pressureCategory(first, it) } }
                .toSortedSet()
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                grades.forEach { CategoryBadge(it) }
            }
        } else {
            HeatmapLegend(heatmap, spec, Modifier.padding(top = 8.dp))
        }
        // How the grid is read, folded away once known; how to use it stays above.
        if (explanation.expanded == true) {
            Text(
                text = stringResource(if (graded) R.string.heatmap_note_graded else R.string.heatmap_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Why total calories are not the basal rate plus the active calories, folded away behind an
 * "i": the owner expected the three tiles to add up (24.10.2026), and they come from three
 * different places.
 */
@Composable
private fun CaloriesNote() {
    val explanation = rememberExplanation("calories")
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.calories_explain_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            InfoToggle(explanation, firstLine = MaterialTheme.typography.bodyMedium)
        }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.calories_explain_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A Pro section's place without Pro: what it is, and the way to it. */
@Composable
private fun LockedSection(@StringRes title: Int, @StringRes body: Int) {
    val activity = LocalActivity.current
    InfoGroup(Modifier.clickable { activity?.let(AppEntitlements.current::buy) }) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp, top = firstLineInset(MaterialTheme.typography.titleSmall, 20.dp)).size(20.dp),
            )
            Text(
                stringResource(R.string.export_premium, stringResource(title)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = firstLineTextInset(MaterialTheme.typography.titleSmall, 20.dp)),
            )
        }
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val RESERVED_SPINNER = 20
private val STAND_IN_GROUP = TrainingNights.Group(meanLow = 0.0, nights = MIN_GROUP_NIGHTS)

/** A streak box comes on a day of a goal ring or of workouts; see the loader's streak. */
private val TileDetailData.expectsStreak: Boolean
    get() = extent != null &&
        ((goal != null && spec.tile.form == TileSpec.Form.RING) || spec.tile.sessionKind == Session.Kind.EXERCISE && spec.tile.form == TileSpec.Form.SESSIONS)

/** A trend box comes on a day of a type with a daily figure, sessions aside. */
private val TileDetailData.expectsTrend: Boolean
    get() = extent != null && spec.aggregate != null && spec.tile.form != TileSpec.Form.SESSIONS

/** The aggregate for a session type comes back in hours; durations format from minutes. */
internal const val MINUTES_PER_HOUR = 60
