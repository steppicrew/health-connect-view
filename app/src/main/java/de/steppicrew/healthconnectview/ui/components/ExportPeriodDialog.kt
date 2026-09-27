package de.steppicrew.healthconnectview.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.export.ExportPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Which days an export covers: the window on screen, a calendar month or year, or a range of
 * one's own. Asked after the file type and before the save dialog, so the file name can say it.
 */
@Composable
fun ExportPeriodDialog(
    shown: ExportPeriod,
    /** Whether older data than 30 days is readable; without it a long period is cut short. */
    historyGranted: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ExportPeriod) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var preset by remember { mutableStateOf<ExportPeriod.Preset?>(ExportPeriod.Preset.SHOWN) }
    var custom by remember { mutableStateOf<ExportPeriod?>(null) }
    var picking by remember { mutableStateOf(false) }
    val chosen = preset?.let { ExportPeriod.of(it, shown, today) } ?: custom

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_period_title)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                ExportPeriod.Preset.entries.forEach { choice ->
                    PeriodRow(
                        label = choice.labelRes,
                        detail = describe(ExportPeriod.of(choice, shown, today)),
                        selected = preset == choice,
                        onClick = { preset = choice },
                    )
                }
                PeriodRow(
                    label = R.string.export_period_custom,
                    detail = custom?.let(::describe) ?: stringResource(R.string.export_period_custom_pick),
                    selected = preset == null,
                    onClick = { picking = true },
                )
                if (chosen != null && !historyGranted && chosen.needsHistory(today)) {
                    Text(
                        text = stringResource(R.string.export_period_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { chosen?.let(onConfirm) }, enabled = chosen != null) {
                Text(stringResource(R.string.export_period_next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (picking) {
        RangePickerDialog(
            initial = custom ?: shown,
            today = today,
            onDismiss = { picking = false },
            onPicked = {
                custom = it
                preset = null
                picking = false
            },
        )
    }
}

@Composable
private fun PeriodRow(@StringRes label: Int, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
    ) {
        // On the label's line, not centred between it and the dates beneath.
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodyLarge, TOGGLE_SIZE.dp)),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The platform's date range picker, limited to days up to today. It speaks in UTC midnights,
 * so dates cross as UTC and never shift by the local offset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(initial: ExportPeriod, today: LocalDate, onDismiss: () -> Unit, onPicked: (ExportPeriod) -> Unit) {
    val lastMillis = today.toUtcMillis()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.first.toUtcMillis(),
        initialSelectedEndDateMillis = initial.last.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= lastMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = {
                    // A single tapped day is a range of one.
                    val first = start?.toLocalDate() ?: return@TextButton
                    onPicked(ExportPeriod(first, end?.toLocalDate() ?: first))
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

/** A radio button or checkbox without its own touch target: the 20 dp mark and its padding. */
private const val TOGGLE_SIZE = 24

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** "August 2026", "2026", or the two dates. */
private fun describe(period: ExportPeriod): String {
    val month = YearMonth.from(period.first)
    return when (period.fileTag) {
        month.toString() -> month.format(DateTimeFormatter.ofPattern("LLLL yyyy"))
        period.first.year.toString() -> period.first.year.toString()
        else -> {
            val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            if (period.first == period.last) period.first.format(dates)
            else "${period.first.format(dates)} – ${period.last.format(dates)}"
        }
    }
}

@get:StringRes
private val ExportPeriod.Preset.labelRes: Int
    get() = when (this) {
        ExportPeriod.Preset.SHOWN -> R.string.export_period_shown
        ExportPeriod.Preset.LAST_MONTH -> R.string.export_period_last_month
        ExportPeriod.Preset.THIS_MONTH -> R.string.export_period_this_month
        ExportPeriod.Preset.LAST_YEAR -> R.string.export_period_last_year
        ExportPeriod.Preset.THIS_YEAR -> R.string.export_period_this_year
    }
