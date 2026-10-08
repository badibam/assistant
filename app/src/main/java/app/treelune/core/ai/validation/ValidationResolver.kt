package app.treelune.core.ai.validation

import app.treelune.core.utils.JsonUtils
import android.content.Context
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.commands.CommandStatus
import app.treelune.core.config.ValidationConfig
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.services.AppConfigService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * Resolves validation hierarchy and generates context for UI
 *
 * Architecture: app > outil > session > validationRequest (OR logic)
 *
 * This resolver:
 * 1. Analyzes each action against all config levels (app/tool/session/AI)
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
     * Implements hierarchy: app > outil
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
                val requiresValidation = appConfig.validateZoneConfigChanges
                ActionAnalysis(
                    actionId = action.id,
                    requiresValidation = requiresValidation,
                    requiresWarning = requiresValidation,
                    trigger = if (requiresValidation) ValidationTrigger.APP_CONFIG else null
                )
            }

            ActionScope.VARIABLES -> {
                val requiresValidation = appConfig.validateVariableChanges
                ActionAnalysis(
                    actionId = action.id,
                    requiresValidation = requiresValidation,
                    requiresWarning = requiresValidation,
                    trigger = if (requiresValidation) ValidationTrigger.APP_CONFIG else null
                )
            }

            ActionScope.TOOL_CONFIG -> {
                val operation = actionType.operation
                val toolInstanceId = extractToolInstanceId(action)

                when (operation) {
                    "create" -> {
                        // CREATE_TOOL: Tool doesn't exist yet, check app config only
                        val appRequires = appConfig.validateToolConfigChanges
                        ActionAnalysis(
                            actionId = action.id,
                            requiresValidation = appRequires,
                            requiresWarning = appRequires,
                            trigger = if (appRequires) ValidationTrigger.APP_CONFIG else null
                        )
                    }
                    else -> {
                        // UPDATE_TOOL, DELETE_TOOL: Tool exists, check app + tool configs
                        val toolConfig = loadToolSettings(toolInstanceId)

                        val toolRequires = toolConfig.boolean("validate_config")
                        val appRequires = appConfig.validateToolConfigChanges

                        val requiresValidation = appRequires || toolRequires
                        ActionAnalysis(
                            actionId = action.id,
                            requiresValidation = requiresValidation,
                            requiresWarning = requiresValidation,
                            trigger = when {
                                appRequires -> ValidationTrigger.APP_CONFIG
                                toolRequires -> ValidationTrigger.TOOL_CONFIG
                                else -> null
                            },
                            toolName = toolConfig.string("name")
                        )
                    }
                }
            }

            ActionScope.TOOL_DATA -> {
                val toolInstanceId = extractToolInstanceId(action)
                val toolConfig = loadToolSettings(toolInstanceId)

                val toolRequires = toolConfig.boolean("validate_data")
                val appRequires = appConfig.validateToolDataChanges

                val requiresValidation = appRequires || toolRequires
                ActionAnalysis(
                    actionId = action.id,
                    requiresValidation = requiresValidation,
                    requiresWarning = requiresValidation,  // Config = warning
                    trigger = when {
                        appRequires -> ValidationTrigger.APP_CONFIG
                        toolRequires -> ValidationTrigger.TOOL_CONFIG
                        else -> null
                    },
                    toolName = toolConfig.string("name")
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


                analysis.trigger == ValidationTrigger.TOOL_CONFIG ->
                    s.shared("validation_reason_tool_config").format(analysis.toolName ?: "")

                sessionRequiresValidation ->
                    s.shared("validation_reason_session")

                aiRequestedValidation ->
                    s.shared("validation_reason_ai_request")

                else -> null
            }

            LogManager.aiService("ValidationResolver: Action ${action.id} -> description='$description', warning=${analysis.requiresWarning}, reason=$validationReason")

            // The entries a write proposes, shown by their fields. A value that does not read
            // is said on the card: the action itself will be refused for it.
            val (entries, entriesError) = try {
                proposedEntries(action) to null
            } catch (e: IllegalArgumentException) {
                LogManager.aiService("ValidationResolver: proposed values of ${action.id} do not read: ${e.message}", "WARN")
                emptyList<ProposedEntry>() to s.shared("validation_values_unreadable").format(e.message ?: "")
            }

            VerbalizedAction(
                actionId = action.id,
                description = description,
                requiresWarning = analysis.requiresWarning,
                validationReason = validationReason,
                entries = entries,
                entriesError = entriesError
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
                "session_id" to sessionId
            ))

            if (result.status == CommandStatus.SUCCESS) {
                val sessionData = result.data?.get("session") as? Map<*, *>
                (sessionData?.get("require_validation") as? Boolean) ?: false
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
     * The config of the tool [toolInstanceId], read through its declaration.
     *
     * @throws IllegalStateException when the tool cannot be read: whether it asks for validation
     *   is then unknown, and an unknown is not taken for a no
     */
    private suspend fun loadToolSettings(toolInstanceId: String): app.treelune.core.fields.settings.SettingValues {
        val (tooltype, config) = loadTool(toolInstanceId)
        return app.treelune.core.tools.ToolConfigSettings.read(tooltype, config, context)
    }

    /** The entries [action] proposes to write, read with the fields of its tool. */
    private suspend fun proposedEntries(action: DataCommand): List<ProposedEntry> = ProposedEntries.read(action, context)

    /**
     * The tooltype and stored config of the tool [toolInstanceId].
     *
     * @throws IllegalStateException when the tool cannot be read
     */
    private suspend fun loadTool(toolInstanceId: String): Pair<String, JSONObject> {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val tooltype = toolInstance?.get("tooltype") as? String
        if (result.status != CommandStatus.SUCCESS || config == null || tooltype == null) {
            throw IllegalStateException("Cannot read tool $toolInstanceId to know whether it asks for validation: ${result.error}")
        }
        return tooltype to config
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

            action.type in listOf("CREATE_VARIABLE", "UPDATE_VARIABLE", "DELETE_VARIABLE") ->
                ParsedActionType(ActionScope.VARIABLES, extractOperation(action.type))

            action.type in listOf("CREATE_ZONE", "UPDATE_ZONE", "DELETE_ZONE") ->
                ParsedActionType(ActionScope.ZONE_CONFIG, extractOperation(action.type))

            action.type in listOf("CREATE_TOOL", "UPDATE_TOOL", "DELETE_TOOL") ->
                ParsedActionType(ActionScope.TOOL_CONFIG, extractOperation(action.type))

            action.type in listOf("CREATE_DATA", "UPDATE_DATA", "DELETE_DATA") ->
                ParsedActionType(ActionScope.TOOL_DATA, extractOperation(action.type))

            // Starting or stopping a stopwatch writes the entry, as any update of it does
            action.type in listOf("START_DURATION", "STOP_DURATION") ->
                ParsedActionType(ActionScope.TOOL_DATA, "update")

            // An import creates and updates the tool's entries, and may add fields to it: its data validation applies
            action.type == "IMPORT_DATA" ->
                ParsedActionType(ActionScope.TOOL_DATA, "create")

            // A tool type's own operation writes its entries: the tool's data validation applies
            action.type == "TOOL_OPERATION" ->
                ParsedActionType(ActionScope.TOOL_DATA, "update")

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
            else -> "unknown"
        }
    }

    /**
     * Extracts tool_instance_id from action params
     */
    private fun extractToolInstanceId(action: DataCommand): String {
        return action.params["tool_instance_id"] as? String
            ?: action.params["tool_instance_id"] as? String
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
    val requiresWarning: Boolean,  // true if validated by CONFIG (app/tool)
    val trigger: ValidationTrigger?,  // null if no config validation
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
    VARIABLES,       // Creating, changing, deleting variables
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
