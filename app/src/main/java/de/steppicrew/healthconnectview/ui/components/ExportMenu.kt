package de.steppicrew.healthconnectview.ui.components

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.export.ExportPeriod
import de.steppicrew.healthconnectview.export.printPdf
import kotlinx.coroutines.launch

/** The files an export can produce; see `Exporter`, `PressureReportPdf` and `ReadingReportPdf`. */
enum class ExportKind(val suffix: String, val extension: String, val feature: Feature) {
    RECORDS("records", "csv", Feature.EXPORT_CSV),
    DAILY("daily", "csv", Feature.EXPORT_CSV),
    REPORT("report", "pdf", Feature.PDF_REPORTS),

    /** The report in the system's print preview: seen, printed or saved there, no file of ours. */
    PRINT("report", "pdf", Feature.PDF_REPORTS),
    ;

    val mimeType: String get() = if (extension == "pdf") "application/pdf" else "text/csv"
}

/**
 * The export action for a detail view: a menu of the files on offer, then the period
 * ([ExportPeriodDialog]), each saved through the system's own "save as" dialog, so the user
 * picks the place and the app never chooses one.
 *
 * Locked entries stay visible with a padlock: a feature that silently vanishes cannot be asked
 * about, and "Pro" says why it is unavailable. Tapping one opens Play's purchase sheet, so the
 * way to the feature starts where the user met the lock.
 */
@Composable
fun ExportAction(
    /** The type's name for the file, e.g. "Weight"; the period and the kind are added to it. */
    typeName: String,
    /** The window on screen, offered first. */
    shown: ExportPeriod,
    historyGranted: Boolean,
    dailyAvailable: Boolean,
    /** Types with a PDF log for a doctor (`Exporter.REPORT_TYPES`). */
    reportAvailable: Boolean,
    /** Whether the file would hold anything; the dialog opens only if so. */
    canExport: suspend (ExportKind, ExportPeriod) -> Boolean,
    onExport: (ExportKind, ExportPeriod, Uri) -> Unit,
    /** The report for a period in memory, for the print preview; null if it failed. */
    renderReport: suspend (ExportPeriod) -> ByteArray?,
    /** Said when the report could not be shown: no print service, or the report failed. */
    onNoViewer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    /** The file type chosen, waiting for its period. */
    var choosing by remember { mutableStateOf<ExportKind?>(null) }
    var pending by remember { mutableStateOf<Pair<ExportKind, ExportPeriod>?>(null) }
    val onSaved: (Uri?) -> Unit = { uri ->
        val job = pending
        pending = null
        if (uri != null && job != null) onExport(job.first, job.second, uri)
    }
    // One launcher per file type: the save dialog's type is fixed when it is registered.
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), onSaved)
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), onSaved)

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.export_action))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val kinds = ExportKind.entries.filter { kind ->
                when (kind) {
                    ExportKind.RECORDS -> true
                    ExportKind.DAILY -> dailyAvailable
                    ExportKind.REPORT, ExportKind.PRINT -> reportAvailable
                }
            }
            kinds.forEach { kind ->
                val unlocked = pro.allows(kind.feature)
                val label = stringResource(
                    when (kind) {
                        ExportKind.RECORDS -> R.string.export_records
                        ExportKind.DAILY -> R.string.export_daily
                        ExportKind.REPORT -> R.string.export_report
                        ExportKind.PRINT -> R.string.export_print
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (unlocked) label else stringResource(R.string.export_premium, label)) },
                    leadingIcon = if (unlocked) null else { { Icon(Icons.Default.Lock, contentDescription = null) } },
                    onClick = {
                        open = false
                        if (unlocked) {
                            choosing = kind
                        } else {
                            activity?.let(AppEntitlements.current::buy)
                        }
                    },
                )
            }
        }
    }

    choosing?.let { kind ->
        ExportPeriodDialog(
            shown = shown,
            historyGranted = historyGranted,
            onDismiss = { choosing = null },
            onConfirm = { period ->
                choosing = null
                scope.launch {
                    if (!canExport(kind, period)) return@launch
                    val name = "${typeName}_${period.fileTag}_${kind.suffix}.${kind.extension}"
                    if (kind == ExportKind.PRINT) {
                        val pdf = renderReport(period)
                        val shownNow = pdf != null && activity != null && printPdf(activity, name, pdf)
                        if (!shownNow) onNoViewer()
                        return@launch
                    }
                    pending = kind to period
                    if (kind.extension == "pdf") savePdf.launch(name) else saveCsv.launch(name)
                }
            },
        )
    }
}
