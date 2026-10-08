package app.treelune.tools.sequence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.treelune.MainActivity
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.icons.Icons
import app.treelune.core.strings.Strings
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * The session running (docs/design/sequence-tool.md, « Pendant la séance »): a foreground service
 * from start to end, screen on or off, which alone sounds the signals and passes a timed step at
 * its end. Its notification, which Android requires, shows the step and its time, with « Done »
 * and « Pause ».
 *
 * A wake lock keeps the processor on while the session runs, the screen staying off: without it,
 * the processor sleeps with the screen off and a signal comes late. It is released while paused,
 * when no signal is due.
 *
 * Everything it knows of the run it reads from the entry, after each gesture (SequenceLive) and at
 * each moment a signal is due: the app killed, nothing is lost but the service.
 */
class SequenceRunService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var entryId: String? = null
    private var toolInstanceId: String? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var wakeLock: PowerManager.WakeLock? = null
    private var signals: SequenceSounds? = null
    private var settings = Settings(JSONObject())

    /** What the tool's config says of its signals and its notification. */
    private class Settings(config: JSONObject) {
        val name: String = config.optString("name")
        val icon: String = config.optString("icon_name").ifEmpty { SequenceToolType.getDefaultIconName() }
        val stepSignal: String = config.optString(SequenceToolType.STEP_SIGNAL, SequenceToolType.StepSignal.SOUND_AND_VIBRATION)
        val countdown: Boolean = config.optBoolean(SequenceToolType.COUNTDOWN, true)
        val voice: Boolean = config.optBoolean(SequenceToolType.VOICE, false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUN -> {
                val id = intent.getStringExtra(EXTRA_ENTRY) ?: return START_NOT_STICKY
                startForegroundNow(notification(null))
                if (entryId != id || loop?.isActive != true) run(id)
            }
            ACTION_GESTURE -> {
                val id = entryId ?: return START_NOT_STICKY
                val operation = intent.getStringExtra(EXTRA_OPERATION) ?: return START_NOT_STICKY
                scope.launch {
                    val result = Coordinator(applicationContext).processUserAction("sequence.$operation", mapOf("tool_instance_id" to toolInstanceId, "id" to id))
                    if (!result.isSuccess) LogManager.service("SequenceRunService: $operation refused: ${result.error}", "WARN")
                }
            }
        }
        // Stopped by Android, it is not started again by itself: the screen offers to resume
        return START_NOT_STICKY
    }

    private fun startForegroundNow(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun run(id: String) {
        loop?.cancel()
        entryId = id
        SequenceLive.setRunning(id)
        loop = scope.launch {
            try {
                val entry = AppDatabase.getDatabase(applicationContext).toolDataDao().getById(id) ?: return@launch
                toolInstanceId = entry.toolInstanceId
                settings = Settings(configOf(entry.toolInstanceId))
                signals = signals ?: SequenceSounds(applicationContext)
                follow(id)
                // The end's sound plays out before the service, and its sounds, go
                delay(END_LINGER)
            } catch (e: Exception) {
                LogManager.service("SequenceRunService: the session $id stopped running: ${e.message}", "ERROR", e)
            } finally {
                SequenceLive.setRunning(null)
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * Follows the run until it ends: sounds each signal at its instant, passes each timer run
     * out, and sleeps until the next one or a gesture.
     */
    private suspend fun follow(id: String) {
        val coordinator = Coordinator(applicationContext)
        val dao = AppDatabase.getDatabase(applicationContext).toolDataDao()
        val gestures = scope.launch {
            SequenceLive.changes.collect { change -> if (change.id == id) { onGesture(change); wake.trySend(Unit) } }
        }
        try {
            var cursor = System.currentTimeMillis()
            var rung: Any? = null
            var begun: Any? = null
            var first = true
            while (true) {
                val run = dao.getById(id)?.let { SequenceService.runOf(it) } ?: break
                val now = System.currentTimeMillis()
                if (first && run.position == 0 && run.stepBegins(now) != null) signals?.edge()
                first = false
                // The beeps due since the last look; one come too late (a sleep) is no longer worth hearing
                if (settings.countdown && SequenceSignals.beeps(run, now).any { it in (cursor + 1)..now && now - it < STALE }) signals?.beep()
                // The first step begins once the lead-in is over
                val beginKey = run.startedAt to run.stepStart
                if (run.position == 0 && run.stepBegins(now) == null && !run.isPaused && begun != beginKey && run.elapsed(now) < STALE) {
                    begun = beginKey
                    stepSignal(run.current)
                }
                // A timer then by hand rings once at its end, and goes on in overtime
                val end = run.timerEnd()
                val ringKey = Triple(run.position, run.stepStart, run.extended)
                if (run.current.end == StepEnd.TIMED_THEN_MANUAL && end != null && end <= now && rung != ringKey) {
                    rung = ringKey
                    if (now - end < STALE) stepSignal(null)
                }
                // A timed step run out passes: the operation writes it, and its change sounds
                if (run.current.end == StepEnd.TIMED && end != null && end <= now) {
                    val passed = coordinator.process(Source.SYSTEM, "sequence.${SequenceService.ADVANCE}", mapOf("id" to id))
                    if (!passed.isSuccess) throw IllegalStateException(passed.error ?: "advance")
                    cursor = now
                    continue
                }
                cursor = now
                if (run.isPaused) releaseWakeLock() else acquireWakeLock()
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(run))
                val next = (SequenceSignals.beeps(run, now) + listOfNotNull(end, run.stepBegins(now))).filter { it > now }.minOrNull()
                val wait = next?.let { it - now } ?: IDLE_WAIT
                withTimeoutOrNull(wait.coerceIn(1L, IDLE_WAIT)) { wake.receive() }
            }
        } finally {
            gestures.cancel()
        }
    }

    /** The signal a gesture calls for: the next step's, or the end's. */
    private fun onGesture(change: SequenceLive.Change) {
        when (val step = change.step) {
            is Step.Finished -> signals?.edge()
            is Step.Running -> if (change.operation in setOf(SequenceService.DONE, SequenceService.SKIP, SequenceService.ADVANCE)) stepSignal(step.run.current)
        }
    }

    /** The change of step as the tool's settings say: sound, vibration, and the step [next] announced. */
    private fun stepSignal(next: PlannedStep?) {
        val sounds = signals ?: return
        when (settings.stepSignal) {
            SequenceToolType.StepSignal.SOUND_AND_VIBRATION -> { sounds.stepChange(); sounds.vibrate() }
            SequenceToolType.StepSignal.SOUND -> sounds.stepChange()
            SequenceToolType.StepSignal.VIBRATION -> sounds.vibrate()
        }
        if (settings.voice && next != null) sounds.say(listOfNotNull(next.name, next.instruction).joinToString(". "))
    }

    private suspend fun configOf(toolInstanceId: String): JSONObject {
        val tool = Coordinator(applicationContext).processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        return ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            ?: throw IllegalStateException(tool.error ?: "tools.get")
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        signals?.release()
        SequenceLive.setRunning(null)
        super.onDestroy()
    }

    /**
     * The notification of the session running: its step, its time counted by Android itself
     * (down to the timer's end, up from the step's start), « Done » and « Pause » or « Resume ».
     */
    private fun notification(run: SequenceRun?): Notification {
        val s = Strings.`for`(tool = "sequence", context = this)
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, s.tool("channel"), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            toolInstanceId?.let { putExtra(MainActivity.EXTRA_TOOL_INSTANCE_ID, it) }
        }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(Icons.lucideDrawable(this, settings.icon) ?: android.R.drawable.ic_media_play)
            .setContentTitle(settings.name.ifEmpty { s.tool("display_name") })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (run == null) return builder.setContentText(s.tool("notification_starting")).build()
        val now = System.currentTimeMillis()
        val step = run.current
        builder.setContentText(if (run.isPaused) s.tool("notification_paused").format(step.name) else step.name)
        if (!run.isPaused) {
            val begins = run.stepBegins(now)
            val end = run.timerEnd()
            when {
                begins != null -> builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(begins)
                end != null && end > now -> builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(end)
                else -> builder.setUsesChronometer(true).setWhen(now - run.elapsed(now))
            }
        }
        builder.addAction(0, s.tool("action_done"), gesture(SequenceService.DONE, 1))
        builder.addAction(0, s.tool(if (run.isPaused) "action_resume" else "action_pause"),
            gesture(if (run.isPaused) SequenceService.RESUME else SequenceService.PAUSE, 2))
        return builder.build()
    }

    private fun gesture(operation: String, requestCode: Int): PendingIntent = PendingIntent.getService(this, requestCode,
        Intent(this, SequenceRunService::class.java).setAction(ACTION_GESTURE).putExtra(EXTRA_OPERATION, operation),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val ACTION_RUN = "app.treelune.sequence.RUN"
        const val ACTION_GESTURE = "app.treelune.sequence.GESTURE"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_OPERATION = "operation"
        const val CHANNEL = "treelune_sequence"
        private const val NOTIFICATION_ID = 0x5345 // "SE"
        private const val WAKE_LOCK_TAG = "treelune:sequence"

        /** A signal more than this late is not sounded: the phone slept past it. */
        private const val STALE = 1_500L

        /** How long the service stays once the session is over, for the end's sound. */
        private const val END_LINGER = 4_000L

        /** The longest the service sleeps without a signal due: the notification is redrawn then. */
        private const val IDLE_WAIT = 60_000L
    }
}
