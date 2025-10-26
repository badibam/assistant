package com.assistant.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.assistant.core.database.dao.ZoneDao
import com.assistant.core.database.dao.ToolInstanceDao
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.dao.AppSettingsCategoryDao
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.AppSettingsCategory
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
        TranscriptionProviderConfigEntity::class
        // Note: Tool entities will be added dynamically
        // via build system and ToolTypeRegistry
    ],
    version = 11,
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
        const val VERSION = 11

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Room database migrations
         *
         * Architecture:
         * - Migrations are explicit and manual (no discovery pattern)
         * - Each migration handles SQL schema changes only
         * - JSON data transformations handled by JsonTransformers (not Room)
         * - Migrations accumulate historically (never deleted)
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty migration - tool_data table already exists in v2
                // Unified architecture already in place
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add active field to zones table with default value true
                database.execSQL("ALTER TABLE zones ADD COLUMN active INTEGER NOT NULL DEFAULT 1")

                // Add enabled field to tool_instances table with default value true
                database.execSQL("ALTER TABLE tool_instances ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
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
                val automationCursor = database.query(
                    "SELECT id, schedule FROM ai_automations WHERE schedule IS NOT NULL AND schedule LIKE '%com.assistant.core.utils.SchedulePattern%'"
                )
                var automationCount = 0
                while (automationCursor.moveToNext()) {
                    val id = automationCursor.getString(0)
                    val oldSchedule = automationCursor.getString(1)

                    val newSchedule = com.assistant.core.versioning.JsonTransformers.fixSchedulePatternTypes(oldSchedule)
                    database.execSQL(
                        "UPDATE ai_automations SET schedule = ? WHERE id = ?",
                        arrayOf(newSchedule, id)
                    )
                    automationCount++
                }
                automationCursor.close()
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

        // Future migrations will be added here:
        // private val MIGRATION_11_12 = object : Migration(11, 12) { ... }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "assistant_database"
                )
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_10_11
                    // Add future migrations here
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