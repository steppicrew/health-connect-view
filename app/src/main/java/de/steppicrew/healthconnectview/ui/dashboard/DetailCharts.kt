package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.components.OverlayLine
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.TrainingNights
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.HrvStanding
import de.steppicrew.healthconnectview.health.HrvSummary
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.labelRes
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import kotlin.math.roundToLong
import de.steppicrew.healthconnectview.ui.components.SeriesColors
import de.steppicrew.healthconnectview.ui.components.MultiLineChart
import de.steppicrew.healthconnectview.ui.components.ChartSeries
import de.steppicrew.healthconnectview.ui.components.StripSegment
import de.steppicrew.healthconnectview.ui.components.DotText
import de.steppicrew.healthconnectview.ui.components.ExpandableChart
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.InfoToggle
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.stackColors
import de.steppicrew.healthconnectview.ui.components.REFERENCE_COLOR
import de.steppicrew.healthconnectview.ui.components.BreakLegend
import de.steppicrew.healthconnectview.ui.components.chartTitle
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.components.rememberExplanation
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.map

// The detail screen's chart and what is drawn around it: legend, toggles, the year-earlier
// line, training nights, HRV status. Split from TileDetailScreen.kt.

/** One session named on a single line, under the chart it explains. */
/**
 * Names every mark on the chart: the series itself, and whatever is drawn behind or across it.
 *
 * The caption above the legend says which *operation* produced the numbers ("daily totals,
 * deduplicated"); this says what each *shape* is. They answer different questions, and the
 * second one went unanswered: the pale blue sleep ribbon behind a chart was reported as
 * unexplained, since nothing on screen connected it to sleep.
 *
 * Only what is actually drawn is listed. A legend naming a mark the chart does not have is
 * worse than none -- it sends the reader looking for something that is not there.
 *
 * The dots on a marked-readings line are that line's own points, not a second series, so
 * they get no entry of their own. They used to, as "Measurement" beside "Reading" with an
 * identical swatch: two names for one thing, which read as a mark the chart did not have.
 */
