package app.treelune.core.utils

import android.content.Context
import android.util.Log
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.LogEntry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Centralized logging manager
 *
 * Features:
 * - Console logging via Android Log (for development)
 * - Database persistence (for in-app logs screen)
 * - Automatic purge when log count exceeds limit
 * - Fallback to println() for tests
 *
 * Must call initialize() in MainActivity.onCreate() before using
 */
object LogManager {

    @Volatile
    private var context: Context? = null

    /**
     * The levels that make the noise, and how many of them are kept.
     *
     * They are counted and purged on their own, because they are what fills the table: a busy
     * two minutes wrote 230 DEBUG lines against 4 of everything else. Kept together with the
     * rest, that noise pushes an error out of the table within the minute, which is exactly
     * when an error is worth reading.
     */
    private val CHATTY_LEVELS = listOf("VERBOSE", "DEBUG")
    private const val MAX_CHATTY_LOGS = 10_000

    /**
     * The levels worth keeping, and how many.
     *
     * Far fewer arrive, so this budget holds a long stretch of use. Measured at the same
     * moment: 18.7 KB for 252 rows, so neither ceiling is what threatens the database.
     */
    private val KEPT_LEVELS = listOf("INFO", "WARN", "ERROR")
    private const val MAX_KEPT_LOGS = 2_000

    /**
     * Maximum message length (chars)
     * Prevents individual log entries from becoming too large
     */
    private const val MAX_MESSAGE_LENGTH = 3000

    /**
     * Maximum throwable message length (chars)
     * Stack traces can be very long, limit them to prevent DB bloat
     */
    private const val MAX_THROWABLE_LENGTH = 5000

    /**
     * The lines waiting to be written, at most [PENDING_CAPACITY], by one writer.
     *
     * While a long transaction holds the database (an import of 64 000 lines), no line can be
     * written: a task per line kept every one waiting with its text, until the app ran out of
     * memory. Past the capacity a line goes to the console only, and the number of those is
     * written once the database is free again ([dropped]).
     */
    private const val PENDING_CAPACITY = 2_000
    private val pending = Channel<LogEntry>(PENDING_CAPACITY)
    private val dropped = AtomicInteger(0)
    @Volatile
    private var writerStarted = false

    /**
     * Check purge every N insertions (probabilistic to reduce DB queries)
     * More aggressive than before (1 in 5 instead of 1 in 10)
     */
    private const val PURGE_CHECK_PROBABILITY = 5

    private var insertionCounter = 0

    /**
     * Initialize LogManager with application context
     * Must be called once in MainActivity.onCreate()
     *
     * @param appContext Application context (not activity context)
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
        startWriter(appContext.applicationContext)
    }

    /** The one task that writes the pending lines, started once whatever the activity recreations. */
    @Synchronized
    private fun startWriter(ctx: Context) {
        if (writerStarted) return
        writerStarted = true
        GlobalScope.launch {
            val database = AppDatabase.getDatabase(ctx)
            for (entry in pending) {
                try {
                    dropped.getAndSet(0).takeIf { it > 0 }?.let { lost ->
                        database.logDao().insertLog(LogEntry(
                            timestamp = System.currentTimeMillis(), level = "WARN", tag = "Log",
                            message = "$lost log lines not written: the database was held too long", throwableMessage = null
                        ))
                    }
                    database.logDao().insertLog(entry)

                    // Probabilistic purge check (1 in PURGE_CHECK_PROBABILITY chance)
                    // This avoids checking on every insertion, reducing DB load
                    insertionCounter++
                    if (insertionCounter % PURGE_CHECK_PROBABILITY == 0) {
                        purgeOldLogsIfNeeded(database)
                    }
                } catch (e: Exception) {
                    // Silent failure - don't log errors from logging system to avoid infinite loop
                    println("LogManager: Failed to persist log to database: ${e.message}")
                }
            }
        }
    }

    fun schema(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Schema", message, level, throwable)
    }

    fun coordination(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Coordination", message, level, throwable)
    }

    fun tracking(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Tracking", message, level, throwable)
    }

    fun database(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Database", message, level, throwable)
    }

    fun ui(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("UI", message, level, throwable)
    }

    fun service(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Service", message, level, throwable)
    }

    fun aiSession(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AISession", message, level, throwable)
    }

    fun aiPrompt(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIPrompt", message, level, throwable)
    }

    fun aiUI(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIUI", message, level, throwable)
    }

    fun aiService(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIService", message, level, throwable)
    }

