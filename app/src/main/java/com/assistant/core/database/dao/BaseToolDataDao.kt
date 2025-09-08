package com.assistant.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.assistant.core.database.entities.ToolDataEntity

/**
 * Data class pour le résultat de la requête getTooltypeMinVersions
 */
data class TooltypeVersion(
    val tooltype: String,
    val min_version: Int
)

/**
 * DAO de base pour opérations communes sur tool_data
 * Les DAOs spécialisés héritent de cette classe
 */
@Dao
abstract class BaseToolDataDao {

    /**
     * Insère une nouvelle entrée dans tool_data
     */
    @Insert
    abstract suspend fun insert(entity: ToolDataEntity)

    /**
     * Met à jour une entrée existante
     */
    @Update
    abstract suspend fun update(entity: ToolDataEntity)

    /**
     * Récupère toutes les entrées d'une instance d'outil
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId ORDER BY timestamp DESC")
    abstract suspend fun getByToolInstance(toolInstanceId: String): List<ToolDataEntity>

    /**
     * Récupère une entrée par son ID
     */
    @Query("SELECT * FROM tool_data WHERE id = :id")
    abstract suspend fun getById(id: String): ToolDataEntity?

    /**
     * Supprime une entrée par son ID
     */
    @Query("DELETE FROM tool_data WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    /**
     * Supprime toutes les entrées d'une instance d'outil
     */
    @Query("DELETE FROM tool_data WHERE tool_instance_id = :toolInstanceId")
    abstract suspend fun deleteByToolInstance(toolInstanceId: String)

    /**
     * Compte le nombre d'entrées d'une instance d'outil
     */
    @Query("SELECT COUNT(*) FROM tool_data WHERE tool_instance_id = :toolInstanceId")
    abstract suspend fun countByToolInstance(toolInstanceId: String): Int

    /**
     * Récupère les entrées les plus récentes
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId ORDER BY timestamp DESC LIMIT :limit")
    abstract suspend fun getRecent(toolInstanceId: String, limit: Int): List<ToolDataEntity>

    /**
     * Récupère toutes les entrées d'un type d'outil spécifique
     */
    @Query("SELECT * FROM tool_data WHERE tooltype = :tooltype ORDER BY timestamp DESC")
    abstract suspend fun getByTooltype(tooltype: String): List<ToolDataEntity>

    /**
     * Récupère les entrées dans une plage de temps avec limite
     */
    @Query("SELECT * FROM tool_data WHERE tool_instance_id = :toolInstanceId AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC LIMIT :limit")
    abstract suspend fun getByTimeRange(toolInstanceId: String, startTime: Long, endTime: Long, limit: Int): List<ToolDataEntity>

    /**
     * Récupère les versions minimales de données par tooltype
     * Utilisé pour déterminer quels tooltypes nécessitent une migration
     */
    @Query("SELECT tooltype, MIN(data_version) as min_version FROM tool_data GROUP BY tooltype")
    abstract suspend fun getTooltypeMinVersions(): List<TooltypeVersion>
}