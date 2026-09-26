package de.steppicrew.healthconnectview.health

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import java.time.Duration

/**
 * Aggregates come back as a plain number, a [Duration], or one of the library's unit types.
 *
 * Every caller has to convert to the same scale the corresponding spec displays, or the
 * number under a chart disagrees with the rows beside it. Missing a branch silently empties a
 * type -- the value is simply dropped -- so this lives in one place rather than being repeated
 * per screen.
 *
 * Lengths need the [metric]: distance is shown in kilometres, but elevation in metres and
 * height in centimetres. Converting every length to kilometres made a week of 670 m climbed
 * read "0.67 m" beside records of 0.8 m each.
 */
fun numericAggregate(value: Any, metric: AggregateMetric<*>? = null): Double? = when (value) {
    is Long -> value.toDouble()
    is Double -> value
    is Duration -> value.toMinutes() / 60.0
    is Mass -> value.inKilograms
    is Length -> when (metric) {
        ElevationGainedRecord.ELEVATION_GAINED_TOTAL -> value.inMeters
        HeightRecord.HEIGHT_AVG -> value.inMeters * 100.0
        else -> value.inKilometers
    }
    is Energy -> value.inKilocalories
    is Volume -> value.inLiters
    is Power -> value.inWatts
    is Velocity -> value.inKilometersPerHour
    is Percentage -> value.value
    is Pressure -> value.inMillimetersOfMercury
    is Temperature -> value.inCelsius
    else -> null
}
