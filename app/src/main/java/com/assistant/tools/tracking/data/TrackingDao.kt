package com.assistant.tools.tracking.data

import androidx.room.*
import com.assistant.tools.tracking.entities.TrackingData

@Dao
interface TrackingDao {
    @Query("SELECT * FROM tracking_data WHERE tool_instance_id = :instanceId ORDER BY recorded_at DESC")
    suspend fun getEntriesByInstance(instanceId: String): List<TrackingData>

    @Query("SELECT * FROM tracking_data WHERE tool_instance_id = :instanceId AND recorded_at BETWEEN :startTime AND :endTime ORDER BY recorded_at DESC")
    suspend fun getEntriesByDateRange(instanceId: String, startTime: Long, endTime: Long): List<TrackingData>

    @Query("SELECT * FROM tracking_data WHERE id = :id")
    suspend fun getEntryById(id: String): TrackingData?

    @Insert
    suspend fun insertEntry(entry: TrackingData)

    @Update
    suspend fun updateEntry(entry: TrackingData)

    @Delete
    suspend fun deleteEntry(entry: TrackingData)

    @Query("DELETE FROM tracking_data WHERE id = :id")
    suspend fun deleteEntryById(id: String)
}