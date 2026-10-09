package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R

/**
 * Says what the grey band over a workout's curve is: a break, found by the movement readings
 * stopping. Without it the band reads as a selection or a rendering fault.
 *
 * The swatch is the band's own colour at a stronger weight, for the reason the detail page's
 * legend gives: at the chart's alpha a 10 dp square is barely distinguishable from the surface.
 */
@Composable
fun BreakLegend(modifier: Modifier = Modifier) {
    DotText(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SWATCH_ALPHA),
        text = stringResource(R.string.session_break_legend),
        style = MaterialTheme.typography.labelSmall,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
        gap = 4.dp,
        shape = RoundedCornerShape(2.dp),
        modifier = modifier,
    )
}

private const val SWATCH_ALPHA = 0.3f
