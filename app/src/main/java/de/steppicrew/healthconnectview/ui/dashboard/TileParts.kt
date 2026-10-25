package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.components.DotText
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.PluralsRes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import kotlin.math.floor
import kotlin.math.roundToInt
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.registry.Formatting
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// The small pieces of a tile: its value and goal share, the trend and streak marks, and the
// locked, loading and failed states. Split from DashboardScreen.kt.

/**
 * Which way the last week went against the month before it. Drawn in the tile's quiet colour,
 * never red or green: up is good for steps and bad for resting heart rate, and the arrow does
 * not know which it is looking at.
 */
@Composable
internal fun TrendMark(trend: Trend) {
    Icon(
        imageVector = trend.icon,
        contentDescription = stringResource(trend.description),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(TREND_ICON.dp),
    )
}

/**
 * How many days in a row the goal was met, or an activity recorded where [active]: a trophy
 * and the count. Not a flame, which beside "kcal" read as calories. In the tile's quiet
 * colour like the arrow, so the ring stays the loudest thing on it.
 *
 * The gap before it only where something precedes it: on the activities tile it opens the
 * line, and a gap there set it in from the tile's other text.
 */
@Composable
internal fun StreakMark(days: Int, active: Boolean, first: Boolean) {
    val description = pluralStringResource(streakPlural(active), days, days)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = if (first) 0.dp else 4.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(TREND_ICON.dp),
        )
        Text(
            text = Formatting.integer(days.toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The arrow for a trend, shared by the tile and the detail view so the two read alike. */
internal val Trend.icon: ImageVector
    get() = when (this) {
        Trend.UP -> Icons.AutoMirrored.Filled.TrendingUp
        Trend.FLAT -> Icons.AutoMirrored.Filled.TrendingFlat
        Trend.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
    }

@get:StringRes
internal val Trend.description: Int
    get() = when (this) {
        Trend.UP -> R.string.trend_up
        Trend.FLAT -> R.string.trend_flat
        Trend.DOWN -> R.string.trend_down
    }

/**
 * A tile whose type is not granted, with the way to change that on the tile itself.
 *
 * Tapping the tile opens its detail, which only repeats "not allowed"; the grant lives two
 * screens away in settings. The button goes straight to the permission list, which is the only
 * thing a locked tile can usefully lead to.
 */
@Composable
internal fun LockedTile(onGrantAccess: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(LOCK_ICON.dp),
        )
        Text(
            text = stringResource(R.string.tile_locked),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onGrantAccess) {
            Text(stringResource(R.string.tile_grant))
        }
    }
}

/** A tile whose read is still running: small, so a full dashboard does not spin loudly. */
@Composable
internal fun TileLoading() {
    val label = stringResource(R.string.tile_loading)
    CircularProgressIndicator(
        modifier = Modifier
            .size(TILE_SPINNER.dp)
            .semantics { contentDescription = label },
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A tile whose read threw; the next return to the app reads it again. */
@Composable
internal fun TileFailed() {
    Text(
        text = stringResource(R.string.tile_failed),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun TileValue(data: TileData, large: Boolean = false) {
    // A tall tile's number grows with it; left at tile size it sat lost in the middle of a
    // ring four times the area.
    val valueStyle = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium
    when {
        // "Not allowed to look" and "nothing here" need different words: showing a dash for a
        // locked type would read as an empty day rather than a missing permission.
        !data.granted -> Text(
            text = stringResource(R.string.tile_locked),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Never the dash: a tile still reading looked exactly like a day with nothing in it.
        data.loading -> TileLoading()

        data.failed -> TileFailed()

        // A missing value is not zero. Rendering null as "0" would claim the user took no
        // steps when in fact nothing was recorded.
        data.value == null -> Text(
            text = stringResource(R.string.tile_no_data),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        data.valueDate != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Muted and dated: a carried weight is the latest known, not today's, and must not
            // read as a measurement taken on the day on screen.
            Text(
                text = tileValueText(data.value, data.secondaryValue, data.spec.valueDecimals),
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.tile_value_as_of,
                    data.valueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The week's HRV: its standing as a dot, as the detail screen shows it, and a note
        // that the number is seven nights, not the latest reading.
        data.spec.tile.nightlyStatus -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val standing = data.standing
            if (standing != null) {
                DotText(
                    color = standing.color(),
                    text = Formatting.number(data.value, data.spec.valueDecimals),
                    style = valueStyle,
                    textColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = Formatting.number(data.value, data.spec.valueDecimals),
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.tile_hrv_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Two values read as one: the grade's colour beside them, as on the detail screen.
        data.secondaryValue != null -> DotText(
            color = ValueZones.ZONE_COLORS[pressureCategory(data.value, data.secondaryValue).ordinal],
            text = tileValueText(data.value, data.secondaryValue, data.spec.valueDecimals),
            style = valueStyle,
            textColor = MaterialTheme.colorScheme.onSurface,
        )

        else -> Text(
            text = Formatting.number(data.value, data.spec.valueDecimals),
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * "107 % of\n2,990": how far the day got against its goal, printed because a ring past its
 * goal cannot say by how much on its own, and an adaptive goal is not one the user typed.
 * Rounded down, so a day short of its goal never reads 100 %.
 */
@Composable
internal fun GoalShare(data: TileData) {
    val goal = data.dayGoal ?: return
    val progress = data.progress ?: return
    val percent = Formatting.percent(floor(progress * PERCENT) / PERCENT, LocalConfiguration.current.locales[0])
    Text(
        text = stringResource(R.string.tile_goal_share, percent, Formatting.number(goal, data.spec.valueDecimals)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private const val PERCENT = 100.0

/** "128/82" for a pair, whole numbers as a cuff shows them; the plain number otherwise. */
private fun tileValueText(value: Double, secondary: Double?, decimals: Int? = null): String =
    if (secondary == null) Formatting.number(value, decimals) else "${value.roundToInt()}/${secondary.roundToInt()}"

@Composable
internal fun dayLabel(date: LocalDate): String =
    if (date == LocalDate.now()) {
        stringResource(R.string.dashboard_title)
    } else {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

/** The most of a wide tile's width the number takes beside its chart, and the gap between. */
internal const val VALUE_MAX_SHARE = 0.5f
internal const val VALUE_GAP = 12

/** How many activity icons fit on a tile face beside the count without crowding it. */
/** Just enough to recognise the app; the tile has little room to spare. */
internal const val TILE_SOURCE_ICON = 16
private const val TREND_ICON = 16

/** The words for a run of [days]: of the goal met, or of days with an activity. */
@PluralsRes
internal fun streakPlural(active: Boolean): Int = if (active) R.plurals.streak_active_days else R.plurals.streak_days

/** One day is not a run; the count shows from two. */
internal const val MIN_STREAK = 2
private const val LOCK_ICON = 20
internal const val LOCK_BADGE = 10

/** How faint a locked control's own icon is, so the padlock reads as its state. */
internal const val LOCKED_ALPHA = 0.45f
internal const val TILE_SOURCE_ICON_PX = 48

internal const val TILE_ICONS = 3
internal const val TILE_ICON_SIZE = 14

/** The spinner in a tile still loading, about the height of a line of its value. */
private const val TILE_SPINNER = 24