    fun aiEnrichment(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIEnrichment", message, level, throwable)
    }

    private fun safeLog(tag: String, message: String, level: String, throwable: Throwable?) {
        // Cut first: every step below copies the message, and a message can hold a whole query
        // result. Neither logcat nor the logs table would keep more than this anyway.
        val readableMessage = unescape(truncate(message, MAX_MESSAGE_LENGTH))

        try {
            // Console logging (always)
            when (level.uppercase()) {
                "VERBOSE" -> Log.v(tag, readableMessage, throwable)
                "DEBUG" -> Log.d(tag, readableMessage, throwable)
                "INFO" -> Log.i(tag, readableMessage, throwable)
                "WARN" -> Log.w(tag, readableMessage, throwable)
                "ERROR" -> Log.e(tag, readableMessage, throwable)
                else -> Log.d(tag, readableMessage, throwable)
            }

            // Database persistence (if initialized)
            persistToDatabase(tag, readableMessage, level, throwable)

        } catch (e: Exception) {
            // Fallback for tests (no Android Log available)
            println("LogManager fallback - $tag: $readableMessage")
            throwable?.let { println("Exception: ${it.message}") }
        }
    }

    private fun truncate(text: String, max: Int): String =
        if (text.length > max) text.take(max) + "\n[... truncated ${text.length - max} chars]" else text

    /**
     * Replace simple escaped characters, but keep double escapes for debug.
     * Strategy: protect double escapes, replace simple escapes, restore double escapes.
     */
    private fun unescape(text: String): String = text
        .replace("\\\\n", "\uE000")     // Protect \\n (double escape) with placeholder
        .replace("\\\\\"", "\uE001")    // Protect \\\" (double escape) with placeholder
        .replace("\\n", "\n")           // Replace \n (simple escape) with real newline
        .replace("\\\"", "\"")          // Replace \" (simple escape) with real quote
        .replace("\uE000", "\\\\n")     // Restore \\n (double escape)
        .replace("\uE001", "\\\\\"")    // Restore \\\" (double escape)

    /**
     * Queue a log entry for the writer ([pending]); a full queue counts it as dropped.
     *
     * Logs are fire-and-forget: not tied to any screen's lifecycle, kept even if the activity is
     * destroyed. The stack trace is cut here, the message arrives already cut.
     */
    private fun persistToDatabase(tag: String, message: String, level: String, throwable: Throwable?) {
        if (context == null) return  // Not initialized yet, skip persistence
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level.uppercase(),
            tag = tag,
            message = message,
            throwableMessage = throwable?.stackTraceToString()?.let { truncate(it, MAX_THROWABLE_LENGTH) }
        )
        if (pending.trySend(entry).isFailure) dropped.incrementAndGet()
    }

    /**
     * Bring each class of log back under its own ceiling.
     *
     * The cutoff is found with an OFFSET query rather than by loading the rows, so the purge
     * costs the same whatever the table holds.
     */
    private suspend fun purgeOldLogsIfNeeded(database: AppDatabase) {
        try {
            purgeLevels(database, CHATTY_LEVELS, MAX_CHATTY_LOGS)
            purgeLevels(database, KEPT_LEVELS, MAX_KEPT_LOGS)
        } catch (e: Exception) {
            // Silent failure - don't log errors from logging system to avoid infinite loop
            println("LogManager: Failed to purge old logs: ${e.message}")
        }
    }

    private suspend fun purgeLevels(database: AppDatabase, levels: List<String>, ceiling: Int) {
        val count = database.logDao().getLogCountForLevels(levels)
        if (count <= ceiling) return

        val cutoffTimestamp = database.logDao().getTimestampAtOffsetForLevels(levels, ceiling - 1)
        if (cutoffTimestamp == null) {
            println("LogManager: Purge skipped for $levels - could not determine cutoff timestamp")
            return
        }

        database.logDao().deleteLogsOlderThanForLevels(levels, cutoffTimestamp)
        val remaining = database.logDao().getLogCountForLevels(levels)
        println("LogManager: Purged $levels. Deleted ${count - remaining} entries. Kept $remaining.")
    }

    /**
     * Manually purge old logs
     * Can be called from UI or at app startup
     * Public function to allow external cleanup
     */
    suspend fun manualPurge() {
        val ctx = context ?: return
        try {
            val database = AppDatabase.getDatabase(ctx)
            purgeOldLogsIfNeeded(database)
        } catch (e: Exception) {
            println("LogManager: Manual purge failed: ${e.message}")
        }
    }
}