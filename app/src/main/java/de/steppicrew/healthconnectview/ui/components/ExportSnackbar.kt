package de.steppicrew.healthconnectview.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.export.ExportResult
import kotlinx.coroutines.flow.SharedFlow

/**
 * Says how an export went in [snackbar], with a way to open the written file in the user's own
 * viewer. Shared by every screen that exports, so a file saved anywhere is reported the same.
 */
@Composable
fun ShowExportResults(results: SharedFlow<ExportResult>, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    val context = LocalContext.current
    LaunchedEffect(results) {
        results.collect { result ->
            val message = when (result) {
                is ExportResult.Written ->
                    resources.getQuantityString(R.plurals.export_done, result.rows, result.rows)
                is ExportResult.Report ->
                    resources.getQuantityString(R.plurals.export_report_done, result.readings, result.readings)
                ExportResult.Failed -> resources.getString(R.string.export_failed)
                ExportResult.Empty -> resources.getString(R.string.export_empty)
                ExportResult.NoViewer -> resources.getString(R.string.export_no_viewer)
            }
            // Where the file went, so it can be opened straight away in the user's own viewer.
            val saved = when (result) {
                is ExportResult.Written -> result.uri to result.mimeType
                is ExportResult.Report -> result.uri to result.mimeType
                else -> null
            }
            if (saved == null) {
                snackbar.showSnackbar(message)
            } else {
                val choice = snackbar.showSnackbar(
                    message,
                    actionLabel = resources.getString(R.string.export_open),
                    duration = SnackbarDuration.Long,
                )
                if (choice == SnackbarResult.ActionPerformed) {
                    val view = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(saved.first, saved.second)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(view)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.showSnackbar(resources.getString(R.string.export_no_viewer))
                    }
                }
            }
        }
    }
}
