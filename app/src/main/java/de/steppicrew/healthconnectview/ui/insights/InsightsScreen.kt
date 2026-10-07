package de.steppicrew.healthconnectview.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Trend
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.firstLineTextInset
import de.steppicrew.healthconnectview.ui.dashboard.icon
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * What moved this week, across every type: each one's last seven days against its thirty,
 * the most unusual first, and the types that held level named beneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onBack: () -> Unit,
    /** A type's own screen, on the four weeks the card compares. */
    onOpenType: (String) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnResume { viewModel.onResume() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
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
        if (state.noAccess) {
            MessageView(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.detail_no_permission_title),
                body = stringResource(R.string.detail_no_permission_body),
                modifier = Modifier.padding(padding),
                actionLabel = stringResource(R.string.history_grant),
                onAction = onOpenPermissions,
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            if (state.loading) {
                item(key = "progress") {
                    // A real fraction: one step per type read, so the bar moves as the list fills.
                    LinearProgressIndicator(
                        progress = { 1f - state.pending.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            items(state.notable, key = { it.spec.type.simpleName.orEmpty() }) { insight ->
                InsightCard(insight, onClick = { onOpenType(insight.spec.type.simpleName.orEmpty()) })
            }
            if (!state.loading && state.notable.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(if (state.insights.isEmpty()) R.string.insights_empty else R.string.insights_none),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            if (state.level.isNotEmpty()) {
                item(key = "level") {
                    val names = state.level.map { stringResource(it.spec.displayNameRes) }.sorted().joinToString(", ")
                    Text(
                        text = stringResource(R.string.insights_level, names),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            item(key = "rule") {
                Text(
                    text = stringResource(R.string.insights_rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

/** A type that moved: by how much, in a percentage where one means something, and the two averages. */
@Composable
private fun InsightCard(insight: Insight, onClick: () -> Unit) {
    val spec = insight.spec
    val trend = insight.trend
    val unit = spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty()
    val decimals = spec.insightDecimals
    val amount = insightAmount(insight)
    val titleStyle = MaterialTheme.typography.titleSmall

    InfoGroup(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = trend.direction.icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp, top = firstLineInset(titleStyle, ICON_SIZE.dp)),
            )
            Column(Modifier.padding(top = firstLineTextInset(titleStyle, ICON_SIZE.dp))) {
                Text(stringResource(spec.displayNameRes), style = titleStyle)
                Text(
                    text = stringResource(
                        if (trend.direction == Trend.UP) R.string.insight_above else R.string.insight_below,
                        amount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.trend_numbers,
                        Formatting.number(trend.recent, decimals) + unit,
                        Formatting.number(trend.baseline, decimals) + unit,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * How far the week moved, unsigned -- the arrow or the words carry the direction: a
 * percentage where the baseline gives one meaning, else the difference in the type's unit.
 */
@Composable
internal fun insightAmount(insight: Insight): String {
    val trend = insight.trend
    trend.percent?.let { return percent(abs(it)) }
    val unit = insight.spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty()
    return Formatting.number(abs(trend.recent - trend.baseline), insight.spec.insightDecimals) + unit
}

/**
 * As the type's own trend section shows its averages: by magnitude unless the unit fixes the
 * places, so "2.952 kcal", not "2.952,4 kcal".
 */
private val RecordTypeSpec<*>.insightDecimals: Int?
    get() = valueDecimals ?: if (tile.integralValues) 0 else null

/** "8 %", "8%" or "%8" as the language writes it, whole percent: a finer figure is noise here. */
private fun percent(value: Double): String =
    NumberFormat.getPercentInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }.format(value / 100.0)

private const val ICON_SIZE = 24
