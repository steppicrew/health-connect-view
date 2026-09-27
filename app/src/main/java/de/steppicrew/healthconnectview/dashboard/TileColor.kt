package de.steppicrew.healthconnectview.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** A tile's background and the text drawn on it, for one theme. */
data class TilePair(val container: Color, val content: Color)

/**
 * A tile's colour: the theme's own, or one of a small palette.
 *
 * A palette rather than free pickers for background and text. Each entry is a pair defined for
 * light and dark, so text stays readable in both and a colour chosen in daylight still works
 * at night. Fixed hues rather than the theme's containers, which follow the wallpaper under
 * dynamic colour: `TileColorTest` checks every pair's text contrast and that each value-zone
 * colour stays clearly apart from each background, which it could not do for colours that
 * change with the wallpaper.
 *
 * Only the card and its plain text take the colour. What carries meaning on a tile -- zone
 * colours, the goal ring, the trend arrow, the source icon -- keeps its own.
 *
 * Tones 90 and 10 of each hue in light, 30 and 90 in dark, as Material's own containers are
 * built.
 */
enum class TileColor(val light: TilePair?, val dark: TilePair?) {
    /** The theme's tile colour; no pair, so it follows dynamic colour as before. */
    DEFAULT(null, null),
    BLUE(TilePair(Color(0xFFD6E3FF), Color(0xFF001B3E)), TilePair(Color(0xFF284777), Color(0xFFD6E3FF))),
    TEAL(TilePair(Color(0xFFCCE8E6), Color(0xFF051F1F)), TilePair(Color(0xFF334B4A), Color(0xFFCCE8E6))),
    GREEN(TilePair(Color(0xFFC8F0CF), Color(0xFF002111)), TilePair(Color(0xFF1E5130), Color(0xFFC8F0CF))),
    AMBER(TilePair(Color(0xFFFFE08B), Color(0xFF241A00)), TilePair(Color(0xFF574500), Color(0xFFFFE08B))),
    CORAL(TilePair(Color(0xFFFFDBCF), Color(0xFF380D00)), TilePair(Color(0xFF73341E), Color(0xFFFFDBCF))),
    ROSE(TilePair(Color(0xFFFFD9E2), Color(0xFF3E001D)), TilePair(Color(0xFF7B2949), Color(0xFFFFD9E2))),
    PURPLE(TilePair(Color(0xFFEADDFF), Color(0xFF21005D)), TilePair(Color(0xFF4F378B), Color(0xFFEADDFF))),
    GREY(TilePair(Color(0xFFC4C7C9), Color(0xFF1A1C1E)), TilePair(Color(0xFF45474A), Color(0xFFE2E2E6))),
    ;

    fun pair(dark: Boolean): TilePair? = if (dark) this.dark else light
}

/**
 * Secondary text on the tile -- its title, the unit, axis values -- as the content colour
 * faded a quarter towards the background, the way the theme's own `onSurfaceVariant` sits
 * beside `onSurface`. `TileColorTest` holds it to the contrast body text needs.
 */
val TilePair.muted: Color get() = lerp(content, container, MUTED_SHARE)

private const val MUTED_SHARE = 0.25f
