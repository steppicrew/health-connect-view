package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.util.appLabelFor
import de.steppicrew.healthconnectview.ui.components.AppIcon
import de.steppicrew.healthconnectview.ui.components.rememberAppIcon
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.ProgressRing
import de.steppicrew.healthconnectview.ui.components.SparkCurve
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
    onOpenType: (String, String) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingGoalFor by remember { mutableStateOf<TileData?>(null) }
    var editing by remember { mutableStateOf(false) }
    var addingTile by remember { mutableStateOf(false) }

    if (addingTile) {
        AddTileDialog(
            candidates = viewModel.addableTypes(),
            onDismiss = { addingTile = false },
            onAdd = viewModel::addTile,
        )
    }

    editingGoalFor?.let { editing ->
        GoalDialog(
            typeName = editing.tile.typeName,
            displayName = stringResource(editing.spec.displayNameRes),
            currentGoal = editing.tile.effectiveGoal,
            onDismiss = { editingGoalFor = null },
            onSave = viewModel::setGoal,
        )
    }

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
                    if (editing) {
                        IconButton(onClick = { addingTile = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.dashboard_add_tile),
                            )
                        }
                        TextButton(onClick = { editing = false }) {
                            Text(stringResource(R.string.dashboard_done))
                        }
                    } else {
                        // Long-press also enters edit mode, but a gesture alone is
                        // undiscoverable and unreachable with accessibility services.
                        IconButton(onClick = { editing = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.dashboard_edit),
                            )
                        }
                        IconButton(onClick = onOpenPermissions) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.permissions_title),
                            )
                        }
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
                    TileCard(
                        data = tile,
                        editing = editing,
                        onClick = {
                            // In edit mode a tap must not navigate away: the user is arranging
                            // tiles, not reading them.
                            if (!editing) onOpenType(tile.tile.typeName, state.date.toString())
                        },
                        onLongClick = { editing = true },
                        onMoveUp = { viewModel.moveTile(tile.tile.typeName, forward = false) },
                        onMoveDown = { viewModel.moveTile(tile.tile.typeName, forward = true) },
                        onRemove = { viewModel.removeTile(tile.tile.typeName) },
                        onSetGoal = { editingGoalFor = tile },
                    )
                }
            }
        }
    }
}

/**
 * One tile. Only the number form is drawn today; ring and curve fall back to it, so a type
 * that declares them is already correct on screen and simply gains its shape later.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCard(
    data: TileData,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetGoal: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                if (editing) {
                    TileEditControls(
                        canSetGoal = data.spec.tile.form == TileSpec.Form.RING,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onRemove = onRemove,
                        onSetGoal = onSetGoal,
                    )
                } else {
                    TileBody(data)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                // Not for a session tile: its face is a count and its subtitle a duration, so
                // the type's own unit ("h", for sleep) would label neither of them.
                data.spec.unitRes?.takeIf { data.spec.tile.form != TileSpec.Form.SESSIONS }
                    ?.let { unit ->
                        Text(
                            text = stringResource(unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                // A filtered tile shows one app's figure, which differs from the combined
                // total the same tile shows unfiltered. Naming the source is what keeps that
                // difference explicable rather than looking like a wrong number.
                data.source?.let { packageName ->
                    // The app's icon rather than its name: on a tile this narrow "Garmin
                    // Connect" crowded out the unit beside it, and the point of the marker is
                    // only to say the figure is one app's rather than the combined total.
                    val icon = rememberAppIcon(packageName)
                    if (icon != null) {
                        AppIcon(
                            icon = icon,
                            packageName = packageName,
                            sizePx = TILE_SOURCE_ICON_PX,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(TILE_SOURCE_ICON.dp),
                        )
                    } else {
                        Text(
                            text = LocalContext.current.appLabelFor(packageName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Edit affordances shown in place of a tile's value.
 *
 * Reordering is by single steps rather than drag-and-drop: it needs no gesture to discover,
 * works with accessibility services, and cannot drop a tile into an unintended slot. Ordering
 * is the whole layout, so a mis-drop is not a trivial mistake to undo.
 */
@Composable
private fun TileEditControls(
    canSetGoal: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetGoal: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            IconButton(onClick = onMoveUp) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.tile_move_up),
                )
            }
            IconButton(onClick = onMoveDown) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.tile_move_down),
                )
            }
        }
        Row {
            if (canSetGoal) {
                IconButton(onClick = onSetGoal) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = stringResource(R.string.tile_set_goal),
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.tile_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Picks the renderer from the type's declared form. Each form falls back to the plain number
 * when its own requirements are not met -- a ring with no goal, a curve with too few readings
 * -- so a tile always shows something rather than an empty box.
 */
@Composable
private fun TileBody(data: TileData) {
    val progress = data.progress
    val scale = data.spec.tile.colorScale

    when {
        !data.granted -> TileValue(data)

        // Before the loading and null-value checks: a session tile never has a value, and
        // zero sessions is a real answer rather than an absence of data.
        data.spec.tile.form == TileSpec.Form.SESSIONS -> SessionCount(data)

        data.loading || data.value == null -> TileValue(data)

        data.spec.tile.form == TileSpec.Form.RING && progress != null ->
            ProgressRing(progress = progress, modifier = Modifier.fillMaxSize()) {
                TileValue(data)
            }

        data.spec.tile.form == TileSpec.Form.CURVE && scale != null && data.curve.size > 1 ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TileValue(data)
                SparkCurve(
                    points = data.curve,
                    scale = scale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CURVE_HEIGHT.dp)
                        .padding(top = 6.dp),
                )
            }

        else -> TileValue(data)
    }
}

/**
 * The day's session count, with everything they covered beneath it.
 *
 * Zero is shown as a word rather than as the missing-data dash: a day with no activities is a
 * fact about the day, not a gap in what was recorded. That is the opposite of the reasoning
 * for a measured type, where a null total genuinely means nothing was written.
 */
@Composable
private fun SessionCount(data: TileData) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (data.loading) {
            Text(
                text = stringResource(R.string.tile_no_data),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        if (data.sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.sessions_none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text = stringResource(R.string.sessions_count, data.sessions.size),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = Formatting.duration(data.sessionDuration),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The activities themselves, as far as they fit: two or three icons say "a ride and a
        // walk" where the bare count says only "two".
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            data.sessions.take(TILE_ICONS).forEach { session ->
                Icon(
                    imageVector = iconFor(session),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TILE_ICON_SIZE.dp),
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
private const val CURVE_HEIGHT = 28

/** How many activity icons fit on a tile face beside the count without crowding it. */
/** Just enough to recognise the app; the tile has little room to spare. */
private const val TILE_SOURCE_ICON = 16
private const val TILE_SOURCE_ICON_PX = 48

private const val TILE_ICONS = 3
private const val TILE_ICON_SIZE = 14
