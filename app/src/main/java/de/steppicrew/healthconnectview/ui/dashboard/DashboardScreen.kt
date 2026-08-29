package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The app's start screen: the stats the user pinned, for one day at a time.
 *
 * Every tile renders through one path driven by the type's TileSpec, so adding a type or
 * changing how it is drawn never touches this file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenType: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Permissions can be changed in system settings while backgrounded, so the day is
    // reloaded on every return rather than trusted from when the screen was built.
    OnResume { viewModel.refresh() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(dayLabel(state.date)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::showPreviousDay) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.dashboard_previous_day),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showNextDay,
                        enabled = state.canStepForward,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.dashboard_next_day),
                        )
                    }
                    IconButton(onClick = onOpenCatalog) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.dashboard_open_catalog),
                        )
                    }
                    IconButton(onClick = onOpenPermissions) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.permissions_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingView(Modifier.padding(padding))

            state.availability != Availability.Available -> MessageView(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.availability_missing_title),
                body = stringResource(R.string.availability_missing_body),
                modifier = Modifier.padding(padding),
            )

            state.tiles.isEmpty() -> MessageView(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.dashboard_empty_title),
                body = stringResource(R.string.dashboard_empty_body),
                modifier = Modifier.padding(padding),
            )

            // Distinct from "no tiles": the dashboard is configured, but nothing on it may be
            // read yet. Sending the user to the type list would be a dead end.
            state.tiles.none { it.granted } -> MessageView(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.detail_no_permission_title),
                body = stringResource(R.string.detail_no_permission_body),
                modifier = Modifier.padding(padding),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(TILE_COLUMNS),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.tiles, key = { it.tile.typeName }) { tile ->
                    TileCard(data = tile, onClick = { onOpenType(tile.tile.typeName) })
                }
            }
        }
    }
}

/**
 * One tile. Only the number form is drawn today; ring and curve fall back to it, so a type
 * that declares them is already correct on screen and simply gains its shape later.
 */
@Composable
private fun TileCard(data: TileData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(data.spec.displayNameRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                TileValue(data)
            }

            data.spec.unitRes?.let { unit ->
                Text(
                    text = stringResource(unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TileValue(data: TileData) {
    when {
        // "Not allowed to look" and "nothing here" need different words: showing a dash for a
        // locked type would read as an empty day rather than a missing permission.
        !data.granted -> Text(
            text = stringResource(R.string.tile_locked),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        data.loading -> Text(
            text = stringResource(R.string.tile_no_data),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // A missing value is not zero. Rendering null as "0" would claim the user took no
        // steps when in fact nothing was recorded.
        data.value == null -> Text(
            text = stringResource(R.string.tile_no_data),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Text(
            text = Formatting.number(data.value),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun dayLabel(date: LocalDate): String =
    if (date == LocalDate.now()) {
        stringResource(R.string.dashboard_title)
    } else {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

private const val TILE_COLUMNS = 2
