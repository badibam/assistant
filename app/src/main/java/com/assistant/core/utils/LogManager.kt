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
     * When this limit is exceeded, oldest logs are purged
     */
    private const val MAX_LOG_COUNT = 1000

    /**
     * Check purge every N insertions (probabilistic to reduce DB queries)
     * 1 in 10 chance = check ~10% of the time
     */
    private const val PURGE_CHECK_PROBABILITY = 10

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
     * - Inserts log to database
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
                val logEntry = LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = level.uppercase(),
                    tag = tag,
                    message = message,
                    throwableMessage = throwable?.stackTraceToString()
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
     * Purge old logs if count exceeds MAX_LOG_COUNT
     * Keeps only the MAX_LOG_COUNT most recent logs
     */
    private suspend fun purgeOldLogsIfNeeded(database: AppDatabase) {
        try {
            val count = database.logDao().getLogCount()
            if (count > MAX_LOG_COUNT) {
                // Calculate cutoff timestamp
                // Keep MAX_LOG_COUNT most recent, delete older ones
                val logsToKeep = database.logDao().getAllLogs().take(MAX_LOG_COUNT)
                if (logsToKeep.isNotEmpty()) {
                    val oldestToKeep = logsToKeep.last()
                    database.logDao().deleteLogsOlderThan(oldestToKeep.timestamp)
                    println("LogManager: Purged old logs. Kept $MAX_LOG_COUNT most recent logs.")
                }
            }
        } catch (e: Exception) {
            // Silent failure
            println("LogManager: Failed to purge old logs: ${e.message}")
        }
    }
}