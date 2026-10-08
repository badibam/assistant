package app.treelune.themes.retro

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.treelune.core.themes.Lch
import app.treelune.core.themes.TagColor
import kotlin.math.max

/**
 * The retro theme's palettes: each a handful of numbers in OKLCH (palettes.json, a resource of
 * this package), every colour derived from them here.
 *
 * The numbers are set by eye on the bench (bench/palettes.html, `./run themes`), which derives
 * the same colours in bench/palette-derive.js and saves them beside the numbers; RetroPaletteTest
 * requires this file to find them again. Any change to the derivation is made in both.
 */

/**
 * One surface's colours: a ground, its three inks, the two tones of a frame's border on it, and
 * the states seen on it.
 */
@Immutable
data class Surface(
    val ground: Lch,
    val ink: Lch,
    /** The secondary ink, written in the thin weight. */
    val dim: Lch,
    /** The ink of a text that stands out (TextType.STRONG): Cartouche has no bold. */
    val strong: Lch,
    val borderOuter: Lch,
    val borderInner: Lch,
    val success: Lch,
    val warning: Lch,
    val error: Lch,
    val info: Lch,
) {
    /** Muted is the dim ink: in this register, grey says "off". */
    val muted: Lch get() = dim
}

/** Every colour of one palette. */
@Immutable
class RetroColors(val numbers: PaletteNumbers) {

    /** The screen's ground and what is drawn straight on it, an input's frame included. */
    val screen: Surface = surface(
        numbers.groundLightness, numbers.groundChroma, numbers.inkLightness,
        numbers.borderOuterStep, numbers.borderInnerStep, numbers.statusLightness,
    )

    /** The inside of a frame: a card, a tile, a dialog, a button, a message. */
    val panel: Surface = surface(
        numbers.panelLightness, numbers.panelChroma, numbers.panelInkLightness,
        numbers.panelBorderOuterStep, numbers.panelBorderInnerStep, numbers.panelStatusLightness,
    )

    /** A tag's colour, its hue spread round the circle at the palette's common lightness and chroma. */
    fun tag(color: TagColor): Lch = when (color) {
        TagColor.GREY -> Lch(numbers.tagLightness, GREY_CHROMA, numbers.hue)
        else -> Lch(numbers.tagLightness, numbers.tagChroma, TAG_HUES.getValue(color))
    }

    /**
     * A name of the palette in a drawing (a chart's series): its tag's hue at the states'
     * lightness and chroma in a frame, which read on a tile; grey the palette's own.
     */
    fun drawing(color: TagColor): Lch = when (color) {
        TagColor.GREY -> Lch(numbers.panelStatusLightness, GREY_CHROMA, numbers.hue)
        else -> Lch(numbers.panelStatusLightness, numbers.statusChroma, TAG_HUES.getValue(color))
    }

    /**
     * An item's icon colour (docs/design/icon-colors.md) on [surface]: its tag's hue at the
     * lightness of the states there, which read on it, and at their chroma.
     */
    fun icon(color: TagColor, surface: Surface): Lch = Lch(surface.success.l, numbers.statusChroma, TAG_HUES.getValue(color))

    /** A tag's name on its colour: dark letters on a light tag, light ones on a dark tag. */
    val tagText: Lch =
        if (numbers.tagLightness >= 0.6f) Lch(0.25f, STRONG_CHROMA, numbers.hue)
        else Lch(0.97f, INK_CHROMA, numbers.hue)

    /** Every colour under the name the bench saves it by, to compare the two derivations. */
    val named: Map<String, Lch>
        get() = buildMap {
            for ((prefix, s) in listOf("screen" to screen, "panel" to panel)) {
                put("${prefix}_ground", s.ground)
                put("${prefix}_ink", s.ink)
                put("${prefix}_dim", s.dim)
                put("${prefix}_strong", s.strong)
                put("${prefix}_border_outer", s.borderOuter)
                put("${prefix}_border_inner", s.borderInner)
                put("${prefix}_status_muted", s.muted)
                put("${prefix}_status_success", s.success)
                put("${prefix}_status_warning", s.warning)
                put("${prefix}_status_error", s.error)
                put("${prefix}_status_info", s.info)
            }
            TagColor.entries.forEach { put("tag_${it.name.lowercase()}", tag(it)) }
            TagColor.entries.forEach { put("drawing_${it.name.lowercase()}", drawing(it)) }
            put("tag_text", tagText)
        }

    /** [up] is the way away from the ground: lighter off a dark ground, darker off a pale one. */
    private fun surface(
        groundL: Float, groundC: Float, inkL: Float, outerStep: Float, innerStep: Float, statusL: Float,
    ): Surface {
        val hue = numbers.hue
        val up = if (groundL < 0.5f) 1f else -1f
        val decor = max(groundC, DECOR_MIN_CHROMA)
        fun status(statusHue: Float) = Lch(statusL, numbers.statusChroma, statusHue)
        return Surface(
            ground = Lch(groundL, groundC, hue),
            ink = Lch(inkL, numbers.inkChroma, hue),
            dim = Lch(inkL - numbers.dimStep * up, numbers.inkChroma, hue),
            strong = Lch((inkL + numbers.strongStep * up).coerceIn(0f, 1f), numbers.inkChroma + STRONG_EXTRA_CHROMA, hue),
            borderOuter = Lch(groundL + outerStep, decor, hue + OUTER_HUE_TURN),
            borderInner = Lch(groundL + innerStep, decor, hue),
            success = status(145f),
            warning = status(70f),
            error = status(25f),
            info = status(250f),
        )
    }

    private companion object {
        /** A tag's name: a faint tint of the palette's hue, a little more on a light tag. */
        const val INK_CHROMA = 0.018f
        const val STRONG_CHROMA = 0.03f
        /** A strong ink carries a little more chroma than the ink, so it reads as the same ink, lit. */
        const val STRONG_EXTRA_CHROMA = 0.012f
        /** A border's chroma never falls under this, or a frame on a grey ground is grey too. */
        const val DECOR_MIN_CHROMA = 0.045f
        /** The outer tone of a border turns this far from the hue, for the relief of two tones. */
        const val OUTER_HUE_TURN = 14f
        /** A grey tag keeps a trace of chroma, so it is this palette's grey. */
        const val GREY_CHROMA = 0.015f

        val TAG_HUES = mapOf(
            TagColor.RED to 25f, TagColor.ORANGE to 55f, TagColor.YELLOW to 95f, TagColor.GREEN to 145f,
            TagColor.TEAL to 185f, TagColor.BLUE to 250f, TagColor.PURPLE to 300f, TagColor.PINK to 350f,
        )
    }
}
