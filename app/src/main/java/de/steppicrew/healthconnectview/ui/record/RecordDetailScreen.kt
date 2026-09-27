package de.steppicrew.healthconnectview.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.registry.DeviceKind
import de.steppicrew.healthconnectview.registry.RecordingMethod
import de.steppicrew.healthconnectview.registry.readingContext
import de.steppicrew.healthconnectview.registry.deviceName
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.util.appLabelFor

/**
 * The record [openId] names, from a list's [records], drawn over that list; nothing when none
 * is open or it has left the list with a reload. Over the list rather than a destination of
 * its own: the list already holds the record, a route would read it again by id, and the list
 * keeps its scroll position underneath. Back closes it.
 *
 * Call it after the list's own screen, so it is drawn on top.
 */
@Composable
fun RecordDetailOverlay(spec: RecordTypeSpec<*>?, records: List<Record>, openId: String?, onClose: () -> Unit) {
    if (spec == null) return
    val record = openId?.let { id -> records.firstOrNull { it.metadata.id == id } } ?: return
    BackHandler(onBack = onClose)
    RecordDetailScreen(spec, record, onBack = onClose, modifier = Modifier.fillMaxSize())
}

/** Everything stored about one record, including which app wrote it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    spec: RecordTypeSpec<*>,
    record: Record,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(spec.displayNameRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = de.steppicrew.healthconnectview.ui.detail.summaryWithUnit(spec, record),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = Formatting.dateTime(spec.timeOf(record)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            HorizontalDivider()

            spec.detailsOf(record).forEach { field ->
                DetailRow(stringResource(field.labelRes), field.value)
            }
            readingContext(record).forEach { item ->
                DetailRow(stringResource(item.labelRes), stringResource(item.valueRes))
            }

            DetailRow(
                label = stringResource(R.string.field_source_app),
                value = context.appLabelFor(record.metadata.dataOrigin.packageName),
            )
            DetailRow(
                label = stringResource(R.string.field_recording),
                value = stringResource(RecordingMethod.of(record.metadata.recordingMethod).labelRes),
            )
            val device = record.metadata.device
            val deviceText = listOfNotNull(
                DeviceKind.of(device)?.let { stringResource(it.labelRes) },
                deviceName(device),
            ).joinToString(" · ")
            if (deviceText.isNotEmpty()) {
                DetailRow(label = stringResource(R.string.field_device), value = deviceText)
            }
            DetailRow(
                label = stringResource(R.string.field_record_id),
                value = record.metadata.id,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
