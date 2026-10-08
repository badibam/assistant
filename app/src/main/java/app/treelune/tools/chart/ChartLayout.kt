package app.treelune.tools.chart

import app.treelune.core.drawing.DrawPoint
import app.treelune.core.drawing.DrawRect
import app.treelune.core.drawing.Drawing
import app.treelune.core.fields.FieldDefinition
import kotlin.math.hypot

/**
 * A chart laid out (ChartSceneBuilder): the drawing the theme draws, and what a touch finds on it.
 *
 * @property hits The rows the shapes stand for, the topmost last
 */
data class ChartLayout(val drawing: Drawing, val hits: List<Hit>) {

    /**
     * What stands at ([x], [y]): the topmost shape holding it, or else the nearest point within
     * [slop] pixels; null when nothing does.
     */
    fun hitAt(x: Float, y: Float, slop: Float): Hit? {
        hits.lastOrNull { it.area != null && it.area.contains(x, y) }?.let { return it }
        return hits.filter { it.point != null }
            .map { it to hypot(it.point!!.x - x, it.point.y - y) }
            .filter { it.second <= slop }
            .minByOrNull { it.second }?.first
    }
}

/**
 * What a touch on a shape finds: the row of a layer's table it stands for, with the table's
 * columns to show it. [area] for a shape with a surface, [point] for a point found by nearness.
 */
data class Hit(val row: Row, val columns: Map<String, FieldDefinition>, val area: DrawRect? = null, val point: DrawPoint? = null)
