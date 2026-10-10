package de.steppicrew.healthconnectview.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.registry.DeviceKind
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.RecordingMethod
import de.steppicrew.healthconnectview.registry.readingContext
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.util.appLabelFor

/**
 * Appends the localised unit, for the types whose unit is a word rather than a symbol, and
 * resolves the words of an enum-valued type.
 */
@Composable
internal fun summaryWithUnit(spec: RecordTypeSpec<*>, record: Record): String {
    spec.summaryResOf(record)?.let { words -> return words.map { stringResource(it) }.joinToString(", ") }
    val value = spec.summaryOf(record)
    val unit = spec.summaryUnitRes?.let { stringResource(it) } ?: return value
    return "$value $unit"
}

/**
 * One stored record: its value, its full time span, and the app that wrote it.
 *
 * Shared with the span view so both lists describe a record identically -- the span and the
 * source are what make a legitimate whole-day summary legible next to itemised records from
 * another app.
 */
@Composable
internal fun RecordRow(
    spec: RecordTypeSpec<*>,
    record: Record,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = summaryWithUnit(spec, record),
            style = MaterialTheme.typography.bodyLarge,
        )
        // What the writer said about the reading -- "Nüchtern · Frühstück", "Sitzend · Linker
        // Oberarm" -- beside the value it qualifies, before when and by what.
        val readingContext = readingContext(record)
        if (readingContext.isNotEmpty()) {
            Text(
                text = readingContext.map { stringResource(it.valueRes) }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = Formatting.timeSpan(spec.timeOf(record), spec.endTimeOf(record)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Every row names its writer. Several apps commonly write the same type -- one
        // itemising as it goes, another posting a daily summary -- so without the source a
        // legitimate whole-day record is indistinguishable from a duplicate or an error.
        // Beside it, what made the record: a watch and a phone counting the same steps, or a
        // value typed in by hand among measured ones.
        val method = RecordingMethod.of(record.metadata.recordingMethod)
        val device = DeviceKind.of(record.metadata.device)
        val parts = listOfNotNull(
            LocalContext.current.appLabelFor(spec.originOf(record)),
            device?.let { stringResource(it.labelRes) },
            method.takeIf { it.shownInList }?.let { stringResource(it.labelRes) },
        )
        Row(verticalAlignment = Alignment.Top) {
            provenanceIcon(method, device)?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 4.dp, top = firstLineInset(MaterialTheme.typography.labelSmall, 14.dp))
                        .size(14.dp),
                )
            }
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A glyph for what made a record, so a list can be scanned for it: a pencil for a value typed
 * in, which matters more than the device, otherwise the device's kind. None when neither is
 * known -- a question mark on most rows would only be noise.
 */
internal fun provenanceIcon(method: RecordingMethod, device: DeviceKind?): ImageVector? = when {
    method == RecordingMethod.MANUAL -> Icons.Default.EditNote
    else -> when (device) {
        DeviceKind.WATCH, DeviceKind.FITNESS_BAND -> Icons.Default.Watch
        DeviceKind.PHONE -> Icons.Default.PhoneAndroid
        DeviceKind.SCALE -> Icons.Default.MonitorWeight
        DeviceKind.RING -> Icons.Default.RadioButtonUnchecked
        DeviceKind.HEAD_MOUNTED -> Icons.Default.ViewInAr
        DeviceKind.CHEST_STRAP -> Icons.Default.MonitorHeart
        DeviceKind.SMART_DISPLAY -> Icons.Default.Tv
        null -> null
    }
}
