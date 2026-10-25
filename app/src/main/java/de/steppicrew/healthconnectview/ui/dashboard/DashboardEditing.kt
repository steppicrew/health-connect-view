package de.steppicrew.healthconnectview.ui.dashboard

import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import de.steppicrew.healthconnectview.dashboard.SIZES
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import de.steppicrew.healthconnectview.R

// The dashboard in edit mode: a tile's drag handle and its controls. Split from DashboardScreen.kt.

/**
 * The grip a tile is dragged by in edit mode. Only the grip starts a drag, so the rest of the
 * dashboard still scrolls under a finger.
 */
@Composable
internal fun DragHandle(onDragStart: () -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit) {
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
internal fun LockableIcon(imageVector: ImageVector, label: String, locked: Boolean) {
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
internal fun TileEditControls(
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

/** The grip's touch target, in dp; its icon is the default 24. */
private const val DRAG_HANDLE = 36

/**
 * How close to the top or bottom the finger starts scrolling, its fastest step per frame, and
 * where the grip sits below a tile's top edge: the card's padding plus half the grip.
 */
internal const val DRAG_EDGE = 56
internal const val DRAG_GRIP_Y = 12 + DRAG_HANDLE / 2
internal const val DRAG_SCROLL_STEP = 12

/** How long the grip is held before its tile lifts, unless the finger moves first. */
private const val LIFT_DELAY_MS = 200L

/** How much of a tile's content shows under its edit controls. */
internal const val EDITING_CONTENT_ALPHA = 0.3f

/** A lifted tile stands slightly out of the grid, so it reads as held. */
internal const val LIFTED_SCALE = 1.04f
internal const val LIFTED_ELEVATION = 8
