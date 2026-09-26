package com.assistant.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
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
     * Retrieves all entries
     * WARNING: Can be heavy, use sparingly
     */
    @Query("SELECT * FROM tool_data ORDER BY timestamp DESC")
    abstract suspend fun getAllEntries(): List<ToolDataEntity>

    /**
     * The entries a query built by EntryFilters.select returns: a tool's entries narrowed by value
     * filters, which a fixed query cannot state since the fields are the tool's own.
     */
    @RawQuery
    abstract suspend fun getFiltered(query: SupportSQLiteQuery): List<ToolDataEntity>

    /** The count a query built by EntryFilters.count returns. */
    @RawQuery
    abstract suspend fun countFiltered(query: SupportSQLiteQuery): Int

    /**
     * The entries of a tool with a DURATION field running (state.running present), whenever
     * they started: a stopwatch left running for days must still be found to be stopped.
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId AND json_extract(state, '$.running') IS NOT NULL ORDER BY timestamp DESC")
    abstract suspend fun getRunning(toolInstanceId: String): List<ToolDataEntity>
}
