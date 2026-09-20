package com.assistant.core.ai.services

import android.content.Context
import com.assistant.core.ai.data.Automation
import com.assistant.core.ai.database.AutomationEntity
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.database.AppDatabase
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.ScheduleConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Service for automation CRUD operations
 *
 * Operations:
 * - create, update, delete, get
 * - list (by zone), list_all
 * - enable, disable
 * - execute_manual (triggers manual execution via AIOrchestrator)
 */
class AutomationService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val json = Json { ignoreUnknownKeys = true }

    // Coroutine scope for async tick() calls
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Access common database
    private val dao by lazy {
        AppDatabase.getDatabase(context).aiDao()
    }

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("AutomationService.execute - operation: $operation", "DEBUG")

        val result = try {
            when (operation) {
                "create" -> createAutomation(params, token)
                "update" -> updateAutomation(params, token)
                "delete" -> deleteAutomation(params, token)
                "duplicate" -> duplicateAutomation(params, token)
                "get" -> getAutomation(params, token)
                "get_by_seed_session" -> getAutomationBySeedSession(params, token)
                "list" -> listAutomations(params, token)
                "list_all" -> listAllAutomations(token)
                "enable" -> setEnabled(params, token, true)
                "disable" -> setEnabled(params, token, false)
                "execute_manual" -> executeManual(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            LogManager.service("AutomationService.execute - Error: ${e.message}", "ERROR", e)
            OperationResult.error(s.shared("service_error_automation").format(e.message ?: ""))
        }

        // Trigger tick() after CRUD operations that affect scheduling (if successful)
        if (result.success && operation in listOf("create", "update", "enable", "disable")) {
            LogManager.service("AutomationService: Triggering tick() after $operation", "DEBUG")
            scope.launch {
                try {
                    AIOrchestrator.tick()
                } catch (e: Exception) {
                    LogManager.service("AutomationService: Error calling tick(): ${e.message}", "ERROR", e)
                }
            }
        }

        return result
    }

    /**
     * Create new automation
     */
    private suspend fun createAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Extract required parameters
        val name = params.optString("name").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_name_required"))
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))
        val seedSessionId = params.optString("seed_session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_seed_session_required"))
        val providerId = params.optString("provider_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_provider_id_required"))

        LogManager.service("Creating automation: name=$name, zoneId=$zoneId", "DEBUG")

        // Parse optional schedule (no nextExecutionTime calculation - dynamic via AutomationScheduler)
        val scheduleJson = params.optString("schedule").takeIf { it.isNotEmpty() }
        val schedule = scheduleJson?.let { json.decodeFromString<ScheduleConfig>(it) }

        // Parse trigger IDs
        val triggerIdsArray = params.optJSONArray("trigger_ids") ?: JSONArray()
        val triggerIds = (0 until triggerIdsArray.length()).map { triggerIdsArray.getString(it) }

        // Create automation entity
        val automationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Parse optional group
        val group = params.optString("group").takeIf { it.isNotEmpty() }

        val entity = AutomationEntity(
            id = automationId,
            name = name,
            zoneId = zoneId,
            seedSessionId = seedSessionId,
            scheduleJson = schedule?.let { json.encodeToString(it) },
            triggerIdsJson = json.encodeToString(triggerIds),
            catchUpWindowMinutes = if (params.has("catch_up_window_minutes") && !params.isNull("catch_up_window_minutes"))
                params.getLong("catch_up_window_minutes")
            else
                null,
            dismissOlderInstances = params.optBoolean("dismiss_older_instances", false),
            providerId = providerId,
            isEnabled = params.optBoolean("is_enabled", true),
            group = group,
            createdAt = now,
            updatedAt = now,
            lastExecutionId = null,
            executionHistoryJson = json.encodeToString(emptyList<String>())
        )

        dao.insertAutomation(entity)

        LogManager.service("Successfully created automation: $automationId", "INFO")

        // Convert timestamp to ISO 8601 for output
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "name" to name,
            "zone_id" to zoneId,
            "created_at" to DateTimeConverter.timestampToISO(now, timezone)
        ))
    }

    /**
     * Update existing automation
     */
    private suspend fun updateAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Updating automation: $automationId", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        // Update fields if provided
        val name = params.optString("name").takeIf { it.isNotEmpty() } ?: entity.name
        val newZoneId = params.optString("zone_id").takeIf { it.isNotBlank() }
        val providerId = params.optString("provider_id").takeIf { it.isNotEmpty() } ?: entity.providerId

        // Parse schedule if provided (no nextExecutionTime calculation - dynamic via AutomationScheduler)
        val scheduleJson = params.optString("schedule")
        val schedule = when {
            scheduleJson == "null" -> null // Explicit removal
            scheduleJson.isNotEmpty() -> {
                // MAJOR: Catch JSON parsing errors and return clear error message to AI
                try {
                    json.decodeFromString<ScheduleConfig>(scheduleJson)
                } catch (e: Exception) {
                    LogManager.service("Invalid schedule JSON format: ${e.message}", "ERROR", e)
                    return OperationResult.error("Invalid schedule JSON format: ${e.message}")
                }
            }
            else -> entity.scheduleJson?.let { json.decodeFromString<ScheduleConfig>(it) }
        }

        // Parse trigger IDs if provided
        val triggerIdsArray = params.optJSONArray("trigger_ids")
        val triggerIds = if (triggerIdsArray != null) {
            (0 until triggerIdsArray.length()).map { triggerIdsArray.getString(it) }
        } else {
            json.decodeFromString<List<String>>(entity.triggerIdsJson)
        }

        val dismissOlderInstances = if (params.has("dismiss_older_instances"))
            params.getBoolean("dismiss_older_instances")
        else
            entity.dismissOlderInstances

        // An explicit null clears the window (no limit); an absent key keeps what is stored
        val catchUpWindowMinutes = if (params.has("catch_up_window_minutes"))
            if (params.isNull("catch_up_window_minutes")) null else params.getLong("catch_up_window_minutes")
        else
            entity.catchUpWindowMinutes

        // Parse optional group (allow updating)
        val group = if (params.has("group")) {
            params.optString("group").takeIf { it.isNotEmpty() }
        } else {
            entity.group // Keep existing if not provided
        }

        // Store old zone_id for notification
        val oldZoneId = entity.zoneId

        val updatedEntity = entity.copy(
            name = name,
            zoneId = newZoneId ?: entity.zoneId, // Update zone if provided
            providerId = providerId,
            scheduleJson = schedule?.let { json.encodeToString(it) },
            triggerIdsJson = json.encodeToString(triggerIds),
            catchUpWindowMinutes = catchUpWindowMinutes,
            dismissOlderInstances = dismissOlderInstances,
            group = group,
            updatedAt = System.currentTimeMillis()
        )

        dao.updateAutomation(updatedEntity)

        // Notify UI of automation change in affected zones
        if (newZoneId != null && newZoneId != oldZoneId) {
            // Automation moved to different zone - notify both old and new zones
            com.assistant.core.utils.DataChangeNotifier.notifyZonesChanged()
            LogManager.service("Automation $automationId moved from zone $oldZoneId to $newZoneId", "DEBUG")
        }

        LogManager.service("Successfully updated automation: $automationId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "name" to name,
            "zone_id" to updatedEntity.zoneId,
            "updated" to true
        ))
    }

    /**
     * Delete automation
     */
    private suspend fun deleteAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Deleting automation: $automationId", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        dao.deleteAutomationById(automationId)

        LogManager.service("Successfully deleted automation: $automationId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "deleted" to true
        ))
    }

    /**
     * Duplicate an existing automation
     *
     * Creates a copy of the source automation with:
     * - A new SEED session (duplicated from source)
     * - All messages from source SEED copied to new SEED
     * - Modified name (adds " (copie)")
     * - Created in the specified target zone and group
     */
    private suspend fun duplicateAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))
        val targetZoneId = params.optString("target_zone_id").takeIf { it.isNotBlank() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))
        val targetGroup = params.optString("target_group").takeIf { it.isNotBlank() }

        LogManager.service("Duplicating automation: $automationId to zone $targetZoneId", "DEBUG")

        // Load source automation
        val sourceEntity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Load source SEED session
        val sourceSeedSession = dao.getSession(sourceEntity.seedSessionId)
            ?: return OperationResult.error("Source SEED session not found: ${sourceEntity.seedSessionId}")

        if (token.isCancelled) return OperationResult.cancelled()

        // Load all messages from source SEED session
        val sourceMessages = dao.getMessagesForSession(sourceEntity.seedSessionId)

        LogManager.service("Loaded ${sourceMessages.size} messages from source SEED session", "DEBUG")

        if (token.isCancelled) return OperationResult.cancelled()

        // Create new SEED session (copy of source)
        val newSeedSessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val newSeedSession = sourceSeedSession.copy(
            id = newSeedSessionId,
            name = "${sourceEntity.name} (copie)", // Name the SEED session with same pattern
            createdAt = now,
            lastActivity = now,
            isActive = false, // SEED sessions are never active
            endReason = null,
            automationId = null, // Will be set after automation is created
            phase = "IDLE",
            waitingContextJson = null,
            totalRoundtrips = 0,
            lastEventTime = now,
            lastUserInteractionTime = now,
            tokensJson = null // Reset token usage for new session
        )

        dao.insertSession(newSeedSession)

        LogManager.service("Created new SEED session: $newSeedSessionId", "DEBUG")

        if (token.isCancelled) return OperationResult.cancelled()

        // Duplicate all messages to new SEED session
        sourceMessages.forEach { sourceMessage ->
            val newMessage = sourceMessage.copy(
                id = UUID.randomUUID().toString(),
                sessionId = newSeedSessionId,
                timestamp = now + sourceMessages.indexOf(sourceMessage) // Preserve order with slight offset
            )
            dao.insertMessage(newMessage)
        }

        LogManager.service("Duplicated ${sourceMessages.size} messages to new SEED session", "DEBUG")

        if (token.isCancelled) return OperationResult.cancelled()

        // Create new automation
        val newAutomationId = UUID.randomUUID().toString()
        val newName = "${sourceEntity.name} (copie)"

        val newAutomation = sourceEntity.copy(
            id = newAutomationId,
            name = newName,
            zoneId = targetZoneId, // Target zone, not source zone
            seedSessionId = newSeedSessionId,
            group = targetGroup, // Use target group (can be null for ungrouped)
            createdAt = now,
            updatedAt = now,
            lastExecutionId = null, // Reset execution history
            executionHistoryJson = json.encodeToString(emptyList<String>())
        )

        dao.insertAutomation(newAutomation)

        // Notify UI
        com.assistant.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully duplicated automation $automationId to $newAutomationId", "INFO")

        // Convert timestamp to ISO 8601 for output
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        return OperationResult.success(mapOf(
            "automation_id" to newAutomationId,
            "source_automation_id" to automationId,
            "name" to newName,
            "zone_id" to newAutomation.zoneId,
            "seed_session_id" to newSeedSessionId,
            "created_at" to DateTimeConverter.timestampToISO(now, timezone)
        ))
    }

    /**
     * Get automation by ID
     */
    private suspend fun getAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Getting automation: $automationId", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        val automation = entityToAutomation(entity)

        return OperationResult.success(mapOf(
            "automation" to automationToMap(automation)
        ))
    }

    /**
     * Get automation by seed session ID
     * Useful for SEED editor to load associated automation
     */
    private suspend fun getAutomationBySeedSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val seedSessionId = params.optString("seed_session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_seed_session_required"))

        LogManager.service("Getting automation by seed session: $seedSessionId", "DEBUG")

        val entity = dao.getAutomationBySeedSession(seedSessionId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        val automation = entityToAutomation(entity)

        return OperationResult.success(mapOf(
            "automation" to automationToMap(automation)
        ))
    }

    /**
     * List automations for a zone
     */
    private suspend fun listAutomations(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))

        LogManager.service("Listing automations for zone: $zoneId", "DEBUG")

        val entities = dao.getAutomationsByZone(zoneId)
        val automations = entities.map { entityToAutomation(it) }

        return OperationResult.success(mapOf(
            "automations" to automations.map { automationToMap(it) },
            "count" to automations.size,
            "zone_id" to zoneId
        ))
    }

    /**
     * List all automations
     */
    private suspend fun listAllAutomations(token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("Listing all automations", "DEBUG")

        val entities = dao.getAllAutomations()
        val automations = entities.map { entityToAutomation(it) }

        return OperationResult.success(mapOf(
            "automations" to automations.map { automationToMap(it) },
            "count" to automations.size
        ))
    }

    /**
     * Enable or disable automation
     */
    private suspend fun setEnabled(
        params: JSONObject,
        token: CancellationToken,
        enabled: Boolean
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Setting automation $automationId enabled=$enabled", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        dao.setAutomationEnabled(automationId, enabled, System.currentTimeMillis())

        // Notify UI of automation change
        com.assistant.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully set automation $automationId enabled=$enabled", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "is_enabled" to enabled
        ))
    }

    /**
     * Execute automation manually
     * This delegates to AIOrchestrator.executeAutomation() with MANUAL trigger
     * Manual executions go through queue if slot occupied (priority after CHAT)
     */
    private suspend fun executeManual(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Manual execution requested for automation: $automationId", "INFO")

        // Verify automation exists
        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        // Delegate to AIOrchestrator V2
        // V2 handles session creation, trigger, and scheduling internally
        // For MANUAL: scheduledFor = click time
        try {
            val clickTime = System.currentTimeMillis()
            AIOrchestrator.executeAutomation(automationId, scheduledFor = clickTime)

            LogManager.service("Successfully triggered automation: $automationId", "INFO")

            return OperationResult.success(mapOf(
                "automation_id" to automationId,
                "status" to "triggered"
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to execute automation: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to execute automation: ${e.message}")
        }
    }

    /**
     * Convert entity to domain model
     */
    private fun entityToAutomation(entity: AutomationEntity): Automation {
        return Automation(
            id = entity.id,
            name = entity.name,
            zoneId = entity.zoneId,
            seedSessionId = entity.seedSessionId,
            schedule = entity.scheduleJson?.let { json.decodeFromString<ScheduleConfig>(it) },
            triggerIds = json.decodeFromString<List<String>>(entity.triggerIdsJson),
            catchUpWindowMinutes = entity.catchUpWindowMinutes,
            dismissOlderInstances = entity.dismissOlderInstances,
            providerId = entity.providerId,
            isEnabled = entity.isEnabled,
            group = entity.group,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastExecutionId = entity.lastExecutionId,
            executionHistory = json.decodeFromString<List<String>>(entity.executionHistoryJson)
        )
    }

    /**
     * Convert automation to map for CommandResult
     * Converts timestamps to ISO 8601 format for output
     */
    private fun automationToMap(automation: Automation): Map<String, Any?> {
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        return mapOf(
            "id" to automation.id,
            "name" to automation.name,
            "zone_id" to automation.zoneId,
            "seed_session_id" to automation.seedSessionId,
            "schedule" to automation.schedule?.let { json.encodeToString(it) },
            "trigger_ids" to automation.triggerIds,
            "catch_up_window_minutes" to automation.catchUpWindowMinutes,
            "dismiss_older_instances" to automation.dismissOlderInstances,
            "provider_id" to automation.providerId,
            "is_enabled" to automation.isEnabled,
            "created_at" to DateTimeConverter.timestampToISO(automation.createdAt, timezone),
            "updated_at" to DateTimeConverter.timestampToISO(automation.updatedAt, timezone),
            "last_execution_id" to automation.lastExecutionId,
            "execution_history" to automation.executionHistory,
            "group" to automation.group
        )
    }

    /**
     * Verbalize automation operations (not exposed to AI typically)
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}
