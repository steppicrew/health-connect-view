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
 * Which unit blood glucose is shown in. Health Connect stores mmol/L.
 *
 * Separate from [UnitSystem] because it follows a country's lab convention, not its measuring
 * system: metric Germany, France and Japan report mg/dL, and so does the imperial US, while
 * the UK and Canada use mmol/L.
 */
enum class GlucoseUnit {
    MMOL_PER_L,
    MG_PER_DL,
    ;

    companion object {
        /**
         * The unit a locale's country conventionally reports in. mg/dL where it is the usual
         * or the prevailing lab unit; mmol/L everywhere else, which is the SI unit and the
         * majority convention. Germany uses both and is counted mg/dL, the unit most of its
         * labs and meters print.
         */
        fun forLocale(locale: Locale): GlucoseUnit =
            if (locale.country in MG_PER_DL_COUNTRIES) MG_PER_DL else MMOL_PER_L

        private val MG_PER_DL_COUNTRIES = setOf(
            "US", "DE", "AT", "FR", "BE", "IT", "ES", "PT", "JP", "KR", "TW",
            "IN", "IL", "EG", "BR", "MX", "AR",
        )
    }
}

/**
 * What a value measures, and so how it converts. Values arrive from the registry in the unit
 * Health Connect stores, named here as the base unit; [convert] turns one into the unit the
 * matching setting shows.
 *
 * Only quantities that have an alternative. Energy stays kilocalories (US labels say
 * "Calories" and mean the same) and blood pressure mmHg everywhere.
 */
enum class Quantity(
    @param:StringRes val baseUnitRes: Int,
    @param:StringRes val alternateUnitRes: Int,
    private val baseSymbol: String,
    private val alternateSymbol: String,
    private val factor: Double,
    private val offset: Double = 0.0,
    private val followsGlucoseUnit: Boolean = false,
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

    /**
     * Glucose's molar mass is 180.16 g/mol, so 1 mmol/L is 18.016 mg/dL; 18 is the factor
     * the Health Connect library and most meters use, and it is used here so a value matches
     * the writing app's own display.
     */
    GLUCOSE(R.string.unit_mmoll, R.string.unit_mgdl, "mmol/L", "mg/dL", 18.0, followsGlucoseUnit = true),
    ;

    /** Whether the settings currently show this quantity in its alternate unit. */
    val alternateShown: Boolean
        get() = if (followsGlucoseUnit) {
            Units.glucose == GlucoseUnit.MG_PER_DL
        } else {
            Units.system == UnitSystem.IMPERIAL
        }

    fun convert(base: Double, alternate: Boolean = alternateShown): Double =
        if (alternate) base * factor + offset else base

    /** The inverse of [convert], for a number the user typed -- a goal -- to be stored as base. */
    fun toBase(shown: Double, alternate: Boolean = alternateShown): Double =
        if (alternate) (shown - offset) / factor else shown

    @StringRes
    fun unitRes(alternate: Boolean = alternateShown): Int = if (alternate) alternateUnitRes else baseUnitRes

    fun symbol(alternate: Boolean = alternateShown): String = if (alternate) alternateSymbol else baseSymbol
}

/**
 * The unit system in force, read wherever a value is converted.
 *
 * A process-wide value rather than a parameter threaded through every screen: conversion
 * happens where values enter the app -- a record's points, an aggregate -- so every chart,
 * total, list and export follows it without knowing it exists. Set at start from the locale,
 * then from the stored settings; screens reload on return, which is when a change shows.
 */
object Units {
    @Volatile
    var system: UnitSystem = UnitSystem.forLocale(Locale.getDefault())

    /** Blood glucose's unit, set like [system] but from its own setting. */
    @Volatile
    var glucose: GlucoseUnit = GlucoseUnit.forLocale(Locale.getDefault())

    /** A stored value as text in the shown unit, for a record's one-line summary. */
    fun format(quantity: Quantity, base: Double): String =
        Formatting.number(quantity.convert(base)) + " " + quantity.symbol()
}
