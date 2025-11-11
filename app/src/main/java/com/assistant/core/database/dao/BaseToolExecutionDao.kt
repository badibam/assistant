package com.assistant.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.assistant.core.database.entities.ToolExecutionEntity

/**
 * Base DAO for common operations on tool_executions
 * Provides queries for execution history across all tooltypes
 */
@Dao
abstract class BaseToolExecutionDao {

    /**
     * Inserts new execution into tool_executions
     */
    @Insert
    abstract suspend fun insert(execution: ToolExecutionEntity)

    /**
     * Updates existing execution
     */
    @Update
    abstract suspend fun update(execution: ToolExecutionEntity)

    /**
     * Retrieves execution by its ID
     */
    @Query("SELECT * FROM tool_executions WHERE id = :id")
    abstract suspend fun getById(id: String): ToolExecutionEntity?

    /**
     * Deletes execution by its ID
     */
    @Query("DELETE FROM tool_executions WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    /**
     * Retrieves all executions for a tool instance
     */
    @Query("SELECT * FROM tool_executions WHERE tool_instance_id = :toolInstanceId ORDER BY execution_time DESC")
    abstract suspend fun getByToolInstance(toolInstanceId: String): List<ToolExecutionEntity>

    /**
     * Retrieves all executions for a specific template
     */
    @Query("SELECT * FROM tool_executions WHERE template_data_id = :templateDataId ORDER BY execution_time DESC")
    abstract suspend fun getByTemplate(templateDataId: String): List<ToolExecutionEntity>

    /**
     * Retrieves executions in time range with pagination
     */
    @Query("SELECT * FROM tool_executions WHERE tool_instance_id = :toolInstanceId AND execution_time >= :startTime AND execution_time < :endTime ORDER BY execution_time DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getByTimeRangePaginated(
        toolInstanceId: String,
        startTime: Long,
        endTime: Long,
        limit: Int,
        offset: Int
    ): List<ToolExecutionEntity>

    /**
     * Counts executions in time range
     */
    @Query("SELECT COUNT(*) FROM tool_executions WHERE tool_instance_id = :toolInstanceId AND execution_time >= :startTime AND execution_time < :endTime")
    abstract suspend fun countByTimeRange(
        toolInstanceId: String,
        startTime: Long,
        endTime: Long
    ): Int

    /**
     * Retrieves executions by status
     */
    @Query("SELECT * FROM tool_executions WHERE tool_instance_id = :toolInstanceId AND status = :status ORDER BY execution_time DESC")
    abstract suspend fun getByStatus(toolInstanceId: String, status: String): List<ToolExecutionEntity>

    /**
     * Retrieves executions with pagination (all periods)
     */
    @Query("SELECT * FROM tool_executions WHERE tool_instance_id = :toolInstanceId ORDER BY execution_time DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getByToolInstancePaginated(
        toolInstanceId: String,
        limit: Int,
        offset: Int
    ): List<ToolExecutionEntity>

    /**
     * Counts total executions for a tool instance
     */
    @Query("SELECT COUNT(*) FROM tool_executions WHERE tool_instance_id = :toolInstanceId")
    abstract suspend fun countByToolInstance(toolInstanceId: String): Int

    /**
     * Retrieves most recent executions
     */
    @Query("SELECT * FROM tool_executions WHERE tool_instance_id = :toolInstanceId ORDER BY execution_time DESC LIMIT :limit")
    abstract suspend fun getRecent(toolInstanceId: String, limit: Int): List<ToolExecutionEntity>

    /**
     * Deletes all executions for a tool instance
     */
    @Query("DELETE FROM tool_executions WHERE tool_instance_id = :toolInstanceId")
    abstract suspend fun deleteByToolInstance(toolInstanceId: String)

    /**
     * Retrieves all executions for specific tooltype
     */
    @Query("SELECT * FROM tool_executions WHERE tooltype = :tooltype ORDER BY execution_time DESC")
    abstract suspend fun getByTooltype(tooltype: String): List<ToolExecutionEntity>

    /**
     * Retrieves pending executions (status = 'pending')
     */
    @Query("SELECT * FROM tool_executions WHERE status = 'pending' ORDER BY scheduled_time ASC")
    abstract suspend fun getPendingExecutions(): List<ToolExecutionEntity>

    /**
     * Retrieves all executions (for backup export)
     */
    @Query("SELECT * FROM tool_executions ORDER BY execution_time DESC")
    abstract suspend fun getAllExecutions(): List<ToolExecutionEntity>
}
