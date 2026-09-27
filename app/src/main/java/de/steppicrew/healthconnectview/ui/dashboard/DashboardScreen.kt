package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.components.SessionTimeline
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.dashboard.TileFace
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Tune
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import de.steppicrew.healthconnectview.dashboard.SIZES
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import androidx.compose.foundation.background
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import de.steppicrew.healthconnectview.registry.ValueZones
import de.steppicrew.healthconnectview.health.pressureCategory
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.ui.components.SourceMark
import de.steppicrew.healthconnectview.health.Trend
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
    /** Type, the dashboard's date, and the window the tile describes. */
    onOpenType: (String, String, Span) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPermissions: () -> Unit,
    /** The permission list itself, one step closer than [onOpenPermissions]'s settings. */
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // The state flow itself rather than has(): it starts from what is already known, so an
    // owner's large tiles do not open as single cells and then jump.
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    val resizable = pro.allows(Feature.TILE_SIZES)
    var editingGoalFor by remember { mutableStateOf<TileData?>(null) }
    var editingZonesFor by remember { mutableStateOf<TileData?>(null) }
    var editingOptionsFor by remember { mutableStateOf<TileData?>(null) }
    var editing by remember { mutableStateOf(false) }

    // Edit mode is a mode on this screen rather than a destination, so the system Back
    // gesture would otherwise pass straight through it and leave the dashboard while the
    // tiles were still being arranged. Leaving the mode is what Back means here.
    BackHandler(enabled = editing) { editing = false }
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
            // Typed and shown in the chosen unit, stored metric: switching units later then
            // converts the goal rather than reinterpreting "5" as five of the new unit.
            currentGoal = editing.tile.effectiveGoal?.let { goal ->
                (editing.spec.display(goal) * 100).roundToInt() / 100.0
            },
            unit = editing.spec.displayUnitRes?.let { stringResource(it) },
            onDismiss = { editingGoalFor = null },
            onSave = { typeName, goal -> viewModel.setGoal(typeName, goal?.let(editing.spec::toMetric)) },
        )
    }

    editingZonesFor?.let { editing ->
        ZonesDialog(
            typeName = editing.tile.typeName,
            displayName = stringResource(editing.spec.displayNameRes),
            currentZones = editing.tile.zones,
            defaultZones = editing.spec.tile.defaultZones,
            onDismiss = { editingZonesFor = null },
            onSave = viewModel::setZones,
        )
    }

    editingOptionsFor?.let { editing ->
        TileOptionsDialog(
            displayName = stringResource(editing.spec.displayNameRes),
            currentSpan = editing.tile.span,
            currentFace = editing.tile.face,
            onDismiss = { editingOptionsFor = null },
            onSave = { span, face -> viewModel.setOptions(editing.tile.typeName, span, face) },
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
                    // Stepping the day while arranging tiles reloads the grid under the
                    // drag in progress, so the arrows are inert in edit mode rather than
                    // hidden: a top bar whose buttons move as the mode changes is worse.
                    IconButton(
                        onClick = viewModel::showPreviousDay,
                        enabled = !editing,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.dashboard_previous_day),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showNextDay,
                        enabled = state.canStepForward && !editing,
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

            else -> TileGrid(
                // Without Pro every tile is drawn as one cell, but the stored sizes are kept: a
                // refund or a restored backup should not cost the layout, and buying Pro again
                // brings it back as it was.
                sizes = state.tiles.map { if (resizable) it.tile.width to it.tile.height else 1 to 1 },
                spacing = 12.dp,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                state.tiles.forEach { tile ->
                    // Keyed so a tile keeps its own state when a move or resize reorders the
                    // children, as the lazy grid's item keys did.
                    key(tile.tile.typeName) {
                        TileCard(
                            data = tile,
                            editing = editing,
                            resizable = resizable,
                            onClick = {
                                // In edit mode a tap must not navigate away: the user is
                                // arranging tiles, not reading them.
                                // It opens on the window the tile shows, so the figure under
                                // the finger is the one at the top of the screen it opens.
                                if (!editing) onOpenType(tile.tile.typeName, state.date.toString(), tile.shownSpan)
                            },
                            onLongClick = { editing = true },
                            onMoveUp = { viewModel.moveTile(tile.tile.typeName, forward = false) },
                            onMoveDown = { viewModel.moveTile(tile.tile.typeName, forward = true) },
                            onResize = {
                                // Locked, the button is where the purchase starts, as the
                                // export menu's locked entries are.
                                if (resizable) {
                                    viewModel.resizeTile(tile.tile.typeName)
                                } else {
                                    activity?.let(AppEntitlements.current::buy)
                                }
                            },
                            onRemove = { viewModel.removeTile(tile.tile.typeName) },
                            onSetGoal = { editingGoalFor = tile },
                            onSetZones = { editingZonesFor = tile },
                            onSetOptions = { editingOptionsFor = tile },
                            onGrantAccess = onGrantAccess,
                        )
                    }
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
    resizable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    onSetGoal: () -> Unit,
    onSetZones: () -> Unit,
    onSetOptions: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    // The grid hands every tile its exact size, square or spanning; the card only fills it.
    Card(
        modifier = Modifier
            .fillMaxSize()
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
            // A tile showing more than the day says which window: "Schritte" over a week's
            // total would otherwise read as today's.
            val name = stringResource(data.spec.displayNameRes)
            Text(
                text = when {
                    data.shownSpan == Span.DAY -> name
                    // Without the history permission a year holds 30 days; the detail screen
                    // warns in red, and a tile titled "Year" over a month would not.
                    data.chart?.historyCapped == true ->
                        stringResource(R.string.tile_title_span_capped, name, stringResource(data.shownSpan.labelRes))
                    else -> stringResource(R.string.tile_title_span, name, stringResource(data.shownSpan.labelRes))
                },
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
                        // Only where a curve is actually coloured by them; a ring or a plain
                        // number has nothing for zones to change.
                        canSetZones = data.spec.tile.defaultZones != null,
                        // Only where there is room to use them; a single cell always shows
                        // the day's value.
                        canSetOptions = resizable && data.tile.isLarge,
                        resizable = resizable,
                        width = data.tile.width,
                        height = data.tile.height,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onResize = onResize,
                        onRemove = onRemove,
                        onSetGoal = onSetGoal,
                        onSetZones = onSetZones,
                        onSetOptions = onSetOptions,
                    )
                } else {
                    TileBody(data, large = resizable && data.tile.height > 1, onGrantAccess)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Not for a session tile: its face is a count and its subtitle a duration, so
                // the type's own unit ("h", for sleep) would label neither of them.
                data.spec.displayUnitRes?.takeIf { data.spec.tile.form != TileSpec.Form.SESSIONS }
                    ?.let { unit ->
                        Text(
                            text = stringResource(unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                data.trend?.takeIf { data.granted && !data.loading }?.let { TrendMark(it) }
                // An explicit spacer rather than SpaceBetween, because the unit above is
                // conditional: with it absent -- every SESSIONS tile, so Sleep and Activities
                // -- the source marker was the row's only child and SpaceBetween put it at
                // the start, so those two tiles named their app bottom-left while every other
                // tile named it bottom-right. Weight keeps the gap whether or not the unit
                // rendered, which pins the marker to the end in both cases.
                Spacer(modifier = Modifier.weight(1f))
                // A filtered tile shows one app's figure, which differs from the combined
                // total the same tile shows unfiltered. Naming the source is what keeps that
                // difference explicable rather than looking like a wrong number.
                data.source?.let { packageName ->
                    // The app's icon rather than its name: on a tile this narrow "Garmin
                    // Connect" crowded out the unit beside it, and the point of the marker is
                    // only to say the figure is one app's rather than the combined total.
                    SourceMark(
                        packageName,
                        TILE_SOURCE_ICON,
                        TILE_SOURCE_ICON_PX,
                        Modifier.padding(start = 4.dp),
                    ) {
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
 *
 * Resizing is a button for the same reasons, stepping through the sizes rather than dragging
 * a corner. It also works where a drag cannot be tested: the Xiaomi refuses injected input.
 *
 * A flowing row, because a resized tile has room for all of them on one line while a square
 * one needs two -- and on the narrowest phones, three.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileEditControls(
    canSetGoal: Boolean,
    canSetZones: Boolean,
    canSetOptions: Boolean,
    resizable: Boolean,
    width: Int,
    height: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    onSetGoal: () -> Unit,
    onSetZones: () -> Unit,
    onSetOptions: () -> Unit,
) {
    val size = stringResource(R.string.tile_size, width, height)
    val resize = stringResource(R.string.tile_resize)
    FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
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
        IconButton(
            onClick = onResize,
            modifier = if (resizable) Modifier.semantics { stateDescription = size } else Modifier,
        ) {
            // The arrows point the way the next tap goes: outwards until the largest
            // size, then inwards back to a single cell.
            val largest = resizable && (width to height) == SIZES.last()
            // Locked, it stays in place with a padlock: a control that silently vanishes
            // cannot be asked about.
            BadgedBox(
                badge = {
                    if (!resizable) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(LOCK_BADGE.dp))
                    }
                },
            ) {
                Icon(
                    imageVector = if (largest) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    contentDescription = if (resizable) resize else stringResource(R.string.export_premium, resize),
                )
            }
        }
        if (canSetGoal) {
            IconButton(onClick = onSetGoal) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = stringResource(R.string.tile_set_goal),
                )
            }
        }
        if (canSetZones) {
            IconButton(onClick = onSetZones) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = stringResource(R.string.tile_set_zones),
                )
            }
        }
        if (canSetOptions) {
            IconButton(onClick = onSetOptions) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.tile_options),
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

/**
 * Picks the renderer from the type's declared form. Each form falls back to the plain number
 * when its own requirements are not met -- a ring with no goal, a curve with too few readings
 * -- so a tile always shows something rather than an empty box.
 */
@Composable
private fun TileBody(data: TileData, large: Boolean, onGrantAccess: () -> Unit) {
    val progress = data.progress
    // The user's bands where they set them; the type's defaults otherwise.
    val zones = data.tile.effectiveZones

    val chart = data.chart?.takeIf { it.points.isNotEmpty() || it.sessions.isNotEmpty() }
    when {
        !data.granted -> LockedTile(onGrantAccess)

        chart != null && data.tile.face == TileFace.CHART ->
            TileChart(chart, Modifier.fillMaxSize(), compactAxis = data.tile.height == 1)

        chart != null && data.tile.face == TileFace.BOTH -> TileValueAndChart(data, chart)

        // Before the loading and null-value checks: a session tile never has a value, and
        // zero sessions is a real answer rather than an absence of data.
        data.spec.tile.form == TileSpec.Form.SESSIONS -> SessionCount(data)

        data.loading || data.value == null -> TileValue(data, large)

        data.spec.tile.form == TileSpec.Form.RING && progress != null ->
            ProgressRing(progress = progress, modifier = Modifier.fillMaxSize()) {
                TileValue(data, large)
            }

        data.spec.tile.form == TileSpec.Form.CURVE && zones != null && data.curve.size > 1 ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TileValue(data, large)
                // A tall tile gives the curve the room below the number: the day's shape is
                // what the extra height is for, and a larger number would say nothing more.
                SparkCurve(
                    points = data.curve,
                    zones = zones,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (large) Modifier.weight(1f) else Modifier.height(CURVE_HEIGHT.dp))
                        .padding(top = 6.dp),
                )
            }

        else -> TileValue(data, large)
    }
}

/**
 * The window's chart filling the tile: the detail screen's chart, with its axis values and
 * goal line, minus the touch readout -- a tap here opens that screen.
 */
@Composable
private fun TileChart(chart: TileDetailData, modifier: Modifier, compactAxis: Boolean = false) {
    val extent = chart.extent
    if (chart.spec.tile.form == TileSpec.Form.SESSIONS && extent != null) {
        // A session type's day has no series; its sessions on the timeline are the chart.
        SessionTimeline(sessions = chart.sessions, extent = extent, fillHeight = true, modifier = modifier)
    } else {
        DataLineChart(chart, modifier, interactive = false, fillHeight = true, compactAxis = compactAxis)
    }
}

/**
 * The number beside the chart on a wide tile, above it on a tall one: a 2x1 tile is too short
 * to stack both and leave the chart anything to show.
 */
@Composable
private fun TileValueAndChart(data: TileData, chart: TileDetailData) {
    if (data.tile.height > 1) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            TileValue(data)
            TileChart(chart, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
        }
    } else {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(VALUE_SHARE), contentAlignment = Alignment.Center) {
                TileValue(data)
            }
            TileChart(chart, Modifier.weight(1f - VALUE_SHARE).fillMaxHeight(), compactAxis = true)
        }
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

/**
 * Which way the last week went against the month before it. Drawn in the tile's quiet colour,
 * never red or green: up is good for steps and bad for resting heart rate, and the arrow does
 * not know which it is looking at.
 */
@Composable
private fun TrendMark(trend: Trend) {
    val (icon, description) = when (trend) {
        Trend.UP -> Icons.AutoMirrored.Filled.TrendingUp to R.string.trend_up
        Trend.FLAT -> Icons.AutoMirrored.Filled.TrendingFlat to R.string.trend_flat
        Trend.DOWN -> Icons.AutoMirrored.Filled.TrendingDown to R.string.trend_down
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(TREND_ICON.dp),
    )
}

/**
 * A tile whose type is not granted, with the way to change that on the tile itself.
 *
 * Tapping the tile opens its detail, which only repeats "not allowed"; the grant lives two
 * screens away in settings. The button goes straight to the permission list, which is the only
 * thing a locked tile can usefully lead to.
 */
@Composable
private fun LockedTile(onGrantAccess: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(LOCK_ICON.dp),
        )
        Text(
            text = stringResource(R.string.tile_locked),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onGrantAccess) {
            Text(stringResource(R.string.tile_grant))
        }
    }
}

