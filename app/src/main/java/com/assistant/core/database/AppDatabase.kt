package com.assistant.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.assistant.core.database.dao.ZoneDao
import com.assistant.core.database.dao.ToolInstanceDao
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.dao.BaseToolExecutionDao
import com.assistant.core.database.dao.AppSettingsCategoryDao
import com.assistant.core.database.dao.LogDao
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.ToolExecutionEntity
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.database.entities.LogEntry
import com.assistant.core.ai.database.AIDao
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.ai.database.SessionMessageEntity
import com.assistant.core.ai.database.AIProviderConfigEntity
import com.assistant.core.ai.database.AutomationEntity
import com.assistant.core.ai.database.AITypeConverters
import com.assistant.core.ai.database.MessageTypeConverters
import com.assistant.core.transcription.database.TranscriptionDao
import com.assistant.core.transcription.database.TranscriptionProviderConfigEntity
import com.assistant.core.utils.LogManager
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Zone::class,
        ToolInstance::class,
        ToolDataEntity::class,
        ToolExecutionEntity::class,
        AppSettingsCategory::class,
        AISessionEntity::class,
        SessionMessageEntity::class,
        AIProviderConfigEntity::class,
        AutomationEntity::class,
        TranscriptionProviderConfigEntity::class,
        LogEntry::class
        // Note: Tool entities will be added dynamically
        // via build system and ToolTypeRegistry
    ],
    version = 14,
    exportSchema = false
)
@androidx.room.TypeConverters(
    AITypeConverters::class,
    MessageTypeConverters::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun toolInstanceDao(): ToolInstanceDao
    abstract fun toolDataDao(): BaseToolDataDao
    abstract fun toolExecutionDao(): BaseToolExecutionDao
    abstract fun appSettingsCategoryDao(): AppSettingsCategoryDao
    abstract fun aiDao(): AIDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun logDao(): LogDao

    companion object {
        /**
         * Database schema version
         *
         * ⚠️ MUST match @Database(version = X) annotation above (line 48)
         * ⚠️ Change BOTH when incrementing database version
         *
         * This constant is needed because @Database annotation value
         * is not accessible as a constant at runtime
         */
        const val VERSION = 14

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Room database migrations
         *
         * Architecture:
         * - Migrations are explicit and manual (no discovery pattern)
         * - Each migration handles SQL schema changes only
         * - JSON data transformations handled by JsonTransformers (not Room)
         * - Minimum supported version: 9 (older versions require clean install)
         *
         * Migration history:
         * - v9 → v10: No schema changes
         * - v10 → v11: Fix SchedulePattern JSON serialization
         * - v11 → v12: Add log_entries table for in-app logging
         */

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty migration - no schema changes in this version
                // Version bump only for consistency with app versioning
                LogManager.database("MIGRATION 9->10: No schema changes")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Fix SchedulePattern serialization format in JSONs
                // Old format: "type":"com.assistant.core.utils.SchedulePattern.SpecificDates"
                // New format: "type":"SpecificDates"
                // This migration updates all schedule JSONs in automations and tool_data

                LogManager.database("MIGRATION 10->11: Fixing SchedulePattern type names in JSONs")

                // 1. Update automations table (schedule column)
                // Check if table exists first (could be fresh install or renamed table)
                val tableExistsCursor = database.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND (name='automations' OR name='ai_automations')"
                )
                val tableName = if (tableExistsCursor.moveToFirst()) {
                    tableExistsCursor.getString(0)
                } else {
                    null
                }
                tableExistsCursor.close()

                var automationCount = 0
                if (tableName != null) {
                    val automationCursor = database.query(
                        "SELECT id, schedule FROM $tableName WHERE schedule IS NOT NULL AND schedule LIKE '%com.assistant.core.utils.SchedulePattern%'"
                    )
                    while (automationCursor.moveToNext()) {
                        val id = automationCursor.getString(0)
                        val oldSchedule = automationCursor.getString(1)

                        val newSchedule = com.assistant.core.versioning.JsonTransformers.fixSchedulePatternTypes(oldSchedule)
                        database.execSQL(
                            "UPDATE $tableName SET schedule = ? WHERE id = ?",
                            arrayOf(newSchedule, id)
                        )
                        automationCount++
                    }
                    automationCursor.close()
                } else {
                    LogManager.database("MIGRATION 10->11: No automations table found (fresh install or already migrated)")
                }
                LogManager.database("MIGRATION 10->11: Fixed $automationCount automation schedules")

                // 2. Update tool_data table (data column, for Messages tool with schedule field)
                val toolDataCursor = database.query(
                    "SELECT id, data FROM tool_data WHERE data IS NOT NULL AND data LIKE '%com.assistant.core.utils.SchedulePattern%'"
                )
                var toolDataCount = 0
                while (toolDataCursor.moveToNext()) {
                    val id = toolDataCursor.getString(0)
                    val oldData = toolDataCursor.getString(1)

                    val newData = com.assistant.core.versioning.JsonTransformers.fixSchedulePatternTypes(oldData)
                    database.execSQL(
                        "UPDATE tool_data SET data = ? WHERE id = ?",
                        arrayOf(newData, id)
                    )
                    toolDataCount++
                }
                toolDataCursor.close()
                LogManager.database("MIGRATION 10->11: Fixed $toolDataCount tool_data entries")

                LogManager.database("MIGRATION 10->11: SchedulePattern type names migration complete")
            }
        }

        /**
         * Migration 11 -> 12: Add log_entries table
         * For in-app error logging and debugging
         *
         * Note: Column order MUST match exactly the field declaration order in LogEntry.kt
         * Room is strict about column ordering
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 11->12: Creating log_entries table")

                // Drop table if it exists (clean slate for migration)
                database.execSQL("DROP TABLE IF EXISTS log_entries")

                // Create table with columns in EXACT order of LogEntry.kt fields
                // Order: id, timestamp, level, tag, message, throwableMessage
                database.execSQL("""
                    CREATE TABLE log_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        level TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        message TEXT NOT NULL,
                        throwableMessage TEXT
                    )
                """.trimIndent())

                // Create index on timestamp for efficient queries
                database.execSQL("""
                    CREATE INDEX index_log_entries_timestamp
                    ON log_entries(timestamp)
                """.trimIndent())

                LogManager.database("MIGRATION 11->12: log_entries table created successfully")
            }
        }

        /**
         * Migration 12 → 13: Clean segments_texts from transcription metadata
         *
         * Problem: segments_texts in transcription_metadata duplicates the full text
         * already stored in the field, doubling data size unnecessarily.
         *
         * Solution: Remove segments_texts from all transcription_metadata in tool_data
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 12->13: Starting - Cleaning segments_texts from transcription metadata", "INFO")

                // Get all tool_data entries with transcription_metadata
                val cursor = database.query(
                    "SELECT id, data FROM tool_data WHERE data LIKE '%transcription_metadata%'"
                )

                val totalEntries = cursor.count
                LogManager.database("MIGRATION 12->13: Found $totalEntries entries with transcription_metadata", "INFO")

                var cleanedCount = 0
                var errorCount = 0

                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val dataJson = cursor.getString(1)

                    try {
                        val dataObj = org.json.JSONObject(dataJson)
                        val metadata = dataObj.optJSONObject("transcription_metadata")

                        if (metadata != null) {
                            // Iterate through all fields in metadata
                            val fieldNames = metadata.keys()
                            var modified = false
                            var fieldsCleanedInEntry = 0

                            while (fieldNames.hasNext()) {
                                val fieldName = fieldNames.next()
                                val fieldMetadata = metadata.optJSONObject(fieldName)

                                // Remove segments_texts if present
                                if (fieldMetadata?.has("segments_texts") == true) {
                                    fieldMetadata.remove("segments_texts")
                                    modified = true
                                    fieldsCleanedInEntry++
                                }
                            }

                            // Update DB if modified
                            if (modified) {
                                database.execSQL(
                                    "UPDATE tool_data SET data = ? WHERE id = ?",
                                    arrayOf(dataObj.toString(), id)
                                )
                                cleanedCount++
                                LogManager.database("MIGRATION 12->13: Cleaned $fieldsCleanedInEntry field(s) in entry $id", "DEBUG")
                            }
                        }
                    } catch (e: Exception) {
                        errorCount++
                        LogManager.database("MIGRATION 12->13: Failed to clean entry $id: ${e.message}", "ERROR", e)
                    }
                }
                cursor.close()

                LogManager.database("MIGRATION 12->13: Completed - Cleaned $cleanedCount entries ($errorCount errors)", "INFO")
            }
        }

        /**
         * Migration 13 → 14: Add tool_executions table
         *
         * Problem: Messages tool stores execution history in JSON array within tool_data.data
         * - Unlimited growth in JSON
         * - Heavy impact on AI tokens (POINTER/USE enrichments send full history)
         * - Not reusable for other tooltypes (Goals, Alerts, Questionnaires)
         * - Difficult to query/filter/paginate
         *
         * Solution: Separate table tool_executions for execution history
         * - Migrates existing Messages executions to new table
         * - Removes executions array from Messages tool_data
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 13->14: Starting - Creating tool_executions table", "INFO")

                // 1. Create tool_executions table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tool_executions (
                        id TEXT PRIMARY KEY NOT NULL,
                        tool_instance_id TEXT NOT NULL,
                        tooltype TEXT NOT NULL,
                        template_data_id TEXT NOT NULL,
                        scheduled_time INTEGER,
                        execution_time INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        snapshot_data TEXT NOT NULL,
                        execution_result TEXT NOT NULL,
                        triggered_by TEXT NOT NULL,
                        metadata TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(tool_instance_id) REFERENCES tool_instances(id) ON DELETE CASCADE,
                        FOREIGN KEY(template_data_id) REFERENCES tool_data(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 2. Create indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_executions_tool ON tool_executions(tool_instance_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_executions_template ON tool_executions(template_data_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_executions_time ON tool_executions(execution_time)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_executions_status ON tool_executions(status)")

                LogManager.database("MIGRATION 13->14: tool_executions table created with indexes", "INFO")

                // 3. Migrate existing Messages executions from tool_data to tool_executions
                val cursor = database.query(
                    "SELECT id, tool_instance_id, data FROM tool_data WHERE tooltype = 'messages' AND data LIKE '%executions%'"
                )

                var migratedCount = 0
                var errorCount = 0

                while (cursor.moveToNext()) {
                    val templateDataId = cursor.getString(0)
                    val toolInstanceId = cursor.getString(1)
                    val dataJson = cursor.getString(2)

                    try {
                        val dataObj = org.json.JSONObject(dataJson)
                        val executions = dataObj.optJSONArray("executions")

                        if (executions != null && executions.length() > 0) {
                            // Migrate each execution to tool_executions table
                            for (i in 0 until executions.length()) {
                                val execution = executions.getJSONObject(i)

                                // Extract execution fields
                                val executionId = java.util.UUID.randomUUID().toString()
                                val executionTime = execution.optLong("timestamp", System.currentTimeMillis())
                                val status = if (execution.optBoolean("read", false)) "completed" else "pending"

                                // Build snapshot_data (template content at execution time)
                                val snapshotData = org.json.JSONObject().apply {
                                    put("title", dataObj.optString("title", ""))
                                    put("content", dataObj.optString("content", ""))
                                    put("priority", dataObj.optString("priority", "default"))
                                }.toString()

                                // Build execution_result
                                val executionResult = org.json.JSONObject().apply {
                                    put("read", execution.optBoolean("read", false))
                                    put("archived", execution.optBoolean("archived", false))
                                    put("notification_sent", true) // Assume sent if in history
                                }.toString()

                                // Build metadata
                                val metadata = org.json.JSONObject().apply {
                                    // Empty for now, can store errors or additional context later
                                }.toString()

                                // Insert into tool_executions
                                database.execSQL(
                                    """INSERT INTO tool_executions
                                        (id, tool_instance_id, tooltype, template_data_id, scheduled_time, execution_time,
                                         status, snapshot_data, execution_result, triggered_by, metadata, created_at, updated_at)
                                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                                    arrayOf(
                                        executionId,
                                        toolInstanceId,
                                        "messages",
                                        templateDataId,
                                        null, // scheduled_time unknown for historical data
                                        executionTime,
                                        status,
                                        snapshotData,
                                        executionResult,
                                        "SCHEDULE", // Assume scheduled since Messages use scheduling
                                        metadata,
                                        executionTime, // created_at = execution_time (best guess)
                                        executionTime  // updated_at = execution_time
                                    )
                                )
                                migratedCount++
                            }

                            // Remove executions array from tool_data using transformer
                            // This ensures consistency between migration and backup imports
                            val cleanedData = com.assistant.core.versioning.JsonTransformers.transformToolData(
                                dataObj.toString(),
                                "messages",
                                13,
                                14
                            )
                            database.execSQL(
                                "UPDATE tool_data SET data = ? WHERE id = ?",
                                arrayOf(cleanedData, templateDataId)
                            )
                        }
                    } catch (e: Exception) {
                        errorCount++
                        LogManager.database("MIGRATION 13->14: Failed to migrate executions for template $templateDataId: ${e.message}", "ERROR", e)
                    }
                }
                cursor.close()

                LogManager.database("MIGRATION 13->14: Completed - Migrated $migratedCount executions ($errorCount errors)", "INFO")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "assistant_database"
                )
                .addMigrations(
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14
                    // Add future migrations here (minimum supported version: 9)
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Database opened successfully
                        LogManager.database("AppDatabase opened successfully - version ${db.version}")
                    }
                })
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}