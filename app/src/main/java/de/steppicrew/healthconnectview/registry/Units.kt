package de.steppicrew.healthconnectview.registry

import androidx.annotation.StringRes
import de.steppicrew.healthconnectview.R
import java.util.Locale

/** How measured values are shown. Health Connect stores metric; imperial is a view of it. */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    companion object {
        /**
         * The system a locale's country uses day to day. Only the United States, Liberia and
         * Myanmar are imperial for body and distance; the UK mixes both and is left metric,
         * since it has kilometres on its medical forms and a setting to change it.
         */
        fun forLocale(locale: Locale): UnitSystem =
            if (locale.country in IMPERIAL_COUNTRIES) IMPERIAL else METRIC

        private val IMPERIAL_COUNTRIES = setOf("US", "LR", "MM")
    }
}

/**
 * What a value measures, and so how it converts. Values arrive from the registry in the metric
 * unit named here; [convert] turns one into the chosen system.
 *
 * Only quantities that differ between the systems. Energy stays kilocalories (US labels say
 * "Calories" and mean the same), blood pressure mmHg and glucose mmol/L -- glucose units
 * follow the country's lab convention, not its measuring system, and would need their own
 * setting.
 */
enum class Quantity(
    @param:StringRes val metricUnitRes: Int,
    @param:StringRes val imperialUnitRes: Int,
    private val metricSymbol: String,
    private val imperialSymbol: String,
    private val factor: Double,
    private val offset: Double = 0.0,
) {
    MASS(R.string.unit_kg, R.string.unit_lb, "kg", "lb", 2.2046226218),
    DISTANCE(R.string.unit_km, R.string.unit_mi, "km", "mi", 0.6213711922),
    ELEVATION(R.string.unit_m, R.string.unit_ft, "m", "ft", 3.2808398950),
    BODY_HEIGHT(R.string.unit_cm, R.string.unit_in, "cm", "in", 1.0 / 2.54),
    SPEED(R.string.unit_kmh, R.string.unit_mph, "km/h", "mph", 0.6213711922),
    TEMPERATURE(R.string.unit_celsius, R.string.unit_fahrenheit, "°C", "°F", 1.8, 32.0),

    /** A difference between temperatures: scaled like one, never shifted by 32. */
    TEMPERATURE_CHANGE(R.string.unit_celsius, R.string.unit_fahrenheit, "°C", "°F", 1.8),
    VOLUME(R.string.unit_l, R.string.unit_floz, "L", "fl oz", 33.8140227018),
    ;

    fun convert(metric: Double, system: UnitSystem): Double =
        if (system == UnitSystem.IMPERIAL) metric * factor + offset else metric

    /** The inverse of [convert], for a number the user typed -- a goal -- to be stored metric. */
    fun toMetric(shown: Double, system: UnitSystem): Double =
        if (system == UnitSystem.IMPERIAL) (shown - offset) / factor else shown

    @StringRes
    fun unitRes(system: UnitSystem): Int = if (system == UnitSystem.IMPERIAL) imperialUnitRes else metricUnitRes

    fun symbol(system: UnitSystem): String = if (system == UnitSystem.IMPERIAL) imperialSymbol else metricSymbol
}

/**
 * The unit system in force, read wherever a value is converted.
 *
 * A process-wide value rather than a parameter threaded through every screen: conversion
 * happens where values enter the app -- a record's points, an aggregate -- so every chart,
 * total, list and export follows it without knowing it exists. Set at start from the locale,
 * then from the stored setting; screens reload on return, which is when a change shows.
 */
object Units {
    @Volatile
    var system: UnitSystem = UnitSystem.forLocale(Locale.getDefault())

    /** A metric value as text in the current system, for a record's one-line summary. */
    fun format(quantity: Quantity, metric: Double): String =
        Formatting.number(quantity.convert(metric, system)) + " " + quantity.symbol(system)
}
