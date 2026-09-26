package de.steppicrew.healthconnectview.ui.cycle

import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import de.steppicrew.healthconnectview.debug.CycleSeeder
import de.steppicrew.healthconnectview.health.CycleRecords
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug build: synthetic cycles held in memory, for checking the overview on a real phone.
 *
 * Seeding would write fake periods into the owner's own Health Connect store, where every app
 * with menstruation access -- a cycle tracker included -- would read them as real. This draws
 * the seeder's cycles without touching the store at all:
 *
 *     adb shell am start -n <pkg>/de.steppicrew.healthconnectview.MainActivity \
 *         -e route "'cycle?fixture=true'"
 *
 * It exercises the drawing, not the reading: permissions and Health Connect are bypassed.
 */
object CycleFixture {
    fun records(zone: ZoneId): CycleRecords {
        val all = CycleSeeder.records(LocalDate.now(zone), zone)
        return CycleRecords(
            periods = all.filterIsInstance<MenstruationPeriodRecord>(),
            flows = all.filterIsInstance<MenstruationFlowRecord>(),
            spotting = all.filterIsInstance<IntermenstrualBleedingRecord>(),
            ovulationTests = all.filterIsInstance<OvulationTestRecord>(),
            mucus = all.filterIsInstance<CervicalMucusRecord>(),
            temperatures = all.filterIsInstance<BasalBodyTemperatureRecord>(),
        )
    }
}
