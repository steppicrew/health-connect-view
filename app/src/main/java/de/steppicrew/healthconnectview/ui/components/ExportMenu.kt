package de.steppicrew.healthconnectview.ui.components

import android.net.Uri
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

/** The two files an export can produce; see `Exporter`. */
enum class ExportKind(val suffix: String) { RECORDS("records"), DAILY("daily") }

/**
 * The export action for a detail view: a menu of the files on offer, each saved through the
 * system's own "save as" dialog, so the user picks the place and the app never chooses one.
 *
 * Locked entries stay visible with a padlock: a feature that silently vanishes cannot be asked
 * about, and "Premium" says why it is unavailable.
 */
@Composable
fun ExportAction(
    fileBase: String,
    dailyAvailable: Boolean,
    onExport: (ExportKind, Uri) -> Unit,
) {
    val unlocked by remember { AppEntitlements.current.has(Feature.EXPORT_CSV) }
        .collectAsStateWithLifecycle(initialValue = false)
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ExportKind?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val kind = pending
        pending = null
        if (uri != null && kind != null) onExport(kind, uri)
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.export_action))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val kinds = if (dailyAvailable) ExportKind.entries else listOf(ExportKind.RECORDS)
            kinds.forEach { kind ->
                val label = stringResource(
                    if (kind == ExportKind.RECORDS) R.string.export_records else R.string.export_daily,
                )
                DropdownMenuItem(
                    text = { Text(if (unlocked) label else stringResource(R.string.export_premium, label)) },
                    enabled = unlocked,
                    leadingIcon = if (unlocked) null else { { Icon(Icons.Default.Lock, contentDescription = null) } },
                    onClick = {
                        open = false
                        pending = kind
                        save.launch("${fileBase}_${kind.suffix}.csv")
                    },
                )
            }
        }
    }
}
