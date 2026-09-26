package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.SleepStage
import de.steppicrew.healthconnectview.health.StageKind
import de.steppicrew.healthconnectview.health.stageTotals
import de.steppicrew.healthconnectview.registry.Formatting
import java.time.Duration
import java.time.Instant

/**
 * A night drawn as its stages: one lane per stage, waking at the top, deep sleep at the bottom,
 * across the night's own start and end. Below it the time in each stage, which is also the
 * legend -- the colours are named once, next to the numbers they stand for.
 *
 * Only what the writer recorded. No score: the one the watch shows is its maker's own
 * calculation and is not in Health Connect, and inventing one would be this app's claim about
 * how well someone slept.
 */
@Composable
fun Hypnogram(stages: List<SleepStage>, start: Instant, end: Instant, modifier: Modifier = Modifier) {
    val totals = stageTotals(stages)
    val lanes = StageKind.entries.filter { kind -> totals.any { it.first == kind } }
    val names = totals.associate { (kind, _) -> kind to stringResource(labelOf(kind)) }
    val description = totals.joinToString(", ") { (kind, total) ->
        names.getValue(kind) + " " + Formatting.duration(total)
    }
    val grid = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height((LANE_HEIGHT * lanes.size).dp)
                .semantics { contentDescription = description },
        ) {
            val span = Duration.between(start, end).toMillis().toFloat().coerceAtLeast(1f)
            val lane = size.height / lanes.size
            lanes.indices.forEach { index ->
                val y = lane * index + lane / 2
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            stages.forEach { stage ->
                val row = lanes.indexOf(stage.kind).takeIf { it >= 0 } ?: return@forEach
                val left = Duration.between(start, stage.start).toMillis() / span * size.width
                val right = Duration.between(start, stage.end).toMillis() / span * size.width
                drawRect(
                    color = colorOf(stage.kind),
                    topLeft = Offset(left.coerceIn(0f, size.width), lane * row + lane * BAR_INSET),
                    size = Size(
                        (right - left).coerceAtLeast(1f).coerceAtMost(size.width - left.coerceAtLeast(0f)),
                        lane * (1 - 2 * BAR_INSET),
                    ),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            totals.forEach { (kind, total) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(colorOf(kind), CircleShape),
                    )
                    Text(
                        text = names.getValue(kind) + " " + Formatting.duration(total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

private fun labelOf(kind: StageKind): Int = when (kind) {
    StageKind.AWAKE -> R.string.stage_awake
    StageKind.REM -> R.string.stage_rem
    StageKind.LIGHT -> R.string.stage_light
    StageKind.DEEP -> R.string.stage_deep
    StageKind.ASLEEP -> R.string.stage_asleep
}

/**
 * Fixed colours rather than theme ones: under dynamic colour every scheme colour can come out a
 * shade of the wallpaper, and deep sleep and REM in two near-identical teals would be one stage
 * to the eye. Blues deepen with depth; REM and waking stand apart from them.
 */
private fun colorOf(kind: StageKind): Color = when (kind) {
    StageKind.AWAKE -> Color(0xFFF08A4B)
    StageKind.REM -> Color(0xFFB57EDC)
    StageKind.LIGHT -> Color(0xFF5B9BE6)
    StageKind.DEEP -> Color(0xFF2B4FA8)
    StageKind.ASLEEP -> Color(0xFF7F8FA6)
}

private const val LANE_HEIGHT = 16
private const val BAR_INSET = 0.12f