@Composable
private fun TileValue(data: TileData, large: Boolean = false) {
    // A tall tile's number grows with it; left at tile size it sat lost in the middle of a
    // ring four times the area.
    val valueStyle = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium
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

        data.valueDate != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Muted and dated: a carried weight is the latest known, not today's, and must not
            // read as a measurement taken on the day on screen.
            Text(
                text = tileValueText(data.value, data.secondaryValue),
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.tile_value_as_of,
                    data.valueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Two values read as one: the grade's colour beside them, as on the detail screen.
        data.secondaryValue != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(10.dp)
                    .background(
                        ValueZones.ZONE_COLORS[pressureCategory(data.value, data.secondaryValue).ordinal],
                        CircleShape,
                    ),
            )
            Text(
                text = tileValueText(data.value, data.secondaryValue),
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        else -> Text(
            text = Formatting.number(data.value),
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "128/82" for a pair, whole numbers as a cuff shows them; the plain number otherwise. */
private fun tileValueText(value: Double, secondary: Double?): String =
    if (secondary == null) Formatting.number(value) else "${value.roundToInt()}/${secondary.roundToInt()}"

@Composable
private fun dayLabel(date: LocalDate): String =
    if (date == LocalDate.now()) {
        stringResource(R.string.dashboard_title)
    } else {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

private const val CURVE_HEIGHT = 28

/** How much of a wide tile's width the number takes beside its chart. */
private const val VALUE_SHARE = 0.35f

/** How many activity icons fit on a tile face beside the count without crowding it. */
/** Just enough to recognise the app; the tile has little room to spare. */
private const val TILE_SOURCE_ICON = 16
private const val TREND_ICON = 16
private const val LOCK_ICON = 20
private const val LOCK_BADGE = 12
private const val TILE_SOURCE_ICON_PX = 48

private const val TILE_ICONS = 3
private const val TILE_ICON_SIZE = 14
