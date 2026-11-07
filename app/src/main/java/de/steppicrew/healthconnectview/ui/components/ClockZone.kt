package de.steppicrew.healthconnectview.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.ZoneId

/**
 * The zone clock times on a chart are read in: the phone's, except on a workout's own screen,
 * where it is the offset the workout was recorded in, so the axis agrees with the times above
 * it after the phone has changed zone.
 */
val LocalClockZone = staticCompositionLocalOf<ZoneId> { ZoneId.systemDefault() }
