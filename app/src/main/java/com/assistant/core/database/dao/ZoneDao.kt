package com.assistant.core.database.dao

import androidx.room.*
import com.assistant.core.database.entities.Zone
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY order_index ASC")
    fun getAllZones(): Flow<List<Zone>>

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