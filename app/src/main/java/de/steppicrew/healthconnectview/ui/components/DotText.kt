package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * A coloured mark before a text, centred on the text's *first* line.
 *
 * Centring on the whole text put the mark between the lines as soon as the text wrapped --
 * "Übliche Spanne 34,1-49,3 ms · in deinem üblichen / Bereich" had its dot floating beside the
 * gap -- so it no longer read as belonging to the first word. Used for every mark-and-label
 * pair, so they all line up the same way.
 */
@Composable
fun DotText(
    color: Color,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    dotSize: Dp = 10.dp,
    gap: Dp = 6.dp,
    shape: Shape = CircleShape,
) {
    val lineHeight = with(LocalDensity.current) {
        (if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * FALLBACK_LINE_HEIGHT).toDp()
    }
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = ((lineHeight - dotSize) / 2).coerceAtLeast(0.dp), end = gap)
                .size(dotSize)
                .background(color, shape),
        )
        Text(text = text, style = style, color = textColor)
    }
}

/** Material's line heights run about 1.2 to 1.5 times the font size; used when none is set. */
private const val FALLBACK_LINE_HEIGHT = 1.3f
