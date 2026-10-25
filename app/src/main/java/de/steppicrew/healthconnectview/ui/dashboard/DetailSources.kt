package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.Record
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.health.Suggestion
import de.steppicrew.healthconnectview.health.labelRes
import de.steppicrew.healthconnectview.registry.DeviceKind
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.deviceName
import de.steppicrew.healthconnectview.ui.components.InfoToggle
import de.steppicrew.healthconnectview.ui.components.SourceMark
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.components.rememberExplanation
import de.steppicrew.healthconnectview.util.appLabelFor
import kotlinx.coroutines.flow.map

// The detail screen's sources: which apps wrote the data, the default writer, and two
// writers compared. Split from TileDetailScreen.kt.

/**
 * Source picker plus a plain statement of what the number above actually is.
 *
 * "All sources" stays first and is the default: it is Health Connect's deduplicated total,
 * which is the right answer for the metric and is deliberately not any single app's figure.
 * Selecting one app answers a different question, so the caption says which question is being
 * answered rather than leaving the user to infer it from a changed number.
 *
 * Only shown where more than one app wrote; with a single writer there is nothing to choose.
 */
/**
 * Every writer's mark, overlapping, as the "all sources" chip: the choice is the union of the
 * icons beside it, and a picture of that says so without words. Writers with no mark at all
 * are left out of the picture; the chip's label still names the choice for a screen reader.
 */
@Composable
private fun StackedSources(sources: List<String>) {
    val label = stringResource(R.string.source_all)
    val ring = MaterialTheme.colorScheme.surface
    Box(Modifier.semantics(mergeDescendants = true) { contentDescription = label }) {
        sources.forEachIndexed { index, packageName ->
            Box(
                Modifier
                    .padding(start = (index * STACK_STEP).dp)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(1.dp)
                    .clearAndSetSemantics { },
            ) {
                SourceMark(packageName, SOURCE_ICON, SOURCE_ICON_PX, Modifier.clip(CircleShape)) {}
            }
        }
    }
}

/**
 * A compact selectable chip for the source picker.
 *
 * Material's filter chip pads its content by 16dp a side, which for a 20dp app icon made each
 * chip three times the width of what it shows, and five writers ran to two lines. Here the
 * padding is 8dp; the touch target stays at the accessible minimum, and the selected state is
 * announced as with any chip.
 */
@Composable
private fun SourceChip(
    selected: Boolean,
    onClick: () -> Unit,
    /** The app chosen in settings as the default: marked with a star, named for TalkBack. */
    isDefault: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier
                .height(SOURCE_CHIP_HEIGHT.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
            if (isDefault) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.source_default),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DEFAULT_STAR.dp),
                )
            }
        }
    }
}

private const val DEFAULT_STAR = 14

