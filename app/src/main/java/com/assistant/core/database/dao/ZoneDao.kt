package com.assistant.core.database.dao

import androidx.room.*
import com.assistant.core.database.entities.Zone
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY grid_y ASC, grid_x ASC")
    suspend fun getAllZones(): List<Zone>

    @Query("UPDATE zones SET grid_x = :gridX, grid_y = :gridY WHERE id = :id")
    suspend fun updatePosition(id: String, gridX: Int, gridY: Int)

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getZoneById(id: String): Zone?

    @Insert
    suspend fun insertZone(zone: Zone)

    @Update
    suspend fun updateZone(zone: Zone)

    @Delete
    suspend fun deleteZone(zone: Zone)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun deleteZoneById(id: String)
}