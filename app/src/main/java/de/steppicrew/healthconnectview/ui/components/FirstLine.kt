package de.steppicrew.healthconnectview.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified

/**
 * The top padding that centres an icon of [size] on the first line of text in [style] beside
 * it, for a row aligned to the top.
 *
 * Centred on the whole row instead, an icon drops between the lines once its text wraps, and
 * belongs to neither; on the first line it marks where the entry starts, as a bullet does.
 */
@Composable
fun firstLineInset(style: TextStyle, size: Dp): Dp {
    if (style.lineHeight.isUnspecified) return 0.dp
    val line = with(LocalDensity.current) { style.lineHeight.toDp() }
    return ((line - size) / 2).coerceAtLeast(0.dp)
}

/**
 * The other way round, for a control taller than a line -- a switch, a checkbox's touch
 * target: the top padding for the text, so its first line is centred on a control of
 * [height] at the top of the row.
 */
@Composable
fun firstLineTextInset(style: TextStyle, height: Dp): Dp {
    if (style.lineHeight.isUnspecified) return 0.dp
    val line = with(LocalDensity.current) { style.lineHeight.toDp() }
    return ((height - line) / 2).coerceAtLeast(0.dp)
}
