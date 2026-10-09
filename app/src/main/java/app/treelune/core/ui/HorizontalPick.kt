package app.treelune.core.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * A touch along the width answered as where it falls, 0 at the start and 1 at the end: a tap,
 * and a drag as the finger moves. The gestures are installed once and call the latest [onPick].
 */
@Composable
fun Modifier.horizontalPick(onPick: (Float) -> Unit): Modifier {
    val latest by rememberUpdatedState(onPick)
    return this
        .pointerInput(Unit) { detectTapGestures { latest((it.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> latest((change.position.x / size.width).coerceIn(0f, 1f)) } }
}

/** A whole value of a range and where it stands along a track that spans the range. */
object RangePick {

    /** The value of [range] at [fraction] of the track, the nearest whole one. */
    fun valueAt(fraction: Float, range: IntRange): Int =
        range.first + (fraction.coerceIn(0f, 1f) * (range.last - range.first)).roundToInt()

    /** Where [value] stands along the track, from 0 to 1. */
    fun fractionOf(value: Int, range: IntRange): Float =
        if (range.last == range.first) 0f else ((value - range.first).toFloat() / (range.last - range.first)).coerceIn(0f, 1f)
}
