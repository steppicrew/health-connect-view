package de.steppicrew.healthconnectview

import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Temperature
import de.steppicrew.healthconnectview.health.CycleDay
import de.steppicrew.healthconnectview.health.CycleRecords
import de.steppicrew.healthconnectview.health.buildCycles
import de.steppicrew.healthconnectview.health.cycleStats
import de.steppicrew.healthconnectview.health.mergeCycleDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CyclesTest {

    private val zone = ZoneOffset.ofHours(2)
    private val jan1: LocalDate = LocalDate.of(2026, 1, 1)
    private val always = LocalDate.MIN..LocalDate.MAX

    private fun bleeding(vararg dates: LocalDate) = dates.associateWith { CycleDay(bleeding = true) }

    private fun at(date: LocalDate, hour: Int, offset: ZoneOffset = zone) =
        date.atTime(hour, 0).toInstant(offset)

    @Test
    fun `cycles run from one period start to the next`() {
        val days = bleeding(jan1, jan1.plusDays(1), jan1.plusDays(28), jan1.plusDays(57))
        val cycles = buildCycles(days, always, lastDay = jan1.plusDays(60))

        assertEquals(listOf(jan1, jan1.plusDays(28), jan1.plusDays(57)), cycles.map { it.start })
        assertEquals(listOf(28, 29, null), cycles.map { it.length })
        assertEquals(28, cycles[0].days.size)
        assertEquals(2, cycles[0].periodLength)
    }

    @Test
    fun `an unlogged day inside a period does not start a new cycle`() {
        val days = bleeding(jan1, jan1.plusDays(1), jan1.plusDays(3), jan1.plusDays(4))
        val cycles = buildCycles(days, always, lastDay = jan1.plusDays(10))

        assertEquals(1, cycles.size)
        assertEquals(5, cycles.single().periodLength)
    }

    @Test
    fun `spotting never starts a cycle`() {
        val days = bleeding(jan1) + (jan1.plusDays(14) to CycleDay(spotting = true))
        assertEquals(1, buildCycles(days, always, lastDay = jan1.plusDays(20)).size)
    }

    @Test
    fun `a cycle starting in the window is kept whole past its end`() {
        val days = bleeding(jan1.minusDays(28), jan1.plusDays(20), jan1.plusDays(48))
        val cycles = buildCycles(days, jan1..jan1.plusDays(30), lastDay = jan1.plusDays(60))

        assertEquals(listOf(jan1.plusDays(20)), cycles.map { it.start })
        assertEquals(28, cycles.single().length)
    }

    @Test
    fun `stats use the median so one missed period does not skew them`() {
        val starts = listOf(0L, 28, 57, 85, 141, 169).map { jan1.plusDays(it) }
        val stats = cycleStats(buildCycles(bleeding(*starts.toTypedArray()), always, jan1.plusDays(180)))!!

        assertEquals(5, stats.completed)
        assertEquals(28, stats.medianLength)
        assertEquals(28, stats.shortest)
        assertEquals(56, stats.longest)
    }

    @Test
    fun `no completed cycle means no stats`() {
        assertNull(cycleStats(buildCycles(bleeding(jan1), always, jan1.plusDays(5))))
    }

    @Test
    fun `a period record covers the days before its exclusive end`() {
        val period = MenstruationPeriodRecord(
            startTime = at(jan1, 0), startZoneOffset = zone,
            endTime = at(jan1.plusDays(3), 0), endZoneOffset = zone,
            metadata = Metadata.manualEntry(),
        )
        val days = mergeCycleDays(CycleRecords(periods = listOf(period)), zone)

        assertEquals(setOf(jan1, jan1.plusDays(1), jan1.plusDays(2)), days.filterValues { it.bleeding }.keys)
    }

    @Test
    fun `a late entry belongs to the day in its own zone`() {
        // 23:30 in UTC+2 is already the next day in UTC+3, the device's zone here.
        val flow = MenstruationFlowRecord(
            time = jan1.atTime(23, 30).toInstant(zone), zoneOffset = zone,
            metadata = Metadata.manualEntry(), flow = MenstruationFlowRecord.FLOW_LIGHT,
        )
        val days = mergeCycleDays(CycleRecords(flows = listOf(flow)), ZoneOffset.ofHours(3))

        assertEquals(setOf(jan1), days.keys)
    }

    @Test
    fun `several writers merge to one day with the heaviest flow`() {
        fun flow(level: Int) = MenstruationFlowRecord(
            time = at(jan1, 8), zoneOffset = zone, metadata = Metadata.manualEntry(), flow = level,
        )
        val days = mergeCycleDays(
            CycleRecords(flows = listOf(flow(MenstruationFlowRecord.FLOW_LIGHT), flow(MenstruationFlowRecord.FLOW_HEAVY))),
            zone,
        )

        assertEquals(MenstruationFlowRecord.FLOW_HEAVY, days.getValue(jan1).flow)
    }

    @Test
    fun `a positive ovulation test outranks a negative one the same day`() {
        fun test(result: Int, hour: Int) = OvulationTestRecord(
            time = at(jan1, hour), zoneOffset = zone, metadata = Metadata.manualEntry(), result = result,
        )
        val days = mergeCycleDays(
            CycleRecords(ovulationTests = listOf(test(OvulationTestRecord.RESULT_POSITIVE, 8), test(OvulationTestRecord.RESULT_NEGATIVE, 20))),
            zone,
        )

        assertEquals(OvulationTestRecord.RESULT_POSITIVE, days.getValue(jan1).ovulationTest)
        assertFalse(days.getValue(jan1).bleeding)
    }

    @Test
    fun `basal temperature is the waking reading, not the mean`() {
        fun reading(hour: Int, celsius: Double) = BasalBodyTemperatureRecord(
            time = at(jan1, hour), zoneOffset = zone, metadata = Metadata.manualEntry(),
            temperature = Temperature.celsius(celsius),
        )
        val days = mergeCycleDays(CycleRecords(temperatures = listOf(reading(9, 36.9), reading(6, 36.4))), zone)

        assertEquals(36.4, days.getValue(jan1).temperature!!, 1e-9)
    }
}
