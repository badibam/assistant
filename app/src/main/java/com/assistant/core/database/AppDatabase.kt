package com.assistant.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.assistant.core.database.dao.ZoneDao
import com.assistant.core.database.dao.ToolInstanceDao
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.dao.AppSettingsCategoryDao
import com.assistant.core.database.dao.LogDao
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.database.entities.ToolDataEntity
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
    version = 13,
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
    abstract fun appSettingsCategoryDao(): AppSettingsCategoryDao
    abstract fun aiDao(): AIDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun logDao(): LogDao

    companion object {
        /**
         * Database schema version
         *
         * ⚠️ MUST match @Database(version = X) annotation above (line 42)
         * ⚠️ Change BOTH when incrementing database version
         *
         * This constant is needed because @Database annotation value
         * is not accessible as a constant at runtime
         */
        const val VERSION = 13

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

        // Future migrations will be added here:
        // private val MIGRATION_12_13 = object : Migration(12, 13) { ... }

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
                    MIGRATION_12_13
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