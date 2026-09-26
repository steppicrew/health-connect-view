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
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature

/** The files an export can produce; see `Exporter` and `PressureReportPdf`. */
enum class ExportKind(val suffix: String, val extension: String, val feature: Feature) {
    RECORDS("records", "csv", Feature.EXPORT_CSV),
    DAILY("daily", "csv", Feature.EXPORT_CSV),
    REPORT("report", "pdf", Feature.PRESSURE_REPORT),
}

/**
 * The export action for a detail view: a menu of the files on offer, each saved through the
 * system's own "save as" dialog, so the user picks the place and the app never chooses one.
 *
 * Locked entries stay visible with a padlock: a feature that silently vanishes cannot be asked
 * about, and "Pro" says why it is unavailable. Tapping one opens Play's purchase sheet, so the
 * way to the feature starts where the user met the lock.
 */
@Composable
fun ExportAction(
    fileBase: String,
    dailyAvailable: Boolean,
    /** Blood pressure only: the log for a doctor. */
    reportAvailable: Boolean,
    onExport: (ExportKind, Uri) -> Unit,
) {
    val activity = LocalActivity.current
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ExportKind?>(null) }
    val onSaved: (Uri?) -> Unit = { uri ->
        val kind = pending
        pending = null
        if (uri != null && kind != null) onExport(kind, uri)
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
                    ExportKind.REPORT -> reportAvailable
                }
            }
            kinds.forEach { kind ->
                val unlocked = pro.allows(kind.feature)
                val label = stringResource(
                    when (kind) {
                        ExportKind.RECORDS -> R.string.export_records
                        ExportKind.DAILY -> R.string.export_daily
                        ExportKind.REPORT -> R.string.export_report
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (unlocked) label else stringResource(R.string.export_premium, label)) },
                    leadingIcon = if (unlocked) null else { { Icon(Icons.Default.Lock, contentDescription = null) } },
                    onClick = {
                        open = false
                        if (unlocked) {
                            pending = kind
                            val name = "${fileBase}_${kind.suffix}.${kind.extension}"
                            if (kind.extension == "pdf") savePdf.launch(name) else saveCsv.launch(name)
                        } else {
                            activity?.let(AppEntitlements.current::buy)
                        }
                    },
                )
            }
        }
    }
}
