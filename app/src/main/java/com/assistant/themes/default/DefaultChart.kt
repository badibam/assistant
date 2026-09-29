package com.assistant.themes.default

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.assistant.core.charts.ChartScene
import com.assistant.core.charts.PathStep
import com.assistant.core.charts.SceneColor
import com.assistant.core.charts.SceneRole
import com.assistant.core.charts.SceneShape
import com.assistant.core.charts.Shape
import com.assistant.core.charts.TextAnchor
import com.assistant.core.charts.TextBaseline
import com.assistant.core.themes.TagColor

/**
 * The default theme's charts: smooth strokes and fills on a Canvas, the palette's names in deeper
 * tones than its tags (a line must stand out on the page, a tag carries dark text), axes and
 * texts in the theme's ink.
 */
internal object DefaultChart {

    /** A name of the palette as a mark of a chart: deep on a light page, light on a dark one. */
    fun color(color: TagColor, dark: Boolean): Color = when (color) {
        TagColor.RED -> if (dark) Color(0xFFF08A8A) else Color(0xFFC94A4A)
        TagColor.ORANGE -> if (dark) Color(0xFFF5AE72) else Color(0xFFD9772B)
        TagColor.YELLOW -> if (dark) Color(0xFFEBD36A) else Color(0xFFB8961A)
        TagColor.GREEN -> if (dark) Color(0xFF8FD08A) else Color(0xFF4E9A45)
        TagColor.TEAL -> if (dark) Color(0xFF79CFC4) else Color(0xFF2E8F84)
        TagColor.BLUE -> if (dark) Color(0xFF8DB0F0) else Color(0xFF3D6FC6)
        TagColor.PURPLE -> if (dark) Color(0xFFBE9BE3) else Color(0xFF7A55B0)
        TagColor.PINK -> if (dark) Color(0xFFEE95C3) else Color(0xFFC4508D)
        TagColor.GREY -> if (dark) Color(0xFFB8B2C2) else Color(0xFF8C8699)
    }

