package de.steppicrew.healthconnectview.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * Pull the page down to read it again -- the owner's request, 09.10.2026, for a page left
 * open while a sync brings in something new.
 *
 * Built on the modifier rather than `PullToRefreshBox`, which cannot be switched off: the
 * dashboard's own drag to rearrange tiles must not start a reload.
 *
 * The indicator spins for at least [MIN_SPIN_MS] once a pull is released. A reload from shared
 * reads can finish in a frame or two, and the indicator then flicked half into view and away,
 * which the owner (14.10.2026) could not tell from a pull too short to reload at all.
 *
 * [indicatorPadding] keeps the spinning indicator clear of a top bar the box is laid out under.
 * Without it the indicator showed while dragged down and then came to rest behind the bar, so a
 * reload looked like nothing at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorPadding: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (holding) {
            delay(MIN_SPIN_MS)
            holding = false
        }
    }
    val shown = refreshing || holding
    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = shown,
            state = state,
            enabled = enabled,
            onRefresh = {
                holding = true
                onRefresh()
            },
        ),
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = shown,
            modifier = Modifier
                .padding(indicatorPadding)
                .align(Alignment.TopCenter),
        )
    }
}

/** The shortest time the indicator spins after a pull: long enough to be seen as a reload. */
private const val MIN_SPIN_MS = 1_000L
