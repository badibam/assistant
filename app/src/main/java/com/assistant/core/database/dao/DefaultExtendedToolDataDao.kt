package com.assistant.core.database.dao

import com.assistant.core.database.entities.ToolDataEntity

/**
 * Implémentation par défaut d'ExtendedToolDataDao
 * Fournit les méthodes communes + méthodes de convenance pour tous les tooltypes
 */
class DefaultExtendedToolDataDao(
    private val baseDao: BaseToolDataDao,
    private val tooltype: String
) : ExtendedToolDataDao {
    
    // === Délégation vers BaseToolDataDao ===
    
    override suspend fun insert(entity: ToolDataEntity) = baseDao.insert(entity)
    
    override suspend fun update(entity: ToolDataEntity) = baseDao.update(entity)
    
    override suspend fun getByToolInstance(toolInstanceId: String): List<ToolDataEntity> = 
        baseDao.getByToolInstance(toolInstanceId)
    
    override suspend fun getById(id: String): ToolDataEntity? = 
        baseDao.getById(id)
    
    override suspend fun deleteById(id: String) = 
        baseDao.deleteById(id)
    
    override suspend fun deleteByToolInstance(toolInstanceId: String) = 
        baseDao.deleteByToolInstance(toolInstanceId)
    
    override suspend fun countByToolInstance(toolInstanceId: String): Int = 
        baseDao.countByToolInstance(toolInstanceId)
    
    override suspend fun getRecent(toolInstanceId: String, limit: Int): List<ToolDataEntity> = 
        baseDao.getRecent(toolInstanceId, limit)
    
    override suspend fun getByTooltype(tooltype: String): List<ToolDataEntity> = 
        baseDao.getByTooltype(tooltype)
    
    override suspend fun getTooltypeMinVersions(): Map<String, Int> = 
        baseDao.getTooltypeMinVersions()
    
    // === Méthodes de convenance génériques ===
    
    override suspend fun getLatest(toolInstanceId: String): ToolDataEntity? {
        return getByToolInstance(toolInstanceId).firstOrNull()
    }
    
    override suspend fun getByDateRange(toolInstanceId: String, startTime: Long, endTime: Long): List<ToolDataEntity> {
        return getByToolInstance(toolInstanceId).filter { 
            it.timestamp?.let { ts -> ts in startTime..endTime } ?: false 
        }
    }
    
    override suspend fun getByItemName(toolInstanceId: String, itemName: String): List<ToolDataEntity> {
        return getByToolInstance(toolInstanceId).filter { it.name == itemName }
    }
}