package de.steppicrew.healthconnectview.debug

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Temperature
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Synthetic cycles for the cycle overview. Debug builds only.
 *
 * Separate from [SampleDataSeeder] because it needs half a year where that one needs a month:
 * a single cycle aligned on day 1 exercises none of what the overview is for. Lengths vary so
 * the rows differ, and period records are written for only every other cycle -- some apps write
 * only daily flow -- so both sources of bleeding are exercised.
 */
object CycleSeeder {

    private val SEEDED_TYPES = listOf(
        MenstruationPeriodRecord::class,
        MenstruationFlowRecord::class,
        IntermenstrualBleedingRecord::class,
        OvulationTestRecord::class,
        CervicalMucusRecord::class,
        BasalBodyTemperatureRecord::class,
    )

    private val LENGTHS = listOf(28, 30, 27, 31, 29, 28)
    private const val PERIOD_DAYS = 5

    suspend fun seed(client: HealthConnectClient) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val random = Random(seed = 7)
        val first = today.minusDays(LENGTHS.sum().toLong() - 10)

        fun metadata() = Metadata.manualEntry()
        fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone)

        val records = buildList<Record> {
            var start = first
            LENGTHS.forEachIndexed { cycleIndex, length ->
                val ovulationDay = length - 14
                if (cycleIndex % 2 == 0) {
                    val from = at(start, 0)
                    val to = at(start.plusDays(PERIOD_DAYS.toLong()), 0)
                    if (!to.toLocalDate().isAfter(today)) add(
                        MenstruationPeriodRecord(
                            startTime = from.toInstant(), startZoneOffset = from.offset,
                            endTime = to.toInstant(), endZoneOffset = to.offset,
                            metadata = metadata(),
                        ),
                    )
                }
                (0 until length).forEach dayLoop@{ day ->
                    val date = start.plusDays(day.toLong())
                    if (date.isAfter(today)) return@dayLoop
                    val morning = at(date, 7)
                    val offset = morning.offset
                    val time = morning.toInstant()
                    if (day < PERIOD_DAYS) add(
                        MenstruationFlowRecord(
                            time = time, zoneOffset = offset, metadata = metadata(),
                            flow = when (day) {
                                0 -> MenstruationFlowRecord.FLOW_MEDIUM
                                1 -> MenstruationFlowRecord.FLOW_HEAVY
                                2 -> MenstruationFlowRecord.FLOW_MEDIUM
                                else -> MenstruationFlowRecord.FLOW_LIGHT
                            },
                        ),
                    )
                    // A small rise after ovulation, the shape a basal chart is read for.
                    val shift = if (day > ovulationDay) 0.3 else 0.0
                    add(
                        BasalBodyTemperatureRecord(
                            time = time.minusSeconds(3600), zoneOffset = offset, metadata = metadata(),
                            temperature = Temperature.celsius(36.3 + shift + random.nextDouble(-0.08, 0.08)),
                        ),
                    )
                    if (day in ovulationDay - 3..ovulationDay + 1) add(
                        OvulationTestRecord(
                            time = time, zoneOffset = offset, metadata = metadata(),
                            result = if (day in ovulationDay - 1..ovulationDay) {
                                OvulationTestRecord.RESULT_POSITIVE
                            } else {
                                OvulationTestRecord.RESULT_NEGATIVE
                            },
                        ),
                    )
                    if (day in ovulationDay - 4..ovulationDay) add(
                        CervicalMucusRecord(
                            time = time, zoneOffset = offset, metadata = metadata(),
                            appearance = if (day >= ovulationDay - 1) {
                                CervicalMucusRecord.APPEARANCE_EGG_WHITE
                            } else {
                                CervicalMucusRecord.APPEARANCE_CREAMY
                            },
                        ),
                    )
                    if (cycleIndex == 2 && day == ovulationDay + 4) add(
                        IntermenstrualBleedingRecord(time = time, zoneOffset = offset, metadata = metadata()),
                    )
                }
                start = start.plusDays(length.toLong())
            }
        }

        val window = TimeRangeFilter.between(
            first.minusDays(1).atStartOfDay(zone).toInstant(),
            Instant.now(),
        )
        SEEDED_TYPES.forEach { type -> runCatching { client.deleteRecords(type, window) } }
        records.chunked(500).forEach { client.insertRecords(it) }
    }
}
