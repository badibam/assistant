package com.assistant.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.assistant.core.database.entities.ToolDataEntity

/**
 * DAO spécialisé pour les données de tracking
 * Hérite des opérations de base et ajoute des requêtes spécifiques
 */
@Dao
abstract class TrackingDataDao : BaseToolDataDao() {

    /**
     * Récupère les entrées de tracking dans une plage de dates
     */
    @Query("""
        SELECT * FROM tool_data 
        WHERE tool_instance_id = :toolInstanceId 
        AND tooltype = 'tracking'
        AND timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp ASC
    """)
    abstract suspend fun getByDateRange(
        toolInstanceId: String, 
        startTime: Long, 
        endTime: Long
    ): List<ToolDataEntity>

    /**
     * Récupère les entrées de tracking pour une date spécifique
     */
    @Query("""
        SELECT * FROM tool_data 
        WHERE tool_instance_id = :toolInstanceId 
        AND tooltype = 'tracking'
        AND timestamp >= :dayStart 
        AND timestamp < :dayEnd 
        ORDER BY timestamp ASC
    """)
    abstract suspend fun getByDay(
        toolInstanceId: String, 
        dayStart: Long, 
        dayEnd: Long
    ): List<ToolDataEntity>

    /**
     * Récupère la dernière entrée de tracking pour une instance
     */
    @Query("""
        SELECT * FROM tool_data 
        WHERE tool_instance_id = :toolInstanceId 
        AND tooltype = 'tracking'
        AND timestamp IS NOT NULL
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    abstract suspend fun getLatest(toolInstanceId: String): ToolDataEntity?

    /**
     * Récupère les entrées de tracking avec un nom spécifique (pour choice/predefined)
     */
    @Query("""
        SELECT * FROM tool_data 
        WHERE tool_instance_id = :toolInstanceId 
        AND tooltype = 'tracking'
        AND name = :itemName 
        ORDER BY timestamp DESC
    """)
    abstract suspend fun getByItemName(
        toolInstanceId: String, 
        itemName: String
    ): List<ToolDataEntity>

    /**
     * Récupère les statistiques de base pour une instance de tracking
     */
    @Query("""
        SELECT COUNT(*) as count,
               MIN(timestamp) as first_entry,
               MAX(timestamp) as last_entry
        FROM tool_data 
        WHERE tool_instance_id = :toolInstanceId 
        AND tooltype = 'tracking'
        AND timestamp IS NOT NULL
    """)
    abstract suspend fun getStats(toolInstanceId: String): TrackingStats?

    /**
     * Classe pour les statistiques de tracking
     */
    data class TrackingStats(
        val count: Int,
        val first_entry: Long?,
        val last_entry: Long?
    )
}