package com.assistant.themes.retro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.assistant.core.drawing.DrawColor
import com.assistant.core.drawing.DrawShape
import com.assistant.core.drawing.Drawing
import com.assistant.core.drawing.InkLevel
import com.assistant.core.drawing.TextAnchor
import com.assistant.core.drawing.TextBaseline
import com.assistant.core.themes.Lch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The retro theme's drawings: put down in its pixels (RetroRaster), blown up by the grid's whole
 * factor without smoothing, its texts written over in Cartouche on whole pixels.
 */
internal object RetroDrawing {

    /** Steps between the two colors of a mix (DrawColor.Mix): five tones, each told apart from the next. */
    private const val MIX_STEPS = 4

    @Composable
    fun Draw(drawing: Drawing, style: TextStyle, modifier: Modifier) {
        val scale = retroGrid().scale
        val colors = retroColors
        val surface = retroSurface
        val measurer = rememberTextMeasurer()
        val resolve = remember(colors, surface) { resolver(colors, surface) }
        val raster = remember(drawing, scale, resolve) { RetroRaster(drawing, scale, resolve) }
        val image = remember(raster) {
            android.graphics.Bitmap.createBitmap(raster.pixels, raster.width, raster.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        val (width, height) = with(LocalDensity.current) { drawing.width.toDp() to drawing.height.toDp() }

        Canvas(modifier = modifier.size(width, height)) {
            clipRect(0f, 0f, size.width, size.height) {
                drawImage(image, dstSize = IntSize(raster.width * scale, raster.height * scale), filterQuality = FilterQuality.None)
                // Texts last, over every shape, their corner on a whole pixel
                drawing.shapes.filterIsInstance<DrawShape.Label>().forEach { label ->
                    val laid = measurer.measure(label.text, style.copy(color = androidx.compose.ui.graphics.Color(resolve(label.color))))
                    val x = when (label.anchor) {
                        TextAnchor.START -> label.at.x
                        TextAnchor.MIDDLE -> label.at.x - laid.size.width / 2f
                        TextAnchor.END -> label.at.x - laid.size.width
                    }
                    val y = when (label.baseline) {
                        TextBaseline.TOP -> label.at.y
                        TextBaseline.MIDDLE -> label.at.y - laid.size.height / 2f
                        TextBaseline.BOTTOM -> label.at.y - laid.size.height
                    }
                    drawText(laid, topLeft = Offset((x / scale).roundToInt() * scale.toFloat(), (y / scale).roundToInt() * scale.toFloat()))
                }
            }
        }
    }

    /**
     * Each color by meaning in this palette, as ARGB: a name as the theme draws series
     * (RetroColors.drawing), a mix in one of five tones between its two, the inks of the surface
     * drawn on, the faint one its border's inner tone.
     */
    fun resolver(colors: RetroColors, surface: Surface): (DrawColor) -> Int = { color ->
        when (color) {
            is DrawColor.Palette -> colors.drawing(color.color).srgb.toArgb()
            is DrawColor.Mix -> {
                val t = (color.t.coerceIn(0f, 1f) * MIX_STEPS).roundToInt() / MIX_STEPS.toFloat()
                mix(colors.drawing(color.from), colors.drawing(color.to), t).srgb.toArgb()
            }
            is DrawColor.Ink -> when (color.level) {
                InkLevel.STRONG -> surface.ink
                InkLevel.MEDIUM -> surface.dim
                InkLevel.FAINT -> surface.borderInner
            }.srgb.toArgb()
        }
    }

    /** [a] to [b] at [t], mixed in OKLab, which keeps a mixed color looking like itself. */
    fun mix(a: Lch, b: Lch, t: Float): Lch {
        fun lab(c: Lch) = Triple(c.l, c.c * cos(Math.toRadians(c.h.toDouble())).toFloat(), c.c * sin(Math.toRadians(c.h.toDouble())).toFloat())
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        val l = l1 + (l2 - l1) * t
        val x = a1 + (a2 - a1) * t
        val y = b1 + (b2 - b1) * t
        return Lch(l, hypot(x, y), Math.toDegrees(atan2(y, x).toDouble()).toFloat())
    }
}
