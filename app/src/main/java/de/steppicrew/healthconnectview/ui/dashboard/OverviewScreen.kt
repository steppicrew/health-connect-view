package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.countsFromZero
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.insights.InsightStrip
import de.steppicrew.healthconnectview.ui.insights.colourAt
import java.time.LocalDate
import java.time.format.TextStyle as DayNameStyle

/**
 * The overview tile at length: each chosen type's figure for the dashboard's day, where it
 * sits against the reader's usual range -- in words and on the strip -- and its week, larger
 * and with the days named. The figures are the tile's own, from the dashboard's view model, so
 * the screen and the tile cannot disagree and nothing is read twice.
 *
 * A row opens its type on the day, its week the type's week, as on the tile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: DashboardViewModel,
    onOpenType: (type: String, date: String, span: Span) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = state.overview?.rows.orEmpty()
    val date = state.date
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.overview_tile_title))
                        Text(
                            text = dayLabel(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            itemsIndexed(rows, key = { index, row -> "$index-" + row.data.spec.type.simpleName }) { _, row ->
                val type = row.data.spec.type.simpleName.orEmpty()
                OverviewDetailRow(
                    row = row,
                    date = date,
                    onOpenDay = { onOpenType(type, date.toString(), Span.DAY) },
                    onOpenWeek = { onOpenType(type, date.toString(), Span.WEEK) },
                )
            }
        }
    }
}

/** One type: its name, the day's figure, the usual range in words and on the strip, the week. */
@Composable
private fun OverviewDetailRow(row: OverviewRow, date: LocalDate, onOpenDay: () -> Unit, onOpenWeek: () -> Unit) {
    val spec = row.data.spec
    val titleStyle = MaterialTheme.typography.titleSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    InfoGroup(Modifier.clickable(onClick = onOpenDay)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = iconFor(spec),
                contentDescription = null,
                tint = muted,
                modifier = Modifier
                    .padding(end = 8.dp, top = firstLineInset(titleStyle, DETAIL_ICON.dp))
                    .size(DETAIL_ICON.dp),
            )
            Column(Modifier.weight(1f).padding(top = firstLineTextInset(titleStyle, DETAIL_ICON.dp))) {
                Text(stringResource(spec.displayNameRes), style = titleStyle)
                Row(verticalAlignment = Alignment.Top) {
                    CellValue(row, MaterialTheme.typography.headlineSmall, withUnit = true, modifier = Modifier.weight(1f, fill = false))
                    if (row.goalReached) GoalMark(row, MaterialTheme.typography.headlineSmall, DETAIL_ICON.dp)
                }
                goalReachedText(row)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                val spreads = row.spreads
                if (row.unusual && spreads != null) {
                    Text(
                        text = stringResource(if (spreads > 0) R.string.overview_unusual_high else R.string.overview_unusual_low),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colourAt(spreads),
                    )
                }
                usualOnly(row)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                row.usual?.let { InsightStrip(it, Modifier.padding(top = 8.dp)) }
                // Not an empty frame with seven day names under it where the week has nothing.
                if (row.data.granted && !row.data.failed && row.week?.any { it != null } != false) {
                    Column(Modifier.padding(top = 12.dp).clickable(onClick = onOpenWeek)) {
                        WeekChart(
                            row.week,
                            fromZero = countsFromZero(spec),
                            modifier = Modifier.fillMaxWidth().height(DETAIL_WEEK_HEIGHT.dp),
                        )
                        WeekdayLabels(date)
                    }
                }
            }
        }
    }
}

/** The seven days under a week chart, each under its own slot, the day shown last. */
@Composable
private fun WeekdayLabels(date: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        repeat(WEEK_DAYS) { at ->
            val day = date.minusDays((WEEK_DAYS - 1 - at).toLong())
            Text(
                text = day.dayOfWeek.getDisplayName(DayNameStyle.SHORT, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val DETAIL_ICON = 24
private const val DETAIL_WEEK_HEIGHT = 72
