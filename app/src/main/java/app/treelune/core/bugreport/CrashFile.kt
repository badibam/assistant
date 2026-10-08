package app.treelune.core.bugreport

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * The app's last crash, kept in a file that outlives it: the database and the log may be what
 * failed, and an app that crashes at every start never reaches a screen that would read them.
 *
 * Written raw by the handler, which must be as short as possible at that moment; cleaned when a
 * report reads it (ReportCleaner). The next crash replaces it. It stays in the reports until
 * then; "seen" only says the screen after a crash has been left (CrashNoticeScreen).
 */
object CrashFile {

    /** A crash as the file holds it. */
    data class Crash(val timestamp: Long, val versionName: String, val versionCode: Int, val stackTrace: String, val seen: Boolean)

    private const val FILE_NAME = "last_crash.json"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /**
     * Records every uncaught exception of the process in the file, then hands it to the
     * handler that was there, which ends the app as before. Installed first thing in the process.
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                write(appContext, System.currentTimeMillis(), e.stackTraceToString())
            } catch (_: Throwable) {
                // Nothing left to tell it to: the crash goes on as it would have
            }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun write(context: Context, timestamp: Long, stackTrace: String) {
        file(context).writeText(JSONObject()
            .put("timestamp", timestamp)
            .put("version_name", app.treelune.BuildConfig.VERSION_NAME)
            .put("version_code", app.treelune.BuildConfig.VERSION_CODE)
            .put("stack_trace", stackTrace)
            .put("seen", false)
            .toString())
    }

    /** The last crash, or null when there is none or its file cannot be read (logged). */
    fun read(context: Context): Crash? {
        val file = file(context)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            Crash(
                timestamp = json.getLong("timestamp"),
                versionName = json.getString("version_name"),
                versionCode = json.getInt("version_code"),
                stackTrace = json.getString("stack_trace"),
                seen = json.optBoolean("seen", false)
            )
        } catch (e: Exception) {
            app.treelune.core.utils.LogManager.service("Crash file unreadable: ${e.message}", "ERROR", e)
            null
        }
    }

    /** The last crash, when the screen after a crash has not been left since. */
    fun unseen(context: Context): Crash? = read(context)?.takeIf { !it.seen }

    /** Marks the last crash seen: the screen after a crash no longer opens for it. */
    fun markSeen(context: Context) {
        val file = file(context)
        if (!file.exists()) return
        try {
            file.writeText(JSONObject(file.readText()).put("seen", true).toString())
        } catch (e: Exception) {
            app.treelune.core.utils.LogManager.service("Crash file not marked seen: ${e.message}", "ERROR", e)
        }
    }
}
