package de.steppicrew.healthconnectview.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.DayPartSplit
import de.steppicrew.healthconnectview.health.NutrientTotal
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.PartAverage
import de.steppicrew.healthconnectview.health.PersonalRecord
import de.steppicrew.healthconnectview.health.PressureCategory
import de.steppicrew.healthconnectview.health.RecordKind
import de.steppicrew.healthconnectview.health.StreakSummary
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.health.TrendResult
import de.steppicrew.healthconnectview.health.duration
import de.steppicrew.healthconnectview.health.labelRes
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.ui.components.DotText
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.InfoTitle
import de.steppicrew.healthconnectview.ui.components.rememberExplanation
import java.time.Duration
import kotlin.math.roundToInt

// The detail screen's explained boxes: trend, streak, best day, blood pressure by grade and
// time of day, nutrients. Split from TileDetailScreen.kt.

/**
 * What the tile's arrow means, with the numbers behind it.
 *
 * On the phone the arrow on floors pointed up on a day with 4 climbed after a day with 10,
 * and read as wrong: it compares a week with a month, not today with yesterday, and leaves
 * today out altogether. Saying so, with both averages, is what makes the arrow checkable.
 */
@Composable
internal fun TrendExplanation(trend: TrendResult, @StringRes unitRes: Int?, decimals: Int? = null) {
    val unit = unitRes?.let { " " + stringResource(it) }.orEmpty()
    val explanation = rememberExplanation("trend")

    InfoGroup {
        // The headline and the averages always show: they are the data. The rule is behind the
        // "i", closed until asked for, so the numbers are what is seen at first glance.
        InfoTitle(
            title = stringResource(
                when (trend.direction) {
                    Trend.UP -> R.string.trend_title_up
                    Trend.FLAT -> R.string.trend_title_flat
                    Trend.DOWN -> R.string.trend_title_down
                },
            ),
            explanation = explanation,
            icon = trend.direction.icon,
        )
        Text(
            text = stringResource(
                R.string.trend_numbers,
                Formatting.number(trend.recent, decimals) + unit,
                Formatting.number(trend.baseline, decimals) + unit,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.trend_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The tile's streak in words, with the year's longest run and the rule behind the "i": that a
 * day without data bridges a goal's run rather than breaking it is what a reader cannot guess,
 * and what makes a count after a forgotten watch believable. Nothing where neither run reaches
 * two days.
 */
@Composable
internal fun StreakExplanation(summary: StreakSummary, active: Boolean) {
    val longest = summary.longest?.takeIf { it.count >= MIN_STREAK }
    val current = summary.current.takeIf { it >= MIN_STREAK }
    val title = when {
        current != null -> pluralStringResource(streakPlural(active), current, current)
        longest != null -> pluralStringResource(R.plurals.streak_longest_title, longest.count, longest.count)
        else -> return
    }
    val explanation = rememberExplanation("streak")
    val zone = HealthRepository.DEFAULT_ZONE
    val range = longest?.let {
        stringResource(
            R.string.streak_range,
            Formatting.date(it.first.atStartOfDay(zone).toInstant(), zone),
            Formatting.date(it.last.atStartOfDay(zone).toInstant(), zone),
        )
    }
    InfoGroup {
        InfoTitle(
            title = title,
            explanation = explanation,
            icon = Icons.Default.EmojiEvents,
        )
        when {
            // The current run is the year's longest: saying both would repeat the number.
            current != null && (longest == null || current >= longest.count) ->
                stringResource(R.string.streak_is_longest)
            current != null && longest != null ->
                pluralStringResource(R.plurals.streak_longest, longest.count, longest.count, range.orEmpty())
            else -> range
        }?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(if (active) R.string.streak_active_rule else R.string.streak_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HistoryLimitNote()
        }
    }
}

/**
 * The type's personal best over the past year: which kind of best, its value, its day.
 *
 * Worded as what the data holds, never as praise or a verdict -- a "lowest" resting rate is a
 * position, not a diagnosis. The rule behind it, including the 30 days without the history
 * permission, is behind the "i", as for the streak.
 */
@Composable
internal fun RecordExplanation(record: PersonalRecord, spec: RecordTypeSpec<*>) {
    val explanation = rememberExplanation("record")
    val zone = HealthRepository.DEFAULT_ZONE
    val title = stringResource(
        when (record.kind) {
            RecordKind.MOST -> R.string.record_title_most
            RecordKind.LOWEST -> R.string.record_title_lowest
            RecordKind.HIGHEST -> R.string.record_title_highest
            RecordKind.LONGEST -> R.string.record_title_longest
        },
    )
    val value = if (record.kind == RecordKind.LONGEST) {
        Formatting.duration(Duration.ofSeconds(record.value.toLong()))
    } else {
        Formatting.number(record.value, spec.valueDecimals) +
            (spec.displayUnitRes?.let { " " + stringResource(it) } ?: "")
    }
    InfoGroup {
        InfoTitle(
            title = title,
            explanation = explanation,
            icon = Icons.Default.MilitaryTech,
        )
        Text(
            text = stringResource(
                R.string.record_value_on,
                value,
                Formatting.date(record.date.atStartOfDay(zone).toInstant(), zone),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Not on the record's own day: there the button would open what is already shown.
        val jump = LocalDayJump.current
        if (jump.shown != record.date) {
            TextButton(onClick = { jump.onShow(record.date) }) {
                Text(stringResource(R.string.record_show))
            }
        }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.record_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HistoryLimitNote()
        }
    }
}

/** "132/85" where a second value exists, whole numbers as a cuff shows them; else the value. */
internal fun pressureText(first: Double, second: Double?, decimals: Int? = null): String =
    if (second == null) Formatting.number(first, decimals) else "${first.roundToInt()}/${second.roundToInt()}"

private val PressureCategory.color: Color get() = ValueZones.ZONE_COLORS[ordinal]

/** The grade in words beside its colour, so the colour is never the only signal. */
@Composable
internal fun CategoryBadge(category: PressureCategory) {
    DotText(
        color = category.color,
        text = stringResource(category.labelRes()),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun PressureLegend() {
    Column(Modifier.padding(top = 6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PressureCategory.entries.forEach { category ->
                LegendEntry(color = category.color, label = category.labelRes())
            }
        }
        Text(
            text = stringResource(R.string.bp_legend_lines),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Morning and evening blood pressure side by side, rather than one blended average that hides
 * a morning surge. The rule for where the day splits is behind the "i".
 */
@Composable
internal fun DayPartsSection(split: DayPartSplit) {
    val explanation = rememberExplanation("dayparts")

    InfoGroup {
        InfoTitle(stringResource(R.string.bp_parts_title), explanation)
        DayPartRow(stringResource(R.string.bp_part_morning), split.morning)
        DayPartRow(stringResource(R.string.bp_part_evening), split.evening)
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.bp_parts_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the meals of the window held beyond energy: each nutrient's total on a day; over
 * several, its mean per day recorded, with the total beneath. Totals only, no targets: what a day
 * should hold depends on the person. How the figures are made is behind the "i".
 */
@Composable
internal fun NutrientsSection(nutrients: List<NutrientTotal>) {
    val explanation = rememberExplanation("nutrients")
    InfoGroup {
        InfoTitle(stringResource(R.string.nutrients_title), explanation)
        nutrients.forEach { row ->
            val unit = stringResource(if (row.nutrient.milligrams) R.string.unit_mg else R.string.unit_g)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(row.nutrient.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                // Over several days the day's mean leads: four weeks' 1,820 g of fat says little
                // until it is a day's 65 g. The total follows, with the days it covers.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (row.days > 1) {
                            stringResource(R.string.nutrient_per_day, nutrientAmount(row.perDay) + Formatting.UNIT_SPACE + unit)
                        } else {
                            nutrientAmount(row.total) + Formatting.UNIT_SPACE + unit
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (row.days > 1) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.nutrients_total_days,
                                row.days,
                                nutrientAmount(row.total) + Formatting.UNIT_SPACE + unit,
                                row.days,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (explanation.expanded == true) {
            Text(
                text = stringResource(R.string.nutrients_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Whole grams or milligrams, with a decimal below ten, where 2.4 g of salt would read as 2. */
private fun nutrientAmount(value: Double): String = Formatting.number(value, if (value < 10) 1 else 0)

@Composable
private fun DayPartRow(label: String, average: PartAverage?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (average == null) {
            Text(
                text = stringResource(R.string.bp_part_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DotText(
                color = pressureCategory(average.systolic, average.diastolic).color,
                text = pluralStringResource(
                    R.plurals.bp_part_value,
                    average.count,
                    // Whole numbers, as every cuff and every doctor gives them.
                    "${average.systolic.roundToInt()}/${average.diastolic.roundToInt()}",
                    average.count,
                ),
                style = MaterialTheme.typography.bodyMedium,
                textColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
