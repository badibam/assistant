package com.assistant.themes.retro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints

/**
 * The pieces of a frame in Cartouche's private area: eight in the light layer from U+E000, the
 * same eight in the dark layer from U+E008 (cartouche-font). A frame is written twice, once per
 * layer, in the two tones of its border exactly on top of each other: the two outer pixels in one
 * tone, the two inner in the other, which gives the soft relief of a console border.
 */
private enum class Piece { TOP_LEFT, TOP, TOP_RIGHT, LEFT, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT }

private fun piece(of: Piece, dark: Boolean): Char = (0xE000 + (if (dark) 8 else 0) + of.ordinal).toChar()

/** Row [row] of a frame [rows] tall and [columns] wide, in one layer. A middle row is hollow. */
private fun frameRow(columns: Int, row: Int, rows: Int, dark: Boolean): String {
    val (start, middle, end) = when (row) {
        0 -> Triple(Piece.TOP_LEFT, piece(Piece.TOP, dark), Piece.TOP_RIGHT)
        rows - 1 -> Triple(Piece.BOTTOM_LEFT, piece(Piece.BOTTOM, dark), Piece.BOTTOM_RIGHT)
        else -> Triple(Piece.LEFT, ' ', Piece.RIGHT)
    }
    return piece(start, dark) + middle.toString().repeat(columns - 2) + piece(end, dark)
}

/** A line of [columns] top pieces in one layer: a divider is the top edge of a frame, both tones. */
internal fun dividerRow(columns: Int, dark: Boolean): String = piece(Piece.TOP, dark).toString().repeat(columns)

/**
 * A frame around [content]: a panel on its own ground, its border written in the font's pieces.
 *
 * A frame says "object" (pixel-ui): a card, a tile, a dialog, a button, a message. Its content is
 * drawn on the panel's surface (LocalRetroSurface), its inks measured against the panel's ground.
 * An [input] is a place on the page rather than an object on it: it stays on the surface around
 * it, its fill that surface's ground.
 *
 * Its size falls on whole cells both ways. A size the caller imposes is rounded down to whole
 * cells; otherwise the content decides. Across, the content is inset by a whole cell each side;
 * down, it is centred in what the frame leaves, the frame keeping [air] around it: the border
 * alone for a [compact] frame (a button, one line in two rows), a whole cell otherwise.
 *
 * [pressed] swaps the border's two tones while a finger is down: no ripple in this register. A
 * frame inside an item being lifted (LocalRetroLifted) swaps them too.
 *
 * A [fillContent] frame gives its content all its inside, a cell in from every edge, laid from the
 * top: a tile, whose content places itself.
 */
@Composable
internal fun Framed(
    modifier: Modifier = Modifier,
    input: Boolean = false,
    compact: Boolean = false,
    pressed: Boolean = false,
    fillContent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val grid = retroGrid()
    val surface = if (input) retroSurface else retroColors.panel
    val swapped = pressed || LocalRetroLifted.current
    val outer = (if (swapped) surface.borderInner else surface.borderOuter).srgb
    val inner = (if (swapped) surface.borderOuter else surface.borderInner).srgb
    val fill = surface.ground.srgb
    val measurer = rememberTextMeasurer()
    val style = grid.text
    val tile = grid.cellPx
    val border = grid.px(RetroGrid.BORDER)
    val air = if (compact) border else tile

    val drawn = modifier.drawWithCache {
        val columns = (size.width / tile).toInt()
        val rows = (size.height / tile).toInt()
        val layers = (0 until rows).map { row ->
            listOf(false, true).map { dark ->
                measurer.measure(frameRow(columns, row, rows, dark), style.copy(color = if (dark) inner else outer), softWrap = false)
            }
        }
        onDrawBehind {
            drawRect(fill, topLeft = Offset(border.toFloat(), border.toFloat()),
                size = Size(size.width - 2 * border, size.height - 2 * border))
            layers.forEachIndexed { row, both ->
                // A piece fills the eleven rows above its baseline: the row's cell ends there.
                for (layout in both) drawText(layout, topLeft = Offset(0f, (row + 1) * tile - layout.firstBaseline))
            }
        }
    }

    Layout(
        modifier = drawn,
        content = {
            CompositionLocalProvider(LocalRetroSurface provides if (input) LocalRetroSurface.current else RetroSurface.PANEL) {
                Box(content = content)
            }
        },
    ) { measurables, constraints ->
        val givenColumns = if (constraints.hasFixedWidth) (constraints.maxWidth / tile).coerceAtLeast(2) else null
        val givenRows = if (constraints.hasFixedHeight) (constraints.maxHeight / tile).coerceAtLeast(2) else null
        val room = when {
            givenColumns != null -> (givenColumns - 2) * tile
            constraints.hasBoundedWidth -> (constraints.maxWidth / tile - 2).coerceAtLeast(0) * tile
            else -> Constraints.Infinity
        }
        if (fillContent && givenColumns != null && givenRows != null) {
            val inside = Constraints.fixed(room, ((givenRows - 2) * tile).coerceAtLeast(0))
            val placeable = measurables.single().measure(inside)
            return@Layout layout(givenColumns * tile, givenRows * tile) { placeable.place(tile, tile) }
        }
        val placeable = measurables.single().measure(
            Constraints(
                minWidth = if (givenColumns != null) room else 0,
                maxWidth = room,
                maxHeight = when {
                    givenRows != null -> (givenRows * tile - 2 * border).coerceAtLeast(0)
                    constraints.hasBoundedHeight -> (constraints.maxHeight - 2 * border).coerceAtLeast(0)
                    else -> Constraints.Infinity
                },
            )
        )
        val columns = givenColumns ?: (cells(placeable.width, tile) + 2).coerceAtLeast(2)
        val rows = givenRows ?: cells(placeable.height + 2 * air, tile).coerceAtLeast(2)
        val width = columns * tile
        val height = rows * tile
        layout(width, height) {
            // Centred down in whole drawing pixels: the vertical is free, as long as it is whole.
            val slack = (height - placeable.height) / 2 / grid.scale * grid.scale
            placeable.place(tile, slack)
        }
    }
}

/** How many whole cells [span] pixels take. */
private fun cells(span: Int, tile: Int): Int = (span + tile - 1) / tile
