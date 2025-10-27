package com.assistant.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.assistant.core.database.entities.LogEntry

/**
 * DAO for log entries
 *
 * Provides queries for:
 * - Inserting new log entries
 * - Fetching logs with filters (level, time range)
 * - Clearing old logs
 *
 * Logs are always ordered by timestamp DESC (newest first)
 */
@Dao
interface LogDao {

    /**
     * Insert a new log entry
     */
    @Insert
    suspend fun insertLog(log: LogEntry)

    /**
     * Get logs filtered by time range and optional tag pattern
     *
     * @param sinceTimestamp Minimum timestamp (logs newer than this)
     * @param tagPattern Tag pattern for LIKE query (e.g., "ai%" for all AI tags, "%" for all tags)
     * @return List of logs ordered by timestamp DESC (newest first)
     */
    @Query("""
        SELECT * FROM log_entries
        WHERE timestamp >= :sinceTimestamp
          AND LOWER(tag) LIKE LOWER(:tagPattern)
        ORDER BY timestamp DESC
    """)
    suspend fun getLogsFiltered(sinceTimestamp: Long, tagPattern: String): List<LogEntry>

    /**
     * Get all logs (no time filter)
     *
     * @return List of all logs ordered by timestamp DESC (newest first)
     */
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<LogEntry>

    /**
     * Delete logs older than specified timestamp
     * Useful for cleanup/retention policy
     *
     * @param olderThan Timestamp threshold (delete logs older than this)
     */
    @Query("DELETE FROM log_entries WHERE timestamp < :olderThan")
    suspend fun deleteLogsOlderThan(olderThan: Long)

    /**
     * Delete all logs
     * For testing or manual cleanup
     */
    @Query("DELETE FROM log_entries")
    suspend fun deleteAllLogs()

    /**
     * Count total logs
     */
    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun getLogCount(): Int
}
