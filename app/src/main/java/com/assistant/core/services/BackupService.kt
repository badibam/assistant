package com.assistant.core.services

import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.strings.Strings
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.*
import com.assistant.core.ai.database.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.assistant.core.ai.data.MessageSender
import com.assistant.core.ai.data.SessionType
import com.assistant.core.versioning.ChoiceOptionsAtV37
import com.assistant.core.versioning.FieldsAtV36
import com.assistant.core.versioning.NumericDecimalsAtV38
import com.assistant.core.versioning.ToolConfigsAtV39
import com.assistant.core.versioning.CatchUpAtV41
import com.assistant.core.versioning.FormatNullsAtV42
import com.assistant.core.versioning.TrackingUnitAtV43
import com.assistant.core.versioning.PointerAtV44
import com.assistant.core.versioning.EnrichmentTextAtV45
import com.assistant.core.versioning.PointerAtV46
import com.assistant.core.versioning.VariableValidationAtV49
import com.assistant.core.versioning.ScheduleDatesAtV51
import com.assistant.core.database.entities.VariableEntity
import com.assistant.core.versioning.JsonTransformers
import com.assistant.core.versioning.KeyCaseRenames
import org.json.JSONObject
import org.json.JSONArray
import com.assistant.core.ai.data.LegacyCatchUp

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
            val variables = database.variableDao().getAll()
            // The files joined to messages, with their session
            val attachedFiles = aiSessions.flatMap { database.attachedFileDao().getForSession(it.id) }

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
                                put("icon_name", zone.icon_name)
                                put("active", zone.active)
                                put("order_index", zone.order_index)
                                put("created_at", zone.created_at)
                                put("updated_at", zone.updated_at)
                                if (zone.tool_groups != null) {
                                    put("tool_groups", zone.tool_groups)
                                }
                                if (zone.group != null) {
                                    put("group", zone.group)
                                }
                            })
                        }
                    })

                    // Tool instances
                    put("tool_instances", JSONArray().apply {
                        toolInstances.forEach { instance ->
                            put(JSONObject().apply {
                                put("id", instance.id)
                                put("zone_id", instance.zone_id)
                                put("tooltype", instance.tooltype)
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
                                if (data.extra != null) {
                                    put("extra", data.extra)
                                }
                                if (data.state != null) {
                                    put("state", data.state)
                                }
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
                                if (session.appStateSnapshot != null) {
                                    put("app_state_snapshot", session.appStateSnapshot)
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
                                put("model_id", message.modelId)
                                put("input_price", message.inputPrice)
                                put("cache_write_price", message.cacheWritePrice)
                                put("cache_read_price", message.cacheReadPrice)
                                put("output_price", message.outputPrice)
                                put("usage_unknown", message.usageUnknown)
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
                                automation.catchUp?.let { put("catch_up", it) }
                                automation.catchUpWindow?.let { put("catch_up_window", it) }
                                put("dismiss_older_instances", automation.dismissOlderInstances)
                                put("provider_id", automation.providerId)
                                put("is_enabled", automation.isEnabled)
                                put("created_at", automation.createdAt)
                                put("updated_at", automation.updatedAt)
                                put("last_execution_id", automation.lastExecutionId)
                                put("execution_history_json", automation.executionHistoryJson)
                                if (automation.group != null) {
                                    put("group", automation.group)
                                }
                            })
                        }
                    })

                    // Variables
                    put("variables", JSONArray().apply {
                        variables.forEach { variable ->
                            put(JSONObject().apply {
                                put("id", variable.id)
                                put("zone_id", variable.zoneId)
                                put("name", variable.name)
                                variable.group?.let { put("group", it) }
                                put("order_index", variable.orderIndex)
                                put("definition_json", variable.definitionJson)
                                put("created_at", variable.createdAt)
                                put("updated_at", variable.updatedAt)
                            })
                        }
                    })

                    // Files joined to messages
                    put("attached_files", JSONArray().apply {
                        attachedFiles.forEach { file ->
                            put(JSONObject().apply {
                                put("id", file.id)
                                put("session_id", file.sessionId)
                                put("name", file.name)
                                put("mime_type", file.mimeType)
                                put("size_bytes", file.sizeBytes)
                                put("line_count", file.lineCount)
                                put("content", file.content)
                                put("created_at", file.createdAt)
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

            // Get versions for validation and transformations
            val exportVersion = metadata.optInt("export_version", -1)
            val dbSchemaVersion = metadata.optInt("db_schema_version", -1)

            if (exportVersion == -1 || dbSchemaVersion == -1) {
                return OperationResult.error(s.shared("backup_invalid_file"))
            }

            // Check app version compatibility (export_version is the app version)
            val currentAppVersion = com.assistant.BuildConfig.VERSION_CODE
            if (exportVersion > currentAppVersion) {
                return OperationResult.error(s.shared("backup_version_too_recent"))
            }

            // Apply JSON transformations based on DB schema version
            // (db_schema_version determines data structure, not app version)
            val currentDbVersion = AppDatabase.VERSION
            val transformedData = if (dbSchemaVersion < currentDbVersion) {
                LogManager.service("Applying JSON transformations from DB schema v$dbSchemaVersion to v$currentDbVersion", "INFO")
                transformBackupData(backupJson, dbSchemaVersion, currentDbVersion)
            } else {
                LogManager.service("No JSON transformations needed (DB schema v$dbSchemaVersion)", "DEBUG")
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
        com.assistant.core.config.AppSettingsDefaults.CATEGORIES.forEach { category ->
            database.appSettingsCategoryDao().insertOrUpdateSettings(
                AppSettingsCategory(
                    category = category,
                    settings = com.assistant.core.config.AppSettingsDefaults.forCategory(category, context)
                )
            )
        }
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
                        icon_name = item.optString("icon_name", null),
                        active = item.optBoolean("active", true),
                        order_index = item.getInt("order_index"),
                        created_at = item.getLong("created_at"),
                        updated_at = item.getLong("updated_at"),
                        tool_groups = item.optString("tool_groups", null),
                        group = item.optString("group", null)
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
                        tooltype = item.getString("tooltype"),
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
                        extra = item.optString("extra", null),
                        state = item.optString("state", null),
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
                        appStateSnapshot = item.optString("app_state_snapshot", null)
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
                        outputTokens = item.optInt("output_tokens", 0),
                        // Absent from backups before v35: the prices of those calls are unknown
                        modelId = item.optString("model_id", null),
                        inputPrice = item.optPrice("input_price"),
                        cacheWritePrice = item.optPrice("cache_write_price"),
                        cacheReadPrice = item.optPrice("cache_read_price"),
                        outputPrice = item.optPrice("output_price"),
                        usageUnknown = item.optBoolean("usage_unknown", false)
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
                        // A backup older than v41 is brought to this form first (CatchUpAtV41)
                        catchUp = item.optString("catch_up").takeIf { it.isNotEmpty() },
                        catchUpWindow = if (item.has("catch_up_window")) item.getLong("catch_up_window") else null,
                        dismissOlderInstances = item.getBoolean("dismiss_older_instances"),
                        providerId = item.getString("provider_id"),
                        isEnabled = item.getBoolean("is_enabled"),
                        createdAt = item.getLong("created_at"),
                        updatedAt = System.currentTimeMillis(), // Always now on import - prevents executing missed periods
                        lastExecutionId = item.optString("last_execution_id", null),
                        executionHistoryJson = item.optString("execution_history_json", "[]"),
                        group = item.optString("group", null)
                    )
                )
            }
        }

        // Variables, after the zones they live in
        data.optJSONArray("variables")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.variableDao().insert(
                    VariableEntity(
                        id = item.getString("id"),
                        zoneId = item.getString("zone_id"),
                        name = item.getString("name"),
                        group = item.optString("group").takeIf { it.isNotEmpty() },
                        orderIndex = item.getInt("order_index"),
                        definitionJson = item.getString("definition_json"),
                        createdAt = item.getLong("created_at"),
                        updatedAt = item.getLong("updated_at")
                    )
                )
            }
        }

        // Files joined to messages, after the sessions they belong to; absent before v50
        data.optJSONArray("attached_files")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                database.attachedFileDao().insert(
                    com.assistant.core.ai.database.AttachedFileEntity(
                        id = item.getString("id"),
                        sessionId = item.getString("session_id"),
                        name = item.getString("name"),
                        mimeType = item.getString("mime_type"),
                        sizeBytes = item.getLong("size_bytes"),
                        lineCount = item.getInt("line_count"),
                        content = item.getString("content"),
                        createdAt = item.getLong("created_at")
                    )
                )
            }
        }

    }

    /**
     * Transform backup data from old version to current version
     * Applies sequential JSON transformations to all config and data fields
     *
     * This function calls JsonTransformers for:
     * - Tool instance config_json (per tooltype)
     * - Tool data entries (per tooltype)
     * - App settings (per category)
     */
    private fun transformBackupData(
        jsonData: JSONObject,
        fromVersion: Int,
        toVersion: Int
    ): JSONObject {
        try {
            // Up to schema 27 the keys could still be written in camelCase: before 26 across the
            // board, and at 27 inside any document a model wrapped in a markdown fence. tool_type
            // also named what is now tooltype. Everything below reads the current names, so the
            // whole document is normalized first, with the same map the database migration uses.
            val document = if (fromVersion < 28) {
                JSONObject(KeyCaseRenames.rename(jsonData.toString()))
            } else {
                jsonData
            }
            val data = document.getJSONObject("data")

            // The column transformations run up to v35. From there an entry changes as a whole
            // (columns renamed, state moved out of data, tracking reshaped against its tool's
            // config), which FieldsAtV36 does on the document.
            val columnsTo = minOf(toVersion, 35)

            // Transform tool instance configurations
            data.optJSONArray("tool_instances")?.let { array ->
                for (i in 0 until array.length()) {
                    val instance = array.getJSONObject(i)
                    val tooltype = instance.getString("tooltype")
                    val configJson = instance.getString("config_json")

                    // Apply JSON transformations to config
                    val transformedConfig = JsonTransformers.transformToolConfig(
                        configJson,
                        tooltype,
                        fromVersion,
                        columnsTo
                    )
                    instance.put("config_json", transformedConfig)
                }
            }

            // Transform tool data entries
            data.optJSONArray("tool_data")?.let { array ->
                for (i in 0 until array.length()) {
                    val entry = array.getJSONObject(i)
                    val tooltype = entry.getString("tooltype")
                    val dataJson = entry.getString("data")

                    // Apply JSON transformations to data
                    var transformedData = JsonTransformers.transformToolData(
                        dataJson,
                        tooltype,
                        fromVersion,
                        columnsTo
                    )

                    // Also fix SchedulePattern types if present (for Messages tool)
                    transformedData = JsonTransformers.fixSchedulePatternTypes(transformedData)

                    entry.put("data", transformedData)
                }
            }

            if (fromVersion < 36 && toVersion >= 36) {
                FieldsAtV36.backup(data)
            }
            if (fromVersion < 37 && toVersion >= 37) {
                ChoiceOptionsAtV37.backup(data)
            }
            if (fromVersion < 38 && toVersion >= 38) {
                NumericDecimalsAtV38.backup(data)
            }
            if (fromVersion < 39 && toVersion >= 39) {
                ToolConfigsAtV39.backup(data)
            }
            if (fromVersion < 41 && toVersion >= 41) {
                CatchUpAtV41.backup(data)
            }
            if (fromVersion < 43 && toVersion >= 43) {
                TrackingUnitAtV43.backup(data)
            }
            if (fromVersion < 44 && toVersion >= 44) {
                PointerAtV44.backup(data)
            }
            if (fromVersion < 45 && toVersion >= 45) {
                EnrichmentTextAtV45.backup(data)
            }
            if (fromVersion < 46 && toVersion >= 46) {
                PointerAtV46.backup(data, context)
            }
            if (fromVersion < 49 && toVersion >= 49) {
                VariableValidationAtV49.backup(data)
            }
            if (fromVersion < 51 && toVersion >= 51) {
                ScheduleDatesAtV51.backup(data)
            }

            // Transform app settings
            data.optJSONArray("app_settings_categories")?.let { array ->
                for (i in 0 until array.length()) {
                    val category = array.getJSONObject(i)
                    val settingsJson = category.getString("settings")

                    // Apply JSON transformations to app config
                    val transformedSettings = JsonTransformers.transformAppConfig(
                        settingsJson,
                        category.getString("category"),
                        fromVersion,
                        toVersion,
                        context  // Pass context for system detection in migrations
                    )
                    category.put("settings", transformedSettings)
                }
            }

            // After the category transformations above, which read the older forms
            if (fromVersion < 42 && toVersion >= 42) {
                FormatNullsAtV42.backup(data)
            }

            // Transform automation schedules (fix SchedulePattern types for v10 → v11)
            data.optJSONArray("automations")?.let { array ->
                for (i in 0 until array.length()) {
                    val automation = array.getJSONObject(i)
                    val scheduleJson = automation.optString("schedule_json", null)
                    if (scheduleJson != null) {
                        // Apply SchedulePattern type fix transformation
                        val transformedSchedule = JsonTransformers.fixSchedulePatternTypes(scheduleJson)
                        automation.put("schedule_json", transformedSchedule)
                    }
                }
            }

            // Update metadata to reflect transformed version
            document.getJSONObject("metadata").apply {
                put("export_version", toVersion)
                put("db_schema_version", AppDatabase.VERSION)
            }

            return document

        } catch (e: Exception) {
            // The import wipes every table before inserting, so handing back the untransformed
            // document would destroy what is there and replace it with data in a format the
            // current schema does not read. Failing here happens before the wipe, and leaves the
            // database exactly as it was.
            LogManager.service("Backup transformation failed: ${e.message}", "ERROR", e)
            throw IllegalStateException("Backup transformation failed: ${e.message}", e)
        }
    }

    /**
     * Verbalize backup operation
     * Backup operations are typically not exposed to AI
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "export" -> "Export des données"
            "import" -> "Import de sauvegarde"
            "reset" -> "Réinitialisation des données"
            else -> s.shared("action_verbalize_unknown")
        }
    }
}

/** A price stored in a backup, or null when absent: an unknown price, never 0. */
private fun JSONObject.optPrice(key: String): Double? =
    if (has(key) && !isNull(key)) getDouble(key) else null
