package app.treelune.core.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.LogManager

/**
 * The player of the interface sounds: the signal a component sends, the sound the current theme
 * answers it with, played when the app's setting allows (AppSettings.UI_SOUNDS).
 *
 * One track only: a sound cuts the one still playing, so that quick touches never pile up.
 * The theme's sounds are loaded ahead (UISoundsLoader, at the app's root), as one loaded at its
 * first touch would play nothing then.
 */
object UISounds {

    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build()
    }

    /** The sounds loaded, by the theme's raw resource. */
    private val loaded = mutableMapOf<Int, Int>()

    /** Loads every sound of the current theme not loaded yet. */
    fun load(context: Context) {
        UISignal.entries.mapNotNull { CurrentTheme.current.sound(it) }.distinct().filter { it !in loaded }
            .forEach { loaded[it] = pool.load(context.applicationContext, it, 1) }
    }

    /** Plays the current theme's sound for [signal], if it has one and sounds are on. */
    fun play(signal: UISignal) {
        if (!AppConfigManager.getUISounds()) return
        val resource = CurrentTheme.current.sound(signal) ?: return
        val sound = loaded[resource]
        if (sound == null) {
            LogManager.ui("UISounds: the sound for $signal was never loaded", "ERROR")
            return
        }
        pool.play(sound, 1f, 1f, 1, 0, 1f)
    }
}

/** Loads the current theme's sounds, again whenever the theme changes. Placed once, at the app's root. */
@Composable
fun UISoundsLoader() {
    val context = LocalContext.current
    val theme = CurrentTheme.current
    LaunchedEffect(theme) { UISounds.load(context) }
}

/** What a composable sends a signal with. */
@Composable
fun rememberUISound(): (UISignal) -> Unit = remember { { signal: UISignal -> UISounds.play(signal) } }
