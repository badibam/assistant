package app.treelune.core.database.dao

import androidx.room.*
import app.treelune.core.database.entities.ToolInstance

@Dao
interface ToolInstanceDao {
    @Query("SELECT * FROM tool_instances WHERE zone_id = :zoneId ORDER BY grid_y ASC, grid_x ASC")
    suspend fun getToolInstancesByZone(zoneId: String): List<ToolInstance>

    @Query("SELECT * FROM tool_instances ORDER BY zone_id ASC, grid_y ASC, grid_x ASC")
    suspend fun getAllToolInstances(): List<ToolInstance>

    @Query("SELECT * FROM tool_instances WHERE id = :id")
    suspend fun getToolInstanceById(id: String): ToolInstance?

    @Insert
    suspend fun insertToolInstance(toolInstance: ToolInstance)

    @Update
    suspend fun updateToolInstance(toolInstance: ToolInstance)

    @Query("UPDATE tool_instances SET grid_x = :gridX, grid_y = :gridY WHERE id = :id")
    suspend fun updatePosition(id: String, gridX: Int, gridY: Int)

    @Delete
    suspend fun deleteToolInstance(toolInstance: ToolInstance)

    @Query("DELETE FROM tool_instances WHERE id = :id")
    suspend fun deleteToolInstanceById(id: String)
}