    @Composable
    fun Draw(scene: ChartScene, style: TextStyle, dark: Boolean, modifier: Modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val ink = MaterialTheme.colorScheme.onSurfaceVariant
        val title = MaterialTheme.colorScheme.onSurface
        val grid = MaterialTheme.colorScheme.outlineVariant
        val hole = MaterialTheme.colorScheme.outline
        val (width, height) = with(density) { scene.width.toDp() to scene.height.toDp() }

        Canvas(modifier = modifier.size(width, height)) {
            fun resolve(color: SceneColor?, fallback: Color): Color = when (color) {
                null -> fallback
                is SceneColor.Tag -> color(color.color, dark)
                is SceneColor.Mix -> lerp(color(color.from, dark), color(color.to, dark), color.t.coerceIn(0f, 1f))
            }
            fun inkOf(role: SceneRole) = when (role) {
                SceneRole.GRID -> grid
                SceneRole.AXIS_TITLE, SceneRole.CELL_TITLE, SceneRole.LEGEND_TITLE -> title
                SceneRole.HOLE -> hole
                else -> ink
            }
            scene.shapes.forEach { shape ->
                when (shape) {
                    is SceneShape.Box -> if (shape.role == SceneRole.HOLE) drawHole(shape, hole)
                        else drawRect(resolve(shape.color, inkOf(shape.role)), Offset(shape.rect.left, shape.rect.top),
                            Size(shape.rect.right - shape.rect.left, shape.rect.bottom - shape.rect.top), alpha = shape.opacity)
                    is SceneShape.Path -> drawPath(
                        path(shape.steps),
                        resolve(shape.color, inkOf(shape.role)),
                        alpha = shape.opacity,
                        style = if (shape.filled) Fill else Stroke(
                            width = shape.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round,
                            pathEffect = shape.dash?.let { PathEffect.dashPathEffect(it.toFloatArray()) }
                        )
                    )
                    is SceneShape.Symbol -> drawSymbol(shape, resolve(shape.color, inkOf(shape.role)), density.density)
                    is SceneShape.Arc -> drawArc(
                        resolve(shape.color, inkOf(shape.role)),
                        // Degrees clockwise from the top; Compose counts from three o'clock
                        startAngle = shape.start - 90f, sweepAngle = shape.sweep, useCenter = true,
                        topLeft = Offset(shape.center.x - shape.outer, shape.center.y - shape.outer),
                        size = Size(shape.outer * 2, shape.outer * 2), alpha = shape.opacity
                    )
                    is SceneShape.Segment -> drawLine(
                        resolve(shape.color, inkOf(shape.role)), Offset(shape.from.x, shape.from.y), Offset(shape.to.x, shape.to.y),
                        strokeWidth = shape.strokeWidth, cap = StrokeCap.Round,
                        pathEffect = shape.dash?.let { PathEffect.dashPathEffect(it.toFloatArray()) }
                    )
                    is SceneShape.Label -> {
                        val laid = measurer.measure(shape.text, style.copy(color = resolve(shape.color, inkOf(shape.role))))
                        val x = when (shape.anchor) {
                            TextAnchor.START -> shape.at.x
                            TextAnchor.MIDDLE -> shape.at.x - laid.size.width / 2f
                            TextAnchor.END -> shape.at.x - laid.size.width
                        }
                        val y = when (shape.baseline) {
                            TextBaseline.TOP -> shape.at.y
                            TextBaseline.MIDDLE -> shape.at.y - laid.size.height / 2f
                            TextBaseline.BOTTOM -> shape.at.y - laid.size.height
                        }
                        if (shape.rotated) rotate(-90f, Offset(shape.at.x, shape.at.y)) { drawText(laid, topLeft = Offset(x, y)) }
                        else drawText(laid, topLeft = Offset(x, y))
                    }
                    is SceneShape.Beyond -> {
                        val s = shape.size
                        val tip = if (shape.up) -s else s
                        drawPath(Path().apply {
                            moveTo(shape.at.x, shape.at.y + tip / 2)
                            lineTo(shape.at.x - s / 2, shape.at.y - tip / 2)
                            lineTo(shape.at.x + s / 2, shape.at.y - tip / 2)
                            close()
                        }, resolve(shape.color, ink))
                    }
                }
            }
        }
    }

    private fun path(steps: List<PathStep>): Path = Path().apply {
        steps.forEach { step ->
            when (step) {
                is PathStep.MoveTo -> moveTo(step.to.x, step.to.y)
                is PathStep.LineTo -> lineTo(step.to.x, step.to.y)
                is PathStep.CubicTo -> cubicTo(step.control1.x, step.control1.y, step.control2.x, step.control2.y, step.to.x, step.to.y)
                PathStep.Close -> close()
            }
        }
    }

    /** A hole: hatched across the plot, a value that is missing and not zero. */
    private fun DrawScope.drawHole(shape: SceneShape.Box, color: Color) {
        val r = shape.rect
        val d = density
        clipRect(r.left, r.top, r.right, r.bottom) {
            var x = r.left - (r.bottom - r.top)
            while (x < r.right) {
                drawLine(color, Offset(x, r.bottom), Offset(x + (r.bottom - r.top), r.top), strokeWidth = d, alpha = 0.5f)
                x += 6 * d
            }
        }
        drawRect(color, Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top), alpha = 0.6f,
            style = Stroke(width = d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * d, 3 * d))))
    }

    private fun DrawScope.drawSymbol(shape: SceneShape.Symbol, color: Color, density: Float) {
        val c = Offset(shape.center.x, shape.center.y)
        val r = shape.size / 2
        val style = if (shape.filled) Fill else Stroke(width = 1.5f * density)
        when (shape.shape) {
            Shape.CIRCLE -> drawCircle(color, r, c, alpha = shape.opacity, style = style)
            Shape.SQUARE -> drawRect(color, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), alpha = shape.opacity, style = style)
            Shape.TRIANGLE -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close() }, color, shape.opacity, style)
            Shape.DIAMOND -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, color, shape.opacity, style)
            Shape.CROSS -> {
                drawLine(color, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = 2f * density, alpha = shape.opacity)
                drawLine(color, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = 2f * density, alpha = shape.opacity)
            }
        }
    }
}
