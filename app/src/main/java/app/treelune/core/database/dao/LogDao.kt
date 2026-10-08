package app.treelune.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.treelune.core.database.entities.LogEntry

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
          AND level IN (:levels)
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getLogsFiltered(
        sinceTimestamp: Long,
        tagPattern: String,
        levels: List<String>,
        limit: Int
    ): List<LogEntry>

    /**
     * Delete all logs
     * For testing or manual cleanup
     */

    /**
     * Count the logs of the given levels.
     *
     * The chatty levels and the ones worth keeping are purged against separate ceilings, so a
     * busy minute of DEBUG cannot push yesterday's error out of the table.
     */
    @Query("SELECT COUNT(*) FROM log_entries WHERE level IN (:levels)")
    suspend fun getLogCountForLevels(levels: List<String>): Int

    /**
     * Timestamp of the nth most recent log of the given levels, used as a purge cutoff.
     */
    @Query("""
        SELECT timestamp FROM log_entries
        WHERE level IN (:levels)
        ORDER BY timestamp DESC
        LIMIT 1 OFFSET :offset
    """)
    suspend fun getTimestampAtOffsetForLevels(levels: List<String>, offset: Int): Long?

    @Query("DELETE FROM log_entries WHERE level IN (:levels) AND timestamp < :olderThan")
    suspend fun deleteLogsOlderThanForLevels(levels: List<String>, olderThan: Long)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAllLogs()
}
