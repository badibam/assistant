package app.treelune.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * What wakes the app to run the scheduler's tick when no screen is open, the phone asleep
 * included: an exact alarm allowed while idle, [INTERVAL] from now, set again each time it rings,
 * when the phone restarts and when the app is updated. The tick looks at what is due at that
 * moment, so nothing needs to be known in advance.
 *
 * Android lets an app's while-idle alarms ring at most about every 9 minutes when the phone
 * sleeps deeply: [INTERVAL] stays above.
 */
object SchedulerAlarm {

    const val INTERVAL = 10 * 60 * 1000L

    const val ACTION_TICK = "app.treelune.action.SCHEDULER_TICK"

    /** Sets the next ring [INTERVAL] from now, replacing the one set before. */
    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, SchedulerAlarmReceiver::class.java).setAction(ACTION_TICK)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + INTERVAL, pending)
        } catch (e: SecurityException) {
            // USE_EXACT_ALARM is granted at install from Android 13, SCHEDULE_EXACT_ALARM by
            // default before: a refusal means the phone took it away, and nothing wakes the app
            LogManager.service("SchedulerAlarm: exact alarm refused, the scheduler will not run in the background: ${e.message}", "ERROR", e)
        }
    }
}

/**
 * The alarm ringing, the phone restarted, or the app updated: the next ring is set first, so a
 * tick that fails does not end the chain, then the tick runs while the broadcast holds the phone
 * awake, for [TICK_LIMIT] at most.
 */
class SchedulerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        // The app schedules no job of its own: one an earlier version left would keep waking it
        // for a service it no longer has
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) context.getSystemService(JobScheduler::class.java).cancelAll()
        SchedulerAlarm.arm(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeout(TICK_LIMIT) { CoreScheduler.tick() }
            } catch (e: Exception) {
                LogManager.service("SchedulerAlarm: tick failed: ${e.message}", "ERROR", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val ACTIONS = setOf(SchedulerAlarm.ACTION_TICK, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)

        /** Below the minute Android gives a background broadcast before declaring it stuck */
        private const val TICK_LIMIT = 50_000L
    }
}
