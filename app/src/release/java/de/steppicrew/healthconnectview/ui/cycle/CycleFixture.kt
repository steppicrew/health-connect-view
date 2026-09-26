package de.steppicrew.healthconnectview.ui.cycle

import de.steppicrew.healthconnectview.health.CycleRecords
import java.time.ZoneId

/**
 * Release build: there are no synthetic cycles. Absent rather than disabled, as with DebugNav --
 * and no route can ask for it anyway, since release has no way to open one from an intent.
 */
object CycleFixture {
    fun records(zone: ZoneId): CycleRecords? = null
}
