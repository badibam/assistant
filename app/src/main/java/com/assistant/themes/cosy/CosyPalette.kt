package com.assistant.themes.cosy

import androidx.compose.runtime.Immutable
import com.assistant.core.themes.Lch
import com.assistant.core.themes.PaletteMode
import com.assistant.core.themes.TagColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The cosy theme's palettes (docs/design/cosy-theme.md): each a handful of numbers in OKLCH
 * (palettes.json, a resource of this package), every colour derived from them here. The light one
 * is the mockup's sand and cream, read off it; the dark one a summer night, blue, its ink cream.
 */

/** The numbers of one palette, as palettes.json holds them. */
@Serializable
data class CosyNumbers(
    /** The palette's hue: the ground's, the tiles', their shadows'. Turned by the hue shift. */
    val hue: Float,
    @SerialName("ground_lightness") val groundLightness: Float,
    @SerialName("ground_chroma") val groundChroma: Float,
    /** The dots sown on the ground, as a step of lightness off it, and their chroma. */
    @SerialName("dot_step") val dotStep: Float,
    @SerialName("dot_chroma") val dotChroma: Float,
    /** A tile, a button, a dialog: the pieces that stand on the ground. */
    @SerialName("tile_lightness") val tileLightness: Float,
    @SerialName("tile_chroma") val tileChroma: Float,
    /** The full shadow under a piece, never blurred. */
    @SerialName("shadow_lightness") val shadowLightness: Float,
    @SerialName("shadow_chroma") val shadowChroma: Float,
    /** The ink, its hue turned this far from the palette's: brown on sand, cream on blue. */
    @SerialName("ink_lightness") val inkLightness: Float,
    @SerialName("ink_chroma") val inkChroma: Float,
    @SerialName("ink_turn") val inkTurn: Float,
    /** The secondary ink, and the strong one of a text that stands out. */
    @SerialName("dim_lightness") val dimLightness: Float,
    @SerialName("strong_lightness") val strongLightness: Float,
    /** The main colour (the leaf green), its hue turned from the palette's, its shadow, its ink. */
    @SerialName("accent_lightness") val accentLightness: Float,
    @SerialName("accent_chroma") val accentChroma: Float,
    @SerialName("accent_turn") val accentTurn: Float,
    @SerialName("accent_shadow_lightness") val accentShadowLightness: Float,
    @SerialName("on_accent_lightness") val onAccentLightness: Float,
    /** Every tag a pastel at one lightness and chroma, its name written in its own hue, darker or lighter. */
    @SerialName("tag_lightness") val tagLightness: Float,
    @SerialName("tag_chroma") val tagChroma: Float,
    @SerialName("tag_ink_lightness") val tagInkLightness: Float,
    @SerialName("tag_ink_chroma") val tagInkChroma: Float,
    /** The states' lightness and chroma, on a tile as on the ground. */
    @SerialName("status_lightness") val statusLightness: Float,
    @SerialName("status_chroma") val statusChroma: Float,
)

/** Every colour of one palette. */
@Immutable
class CosyColors(val numbers: CosyNumbers) {
    private val hue = numbers.hue

    val ground = Lch(numbers.groundLightness, numbers.groundChroma, hue)
    val dots = Lch(numbers.groundLightness + numbers.dotStep, numbers.dotChroma, hue)
    val tile = Lch(numbers.tileLightness, numbers.tileChroma, hue)
    val shadow = Lch(numbers.shadowLightness, numbers.shadowChroma, hue)

    val ink = Lch(numbers.inkLightness, numbers.inkChroma, hue + numbers.inkTurn)
    val dim = Lch(numbers.dimLightness, numbers.inkChroma, hue + numbers.inkTurn)
    val strong = Lch(numbers.strongLightness, numbers.inkChroma, hue + numbers.inkTurn)

    val accent = Lch(numbers.accentLightness, numbers.accentChroma, hue + numbers.accentTurn)
    val accentShadow = Lch(numbers.accentShadowLightness, numbers.accentChroma, hue + numbers.accentTurn)
    val onAccent = Lch(numbers.onAccentLightness, numbers.accentChroma / 2f, hue + numbers.accentTurn)

    val success = status(145f)
    val warning = status(55f)
    val error = status(25f)
    val info = status(250f)

    /** Muted is the dim ink: what is off steps back. */
    val muted: Lch get() = dim

    /** A tag's pastel, its hue its meaning, a grey keeping a trace of the palette's hue. */
    fun tag(color: TagColor): Lch = Lch(numbers.tagLightness, chromaOf(color, numbers.tagChroma), hueOf(color))

    /** A tag's name, in its own hue, readable on its pastel. */
    fun tagInk(color: TagColor): Lch = Lch(numbers.tagInkLightness, chromaOf(color, numbers.tagInkChroma), hueOf(color))

    private fun status(statusHue: Float) = Lch(numbers.statusLightness, numbers.statusChroma, statusHue)
    private fun hueOf(color: TagColor) = TAG_HUES[color] ?: hue
    private fun chromaOf(color: TagColor, chroma: Float) = if (color == TagColor.GREY) GREY_CHROMA else chroma

    private companion object {
        /** A grey tag keeps a trace of chroma, so it is this palette's grey. */
        const val GREY_CHROMA = 0.015f

        val TAG_HUES = mapOf(
            TagColor.RED to 25f, TagColor.ORANGE to 55f, TagColor.YELLOW to 95f, TagColor.GREEN to 145f,
            TagColor.TEAL to 185f, TagColor.BLUE to 230f, TagColor.PURPLE to 300f, TagColor.PINK to 355f,
        )
    }
}

/** One palette of palettes.json: its id ("cosy_light", "cosy_night"), its mode, its numbers. */
@Serializable
data class CosyPaletteEntry(val id: String, val mode: PaletteMode, val numbers: CosyNumbers)

@Serializable
private data class CosyPaletteFile(val palettes: List<CosyPaletteEntry>)

/** The palettes of palettes.json, read once: one per mode. */
object CosyPalettes {

    val entries: List<CosyPaletteEntry> by lazy {
        // An absolute path: the release build renames and moves this class out of its package,
        // and a relative one would then be looked up beside the renamed class, where it is not.
        val stream = CosyPalettes::class.java.getResourceAsStream("/com/assistant/themes/cosy/palettes.json")
            ?: error("palettes.json is missing from the cosy theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(CosyPaletteFile.serializer(), text).palettes
    }

    /** The colours derived so far, by mode and hue shift: a shift is chosen by dragging a slider. */
    private val colors = java.util.concurrent.ConcurrentHashMap<Pair<PaletteMode, Int>, CosyColors>()

    /**
     * The colours of [mode], the palette's hue turned [hueShift] degrees (Appearance.hueShift):
     * every colour derived from the hue turns with it, the states and the tags keeping their own.
     */
    fun colors(mode: PaletteMode, hueShift: Int): CosyColors = colors.getOrPut(mode to hueShift) {
        val numbers = entries.firstOrNull { it.mode == mode }?.numbers ?: error("no cosy palette in ${mode.name}")
        CosyColors(numbers.copy(hue = (numbers.hue + hueShift) % 360f))
    }
}
