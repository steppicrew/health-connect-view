package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import de.steppicrew.healthconnectview.dashboard.TileColor
import de.steppicrew.healthconnectview.dashboard.TilePair
import de.steppicrew.healthconnectview.dashboard.muted

/**
 * Draws [content] in [color]: the theme with its surfaces and the text on them replaced by
 * the tile's pair, so everything inside that reads the theme -- title, value, unit, axis
 * values, a ring's track -- follows without each part knowing about tile colours. Accents
 * such as the goal ring and fixed colours such as the zones are left as they are.
 *
 * Light or dark is read off the theme in force rather than the system, since the app has
 * its own theme setting.
 */
@Composable
internal fun TileColored(color: TileColor, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pair = color.pair(dark = scheme.surface.luminance() < DARK_LUMINANCE)
    if (pair == null) {
        content()
    } else {
        MaterialTheme(
            colorScheme = scheme.tinted(pair),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

private fun ColorScheme.tinted(pair: TilePair): ColorScheme = copy(
    surface = pair.container,
    surfaceContainerHighest = pair.container,
    onSurface = pair.content,
    onSurfaceVariant = pair.muted,
    surfaceVariant = lerp(pair.container, pair.content, TRACK_SHARE),
    outlineVariant = lerp(pair.container, pair.content, OUTLINE_SHARE),
)

private const val DARK_LUMINANCE = 0.5f

/** How far a ring's track and a chart's grid lines move from the background towards the text. */
private const val TRACK_SHARE = 0.12f
private const val OUTLINE_SHARE = 0.2f
