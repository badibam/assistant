package com.assistant.tools.tracking.data

import com.assistant.tools.tracking.entities.TrackingData
import kotlinx.coroutines.flow.Flow

/**
 * Repository for tracking data operations
 * Handles CRUD operations for tracking entries
 */
class TrackingRepository(private val dao: TrackingDao) {
    
    /**
     * Get all entries for a specific tool instance
     */
    fun getEntries(instanceId: String): Flow<List<TrackingData>> {
        return dao.getEntriesByInstance(instanceId)
    }
    
    /**
     * Get entries within a date range
     */
    fun getEntriesByDateRange(instanceId: String, startTime: Long, endTime: Long): Flow<List<TrackingData>> {
        return dao.getEntriesByDateRange(instanceId, startTime, endTime)
    }
    
    /**
     * Get a specific entry by ID
     */
    suspend fun getEntry(id: String): TrackingData? {
        return dao.getEntryById(id)
    }
    
    /**
     * Add a new tracking entry
     */
    suspend fun addEntry(entry: TrackingData) {
        dao.insertEntry(entry)
    }
    
    /**
     * Update an existing entry
     */
    suspend fun updateEntry(entry: TrackingData) {
        dao.updateEntry(entry.copy(updated_at = System.currentTimeMillis()))
    }
    
    /**
     * Delete an entry
     */
    suspend fun deleteEntry(id: String) {
        dao.deleteEntryById(id)
    }
}