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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.assistant.core.drawing.DrawColor
import com.assistant.core.drawing.DrawShape
import com.assistant.core.drawing.Drawing
import com.assistant.core.drawing.InkLevel
import com.assistant.core.drawing.PathStep
import com.assistant.core.drawing.SymbolShape
import com.assistant.core.drawing.TextAnchor
import com.assistant.core.drawing.TextBaseline
import com.assistant.core.themes.TagColor

/**
 * The default theme's drawings: smooth strokes and fills on a Canvas, the palette's names in deeper
 * tones than its tags (a line must stand out on the page, a tag carries dark text), its inks from
 * the Material scheme, what is missing hatched.
 */
internal object DefaultDrawing {

    /** A name of the palette in a drawing: deep on a light page, light on a dark one. */
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
    fun Draw(drawing: Drawing, style: TextStyle, dark: Boolean, modifier: Modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val strong = MaterialTheme.colorScheme.onSurface
        val medium = MaterialTheme.colorScheme.onSurfaceVariant
        val faint = MaterialTheme.colorScheme.outlineVariant
        val (width, height) = with(density) { drawing.width.toDp() to drawing.height.toDp() }

        Canvas(modifier = modifier.size(width, height)) {
            fun resolve(color: DrawColor): Color = when (color) {
                is DrawColor.Palette -> color(color.color, dark)
                is DrawColor.Mix -> lerp(color(color.from, dark), color(color.to, dark), color.t.coerceIn(0f, 1f))
                is DrawColor.Ink -> when (color.level) {
                    InkLevel.STRONG -> strong
                    InkLevel.MEDIUM -> medium
                    InkLevel.FAINT -> faint
                }
            }
            fun dash(lengths: List<Float>?) = lengths?.let { PathEffect.dashPathEffect(it.toFloatArray()) }

            drawing.shapes.forEach { shape ->
                when (shape) {
                    is DrawShape.Box -> if (shape.missing) drawMissing(shape, resolve(shape.color))
                        else drawRect(resolve(shape.color), Offset(shape.rect.left, shape.rect.top),
                            Size(shape.rect.right - shape.rect.left, shape.rect.bottom - shape.rect.top), alpha = shape.opacity)
                    is DrawShape.Path -> drawPath(
                        path(shape.steps), resolve(shape.color), alpha = shape.opacity,
                        style = if (shape.filled) Fill else Stroke(width = shape.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash(shape.dash))
                    )
                    is DrawShape.Symbol -> drawSymbol(shape, resolve(shape.color))
                    is DrawShape.Arc -> drawArc(
                        resolve(shape.color),
                        // Degrees clockwise from the top; Compose counts from three o'clock
                        startAngle = shape.start - 90f, sweepAngle = shape.sweep, useCenter = true,
                        topLeft = Offset(shape.center.x - shape.outer, shape.center.y - shape.outer),
                        size = Size(shape.outer * 2, shape.outer * 2), alpha = shape.opacity
                    )
                    is DrawShape.Segment -> drawLine(
                        resolve(shape.color), Offset(shape.from.x, shape.from.y), Offset(shape.to.x, shape.to.y),
                        strokeWidth = shape.strokeWidth, cap = StrokeCap.Round, pathEffect = dash(shape.dash)
                    )
                    is DrawShape.Label -> {
                        val laid = measurer.measure(shape.text, style.copy(color = resolve(shape.color)))
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
                        drawText(laid, topLeft = Offset(x, y))
                    }
                    is DrawShape.Arrowhead -> {
                        val s = shape.size
                        val tip = if (shape.up) -s else s
                        drawPath(Path().apply {
                            moveTo(shape.at.x, shape.at.y + tip / 2)
                            lineTo(shape.at.x - s / 2, shape.at.y - tip / 2)
                            lineTo(shape.at.x + s / 2, shape.at.y - tip / 2)
                            close()
                        }, resolve(shape.color))
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

    /** What is missing: hatched in its color, a dashed border round it, never a value. */
    private fun DrawScope.drawMissing(shape: DrawShape.Box, color: Color) {
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

    private fun DrawScope.drawSymbol(shape: DrawShape.Symbol, color: Color) {
        val c = Offset(shape.center.x, shape.center.y)
        val r = shape.size / 2
        val style = if (shape.filled) Fill else Stroke(width = 1.5f * density)
        when (shape.shape) {
            SymbolShape.CIRCLE -> drawCircle(color, r, c, alpha = shape.opacity, style = style)
            SymbolShape.SQUARE -> drawRect(color, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), alpha = shape.opacity, style = style)
            SymbolShape.TRIANGLE -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close() }, color, shape.opacity, style)
            SymbolShape.DIAMOND -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, color, shape.opacity, style)
            SymbolShape.CROSS -> {
                drawLine(color, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = 2f * density, alpha = shape.opacity)
                drawLine(color, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = 2f * density, alpha = shape.opacity)
            }
        }
    }
}