@Composable
internal fun SourceSection(data: TileDetailData, onSelectSource: (String?) -> Unit) {
    val context = LocalContext.current
    val sources = data.contributingApps.sortedBy { context.appLabelFor(it) }

    val explanation = rememberExplanation("sources")
    val defaultAction = LocalSourceDefault.current

    // One outlined box for chips, "i" and explanation, so they read as one control -- and so
    // the space is already taken while the writers are still being read, and the chart below
    // does not jump when they arrive.
    //
    // The label sits in the top border, as on a form's fieldset: it names the box once, which
    // is what lets the "all" chip be a picture of every writer rather than the words.
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedCard(Modifier.fillMaxWidth().padding(top = SOURCE_LEGEND_OFFSET.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (data.listPending) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SOURCE_PLACEHOLDER_HEIGHT.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    return@Column
                }
                if (sources.size > 1) {
                    Row(verticalAlignment = Alignment.Top) {
                        // Wraps rather than scrolls: with four or five writers a scrolling row hid the
                        // last ones off the edge, and nothing said there were more.
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourceChip(
                                selected = data.selectedSource == null,
                                onClick = { onSelectSource(null) },
                            ) {
                                StackedSources(sources)
                            }
                            sources.forEach { packageName ->
                                SourceChip(
                                    selected = data.selectedSource == packageName,
                                    onClick = { onSelectSource(packageName) },
                                    isDefault = packageName == defaultAction?.state?.preferred,
                                ) {
                                    // The app's own icon where it has one, which fits several sources
                                    // on screen at once where "Garmin Connect" and "Health Sync"
                                    // already ran off the edge. The label stays as the icon's content
                                    // description, and as the visible text wherever no icon can be had.
                                    SourceMark(packageName, SOURCE_ICON, SOURCE_ICON_PX) {
                                        Text(context.appLabelFor(packageName), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        // The chips are the data; what choosing one means is the explanation.
                        InfoToggle(explanation)
                    }
                    // Back to the app chosen in settings, named, wherever this type shows something
                    // else by a choice of its own -- "Alle" included, which no longer means
                    // "the default" since it is remembered as a choice.
                    val preferred = defaultAction?.state?.preferred
                    if (preferred != null && preferred in sources && defaultAction.state.ownChoice &&
                        data.selectedSource != preferred
                    ) {
                        TextButton(onClick = defaultAction.onUseDefault) {
                            Text(stringResource(R.string.source_use_default, context.appLabelFor(preferred)))
                        }
                    }
                }

                // With a single writer there is nothing to choose and nothing to explain: naming it
                // is the data itself, so it always shows -- with its icon, as the chips would. Even
                // when that app is the preferred source: "only this app's value, not the combined
                // one" was wrong there, since one writer's value is the combined one.
                if (sources.size == 1) {
                    Row(verticalAlignment = Alignment.Top) {
                        SourceMark(
                            sources.first(),
                            SOURCE_ICON,
                            SOURCE_ICON_PX,
                            Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                        ) {}
                        Text(
                            text = stringResource(R.string.detail_written_by, context.appLabelFor(sources.first())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = firstLineTextInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                        )
                    }
                } else if (explanation.expanded == true) {
                    Text(
                        text = data.selectedSource?.let {
                            stringResource(R.string.source_showing_one, context.appLabelFor(it))
                        } ?: stringResource(R.string.source_all_explained),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // Expanded, each chip is spelled out: the app's name, and the devices its records
                // say they came from -- a watch, the phone, "Garmin Forerunner 265" where the writer
                // stored one. Too long for a chip; worth reading once the box is opened.
                if (sources.size > 1 && explanation.expanded == true) {
                    val devices = remember(data.records) { devicesBySource(data.records) }
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sources.forEach { packageName ->
                            val deviceText = devices[packageName].orEmpty().map { (kind, name) ->
                                val kindText = kind?.let { stringResource(it.labelRes) }
                                when {
                                    kindText != null && name != null -> "$kindText ($name)"
                                    else -> kindText ?: name.orEmpty()
                                }
                            }.distinct().joinToString(", ")
                            Row(verticalAlignment = Alignment.Top) {
                                SourceMark(
                                    packageName,
                                    SOURCE_ICON,
                                    SOURCE_ICON_PX,
                                    Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                                ) {}
                                Text(
                                    text = listOf(context.appLabelFor(packageName), deviceText)
                                        .filter { it.isNotEmpty() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, top = firstLineTextInset(MaterialTheme.typography.bodySmall, SOURCE_ICON.dp)),
                                )
                            }
                        }
                    }
                }

                if (sources.size > 1 && explanation.expanded == true) {
                    LocalSourceCompare.current?.let { SourceComparison(it, data.selectedSource) }
                }

                // The overlap winner is Health Connect's own priority setting, not ours to define.
                // Only for the combined view: with one app selected nothing is deduplicated, so
                // which app would win an overlap says nothing about the figure shown.
                if (sources.size > 1 && explanation.expanded == true && data.selectedSource == null) {
                    Text(
                        text = stringResource(R.string.source_priority_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.source_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 4.dp),
        )
    }
}

/**
 * Per writing app, the devices its records name: kind and, where stored, maker and model. From
 * the records the list holds, so a source whose records all lie beyond the list's cap names
 * none -- the line then shows the app alone.
 */
private fun devicesBySource(records: List<Record>): Map<String, List<Pair<DeviceKind?, String?>>> =
    records.groupBy { it.metadata.dataOrigin.packageName }.mapValues { (_, ofSource) ->
        ofSource.mapNotNull { record ->
            val device = record.metadata.device
            val kind = DeviceKind.of(device)
            val name = deviceName(device)
            if (kind == null && name == null) null else kind to name
        }.distinct()
    }

/** Big enough to recognise a brand mark, small enough that several chips fit a phone width. */
private const val SOURCE_ICON = 20
private const val SOURCE_CHIP_HEIGHT = 32

/** Half the label's line height, so the border runs through its middle. */
private const val SOURCE_LEGEND_OFFSET = 8

/** How far each icon in the "all sources" stack sits from the one before it. */
private const val STACK_STEP = 12

/** Roughly one row of chips, so the box keeps its size while the writers load. */
private const val SOURCE_PLACEHOLDER_HEIGHT = 48
private const val SOURCE_ICON_PX = 64

/**
 * Which app covers this type best, worded as what the data shows: per writer, on how many of
 * the last 30 days it wrote anything, how many entries a day, since when, and for sleep how
 * many nights carried stages. Never a verdict on a device or an app -- "covers the most days"
 * is a count -- and only on request, since it reads every record of the type.
 *
 * The offer is the per-type source filter, the same as tapping the app's chip. It cannot make
 * the app win overlaps in the combined total: that is Health Connect's priority list.
 */
@Composable
private fun SourceComparison(compare: SourceCompare, selected: String?) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val load = compare.load) {
        null -> TextButton(onClick = compare.onCompare) {
            Text(stringResource(R.string.sources_compare))
        }

        CoverageLoad.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))

        CoverageLoad.Failed -> Text(
            text = stringResource(R.string.sources_compare_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )

        is CoverageLoad.Done -> {
            val coverage = load.coverage
            val suggestion = coverage.suggestion
            val suggested = suggestion?.packageName
            Text(
                text = stringResource(R.string.sources_compare_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                coverage.writers.forEach { writer ->
                    Row(verticalAlignment = Alignment.Top) {
                        SourceMark(
                            writer.packageName,
                            SOURCE_ICON,
                            SOURCE_ICON_PX,
                            Modifier.padding(top = firstLineInset(MaterialTheme.typography.bodyMedium, SOURCE_ICON.dp)),
                        ) {}
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                text = context.appLabelFor(writer.packageName),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (suggestion != null && writer.packageName == suggested) {
                                Text(
                                    text = stringResource(
                                        when (suggestion.reason) {
                                            Suggestion.Reason.MOST_DAYS -> R.string.sources_most_days
                                            Suggestion.Reason.MOST_ENTRIES -> R.string.sources_most_entries
                                            Suggestion.Reason.LONGEST_HISTORY -> R.string.sources_longest_history
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = listOfNotNull(
                                    stringResource(R.string.sources_days, writer.days, coverage.days),
                                    stringResource(R.string.sources_per_day, writer.perDay),
                                    writer.nightsWithStages?.let {
                                        stringResource(R.string.sources_stages, it, writer.records)
                                    },
                                    writer.since?.let {
                                        val zone = HealthRepository.DEFAULT_ZONE
                                        stringResource(R.string.sources_since, Formatting.date(it.atStartOfDay(zone).toInstant(), zone))
                                    },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                    }
                }
            }
            if (suggested != null && suggested != selected) {
                TextButton(onClick = { compare.onSelect(suggested) }) {
                    Text(stringResource(R.string.sources_show_only, context.appLabelFor(suggested)))
                }
            }
        }
    }
}
