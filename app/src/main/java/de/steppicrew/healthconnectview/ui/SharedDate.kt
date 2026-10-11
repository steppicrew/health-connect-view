package de.steppicrew.healthconnectview.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * The one date the whole app shows, treated as today: the dashboard's day, and the day every
 * window on a detail screen ends on -- a year is the 365 days back from it. Moving it on any
 * screen moves it on all of them, the owner's request, 11.10.2026 (step 86).
 *
 * In memory only, and today on a cold start. Never later than today: there is nothing to show
 * there, and a window ending tomorrow would read a day that has not happened.
 */
object SharedDate {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val current: LocalDate get() = _date.value

    fun set(date: LocalDate) {
        _date.value = minOf(date, LocalDate.now())
    }
}
