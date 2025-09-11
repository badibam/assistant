package com.assistant.core.database.dao

import com.assistant.core.database.entities.ToolDataEntity

/**
 * Interface pour DAOs étendus qui combinent les opérations de base
 * avec des méthodes de convenance communes à tous les tooltypes
 */
interface ExtendedToolDataDao {
    
    // Common methods (delegation to BaseToolDataDao)
    suspend fun insert(entity: ToolDataEntity)
    suspend fun update(entity: ToolDataEntity)
    suspend fun getByToolInstance(toolInstanceId: String): List<ToolDataEntity>
    suspend fun getById(id: String): ToolDataEntity?
    suspend fun deleteById(id: String)
    suspend fun deleteByToolInstance(toolInstanceId: String)
    suspend fun countByToolInstance(toolInstanceId: String): Int
    suspend fun getRecent(toolInstanceId: String, limit: Int): List<ToolDataEntity>
    suspend fun getByTooltype(tooltype: String): List<ToolDataEntity>
    suspend fun getTooltypeMinVersions(): Map<String, Int>
    
    // Generic convenience methods
    suspend fun getLatest(toolInstanceId: String): ToolDataEntity?
    suspend fun getByDateRange(toolInstanceId: String, startTime: Long, endTime: Long): List<ToolDataEntity>
    suspend fun getByItemName(toolInstanceId: String, itemName: String): List<ToolDataEntity>
}