package app.treelune.core.themes

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * A colour turned round the hue circle in OKLCH (Björn Ottosson's OKLab), its lightness and
 * chroma kept: what a theme's colours become at a hue shift (Appearance.hueShift). A colour the
 * screen cannot show at its new hue loses chroma, never lightness, so its contrast holds.
 */
object Oklch {

    fun rotate(color: Color, degrees: Float): Color {
        if (degrees % 360f == 0f) return color
        val (l, c, h) = lch(color)
        return color(l, c, h + degrees, color.alpha)
    }

    private fun lch(color: Color): Triple<Float, Float, Float> {
        val r = linear(color.red); val g = linear(color.green); val b = linear(color.blue)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        return Triple(lightness, hypot(a, bb), Math.toDegrees(atan2(bb, a).toDouble()).toFloat())
    }

    private fun color(l: Float, c: Float, h: Float, alpha: Float): Color {
        var chroma = c
        if (!inGamut(toLinear(l, chroma, h))) {
            var low = 0f
            var high = chroma
            repeat(GAMUT_STEPS) {
                val middle = (low + high) / 2f
                if (inGamut(toLinear(l, middle, h))) low = middle else high = middle
            }
            chroma = low
        }
        val (r, g, b) = toLinear(l, chroma, h).map(::encode)
        return Color(r, g, b, alpha)
    }

    private fun toLinear(l: Float, c: Float, h: Float): List<Float> {
        val radians = Math.toRadians(h.toDouble()).toFloat()
        val a = c * cos(radians)
        val b = c * sin(radians)
        val lp = (l + 0.3963377774f * a + 0.2158037573f * b).pow(3)
        val mp = (l - 0.1055613458f * a - 0.0638541728f * b).pow(3)
        val sp = (l - 0.0894841775f * a - 1.2914855480f * b).pow(3)
        return listOf(
            4.0767416621f * lp - 3.3077115913f * mp + 0.2309699292f * sp,
            -1.2684380046f * lp + 2.6097574011f * mp - 0.3413193965f * sp,
            -0.0041960863f * lp - 0.7034186147f * mp + 1.7076147010f * sp,
        )
    }

    private fun inGamut(rgb: List<Float>) = rgb.all { it >= -SLACK && it <= 1f + SLACK }

    private fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    private fun encode(channel: Float): Float {
        val x = channel.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f
    }

    /** Rounding room, so a colour exactly on the gamut's edge is not walked back in. */
    private const val SLACK = 0.0005f

    /** Bisections of the chroma: eighteen land well inside one 8-bit step. */
    private const val GAMUT_STEPS = 18
}
