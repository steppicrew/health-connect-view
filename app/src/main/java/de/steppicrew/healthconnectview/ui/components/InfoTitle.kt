package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A section's title with the "i" of its explanation at the end, and an [icon] before it where
 * one says what the section is about at a glance -- the trend's arrow, a cup for a streak. The
 * icon is taller than a line, so the title and the "i" come down to its middle; without one
 * both sit on the title's first line, however far it wraps.
 */
@Composable
fun InfoTitle(
    title: String,
    explanation: ExplanationState?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val style = MaterialTheme.typography.titleSmall
    val inset = if (icon != null) firstLineTextInset(style, TITLE_ICON.dp) else 0.dp
    Row(modifier, verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp, top = firstLineInset(style, TITLE_ICON.dp)),
            )
        }
        Text(text = title, style = style, modifier = Modifier.weight(1f).padding(top = inset))
        if (explanation != null) InfoToggle(explanation, Modifier.padding(top = inset), firstLine = style)
    }
}

private const val TITLE_ICON = 24
