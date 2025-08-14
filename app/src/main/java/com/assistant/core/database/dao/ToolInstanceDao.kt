package com.assistant.core.database.dao

import androidx.room.*
import com.assistant.core.database.entities.ToolInstance
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolInstanceDao {
    @Query("SELECT * FROM tool_instances WHERE zone_id = :zoneId ORDER BY order_index ASC")
    fun getToolInstancesByZone(zoneId: String): Flow<List<ToolInstance>>

    @Query("SELECT * FROM tool_instances WHERE id = :id")
    suspend fun getToolInstanceById(id: String): ToolInstance?

    @Insert
    suspend fun insertToolInstance(toolInstance: ToolInstance)

    @Update
    suspend fun updateToolInstance(toolInstance: ToolInstance)

    @Delete
    suspend fun deleteToolInstance(toolInstance: ToolInstance)

    @Query("DELETE FROM tool_instances WHERE id = :id")
    suspend fun deleteToolInstanceById(id: String)
}