package com.assistant.core.ai.database

import androidx.room.*

/**
 * Automation database entity
 * Complex structures (schedule, triggerIds, executionHistory) stored as JSON
 */
@Entity(
    tableName = "automations",
    indices = [
        Index(value = ["zoneId"]),
        Index(value = ["isEnabled"]),
        Index(value = ["seedSessionId"])
    ]
)
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val zoneId: String,
    val seedSessionId: String,
    val scheduleJson: String?,              // JSON of ScheduleConfig
    val triggerIdsJson: String,             // JSON array of trigger IDs
    val dismissOlderInstances: Boolean,
    val providerId: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    val lastExecutionId: String?,
    val executionHistoryJson: String        // JSON array of execution session IDs
)

/**
 * DAO for automation CRUD operations
 */
@Dao
interface AutomationDao {

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationEntity?

    @Query("SELECT * FROM automations WHERE zoneId = :zoneId ORDER BY createdAt DESC")
    suspend fun getByZone(zoneId: String): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE seedSessionId = :seedSessionId")
    suspend fun getBySeedSession(seedSessionId: String): AutomationEntity?

    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    suspend fun getAll(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: AutomationEntity)

    @Update
    suspend fun update(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE automations SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE automations SET lastExecutionId = :executionId WHERE id = :id")
    suspend fun updateLastExecution(id: String, executionId: String)
}

/**
 * Standalone database for automations (discovery pattern)
 */
@Database(
    entities = [AutomationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AutomationDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
}
