package de.steppicrew.healthconnectview.ui.active

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.ActiveDay
import de.steppicrew.healthconnectview.health.ActiveMinutes
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.health.WHO_WEEKLY
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.components.InfoGroup
import de.steppicrew.healthconnectview.ui.components.InfoTitle
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.SpanSelector
import de.steppicrew.healthconnectview.ui.components.WindowStepper
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import de.steppicrew.healthconnectview.ui.components.rememberExplanation
import de.steppicrew.healthconnectview.ui.components.swipeToStep
import de.steppicrew.healthconnectview.ui.components.windowLabel
import de.steppicrew.healthconnectview.util.appLabelFor
import java.time.Duration
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * A week or four weeks of active minutes against the WHO's 150 a week: the total, a bar a day,
 * moderate below vigorous, and how the minutes were made. Estimated from heart rate, and the
 * screen says so; never a verdict on whether the week was enough.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveMinutesScreen(
    viewModel: ActiveMinutesViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnResume { viewModel.onResume() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.active_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SpanSelector(selected = state.span, onSelect = viewModel::select, spans = SPANS)
            WindowStepper(
                label = windowLabel(state.span, state.offset, state.anchor),
                canStepForward = state.canStepForward,
                onBack = { viewModel.step(back = true) },
                onForward = { viewModel.step(back = false) },
                onNow = viewModel::now,
            )
            val body = Modifier
                .fillMaxSize()
                .swipeToStep(state.canStepForward, { viewModel.step(back = true) }, { viewModel.step(back = false) })
            when (val read = state.read) {
                null -> LoadingView(body)
                ActiveRead.NoAccess -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_no_permission_title),
                    body = stringResource(R.string.active_no_access),
                    modifier = body,
                    actionLabel = stringResource(R.string.tile_grant),
                    onAction = onOpenPermissions,
                )
                ActiveRead.NoMax -> MessageView(
                    icon = Icons.Default.MonitorHeart,
                    title = stringResource(R.string.active_no_max_title),
                    body = stringResource(R.string.active_no_max),
                    modifier = body,
                    actionLabel = stringResource(R.string.settings_title),
                    onAction = onOpenSettings,
                )
                ActiveRead.Empty -> Message(stringResource(R.string.active_none), body)
                ActiveRead.Failed -> Message(stringResource(R.string.tile_failed), body)
                is ActiveRead.Data -> Figures(read.minutes, state.span, body)
            }
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Figures(minutes: ActiveMinutes, span: Span, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val weeks = minutes.days.chunked(DAYS_PER_WEEK)
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // A week's total against 150; over four weeks the mean of their totals, since the
        // guideline is per week and four weeks' sum against 600 reads as a different target.
        val weighted = if (span == Span.WEEK) minutes.weighted else (minutes.weighted.toFloat() / weeks.size.coerceAtLeast(1)).roundToInt()
        Text(
            text = Formatting.integer(weighted.toLong()),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(
                if (span == Span.WEEK) R.string.active_week_total else R.string.active_week_mean,
                Formatting.integer(WHO_WEEKLY.toLong()),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )

        ActiveBars(minutes.days, span, Modifier.padding(top = 16.dp).fillMaxWidth().height(BAR_HEIGHT.dp))
        Legend(minutes)

        if (span != Span.WEEK) {
            InfoGroup {
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = Formatting.dayAndMonth(week.first().date.atStartOfDay(ZONE).toInstant(), ZONE) + " – " +
                                Formatting.dayAndMonth(week.last().date.atStartOfDay(ZONE).toInstant(), ZONE),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(
                                R.string.active_of,
                                Formatting.integer(week.sumOf { it.weighted }.toLong()),
                                Formatting.integer(WHO_WEEKLY.toLong()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // A caveat that changes the figure stays open; how it is made folds out behind the "i".
        if (minutes.leftOut > 0) {
            InfoGroup {
                Text(
                    text = stringResource(R.string.active_left_out, Formatting.duration(Duration.ofMinutes(minutes.leftOut.toLong()))),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val explanation = rememberExplanation("active_minutes")
        InfoGroup {
            InfoTitle(stringResource(R.string.active_how_title), explanation)
            Text(
                text = stringResource(R.string.active_guideline),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (explanation.expanded == true) {
                Text(
                    text = stringResource(
                        if (minutes.limits.fromReserve) R.string.active_limits_reserve else R.string.active_limits_max,
                        Formatting.integer(kotlin.math.round(minutes.limits.moderate).toLong()),
                        Formatting.integer(kotlin.math.round(minutes.limits.vigorous).toLong()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.active_counted, LocalContext.current.appLabelFor(minutes.writer), Formatting.percent(minutes.coverage)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Moderate and vigorous minutes of the window, each beside its colour. */
@Composable
private fun Legend(minutes: ActiveMinutes) {
    val style = MaterialTheme.typography.bodyMedium
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            MaterialTheme.colorScheme.primary to stringResource(R.string.active_moderate, Formatting.duration(Duration.ofMinutes(minutes.moderate.toLong()))),
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.active_vigorous, Formatting.duration(Duration.ofMinutes(minutes.vigorous.toLong()))),
        ).forEach { (color, text) ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = firstLineInset(style, SWATCH.dp), end = 8.dp)
                        .size(SWATCH.dp)
                        .background(color, RoundedCornerShape(2.dp)),
                )
                Text(text, style = style)
            }
        }
    }
}

/**
 * A bar a day, moderate at the bottom and vigorous above, on a scale of the fullest day. Under
 * a week the days are named; four weeks are too narrow for that. The figures beside say the
 * same, so a screen reader skips the drawing.
 */
@Composable
internal fun ActiveBars(days: List<ActiveDay>, span: Span, modifier: Modifier) {
    val moderate = MaterialTheme.colorScheme.primary
    val vigorous = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val top = days.maxOfOrNull { it.moderate + it.vigorous }?.coerceAtLeast(MIN_SCALE) ?: MIN_SCALE
    val locale = LocalLocale.current.platformLocale
    Column(modifier.clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val slot = size.width / days.size
            val bar = slot * BAR_SHARE
            days.forEachIndexed { index, day ->
                val x = index * slot + (slot - bar) / 2
                fun rect(color: Color, from: Int, minutes: Int) {
                    if (minutes <= 0) return
                    val y0 = size.height * (1f - from.toFloat() / top)
                    val h = size.height * minutes.toFloat() / top
                    drawRoundRect(color, Offset(x, y0 - h), Size(bar, h), CornerRadius(2.dp.toPx()))
                }
                drawRect(track, Offset(x, size.height - 1.dp.toPx()), Size(bar, 1.dp.toPx()))
                rect(moderate, 0, day.moderate)
                rect(vigorous, day.moderate, day.vigorous)
            }
        }
        // Left to right like the drawing above, which does not mirror in a right-to-left language.
        if (span == Span.WEEK) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                days.forEach { day ->
                    Text(
                        text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val SPANS = listOf(Span.WEEK, Span.MONTH)
private val ZONE: ZoneId get() = ZoneId.systemDefault()
private const val DAYS_PER_WEEK = 7
private const val BAR_HEIGHT = 160
private const val BAR_SHARE = 0.6f
private const val MIN_SCALE = 30
private const val SWATCH = 12
