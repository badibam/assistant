package com.assistant.core.utils

import android.content.Context
import android.util.Log
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.LogEntry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
     * Maximum number of logs to keep in database
     * Reduced to prevent CursorWindow overflow and memory saturation
     * When this limit is exceeded, oldest logs are purged
     */
    private const val MAX_LOG_COUNT = 300

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
     * Purge threshold - start purging when we reach this many logs
     * Set lower than MAX_LOG_COUNT to delete in batches efficiently
     */
    private const val PURGE_THRESHOLD = 250

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
        safeLog("Service", message, level, throwable)
    }

    fun aiEnrichment(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIEnrichment", message, level, throwable)
    }

    private fun safeLog(tag: String, message: String, level: String, throwable: Throwable?) {
        try {
            // Replace simple escaped characters, but keep double escapes for debug
            // Strategy: protect double escapes, replace simple escapes, restore double escapes
            val readableMessage = message
                .replace("\\\\n", "\uE000")     // Protect \\n (double escape) with placeholder
                .replace("\\\\\"", "\uE001")    // Protect \\\" (double escape) with placeholder
                .replace("\\n", "\n")           // Replace \n (simple escape) with real newline
                .replace("\\\"", "\"")          // Replace \" (simple escape) with real quote
                .replace("\uE000", "\\\\n")     // Restore \\n (double escape)
                .replace("\uE001", "\\\\\"")    // Restore \\\" (double escape)

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
            val readableMessage = message
                .replace("\\\\n", "\uE000")
                .replace("\\\\\"", "\uE001")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\uE000", "\\\\n")
                .replace("\uE001", "\\\\\"")
            println("LogManager fallback - $tag: $readableMessage")
            throwable?.let { println("Exception: ${it.message}") }
        }
    }

    /**
     * Persist log entry to database with automatic purge
     * Non-blocking (uses GlobalScope for fire-and-forget)
     *
     * Features:
     * - Inserts log to database with size limits to prevent overflow
     * - Truncates messages and stack traces to prevent CursorWindow overflow
     * - Probabilistic purge check (1 in N chance) to limit DB queries
     * - Keeps only MAX_LOG_COUNT most recent logs
     *
     * Note: GlobalScope is appropriate here because logs are:
     * - Fire-and-forget operations
     * - Not tied to any specific lifecycle
     * - Should persist even if activity is destroyed
     */
    private fun persistToDatabase(tag: String, message: String, level: String, throwable: Throwable?) {
        val ctx = context ?: return  // Not initialized yet, skip persistence

        GlobalScope.launch {
            try {
                val database = AppDatabase.getDatabase(ctx)

                // Truncate message if too long to prevent CursorWindow overflow
                val truncatedMessage = if (message.length > MAX_MESSAGE_LENGTH) {
                    message.take(MAX_MESSAGE_LENGTH) + "\n[... truncated ${message.length - MAX_MESSAGE_LENGTH} chars]"
                } else {
                    message
                }

                // Truncate throwable stack trace if too long
                val throwableStr = throwable?.stackTraceToString()
                val truncatedThrowable = if (throwableStr != null && throwableStr.length > MAX_THROWABLE_LENGTH) {
                    throwableStr.take(MAX_THROWABLE_LENGTH) + "\n[... truncated ${throwableStr.length - MAX_THROWABLE_LENGTH} chars]"
                } else {
                    throwableStr
                }

                val logEntry = LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = level.uppercase(),
                    tag = tag,
                    message = truncatedMessage,
                    throwableMessage = truncatedThrowable
                )
                database.logDao().insertLog(logEntry)

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

    /**
     * Purge old logs if count exceeds threshold
     * Uses efficient SQL query to find cutoff timestamp without loading all logs
     * This prevents CursorWindow overflow when there are many large log entries
     */
    private suspend fun purgeOldLogsIfNeeded(database: AppDatabase) {
        try {
            val count = database.logDao().getLogCount()
            if (count > PURGE_THRESHOLD) {
                // Find the timestamp of the PURGE_THRESHOLD-th most recent log
                // This is our cutoff point - delete everything older
                // Uses OFFSET query to avoid loading all logs into memory
                val cutoffTimestamp = database.logDao().getTimestampAtOffset(PURGE_THRESHOLD - 1)

                if (cutoffTimestamp != null) {
                    // Delete all logs older than the cutoff
                    database.logDao().deleteLogsOlderThan(cutoffTimestamp)
                    val remaining = database.logDao().getLogCount()
                    println("LogManager: Purged old logs. Deleted ${count - remaining} entries. Kept $remaining logs.")
                } else {
                    println("LogManager: Purge skipped - could not determine cutoff timestamp")
                }
            }
        } catch (e: Exception) {
            // Silent failure - don't log errors from logging system to avoid infinite loop
            println("LogManager: Failed to purge old logs: ${e.message}")
        }
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