@Composable
internal fun ChartLegend(data: TileDetailData) {
    // A classified pair of lines: the colours are the message, so the legend names the grades
    // instead of repeating "line" and "reading" in a colour neither line has.
    if (data.secondaryZones != null && data.points.isNotEmpty()) {
        PressureLegend()
        return
    }
    val sleepShown = data.bandSessions.any { it.kind == Session.Kind.SLEEP }
    val exerciseShown = data.bandSessions.any { it.kind == Session.Kind.EXERCISE }
    val stacked = data.stack.isNotEmpty() && data.stackLabels.isNotEmpty()

    // The series' own name depends on what a point means, which is exactly what the mark
    // already encodes: a bar is a bucket's total, a line is a reading.
    val seriesLabel = when {
        // No series to name: a session type's day is the timeline alone, whose bands are
        // named below like any other.
        data.points.isEmpty() -> null
        stacked -> null // The stack's own segments are named below; a total above them is noise.
        data.sessionCounts && data.weeklyBuckets -> R.string.chart_source_sessions_per_week
        data.sessionCounts -> R.string.legend_value_sessions
        data.spec.tile.form == TileSpec.Form.SESSIONS && data.bars &&
            data.spec.tile.sessionKind == Session.Kind.MINDFULNESS ->
            if (data.weeklyBuckets) R.string.chart_source_session_time_weekly else R.string.chart_source_session_time_daily
        data.spec.tile.form == TileSpec.Form.SESSIONS && data.bars ->
            R.string.legend_value_sleep_hours
        data.bars && data.weeklyBuckets -> R.string.legend_value_bars_weekly
        data.bars -> R.string.legend_value_bars
        data.cumulative -> R.string.legend_value_cumulative
        data.hrv != null && data.extent == null -> R.string.legend_hrv_week
        // A year is bucketed by week, as the caption says; "daily" here contradicted it.
        data.rangeBand.isNotEmpty() && data.weeklyBuckets -> R.string.legend_value_mean_weekly
        data.rangeBand.isNotEmpty() -> R.string.legend_value_mean
        // Named as the caption names it: across days a weight point is that day's mean, not a
        // reading, and calling it "Reading" contradicted "Daily averages" printed above.
        data.aggregated && data.weeklyBuckets && data.spec.isAveraged -> R.string.legend_value_mean_weekly
        data.aggregated && data.spec.isAveraged -> R.string.legend_value_mean
        data.dailyFromReadings && data.weeklyBuckets -> R.string.legend_value_mean_weekly
        data.dailyFromReadings -> R.string.legend_value_mean
        else -> R.string.legend_value_line
    }

    // Nothing worth naming: a plain line with no goal, no band and no sessions behind it is
    // already self-explanatory, and a legend of one entry repeating the caption is clutter.
    //
    // A shaded band is the exception, and always names itself even when it is the only entry.
    // It is the one mark with no other explanation on screen -- a line has an axis and a
    // caption, a bar has both plus its own label, but a pale rectangle behind them has
    // nothing. The sleep timeline is exactly that case: a single band, reported as
    // unexplained, which the "more than one entry" rule would have gone on suppressing.
    val reference = data.spec.tile.referenceRange
    val trainingShown = data.trainingDays.isNotEmpty()
    val bandShown = sleepShown || exerciseShown || trainingShown || data.rangeBand.isNotEmpty() || reference != null
    val entries = (if (seriesLabel != null) 1 else 0) +
        (if (stacked) data.stackLabels.size else 0) +
        listOf(
            data.rangeBand.isNotEmpty(),
            reference != null,
            sleepShown,
            exerciseShown,
            trainingShown,
            data.goal != null,
            data.nightPoints.isNotEmpty(),
            data.baseline.isNotEmpty(),
            !data.previousYear.isNullOrEmpty(),
        ).count { it }
    if (entries < 2 && !bandShown) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        seriesLabel?.let { LegendEntry(color = MaterialTheme.colorScheme.primary, label = it) }

        // Named in the same order and colour as the segments they stand for.
        if (stacked) {
            val colors = stackColors(MaterialTheme.colorScheme.primary, data.stackLabels.size)
            data.stackLabels.forEachIndexed { index, label ->
                LegendEntry(color = colors[index], label = label)
            }
        }

        if (data.rangeBand.isNotEmpty()) {
            LegendEntry(
                color = MaterialTheme.colorScheme.primary.copy(alpha = LEGEND_BAND_ALPHA),
                label = when {
                    data.hrv != null || data.usualBand.isNotEmpty() -> R.string.legend_hrv_usual
                    data.weeklyBuckets -> R.string.legend_range_weekly
                    else -> R.string.legend_range
                },
            )
        }
        // Named with where it comes from, so it is never read as the wearer's own range.
        reference?.let {
            LegendEntry(
                color = REFERENCE_COLOR.copy(alpha = LEGEND_BAND_ALPHA),
                label = it.labelRes,
            )
        }
        if (sleepShown) {
            LegendEntry(color = LEGEND_SLEEP, label = R.string.legend_sleep)
        }
        if (exerciseShown) {
            LegendEntry(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = LEGEND_BAND_ALPHA),
                label = R.string.legend_exercise,
            )
        }
        // A workout's break is drawn grey inside its band, so the band does not claim the
        // hours off the bike as training.
        if (data.bandSessions.any { it.breaks.isNotEmpty() }) {
            LegendEntry(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = LEGEND_BREAK_ALPHA),
                label = R.string.session_break,
            )
        }
        if (data.nightPoints.isNotEmpty()) {
            LegendEntry(
                color = MaterialTheme.colorScheme.primary.copy(alpha = LEGEND_BAND_ALPHA),
                label = R.string.legend_hrv_night,
                round = true,
            )
        }
        if (data.baseline.isNotEmpty()) {
            LegendEntry(color = MaterialTheme.colorScheme.onSurfaceVariant, label = R.string.legend_rolling_mean)
        }
        if (!data.previousYear.isNullOrEmpty()) {
            LegendEntry(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PREVIOUS_YEAR_ALPHA), label = R.string.legend_previous_year)
        }
        if (data.goal != null) {
            LegendEntry(color = MaterialTheme.colorScheme.tertiary, label = R.string.legend_goal)
        }
        if (trainingShown) {
            LegendEntry(color = trainingColor(), label = R.string.legend_training_days)
        }
    }
}

/**
 * Workouts across days: the count per day or week, and the training time behind it -- the
 * owner's idea, 09.10.2026. A chip each, as on a workout's chart: the one tapped last owns the
 * labelled axis, the other stands beside it as lighter bars on its own unlabelled scale, and
 * a touch reads both. Moving time where breaks were worked out, whole durations across a year.
 */
