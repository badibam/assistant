package com.assistant.core.services

import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.strings.Strings
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.*
import com.assistant.core.ai.database.*
import com.assistant.core.transcription.database.TranscriptionProviderConfigEntity
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.assistant.core.ai.data.MessageSender
import com.assistant.core.ai.data.SessionType
import org.json.JSONObject
import org.json.JSONArray

/**
 * Backup service - handles export, import and reset operations
 *
 * Architecture:
 * - Pure logic, returns/accepts JSON strings
 * - No file I/O (handled by UI with SAF)
 * - No Android framework dependencies except Context for DB access
 *
 * Operations:
 * - export: Generate JSON backup of all data
 * - import: Restore data from JSON backup (with version migrations)
 * - reset: Wipe all data and restore defaults
 */
class BackupService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database = AppDatabase.getDatabase(context)

    // No companion object needed - use BuildConfig and AppDatabase.VERSION directly

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        return withContext(Dispatchers.IO) {
            when (operation) {
                "export" -> performExport(token)
                "import" -> performImport(params.optString("json_data"), token)
                "reset" -> performReset(token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        }
    }

    /**
     * Export all database data to JSON string
     * Returns OperationResult with "json_data" field containing the backup JSON
     */
    private suspend fun performExport(token: CancellationToken): OperationResult {
        return try {
            LogManager.service("Starting backup export")

            // Check cancellation
            if (token.isCancelled) {
                return OperationResult.error("Operation cancelled")
            }

            // Query all tables in dependency order
            val appSettings = database.appSettingsCategoryDao().getAllSettings()
            val zones = database.zoneDao().getAllZones()
            val toolInstances = database.toolInstanceDao().getAllToolInstances()
            val toolData = database.toolDataDao().getAllEntries()

            val aiSessions = database.aiDao().getAllSessions()
            val aiMessages = mutableListOf<SessionMessageEntity>()
            aiSessions.forEach { session ->
                aiMessages.addAll(database.aiDao().getMessagesForSession(session.id))
            }

            val aiProviderConfigs = database.aiDao().getAllProviderConfigs()
            val automations = database.aiDao().getAllAutomations()
            val transcriptionProviderConfigs = database.transcriptionDao().getAllProviderConfigs()

            // Check cancellation before building JSON
            if (token.isCancelled) {
                return OperationResult.error("Operation cancelled")
            }

            // Build JSON structure
            val backupJson = JSONObject().apply {
                put("metadata", JSONObject().apply {
                    put("app_version", com.assistant.BuildConfig.VERSION_NAME)
                    put("export_version", com.assistant.BuildConfig.VERSION_CODE)
                    put("db_schema_version", AppDatabase.VERSION)
                    put("export_timestamp", System.currentTimeMillis())
                })

                put("data", JSONObject().apply {
                    // App settings
                    put("app_settings_categories", JSONArray().apply {
                        appSettings.forEach { setting ->
                            put(JSONObject().apply {
                                put("category", setting.category)
                                put("settings", setting.settings)
                                put("updated_at", setting.updatedAt)
                            })
                        }
                    })

                    // Zones
                    put("zones", JSONArray().apply {
                        zones.forEach { zone ->
                            put(JSONObject().apply {
                                put("id", zone.id)
                                put("name", zone.name)
                                put("description", zone.description)
                                put("color", zone.color)
                                put("active", zone.active)
                                put("order_index", zone.order_index)
                                put("created_at", zone.created_at)
                                put("updated_at", zone.updated_at)
                            })
                        }
                    })

                    // Tool instances
                    put("tool_instances", JSONArray().apply {
                        toolInstances.forEach { instance ->
                            put(JSONObject().apply {
                                put("id", instance.id)
                                put("zone_id", instance.zone_id)
                                put("tool_type", instance.tool_type)
                                put("config_json", instance.config_json)
                                put("enabled", instance.enabled)
                                put("order_index", instance.order_index)
                                put("created_at", instance.created_at)
                                put("updated_at", instance.updated_at)
                            })
                        }
                    })

                    // Tool data
                    put("tool_data", JSONArray().apply {
                        toolData.forEach { data ->
                            put(JSONObject().apply {
                                put("id", data.id)
                                put("tool_instance_id", data.toolInstanceId)
                                put("tooltype", data.tooltype)
                                put("name", data.name)
                                put("timestamp", data.timestamp)
                                put("data", data.data)
                                put("created_at", data.createdAt)
                                put("updated_at", data.updatedAt)
                            })
                        }
                    })

                    // AI sessions
                    put("ai_sessions", JSONArray().apply {
                        aiSessions.forEach { session ->
                            put(JSONObject().apply {
                                put("id", session.id)
                                put("name", session.name)
                                put("type", session.type)
                                put("require_validation", session.requireValidation)
                                put("phase", session.phase)
                                put("waiting_context_json", session.waitingContextJson)
                                put("total_roundtrips", session.totalRoundtrips)
                                put("last_event_time", session.lastEventTime)
                                put("last_user_interaction_time", session.lastUserInteractionTime)
                                put("automation_id", session.automationId)
                                put("scheduled_execution_time", session.scheduledExecutionTime)
                                put("provider_id", session.providerId)
                                put("provider_session_id", session.providerSessionId)
                                put("created_at", session.createdAt)
                                put("last_activity", session.lastActivity)
                                put("is_active", session.isActive)
                                put("end_reason", session.endReason)
                                if (session.tokensUsed != null) {
                                    put("tokens_used", session.tokensUsed)
                                }
                            })
                        }
                    })

                    // AI messages
                    put("session_messages", JSONArray().apply {
                        aiMessages.forEach { message ->
                            put(JSONObject().apply {
                                put("id", message.id)
                                put("session_id", message.sessionId)
                                put("timestamp", message.timestamp)
                                put("sender", message.sender.name)
                                put("rich_content_json", message.richContentJson)
                                put("text_content", message.textContent)
                                put("ai_message_json", message.aiMessageJson)
                                put("ai_message_parsed_json", message.aiMessageParsedJson)
                                put("system_message_json", message.systemMessageJson)
                                put("execution_metadata_json", message.executionMetadataJson)
                                put("exclude_from_prompt", message.excludeFromPrompt)
                                put("input_tokens", message.inputTokens)
                                put("cache_write_tokens", message.cacheWriteTokens)
                                put("cache_read_tokens", message.cacheReadTokens)
                                put("output_tokens", message.outputTokens)
                            })
                        }
                    })

                    // AI provider configs
                    put("ai_provider_configs", JSONArray().apply {
                        aiProviderConfigs.forEach { config ->
                            put(JSONObject().apply {
                                put("provider_id", config.providerId)
                                put("display_name", config.displayName)
                                put("config_json", config.configJson)
                                put("is_configured", config.isConfigured)
                                put("is_active", config.isActive)
                                put("created_at", config.createdAt)
                                put("updated_at", config.updatedAt)
                            })
                        }
                    })

                    // Automations
                    put("automations", JSONArray().apply {
                        automations.forEach { automation ->
                            put(JSONObject().apply {
                                put("id", automation.id)
                                put("name", automation.name)
                                put("zone_id", automation.zoneId)
                                put("seed_session_id", automation.seedSessionId)
                                put("schedule_json", automation.scheduleJson)
                                put("trigger_ids_json", automation.triggerIdsJson)
                                put("dismiss_older_instances", automation.dismissOlderInstances)
                                put("provider_id", automation.providerId)
                                put("is_enabled", automation.isEnabled)
                                put("created_at", automation.createdAt)
                                put("last_execution_id", automation.lastExecutionId)
                                put("execution_history_json", automation.executionHistoryJson)
                            })
                        }
                    })

                    // Transcription provider configs
                    put("transcription_provider_configs", JSONArray().apply {
                        transcriptionProviderConfigs.forEach { config ->
                            put(JSONObject().apply {
                                put("provider_id", config.providerId)
                                put("display_name", config.displayName)
                                put("config_json", config.configJson)
                                put("is_configured", config.isConfigured)
                                put("is_active", config.isActive)
                                put("created_at", config.createdAt)
                                put("updated_at", config.updatedAt)
                            })
                        }
                    })
                })
            }

            val jsonString = backupJson.toString(2) // Pretty print with 2-space indent
            LogManager.service("Backup export completed successfully")

            OperationResult.success(mapOf("json_data" to jsonString))

        } catch (e: Exception) {
            LogManager.service("Backup export failed: ${e.message}", "ERROR", e)
            OperationResult.error("Export failed: ${e.message}")
        }
    }

    /**
     * Import data from JSON backup string
     * Validates version and applies transformations if needed
     */
    private suspend fun performImport(jsonData: String, token: CancellationToken): OperationResult {
        return try {
            LogManager.service("Starting backup import")

            if (jsonData.isEmpty()) {
                return OperationResult.error(s.shared("backup_invalid_file"))
            }

            // Parse and validate JSON
            val backupJson = JSONObject(jsonData)
            val metadata = backupJson.optJSONObject("metadata")
                ?: return OperationResult.error(s.shared("backup_invalid_file"))

            // Try new field name first, fallback to old for compatibility
            val backupDbVersion = metadata.optInt("db_schema_version",
                metadata.optInt("db_version", -1))
            if (backupDbVersion == -1) {
                return OperationResult.error(s.shared("backup_invalid_file"))
            }

            // Check version compatibility
            val currentDbVersion = AppDatabase.VERSION
            if (backupDbVersion > currentDbVersion) {
                return OperationResult.error(s.shared("backup_version_too_recent"))
            }

            // Apply transformations if needed
            val transformedData = if (backupDbVersion < currentDbVersion) {
                LogManager.service("Applying transformations from version $backupDbVersion to $currentDbVersion")
                transformBackupData(backupJson, backupDbVersion, currentDbVersion)
            } else {
                backupJson
            }

            // Check cancellation
            if (token.isCancelled) {
                return OperationResult.error("Operation cancelled")
            }

            val data = transformedData.getJSONObject("data")

            // Wipe all tables in reverse dependency order, then insert data
            // This operation is wrapped in a Room transaction for atomicity
            database.withTransaction {
                // Delete in reverse dependency order
                wipeAllTables()

                // Check cancellation
                if (token.isCancelled) {
                    throw Exception("Operation cancelled")
                }

                // Insert data in dependency order (preserving original IDs)
                insertImportedData(data)
            }

            LogManager.service("Backup import completed successfully")
            OperationResult.success(mapOf("message" to s.shared("backup_import_success")))

        } catch (e: Exception) {
            LogManager.service("Backup import failed: ${e.message}", "ERROR", e)
            OperationResult.error("Import failed: ${e.message}")
        }
    }

    /**
     * Reset all data - wipe database and restore defaults
     */
    private suspend fun performReset(token: CancellationToken): OperationResult {
        return try {
            LogManager.service("Starting database reset")

            // Check cancellation
            if (token.isCancelled) {
                return OperationResult.error("Operation cancelled")
            }

            // Wipe all tables and restore defaults in a transaction
            database.withTransaction {
                wipeAllTables()

                // Check cancellation
                if (token.isCancelled) {
                    throw Exception("Operation cancelled")
                }

                insertDefaultAppConfig()
            }

            LogManager.service("Database reset completed successfully")
            OperationResult.success(mapOf("message" to s.shared("backup_reset_success")))

        } catch (e: Exception) {
            LogManager.service("Database reset failed: ${e.message}", "ERROR", e)
            OperationResult.error("Reset failed: ${e.message}")
        }
    }

    /**
     * Wipe all tables in reverse dependency order
     * Must be called within a Room transaction
     */
    private fun wipeAllTables() {
        // Reverse dependency order: children first, parents last
        database.clearAllTables()
    }

    /**
     * Insert default app configuration
     * Called after reset to ensure app has valid defaults
     */
    private suspend fun insertDefaultAppConfig() {
        // Insert default app settings
        database.appSettingsCategoryDao().insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.FORMAT,
                settings = DefaultFormatSettings.JSON.trimIndent()
            )
        )
        database.appSettingsCategoryDao().insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.AI_LIMITS,
                settings = DefaultAILimitsSettings.JSON.trimIndent()
            )
        )
        database.appSettingsCategoryDao().insertOrUpdateSettings(
            AppSettingsCategory(
                category = AppSettingCategories.VALIDATION_CONFIG,
                settings = DefaultValidationSettings.JSON.trimIndent()
            )
        )
    }

    /**
     * Insert imported data from JSON
     * Must be called within a Room transaction
     * Data is inserted in dependency order to respect foreign keys
     */
    private suspend fun insertImportedData(data: JSONObject) {
        // App settings
        data.optJSONArray("app_settings_categories")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.appSettingsCategoryDao().insertOrUpdateSettings(
                    AppSettingsCategory(
                        category = item.getString("category"),
                        settings = item.getString("settings"),
                        updatedAt = item.getLong("updated_at")
                    )
                )
            }
        }

        // Zones
        data.optJSONArray("zones")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.zoneDao().insertZone(
                    Zone(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        description = item.optString("description", null),
                        color = item.optString("color", null),
                        active = item.optBoolean("active", true),
                        order_index = item.getInt("order_index"),
                        created_at = item.getLong("created_at"),
                        updated_at = item.getLong("updated_at")
                    )
                )
            }
        }

        // Tool instances
        data.optJSONArray("tool_instances")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.toolInstanceDao().insertToolInstance(
                    ToolInstance(
                        id = item.getString("id"),
                        zone_id = item.getString("zone_id"),
                        tool_type = item.getString("tool_type"),
                        config_json = item.getString("config_json"),
                        enabled = item.optBoolean("enabled", true),
                        order_index = item.getInt("order_index"),
                        created_at = item.getLong("created_at"),
                        updated_at = item.getLong("updated_at")
                    )
                )
            }
        }

        // Tool data
        data.optJSONArray("tool_data")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.toolDataDao().insert(
                    ToolDataEntity(
                        id = item.getString("id"),
                        toolInstanceId = item.getString("tool_instance_id"),
                        tooltype = item.getString("tooltype"),
                        timestamp = item.optLong("timestamp", 0).let { if (it == 0L) null else it },
                        name = item.optString("name", null),
                        data = item.getString("data"),
                        createdAt = item.getLong("created_at"),
                        updatedAt = item.getLong("updated_at")
                    )
                )
            }
        }

        // AI provider configs
        data.optJSONArray("ai_provider_configs")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.aiDao().insertProviderConfig(
                    AIProviderConfigEntity(
                        providerId = item.getString("provider_id"),
                        displayName = item.getString("display_name"),
                        configJson = item.getString("config_json"),
                        isConfigured = item.getBoolean("is_configured"),
                        isActive = item.getBoolean("is_active"),
                        createdAt = item.getLong("created_at"),
                        updatedAt = item.getLong("updated_at")
                    )
                )
            }
        }

        // AI sessions
        data.optJSONArray("ai_sessions")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.aiDao().insertSession(
                    AISessionEntity(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        type = SessionType.valueOf(item.getString("type")),
                        requireValidation = item.getBoolean("require_validation"),
                        phase = item.getString("phase"),
                        waitingContextJson = item.optString("waiting_context_json", null),
                        totalRoundtrips = item.getInt("total_roundtrips"),
                        lastEventTime = item.getLong("last_event_time"),
                        lastUserInteractionTime = item.getLong("last_user_interaction_time"),
                        automationId = item.optString("automation_id", null),
                        scheduledExecutionTime = item.optLong("scheduled_execution_time", 0).let { if (it == 0L) null else it },
                        providerId = item.getString("provider_id"),
                        providerSessionId = item.getString("provider_session_id"),
                        createdAt = item.getLong("created_at"),
                        lastActivity = item.getLong("last_activity"),
                        isActive = item.getBoolean("is_active"),
                        endReason = item.optString("end_reason", null),
                        tokensUsed = if (item.has("tokens_used")) item.getInt("tokens_used") else null
                    )
                )
            }
        }

        // AI messages
        data.optJSONArray("session_messages")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.aiDao().insertMessage(
                    SessionMessageEntity(
                        id = item.getString("id"),
                        sessionId = item.getString("session_id"),
                        timestamp = item.getLong("timestamp"),
                        sender = MessageSender.valueOf(item.getString("sender")),
                        richContentJson = item.optString("rich_content_json", null),
                        textContent = item.optString("text_content", null),
                        aiMessageJson = item.optString("ai_message_json", null),
                        aiMessageParsedJson = item.optString("ai_message_parsed_json", null),
                        systemMessageJson = item.optString("system_message_json", null),
                        executionMetadataJson = item.optString("execution_metadata_json", null),
                        excludeFromPrompt = item.optBoolean("exclude_from_prompt", false),
                        inputTokens = item.optInt("input_tokens", 0),
                        cacheWriteTokens = item.optInt("cache_write_tokens", 0),
                        cacheReadTokens = item.optInt("cache_read_tokens", 0),
                        outputTokens = item.optInt("output_tokens", 0)
                    )
                )
            }
        }

        // Automations
        data.optJSONArray("automations")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.aiDao().insertAutomation(
                    AutomationEntity(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        zoneId = item.getString("zone_id"),
                        seedSessionId = item.getString("seed_session_id"),
                        scheduleJson = item.optString("schedule_json", null),
                        triggerIdsJson = item.optString("trigger_ids_json", "[]"),
                        dismissOlderInstances = item.optBoolean("dismiss_older_instances", false),
                        providerId = item.getString("provider_id"),
                        isEnabled = item.getBoolean("is_enabled"),
                        createdAt = item.getLong("created_at"),
                        lastExecutionId = item.optString("last_execution_id", null),
                        executionHistoryJson = item.optString("execution_history_json", "[]")
                    )
                )
            }
        }

        // Transcription provider configs
        data.optJSONArray("transcription_provider_configs")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.transcriptionDao().insertProviderConfig(
                    TranscriptionProviderConfigEntity(
                        providerId = item.getString("provider_id"),
                        displayName = item.getString("display_name"),
                        configJson = item.getString("config_json"),
                        isConfigured = item.getBoolean("is_configured"),
                        isActive = item.getBoolean("is_active"),
                        createdAt = item.getLong("created_at"),
                        updatedAt = item.getLong("updated_at")
                    )
                )
            }
        }
    }

    /**
     * Transform backup data from old version to current version
     * Applies sequential migrations for each version increment
     */
    private fun transformBackupData(
        jsonData: JSONObject,
        fromVersion: Int,
        toVersion: Int
    ): JSONObject {
        var transformed = jsonData

        for (version in fromVersion until toVersion) {
            transformed = when (version) {
                // Future migrations will be added here
                // Example:
                // 1 -> migrateFrom1To2(transformed)
                // 2 -> migrateFrom2To3(transformed)
                else -> transformed // No migration needed yet
            }
        }

        // Update metadata version to current
        transformed.getJSONObject("metadata").put("db_version", toVersion)
        return transformed
    }

    /**
     * Verbalize backup operation
     * Backup operations are typically not exposed to AI
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "export" -> "Export des données"
            "import" -> "Import de sauvegarde"
            "reset" -> "Réinitialisation des données"
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
