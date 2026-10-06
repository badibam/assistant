package com.assistant.core.themes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** A colour as a palette states it: lightness, chroma and hue, OKLab's polar form. */
@Immutable
data class Lch(val l: Float, val c: Float, val h: Float) {

    /** The nearest colour sRGB can show, its chroma reduced and its hue kept. */
    val srgb: Color
        get() {
            val lightness = l.coerceIn(0f, 1f)
            val hue = ((h % 360f) + 360f) % 360f
            var chroma = max(0f, c)
            if (!inGamut(linear(lightness, chroma, hue))) {
                var low = 0f
                var high = chroma
                repeat(GAMUT_STEPS) {
                    val middle = (low + high) / 2f
                    if (inGamut(linear(lightness, middle, hue))) low = middle else high = middle
                }
                chroma = low
            }
            val (r, g, b) = linear(lightness, chroma, hue).map(::encode)
            return Color(red = r, green = g, blue = b)
        }

    private fun linear(l: Float, c: Float, h: Float): List<Float> {
        val radians = h * PI.toFloat() / 180f
        val a = c * cos(radians)
        val b = c * sin(radians)
        val lp = l + 0.3963377774f * a + 0.2158037573f * b
        val mp = l - 0.1055613458f * a - 0.0638541728f * b
        val sp = l - 0.0894841775f * a - 1.2914855480f * b
        val lc = lp * lp * lp
        val mc = mp * mp * mp
        val sc = sp * sp * sp
        return listOf(
            +4.0767416621f * lc - 3.3077115913f * mc + 0.2309699292f * sc,
            -1.2684380046f * lc + 2.6097574011f * mc - 0.3413193965f * sc,
            -0.0041960863f * lc - 0.7034186147f * mc + 1.7076147010f * sc,
        )
    }

    private fun inGamut(rgb: List<Float>) = rgb.all { it >= -SLACK && it <= 1f + SLACK }

    private fun encode(channel: Float): Float {
        val x = channel.coerceIn(0f, 1f)
        val encoded = if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f
        // Rounded to 8 bits, as a theme's bench writes it: two derivations agree on whole steps.
        return kotlin.math.round(encoded * 255f) / 255f
    }

    private companion object {
        /** Rounding room, so a colour exactly on the gamut's edge is not walked back in. */
        const val SLACK = 0.0005f

        /** Bisections of the chroma: eighteen land well inside one 8-bit step. */
        const val GAMUT_STEPS = 18
    }
}