@Composable
internal fun WorkoutCountAndTime(data: TileDetailData, period: String, modifier: Modifier) {
    val countLabel = stringResource(R.string.type_exercise_session)
    val timeLabel = stringResource(if (data.timeMoving) R.string.session_time_moving else R.string.chart_training_time_whole)
    ExpandableChart(chartTitle(stringResource(data.spec.displayNameRes), null, period)) { expanded, onExpand ->
        MultiLineChart(
            chartId = WORKOUT_TIME_CHART,
            series = listOf(
                ChartSeries(
                    key = LINE_COUNT,
                    label = countLabel,
                    points = data.points,
                    color = SeriesColors.blue(),
                    unitKey = LINE_COUNT,
                    integral = true,
                    bars = true,
                ),
                ChartSeries(
                    key = LINE_TIME,
                    label = timeLabel,
                    points = data.timePoints,
                    color = SeriesColors.orange(),
                    unitKey = LINE_TIME,
                    bars = true,
                    valueText = ::hoursText,
                ),
            ),
            defaultShown = listOf(LINE_COUNT, LINE_TIME),
            dateOnly = true,
            fillHeight = expanded,
            onExpand = onExpand,
            holdSelection = expanded,
            modifier = if (expanded) Modifier.fillMaxSize() else modifier,
        )
    }
}

/** A time in hours as the axis and readout write it: "8h 33m", and a whole "30h" or "0" plain. */
private fun hoursText(hours: Double): String {
    val minutes = (hours * MINUTES_PER_HOUR).roundToLong()
    return when {
        minutes == 0L -> Formatting.number(0.0)
        minutes % MINUTES_PER_HOUR == 0L -> Formatting.hours(minutes / MINUTES_PER_HOUR)
        else -> Formatting.duration(Duration.ofMinutes(minutes))
    }
}

private const val WORKOUT_TIME_CHART = "workouts_count_time"
private const val LINE_COUNT = "count"
private const val LINE_TIME = "time"

/** The strip of training days and its legend swatch: the exercise bands' hue, solid enough to see at 8 dp. */
@Composable
private fun trainingColor(): Color = MaterialTheme.colorScheme.tertiary.copy(alpha = TRAINING_ALPHA)

/**
 * The night's lowest heart rate after training days against rest days, side by side with the
 * number of nights behind each. No verdict: a higher low after training is common, and the
 * note names what else moves it.
 */
