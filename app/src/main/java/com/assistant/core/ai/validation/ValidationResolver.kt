package com.assistant.core.ai.validation

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.commands.CommandStatus
import com.assistant.core.config.ValidationConfig
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.services.AppConfigService
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Resolves validation hierarchy and generates context for UI
 *
 * Architecture: app > zone > outil > session > validationRequest (OR logic)
 *
 * This resolver:
 * 1. Analyzes each action against all config levels (app/zone/tool/session/AI)
 * 2. Determines if validation is required (any level = true)
 * 3. Generates verbalized actions with warnings and reasons
 * 4. Returns ValidationResult with ValidationContext for UI display
 *
 * Important: No circular dependency with AIOrchestrator
 * Uses Coordinator for all data access (no direct DAO access)
 */
class ValidationResolver(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val appConfigService = AppConfigService(context)

    /**
     * Determines if actions require validation and generates context
     *
     * @param actions ALL actions IA wants to execute (validated and non-validated)
     * @param sessionId ID of active IA session
     * @param aiMessageId ID of AI message containing actions (for persistence)
     * @param aiRequestedValidation true if IA explicitly requested validation (validationRequest: true)
     * @return ValidationResult.RequiresValidation with context OR NoValidation
     */
    suspend fun shouldValidate(
        actions: List<DataCommand>,
        sessionId: String,
        aiMessageId: String,
        aiRequestedValidation: Boolean
    ): ValidationResult {
        LogManager.aiService("ValidationResolver: shouldValidate for ${actions.size} actions, session=$sessionId, aiRequested=$aiRequestedValidation")

        // 1. Load configs
        val appValidationConfig = loadAppValidationConfig()
        val sessionRequiresValidation = loadSessionRequiresValidation(sessionId)

        LogManager.aiService("ValidationResolver: appConfig=$appValidationConfig, sessionRequires=$sessionRequiresValidation")

        // 2. Analyze each action individually
        val actionAnalyses = actions.map { action ->
            analyzeAction(action, appValidationConfig)
        }

        // 3. Determine if AT LEAST ONE action requires validation
        val requiresValidation = aiRequestedValidation ||
            sessionRequiresValidation ||
            actionAnalyses.any { it.requiresValidation }

        LogManager.aiService("ValidationResolver: requiresValidation=$requiresValidation (actions=${actionAnalyses.count { it.requiresValidation }})")

        if (!requiresValidation) {
            return ValidationResult.NoValidation
        }

        // 4. Verbalize ALL actions (validated and non-validated)
        val verbalizedActions = verbalizeActionsWithReasons(
            actions,
            actionAnalyses,
            sessionRequiresValidation,
            aiRequestedValidation
        )

        LogManager.aiService("ValidationResolver: Generated ${verbalizedActions.size} verbalized actions")

        return ValidationResult.RequiresValidation(
            ValidationContext(
                aiMessageId = aiMessageId,
                actions = actions,
                verbalizedActions = verbalizedActions
            )
        )
    }

    /**
     * Analyzes a single action according to configs and determines validation/warning/trigger
     * Implements hierarchy: app > zone > outil
     */
    private suspend fun analyzeAction(
        action: DataCommand,
        appConfig: ValidationConfig
    ): ActionAnalysis {
        val actionType = parseActionType(action)

        LogManager.aiService("ValidationResolver: Analyzing action ${action.id}, scope=${actionType.scope}, operation=${actionType.operation}")

        return when (actionType.scope) {
            ActionScope.APP_CONFIG -> {
                val requiresValidation = appConfig.validateAppConfigChanges
                ActionAnalysis(
                    actionId = action.id,
                    requiresValidation = requiresValidation,
                    requiresWarning = requiresValidation,  // Config = warning
                    trigger = if (requiresValidation) ValidationTrigger.APP_CONFIG else null
                )
            }

            ActionScope.ZONE_CONFIG -> {
                val zoneId = extractZoneId(action)
                val zoneConfig = loadZoneConfig(zoneId)
                val zoneRequires = zoneConfig.optBoolean("validateZoneConfigChanges", false)
                val appRequires = appConfig.validateZoneConfigChanges

                val requiresValidation = appRequires || zoneRequires
                ActionAnalysis(
                    actionId = action.id,
                    requiresValidation = requiresValidation,
                    requiresWarning = requiresValidation,  // Config = warning
                    trigger = when {
                        appRequires -> ValidationTrigger.APP_CONFIG
                        zoneRequires -> ValidationTrigger.ZONE_CONFIG
                        else -> null
                    },
                    zoneName = zoneConfig.optString("name").takeIf { it.isNotBlank() }
                )
            }

            ActionScope.TOOL_CONFIG -> {
                val toolInstanceId = extractToolInstanceId(action)
                val toolConfig = loadToolConfig(toolInstanceId)
                val zoneConfig = loadZoneConfigForTool(toolInstanceId)

                val toolRequires = toolConfig.optBoolean("validateConfig", false)
                val zoneRequires = zoneConfig.optBoolean("validateToolConfigChanges", false)
                val appRequires = appConfig.validateToolConfigChanges

                val requiresValidation = appRequires || zoneRequires || toolRequires
                ActionAnalysis(
                    actionId = action.id,
                    requiresValidation = requiresValidation,
                    requiresWarning = requiresValidation,  // Config = warning
                    trigger = when {
                        appRequires -> ValidationTrigger.APP_CONFIG
                        zoneRequires -> ValidationTrigger.ZONE_CONFIG
                        toolRequires -> ValidationTrigger.TOOL_CONFIG
                        else -> null
                    },
                    zoneName = zoneConfig.optString("name").takeIf { it.isNotBlank() },
                    toolName = toolConfig.optString("name").takeIf { it.isNotBlank() }
                )
            }

            ActionScope.TOOL_DATA -> {
                val toolInstanceId = extractToolInstanceId(action)
                val toolConfig = loadToolConfig(toolInstanceId)
                val zoneConfig = loadZoneConfigForTool(toolInstanceId)

                val toolRequires = toolConfig.optBoolean("validateData", false)
                val zoneRequires = zoneConfig.optBoolean("validateToolDataChanges", false)
                val appRequires = appConfig.validateToolDataChanges

                val requiresValidation = appRequires || zoneRequires || toolRequires
                ActionAnalysis(
                    actionId = action.id,
                    requiresValidation = requiresValidation,
                    requiresWarning = requiresValidation,  // Config = warning
                    trigger = when {
                        appRequires -> ValidationTrigger.APP_CONFIG
                        zoneRequires -> ValidationTrigger.ZONE_CONFIG
                        toolRequires -> ValidationTrigger.TOOL_CONFIG
                        else -> null
                    },
                    zoneName = zoneConfig.optString("name").takeIf { it.isNotBlank() },
                    toolName = toolConfig.optString("name").takeIf { it.isNotBlank() }
                )
            }
        }
    }

    /**
     * Verbalizes actions and adds validation reasons
     */
    private suspend fun verbalizeActionsWithReasons(
        actions: List<DataCommand>,
        analyses: List<ActionAnalysis>,
        sessionRequiresValidation: Boolean,
        aiRequestedValidation: Boolean
    ): List<VerbalizedAction> {
        val s = Strings.`for`(context = context)

        return actions.mapIndexed { index, action ->
            val analysis = analyses[index]

            // Verbalize action via ActionVerbalizerHelper
            val description = ActionVerbalizerHelper.verbalizeAction(action, context)

            // Determine validation reason (highest priority wins)
            val validationReason = when {
                // If action doesn't require validation by itself
                !analysis.requiresValidation && !sessionRequiresValidation && !aiRequestedValidation -> null

                // Otherwise, determine reason according to priority
                analysis.trigger == ValidationTrigger.APP_CONFIG ->
                    s.shared("validation_reason_app_config")

                analysis.trigger == ValidationTrigger.ZONE_CONFIG ->
                    s.shared("validation_reason_zone_config").format(analysis.zoneName ?: "")

                analysis.trigger == ValidationTrigger.TOOL_CONFIG ->
                    s.shared("validation_reason_tool_config").format(analysis.toolName ?: "")

                sessionRequiresValidation ->
                    s.shared("validation_reason_session")

                aiRequestedValidation ->
                    s.shared("validation_reason_ai_request")

                else -> null
            }

            LogManager.aiService("ValidationResolver: Action ${action.id} -> description='$description', warning=${analysis.requiresWarning}, reason=$validationReason")

            VerbalizedAction(
                actionId = action.id,
                description = description,
                requiresWarning = analysis.requiresWarning,
                validationReason = validationReason
            )
        }
    }

    // =============================
    // Config loading helpers
    // =============================

    /**
     * Loads app-level validation configuration
     */
    private suspend fun loadAppValidationConfig(): ValidationConfig {
        return try {
            appConfigService.getValidationConfig()
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: Failed to load app validation config: ${e.message}", "ERROR", e)
            ValidationConfig()  // Default to all false
        }
    }

    /**
     * Loads session requireValidation flag
     */
    private suspend fun loadSessionRequiresValidation(sessionId: String): Boolean {
        return try {
            val result = coordinator.processUserAction("ai_sessions.get_session", mapOf(
                "sessionId" to sessionId
            ))

            if (result.status == CommandStatus.SUCCESS) {
                val sessionData = result.data?.get("session") as? Map<*, *>
                (sessionData?.get("requireValidation") as? Boolean) ?: false
            } else {
                LogManager.aiService("ValidationResolver: Failed to load session: ${result.error}", "WARN")
                false
            }
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: Exception loading session: ${e.message}", "ERROR", e)
            false
        }
    }

    /**
     * Loads zone configuration JSON
     */
    private suspend fun loadZoneConfig(zoneId: String): JSONObject {
        return try {
            val result = coordinator.processUserAction("zones.get", mapOf(
                "zone_id" to zoneId
            ))

            if (result.status == CommandStatus.SUCCESS) {
                // zones.get returns "zone" map (zones don't have config_json - they're simpler entities)
                // For now, zones don't have validation configs, so we return empty JSON
                // Future: could add config_json field to Zone entity if needed
                LogManager.aiService("ValidationResolver: Zone $zoneId loaded (zones don't have config_json yet)", "DEBUG")
                JSONObject()
            } else {
                LogManager.aiService("ValidationResolver: Failed to load zone $zoneId: ${result.error}", "WARN")
                JSONObject()
            }
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: Exception loading zone config: ${e.message}", "ERROR", e)
            JSONObject()
        }
    }

    /**
     * Loads tool instance configuration JSON
     */
    private suspend fun loadToolConfig(toolInstanceId: String): JSONObject {
        return try {
            val result = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))

            if (result.status == CommandStatus.SUCCESS) {
                // tools.get returns "tool_instance" map containing config_json
                val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
                val configJson = toolInstance?.get("config_json") as? String
                if (configJson != null) {
                    JSONObject(configJson)
                } else {
                    LogManager.aiService("ValidationResolver: Tool $toolInstanceId has no config", "WARN")
                    JSONObject()
                }
            } else {
                LogManager.aiService("ValidationResolver: Failed to load tool $toolInstanceId: ${result.error}", "WARN")
                JSONObject()
            }
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: Exception loading tool config: ${e.message}", "ERROR", e)
            JSONObject()
        }
    }

    /**
     * Loads zone configuration for a tool instance (via tool's zone_id)
     */
    private suspend fun loadZoneConfigForTool(toolInstanceId: String): JSONObject {
        return try {
            // First get tool to find zone_id
            val toolResult = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))

            if (toolResult.status == CommandStatus.SUCCESS) {
                // tools.get returns "tool_instance" map containing zone_id
                val toolInstance = toolResult.data?.get("tool_instance") as? Map<*, *>
                val zoneId = toolInstance?.get("zone_id") as? String
                if (zoneId != null) {
                    loadZoneConfig(zoneId)
                } else {
                    LogManager.aiService("ValidationResolver: Tool $toolInstanceId has no zone_id", "WARN")
                    JSONObject()
                }
            } else {
                LogManager.aiService("ValidationResolver: Failed to load tool for zone lookup: ${toolResult.error}", "WARN")
                JSONObject()
            }
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: Exception loading zone config for tool: ${e.message}", "ERROR", e)
            JSONObject()
        }
    }

    // =============================
    // Action parsing helpers
    // =============================

    /**
     * Extracts action type and scope from DataCommand
     */
    private fun parseActionType(action: DataCommand): ParsedActionType {
        return when {
            action.type == "UPDATE_APP_CONFIG" ->
                ParsedActionType(ActionScope.APP_CONFIG, "update")

            action.type in listOf("CREATE_ZONE", "UPDATE_ZONE", "DELETE_ZONE") ->
                ParsedActionType(ActionScope.ZONE_CONFIG, extractOperation(action.type))

            action.type in listOf("CREATE_TOOL", "UPDATE_TOOL_CONFIG", "DELETE_TOOL") ->
                ParsedActionType(ActionScope.TOOL_CONFIG, extractOperation(action.type))

            action.type in listOf("CREATE_DATA", "UPDATE_DATA", "DELETE_DATA",
                                  "BATCH_CREATE_DATA", "BATCH_UPDATE_DATA", "BATCH_DELETE_DATA") ->
                ParsedActionType(ActionScope.TOOL_DATA, extractOperation(action.type))

            else -> {
                LogManager.aiService("ValidationResolver: Unknown action type ${action.type}, defaulting to TOOL_DATA", "WARN")
                ParsedActionType(ActionScope.TOOL_DATA, "unknown")
            }
        }
    }

    /**
     * Extracts operation from action type string
     */
    private fun extractOperation(type: String): String {
        return when {
            type.startsWith("CREATE") -> "create"
            type.startsWith("UPDATE") -> "update"
            type.startsWith("DELETE") -> "delete"
            type.startsWith("BATCH") -> type.removePrefix("BATCH_").lowercase()
            else -> "unknown"
        }
    }

    /**
     * Extracts zone_id from action params
     */
    private fun extractZoneId(action: DataCommand): String {
        return action.params["zone_id"] as? String
            ?: action.params["id"] as? String
            ?: action.params["zoneId"] as? String
            ?: ""
    }

    /**
     * Extracts tool_instance_id from action params
     */
    private fun extractToolInstanceId(action: DataCommand): String {
        return action.params["tool_instance_id"] as? String
            ?: action.params["toolInstanceId"] as? String
            ?: action.params["id"] as? String
            ?: ""
    }
}

// =============================
// Data classes
// =============================

/**
 * Result of action analysis
 * Contains all metadata needed to determine validation and reason
 */
internal data class ActionAnalysis(
    val actionId: String,
    val requiresValidation: Boolean,
    val requiresWarning: Boolean,  // true if validated by CONFIG (app/zone/tool)
    val trigger: ValidationTrigger?,  // null if no config validation
    val zoneName: String? = null,
    val toolName: String? = null
)

/**
 * Result of validation check
 */
sealed class ValidationResult {
    object NoValidation : ValidationResult()
    data class RequiresValidation(val context: ValidationContext) : ValidationResult()
}

/**
 * Action scope in validation hierarchy
 */
enum class ActionScope {
    APP_CONFIG,      // Modifying app configuration
    ZONE_CONFIG,     // Modifying zone configuration
    TOOL_CONFIG,     // Modifying tool instance configuration
    TOOL_DATA        // Modifying tool data
}

/**
 * Parsed action type information
 */
internal data class ParsedActionType(
    val scope: ActionScope,
    val operation: String  // create/update/delete/unknown
)
