package com.assistant.themes.retro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.assistant.R
import com.assistant.core.themes.CurrentTheme
import kotlin.math.roundToInt

/**
 * The retro theme's grid: one drawing pixel of the font, blown up by a whole number of screen
 * pixels. That is the register's one hard rule (pixel-ui): a fractional factor makes pixels of
 * unequal widths.
 *
 * Everything the theme lays out is counted in drawing pixels and in cells, the cell being the
 * font's box across (eleven pixels): frames, panels, margins and spaces fall on whole cells. The
 * vertical of a line of text is free, as long as it is whole pixels.
 */
@Immutable
class RetroGrid(
    /** Screen pixels per drawing pixel: a whole number. */
    val scale: Int,
    private val density: Density,
) {
    /** [pixels] drawing pixels, as screen pixels. */
    fun px(pixels: Int): Int = pixels * scale

    /** [pixels] drawing pixels, as the length Compose lays out with. */
    fun dp(pixels: Int): Dp = with(density) { (pixels * scale).toDp() }

    /** [count] cells. */
    fun cells(count: Int): Dp = dp(count * CELL)

    /** The cell, in screen pixels. */
    val cellPx: Int get() = px(CELL)

    /** Ordinary text: the regular weight, the ink of the surface it is on. */
    val text: TextStyle = style(FontWeight.Normal)

    /** The thin weight: the register's secondary ink, a one-pixel stroke against the regular's two. */
    val thin: TextStyle = style(FontWeight.Thin)

    private fun style(weight: FontWeight) = TextStyle(
        fontFamily = Cartouche,
        fontWeight = weight,
        // Through toSp, which divides by the font scale as well as the density: the system's
        // text size is ignored, since at 1.3 a drawing pixel would land on 3.9 screen pixels.
        // The app enlarges by whole steps of the scale instead (CurrentTheme.sizeStep).
        fontSize = with(density) { px(EM).toSp() },
        lineHeight = with(density) { px(LINE).toSp() },
        // The row of air under a line goes at its bottom and is dropped after the last one: a
        // single line measures the font's fourteen rows, so a button holds in two frame rows
        // (four of border, fourteen of box, four of border).
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.LastLineBottom),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    companion object {
        /** The cell: the font's box across, eleven drawing pixels, and a frame piece's height. */
        const val CELL = 11

        /** A line of text's pitch: the font's fourteen rows and one of air (cartouche-font). */
        const val LINE = 15

        /** From the top of a line's box to its baseline: accents and capitals, twelve rows. */
        const val ASCENT = 12

        /** The em: 1024 units at 64 per drawing pixel. */
        const val EM = 16

        /** A frame's border, in drawing pixels: the font's pieces are cut to it. */
        const val BORDER = 4

        /** An icon's box, the register's second size: two cells. */
        const val ICON = 22
    }
}

/**
 * The grid of this screen: the density rounded, which puts a capital nearest 10 dp, moved by the
 * size steps the user chose. At least one pixel per drawing pixel.
 */
@Composable
fun retroGrid(): RetroGrid {
    val density = LocalDensity.current
    val step = CurrentTheme.sizeStep
    return remember(density, step) {
        RetroGrid((density.density.roundToInt() + step).coerceAtLeast(1), density)
    }
}

/** Cartouche's two weights, embedded (res/font, checked against the font's project). */
val Cartouche = FontFamily(
    Font(R.font.cartouche_thin, FontWeight.Thin),
    Font(R.font.cartouche_regular, FontWeight.Normal),
)

/**
 * Where something is drawn: on the screen's ground, or inside a frame, whose panel has its own
 * ground and inks. A frame provides [PANEL] to its content; outside any frame one is on the screen.
 */
enum class RetroSurface { SCREEN, PANEL }

val LocalRetroSurface = compositionLocalOf { RetroSurface.SCREEN }

/** The current palette's colours. */
val retroColors: RetroColors
    @Composable @ReadOnlyComposable get() = RetroPalettes.colors(CurrentTheme.currentPaletteId)

/** The colours of the surface one is drawing on. */
val retroSurface: Surface
    @Composable @ReadOnlyComposable get() = retroColors.let {
        if (LocalRetroSurface.current == RetroSurface.PANEL) it.panel else it.screen
    }