@Composable
internal fun TrainingNightsSection(split: TrainingNights, @StringRes unitRes: Int?) {
    val unit = unitRes?.let { " " + stringResource(it) }.orEmpty()
    InfoGroup {
        Text(stringResource(R.string.training_nights_title), style = MaterialTheme.typography.titleSmall)
        listOf(
            R.string.training_nights_after_training to split.afterTraining,
            R.string.training_nights_after_rest to split.afterRest,
        ).forEach { (label, group) ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = group?.let { Formatting.number(it.meanLow, 0) + unit } ?: "–",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = group?.let { pluralStringResource(R.plurals.training_nights_count, it.nights, it.nights) }
                            ?: stringResource(R.string.training_nights_too_few),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.training_nights_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The year-earlier window over this one, on its scale since it is the same quantity: lighter
 * bars beside each bar, or a lighter line under the line. Read by touch like any overlay.
 */
@Composable
private fun previousYearOverlay(data: TileDetailData): List<OverlayLine> {
    // Within this window's own buckets: a year's last, one-day bucket of last year has no
    // bar beside it yet today, and drawn on its own it stood past the plot's edge.
    val first = data.points.firstOrNull()?.time ?: return emptyList()
    val last = data.points.last().time
    val points = data.previousYear?.filter { it.time in first..last }?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val label = stringResource(R.string.legend_previous_year)
    val unit = data.spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty()
    val sessions = data.spec.tile.form == TileSpec.Form.SESSIONS
    return listOf(
        OverlayLine(
            points = points,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PREVIOUS_YEAR_ALPHA),
            format = { v ->
                "$label " + if (sessions) Formatting.duration(Duration.ofMinutes((v * MINUTES_PER_HOUR).toLong()))
                else Formatting.number(v, data.spec.valueDecimals) + unit
            },
            sharesScale = true,
            bars = data.bars,
        ),
    )
}

private const val PREVIOUS_YEAR_ALPHA = 0.7f

private val HALF_DAY: Duration = Duration.ofHours(12)
private const val TRAINING_ALPHA = 0.7f

/**
 * The window's series as a chart, drawn the same wherever it appears.
 *
 * Shared by the detail screen and a large dashboard tile, so a tile's chart is the chart its
 * tap opens -- same marks, same goal line, same bands -- only without the touch readout.
 */
@Composable
internal fun DataLineChart(
    data: TileDetailData,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    fillHeight: Boolean = false,
    compactAxis: Boolean = false,
    sessionIcons: Boolean = true,
    onVisibleRange: ((ClosedRange<Instant>?) -> Unit)? = null,
    /**
     * The time axis, where it must match another chart's -- a comparison. Not written into
     * [data]: its own extent also says "this is a day", which decides the session bands.
     */
    axis: ClosedRange<Instant>? = data.extent,
    onExpand: (() -> Unit)? = null,
    holdSelection: Boolean = false,
    linkedTime: Instant? = null,
    onSelectTime: ((Instant?) -> Unit)? = null,
) {
    LineChart(
        points = data.points,
        // Safe to smooth now that each rise spans the interval the record actually
        // covered: the curve rounds the corners of a real ramp rather than inventing
        // a slope where the data says a vertical jump. The clamp keeps every segment
        // within the two values it joins, so a plateau cannot bulge.
        // Never a running total: between two of its points nothing was recorded, so it rises
        // straight, and the goal's crossing -- found on straight lines -- sits on it. Drawn as
        // a curve, a sparse day's total met the goal hours before the marker (24.10.2026).
        smooth = data.spec.tile.smoothChart && !data.bars && !data.cumulative,
        bars = data.bars,
        rangeBand = data.rangeBand,
        secondaryRangeBand = data.secondaryRangeBand,
        stack = data.stack,
        goal = data.goal,
        goalCrossing = data.goalCrossing,
        unitRes = data.spec.displayUnitRes,
        valueDecimals = data.spec.valueDecimals,
        emptyBuckets = data.emptyBuckets,
        sessions = data.bandSessions,
        referenceRange = data.spec.tile.referenceRange?.let { it.low..it.high },
        zones = data.lineZones,
        secondaryPoints = data.secondaryPoints,
        secondaryZones = data.secondaryZones,
        markReadings = data.spec.tile.markReadings,
        integral = data.spec.tile.integralValues,
        extent = axis,
        interactive = interactive,
        fillHeight = fillHeight,
        compactAxis = compactAxis,
        sessionIcons = sessionIcons,
        // Only the departures are coloured: a week inside its range keeps the line's own
        // colour, so an orange stretch is the one thing that stands out.
        pointColors = data.pointStandings.map { standing ->
            standing?.takeIf { it != HrvStanding.WITHIN }?.color()
        },
        scatter = data.nightPoints,
        baseline = data.baseline,
        overlays = previousYearOverlay(data),
        // Each training day centred on its point, as the day's value sits at its start.
        strip = trainingColor().let { color ->
            data.trainingDays.map { day ->
                val point = day.atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant()
                StripSegment(point.minus(HALF_DAY), point.plus(HALF_DAY), color)
            }
        },
        onVisibleRange = onVisibleRange,
        onExpand = onExpand,
        holdSelection = holdSelection,
        linkedTime = linkedTime,
        onSelectTime = onSelectTime,
        modifier = modifier,
    )
}

/**
 * [DataLineChart] that opens full screen, titled with its type, unit and days. Full screen it
 * fills the page and keeps a tapped value; it does not report its zoom, which is its own and
 * must not move the list on the page behind it.
 */
@Composable
internal fun ExpandableDataChart(
    data: TileDetailData,
    /** The window as the screen names it, for the title. */
    period: String,
    modifier: Modifier = Modifier,
    onVisibleRange: ((ClosedRange<Instant>?) -> Unit)? = null,
    axis: ClosedRange<Instant>? = data.extent,
    /** See [LineChart]'s parameters of the same names; inline only, full screen reads alone. */
    linkedTime: Instant? = null,
    onSelectTime: ((Instant?) -> Unit)? = null,
) {
    if (data.points.isEmpty()) return
    val title = chartTitle(stringResource(data.spec.displayNameRes), data.spec.displayUnitRes, period)
    val description = chartDescription(data, period)
    ExpandableChart(title) { expanded, onExpand ->
        DataLineChart(
            data,
            (if (expanded) Modifier.fillMaxSize() else modifier).semantics { contentDescription = description },
            onVisibleRange = if (expanded) null else onVisibleRange,
            axis = axis,
            fillHeight = expanded,
            onExpand = onExpand,
            holdSelection = expanded,
            linkedTime = if (expanded) null else linkedTime,
            onSelectTime = if (expanded) null else onSelectTime,
        )
    }
}

/**
 * The week's HRV against the usual range, in words and with a coloured dot.
 *
 * Worded as a position, never a verdict -- "below your usual range", not "poor recovery": the
 * range is this app's own arithmetic on the device's readings, and the explanation beneath says
 * so -- without naming a brand, since the readings may come from any wearable.
 */
@Composable
internal fun HrvStatusLine(summary: HrvSummary, dayView: Boolean) {
    val day = summary.day
    val explanation = rememberExplanation("hrv")
    Column(Modifier.padding(top = 4.dp)) {
        // On a day the headline is the night; the week it is judged by is named here.
        if (dayView) {
            day?.weekMean?.let { mean ->
                Text(
                    text = stringResource(R.string.hrv_week_mean, Formatting.number(mean)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val low = day?.usualLow
        val high = day?.usualHigh
        val standing = day?.standing
        // The standing always shows: it is the data. How it is worked out is behind the "i",
        // closed until asked for, as the trend's rule is.
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                if (low != null && high != null && standing != null) {
                    DotText(
                        color = standing.color(),
                        text = stringResource(
                            R.string.hrv_usual,
                            Formatting.number(low),
                            Formatting.number(high),
                            stringResource(standing.labelRes()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.hrv_no_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            InfoToggle(explanation, firstLine = MaterialTheme.typography.bodyMedium)
        }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.hrv_explained),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Green inside the usual range, orange outside it on either side -- as the watch colours it. */
internal fun HrvStanding.color(): Color = when (this) {
    HrvStanding.WITHIN -> ValueZones.ZONE_COLORS[1]
    HrvStanding.BELOW, HrvStanding.ABOVE -> ValueZones.ZONE_COLORS[3]
}

private fun HrvStanding.labelRes(): Int = when (this) {
    HrvStanding.WITHIN -> R.string.hrv_within
    HrvStanding.BELOW -> R.string.hrv_below
    HrvStanding.ABOVE -> R.string.hrv_above
}

/** One swatch and its name; a round swatch for a mark drawn as dots. */
/** A remembered on/off for an optional mark on the chart, the whole row tappable. */
@Composable
internal fun ChartToggle(checked: Boolean, @StringRes label: Int, locked: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        // On the first line, should a long label wrap.
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodyMedium, TOGGLE_SIZE.dp)),
        )
        // The box has no touch target of its own here -- the row is the target -- so nothing
        // keeps the label off it but this gap, as beside the export dialog's radio buttons.
        // Without Pro, named as every Pro part is, and a tap opens the purchase.
        Text(
            text = if (locked) stringResource(R.string.export_premium, stringResource(label)) else stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                start = 12.dp,
                top = firstLineTextInset(MaterialTheme.typography.bodyMedium, TOGGLE_SIZE.dp),
            ),
        )
    }
}

/**
 * The year-earlier window's own figure under the chart, as the headline gives this one's, so
 * the comparison has a number and not only two shapes; or that it is being read, or that
 * nothing was recorded then.
 */
@Composable
internal fun YearEarlierLine(data: TileDetailData) {
    val previous = data.previousYear
    val total = data.previousTotal
    Text(
        text = when {
            previous == null -> stringResource(R.string.chart_previous_year_reading)
            total != null -> stringResource(R.string.chart_previous_year_total, totalText(data, total, data.previousSecondaryTotal)) +
                // A sum over fewer days is no comparison; say so rather than let it read as one.
                if (previous.size < data.points.size) " " + stringResource(R.string.chart_previous_year_partial) else ""
            previous.isEmpty() -> stringResource(R.string.chart_previous_year_none)
            else -> stringResource(R.string.legend_previous_year)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = TOGGLE_SIZE.dp + 12.dp),
    )
}

@Composable
internal fun LegendEntry(color: Color, @StringRes label: Int, round: Boolean = false) {
    DotText(
        color = color,
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        gap = 4.dp,
        shape = if (round) CircleShape else RoundedCornerShape(2.dp),
    )
}

/**
 * Alpha for a band's swatch.
 *
 * Deliberately stronger than the chart's own 0.16: a band covers a wide stretch of plot and
 * reads clearly at that weight, while the same alpha in a 10dp square is a tint barely
 * distinguishable from the surface -- (227,231,254) against (253,251,255). The swatch has to
 * be identifiable as a colour first and an exact match second, so it is the same hue at a
 * weight that survives being small.
 */
private const val LEGEND_BAND_ALPHA = 0.45f

/** The break swatch, as [BreakLegend] draws it under a curve. */
private const val LEGEND_BREAK_ALPHA = 0.3f

/**
 * The chart's fixed sleep blue. Not a theme colour, for the same reason the band is not: it
 * means night, and under dynamic colour a themed hue drifts with the wallpaper.
 */
private val LEGEND_SLEEP = Color(0xFF5C7CFA).copy(alpha = LEGEND_BAND_ALPHA)

/** Matches the chart's own stack weighting, so the swatch reads as the segment it names. */

/**
 * A checkbox or radio button without its own touch target: the 20 dp box and its padding. The
 * detail screen's toggles and the goal and insight dialogs use it alike.
 */
internal const val TOGGLE_SIZE = 24
