package com.assistant.themes.retro

import com.assistant.core.themes.PaletteMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * palettes.json as the theme reads it: each palette's id, its family and mode, its numbers, and the colours
 * the bench derived from them (RetroPalette.kt derives the theme's own).
 */

/** The numbers of one palette, as palettes.json holds them. */
@Serializable
data class PaletteNumbers(
    val hue: Float,
    @SerialName("ground_lightness") val groundLightness: Float,
    @SerialName("ground_chroma") val groundChroma: Float,
    @SerialName("ink_lightness") val inkLightness: Float,
    /** The chroma of every ink, in the palette's hue: the text's and the icons' warmth. */
    @SerialName("ink_chroma") val inkChroma: Float,
    /** How far the dim ink stands from the ink, toward the ground. */
    @SerialName("dim_step") val dimStep: Float,
    /** How far the strong ink stands from the ink, away from the ground. */
    @SerialName("strong_step") val strongStep: Float,
    /** The border of a frame on the screen's ground (an input), as steps of lightness off it. */
    @SerialName("border_outer_step") val borderOuterStep: Float,
    @SerialName("border_inner_step") val borderInnerStep: Float,
    /** The inside of a frame: a panel with its own ground and ink. */
    @SerialName("panel_lightness") val panelLightness: Float,
    @SerialName("panel_chroma") val panelChroma: Float,
    @SerialName("panel_ink_lightness") val panelInkLightness: Float,
    @SerialName("panel_border_outer_step") val panelBorderOuterStep: Float,
    @SerialName("panel_border_inner_step") val panelBorderInnerStep: Float,
    /** Every tag colour shares one lightness and one chroma; only the hue tells them apart. */
    @SerialName("tag_lightness") val tagLightness: Float,
    @SerialName("tag_chroma") val tagChroma: Float,
    /** The states' lightness on the screen and in a frame, and their common chroma. */
    @SerialName("status_lightness") val statusLightness: Float,
    @SerialName("panel_status_lightness") val panelStatusLightness: Float,
    @SerialName("status_chroma") val statusChroma: Float,
)

/**
 * One palette of palettes.json: its id ("retro_<family>_<mode>"), its family and mode, its
 * numbers, and the colours the bench derived. Every family is there in both modes.
 */
@Serializable
data class PaletteEntry(
    val id: String,
    val family: String,
    val mode: PaletteMode,
    val numbers: PaletteNumbers,
    val derived: Map<String, String> = emptyMap(),
)

@Serializable
private data class PaletteFile(val palettes: List<PaletteEntry>)

/** The palettes of palettes.json, read once. */
object RetroPalettes {

    val entries: List<PaletteEntry> by lazy {
        // An absolute path: the release build renames and moves this class out of its package,
        // and a relative one would then be looked up beside the renamed class, where it is not.
        val stream = RetroPalettes::class.java.getResourceAsStream("/com/assistant/themes/retro/palettes.json")
            ?: error("palettes.json is missing from the retro theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(PaletteFile.serializer(), text).palettes
    }

    private val colors: Map<String, RetroColors> by lazy {
        entries.associate { it.id to RetroColors(it.numbers) }
    }

    /** The colours of [paletteId], which must be one of this theme's: a palette asked of the wrong theme is a bug. */
    fun colors(paletteId: String): RetroColors =
        colors[paletteId] ?: error("no retro palette '$paletteId'")
}
