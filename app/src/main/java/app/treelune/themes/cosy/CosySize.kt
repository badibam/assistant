package app.treelune.themes.cosy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.treelune.R
import app.treelune.core.themes.CurrentTheme

/**
 * The cosy theme's sizes: every length and every text of the mockup, times the factor of the size
 * step the user chose (CurrentTheme.sizeStep), so that the whole interface grows together and a
 * tile stays plump around a larger text. Texts are in sp, so Android's text size enlarges them on
 * top, for the text alone (docs/design/cosy-theme.md).
 */
@Immutable
class CosySize(val factor: Float) {

    /** [value] dp of the mockup, at this size. */
    fun dp(value: Float): Dp = (value * factor).dp

    /** The depth of the full shadow under a tile, and under a button. */
    val tileDepth: Dp get() = dp(5f)
    val buttonDepth: Dp get() = dp(4f)

    /** The rounding of a tile, a dialog, a field. */
    val tileRadius: Dp get() = dp(22f)
    val dialogRadius: Dp get() = dp(26f)
    val fieldRadius: Dp get() = dp(16f)

    /** A finger's target, Android's 48 dp at the first step, and the floating button's side. */
    val touch: Dp get() = dp(48f)
    val floating: Dp get() = dp(62f)

    /** An icon beside a text, and alone in a round button. */
    val icon: Dp get() = dp(22f)

    val body: TextStyle = style(16f, 22f, FontWeight.Normal)
    val strong: TextStyle = style(16f, 22f, FontWeight.Bold)
    val caption: TextStyle = style(13f, 18f, FontWeight.Medium)
    val label: TextStyle = style(13f, 18f, FontWeight.Bold)
    val button: TextStyle = style(16f, 20f, FontWeight.Bold)
    val heading: TextStyle = style(18f, 22f, FontWeight.ExtraBold)
    val subtitle: TextStyle = style(20f, 26f, FontWeight.Bold)
    val title: TextStyle = style(24f, 30f, FontWeight.ExtraBold)
    val number: TextStyle = style(24f, 28f, FontWeight.ExtraBold)

    private fun style(size: Float, line: Float, weight: FontWeight) = TextStyle(
        fontFamily = Baloo2,
        fontWeight = weight,
        fontSize = (size * factor).sp,
        lineHeight = (line * factor).sp,
        // Baloo's tall ascent would push a single line off the centre of a pill: the line's air
        // is shared above and under it, and the font's own padding left out
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    companion object {
        /** The factor of each size step, 0 to 3: the mockup, then larger by about an eighth each. */
        val FACTORS = floatArrayOf(1f, 1.12f, 1.25f, 1.4f)
    }
}

/** The size of the step the user chose. */
@Composable
fun cosySize(): CosySize {
    val step = CurrentTheme.sizeStep
    return remember(step) { CosySize(CosySize.FACTORS[step.coerceIn(0, CosySize.FACTORS.lastIndex)]) }
}

/** Baloo 2's five weights, embedded (res/font, made by scripts/make_baloo_fonts.py). */
val Baloo2 = FontFamily(
    Font(R.font.baloo2_regular, FontWeight.Normal),
    Font(R.font.baloo2_medium, FontWeight.Medium),
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold),
)

/** The current palette's colours. */
val cosyColors: CosyColors
    @Composable @ReadOnlyComposable get() = CosyPalettes.colors(CurrentTheme.paletteMode, CurrentTheme.hueShift)

/**
 * What a piece is drawn on: the screen's ground, or a tile (a card, a dialog, a message). An input
 * is a well in what holds it, so it takes the other one's colour.
 */
enum class CosyLayer { GROUND, TILE }

val LocalCosyLayer = compositionLocalOf { CosyLayer.GROUND }

/** An ink a container imposes on what it holds (a main button's, a disabled one's). */
internal val LocalCosyInk = compositionLocalOf<Color?> { null }

/** Whether the tile drawn is the one lifted from its grid in edit mode: its shadow goes deeper. */
internal val LocalCosyLifted = compositionLocalOf { false }
