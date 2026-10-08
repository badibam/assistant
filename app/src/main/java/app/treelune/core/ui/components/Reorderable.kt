package app.treelune.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme

/** What an item of a [ReorderableColumn] can place in its content. */
interface ReorderItemScope {
    /** The grip the item is dragged by, drawn by the theme; its own list only, never an outer one. */
    @Composable
    fun DragHandle()
}

/**
 * A column whose items are put in another order by dragging their handle.
 *
 * The behaviour lives here and the look in the theme: the theme draws the handle
 * ([app.treelune.core.themes.ThemeContract.DragHandle]) and the item while it is lifted
 * ([app.treelune.core.themes.ThemeContract.ReorderItem]); the gap the others open shows where it
 * will land. Each column keeps its own order: a column nested in an item of another is dragged by
 * its own handles, and an item never leaves its column.
 *
 * The new order is given once, on release, by [onMove] — never while dragging — always the
 * [onMove] of the latest composition, so it reads the caller's list as it stands. Until [items]
 * changes, the column shows the moved order, so a list that is written and read back (the notes,
 * a list's items) does not flash its old order. "Changes" is by content: a list derived anew at
 * every recomposition, equal to the one moved from, keeps the moved order. [items] must therefore
 * change once a move is taken into account: a caller that orders positions rather than elements
 * pairs each position with its element.
 *
 * Dragging near the edge of whatever scrolls around the column scrolls it. The handle also
 * carries "move up" and "move down" as accessibility actions.
 *
 * @param key What identifies an item across orders, so its state follows it
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    key: (T) -> Any = { it as Any },
    itemContent: @Composable ReorderItemScope.(index: Int, item: T) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val haptics = LocalHapticFeedback.current
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    // How far beyond the lifted item must stay in view: near an edge, the scroll around follows
    val scrollMarginPx = with(LocalDensity.current) { 48.dp.toPx() }

    // A drag outlives the composition it started in: what it reads when released is the latest
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    // The order shown: the moved one while [items] is still equal to the list it was moved from,
    // the given one as soon as a list with other content comes
    var moved by remember { mutableStateOf<Pair<List<T>, List<T>>?>(null) }
    fun shownNow(): List<T> = moved?.takeIf { it.first == currentItems }?.second ?: currentItems
    val shown = shownNow()

    // Where each item sits in the column when nothing is dragged, by index
    val tops = remember { mutableStateMapOf<Int, Float>() }
    val heights = remember { mutableStateMapOf<Int, Float>() }

    var columnTopInRoot by remember { mutableFloatStateOf(0f) }

    // The drag: which item, the finger in root coordinates, and where in the item it took hold.
    // The item's offset is worked out from the finger and the column, so it stays under the
    // finger when the column scrolls.
    var dragged by remember { mutableStateOf<Int?>(null) }
    var fingerInRoot by remember { mutableFloatStateOf(0f) }
    var grabInItem by remember { mutableFloatStateOf(0f) }

    fun offsetOf(index: Int): Float {
        val top = tops[index] ?: return 0f
        val height = heights[index] ?: return 0f
        val lastBottom = shownNow().indices.maxOfOrNull { (tops[it] ?: 0f) + (heights[it] ?: 0f) } ?: 0f
        // Where the item's top now is in the column, less where it rests
        return (fingerInRoot - columnTopInRoot - grabInItem - top).coerceIn(-top, lastBottom - height - top)
    }

    // The place the lifted item would take. It passes an item below once its bottom edge is past
    // that item's middle, and one above once its top edge is: an edge, not its own middle, which
    // could never pass the middle of a last or first item shorter than itself.
    fun targetOf(index: Int): Int {
        val top = (tops[index] ?: 0f) + offsetOf(index)
        val bottom = top + (heights[index] ?: 0f)
        return shownNow().indices.count {
            val middle = (tops[it] ?: 0f) + (heights[it] ?: 0f) / 2
            when {
                it < index -> top >= middle
                it > index -> bottom > middle
                else -> false
            }
        }
    }

    fun move(from: Int, to: Int) {
        val order = shownNow()
        if (from == to || to !in order.indices) return
        moved = currentItems to order.toMutableList().apply { add(to, removeAt(from)) }
        currentOnMove(from, to)
    }

    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(dragged) {
        val index = dragged ?: return@LaunchedEffect
        while (true) {
            val height = heights[index] ?: 0f
            requester.bringIntoView(Rect(0f, -scrollMarginPx, 1f, height + scrollMarginPx))
            withFrameNanos { }
        }
    }

    Column(
        modifier = modifier.onGloballyPositioned { columnTopInRoot = it.positionInRoot().y },
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        val lifted = dragged
        val target = lifted?.let { targetOf(it) }

        shown.forEachIndexed { index, item ->
            key(key(item)) {
                // The gesture runs as long as the item is there, its place changing under it
                val currentIndex by rememberUpdatedState(index)
                // The others open a gap where the lifted item would land
                val step = (lifted?.let { heights[it] } ?: 0f) + spacingPx
                val shift = when {
                    lifted == null || target == null || index == lifted -> 0f
                    lifted < target && index in (lifted + 1)..target -> -step
                    target < lifted && index in target until lifted -> step
                    else -> 0f
                }
                val animatedShift by animateFloatAsState(
                    targetValue = shift,
                    animationSpec = if (lifted != null) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
                    label = "reorder_shift"
                )

                var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val scope = object : ReorderItemScope {
                    @Composable
                    override fun DragHandle() {
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { handleCoordinates = it }
                                .semantics {
                                    customActions = listOfNotNull(
                                        CustomAccessibilityAction(s.shared("action_up")) { move(index, index - 1); true }
                                            .takeIf { index > 0 },
                                        CustomAccessibilityAction(s.shared("action_down")) { move(index, index + 1); true }
                                            .takeIf { index < shown.size - 1 }
                                    )
                                }
                                .pointerInput(Unit) {
                                    fun rootY(position: Offset) = handleCoordinates?.localToRoot(position)?.y ?: 0f
                                    detectDragGestures(
                                        onDragStart = { position ->
                                            fingerInRoot = rootY(position)
                                            grabInItem = fingerInRoot - columnTopInRoot - (tops[currentIndex] ?: 0f)
                                            dragged = currentIndex
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            fingerInRoot = rootY(change.position)
                                        },
                                        onDragEnd = {
                                            val from = currentIndex
                                            val to = targetOf(from)
                                            dragged = null
                                            move(from, to)
                                        },
                                        onDragCancel = { dragged = null }
                                    )
                                }
                        ) {
                            CurrentTheme.current.DragHandle()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Measured before the translation, so a place is where the item rests
                        .onGloballyPositioned {
                            tops[index] = it.positionInParent().y
                            heights[index] = it.size.height.toFloat()
                        }
                        .zIndex(if (index == lifted) 1f else 0f)
                        // Nothing lifted, nothing shifted: the animated shift only snaps back on
                        // the next frame, and would show the old gaps over the new order for one
                        .graphicsLayer {
                            translationY = when {
                                index == lifted -> offsetOf(index)
                                lifted == null -> 0f
                                else -> animatedShift
                            }
                        }
                        .let { if (index == lifted) it.bringIntoViewRequester(requester) else it }
                ) {
                    CurrentTheme.current.ReorderItem(lifted = index == lifted) {
                        scope.itemContent(index, item)
                    }
                }
            }
        }
    }
}
