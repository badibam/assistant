package app.treelune.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import app.treelune.core.database.dao.ZoneDao
import app.treelune.core.database.dao.ToolInstanceDao
import app.treelune.core.database.dao.BaseToolDataDao
import app.treelune.core.database.dao.AppSettingsCategoryDao
import app.treelune.core.database.dao.LogDao
import app.treelune.core.database.dao.VariableDao
import app.treelune.core.database.entities.Zone
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.database.entities.AppSettingsCategory
import app.treelune.core.database.entities.LogEntry
import app.treelune.core.database.entities.VariableEntity
import app.treelune.core.ai.database.AIDao
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.ai.database.SessionMessageEntity
import app.treelune.core.ai.database.AttachedFileEntity
import app.treelune.core.ai.database.AttachedFileDao
import app.treelune.core.ai.database.AttachedImageEntity
import app.treelune.core.ai.database.AttachedImageDao
import app.treelune.core.ai.database.AIProviderConfigEntity
import app.treelune.core.ai.database.AutomationEntity
import app.treelune.core.ai.database.AITypeConverters
import app.treelune.core.ai.database.MessageTypeConverters
import app.treelune.core.utils.LogManager
import app.treelune.core.versioning.AILimitsAtV32
import app.treelune.core.versioning.AILimitsAtV34
import app.treelune.core.versioning.AILimitsAtV64
import app.treelune.core.versioning.DateFieldBounds
import app.treelune.core.versioning.SettingsAtV33
import app.treelune.core.versioning.ChoiceOptionsAtV37
import app.treelune.core.versioning.FieldsAtV36
import app.treelune.core.versioning.NumericDecimalsAtV38
import app.treelune.core.versioning.ToolConfigsAtV39
import app.treelune.core.versioning.CatchUpAtV41
import app.treelune.core.versioning.FormatNullsAtV42
import app.treelune.core.versioning.ScheduleDatesAtV51
import app.treelune.core.versioning.ConditionsAtV52
import app.treelune.core.versioning.GridAtV53
import app.treelune.core.versioning.ZoneGridAtV54
import app.treelune.core.versioning.UiAppearanceAtV55
import app.treelune.core.versioning.UiSizeStepAtV56
import app.treelune.core.versioning.UiThemeModeAtV57
import app.treelune.core.versioning.UiHueShiftAtV58
import app.treelune.core.versioning.TextLengthAtV61
import app.treelune.core.versioning.GroupsAtV62
import app.treelune.core.versioning.TrackingUnitAtV43
import app.treelune.core.versioning.PointerAtV44
import app.treelune.core.versioning.EnrichmentTextAtV45
import app.treelune.core.versioning.PointerAtV46
import app.treelune.core.versioning.VariableValidationAtV49
import app.treelune.core.versioning.FormerDefaultIcons
import app.treelune.core.versioning.KeyCaseRenames
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.treelune.core.ai.data.LegacyCatchUp

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
        LogEntry::class,
        VariableEntity::class,
        AttachedFileEntity::class,
        AttachedImageEntity::class,
        app.treelune.core.mcp.McpClientEntity::class,
        app.treelune.core.mcp.McpTokenEntity::class
        // Note: Tool entities will be added dynamically
        // via build system and ToolTypeRegistry
    ],
    version = AppDatabase.VERSION,
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
    abstract fun logDao(): LogDao
    abstract fun variableDao(): VariableDao
    abstract fun attachedFileDao(): AttachedFileDao
    abstract fun attachedImageDao(): AttachedImageDao
    abstract fun mcpDao(): app.treelune.core.mcp.McpDao

    companion object {
        /**
         * Database schema version, which the @Database annotation above reads. Backups record
         * it, and an import transforms its data from the version it records.
         */
        const val VERSION = 69

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

                        val newSchedule = app.treelune.core.versioning.JsonTransformers.fixSchedulePatternTypes(oldSchedule)
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

                    val newData = app.treelune.core.versioning.JsonTransformers.fixSchedulePatternTypes(oldData)
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

                // 2. Create indexes (matching Entity @Index definitions with Room naming)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_executions_tool_instance_id ON tool_executions(tool_instance_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_executions_template_data_id ON tool_executions(template_data_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_executions_execution_time ON tool_executions(execution_time)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_executions_status ON tool_executions(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_executions_tool_instance_id_execution_time ON tool_executions(tool_instance_id, execution_time)")

                LogManager.database("MIGRATION 13->14: tool_executions table created with indexes", "INFO")

                // 3. Update Messages tool instances config to add execution_schema_id
                val configCursor = database.query(
                    "SELECT id, config_json FROM tool_instances WHERE tool_type = 'messages'"
                )

                var configUpdateCount = 0
                while (configCursor.moveToNext()) {
                    val instanceId = configCursor.getString(0)
                    val configJson = configCursor.getString(1)

                    try {
                        val configObj = org.json.JSONObject(configJson)

                        // Add execution_schema_id if not already present
                        if (!configObj.has("execution_schema_id")) {
                            configObj.put("execution_schema_id", "messages_execution")

                            database.execSQL(
                                "UPDATE tool_instances SET config_json = ? WHERE id = ?",
                                arrayOf(configObj.toString(), instanceId)
                            )
                            configUpdateCount++
                        }
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 13->14: Failed to update config for instance $instanceId: ${e.message}", "ERROR", e)
                    }
                }
                configCursor.close()

                LogManager.database("MIGRATION 13->14: Updated $configUpdateCount Messages instance configs with execution_schema_id", "INFO")

                // 4. Migrate existing Messages executions from tool_data to tool_executions
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
                                    arrayOf<Any?>(
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
                            val cleanedData = app.treelune.core.versioning.JsonTransformers.transformToolData(
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

        /**
         * Migration 14 → 15: Remove schema_id from Messages data.properties
         *
         * Problem: Messages tool had schema_id in data.properties, inconsistent with other tooltypes
         * - Tracking, Journal, Note tools don't have schema_id in data.properties
         * - schema_id should only exist at entry root level (systemManaged)
         * - Having it in data.properties is confusing and redundant
         *
         * Solution: Remove schema_id from data object for all Messages entries
         * - Uses JsonTransformers.transformToolData() for consistent transformation
         * - Only affects Messages tooltype entries
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 14->15: Starting - Removing schema_id from Messages data.properties", "INFO")

                // Get all Messages tool_data entries
                val cursor = database.query(
                    "SELECT id, data FROM tool_data WHERE tooltype = 'messages'"
                )

                val totalEntries = cursor.count
                LogManager.database("MIGRATION 14->15: Found $totalEntries Messages entries", "INFO")

                var cleanedCount = 0
                var errorCount = 0

                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val dataJson = cursor.getString(1)

                    try {
                        // Use JsonTransformers to apply transformation v14->v15
                        val transformedJson = app.treelune.core.versioning.JsonTransformers.transformToolData(
                            json = dataJson,
                            tooltype = "messages",
                            fromVersion = 14,
                            toVersion = 15
                        )

                        // Update only if transformation actually changed the data
                        if (transformedJson != dataJson) {
                            database.execSQL(
                                "UPDATE tool_data SET data = ? WHERE id = ?",
                                arrayOf(transformedJson, id)
                            )
                            cleanedCount++
                            LogManager.database("MIGRATION 14->15: Cleaned schema_id from entry $id", "DEBUG")
                        }
                    } catch (e: Exception) {
                        errorCount++
                        LogManager.database("MIGRATION 14->15: Failed to clean entry $id: ${e.message}", "ERROR", e)
                    }
                }
                cursor.close()

                LogManager.database("MIGRATION 14->15: Completed - Cleaned $cleanedCount entries ($errorCount errors)", "INFO")
            }
        }

        /**
         * Migration 15 → 16: Add updatedAt column to automations table
         *
         * Problem: AutomationScheduler uses lastExecutionTime to calculate next execution
         * - When automation is disabled then re-enabled, it may execute for all missed periods
         * - No way to track when automation config/schedule was last modified
         *
         * Solution: Add updatedAt timestamp to track last modification
         * - Set on create, update, enable, disable operations
         * - AutomationScheduler uses max(lastExecutionTime, updatedAt) as reference
         * - Skips executions that would have occurred before last modification
         * - For existing automations at migration time: set to now (safe default)
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 15->16: Starting - Adding updatedAt to automations + appStateSnapshot to ai_sessions", "INFO")

                // 1. Add updatedAt column to automations with default value 0
                database.execSQL("""
                    ALTER TABLE automations
                    ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0
                """)

                // 2. Set updatedAt = now for existing automations
                // Rationale: Safer to start fresh from migration time than risk executing missed periods
                val now = System.currentTimeMillis()
                database.execSQL("""
                    UPDATE automations
                    SET updatedAt = $now
                    WHERE updatedAt = 0
                """)

                val cursorAutomations = database.query("SELECT COUNT(*) FROM automations")
                val automationsCount = if (cursorAutomations.moveToFirst()) cursorAutomations.getInt(0) else 0
                cursorAutomations.close()

                LogManager.database("MIGRATION 15->16: Updated $automationsCount automations with updatedAt = now", "INFO")

                // 3. Add appStateSnapshot column to ai_sessions (nullable, default NULL)
                database.execSQL("""
                    ALTER TABLE ai_sessions
                    ADD COLUMN appStateSnapshot TEXT DEFAULT NULL
                """)

                LogManager.database("MIGRATION 15->16: Completed - Added appStateSnapshot column to ai_sessions", "INFO")
            }
        }

        /**
         * Migration 16 → 17: Replace tokensUsed with tokensJson and costJson in ai_sessions
         *
         * Problem: tokensUsed: Int? doesn't capture token breakdown (uncached input, cache write, cache read, output)
         * - Cannot display detailed token usage
         * - Cannot calculate accurate costs
         * - Requires recalculation from messages every time
         *
         * Solution: Store comprehensive token and cost breakdowns
         * - tokensJson: Always available (from API responses)
         * - costJson: Calculated when model prices available
         * - Incremental updates when messages added
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 16->17: Starting - Replacing tokensUsed with tokensJson and costJson", "INFO")

                // 1. Add new columns (nullable, will be populated incrementally as sessions run)
                database.execSQL("""
                    ALTER TABLE ai_sessions
                    ADD COLUMN tokensJson TEXT DEFAULT NULL
                """)

                database.execSQL("""
                    ALTER TABLE ai_sessions
                    ADD COLUMN costJson TEXT DEFAULT NULL
                """)

                LogManager.database("MIGRATION 16->17: Added tokensJson and costJson columns", "INFO")

                // 2. Drop old tokensUsed column
                // SQLite doesn't support DROP COLUMN directly before version 3.35.0
                // Use table recreation pattern for compatibility

                // Create new table with correct schema
                database.execSQL("""
                    CREATE TABLE ai_sessions_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        requireValidation INTEGER NOT NULL DEFAULT 0,
                        phase TEXT NOT NULL DEFAULT 'IDLE',
                        waitingContextJson TEXT DEFAULT NULL,
                        totalRoundtrips INTEGER NOT NULL DEFAULT 0,
                        lastEventTime INTEGER NOT NULL DEFAULT 0,
                        lastUserInteractionTime INTEGER NOT NULL DEFAULT 0,
                        automationId TEXT DEFAULT NULL,
                        scheduledExecutionTime INTEGER DEFAULT NULL,
                        providerId TEXT NOT NULL,
                        providerSessionId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastActivity INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        endReason TEXT DEFAULT NULL,
                        appStateSnapshot TEXT DEFAULT NULL,
                        tokensJson TEXT DEFAULT NULL,
                        costJson TEXT DEFAULT NULL
                    )
                """)

                // Copy data from old table to new table (excluding tokensUsed)
                database.execSQL("""
                    INSERT INTO ai_sessions_new (
                        id, name, type, requireValidation, phase, waitingContextJson,
                        totalRoundtrips, lastEventTime, lastUserInteractionTime,
                        automationId, scheduledExecutionTime, providerId, providerSessionId,
                        createdAt, lastActivity, isActive, endReason, appStateSnapshot,
                        tokensJson, costJson
                    )
                    SELECT
                        id, name, type, requireValidation, phase, waitingContextJson,
                        totalRoundtrips, lastEventTime, lastUserInteractionTime,
                        automationId, scheduledExecutionTime, providerId, providerSessionId,
                        createdAt, lastActivity, isActive, endReason, appStateSnapshot,
                        NULL, NULL
                    FROM ai_sessions
                """)

                // Drop old table
                database.execSQL("DROP TABLE ai_sessions")

                // Rename new table
                database.execSQL("ALTER TABLE ai_sessions_new RENAME TO ai_sessions")

                // Recreate indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_isActive ON ai_sessions(isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_type ON ai_sessions(type)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_lastActivity ON ai_sessions(lastActivity)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_automationId ON ai_sessions(automationId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_phase ON ai_sessions(phase)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_endReason ON ai_sessions(endReason)")

                val cursorSessions = database.query("SELECT COUNT(*) FROM ai_sessions")
                val sessionsCount = if (cursorSessions.moveToFirst()) cursorSessions.getInt(0) else 0
                cursorSessions.close()

                LogManager.database("MIGRATION 16->17: Migrated $sessionsCount sessions (tokensUsed removed, tokensJson and costJson added)", "INFO")
                LogManager.database("MIGRATION 16->17: Completed - Token and cost data will be populated incrementally as sessions execute", "INFO")
            }
        }

        /**
         * Migration 17 → 18: Add groups system for zones and tools
         *
         * Problem: No organizational structure for tools and automations within zones
         * - Users need to group related tools/automations together
         * - Similar need at app level for grouping zones
         *
         * Solution: Two-level groups system
         * - Zone level: tool_groups (JSON array) for organizing tools and automations
         * - App level: zone_groups (in app_config JSON) for organizing zones
         * - Tool instances: group field (in config JSON via BaseSchemas)
         * - Automations: group column (nullable string)
         *
         * Implementation:
         * 1. Add tool_groups column to zones table (JSON array of group names)
         * 2. Add group column to automations table (nullable string reference)
         * 3. zone_groups will be added to app_config JSON by AppConfigService (no ALTER needed)
         * 4. Tool instances group field already handled by config JSON (no ALTER needed)
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 17->18: Starting - Adding groups system", "INFO")

                // 1. Add tool_groups column to zones (JSON array of group names)
                database.execSQL("""
                    ALTER TABLE zones
                    ADD COLUMN tool_groups TEXT DEFAULT NULL
                """)

                LogManager.database("MIGRATION 17->18: Added tool_groups column to zones", "INFO")

                // 2. Add group column to zones (nullable string linking to zone_groups in app_config)
                database.execSQL("""
                    ALTER TABLE zones
                    ADD COLUMN `group` TEXT DEFAULT NULL
                """)

                LogManager.database("MIGRATION 17->18: Added group column to zones", "INFO")

                // 3. Add group column to automations (nullable string linking to zone's tool_groups)
                database.execSQL("""
                    ALTER TABLE automations
                    ADD COLUMN `group` TEXT DEFAULT NULL
                """)

                LogManager.database("MIGRATION 17->18: Added group column to automations", "INFO")

                // 4. Add seedId column to ai_sessions (for CHAT sessions created from automation button)
                database.execSQL("""
                    ALTER TABLE ai_sessions
                    ADD COLUMN seedId TEXT DEFAULT NULL
                """)

                LogManager.database("MIGRATION 17->18: Added seedId column to ai_sessions", "INFO")

                // 5. Add custom_fields column to tool_data (JSON object for custom field values)
                database.execSQL("""
                    ALTER TABLE tool_data
                    ADD COLUMN custom_fields TEXT DEFAULT NULL
                """)

                LogManager.database("MIGRATION 17->18: Added custom_fields column to tool_data", "INFO")

                // Count affected records for logging
                val zonesCount = database.query("SELECT COUNT(*) FROM zones").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val automationsCount = database.query("SELECT COUNT(*) FROM automations").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val sessionsCount = database.query("SELECT COUNT(*) FROM ai_sessions").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val toolDataCount = database.query("SELECT COUNT(*) FROM tool_data").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }

                LogManager.database("MIGRATION 17->18: Completed - $zonesCount zones, $automationsCount automations, $sessionsCount sessions, and $toolDataCount tool_data entries ready", "INFO")
            }
        }

        /**
         * Migration 18 → 19: Remove transcription system
         *
         * Problem: Transcription is being moved to external app
         * - TranscriptionProviderConfigEntity table no longer needed
         * - transcription_metadata field in tool_data no longer needed
         *
         * Solution: Drop transcription-related database objects
         * - Drop transcription_provider_config table
         * - Remove transcription_metadata from all tool_data entries
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 18->19: Starting - Removing transcription system", "INFO")

                // 1. Drop transcription_provider_config table (if exists)
                database.execSQL("DROP TABLE IF EXISTS transcription_provider_config")
                LogManager.database("MIGRATION 18->19: Dropped transcription_provider_config table", "INFO")

                // 2. Remove transcription_metadata from all tool_data entries
                val cursor = database.query(
                    "SELECT id, data FROM tool_data WHERE data LIKE '%transcription_metadata%'"
                )

                val totalEntries = cursor.count
                LogManager.database("MIGRATION 18->19: Found $totalEntries entries with transcription_metadata", "INFO")

                var cleanedCount = 0
                var errorCount = 0

                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val dataJson = cursor.getString(1)

                    try {
                        val dataObj = org.json.JSONObject(dataJson)

                        // Remove transcription_metadata if present
                        if (dataObj.has("transcription_metadata")) {
                            dataObj.remove("transcription_metadata")

                            database.execSQL(
                                "UPDATE tool_data SET data = ? WHERE id = ?",
                                arrayOf(dataObj.toString(), id)
                            )
                            cleanedCount++
                            LogManager.database("MIGRATION 18->19: Cleaned transcription_metadata from entry $id", "DEBUG")
                        }
                    } catch (e: Exception) {
                        errorCount++
                        LogManager.database("MIGRATION 18->19: Failed to clean entry $id: ${e.message}", "ERROR", e)
                    }
                }
                cursor.close()

                LogManager.database("MIGRATION 18->19: Completed - Cleaned $cleanedCount entries ($errorCount errors)", "INFO")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                LogManager.database("MIGRATION 19->20: Version bump only - data migration handled in post-migration", "INFO")
                // No schema changes - just version bump
                // Actual data migration (filling null format values) happens in post-migration
                // after Room initialization, where we have access to Context for system detection
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Messages refonte: a Messages instance is now one notification template, its
                // config holds what used to be a tool_data template entry, and its tool_data
                // holds the occurrences that used to be tool_executions rows.
                //
                // Nothing is converted. Pre-existing Messages instances were multi-template and
                // their configs cannot satisfy the new schema; the user chose a clean slate over
                // a conversion nobody would trust.
                //
                // tool_executions itself is not dropped here: the entity still exists at this
                // version, and Room validates the schema against its entities at startup.

                val dataCursor = database.query("SELECT COUNT(*) FROM tool_data WHERE tooltype = 'messages'")
                val deletedEntries = if (dataCursor.moveToFirst()) dataCursor.getInt(0) else 0
                dataCursor.close()

                val instanceCursor = database.query("SELECT COUNT(*) FROM tool_instances WHERE tool_type = 'messages'")
                val deletedInstances = if (instanceCursor.moveToFirst()) instanceCursor.getInt(0) else 0
                instanceCursor.close()

                database.execSQL("DELETE FROM tool_executions WHERE tooltype = 'messages'")
                database.execSQL("DELETE FROM tool_data WHERE tooltype = 'messages'")
                database.execSQL("DELETE FROM tool_instances WHERE tool_type = 'messages'")

                LogManager.database(
                    "MIGRATION 20->21: Removed $deletedInstances Messages instance(s) and $deletedEntries entry(ies) - clean slate for the refonte",
                    "INFO"
                )
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The execution plane is gone. Occurrences are ordinary tool_data entries, so
                // there is no third place where a tool records what it produced.
                //
                // Dropping the table here rather than with the data deletion of 20->21: Room
                // validates the schema against its entities at startup, so the table can only
                // go once ToolExecutionEntity does, and both happen at this version.
                // SQLite drops a table's indexes with the table, so the five created by 13->14
                // need no statement of their own.
                database.execSQL("DROP TABLE IF EXISTS tool_executions")

                LogManager.database("MIGRATION 21->22: tool_executions dropped", "INFO")
            }
        }

        /**
         * Migration 22 -> 23: Add catchUpWindowMinutes to automations
         *
         * A scheduled automation used to catch up on every occurrence it had missed, with no
         * limit: reopened after 47 days, a daily one ran 47 times in a row. The window says how
         * late an occurrence may be and still run; null means no limit.
         *
         * Existing automations are read by the LegacyCatchUp rule: no window, most recent
         * occurrence only. The column is nullable, so the window needs no statement -- an added
         * column is NULL everywhere. dismissOlderInstances does: it has been stored all along
         * with a value nothing could set, and left at false it would keep the old behaviour
         * under the new setting. Only scheduled automations are touched, since the two settings
         * mean nothing for a manual or event-driven one.
         */
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE automations
                    ADD COLUMN catchUpWindowMinutes INTEGER DEFAULT NULL
                """)

                val dismissOlder = if (LegacyCatchUp.DISMISS_OLDER) 1 else 0
                database.execSQL("""
                    UPDATE automations
                    SET dismissOlderInstances = $dismissOlder
                    WHERE scheduleJson IS NOT NULL
                """)

                val cursor = database.query("SELECT COUNT(*) FROM automations WHERE scheduleJson IS NOT NULL")
                val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                cursor.close()

                LogManager.database("MIGRATION 22->23: $count scheduled automation(s) read as unlimited window, most recent only", "INFO")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Rename tool_instances.tool_type to tooltype, the single name the app now uses
                // for a tool's type. SQLite before 3.25 has no RENAME COLUMN, and minSdk 26 ships
                // 3.19, so the table is recreated. The foreign key on zone_id is part of the
                // schema Room checks at open, so the new table carries it.
                database.execSQL("""
                    CREATE TABLE tool_instances_new (
                        id TEXT NOT NULL,
                        zone_id TEXT NOT NULL,
                        tooltype TEXT NOT NULL,
                        config_json TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        order_index INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(zone_id) REFERENCES zones(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)

                database.execSQL("""
                    INSERT INTO tool_instances_new (
                        id, zone_id, tooltype, config_json, enabled, order_index, created_at, updated_at
                    )
                    SELECT
                        id, zone_id, tool_type, config_json, enabled, order_index, created_at, updated_at
                    FROM tool_instances
                """)

                database.execSQL("DROP TABLE tool_instances")
                database.execSQL("ALTER TABLE tool_instances_new RENAME TO tool_instances")

                val cursor = database.query("SELECT COUNT(*) FROM tool_instances")
                val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                cursor.close()

                LogManager.database("MIGRATION 23->24: $count tool instance(s) moved to the tooltype column", "INFO")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ai_provider_configs moves its columns to snake_case, the naming rule the whole
                // project follows. No RENAME COLUMN before SQLite 3.25 and minSdk 26 ships 3.19,
                // so the table is recreated; its two indices are recreated under the names Room
                // derives from the new column names.
                database.execSQL("""
                    CREATE TABLE ai_provider_configs_new (
                        provider_id TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        config_json TEXT NOT NULL,
                        is_configured INTEGER NOT NULL,
                        is_active INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(provider_id)
                    )
                """)

                database.execSQL("""
                    INSERT INTO ai_provider_configs_new (
                        provider_id, display_name, config_json, is_configured, is_active, created_at, updated_at
                    )
                    SELECT
                        providerId, displayName, configJson, isConfigured, isActive, createdAt, updatedAt
                    FROM ai_provider_configs
                """)

                database.execSQL("DROP TABLE ai_provider_configs")
                database.execSQL("ALTER TABLE ai_provider_configs_new RENAME TO ai_provider_configs")

                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ai_provider_configs_provider_id ON ai_provider_configs(provider_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_provider_configs_is_active ON ai_provider_configs(is_active)")

                val cursor = database.query("SELECT COUNT(*) FROM ai_provider_configs")
                val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                cursor.close()

                LogManager.database("MIGRATION 24->25: $count provider config(s) moved to snake_case columns", "INFO")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The three remaining AI tables move their columns to snake_case, the naming rule
                // the whole project follows. SQLite before 3.25 has no RENAME COLUMN and minSdk 26
                // ships 3.19, so each table is recreated with its indices. ai_sessions comes first
                // and session_messages last, so the foreign key between them lands on the final
                // table; Room defers foreign key checks to the end of the migration transaction.

                database.execSQL("""
                    CREATE TABLE ai_sessions_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        require_validation INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        waiting_context_json TEXT,
                        total_roundtrips INTEGER NOT NULL,
                        last_event_time INTEGER NOT NULL,
                        last_user_interaction_time INTEGER NOT NULL,
                        automation_id TEXT,
                        seed_id TEXT,
                        scheduled_execution_time INTEGER,
                        provider_id TEXT NOT NULL,
                        provider_session_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_activity INTEGER NOT NULL,
                        is_active INTEGER NOT NULL,
                        end_reason TEXT,
                        tokens_json TEXT,
                        cost_json TEXT,
                        app_state_snapshot TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO ai_sessions_new
                    SELECT id, name, type, requireValidation, phase, waitingContextJson,
                           totalRoundtrips, lastEventTime, lastUserInteractionTime,
                           automationId, seedId, scheduledExecutionTime, providerId,
                           providerSessionId, createdAt, lastActivity, isActive, endReason,
                           tokensJson, costJson, appStateSnapshot
                    FROM ai_sessions
                """)
                database.execSQL("DROP TABLE ai_sessions")
                database.execSQL("ALTER TABLE ai_sessions_new RENAME TO ai_sessions")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_is_active ON ai_sessions(is_active)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_type ON ai_sessions(type)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_last_activity ON ai_sessions(last_activity)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_automation_id ON ai_sessions(automation_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_phase ON ai_sessions(phase)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_end_reason ON ai_sessions(end_reason)")

                database.execSQL("""
                    CREATE TABLE automations_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        zone_id TEXT NOT NULL,
                        seed_session_id TEXT NOT NULL,
                        schedule_json TEXT,
                        trigger_ids_json TEXT NOT NULL,
                        catch_up_window_minutes INTEGER,
                        dismiss_older_instances INTEGER NOT NULL,
                        provider_id TEXT NOT NULL,
                        is_enabled INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        last_execution_id TEXT,
                        execution_history_json TEXT NOT NULL,
                        `group` TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO automations_new
                    SELECT id, name, zoneId, seedSessionId, scheduleJson, triggerIdsJson,
                           catchUpWindowMinutes, dismissOlderInstances, providerId, isEnabled,
                           createdAt, updatedAt, lastExecutionId, executionHistoryJson, `group`
                    FROM automations
                """)
                database.execSQL("DROP TABLE automations")
                database.execSQL("ALTER TABLE automations_new RENAME TO automations")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_automations_zone_id ON automations(zone_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_automations_is_enabled ON automations(is_enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_automations_seed_session_id ON automations(seed_session_id)")

                database.execSQL("""
                    CREATE TABLE session_messages_new (
                        id TEXT NOT NULL,
                        session_id TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sender TEXT NOT NULL,
                        rich_content_json TEXT,
                        text_content TEXT,
                        ai_message_json TEXT,
                        ai_message_parsed_json TEXT,
                        system_message_json TEXT,
                        execution_metadata_json TEXT,
                        exclude_from_prompt INTEGER NOT NULL,
                        input_tokens INTEGER NOT NULL,
                        cache_write_tokens INTEGER NOT NULL,
                        cache_read_tokens INTEGER NOT NULL,
                        output_tokens INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(session_id) REFERENCES ai_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT INTO session_messages_new
                    SELECT id, sessionId, timestamp, sender, richContentJson, textContent,
                           aiMessageJson, aiMessageParsedJson, systemMessageJson,
                           executionMetadataJson, excludeFromPrompt, inputTokens,
                           cacheWriteTokens, cacheReadTokens, outputTokens
                    FROM session_messages
                """)
                database.execSQL("DROP TABLE session_messages")
                database.execSQL("ALTER TABLE session_messages_new RENAME TO session_messages")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_session_messages_session_id ON session_messages(session_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_session_messages_timestamp ON session_messages(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_session_messages_sender ON session_messages(sender)")

                val cursor = database.query("SELECT (SELECT COUNT(*) FROM ai_sessions), (SELECT COUNT(*) FROM automations), (SELECT COUNT(*) FROM session_messages)")
                val counts = if (cursor.moveToFirst()) "${cursor.getInt(0)} session(s), ${cursor.getInt(1)} automation(s), ${cursor.getInt(2)} message(s)" else "no row"
                cursor.close()

                LogManager.database("MIGRATION 25->26: $counts moved to snake_case columns", "INFO")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The columns are in snake_case; the JSON they hold is not yet. A session's waiting
                // context, a stored AI message, a tool's config and the app settings all carry the
                // old key names, and the code that reads them now looks for the new ones.
                // KeyCaseRenames says what became what, and is the same map the backup import uses.
                val columns = listOf(
                    Triple("ai_sessions", "id", listOf("waiting_context_json", "tokens_json", "cost_json", "app_state_snapshot")),
                    Triple("session_messages", "id", listOf("rich_content_json", "ai_message_json", "ai_message_parsed_json", "system_message_json", "execution_metadata_json")),
                    Triple("automations", "id", listOf("schedule_json")),
                    Triple("tool_instances", "id", listOf("config_json")),
                    Triple("tool_data", "id", listOf("data", "custom_fields")),
                    Triple("app_settings_categories", "category", listOf("settings"))
                )

                var rewritten = 0
                for ((table, key, jsonColumns) in columns) {
                    for (column in jsonColumns) {
                        val cursor = database.query(
                            "SELECT $key, $column FROM $table WHERE $column IS NOT NULL AND $column != ''"
                        )
                        while (cursor.moveToNext()) {
                            val rowKey = cursor.getString(0)
                            val before = cursor.getString(1)
                            val after = try {
                                KeyCaseRenames.rename(before)
                            } catch (e: Exception) {
                                // A column that does not hold JSON, or holds something malformed:
                                // leave it exactly as it is rather than write a guess over it.
                                LogManager.database("MIGRATION 26->27: $table.$column of $rowKey left as is (${e.message})", "WARN")
                                before
                            }
                            if (after != before) {
                                database.execSQL(
                                    "UPDATE $table SET $column = ? WHERE $key = ?",
                                    arrayOf(after, rowKey)
                                )
                                rewritten++
                            }
                        }
                        cursor.close()
                    }
                }

                LogManager.database("MIGRATION 26->27: $rewritten stored JSON value(s) rewritten with snake_case keys", "INFO")
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration 26->27 left behind every document that does not begin with a brace.
                // A model's reply is stored exactly as it came and usually arrives wrapped in a
                // markdown fence, so those rows kept their old key names while the prompt asks
                // for the new ones -- the model then reads its own past replies in one spelling
                // and is told to answer in another. KeyCaseRenames now converts a wrapped
                // document too, so the same columns are passed over again.
                val columns = listOf(
                    Triple("ai_sessions", "id", listOf("waiting_context_json", "tokens_json", "cost_json", "app_state_snapshot")),
                    Triple("session_messages", "id", listOf("rich_content_json", "ai_message_json", "ai_message_parsed_json", "system_message_json", "execution_metadata_json")),
                    Triple("automations", "id", listOf("schedule_json")),
                    Triple("tool_instances", "id", listOf("config_json")),
                    Triple("tool_data", "id", listOf("data", "custom_fields")),
                    Triple("app_settings_categories", "category", listOf("settings"))
                )

                var rewritten = 0
                for ((table, key, jsonColumns) in columns) {
                    for (column in jsonColumns) {
                        val cursor = database.query(
                            "SELECT $key, $column FROM $table WHERE $column IS NOT NULL AND $column != ''"
                        )
                        while (cursor.moveToNext()) {
                            val rowKey = cursor.getString(0)
                            val before = cursor.getString(1)
                            val after = try {
                                KeyCaseRenames.rename(before)
                            } catch (e: Exception) {
                                LogManager.database("MIGRATION 27->28: $table.$column of $rowKey left as is (${e.message})", "WARN")
                                before
                            }
                            if (after != before) {
                                database.execSQL(
                                    "UPDATE $table SET $column = ? WHERE $key = ?",
                                    arrayOf(after, rowKey)
                                )
                                rewritten++
                            }
                        }
                        cursor.close()
                    }
                }

                LogManager.database("MIGRATION 27->28: $rewritten wrapped JSON value(s) rewritten with snake_case keys", "INFO")
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The min/max bounds of a DATE or DATETIME custom field never constrained a
                // value, and they are gone from the schema. They have to leave the stored
                // configs with it: the config schema refuses a property it does not declare, so
                // a config carrying one would stop passing the validation it used to pass.
                var removed = 0
                val cursor = database.query(
                    "SELECT id, config_json FROM tool_instances WHERE config_json IS NOT NULL AND config_json != ''"
                )
                while (cursor.moveToNext()) {
                    val rowId = cursor.getString(0)
                    val before = cursor.getString(1)
                    try {
                        val config = org.json.JSONObject(before)
                        val stripped = DateFieldBounds.strip(config)
                        if (stripped > 0) {
                            database.execSQL(
                                "UPDATE tool_instances SET config_json = ? WHERE id = ?",
                                arrayOf(config.toString(), rowId)
                            )
                            removed += stripped
                        }
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 28->29: tool_instances.config_json of $rowId left as is (${e.message})", "WARN")
                    }
                }
                cursor.close()

                LogManager.database("MIGRATION 28->29: $removed dead date bound(s) removed", "INFO")
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Zones get the icon the zone schema and the prompt always offered, and that
                // nothing could store. Existing zones have none; they show without one until
                // it is chosen.
                database.execSQL("ALTER TABLE zones ADD COLUMN icon_name TEXT")

                var renamed = 0
                val cursor = database.query(
                    "SELECT id, config_json FROM tool_instances WHERE config_json IS NOT NULL AND config_json != ''"
                )
                while (cursor.moveToNext()) {
                    val rowId = cursor.getString(0)
                    try {
                        val config = org.json.JSONObject(cursor.getString(1))
                        if (FormerDefaultIcons.rename(config)) {
                            database.execSQL(
                                "UPDATE tool_instances SET config_json = ? WHERE id = ?",
                                arrayOf(config.toString(), rowId)
                            )
                            renamed++
                        }
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 29->30: tool_instances.config_json of $rowId left as is (${e.message})", "WARN")
                    }
                }
                cursor.close()

                LogManager.database("MIGRATION 29->30: icon_name column added to zones, $renamed former default icon(s) renamed", "INFO")
            }
        }

        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // One field system: see FieldsAtV36. tool_data's custom_fields column becomes
                // extra and the table gains state. SQLite before 3.25 has no RENAME COLUMN and
                // minSdk 26 ships 3.19, so the table is recreated, with the foreign key and the
                // indices Room checks at open.
                database.execSQL("""
                    CREATE TABLE tool_data_new (
                        id TEXT NOT NULL,
                        tool_instance_id TEXT NOT NULL,
                        tooltype TEXT NOT NULL,
                        timestamp INTEGER,
                        name TEXT,
                        data TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        extra TEXT,
                        state TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(tool_instance_id) REFERENCES tool_instances(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT INTO tool_data_new (id, tool_instance_id, tooltype, timestamp, name, data, created_at, updated_at, extra, state)
                    SELECT id, tool_instance_id, tooltype, timestamp, name, data, created_at, updated_at, custom_fields, NULL
                    FROM tool_data
                """)
                database.execSQL("DROP TABLE tool_data")
                database.execSQL("ALTER TABLE tool_data_new RENAME TO tool_data")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_data_tool_instance_id ON tool_data(tool_instance_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_data_timestamp ON tool_data(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_data_tooltype ON tool_data(tooltype)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tool_data_tool_instance_id_timestamp ON tool_data(tool_instance_id, timestamp)")

                // Configs, read before they are rewritten: an entry is rewritten against its
                // tool's config as it stood at v35.
                // A numeric tracking tool's units are read from its entries too.
                val entriesData = mutableMapOf<String, MutableList<org.json.JSONObject>>()
                database.query("SELECT tool_instance_id, data FROM tool_data").use { cursor ->
                    while (cursor.moveToNext()) {
                        try {
                            entriesData.getOrPut(cursor.getString(0)) { mutableListOf() }.add(org.json.JSONObject(cursor.getString(1)))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 35->36: unreadable entry data of tool ${cursor.getString(0)}, its units not read: ${e.message}", "ERROR", e)
                        }
                    }
                }
                val configsAtV35 = mutableMapOf<String, org.json.JSONObject>()
                database.query("SELECT id, tooltype, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A config that cannot be read stays as it was and is logged; its
                        // entries cannot be rewritten against it and stay as they were too.
                        try {
                            val config = org.json.JSONObject(cursor.getString(2))
                            database.execSQL(
                                "UPDATE tool_instances SET config_json = ? WHERE id = ?",
                                arrayOf(FieldsAtV36.config(cursor.getString(1), config, entriesData[id] ?: emptyList()).toString(), id)
                            )
                            configsAtV35[id] = config
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 35->36: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }

                // Entries. A row that cannot be read stays as it was and is logged, never deleted.
                var rewritten = 0
                var failed = 0
                database.query("SELECT id, tool_instance_id, tooltype, name, data, extra FROM tool_data").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        try {
                            val entry = FieldsAtV36.entry(
                                cursor.getString(2),
                                FieldsAtV36.Entry(
                                    name = if (cursor.isNull(3)) null else cursor.getString(3),
                                    data = org.json.JSONObject(cursor.getString(4)),
                                    extra = if (cursor.isNull(5)) null else org.json.JSONObject(cursor.getString(5)),
                                    state = null
                                ),
                                configsAtV35.getValue(cursor.getString(1))
                            )
                            database.execSQL(
                                "UPDATE tool_data SET name = ?, data = ?, extra = ?, state = ? WHERE id = ?",
                                arrayOf(entry.name, entry.data.toString(), entry.extra?.toString(), entry.state?.toString(), id)
                            )
                            rewritten++
                        } catch (e: Exception) {
                            failed++
                            LogManager.database("MIGRATION 35->36: entry $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }

                LogManager.database("MIGRATION 35->36: ${configsAtV35.size} config(s), $rewritten entr(ies) rewritten, $failed left as they were", "INFO")
            }
        }

        /** Stored filters become conditions, {"left", "op", "right"}: see ConditionsAtV52. */
        /**
         * A zone stands at grid_x and grid_y in the grid of its zone group on the home screen,
         * shown in its display_mode: see ZoneGridAtV54. order_index goes; the table is recreated
         * under another name and renamed last, as at 39->40, so the foreign keys onto it stay.
         */
        private val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val mainScreen = database.query("SELECT settings FROM app_settings_categories WHERE category = ?", arrayOf<Any?>(app.treelune.core.database.entities.AppSettingCategories.MAIN_SCREEN)).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                val zones = database.query("SELECT id, `group` FROM zones ORDER BY order_index, rowid").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(ZoneGridAtV54.Zone(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1))) }
                }
                val placed = ZoneGridAtV54.place(zones, ZoneGridAtV54.zoneGroups(mainScreen))

                database.execSQL("""
                    CREATE TABLE zones_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        icon_name TEXT,
                        active INTEGER NOT NULL,
                        display_mode TEXT NOT NULL,
                        grid_x INTEGER NOT NULL,
                        grid_y INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        tool_groups TEXT,
                        `group` TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO zones_new (id, name, description, icon_name, active, display_mode, grid_x, grid_y, created_at, updated_at, tool_groups, `group`)
                    SELECT id, name, description, icon_name, active, '${ZoneGridAtV54.MODE}', 0, 0, created_at, updated_at, tool_groups, `group` FROM zones
                """)
                for ((id, row) in placed) {
                    database.execSQL("UPDATE zones_new SET grid_y = ? WHERE id = ?", arrayOf<Any?>(row, id))
                }
                database.execSQL("DROP TABLE zones")
                database.execSQL("ALTER TABLE zones_new RENAME TO zones")
                LogManager.database("MIGRATION 53->54: ${placed.size} zone(s) placed in their grids", "INFO")
            }
        }

        /**
         * A tool stands at grid_x and grid_y in the grid of its group section, its config holding
         * its display mode: see GridAtV53. order_index goes; no DROP COLUMN before SQLite 3.35,
         * so the table is recreated under another name and renamed last, as zones was at 39->40.
         */
        private val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val zoneGroups = database.query("SELECT id, tool_groups FROM zones").use { cursor ->
                    buildMap { while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1)) }
                }
                val tools = database.query("SELECT id, zone_id, tooltype, config_json FROM tool_instances ORDER BY zone_id, order_index, rowid").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(GridAtV53.Tool(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))) }
                }
                // Every tool needs a place: one whose mode cannot be known fails the migration
                val placed = GridAtV53.place(tools, zoneGroups)

                database.execSQL("""
                    CREATE TABLE tool_instances_new (
                        id TEXT NOT NULL,
                        zone_id TEXT NOT NULL,
                        tooltype TEXT NOT NULL,
                        config_json TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        grid_x INTEGER NOT NULL,
                        grid_y INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(zone_id) REFERENCES zones(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT INTO tool_instances_new (id, zone_id, tooltype, config_json, enabled, grid_x, grid_y, created_at, updated_at)
                    SELECT id, zone_id, tooltype, config_json, enabled, 0, 0, created_at, updated_at FROM tool_instances
                """)
                for ((id, place) in placed) {
                    database.execSQL(
                        "UPDATE tool_instances_new SET config_json = ?, grid_x = ?, grid_y = ? WHERE id = ?",
                        arrayOf<Any?>(place.configJson, place.gridX, place.gridY, id)
                    )
                }
                database.execSQL("DROP TABLE tool_instances")
                database.execSQL("ALTER TABLE tool_instances_new RENAME TO tool_instances")
                LogManager.database("MIGRATION 52->53: ${placed.size} tool(s) placed in their grids", "INFO")
            }
        }

        private val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // A row that cannot be read stays as it was and is logged
                var rewritten = 0
                database.query("SELECT id, rich_content_json FROM session_messages WHERE rich_content_json LIKE '%POINTER%'").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        try {
                            ConditionsAtV52.richContent(cursor.getString(1))?.let { next ->
                                database.execSQL("UPDATE session_messages SET rich_content_json = ? WHERE id = ?", arrayOf<Any?>(next, id))
                                rewritten++
                            }
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 51->52: pointers of message $id left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                database.query("SELECT id, definition_json FROM variables").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        try {
                            ConditionsAtV52.definition(cursor.getString(1))?.let { next ->
                                database.execSQL("UPDATE variables SET definition_json = ? WHERE id = ?", arrayOf<Any?>(next, id))
                                rewritten++
                            }
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 51->52: terms of variable $id left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 51->52: $rewritten message(s) and variable(s) with their filters as conditions", "INFO")
            }
        }

        /** A schedule is its pattern alone, without start and end dates: see ScheduleDatesAtV51. */
        private val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // A row that cannot be read stays as it was and is logged
                var rewritten = 0
                database.query("SELECT id, schedule_json FROM automations WHERE schedule_json IS NOT NULL AND schedule_json != ''").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        try {
                            database.execSQL("UPDATE automations SET schedule_json = ? WHERE id = ?",
                                arrayOf<Any?>(ScheduleDatesAtV51.schedule(cursor.getString(1)), id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 50->51: schedule of automation $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                database.query("SELECT id, tooltype, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val tooltype = cursor.getString(1)
                        if (tooltype !in ScheduleDatesAtV51.SCHEDULED_TOOLTYPES) continue
                        try {
                            database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?",
                                arrayOf<Any?>(ScheduleDatesAtV51.toolConfig(tooltype, cursor.getString(2)), id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 50->51: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 50->51: $rewritten schedule(s) without start and end dates", "INFO")
            }
        }

        /** Files joined to messages, kept with their session: see AttachedFileEntity. */
        private val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS attached_files (
                        id TEXT NOT NULL,
                        session_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        mime_type TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        line_count INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(session_id) REFERENCES ai_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_attached_files_session_id ON attached_files(session_id)")
                LogManager.database("MIGRATION 49->50: attached_files table created", "INFO")
            }
        }

        /** The interface settings gain the appearance and the size step: see UiAppearanceAtV55. */
        private val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.query("SELECT category, settings FROM app_settings_categories").use { cursor ->
                    while (cursor.moveToNext()) {
                        val category = cursor.getString(0)
                        // A row that cannot be read stays as it was and is logged
                        try {
                            val settings = UiAppearanceAtV55.settings(category, org.json.JSONObject(cursor.getString(1)))
                            database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 54->55: settings of $category left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 54->55: interface appearance and size step added", "INFO")
            }
        }

        /** The size step counts from the former −1: see UiSizeStepAtV56. */
        private val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.query("SELECT category, settings FROM app_settings_categories").use { cursor ->
                    while (cursor.moveToNext()) {
                        val category = cursor.getString(0)
                        // A row that cannot be read stays as it was and is logged
                        try {
                            val settings = UiSizeStepAtV56.settings(category, org.json.JSONObject(cursor.getString(1)))
                            database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 55->56: settings of $category left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 55->56: size step moved up by one", "INFO")
            }
        }

        /** Images joined to messages, kept with their session: see AttachedImageEntity. */
        private val MIGRATION_58_59 = object : Migration(58, 59) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS attached_images (
                        id TEXT NOT NULL,
                        session_id TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(session_id) REFERENCES ai_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_attached_images_session_id ON attached_images(session_id)")
                LogManager.database("MIGRATION 58->59: attached_images table created", "INFO")
            }
        }

        /** The MCP server's OAuth clients and tokens (docs/design/mcp-server.md): two new tables, empty. */
        private val MIGRATION_59_60 = object : Migration(59, 60) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcp_clients (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        redirect_uris TEXT NOT NULL,
                        secret_hash TEXT,
                        created_at INTEGER NOT NULL,
                        last_used_at INTEGER,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcp_tokens (
                        hash TEXT NOT NULL,
                        client_id TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        expires_at INTEGER NOT NULL,
                        PRIMARY KEY(hash),
                        FOREIGN KEY(client_id) REFERENCES mcp_clients(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mcp_tokens_client_id ON mcp_tokens(client_id)")
                LogManager.database("MIGRATION 59->60: mcp_clients and mcp_tokens tables created", "INFO")
            }
        }

        /** A group held is null or one that exists: see GroupsAtV62. */
        private val MIGRATION_61_62 = object : Migration(61, 62) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val homeGroups = GroupsAtV62.zoneGroups(
                    database.query("SELECT settings FROM app_settings_categories WHERE category = ?", arrayOf<Any?>(app.treelune.core.database.entities.AppSettingCategories.MAIN_SCREEN)).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                )

                // The zones' groups, and the tool groups each zone has
                var emptied = 0
                val toolGroups = mutableMapOf<String, List<String>>()
                database.query("SELECT id, `group`, tool_groups FROM zones").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val group = if (cursor.isNull(1)) null else cursor.getString(1)
                        if (group != null && GroupsAtV62.kept(group, homeGroups) == null) {
                            database.execSQL("UPDATE zones SET `group` = NULL WHERE id = ?", arrayOf<Any?>(id))
                            emptied++
                        }
                        // A zone whose tool groups cannot be read leaves its tools, automations and
                        // variables as they were, and is logged
                        try {
                            toolGroups[id] = GroupsAtV62.toolGroups(if (cursor.isNull(2)) null else cursor.getString(2))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 61->62: tool groups of zone $id unreadable, its members left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }

                database.query("SELECT id, zone_id, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A config that cannot be read stays as it was and is logged
                        try {
                            val config = org.json.JSONObject(cursor.getString(2))
                            val groups = toolGroups[cursor.getString(1)] ?: continue
                            val kept = GroupsAtV62.toolConfig(config, groups)
                            if (kept.has("group") != config.has("group")) {
                                database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?", arrayOf<Any?>(kept.toString(), id))
                                emptied++
                            }
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 61->62: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }

                for (table in listOf("automations", "variables")) {
                    database.query("SELECT id, zone_id, `group` FROM $table WHERE `group` IS NOT NULL").use { cursor ->
                        while (cursor.moveToNext()) {
                            val groups = toolGroups[cursor.getString(1)] ?: continue
                            if (GroupsAtV62.kept(cursor.getString(2), groups) == null) {
                                database.execSQL("UPDATE $table SET `group` = NULL WHERE id = ?", arrayOf<Any?>(cursor.getString(0)))
                                emptied++
                            }
                        }
                    }
                }
                LogManager.database("MIGRATION 61->62: $emptied group(s) that did not exist emptied", "INFO")
            }
        }

        /**
         * Validation by levels (docs/design/validation.md, ValidationAtV69): the app's settings keep
         * one switch, a zone gains its own (on when one of its tools had its config validated), a
         * tool loses validate_config and management, a session's switch becomes three boxes. SQLite
         * before 3.35 cannot drop a column: ai_sessions is rebuilt.
         */
        private val MIGRATION_68_69 = object : Migration(68, 69) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val category = app.treelune.core.database.entities.AppSettingCategories.VALIDATION_CONFIG
                database.query("SELECT settings FROM app_settings_categories WHERE category = ?", arrayOf<Any?>(category)).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        LogManager.database("MIGRATION 68->69: no validation settings stored, the defaults are written on first read", "INFO")
                    } else {
                        // Settings that cannot be read stay as they are; reading them then fails with the reason
                        try {
                            val settings = app.treelune.core.versioning.ValidationAtV69.appSettings(category, org.json.JSONObject(cursor.getString(0)))
                            database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 68->69: validation settings left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }

                database.execSQL("ALTER TABLE zones ADD COLUMN validate INTEGER NOT NULL DEFAULT 0")
                val protectedZones = mutableSetOf<String>()
                var rewritten = 0
                database.query("SELECT id, zone_id, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        try {
                            val config = org.json.JSONObject(cursor.getString(2))
                            if (app.treelune.core.versioning.ValidationAtV69.protectsZone(config)) protectedZones.add(cursor.getString(1))
                            database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?",
                                arrayOf<Any?>(app.treelune.core.versioning.ValidationAtV69.toolConfig(config).toString(), id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 68->69: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                protectedZones.forEach { database.execSQL("UPDATE zones SET validate = 1 WHERE id = ?", arrayOf<Any?>(it)) }

                database.execSQL("""
                    CREATE TABLE ai_sessions_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        validate_app INTEGER NOT NULL,
                        validate_zones INTEGER NOT NULL,
                        validate_data INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        total_roundtrips INTEGER NOT NULL,
                        last_event_time INTEGER NOT NULL,
                        last_user_interaction_time INTEGER NOT NULL,
                        automation_id TEXT,
                        scheduled_execution_time INTEGER,
                        provider_id TEXT NOT NULL,
                        provider_session_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_activity INTEGER NOT NULL,
                        is_active INTEGER NOT NULL,
                        end_reason TEXT,
                        app_state_snapshot TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO ai_sessions_new
                    SELECT id, name, type, require_validation, require_validation, require_validation, phase,
                           total_roundtrips, last_event_time, last_user_interaction_time, automation_id,
                           scheduled_execution_time, provider_id, provider_session_id, created_at,
                           last_activity, is_active, end_reason, app_state_snapshot
                    FROM ai_sessions
                """)
                // session_messages references ai_sessions and needs nothing: foreign keys are not
                // enforced while a migration runs, and the name stays the same
                database.execSQL("DROP TABLE ai_sessions")
                database.execSQL("ALTER TABLE ai_sessions_new RENAME TO ai_sessions")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_is_active ON ai_sessions(is_active)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_type ON ai_sessions(type)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_last_activity ON ai_sessions(last_activity)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_automation_id ON ai_sessions(automation_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_phase ON ai_sessions(phase)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_end_reason ON ai_sessions(end_reason)")

                LogManager.database("MIGRATION 68->69: validation by levels, $rewritten tool config(s) rewritten, ${protectedZones.size} zone(s) protected, session switches split", "INFO")
            }
        }

        /** The interface settings gain « One column », off: see UiOneColumnAtV67. */
        private val MIGRATION_66_67 = object : Migration(66, 67) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.query("SELECT settings FROM app_settings_categories WHERE category = ?", arrayOf<Any?>(app.treelune.core.database.entities.AppSettingCategories.UI)).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        LogManager.database("MIGRATION 66->67: no interface settings stored, the defaults are written on first read", "INFO")
                        return
                    }
                    // Settings that cannot be read stay as they are; reading them then fails with the reason
                    try {
                        val settings = app.treelune.core.versioning.UiOneColumnAtV67.rewrite(app.treelune.core.database.entities.AppSettingCategories.UI, org.json.JSONObject(cursor.getString(0)))
                        database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), app.treelune.core.database.entities.AppSettingCategories.UI))
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 66->67: interface settings left as they were: ${e.message}", "ERROR", e)
                        return
                    }
                }
                LogManager.database("MIGRATION 66->67: interface settings gain one_column, off", "INFO")
            }
        }

        /** The external access settings gain the access mode: see ExternalAccessModeAtV68. */
        private val MIGRATION_67_68 = object : Migration(67, 68) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val category = app.treelune.core.database.entities.AppSettingCategories.EXTERNAL_ACCESS
                database.query("SELECT settings FROM app_settings_categories WHERE category = ?", arrayOf<Any?>(category)).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        LogManager.database("MIGRATION 67->68: no external access settings stored, the defaults are written on first read", "INFO")
                        return
                    }
                    // Settings that cannot be read stay as they are; reading them then fails with the reason
                    try {
                        val settings = app.treelune.core.versioning.ExternalAccessModeAtV68.rewrite(category, org.json.JSONObject(cursor.getString(0)))
                        database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        LogManager.database("MIGRATION 67->68: external access mode set to ${settings.optString(app.treelune.core.config.AppSettings.ACCESS_MODE)}", "INFO")
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 67->68: external access settings left as they were: ${e.message}", "ERROR", e)
                    }
                }
            }
        }

        /**
         * An MCP token names the refresh token its pair was handed out for, until the pair is used
         * (StoredToken.replaces): a new column, empty, every token held replacing nothing.
         */
        private val MIGRATION_65_66 = object : Migration(65, 66) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE mcp_tokens ADD COLUMN replaces TEXT")
                LogManager.database("MIGRATION 65->66: mcp_tokens.replaces added", "INFO")
            }
        }

        /** Every questionnaire entry has its status: one with none is filled (QuestionnaireStateAtV65). */
        private val MIGRATION_64_65 = object : Migration(64, 65) {
            override fun migrate(database: SupportSQLiteDatabase) {
                var given = 0
                database.query("SELECT id, state FROM tool_data WHERE tooltype = ?", arrayOf<Any?>(app.treelune.core.versioning.QuestionnaireStateAtV65.TOOLTYPE)).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        try {
                            val state = if (cursor.isNull(1)) null else cursor.getString(1)
                            app.treelune.core.versioning.QuestionnaireStateAtV65.state(app.treelune.core.versioning.QuestionnaireStateAtV65.TOOLTYPE, state)?.let {
                                database.execSQL("UPDATE tool_data SET state = ? WHERE id = ?", arrayOf<Any?>(it, id))
                                given++
                            }
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 64->65: state of entry $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 64->65: $given questionnaire entr(ies) without a status now filled", "INFO")
            }
        }

        /** A zone's icon colour (docs/design/icon-colors.md): a new column, empty, every icon staying neutral. */
        private val MIGRATION_63_64 = object : Migration(63, 64) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ai_limits gains the threshold of the tools sent always: see AILimitsAtV64.
                // Settings that cannot be read are left as they are; reading them then fails with the reason.
                database.query("SELECT settings FROM app_settings_categories WHERE category = 'ai_limits'").use { cursor ->
                    if (!cursor.moveToFirst()) {
                        LogManager.database("MIGRATION 63->64: no ai_limits stored, the defaults are written on first read", "INFO")
                        return
                    }
                    try {
                        val rewritten = AILimitsAtV64.rewrite(org.json.JSONObject(cursor.getString(0)))
                        database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = 'ai_limits'", arrayOf(rewritten.toString()))
                        LogManager.database("MIGRATION 63->64: ai_limits rewritten to $rewritten", "INFO")
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 63->64: ai_limits left as is (${e.message})", "ERROR")
                    }
                }
            }
        }

        private val MIGRATION_62_63 = object : Migration(62, 63) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE zones ADD COLUMN icon_color TEXT")
                LogManager.database("MIGRATION 62->63: zones.icon_color added", "INFO")
            }
        }

        /** The former text types left in users' fields become a TEXT with its length: see TextLengthAtV61. */
        private val MIGRATION_60_61 = object : Migration(60, 61) {
            override fun migrate(database: SupportSQLiteDatabase) {
                var rewritten = 0
                database.query("SELECT id, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A config that cannot be read stays as it was and is logged
                        try {
                            val config = TextLengthAtV61.config(org.json.JSONObject(cursor.getString(1)))
                            database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?", arrayOf(config.toString(), id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 60->61: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 60->61: $rewritten config(s) rewritten", "INFO")
            }
        }

        /** A theme's palette family gives way to a hue shift: see UiHueShiftAtV58. */
        private val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.query("SELECT category, settings FROM app_settings_categories").use { cursor ->
                    while (cursor.moveToNext()) {
                        val category = cursor.getString(0)
                        // A row that cannot be read stays as it was and is logged
                        try {
                            val settings = UiHueShiftAtV58.settings(category, org.json.JSONObject(cursor.getString(1)))
                            database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 57->58: settings of $category left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 57->58: palette family replaced by a hue shift", "INFO")
            }
        }

        /** The appearance becomes a theme, a palette family and a mode: see UiThemeModeAtV57. */
        private val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.query("SELECT category, settings FROM app_settings_categories").use { cursor ->
                    while (cursor.moveToNext()) {
                        val category = cursor.getString(0)
                        // A row that cannot be read stays as it was and is logged
                        try {
                            val settings = UiThemeModeAtV57.settings(category, org.json.JSONObject(cursor.getString(1)))
                            database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 56->57: settings of $category left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 56->57: appearance split into theme, palette and mode", "INFO")
            }
        }

        /** The AI's changes to variables get their own validation switch: see VariableValidationAtV49. */
        private val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.query("SELECT category, settings FROM app_settings_categories").use { cursor ->
                    while (cursor.moveToNext()) {
                        val category = cursor.getString(0)
                        // A row that cannot be read stays as it was and is logged
                        try {
                            val settings = VariableValidationAtV49.settings(category, org.json.JSONObject(cursor.getString(1)))
                            database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 48->49: settings of $category left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 48->49: variables' validation switch added", "INFO")
            }
        }

        /**
         * A chat opened prefilled takes its content as the draft of its composer: the seed a
         * session pointed to goes. SQLite before 3.35 cannot drop a column, so the table is rebuilt.
         */
        private val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE ai_sessions_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        require_validation INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        total_roundtrips INTEGER NOT NULL,
                        last_event_time INTEGER NOT NULL,
                        last_user_interaction_time INTEGER NOT NULL,
                        automation_id TEXT,
                        scheduled_execution_time INTEGER,
                        provider_id TEXT NOT NULL,
                        provider_session_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_activity INTEGER NOT NULL,
                        is_active INTEGER NOT NULL,
                        end_reason TEXT,
                        app_state_snapshot TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO ai_sessions_new
                    SELECT id, name, type, require_validation, phase, total_roundtrips,
                           last_event_time, last_user_interaction_time, automation_id,
                           scheduled_execution_time, provider_id, provider_session_id, created_at,
                           last_activity, is_active, end_reason, app_state_snapshot
                    FROM ai_sessions
                """)
                // session_messages references ai_sessions and needs nothing: foreign keys are not
                // enforced during a migration (see 34 -> 35, rebuilt the same way)
                database.execSQL("DROP TABLE ai_sessions")
                database.execSQL("ALTER TABLE ai_sessions_new RENAME TO ai_sessions")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_is_active ON ai_sessions(is_active)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_type ON ai_sessions(type)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_last_activity ON ai_sessions(last_activity)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_automation_id ON ai_sessions(automation_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_phase ON ai_sessions(phase)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_end_reason ON ai_sessions(end_reason)")
                LogManager.database("MIGRATION 47->48: seed_id dropped from ai_sessions", "INFO")
            }
        }

        /** The variables of the core, each in a zone and deleted with it, named once in the app. */
        private val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS variables (
                        id TEXT NOT NULL,
                        zone_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        `group` TEXT,
                        order_index INTEGER NOT NULL,
                        definition_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(zone_id) REFERENCES zones(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_variables_zone_id ON variables(zone_id)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_variables_name ON variables(name)")
                LogManager.database("MIGRATION 46->47: variables table created", "INFO")
            }
        }

        /**
         * A pointer holds a selection of the core, its period apart and its relative dates as
         * objects with their edge: see PointerAtV46. Which field is a date is read from the
         * tool's config, hence the context.
         */
        private fun migration45to46(context: Context) = object : Migration(45, 46) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val fieldTypes = { toolInstanceId: String ->
                    database.query("SELECT tooltype, config_json FROM tool_instances WHERE id = ?", arrayOf<Any?>(toolInstanceId)).use { cursor ->
                        if (cursor.moveToFirst()) PointerAtV46.fieldTypes(cursor.getString(0), cursor.getString(1), context) else null
                    }
                }
                var rewritten = 0
                database.query("SELECT id, rich_content_json FROM session_messages WHERE rich_content_json LIKE '%POINTER%'").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A message or a pointer that cannot be read stays as it was and is logged
                        try {
                            val next = PointerAtV46.richContent(cursor.getString(1), fieldTypes) { e ->
                                LogManager.database("MIGRATION 45->46: a pointer of message $id left as it was: ${e.message}", "ERROR", e)
                            } ?: continue
                            database.execSQL("UPDATE session_messages SET rich_content_json = ? WHERE id = ?", arrayOf<Any?>(next, id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 45->46: message $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 45->46: $rewritten message(s) with their pointers as selections", "INFO")
            }
        }

        private val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // A message stores its segments alone, their texts written when read: see EnrichmentTextAtV45
                var rewritten = 0
                database.query("SELECT id, rich_content_json FROM session_messages WHERE rich_content_json IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A message that cannot be read stays as it was and is logged
                        try {
                            val next = EnrichmentTextAtV45.richContent(cursor.getString(1)) ?: continue
                            database.execSQL("UPDATE session_messages SET rich_content_json = ? WHERE id = ?", arrayOf<Any?>(next, id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 44->45: message $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 44->45: $rewritten message(s) without their stored texts", "INFO")
            }
        }

        private val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // A pointer names its target by id and its period as filters: see PointerAtV44
                val format = database.query("SELECT settings FROM app_settings_categories WHERE category = ?", arrayOf<Any?>(app.treelune.core.database.entities.AppSettingCategories.FORMAT)).use { cursor ->
                    if (cursor.moveToFirst()) org.json.JSONObject(cursor.getString(0)) else null
                }
                val calendar = PointerAtV44.Calendar.of(format)
                var rewritten = 0
                database.query("SELECT id, rich_content_json FROM session_messages WHERE rich_content_json LIKE '%POINTER%'").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A message or a pointer that cannot be read stays as it was and is logged
                        try {
                            val next = PointerAtV44.richContent(cursor.getString(1), calendar) { e ->
                                LogManager.database("MIGRATION 43->44: a pointer of message $id left as it was: ${e.message}", "ERROR", e)
                            } ?: continue
                            database.execSQL("UPDATE session_messages SET rich_content_json = ? WHERE id = ?", arrayOf<Any?>(next, id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 43->44: message $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 43->44: $rewritten message(s) with their pointers rewritten", "INFO")
            }
        }

        private val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // A numeric tracking tool's units live in "units" alone: see TrackingUnitAtV43
                val units = mutableMapOf<String, String>()
                database.query("SELECT id, config_json FROM tool_instances WHERE tooltype = 'tracking'").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A config that cannot be read stays as it was and is logged
                        try {
                            val config = org.json.JSONObject(cursor.getString(1))
                            TrackingUnitAtV43.valueUnit("tracking", config)?.let { units[id] = it }
                            database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?",
                                arrayOf<Any?>(TrackingUnitAtV43.config("tracking", config).toString(), id))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 42->43: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                var entries = 0
                units.forEach { (toolId, unit) ->
                    database.query("SELECT id, data FROM tool_data WHERE tool_instance_id = ?", arrayOf<Any?>(toolId)).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(0)
                            // An entry that cannot be read stays as it was and is logged
                            try {
                                val data = TrackingUnitAtV43.entryData(org.json.JSONObject(cursor.getString(1)), unit)
                                database.execSQL("UPDATE tool_data SET data = ? WHERE id = ?", arrayOf<Any?>(data.toString(), id))
                                entries++
                            } catch (e: Exception) {
                                LogManager.database("MIGRATION 42->43: entry $id left as it was: ${e.message}", "ERROR", e)
                            }
                        }
                    }
                }
                LogManager.database("MIGRATION 42->43: ${units.size} tracking unit(s) moved to their units, $entries entry(ies) rewritten", "INFO")
            }
        }

        private val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The format settings say "follow the phone" by an absence: see FormatNullsAtV42
                database.query("SELECT category, settings FROM app_settings_categories").use { cursor ->
                    while (cursor.moveToNext()) {
                        val category = cursor.getString(0)
                        // A row that cannot be read stays as it was and is logged
                        try {
                            val settings = FormatNullsAtV42.settings(category, org.json.JSONObject(cursor.getString(1)))
                            database.execSQL("UPDATE app_settings_categories SET settings = ? WHERE category = ?", arrayOf<Any?>(settings.toString(), category))
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 41->42: settings of $category left as they were: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 41->42: format settings without nulls", "INFO")
            }
        }

        private val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // A scheduled automation stores its explicit catch-up choice, and a limited window
                // in milliseconds: see CatchUpAtV41. No RENAME COLUMN before SQLite 3.25 and
                // minSdk 26 ships 3.19, so the table is recreated with its indices.
                database.execSQL("""
                    CREATE TABLE automations_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        zone_id TEXT NOT NULL,
                        seed_session_id TEXT NOT NULL,
                        schedule_json TEXT,
                        trigger_ids_json TEXT NOT NULL,
                        catch_up TEXT,
                        catch_up_window INTEGER,
                        dismiss_older_instances INTEGER NOT NULL,
                        provider_id TEXT NOT NULL,
                        is_enabled INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        last_execution_id TEXT,
                        execution_history_json TEXT NOT NULL,
                        `group` TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO automations_new (id, name, zone_id, seed_session_id, schedule_json, trigger_ids_json,
                        dismiss_older_instances, provider_id, is_enabled, created_at, updated_at, last_execution_id,
                        execution_history_json, `group`)
                    SELECT id, name, zone_id, seed_session_id, schedule_json, trigger_ids_json,
                        dismiss_older_instances, provider_id, is_enabled, created_at, updated_at, last_execution_id,
                        execution_history_json, `group`
                    FROM automations
                """)
                var converted = 0
                database.query("SELECT id, schedule_json, catch_up_window_minutes FROM automations").use { cursor ->
                    while (cursor.moveToNext()) {
                        val scheduled = !cursor.isNull(1)
                        val minutes = if (cursor.isNull(2)) null else cursor.getLong(2)
                        val (choice, window) = CatchUpAtV41.of(scheduled, minutes)
                        database.execSQL("UPDATE automations_new SET catch_up = ?, catch_up_window = ? WHERE id = ?",
                            arrayOf<Any?>(choice, window, cursor.getString(0)))
                        if (choice != null) converted++
                    }
                }
                database.execSQL("DROP TABLE automations")
                database.execSQL("ALTER TABLE automations_new RENAME TO automations")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_automations_zone_id ON automations(zone_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_automations_is_enabled ON automations(is_enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_automations_seed_session_id ON automations(seed_session_id)")
                LogManager.database("MIGRATION 40->41: $converted scheduled automation(s) given their catch-up choice", "INFO")
            }
        }

        private val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // zones loses color, which nothing wrote nor showed. No DROP COLUMN before SQLite
                // 3.35 and minSdk 26 ships 3.19, so the table is recreated. The new one is created
                // under another name and renamed last: renaming the old one instead would carry
                // the foreign keys of tool_instances and the others over to it.
                database.execSQL("""
                    CREATE TABLE zones_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        icon_name TEXT,
                        active INTEGER NOT NULL,
                        order_index INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        tool_groups TEXT,
                        `group` TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO zones_new (id, name, description, icon_name, active, order_index, created_at, updated_at, tool_groups, `group`)
                    SELECT id, name, description, icon_name, active, order_index, created_at, updated_at, tool_groups, `group` FROM zones
                """)
                database.execSQL("DROP TABLE zones")
                database.execSQL("ALTER TABLE zones_new RENAME TO zones")

                val count = database.query("SELECT COUNT(*) FROM zones").use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                LogManager.database("MIGRATION 39->40: $count zone(s) moved to a table without color", "INFO")
            }
        }

        private val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Tool configs take the form their declaration describes: see ToolConfigsAtV39
                var rewritten = 0
                database.query("SELECT id, tooltype, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A config that cannot be read stays as it was and is logged
                        try {
                            val config = ToolConfigsAtV39.config(cursor.getString(1), org.json.JSONObject(cursor.getString(2)))
                            database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?", arrayOf(config.toString(), id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 38->39: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 38->39: $rewritten config(s) rewritten", "INFO")
            }
        }

        private val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Every NUMERIC says its decimals: see NumericDecimalsAtV38. Configs only.
                var rewritten = 0
                database.query("SELECT id, tooltype, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A config that cannot be read stays as it was and is logged
                        try {
                            val config = NumericDecimalsAtV38.config(cursor.getString(1), org.json.JSONObject(cursor.getString(2)))
                            database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?", arrayOf(config.toString(), id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 37->38: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 37->38: $rewritten config(s) rewritten", "INFO")
            }
        }

        private val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // A CHOICE option becomes a group holding its value, label and color: see
                // ChoiceOptionsAtV37. Only configs change; entries keep the option's value.
                var rewritten = 0
                database.query("SELECT id, tooltype, config_json FROM tool_instances").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        // A config that cannot be read stays as it was and is logged
                        try {
                            val config = ChoiceOptionsAtV37.config(cursor.getString(1), org.json.JSONObject(cursor.getString(2)))
                            database.execSQL("UPDATE tool_instances SET config_json = ? WHERE id = ?", arrayOf(config.toString(), id))
                            rewritten++
                        } catch (e: Exception) {
                            LogManager.database("MIGRATION 36->37: config of tool $id left as it was: ${e.message}", "ERROR", e)
                        }
                    }
                }
                LogManager.database("MIGRATION 36->37: $rewritten config(s) rewritten", "INFO")
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Each AI message keeps the model and prices of its call. Messages from before
                // have none: their prices stay unknown rather than today's applied after the fact.
                database.execSQL("ALTER TABLE session_messages ADD COLUMN model_id TEXT")
                database.execSQL("ALTER TABLE session_messages ADD COLUMN input_price REAL")
                database.execSQL("ALTER TABLE session_messages ADD COLUMN cache_write_price REAL")
                database.execSQL("ALTER TABLE session_messages ADD COLUMN cache_read_price REAL")
                database.execSQL("ALTER TABLE session_messages ADD COLUMN output_price REAL")
                // Marks a call cut or lost after its request went out
                database.execSQL("ALTER TABLE session_messages ADD COLUMN usage_unknown INTEGER NOT NULL DEFAULT 0")

                // A session's tokens and cost are summed from its messages when read: the stored
                // totals go. SQLite before 3.35 cannot drop a column, so the table is rebuilt.
                database.execSQL("""
                    CREATE TABLE ai_sessions_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        require_validation INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        total_roundtrips INTEGER NOT NULL,
                        last_event_time INTEGER NOT NULL,
                        last_user_interaction_time INTEGER NOT NULL,
                        automation_id TEXT,
                        seed_id TEXT,
                        scheduled_execution_time INTEGER,
                        provider_id TEXT NOT NULL,
                        provider_session_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_activity INTEGER NOT NULL,
                        is_active INTEGER NOT NULL,
                        end_reason TEXT,
                        app_state_snapshot TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO ai_sessions_new
                    SELECT id, name, type, require_validation, phase, total_roundtrips,
                           last_event_time, last_user_interaction_time, automation_id, seed_id,
                           scheduled_execution_time, provider_id, provider_session_id, created_at,
                           last_activity, is_active, end_reason, app_state_snapshot
                    FROM ai_sessions
                """)
                // session_messages references ai_sessions and needs nothing: foreign keys are not
                // enforced during a migration (see 30 -> 31, rebuilt the same way)
                database.execSQL("DROP TABLE ai_sessions")
                database.execSQL("ALTER TABLE ai_sessions_new RENAME TO ai_sessions")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_is_active ON ai_sessions(is_active)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_type ON ai_sessions(type)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_last_activity ON ai_sessions(last_activity)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_automation_id ON ai_sessions(automation_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_phase ON ai_sessions(phase)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_end_reason ON ai_sessions(end_reason)")

                LogManager.database("MIGRATION 34->35: prices and usage mark on session_messages, stored totals dropped from ai_sessions", "INFO")
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ai_limits gains the two data size thresholds: see AILimitsAtV34. Settings that
                // cannot be read are left as they are; reading them then fails with the reason.
                val cursor = database.query(
                    "SELECT settings FROM app_settings_categories WHERE category = 'ai_limits'"
                )
                if (cursor.moveToFirst()) {
                    try {
                        val rewritten = AILimitsAtV34.rewrite(org.json.JSONObject(cursor.getString(0)))
                        database.execSQL(
                            "UPDATE app_settings_categories SET settings = ? WHERE category = 'ai_limits'",
                            arrayOf(rewritten.toString())
                        )
                        LogManager.database("MIGRATION 33->34: ai_limits rewritten to $rewritten", "INFO")
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 33->34: ai_limits left as is (${e.message})", "ERROR")
                    }
                } else {
                    LogManager.database("MIGRATION 33->34: no ai_limits stored, the defaults are written on first read", "INFO")
                }
                cursor.close()
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // format, validation_config and main_screen get the required keys they lack and
                // lose the ones nothing declares: see SettingsAtV33. A row that cannot be read is
                // left as it is; reading it then fails with the reason, instead of the migration
                // replacing it.
                val rows = mutableListOf<Pair<String, String>>()
                val cursor = database.query("SELECT category, settings FROM app_settings_categories")
                while (cursor.moveToNext()) rows += cursor.getString(0) to cursor.getString(1)
                cursor.close()

                for ((category, settings) in rows) {
                    try {
                        val rewritten = SettingsAtV33.rewrite(category, org.json.JSONObject(settings)) ?: continue
                        database.execSQL(
                            "UPDATE app_settings_categories SET settings = ? WHERE category = ?",
                            arrayOf(rewritten.toString(), category)
                        )
                        LogManager.database("MIGRATION 32->33: $category rewritten to $rewritten", "INFO")
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 32->33: $category left as is (${e.message})", "ERROR")
                    }
                }
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ai_limits keeps the two roundtrip limits alone, the CHAT one set to 10: see
                // AILimitsAtV32. Settings that cannot be read are left as they are; reading
                // them at startup then fails with the reason, instead of the migration
                // replacing them.
                val cursor = database.query(
                    "SELECT settings FROM app_settings_categories WHERE category = 'ai_limits'"
                )
                if (cursor.moveToFirst()) {
                    try {
                        val rewritten = AILimitsAtV32.rewrite(org.json.JSONObject(cursor.getString(0)))
                        database.execSQL(
                            "UPDATE app_settings_categories SET settings = ? WHERE category = 'ai_limits'",
                            arrayOf(rewritten.toString())
                        )
                        LogManager.database("MIGRATION 31->32: ai_limits rewritten to $rewritten", "INFO")
                    } catch (e: Exception) {
                        LogManager.database("MIGRATION 31->32: ai_limits left as is (${e.message})", "ERROR")
                    }
                } else {
                    LogManager.database("MIGRATION 31->32: no ai_limits stored, the defaults are written on first read", "INFO")
                }
                cursor.close()
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // waiting_context_json goes: the waiting context derives from the last AI message
                // and is rebuilt on entering a waiting phase, and nothing ever read the column.
                // SQLite before 3.35 cannot drop a column and minSdk 26 ships 3.19, so the table is
                // recreated with its indices, its definition as Room generates it. session_messages
                // references ai_sessions by name and needs nothing: foreign keys are not enforced
                // during a migration -- the messages kept since 2025 went through 25 -> 26, which
                // dropped this table the same way.
                database.execSQL("""
                    CREATE TABLE ai_sessions_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        require_validation INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        total_roundtrips INTEGER NOT NULL,
                        last_event_time INTEGER NOT NULL,
                        last_user_interaction_time INTEGER NOT NULL,
                        automation_id TEXT,
                        seed_id TEXT,
                        scheduled_execution_time INTEGER,
                        provider_id TEXT NOT NULL,
                        provider_session_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_activity INTEGER NOT NULL,
                        is_active INTEGER NOT NULL,
                        end_reason TEXT,
                        tokens_json TEXT,
                        cost_json TEXT,
                        app_state_snapshot TEXT,
                        PRIMARY KEY(id)
                    )
                """)
                database.execSQL("""
                    INSERT INTO ai_sessions_new
                    SELECT id, name, type, require_validation, phase, total_roundtrips,
                           last_event_time, last_user_interaction_time, automation_id, seed_id,
                           scheduled_execution_time, provider_id, provider_session_id, created_at,
                           last_activity, is_active, end_reason, tokens_json, cost_json,
                           app_state_snapshot
                    FROM ai_sessions
                """)
                database.execSQL("DROP TABLE ai_sessions")
                database.execSQL("ALTER TABLE ai_sessions_new RENAME TO ai_sessions")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_is_active ON ai_sessions(is_active)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_type ON ai_sessions(type)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_last_activity ON ai_sessions(last_activity)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_automation_id ON ai_sessions(automation_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_phase ON ai_sessions(phase)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sessions_end_reason ON ai_sessions(end_reason)")

                LogManager.database("MIGRATION 30->31: waiting_context_json dropped from ai_sessions", "INFO")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "treelune_database"
                )
                .addMigrations(
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                    MIGRATION_24_25,
                    MIGRATION_25_26,
                    MIGRATION_26_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                    MIGRATION_29_30,
                    MIGRATION_30_31,
                    MIGRATION_31_32,
                    MIGRATION_32_33,
                    MIGRATION_33_34,
                    MIGRATION_34_35,
                    MIGRATION_35_36,
                    MIGRATION_36_37,
                    MIGRATION_37_38,
                    MIGRATION_38_39,
                    MIGRATION_39_40,
                    MIGRATION_40_41,
                    MIGRATION_41_42,
                    MIGRATION_42_43,
                    MIGRATION_43_44,
                    MIGRATION_44_45,
                    migration45to46(context),
                    MIGRATION_46_47,
                    MIGRATION_47_48,
                    MIGRATION_48_49,
                    MIGRATION_49_50,
                    MIGRATION_50_51,
                    MIGRATION_51_52,
                    MIGRATION_52_53,
                    MIGRATION_53_54,
                    MIGRATION_54_55,
                    MIGRATION_55_56,
                    MIGRATION_56_57,
                    MIGRATION_57_58,
                    MIGRATION_58_59,
                    MIGRATION_59_60,
                    MIGRATION_60_61,
                    MIGRATION_61_62,
                    MIGRATION_62_63,
                    MIGRATION_63_64,
                    MIGRATION_64_65,
                    MIGRATION_65_66,
                    MIGRATION_66_67,
                    MIGRATION_67_68,
                    MIGRATION_68_69
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

                // Post-migration: Fix null format settings (migration 19->20 data fix)
                fixNullFormatSettings(context, instance)

                INSTANCE = instance
                instance
            }
        }

        /**
         * Post-migration data fix for version 19->20
         * Fills null use_24_hour_format and date_format_pattern with system-detected values
         */
        private fun fixNullFormatSettings(context: Context, database: AppDatabase) {
            try {
                val cursor = database.openHelper.readableDatabase.query(
                    "SELECT settings FROM app_settings_categories WHERE category = 'format'"
                )

                if (cursor.moveToFirst()) {
                    val settingsJson = cursor.getString(0)
                    val settings = org.json.JSONObject(settingsJson)
                    var needsUpdate = false

                    // Check and fix use_24_hour_format
                    if (!settings.has("use_24_hour_format") || settings.isNull("use_24_hour_format")) {
                        val systemValue = app.treelune.core.config.FormatDefaults.getSystemDefault24HourFormat(context)
                        settings.put("use_24_hour_format", systemValue)
                        needsUpdate = true
                        LogManager.database("POST-MIGRATION 19->20: Filled use_24_hour_format with system value: $systemValue", "INFO")
                    }

                    // Check and fix date_format_pattern
                    if (!settings.has("date_format_pattern") || settings.isNull("date_format_pattern")) {
                        val systemValue = app.treelune.core.config.FormatDefaults.getSystemDefaultDatePattern(context)
                        settings.put("date_format_pattern", systemValue)
                        needsUpdate = true
                        LogManager.database("POST-MIGRATION 19->20: Filled date_format_pattern with system value: $systemValue", "INFO")
                    }

                    // Update database if needed
                    if (needsUpdate) {
                        database.openHelper.writableDatabase.execSQL(
                            "UPDATE app_settings_categories SET settings = ? WHERE category = 'format'",
                            arrayOf(settings.toString())
                        )
                        LogManager.database("POST-MIGRATION 19->20: Updated format settings in database", "INFO")
                    }
                }
                cursor.close()
            } catch (e: Exception) {
                LogManager.database("POST-MIGRATION 19->20: Failed to fix null format settings: ${e.message}", "ERROR", e)
                // Don't throw - app can continue with null values (will show loading placeholders)
            }
        }
    }
}