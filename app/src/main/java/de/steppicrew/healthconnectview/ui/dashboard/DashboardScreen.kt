package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.foundation.shape.RoundedCornerShape
import de.steppicrew.healthconnectview.ui.components.HeatmapLegend
import de.steppicrew.healthconnectview.ui.components.heatmapValue
import de.steppicrew.healthconnectview.ui.components.heatmapColors
import de.steppicrew.healthconnectview.ui.components.YearHeatmapGrid
import de.steppicrew.healthconnectview.health.YearHeatmap
import de.steppicrew.healthconnectview.ui.components.smoothPath
import de.steppicrew.healthconnectview.ui.components.DotText
import androidx.annotation.StringRes
import androidx.compose.material3.LocalContentColor
import de.steppicrew.healthconnectview.ui.components.SessionTimeline
import de.steppicrew.healthconnectview.health.Span
import de.steppicrew.healthconnectview.dashboard.Tile
import de.steppicrew.healthconnectview.dashboard.companionsOf
import de.steppicrew.healthconnectview.ui.components.firstLineInset
import androidx.compose.material.icons.filled.Insights
import de.steppicrew.healthconnectview.ui.insights.notable
import de.steppicrew.healthconnectview.ui.insights.insightAmount
import de.steppicrew.healthconnectview.dashboard.TileColor
import de.steppicrew.healthconnectview.dashboard.facesFor
import de.steppicrew.healthconnectview.dashboard.TileFace
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.PluralsRes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import de.steppicrew.healthconnectview.ui.components.DayPickerDialog
import de.steppicrew.healthconnectview.registry.RecordRegistry
import de.steppicrew.healthconnectview.registry.RecordTypeSpec
import de.steppicrew.healthconnectview.registry.Point
import de.steppicrew.healthconnectview.ui.components.LineChart
import de.steppicrew.healthconnectview.ui.components.colorOf
import de.steppicrew.healthconnectview.ui.components.StageTotals
import de.steppicrew.healthconnectview.ui.components.StripSegment
import de.steppicrew.healthconnectview.ui.components.SessionLine
import de.steppicrew.healthconnectview.health.Session
import de.steppicrew.healthconnectview.health.night
import de.steppicrew.healthconnectview.health.counted
import de.steppicrew.healthconnectview.ui.components.Hypnogram
import de.steppicrew.healthconnectview.ui.components.RefreshBox
import de.steppicrew.healthconnectview.ui.components.rememberAppIcon
import de.steppicrew.healthconnectview.ui.components.iconFor
import de.steppicrew.healthconnectview.ui.components.LoadingView
import de.steppicrew.healthconnectview.ui.components.SlowLoadingPill
import de.steppicrew.healthconnectview.ui.components.MessageView
import de.steppicrew.healthconnectview.ui.components.OnResume
import de.steppicrew.healthconnectview.ui.components.ProgressRing
import de.steppicrew.healthconnectview.ui.components.SparkCurve
import java.time.Duration
import java.time.Instant
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
    onOpenSettings: () -> Unit,
    /** The permission list itself, one step closer than [onOpenSettings]. */
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    /** The insights tile's full list. */
    onOpenInsights: () -> Unit = {},
    /** The body composition screen, which a weight tile's body face opens. */
    onOpenBody: () -> Unit = {},
    /** One workout's screen, which a workout tile showing a single workout opens. */
    onOpenSession: (Session) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // The state flow itself rather than has(): it starts from what is already known, so an
    // owner's large tiles do not open as single cells and then jump.
    val pro by AppEntitlements.current.pro.collectAsStateWithLifecycle()
    val resizable = pro.allows(Feature.TILE_SIZES)
    val repeatable = pro.allows(Feature.TILE_REPEAT)
    val colorsUnlocked = pro.allows(Feature.TILE_COLORS)
    var editingColorFor by remember { mutableStateOf<TileData?>(null) }
    var editingGoalFor by remember { mutableStateOf<TileData?>(null) }
    var editingZonesFor by remember { mutableStateOf<TileData?>(null) }
    var editingOptionsFor by remember { mutableStateOf<TileData?>(null) }
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

    if (addingTile) {
        AddTileDialog(
            candidates = viewModel.addableTypes(),
            repeatUnlocked = repeatable,
            onDismiss = { addingTile = false },
            onAdd = viewModel::addTile,
            onBuy = { activity?.let(AppEntitlements.current::buy) },
            insightsOffered = !viewModel.hasInsightsTile,
            insightsUnlocked = pro.allows(Feature.INSIGHTS),
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

    editingColorFor?.let { editing ->
        TileColorDialog(
            displayName = stringResource(editing.spec.displayNameRes),
            current = editing.tile.color,
            onDismiss = { editingColorFor = null },
            onPick = { viewModel.setColor(editing.tile.id, it) },
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
            onSave = { span, face, companion, calendarYear ->
                viewModel.setOptions(editing.tile.id, span, face, companion, calendarYear)
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
        ) {
            when {
                state.loading -> LoadingView(Modifier.padding(padding))
    
                state.availability != Availability.Available -> MessageView(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.availability_missing_title),
                    body = stringResource(R.string.availability_missing_body),
                    modifier = Modifier.padding(padding),
                )
    
                state.tiles.isEmpty() && state.insights == null -> MessageView(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.dashboard_empty_title),
                    body = stringResource(R.string.dashboard_empty_body),
                    modifier = Modifier.padding(padding),
                )
    
                // Distinct from "no tiles": the dashboard is configured, but nothing on it may be
                // read yet. Sending the user to the type list would be a dead end.
                state.insights == null && state.tiles.none { it.granted } -> MessageView(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.detail_no_permission_title),
                    body = stringResource(R.string.detail_no_permission_body),
                    modifier = Modifier.padding(padding),
                )
    
                else -> {
                    // Every tile in the stored order, the insights tile among the types'; during a
                    // drag, in the order the drag has reached.
                    val entries = remember(state.tiles, state.insights, state.layout) {
                        val all = state.tiles.map(GridEntry::Type) + listOfNotNull(state.insights?.let(GridEntry::Insights))
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
                                if (entry is GridEntry.Insights) {
                                    InsightsTileCard(
                                        data = entry.data,
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
                                    )
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
                                                    editingColorFor = tile
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
                    TileBody(data, large = resizable && data.tile.height > 1, onGrantAccess)
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
 * The insights tile: the week's most unusual moves, up to [INSIGHT_ROWS] of them, each as an
 * arrow, the type's name and how far it moved. A tap opens the whole list. One cell only for
 * now; what a larger face should show is still to be decided.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InsightsTileCard(
    data: InsightsTileData,
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
) {
    val moveUp = stringResource(R.string.tile_move_up)
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
                    text = stringResource(R.string.insights_tile_title),
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
                        InsightsTileBody(data)
                    }
                    TileEditControls(
                        canSetGoal = false,
                        canSetZones = false,
                        canSetOptions = false,
                        resizable = false,
                        width = 1,
                        height = 1,
                        colorsUnlocked = false,
                        onResize = {},
                        onSetColor = {},
                        onRemove = onRemove,
                        onSetGoal = {},
                        onSetZones = {},
                        onSetOptions = {},
                        canResize = false,
                        canSetColor = false,
                    )
                } else {
                    InsightsTileBody(data)
                }
            }
            Text(
                text = stringResource(R.string.insights_tile_caption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InsightsTileBody(data: InsightsTileData) {
    val insights = data.insights
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        data.locked -> LockableIcon(Icons.Default.Insights, stringResource(R.string.settings_pro), locked = true)
        insights == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        insights.isEmpty() -> Text(stringResource(R.string.insights_tile_few), style = MaterialTheme.typography.bodySmall, color = muted)
        insights.notable().isEmpty() -> Text(stringResource(R.string.insights_tile_level), style = MaterialTheme.typography.bodyMedium)
        else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val notable = insights.notable()
            notable.take(INSIGHT_ROWS).forEach { insight ->
                val style = MaterialTheme.typography.bodySmall
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = insight.trend.direction.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp, top = firstLineInset(style, INSIGHT_ICON.dp))
                            .size(INSIGHT_ICON.dp),
                    )
                    Text(
                        text = stringResource(insight.spec.displayNameRes),
                        style = style,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = insightAmount(insight),
                        style = style,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            val more = notable.size - INSIGHT_ROWS
            if (more > 0) {
                Text(
                    text = pluralStringResource(R.plurals.insights_tile_more, more, more),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
    }
}

/** Rows on a single-cell insights tile: three fit under the title at the smallest phone width. */
private const val INSIGHT_ROWS = 3

private const val INSIGHT_ICON = 16

/**
 * The grip a tile is dragged by in edit mode. Only the grip starts a drag, so the rest of the
 * dashboard still scrolls under a finger.
 */
@Composable
private fun DragHandle(onDragStart: () -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit) {
    // The gesture outlives recompositions, so it calls whatever the latest callbacks are.
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DRAG_HANDLE.dp)
            .pointerInput(Unit) {
                // The press is consumed at once, so it never reaches the card: left to the
                // card, its long-press took the gesture and a grip held a moment before moving
                // did nothing. The tile lifts after a short hold, or as soon as the finger
                // moves, so a quick drag is not held up; a brief touch leaves it alone.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var early = Offset.Zero
                    val moved = withTimeoutOrNull(LIFT_DELAY_MS) {
                        var pressed = true
                        while (pressed && early.getDistance() <= viewConfiguration.touchSlop) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            pressed = change?.pressed == true
                            if (change != null && pressed) {
                                early += change.positionChange()
                                change.consume()
                            }
                        }
                        pressed
                    }
                    // False: let go before the hold was over. Null: held long enough.
                    if (moved == false) return@awaitEachGesture
                    start()
                    move(early)
                    drag(down.id) { change ->
                        move(change.positionChange())
                        change.consume()
                    }
                    end()
                }
            },
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            // The card's accessibility actions move it; the grip itself is for a finger.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * An edit control's icon, with a padlock where the feature needs Pro. Locked, it stays in
 * place: a control that silently vanishes cannot be asked about. The padlock sits in a filled
 * disc and the icon is dimmed: drawn bare, the lock landed on the resize arrowhead and read as
 * part of the arrow, so nobody saw a lock at all.
 */
@Composable
private fun LockableIcon(imageVector: ImageVector, label: String, locked: Boolean) {
    BadgedBox(
        badge = {
            if (locked) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(LOCK_BADGE.dp))
                }
            }
        },
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = if (locked) stringResource(R.string.export_premium, label) else label,
            tint = if (locked) LocalContentColor.current.copy(alpha = LOCKED_ALPHA) else LocalContentColor.current,
        )
    }
}

/**
 * Edit affordances shown in place of a tile's value.
 *
 * Reordering is not here: a tile moves by the handle beside its title (see [DragHandle]).
 * Up and down buttons stepped one place per tap, which took many taps across a full dashboard
 * and could not show where a tile would land.
 *
 * Resizing is a button, stepping through the sizes rather than dragging a corner: a size is
 * one of three, and a button reaches each without aiming.
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
    colorsUnlocked: Boolean,
    onResize: () -> Unit,
    onSetColor: () -> Unit,
    onRemove: () -> Unit,
    onSetGoal: () -> Unit,
    onSetZones: () -> Unit,
    onSetOptions: () -> Unit,
    /** Off for the insights tile, which has one size and no colour of its own yet. */
    canResize: Boolean = true,
    canSetColor: Boolean = true,
) {
    val size = stringResource(R.string.tile_size, width, height)
    val resize = stringResource(R.string.tile_resize)
    FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
        if (canResize) IconButton(
            onClick = onResize,
            modifier = if (resizable) Modifier.semantics { stateDescription = size } else Modifier,
        ) {
            // The arrows point the way the next tap goes: outwards until the largest
            // size, then inwards back to a single cell.
            val largest = resizable && (width to height) == SIZES.last()
            LockableIcon(
                imageVector = if (largest) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                label = resize,
                locked = !resizable,
            )
        }
        if (canSetColor) IconButton(onClick = onSetColor) {
            LockableIcon(Icons.Default.FormatColorFill, stringResource(R.string.tile_color), locked = !colorsUnlocked)
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

    // A day of a one-value-a-day type has no chart to draw, so its tile falls back to the value.
    val chart = data.chart?.takeIf { it.drawsChart || it.sessions.isNotEmpty() }
    when {
        !data.granted -> LockedTile(onGrantAccess)

        // With no part known, the plain weight: half a tile left empty read as broken.
        data.bodyWindow != null && !data.loading -> BodyCurves(data, data.bodyReadings.orEmpty(), data.bodyWindow)

        !data.bodyReadings.isNullOrEmpty() && !data.loading -> BodyFace(data, data.bodyReadings, large)

        data.heatmap != null && data.tile.face == TileFace.CALENDAR -> TileCalendar(data, data.heatmap)

        chart != null && data.tile.face == TileFace.CHART ->
            TileChart(chart, Modifier.fillMaxSize(), compactAxis = data.tile.height == 1, companion = data.companion())

        chart != null && data.tile.face == TileFace.BOTH -> TileValueAndChart(data, chart)

        // Before the loading and null-value checks: a session tile never has a value, and
        // zero sessions is a real answer rather than an absence of data.
        data.spec.tile.form == TileSpec.Form.SESSIONS -> SessionCount(data)

        data.loading || data.failed || data.value == null -> TileValue(data, large)

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
 * Weight beside its parts: the weight as the single cell shows it, and body fat, water and
 * bone mass each with its value. A part measured on another day than the weight carries its
 * own date, muted, so a body fat reading from months ago is not read as this morning's.
 */
@Composable
private fun BodyFace(data: TileData, parts: List<BodyReading>, large: Boolean) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { TileValue(data, large) }
        Column(Modifier.weight(BODY_PARTS_WEIGHT), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEach { part ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = stringResource(part.spec.displayNameRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatting.number(part.value, part.spec.valueDecimals) +
                                part.spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        part.date?.let { day ->
                            Text(
                                text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A 2x2 body face over a window: weight and each part in a row, name and latest value above a
 * curve of its daily means across the window. Each curve on its own scale, fitted to its own
 * range, so a change of half a kilo shows as clearly as one of a percent -- never two scales
 * on one plot, and never weight and bone mass squashed onto one.
 */
@Composable
private fun BodyCurves(data: TileData, parts: List<BodyReading>, window: ClosedRange<Instant>) {
    val rows = buildList {
        data.value?.let { add(BodyRow(data.spec, it, data.valueDate, data.bodyCurves[data.spec.type.simpleName].orEmpty())) }
        parts.forEach { add(BodyRow(it.spec, it.value, it.date, data.bodyCurves[it.spec.type.simpleName].orEmpty())) }
    }
    if (rows.isEmpty()) {
        TileValue(data, large = true)
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = stringResource(row.spec.displayNameRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    row.date?.let { day ->
                        Text(
                            text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) + "  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = Formatting.number(row.value, row.spec.valueDecimals) +
                            row.spec.displayUnitRes?.let { " " + stringResource(it) }.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                PartCurve(row.curve, window, Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp))
            }
        }
    }
}

private data class BodyRow(val spec: RecordTypeSpec<*>, val value: Double, val date: LocalDate?, val curve: List<Point>)

/**
 * One part's daily means across [window], placed by time so the rows' days line up, on the
 * part's own range. A single day is a dot; none leaves the row its number alone.
 */
@Composable
private fun PartCurve(points: List<Point>, window: ClosedRange<Instant>, modifier: Modifier) {
    if (points.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    val range = (high - low).takeIf { it > 0.0 } ?: 1.0
    val from = window.start.toEpochMilli()
    val length = (window.endInclusive.toEpochMilli() - from).coerceAtLeast(1L).toFloat()
    Canvas(modifier) {
        val stroke = PART_STROKE.dp.toPx()
        val offsets = points.map { point ->
            Offset(
                x = ((point.time.toEpochMilli() - from) / length).coerceIn(0f, 1f) * size.width,
                // Flat where all readings agree: drawn through the middle rather than at the floor.
                y = if (high > low) size.height - ((point.value - low) / range).toFloat() * size.height else size.height / 2,
            )
        }
        // Smoothed like the detail charts, monotone so the curve never overshoots a reading.
        if (offsets.size > 1) drawPath(smoothPath(offsets), color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        offsets.forEach { drawCircle(color, radius = stroke, center = it) }
    }
}

private const val PART_STROKE = 1.5f

/** The parts' column against the weight's: the names need the room. */
private const val BODY_PARTS_WEIGHT = 1.6f

/**
 * The window's chart filling the tile: the detail screen's chart, with its axis values and
 * goal line, minus the touch readout -- a tap here opens that screen.
 */
@Composable
private fun TileChart(
    chart: TileDetailData,
    modifier: Modifier,
    compactAxis: Boolean = false,
    /** The tile's second curve and its readings; see [Tile.companion]. */
    companion: Pair<SessionLine, List<Pair<Session, List<Point>>>>? = null,
) {
    val extent = chart.extent
    // A day of sleep is last night, and a night with stages is drawn as them: the owner's
    // idea, 09.10.2026. A band from 23:02 to 05:15 says when; the stages say how. The night is
    // the day's longest sleep, so a nap with stages does not take its place.
    val night = chart.sessions.night()?.takeIf { it.stages.isNotEmpty() }
    if (companion != null && extent != null) {
        CompanionCurves(companion.first, companion.second, compactAxis, modifier)
    } else if (chart.spec.tile.sessionKind == Session.Kind.SLEEP && extent != null && night != null) {
        Hypnogram(
            stages = night.stages,
            start = night.start,
            end = night.end,
            fillHeight = true,
            totalsShown = !compactAxis,
            modifier = modifier,
        )
    } else if (chart.spec.tile.form == TileSpec.Form.SESSIONS && extent != null) {
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
    // A session tile's figure as its single cell writes it: "6,22" was a night's hours as a
    // bare decimal, where the cell beside says "6h 13m".
    @Composable
    fun Figure(stages: StageTotalsAt, inline: Boolean = false) =
        if (data.spec.tile.form == TileSpec.Form.SESSIONS) SessionCount(data, stages, inline) else TileValue(data)
    if (data.tile.height > 1) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The stage chart names its own stages; a curve over the strip does not.
            Figure(if (data.companion() != null) StageTotalsAt.ROW else StageTotalsAt.NONE, inline = true)
            TileChart(chart, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), companion = data.companion())
        }
    } else {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(VALUE_SHARE), contentAlignment = Alignment.Center) {
                Figure(StageTotalsAt.COLUMN)
            }
            TileChart(chart, Modifier.weight(1f - VALUE_SHARE).fillMaxHeight(), compactAxis = true, companion = data.companion())
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
private fun SessionCount(
    data: TileData,
    stagesAt: StageTotalsAt = StageTotalsAt.NONE,
    /**
     * Count, time and icons on one line, above a tall tile's charts: stacked they took three
     * lines of the height the workouts' curves are there for -- the owner's request.
     */
    inline: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (data.loading) {
            TileLoading()
            return@Column
        }
        // Zero sessions is an answer; a read that threw is not, so it must not say "none".
        if (data.failed) {
            TileFailed()
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

        // The activities themselves, as far as they fit: two or three icons say "a ride and a
        // walk" where the bare count says only "two".
        @Composable
        fun Icons(modifier: Modifier) = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
            data.sessions.take(TILE_ICONS).forEach { session ->
                Icon(
                    imageVector = iconFor(session),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TILE_ICON_SIZE.dp),
                )
            }
        }

        val sleep = data.spec.tile.sessionKind == Session.Kind.SLEEP
        if (inline && !sleep) {
            // One line that cannot wrap in any language: a number, a duration and icons.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sessions_count, data.sessions.size),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = Formatting.duration(data.sessionDuration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Icons(Modifier)
            }
            return@Column
        }

        // A night is asked "how long", never "how many": a large "1" above 6h 13m answered the
        // question nobody had. Workouts keep their count, which is the figure they are asked by.
        if (sleep) {
            Text(
                text = Formatting.duration(data.sessionDuration),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
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
        }
        // A night's stages where there is room: the moon beneath "6h 13m" said only "slept".
        val night = data.sessions.night()?.takeIf { it.stages.isNotEmpty() }
        if (night != null && stagesAt != StageTotalsAt.NONE) {
            StageTotals(night.stages, Modifier.padding(top = 4.dp), stacked = stagesAt == StageTotalsAt.COLUMN)
            return@Column
        }
        Icons(Modifier.padding(top = 4.dp))
    }
}

/**
 * Which way the last week went against the month before it. Drawn in the tile's quiet colour,
 * never red or green: up is good for steps and bad for resting heart rate, and the arrow does
 * not know which it is looking at.
 */
@Composable
private fun TrendMark(trend: Trend) {
    Icon(
        imageVector = trend.icon,
        contentDescription = stringResource(trend.description),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(TREND_ICON.dp),
    )
}

/**
 * How many days in a row the goal was met, or an activity recorded where [active]: a trophy
 * and the count. Not a flame, which beside "kcal" read as calories. In the tile's quiet
 * colour like the arrow, so the ring stays the loudest thing on it.
 *
 * The gap before it only where something precedes it: on the activities tile it opens the
 * line, and a gap there set it in from the tile's other text.
 */
@Composable
private fun StreakMark(days: Int, active: Boolean, first: Boolean) {
    val description = pluralStringResource(streakPlural(active), days, days)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = if (first) 0.dp else 4.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(TREND_ICON.dp),
        )
        Text(
            text = Formatting.integer(days.toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The arrow for a trend, shared by the tile and the detail view so the two read alike. */
internal val Trend.icon: ImageVector
    get() = when (this) {
        Trend.UP -> Icons.AutoMirrored.Filled.TrendingUp
        Trend.FLAT -> Icons.AutoMirrored.Filled.TrendingFlat
        Trend.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
    }

@get:StringRes
internal val Trend.description: Int
    get() = when (this) {
        Trend.UP -> R.string.trend_up
        Trend.FLAT -> R.string.trend_flat
        Trend.DOWN -> R.string.trend_down
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

/** A tile whose read is still running: small, so a full dashboard does not spin loudly. */
@Composable
private fun TileLoading() {
    val label = stringResource(R.string.tile_loading)
    CircularProgressIndicator(
        modifier = Modifier
            .size(TILE_SPINNER.dp)
            .semantics { contentDescription = label },
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A tile whose read threw; the next return to the app reads it again. */
@Composable
private fun TileFailed() {
    Text(
        text = stringResource(R.string.tile_failed),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
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

        // Never the dash: a tile still reading looked exactly like a day with nothing in it.
        data.loading -> TileLoading()

        data.failed -> TileFailed()

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
                text = tileValueText(data.value, data.secondaryValue, data.spec.valueDecimals),
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

        // The week's HRV: its standing as a dot, as the detail screen shows it, and a note
        // that the number is seven nights, not the latest reading.
        data.spec.tile.nightlyStatus -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val standing = data.standing
            if (standing != null) {
                DotText(
                    color = standing.color(),
                    text = Formatting.number(data.value, data.spec.valueDecimals),
                    style = valueStyle,
                    textColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = Formatting.number(data.value, data.spec.valueDecimals),
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.tile_hrv_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Two values read as one: the grade's colour beside them, as on the detail screen.
        data.secondaryValue != null -> DotText(
            color = ValueZones.ZONE_COLORS[pressureCategory(data.value, data.secondaryValue).ordinal],
            text = tileValueText(data.value, data.secondaryValue, data.spec.valueDecimals),
            style = valueStyle,
            textColor = MaterialTheme.colorScheme.onSurface,
        )

        else -> Text(
            text = Formatting.number(data.value, data.spec.valueDecimals),
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "128/82" for a pair, whole numbers as a cuff shows them; the plain number otherwise. */
private fun tileValueText(value: Double, secondary: Double?, decimals: Int? = null): String =
    if (secondary == null) Formatting.number(value, decimals) else "${value.roundToInt()}/${secondary.roundToInt()}"

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

/** The words for a run of [days]: of the goal met, or of days with an activity. */
@PluralsRes
internal fun streakPlural(active: Boolean): Int = if (active) R.plurals.streak_active_days else R.plurals.streak_days

/** One day is not a run; the count shows from two. */
internal const val MIN_STREAK = 2
private const val LOCK_ICON = 20
private const val LOCK_BADGE = 10

/** How faint a locked control's own icon is, so the padlock reads as its state. */
private const val LOCKED_ALPHA = 0.45f
private const val TILE_SOURCE_ICON_PX = 48

private const val TILE_ICONS = 3
private const val TILE_ICON_SIZE = 14

/** The spinner in a tile still loading, about the height of a line of its value. */
private const val TILE_SPINNER = 24

/** The grip's touch target, in dp; its icon is the default 24. */
private const val DRAG_HANDLE = 36

/**
 * How close to the top or bottom the finger starts scrolling, its fastest step per frame, and
 * where the grip sits below a tile's top edge: the card's padding plus half the grip.
 */
private const val DRAG_EDGE = 56
private const val DRAG_GRIP_Y = 12 + DRAG_HANDLE / 2
private const val DRAG_SCROLL_STEP = 12

/** How long the grip is held before its tile lifts, unless the finger moves first. */
private const val LIFT_DELAY_MS = 200L

/** How much of a tile's content shows under its edit controls. */
private const val EDITING_CONTENT_ALPHA = 0.3f

/** A lifted tile stands slightly out of the grid, so it reads as held. */
private const val LIFTED_SCALE = 1.04f
private const val LIFTED_ELEVATION = 8

/** One cell of the grid: a type's tile, or the insights tile that belongs to none. */
private sealed interface GridEntry {
    val tile: Tile
    val id: String get() = tile.id

    data class Type(val data: TileData) : GridEntry {
        override val tile: Tile get() = data.tile
    }

    data class Insights(val data: InsightsTileData) : GridEntry {
        override val tile: Tile get() = data.tile
    }
}

/** The tile's chosen curve with the sessions it was read over, where one is chosen and was read. */
private fun TileData.companion(): Pair<SessionLine, List<Pair<Session, List<Point>>>>? {
    val line = SessionLine.of(tile.companion) ?: return null
    val curves = companionCurves.filter { (_, points) -> points.size > 1 }.ifEmpty { return null }
    return line to curves
}

/**
 * The chosen reading over each of the day's last sessions, each across its own span: how the
 * two go together, the owner's request. A night's stages run along the bottom, as on its own
 * screen; a workout's breaks are shaded, and above it its icon and times say which one it is
 * -- the timeline it replaces said that by where the band sat.
 */
@Composable
private fun CompanionCurves(
    line: SessionLine,
    curves: List<Pair<Session, List<Point>>>,
    compactAxis: Boolean,
    modifier: Modifier,
) {
    val spec = RecordRegistry.specOrNull(line.typeName)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        curves.forEach { (session, points) ->
            if (session.kind != Session.Kind.SLEEP) {
                // One line that cannot wrap: the icon rule for wrapping text does not apply.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = iconFor(session),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(COMPANION_ICON_SIZE.dp),
                    )
                    Text(
                        text = Formatting.time(session.start) + "–" + Formatting.time(session.end) + " · " + Formatting.duration(session.counted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            LineChart(
                points = line.shown(points),
                unitRes = spec?.displayUnitRes,
                integral = spec?.tile?.integralValues ?: false,
                minSpan = line.minSpan,
                lineColorOverride = line.color(),
                dottedLine = line.dots,
                strip = session.stages.map { StripSegment(it.start, it.end, colorOf(it.kind)) },
                breaks = session.breaks.map { it.start..it.end },
                extent = session.start..session.end,
                interactive = false,
                fillHeight = true,
                compactAxis = compactAxis,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/** Where a sleep tile lists the time in each stage beside its hours, if anywhere. */
private enum class StageTotalsAt { NONE, ROW, COLUMN }

private const val COMPANION_ICON_SIZE = 14


/**
 * The calendar face: the year's figure above its grid, as on the detail screen but without its
 * taps -- a tap on the tile opens the year there, where a day can be read -- and the legend
 * beneath. Blood pressure's days are coloured by grade, which the shades' legend would misread.
 */
@Composable
private fun TileCalendar(data: TileData, heatmap: YearHeatmap) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val value = data.value
        // Sessions in hours, written as a duration: "7,5" read as a decimal.
        if (data.spec.tile.form == TileSpec.Form.SESSIONS && value != null) {
            Text(
                text = heatmapValue(data.spec)(value),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            TileValue(data)
        }
        YearHeatmapGrid(
            heatmap = heatmap,
            colorOf = heatmapColors(heatmap),
            selected = null,
            onSelect = null,
            description = stringResource(R.string.heatmap_title),
        )
        if (heatmap.secondValues.isEmpty()) HeatmapLegend(heatmap, data.spec, Modifier.align(Alignment.Start))
    }
}

