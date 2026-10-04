package de.steppicrew.healthconnectview.ui.components

/**
 * Parses a number typed into one of the app's dialogs, or null if it is not one.
 *
 * Lenient on purpose, since the keyboard follows the locale while [String.toDoubleOrNull]
 * accepts only ASCII: a decimal comma is taken as a point, and digits from any script count.
 * An Arabic keyboard types "٨٠٠٠" and "٧٫٥", which the plain parse rejects outright -- the
 * goal dialog then saved nothing and the zones dialog called valid boundaries invalid.
 */
fun parseDecimalInput(text: String): Double? =
    buildString {
        text.trim().forEach { c ->
            when {
                c.isDigit() -> append(c.digitToInt())
                c == ',' || c == ARABIC_DECIMAL_SEPARATOR -> append('.')
                else -> append(c)
            }
        }
    }.toDoubleOrNull()

private const val ARABIC_DECIMAL_SEPARATOR = '٫'
