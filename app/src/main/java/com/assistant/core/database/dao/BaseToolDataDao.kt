package com.assistant.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.assistant.core.database.entities.ToolDataEntity


/**
 * Base DAO for common operations on tool_data
 * Specialized DAOs inherit from this class
 */
@Dao
abstract class BaseToolDataDao {

    /**
     * Inserts new entry into tool_data
     */
    @Insert
    abstract suspend fun insert(entity: ToolDataEntity)

    /**
     * Updates existing entry
     */
    @Update
    abstract suspend fun update(entity: ToolDataEntity)

    /**
     * Retrieves all entries for a tool instance
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId ORDER BY timestamp DESC")
    abstract suspend fun getByToolInstance(toolInstanceId: String): List<ToolDataEntity>

    /**
     * Retrieves entry by its ID
     */
    @Query("SELECT * FROM tool_data WHERE id = :id")
    abstract suspend fun getById(id: String): ToolDataEntity?

    /**
     * Deletes entry by its ID
     */
    @Query("DELETE FROM tool_data WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    /**
     * Deletes all entries for a tool instance
     */
    @Query("DELETE FROM tool_data WHERE tool_instance_id = :toolInstanceId")
    abstract suspend fun deleteByToolInstance(toolInstanceId: String)

    /**
     * Counts entries for a tool instance
     */
    @Query("SELECT COUNT(*) FROM tool_data WHERE tool_instance_id = :toolInstanceId")
    abstract suspend fun countByToolInstance(toolInstanceId: String): Int

    /**
     * Retrieves most recent entries
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId ORDER BY timestamp DESC LIMIT :limit")
    abstract suspend fun getRecent(toolInstanceId: String, limit: Int): List<ToolDataEntity>

    /**
     * Retrieves all entries for specific tool type
     */
    @Query("SELECT * FROM tool_data WHERE tooltype = :tooltype ORDER BY timestamp DESC")
    abstract suspend fun getByTooltype(tooltype: String): List<ToolDataEntity>

    /**
     * Retrieves entries in time range with limit and offset (pagination)
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getByTimeRangePaginated(toolInstanceId: String, startTime: Long, endTime: Long, limit: Int, offset: Int): List<ToolDataEntity>
    
    /**
     * Counts entries in time range
     */
    @Query("SELECT COUNT(*) FROM tool_data WHERE tool_instance_id = :toolInstanceId AND timestamp >= :startTime AND timestamp < :endTime")
    abstract suspend fun countByTimeRange(toolInstanceId: String, startTime: Long, endTime: Long): Int
    
    /**
     * Retrieves entries with pagination (all periods)
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getByToolInstancePaginated(toolInstanceId: String, limit: Int, offset: Int): List<ToolDataEntity>

    /**
     * Retrieves all entries
     * WARNING: Can be heavy, use sparingly
     */
    @Query("SELECT * FROM tool_data ORDER BY timestamp DESC")
    abstract suspend fun getAllEntries(): List<ToolDataEntity>

    /**
     * Retrieves entries whose data carries a given status, within a time range.
     *
     * Only meaningful for tooltypes whose data schema defines a "status" field — the
     * occurrence-based ones (Messages, and the future Calcul, Alertes, Objectifs), whose
     * entries have a lifecycle rather than being written once and left alone.
     *
     * Exists because time is otherwise the only query axis on tool_data, and a scheduler
     * that can only ask "what falls in this window" cannot find a pending occurrence left
     * behind by a gap longer than the window. Narrowing by status removes the window.
     *
     * Ordered ascending: a scheduler processes what is due oldest first.
     * Uses SQLite's json_extract (JSON1, available since API 24; the project targets 26).
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId AND json_extract(state, '$.status') = :status AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    abstract suspend fun getByStatusAndTimeRangePaginated(toolInstanceId: String, status: String, startTime: Long, endTime: Long, limit: Int, offset: Int): List<ToolDataEntity>

    /**
     * Counts entries matching a status within a time range.
     * Counting in SQL rather than loading rows to call .size on them.
     */
    @Query("SELECT COUNT(*) FROM tool_data WHERE tool_instance_id = :toolInstanceId AND json_extract(state, '$.status') = :status AND timestamp >= :startTime AND timestamp < :endTime")
    abstract suspend fun countByStatusAndTimeRange(toolInstanceId: String, status: String, startTime: Long, endTime: Long): Int

}