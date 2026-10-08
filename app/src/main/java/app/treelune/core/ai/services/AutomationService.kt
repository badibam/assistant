package app.treelune.core.ai.services

import android.content.Context
import app.treelune.core.ai.data.Automation
import app.treelune.core.ai.data.AutomationSettings
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.ai.database.AutomationEntity
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.database.AppDatabase
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.grid.Groups
import app.treelune.core.grid.ToolPositions
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.ScheduleConfig
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
     *
     * Its settings (AutomationSettings) are given as their declaration describes them: "schedule"
     * an object, "catch_up" an object with the schedule and only with it.
     */
    private suspend fun createAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_zone_id_required"))
        val seedSessionId = params.optString("seed_session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_seed_session_required"))

        // Parse trigger IDs
        val triggerIdsArray = params.optJSONArray("trigger_ids") ?: JSONArray()
        val triggerIds = (0 until triggerIdsArray.length()).map { triggerIdsArray.getString(it) }

        // The app's own demo gives its ids; every other caller gets one made here
        val automationId = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }
        val now = System.currentTimeMillis()

        val entity = withSettings(AutomationEntity(
            id = automationId,
            name = "",
            zoneId = zoneId,
            seedSessionId = seedSessionId,
            scheduleJson = null,
            triggerIdsJson = json.encodeToString(triggerIds),
            catchUp = null,
            catchUpWindow = null,
            dismissOlderInstances = false,
            providerId = "",
            isEnabled = true,
            group = null,
            createdAt = now,
            updatedAt = now,
            lastExecutionId = null,
            executionHistoryJson = json.encodeToString(emptyList<String>())
        ), settingsGiven(params, null)).getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        groupRefusal(entity.group, zoneId)?.let { return OperationResult.error(it) }

        LogManager.service("Creating automation: name=${entity.name}, zoneId=$zoneId", "DEBUG")
        dao.insertAutomation(entity)

        LogManager.service("Successfully created automation: $automationId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "name" to entity.name,
            "zone_id" to zoneId,
            "created_at" to now
        ))
    }

    /**
     * Update existing automation
     *
     * A partial update: a setting left out keeps its value, a setting given as null is removed.
     */
    private suspend fun updateAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_automation_id_required"))

        LogManager.service("Updating automation: $automationId", "DEBUG")

        val entity = dao.getAutomationById(automationId)
            ?: return OperationResult.error(s.shared("error_automation_not_found"))

        val newZoneId = params.optString("zone_id").takeIf { it.isNotBlank() }

        // Parse trigger IDs if provided
        val triggerIdsArray = params.optJSONArray("trigger_ids")
        val triggerIdsJson = triggerIdsArray?.let { array -> json.encodeToString((0 until array.length()).map { array.getString(it) }) }
            ?: entity.triggerIdsJson

        // An automation changing zone without a group given leaves its group behind
        val settings = settingsGiven(params, settingsOf(entity))
        val emptiedGroup = entity.group?.takeIf { newZoneId != null && newZoneId != entity.zoneId && !params.has("group") }
        if (emptiedGroup != null) settings.remove("group")

        val updatedEntity = withSettings(entity.copy(
            zoneId = newZoneId ?: entity.zoneId,
            triggerIdsJson = triggerIdsJson,
            updatedAt = System.currentTimeMillis()
        ), settings).getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        groupRefusal(updatedEntity.group, updatedEntity.zoneId)?.let { return OperationResult.error(it) }

        dao.updateAutomation(updatedEntity)

        // Notify UI of automation change in affected zones
        if (newZoneId != null && newZoneId != entity.zoneId) {
            app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()
            LogManager.service("Automation $automationId moved from zone ${entity.zoneId} to $newZoneId", "DEBUG")
        }

        LogManager.service("Successfully updated automation: $automationId", "INFO")

        return OperationResult.success(mapOf(
            "automation_id" to automationId,
            "name" to updatedEntity.name,
            "zone_id" to updatedEntity.zoneId,
            "updated" to true
        ) + (emptiedGroup?.let { mapOf("group_emptied" to it) } ?: emptyMap()))
    }

    /**
     * Why [group] cannot be held by an automation of the zone [zoneId], whose tool groups it
     * names; null when it can (none, or one of them).
     */
    private suspend fun groupRefusal(group: String?, zoneId: String): String? {
        val zone = AppDatabase.getDatabase(context).zoneDao().getZoneById(zoneId)
            ?: return s.shared("service_error_zone_not_found")
        return Groups.refusal(group, ToolPositions.zoneGroups(zone.tool_groups), s)
    }

    /** The settings an automation stores, as its declaration describes them (AutomationSettings). */
    private fun settingsOf(entity: AutomationEntity): JSONObject = JSONObject().apply {
        put("name", entity.name)
        put("provider_id", entity.providerId)
        entity.group?.let { put("group", it) }
        put("is_enabled", entity.isEnabled)
        entity.scheduleJson?.let { put("schedule", JSONObject(it)) }
        entity.catchUp?.let { limit ->
            put("catch_up", JSONObject().apply {
                put("limit", limit)
                entity.catchUpWindow?.let { put("window", it) }
                put("dismiss_older_instances", entity.dismissOlderInstances)
            })
        }
    }

    /**
     * [stored] with the settings [params] give: a setting left out keeps its value, a setting
     * given as null is removed. Without [stored], a creation, only what is given.
     */
    private fun settingsGiven(params: JSONObject, stored: JSONObject?): JSONObject {
        val settings = stored?.let { JSONObject(it.toString()) } ?: JSONObject()
        listOf("name", "provider_id", "group", "is_enabled", "schedule", "catch_up").forEach { key ->
            if (!params.has(key)) return@forEach
            if (params.isNull(key)) settings.remove(key) else settings.put(key, params.get(key))
        }
        return settings
    }

    /**
     * [entity] holding [settings], once they are checked against the schema generated from their
     * declaration, and against the rule it cannot say: the catch-up settings come with a schedule
     * and only with it.
     */
    private fun withSettings(entity: AutomationEntity, settings: JSONObject): Result<AutomationEntity> {
        val checked = app.treelune.core.validation.SchemaValidator.validate(
            AutomationSettings.schema(context), app.treelune.core.utils.JsonUtils.toMap(settings), context)
        if (!checked.isValid) return Result.failure(IllegalArgumentException(checked.errorMessage))
        val schedule = settings.optJSONObject("schedule")
        val catchUp = settings.optJSONObject("catch_up")
        if (schedule != null && catchUp == null) return Result.failure(IllegalArgumentException(s.shared("automation_catch_up_required")))
        if (schedule == null && catchUp != null) return Result.failure(IllegalArgumentException(s.shared("automation_catch_up_without_schedule")))

        // A schedule the scheduler cannot read is refused here rather than failing each tick
        val scheduleJson = schedule?.toString()?.also {
            try {
                json.decodeFromString<ScheduleConfig>(it)
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Invalid schedule: ${e.message}"))
            }
        }
        return Result.success(entity.copy(
            name = settings.getString("name"),
            providerId = settings.getString("provider_id"),
            group = settings.optString("group").takeIf { it.isNotEmpty() },
            isEnabled = SettingValues(AutomationSettings.nodes(context), settings).boolean("is_enabled"),
            scheduleJson = scheduleJson,
            catchUp = catchUp?.getString("limit"),
            catchUpWindow = catchUp?.takeIf { it.has("window") }?.getLong("window"),
            // Without a schedule nothing is missed: the column holds its declared default
            dismissOlderInstances = SettingValues(AutomationSettings.catchUpNodes(context), catchUp ?: JSONObject()).boolean("dismiss_older_instances")
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
     * - Its name marked as a copy (copy_name)
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
        groupRefusal(targetGroup, targetZoneId)?.let { return OperationResult.error(it) }

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
            name = s.shared("copy_name").format(sourceEntity.name), // Named as the automation
            createdAt = now,
            lastActivity = now,
            isActive = false, // SEED sessions are never active
            endReason = null,
            automationId = null, // Will be set after automation is created
            phase = "IDLE",
            totalRoundtrips = 0,
            lastEventTime = now,
            lastUserInteractionTime = now
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
        val newName = s.shared("copy_name").format(sourceEntity.name)

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
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

        LogManager.service("Successfully duplicated automation $automationId to $newAutomationId", "INFO")

        // Convert timestamp to ISO 8601 for output
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        return OperationResult.success(mapOf(
            "automation_id" to newAutomationId,
            "source_automation_id" to automationId,
            "name" to newName,
            "zone_id" to newAutomation.zoneId,
            "seed_session_id" to newSeedSessionId,
            "created_at" to now
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

        return OperationResult.success(mapOf(
            "automation" to automationToMap(entity)
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

        return OperationResult.success(mapOf(
            "automation" to automationToMap(entity)
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
        return OperationResult.success(mapOf(
            "automations" to entities.map { automationToMap(it) },
            "count" to entities.size,
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
        return OperationResult.success(mapOf(
            "automations" to entities.map { automationToMap(it) },
            "count" to entities.size
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
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()

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
     * An automation as a result gives it: what locates it and its history, then its settings in
     * the form their declaration describes (settingsOf). Automation.fromResult reads it back.
     */
    private fun automationToMap(entity: AutomationEntity): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "zone_id" to entity.zoneId,
        "seed_session_id" to entity.seedSessionId,
        "trigger_ids" to json.decodeFromString<List<String>>(entity.triggerIdsJson),
        "created_at" to entity.createdAt,
        "updated_at" to entity.updatedAt,
        "last_execution_id" to entity.lastExecutionId,
        "execution_history" to json.decodeFromString<List<String>>(entity.executionHistoryJson)
    ) + app.treelune.core.utils.JsonUtils.toMap(settingsOf(entity))

    /**
     * Verbalize automation operations (not exposed to AI typically)
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}
