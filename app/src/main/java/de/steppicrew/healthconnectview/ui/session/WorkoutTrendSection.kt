package de.steppicrew.healthconnectview.ui.session

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.health.hasSpeed
import de.steppicrew.healthconnectview.health.readsPace
import de.steppicrew.healthconnectview.registry.AxisScale
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.Quantity
import de.steppicrew.healthconnectview.ui.components.exerciseNameRes
import de.steppicrew.healthconnectview.ui.components.labelFor
import java.time.Instant
import de.steppicrew.healthconnectview.registry.KMH_PER_MS
import de.steppicrew.healthconnectview.registry.METRES_PER_KM
import de.steppicrew.healthconnectview.registry.METRES_PER_MILE

/**
 * One kind's course over the year (step 81, Pro), above its list: a dot per workout for its
 * average speed -- a pace for running, walking and hiking -- and one for its mean heart rate,
 * each on a chart of its own, so each has its one labelled scale. The count names the
 * exercise types it matched, since writers disagree about them: the same indoor ride arrives
 * as biking from a watch and as stationary biking from the machine.
 */
@Composable
internal fun WorkoutTrendSection(trend: WorkoutTrend, modifier: Modifier = Modifier) {
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    val title = stringResource(R.string.workouts_trend_title)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!pro.allows(Feature.WORKOUT_TRENDS)) {
        val activity = LocalActivity.current
        Row(
            verticalAlignment = Alignment.Top,
            modifier = modifier
                .fillMaxWidth()
                .clickable { activity?.let(AppEntitlements.current::buy) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.export_premium, title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Lock, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        }
        return
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        val matched = trend.sessions.mapNotNull { exerciseNameRes(it.exerciseType) }.distinct().map { stringResource(it) }
            .ifEmpty { listOf(stringResource(labelFor(trend.family))) }
        Text(
            text = pluralStringResource(R.plurals.workouts_trend_count, trend.sessions.size, trend.sessions.size, matched.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        val points = trend.points
        if (points == null) {
            CircularProgressIndicator(Modifier.padding(vertical = 16.dp).size(24.dp), strokeWidth = 2.dp)
            return@Column
        }
        if (trend.family.hasSpeed()) {
            val speeds = points.mapNotNull { p -> p.metresPerSecond?.let { p.start to it } }
            if (trend.family.readsPace()) {
                // Seconds per km or mile; the faster up, as on a runner's chart.
                val metres = if (Quantity.DISTANCE.alternateShown) METRES_PER_MILE else METRES_PER_KM
                val paces = speeds.map { (t, v) -> t to metres / v / SECONDS_PER_MINUTE }.filter { it.second < MAX_PACE_MINUTES }
                DotChart(stringResource(R.string.workouts_trend_pace, Formatting.paceUnit()), paces, inverted = true) { Formatting.minutes(it) }
            } else {
                val kmh = speeds.map { (t, v) -> t to Quantity.SPEED.convert(v * KMH_PER_MS) }
                DotChart(stringResource(R.string.workouts_trend_speed, stringResource(Quantity.SPEED.unitRes())), kmh) { Formatting.number(it, 1) }
            }
        }
        val hearts = points.mapNotNull { p -> p.heartRate?.let { p.start to it } }
        DotChart(stringResource(R.string.workouts_trend_heart, stringResource(R.string.unit_bpm)), hearts) { Formatting.number(it, 0) }
        Text(stringResource(R.string.workouts_trend_note), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Dots over the year, one per workout, on one labelled value scale with the first and last
 * date beneath. Nothing joins them: a line would claim the days between had a speed. Two dots
 * at least, else there is no course to show.
 */
@Composable
private fun DotChart(label: String, values: List<Pair<Instant, Double>>, inverted: Boolean = false, format: (Double) -> String) {
    if (values.size < 2) return
    val dot = MaterialTheme.colorScheme.primary
    val rule = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val low = values.minOf { it.second }
    val high = values.maxOf { it.second }
    val scale = AxisScale.of(low, high)
    val first = values.minOf { it.first }
    val last = values.maxOf { it.first }
    val description = label + ". " + stringResource(R.string.chart_description_range, format(low), format(high))
    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT.dp).semantics { contentDescription = description }) {
        val guideLabels = scale.guides.map { measurer.measure(format(it), labelStyle) }
        val axis = (guideLabels.maxOfOrNull { it.size.width } ?: 0) + 6.dp.toPx()
        val dateFirst = measurer.measure(Formatting.date(first), labelStyle)
        val dateLast = measurer.measure(Formatting.date(last), labelStyle)
        val bottom = size.height - dateFirst.size.height - 4.dp.toPx()
        val top = labelStyle.fontSize.toPx() / 2
        val radius = DOT_RADIUS.dp.toPx()
        val left = axis + radius
        val right = size.width - radius
        fun y(v: Double): Float {
            val f = ((v - scale.min) / (scale.max - scale.min)).toFloat()
            return if (inverted) top + (bottom - top) * f else bottom - (bottom - top) * f
        }
        fun x(t: Instant): Float {
            val span = (last.toEpochMilli() - first.toEpochMilli()).coerceAtLeast(1)
            return left + (right - left) * ((t.toEpochMilli() - first.toEpochMilli()).toFloat() / span)
        }
        scale.guides.forEachIndexed { index, guide ->
            drawLine(rule, Offset(axis, y(guide)), Offset(size.width, y(guide)), strokeWidth = 1.dp.toPx())
            val text = guideLabels[index]
            drawText(text, topLeft = Offset(axis - 6.dp.toPx() - text.size.width, y(guide) - text.size.height / 2))
        }
        values.forEach { (t, v) -> drawCircle(dot, radius, Offset(x(t), y(v))) }
        drawText(dateFirst, topLeft = Offset(axis, bottom + 4.dp.toPx()))
        drawText(dateLast, topLeft = Offset(size.width - dateLast.size.width, bottom + 4.dp.toPx()))
    }
}

private const val CHART_HEIGHT = 120
private const val DOT_RADIUS = 3.5f
private const val SECONDS_PER_MINUTE = 60.0

/** A pause is hours per km; past this a "pace" says nothing, as `Formatting.pace` holds. */
private const val MAX_PACE_MINUTES = 30.0
