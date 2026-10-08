package app.treelune.tools.sequence

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import app.treelune.R
import app.treelune.core.utils.LogManager

/**
 * The session's signals (docs/design/sequence-tool.md, « Les signaux »): three marimba sounds
 * (scripts/make_sequence_sounds.py), a vibration, and the voice of Android's speech synthesis.
 *
 * The sounds go through the media channel, the music lowered a moment while they play. Silent
 * mode is kept: the phone set to silence or vibration plays no sound and says nothing, the
 * vibration stays.
 */
class SequenceSounds(private val context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
    private val countdown = pool.load(context, R.raw.sequence_countdown, 1)
    private val stepChange = pool.load(context, R.raw.sequence_step_change, 1)
    private val edge = pool.load(context, R.raw.sequence_session_edge, 1)

    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
    private val main = Handler(Looper.getMainLooper())

    private var speech: TextToSpeech? = null
    private var speechReady = false

    fun beep() = play(countdown, BEEP_LENGTH)
    fun stepChange() = play(stepChange, STEP_CHANGE_LENGTH)
    fun edge() = play(edge, EDGE_LENGTH)

    fun vibrate() {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(VIBRATION, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Says [text] after the sound of the step change, the synthesis made ready at its first use. */
    fun say(text: String) {
        if (!soundAllowed()) return
        if (speech == null) {
            speech = TextToSpeech(context) { status ->
                speechReady = status == TextToSpeech.SUCCESS
                if (!speechReady) LogManager.service("SequenceSounds: speech synthesis unavailable ($status)", "WARN")
                else main.postDelayed({ speak(text) }, VOICE_DELAY)
            }
        } else if (speechReady) {
            main.postDelayed({ speak(text) }, VOICE_DELAY)
        }
    }

    private fun speak(text: String) {
        val tts = speech ?: return
        tts.setAudioAttributes(attributes)
        audio.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sequence")
        main.postDelayed({ audio.abandonAudioFocusRequest(focus) }, SPEECH_FOCUS)
    }

    private fun play(sound: Int, length: Long) {
        if (!soundAllowed()) return
        audio.requestAudioFocus(focus)
        pool.play(sound, 1f, 1f, 1, 0, 1f)
        main.postDelayed({ audio.abandonAudioFocusRequest(focus) }, length)
    }

    private fun soundAllowed(): Boolean = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL

    fun release() {
        main.removeCallbacksAndMessages(null)
        audio.abandonAudioFocusRequest(focus)
        pool.release()
        speech?.shutdown()
    }

    private companion object {
        const val BEEP_LENGTH = 300L
        const val STEP_CHANGE_LENGTH = 1_600L
        const val EDGE_LENGTH = 3_600L
        const val VIBRATION = 250L
        /** The voice waits for the step change's notes. */
        const val VOICE_DELAY = 900L
        /** The music stays lowered this long for a sentence. */
        const val SPEECH_FOCUS = 5_000L
    }
}
