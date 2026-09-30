package com.assistant.core.tools

import androidx.compose.runtime.Composable

/**
 * What a tool type shows on a tool's tile, beside the header the core draws (icon and name,
 * 2×1 cells). Made once per tile by ToolTypeContract.rememberTile, so that its two parts share
 * what the tile has loaded.
 *
 * Nothing in a tile scrolls: what does not fit is the tool type's to leave out, and to say so.
 */
interface ToolTile {

    /**
     * One component of 2×1 cells, two lines high, the same in every mode that has it (LINE,
     * CONDENSED, EXTENDED, SQUARE, FULL), its actions included.
     */
    @Composable
    fun Summary()

    /**
     * The body under the header and the summary, four cells wide: [rows] rows of cells, each two
     * lines high (one for EXTENDED, three for SQUARE), or null for FULL, which takes the rows
     * what it shows needs.
     */
    @Composable
    fun Body(rows: Int?)

    /**
     * What stands at the summary's place, 2×1, in the modes without a body (LINE, CONDENSED): the
     * summary, unless the tool type shows there what its body shows better in so little room.
     */
    @Composable
    fun Glance() = Summary()
}
