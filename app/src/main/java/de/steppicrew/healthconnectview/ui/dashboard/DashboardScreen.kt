package de.steppicrew.healthconnectview.ui.dashboard

import de.steppicrew.healthconnectview.ui.SharedDate
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.dashboard.companionsOf
import de.steppicrew.healthconnectview.dashboard.offersMean
import androidx.compose.material.icons.filled.Insights
import de.steppicrew.healthconnectview.dashboard.TileColor
import de.steppicrew.healthconnectview.dashboard.facesFor
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import de.steppicrew.healthconnectview.billing.AppEntitlements
import de.steppicrew.healthconnectview.billing.Feature
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.steppicrew.healthconnectview.R
import kotlin.math.floor
import kotlin.math.roundToInt
import de.steppicrew.healthconnectview.health.Availability
import de.steppicrew.healthconnectview.ui.components.SourceMark
import de.steppicrew.healthconnectview.registry.Formatting
import de.steppicrew.healthconnectview.health.HealthRepository
import de.steppicrew.healthconnectview.registry.TileSpec
import de.steppicrew.healthconnectview.util.appLabelFor
import de.steppicrew.healthconnectview.ui.components.DayPickerDialog
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.ui.components.RefreshBox
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.SlowLoadingPill
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume

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
    onOpenSettings: () -> Unit,
    /** The permission list itself, one step closer than [onOpenSettings]. */
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    /** The insights tile's full list. */
    onOpenInsights: () -> Unit = {},
    onOpenCycle: () -> Unit = {},
    /** The body composition screen, which a weight tile's body face opens. */
    onOpenBody: () -> Unit = {},
    /** One workout's screen, which a workout tile showing a single workout opens. */
    onOpenSession: (Session) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A day picked or stepped to on a detail screen is the dashboard's day on return.
    val sharedDate by SharedDate.date.collectAsStateWithLifecycle()
    LaunchedEffect(sharedDate) { viewModel.showDate(sharedDate) }
    val activity = LocalActivity.current
    // The state flow itself rather than has(): it starts from what is already known, so an
    // owner's large tiles do not open as single cells and then jump.
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    val resizable = pro.allows(Feature.TILE_SIZES)
    val repeatable = pro.allows(Feature.TILE_REPEAT)
    val colorsUnlocked = pro.allows(Feature.TILE_COLORS)
    // The tile and its name: the week's changes and the cycle tile are named by no type.
    var editingColorFor by remember { mutableStateOf<Pair<Tile, Int>?>(null) }
    var editingGoalFor by remember { mutableStateOf<TileData?>(null) }
    var editingZonesFor by remember { mutableStateOf<TileData?>(null) }
    var editingOptionsFor by remember { mutableStateOf<TileData?>(null) }
    var editingPicksFor by remember { mutableStateOf<InsightsTileData?>(null) }
    var editing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val drag = remember(scroll) { TileDragState(scroll) }
    val density = LocalDensity.current
    LaunchedEffect(drag.dragging) {
        if (drag.dragging != null) {
            with(density) { drag.autoScroll(DRAG_EDGE.dp.toPx(), DRAG_SCROLL_STEP.dp.toPx(), DRAG_GRIP_Y.dp.toPx()) }
        }
    }
    // Leaving edit mode mid-drag keeps the order the drag had reached.
    LaunchedEffect(editing) { if (!editing) drag.end()?.let(viewModel::reorder) }

    // Edit mode is a mode on this screen rather than a destination, so the system Back
    // gesture would otherwise pass straight through it and leave the dashboard while the
    // tiles were still being arranged. Leaving the mode is what Back means here.
    BackHandler(enabled = editing) { editing = false }
    var addingTile by remember { mutableStateOf(false) }
    val cycleRecorded by viewModel.cycleRecorded.collectAsStateWithLifecycle()
    // Looked up as the picker opens: the cycle tile is offered where a period was recorded.
    LaunchedEffect(addingTile) { if (addingTile) viewModel.checkCycleRecorded() }

    if (addingTile) {
        AddTileDialog(
            candidates = viewModel.addableTypes(),
            repeatUnlocked = repeatable,
            onDismiss = { addingTile = false },
            onAdd = { viewModel.addTile(it, before = drag.firstInView(state.layout)) },
            onBuy = { activity?.let(AppEntitlements.current::buy) },
            insightsOffered = !viewModel.hasInsightsTile,
            insightsUnlocked = pro.allows(Feature.INSIGHTS),
            cycleOffered = cycleRecorded && !viewModel.hasCycleTile,
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
            onSave = { typeName, choice ->
                viewModel.setGoal(
                    typeName,
                    choice.goal?.let(editing.spec::toMetric),
                    choice.adaptive,
                    choice.floor?.let(editing.spec::toMetric),
                )
            },
            adaptiveOffered = editing.spec.tile.adaptiveRule != null,
            currentAdaptive = editing.tile.adaptiveGoal,
            currentFloor = editing.tile.goalFloor?.let { (editing.spec.display(it) * 100).roundToInt() / 100.0 },
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

    editingColorFor?.let { (tile, name) ->
        TileColorDialog(
            displayName = stringResource(name),
            current = tile.color,
            onDismiss = { editingColorFor = null },
            onPick = { viewModel.setColor(tile.id, it) },
        )
    }

    editingPicksFor?.let { editing ->
        InsightPicksDialog(
            candidates = editing.insights.orEmpty().map { it.spec.type.simpleName.orEmpty() },
            current = editing.tile.picks,
            onDismiss = { editingPicksFor = null },
            onSave = { viewModel.setInsightPicks(editing.tile.id, it) },
        )
    }
    editingOptionsFor?.let { editing ->
        TileOptionsDialog(
            displayName = stringResource(editing.spec.displayNameRes),
            currentSpan = editing.tile.span,
            currentFace = editing.tile.face,
            companions = companionsOf(editing.tile.typeName),
            currentCompanion = editing.tile.companion,
            faces = facesFor(editing.tile.typeName),
            // A 2x2 body face draws its window; a 2x1 one shows the latest numbers alone.
            bodyHasWindow = editing.tile.height > 1,
            onDismiss = { editingOptionsFor = null },
            currentCalendarYear = editing.tile.calendarYear,
            meanOffered = offersMean(editing.tile.typeName),
            currentMean = editing.tile.mean,
            onSave = { span, face, companion, calendarYear, mean ->
                viewModel.setOptions(editing.tile.id, span, face, companion, calendarYear, mean)
            },
        )
    }

    // Permissions can be changed in system settings while backgrounded, so the day is
    // reloaded on every return rather than trusted from when the screen was built.
    OnResume { viewModel.refresh() }
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    var pickingDay by remember { mutableStateOf(false) }
    if (pickingDay) {
        DayPickerDialog(
            initial = state.date,
            onDismiss = { pickingDay = false },
            onPicked = {
                pickingDay = false
                viewModel.showDate(it)
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // Tapping the day opens a calendar, for a day too far back to step to.
                title = {
                    Text(
                        text = dayLabel(state.date),
                        modifier = Modifier.clickable(
                            enabled = !editing,
                            onClickLabel = stringResource(R.string.date_pick),
                        ) { pickingDay = true },
                    )
                },
                navigationIcon = {
                    // Stepping the day while arranging tiles reloads the grid under the
                    // drag in progress, so the arrows are inert in edit mode rather than
                    // hidden: a top bar whose buttons move as the mode changes is worse.
                    IconButton(
                        onClick = viewModel::showPreviousDay,
                        enabled = !editing,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
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
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
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
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        RefreshBox(
            refreshing = refreshing,
            onRefresh = viewModel::pullRefresh,
            // Not while arranging tiles: the drag that moves one must not start a reload.
            enabled = !editing,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = PaddingValues(top = padding.calculateTopPadding()),
        ) {
            when {
                state.loading -> LoadingView(Modifier.padding(padding))
    
                state.availability != Availability.Available -> MessageView(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.availability_missing_title),
                    body = stringResource(R.string.availability_missing_body),
                    modifier = Modifier.padding(padding),
                )
    
                state.tiles.isEmpty() && state.insights == null && state.cycle == null -> MessageView(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.dashboard_empty_title),
                    body = stringResource(R.string.dashboard_empty_body),
                    modifier = Modifier.padding(padding),
                )
    
                // Distinct from "no tiles": the dashboard is configured, but nothing on it may be
                // read yet. Sending the user to the type list would be a dead end.
                state.insights == null && state.cycle == null && state.tiles.none { it.granted } -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_no_permission_title),
                    body = stringResource(R.string.detail_no_permission_body),
                    modifier = Modifier.padding(padding),
                )
    
                else -> {
                    // Every tile in the stored order, the insights tile among the types'; during a
                    // drag, in the order the drag has reached.
                    val entries = remember(state.tiles, state.insights, state.cycle, state.layout) {
                        val all = state.tiles.map(GridEntry::Type) +
                            listOfNotNull(state.insights?.let(GridEntry::Insights), state.cycle?.let(GridEntry::Cycle))
                        val position = state.layout.withIndex().associate { (index, id) -> id to index }
                        all.sortedBy { position[it.id] ?: Int.MAX_VALUE }
                    }
                    val shownTiles = drag.order?.let { ids ->
                        val byId = entries.associateBy { it.id }
                        ids.mapNotNull(byId::get) + entries.filterNot { it.id in ids }
                    } ?: entries
                    // Without Pro every tile is drawn as one cell, but the stored sizes are kept: a
                    // refund or a restored backup should not cost the layout, and buying Pro again
                    // brings it back as it was.
                    fun drawnSize(entry: GridEntry) = if (resizable) entry.tile.width to entry.tile.height else 1 to 1
                    TileGrid(
                        sizes = shownTiles.map(::drawnSize),
                        spacing = 12.dp,
                        contentPadding = PaddingValues(12.dp),
                        scrollState = scroll,
                        onMetrics = { drag.metrics = it },
                        // Scrolling with the tiles, so it never stands between someone and
                        // them. Not while arranging.
                        header = { if (!editing) WhatsNewCard() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .onSizeChanged { drag.viewport = it.height },
                    ) {
                        shownTiles.forEach { entry ->
                            // Keyed so a tile keeps its own state when a move or resize reorders the
                            // children, as the lazy grid's item keys did.
                            key(entry.id) {
                                val id = entry.id
                                val lifted = drag.dragging == id
                                val placed = Modifier
                                    .onGloballyPositioned { drag.bounds[id] = it.boundsInParent() }
                                    .zIndex(if (lifted) 1f else 0f)
                                    .graphicsLayer {
                                        val moved = drag.translation(id)
                                        translationX = moved.x
                                        translationY = moved.y
                                        if (lifted) {
                                            scaleX = LIFTED_SCALE
                                            scaleY = LIFTED_SCALE
                                            shadowElevation = LIFTED_ELEVATION.dp.toPx()
                                        }
                                    }
                                val startDrag = {
                                    drag.start(
                                        id,
                                        shownTiles.map { it.id },
                                        shownTiles.associate { it.id to drawnSize(it) },
                                    )
                                }
                                if (entry is GridEntry.Cycle) {
                                    TileColored(if (colorsUnlocked) entry.data.tile.color else TileColor.DEFAULT) { SharedTileCard(
                                        tile = entry.data.tile,
                                        title = stringResource(R.string.cycle_tile_title),
                                        colorsUnlocked = colorsUnlocked,
                                        onSetColor = {
                                            if (colorsUnlocked) {
                                                editingColorFor = entry.data.tile to R.string.cycle_tile_title
                                            } else {
                                                activity?.let(AppEntitlements.current::buy)
                                            }
                                        },
                                        caption = entry.data.now?.let { now ->
                                            stringResource(R.string.cycle_tile_since, Formatting.dayAndMonth(now.start.atStartOfDay(HealthRepository.DEFAULT_ZONE).toInstant(), HealthRepository.DEFAULT_ZONE))
                                        }.orEmpty(),
                                        modifier = placed,
                                        editing = editing,
                                        onDragStart = startDrag,
                                        onDrag = drag::drag,
                                        onDragEnd = { drag.end()?.let(viewModel::reorder) },
                                        onClick = { if (!editing) onOpenCycle() },
                                        onLongClick = { editing = true },
                                        onMoveUp = { viewModel.moveTile(id, forward = false) },
                                        onMoveDown = { viewModel.moveTile(id, forward = true) },
                                        onRemove = { viewModel.removeTile(id) },
                                        resizable = resizable,
                                        onResize = {
                                            if (resizable) viewModel.resizeTile(id) else activity?.let(AppEntitlements.current::buy)
                                        },
                                    ) { width, height -> CycleTileBody(entry.data, width, height) } }
                                } else if (entry is GridEntry.Insights) {
                                    TileColored(if (colorsUnlocked) entry.data.tile.color else TileColor.DEFAULT) { SharedTileCard(
                                        tile = entry.data.tile,
                                        title = stringResource(R.string.insights_tile_title),
                                        colorsUnlocked = colorsUnlocked,
                                        onSetColor = {
                                            if (colorsUnlocked) {
                                                editingColorFor = entry.data.tile to R.string.insights_tile_title
                                            } else {
                                                activity?.let(AppEntitlements.current::buy)
                                            }
                                        },
                                        caption = stringResource(R.string.insights_tile_caption),
                                        modifier = placed,
                                        editing = editing,
                                        onDragStart = startDrag,
                                        onDrag = drag::drag,
                                        onDragEnd = { drag.end()?.let(viewModel::reorder) },
                                        onClick = {
                                            when {
                                                editing -> Unit
                                                entry.data.locked -> activity?.let(AppEntitlements.current::buy)
                                                else -> onOpenInsights()
                                            }
                                        },
                                        onLongClick = { editing = true },
                                        onMoveUp = { viewModel.moveTile(id, forward = false) },
                                        onMoveDown = { viewModel.moveTile(id, forward = true) },
                                        onRemove = { viewModel.removeTile(id) },
                                        resizable = resizable,
                                        // Which types, in which order: Pro like the tile, so offered only with it.
                                        canSetOptions = !entry.data.locked,
                                        onSetOptions = { editingPicksFor = entry.data },
                                        onResize = {
                                            if (resizable) viewModel.resizeTile(id) else activity?.let(AppEntitlements.current::buy)
                                        },
                                    ) { width, height -> InsightsTileBody(entry.data, width, height) } }
                                } else {
                                    val tile = (entry as GridEntry.Type).data
                                    // Without Pro drawn in the theme's colour, the chosen one kept, as
                                    // sizes are.
                                    TileColored(if (colorsUnlocked) tile.tile.color else TileColor.DEFAULT) {
                                        TileCard(
                                            modifier = placed,
                                            onDragStart = startDrag,
                                            onDrag = drag::drag,
                                            onDragEnd = { drag.end()?.let(viewModel::reorder) },
                                            data = tile,
                                            editing = editing,
                                            resizable = resizable,
                                            onClick = {
                                                // In edit mode a tap must not navigate away: the user is
                                                // arranging tiles, not reading them.
                                                // It opens on the window the tile shows, so the figure under
                                                // the finger is the one at the top of the screen it opens.
                                                if (!editing) {
                                                    // The body face names four types; its own screen is the one
                                                    // that shows them together.
                                                    val workout = tile.openedWorkout
                                                    if (tile.bodyReadings != null) {
                                                        onOpenBody()
                                                    } else if (workout != null) {
                                                        onOpenSession(workout)
                                                    } else {
                                                        onOpenType(tile.tile.typeName, state.date.toString(), tile.shownSpan)
                                                    }
                                                }
                                            },
                                            // Where a tap opens one workout, the count beside its curve
                                            // still opens the day's list, as a small tile does: without
                                            // it the day's workouts were out of reach from a large tile.
                                            onOpenDay = tile.openedWorkout?.let {
                                                { onOpenType(tile.tile.typeName, state.date.toString(), tile.shownSpan) }
                                            },
                                            onOpenWorkout = onOpenSession,
                                            onLongClick = { editing = true },
                                            onMoveUp = { viewModel.moveTile(tile.tile.id, forward = false) },
                                            onMoveDown = { viewModel.moveTile(tile.tile.id, forward = true) },
                                            onResize = {
                                                // Locked, the button is where the purchase starts, as the
                                                // export menu's locked entries are.
                                                if (resizable) {
                                                    viewModel.resizeTile(tile.tile.id)
                                                } else {
                                                    activity?.let(AppEntitlements.current::buy)
                                                }
                                            },
                                            colorsUnlocked = colorsUnlocked,
                                            onSetColor = {
                                                if (colorsUnlocked) {
                                                    editingColorFor = tile.tile to tile.spec.displayNameRes
                                                } else {
                                                    activity?.let(AppEntitlements.current::buy)
                                                }
                                            },
                                            onRemove = { viewModel.removeTile(tile.tile.id) },
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
            }
            // Tiles each spin on their own; past a few seconds, words say the app is still at it.
            SlowLoadingPill(
                active = !state.loading && state.tiles.any { it.loading },
                modifier = Modifier.padding(padding),
            )
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
    modifier: Modifier,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    editing: Boolean,
    resizable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onResize: () -> Unit,
    colorsUnlocked: Boolean,
    onSetColor: () -> Unit,
    onRemove: () -> Unit,
    onSetGoal: () -> Unit,
    onSetZones: () -> Unit,
    onSetOptions: () -> Unit,
    onGrantAccess: () -> Unit,
    /** What a tap on the figure opens where it differs from the tile's own tap; see [TileValueAndChart]. */
    onOpenDay: (() -> Unit)? = null,
    /** What a tap on one workout's curve opens; see [CompanionCurves]. */
    onOpenWorkout: ((Session) -> Unit)? = null,
) {
    val moveUp = stringResource(R.string.tile_move_up)
    val moveDown = stringResource(R.string.tile_move_down)
    // The grid hands every tile its exact size, square or spanning; the card only fills it.
    Card(
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // A drag needs a finger; a screen reader moves a tile by these instead.
            .semantics {
                if (editing) {
                    customActions = listOf(
                        CustomAccessibilityAction(moveUp) { onMoveUp(); true },
                        CustomAccessibilityAction(moveDown) { onMoveDown(); true },
                    )
                }
            },
        // From the theme in force, which [TileColored] tints for a tile with its own colour.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = when {
                        // A 2x2 body face names weight in its first row; the tile is all four.
                        data.bodyWindow != null ->
                            stringResource(R.string.tile_title_span, stringResource(R.string.body_composition), stringResource(data.shownSpan.labelRes))
                        // A calendar year is named by its number; the last 365 days as a year.
                        data.heatmap != null && data.tile.calendarYear ->
                            stringResource(
                                if (data.heatmapCapped) R.string.tile_title_span_capped else R.string.tile_title_span,
                                name,
                                data.heatmap.first.year.toString(),
                            )
                        data.heatmap != null && data.heatmapCapped ->
                            stringResource(R.string.tile_title_span_capped, name, stringResource(data.shownSpan.labelRes))
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
                if (editing) DragHandle(onDragStart, onDrag, onDragEnd)
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (editing) {
                    // The tile's own content stays, faded, under the controls: arranging
                    // tiles is easier seeing what each one shows. Its "grant access" button
                    // does nothing here, where a tap belongs to the controls above it.
                    Box(
                        modifier = Modifier.matchParentSize().alpha(EDITING_CONTENT_ALPHA),
                        contentAlignment = Alignment.Center,
                    ) {
                        TileBody(data, large = resizable && data.tile.height > 1, onGrantAccess = {})
                    }
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
                        colorsUnlocked = colorsUnlocked,
                        onResize = onResize,
                        onSetColor = onSetColor,
                        onRemove = onRemove,
                        onSetGoal = onSetGoal,
                        onSetZones = onSetZones,
                        onSetOptions = onSetOptions,
                    )
                } else {
                    TileBody(data, large = resizable && data.tile.height > 1, onGrantAccess, onOpenDay, onLongClick, onOpenWorkout)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Not for a session tile: its face is a count and its subtitle a duration, so
                // the type's own unit ("h", for sleep) would label neither of them.
                // Nor for a 2x2 body face, whose rows carry their own units, kg and % alike.
                data.spec.displayUnitRes?.takeIf { data.spec.tile.form != TileSpec.Form.SESSIONS && data.bodyWindow == null }
                    ?.let { unit ->
                        Text(
                            text = stringResource(unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                val trend = data.trend?.takeIf { data.granted && !data.loading }
                trend?.let { TrendMark(it) }
                data.streak.takeIf { it >= MIN_STREAK && data.granted && !data.loading }?.let {
                    val sessions = data.spec.tile.form == TileSpec.Form.SESSIONS
                    val unitShown = data.spec.displayUnitRes != null && !sessions
                    StreakMark(it, active = sessions, first = !unitShown && trend == null)
                }
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
 * The frame of a tile that is no type's -- the insights and the cycle tile: a title, the body,
 * a caption, and in edit mode the drag handle and the controls that apply to it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedTileCard(
    tile: Tile,
    title: String,
    caption: String,
    modifier: Modifier,
    editing: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    /** Whether colours are unlocked, and what the colour button does; see [TileEditControls]. */
    colorsUnlocked: Boolean,
    onSetColor: () -> Unit,
    /** Whether larger sizes are unlocked; drawn at one cell without, as type tiles are. */
    resizable: Boolean = false,
    onResize: () -> Unit = {},
    /** Whether the tile has options of its own, and what opens them. */
    canSetOptions: Boolean = false,
    onSetOptions: () -> Unit = {},
    body: @Composable (width: Int, height: Int) -> Unit,
) {
    val moveUp = stringResource(R.string.tile_move_up)
    val width = if (resizable) tile.width else 1
    val height = if (resizable) tile.height else 1
    val moveDown = stringResource(R.string.tile_move_down)
    Card(
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                if (editing) {
                    customActions = listOf(
                        CustomAccessibilityAction(moveUp) { onMoveUp(); true },
                        CustomAccessibilityAction(moveDown) { onMoveDown(); true },
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (editing) DragHandle(onDragStart, onDrag, onDragEnd)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (editing) {
                    Box(Modifier.matchParentSize().alpha(EDITING_CONTENT_ALPHA), contentAlignment = Alignment.Center) {
                        body(width, height)
                    }
                    TileEditControls(
                        canSetGoal = false,
                        canSetZones = false,
                        canSetOptions = canSetOptions,
                        resizable = resizable,
                        width = width,
                        height = height,
                        colorsUnlocked = colorsUnlocked,
                        onResize = onResize,
                        onSetColor = onSetColor,
                        onRemove = onRemove,
                        onSetGoal = {},
                        onSetZones = {},
                        onSetOptions = onSetOptions,
                    )
                } else {
                    body(width, height)
                }
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One cell of the grid: a type's tile, or the insights or cycle tile that belongs to none. */
private sealed interface GridEntry {
    val tile: Tile
    val id: String get() = tile.id

    data class Type(val data: TileData) : GridEntry {
        override val tile: Tile get() = data.tile
    }

    data class Insights(val data: InsightsTileData) : GridEntry {
        override val tile: Tile get() = data.tile
    }

    data class Cycle(val data: CycleTileData) : GridEntry {
        override val tile: Tile get() = data.tile
    }
}
