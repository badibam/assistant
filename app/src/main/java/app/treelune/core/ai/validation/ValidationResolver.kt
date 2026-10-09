package app.treelune.core.ai.validation

import android.content.Context
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.SessionValidation
import app.treelune.core.commands.CommandStatus
import app.treelune.core.config.ValidationConfig
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.services.AppConfigService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager

/**
 * Whether the actions of an AI message wait for the user's approval, and what the approval shows
 * (docs/design/validation.md).
 *
 * Three levels, each guarding what it holds directly, nothing by default:
 * - the app (validation_config): the zones and the home screen's groups;
 * - a zone (its `validate`): its tools, their configs included, its tool groups, its variables;
 * - a tool (its `validate_data`): its entries.
 *
 * The session adds to them (SessionValidation: a level extended to all its objects), and the AI
 * may ask on its own (validation_request). An action is validated as soon as one of these asks.
 * What cannot be read to decide (a tool gone, a session unreadable) is asked too, saying why: an
 * unknown is never taken for a no.
 */
class ValidationResolver(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val appConfigService = AppConfigService(context)
    private val s = Strings.`for`(context = context)

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

        // The levels' settings; unreadable, every action is asked with the reason
        val levels = try {
            Levels(appConfigService.getValidationConfig(), loadSessionValidation(sessionId))
        } catch (e: Exception) {
            LogManager.aiService("ValidationResolver: settings unreadable: ${e.message}", "ERROR", e)
            null
        }

        val reasons = actions.map { action ->
            if (levels == null) s.shared("validation_reason_unknown").format(s.shared("validation_reason_settings_unreadable"))
            else try {
                reason(action, levels)
            } catch (e: Exception) {
                LogManager.aiService("ValidationResolver: cannot tell whether ${action.id} is protected: ${e.message}", "WARN", e)
                s.shared("validation_reason_unknown").format(e.message ?: "")
            }
        }

        if (!aiRequestedValidation && reasons.all { it == null }) return ValidationResult.NoValidation

        return ValidationResult.RequiresValidation(
            ValidationContext(
                aiMessageId = aiMessageId,
                actions = actions,
                verbalizedActions = actions.mapIndexed { i, action -> verbalize(action, reasons[i], aiRequestedValidation) }
            )
        )
    }

    /** What the app and the session ask for. */
    private class Levels(val app: ValidationConfig, val session: SessionValidation)

    /**
     * Why [action] waits for approval, null when nothing asks: the level holding what it touches,
     * or the session extending that level.
     *
     * @throws IllegalStateException when what it touches cannot be read
     */
    private suspend fun reason(action: DataCommand, levels: Levels): String? {
        val params = action.params
        return when (action.type) {
            // The app's level: the zones and the home screen's groups. A zone's tool groups are its own content.
            "CREATE_ZONE", "DELETE_ZONE", "UPDATE_APP_CONFIG" -> appReason(levels)
            "UPDATE_ZONE" -> {
                val zoneId = params["zone_id"] as? String ?: error(s.shared("validation_reason_no_target"))
                val ownSettings = params.keys.any { it != "zone_id" && it != "tool_groups" }
                (if (ownSettings) appReason(levels) else null)
                    ?: (if (params.containsKey("tool_groups")) zoneReason(zoneId, levels) else null)
            }

            // A zone's level: its tools, their configs, its variables
            "CREATE_TOOL" -> zoneReason(params["zone_id"] as? String ?: error(s.shared("validation_reason_no_target")), levels)
            "UPDATE_TOOL", "DELETE_TOOL" -> {
                val zoneId = toolZone(toolId(action))
                // A tool moved to another zone changes that zone's content too
                val target = (params["zone_id"] as? String)?.takeIf { it != zoneId }
                zoneReason(zoneId, levels) ?: target?.let { zoneReason(it, levels) }
            }
            "CREATE_VARIABLE" -> zoneReason(params["zone_id"] as? String ?: error(s.shared("validation_reason_no_target")), levels)
            "UPDATE_VARIABLE", "DELETE_VARIABLE" -> zoneReason(variableZone(params["variable_id"] as? String ?: error(s.shared("validation_reason_no_target"))), levels)

            // A tool's level: its entries, whatever writes them -- a stopwatch, an import, the
            // tool type's own operations
            "CREATE_DATA", "UPDATE_DATA", "DELETE_DATA", "START_DURATION", "STOP_DURATION", "IMPORT_DATA", "TOOL_OPERATION" ->
                dataReason(toolId(action), levels)

            // An action this resolver does not know is asked rather than let through
            else -> s.shared("validation_reason_unknown").format(action.type)
        }
    }

    private fun appReason(levels: Levels): String? = when {
        levels.app.validateApp -> s.shared("validation_reason_app")
        levels.session.app -> s.shared("validation_reason_session")
        else -> null
    }

    private suspend fun zoneReason(zoneId: String, levels: Levels): String? {
        val zone = read("zones.get", mapOf("zone_id" to zoneId), "zone")
        return when {
            zone["validate"] as? Boolean ?: error("zone $zoneId without its protection") -> s.shared("validation_reason_zone").format(zone["name"] as? String ?: "")
            levels.session.zones -> s.shared("validation_reason_session")
            else -> null
        }
    }

    private suspend fun dataReason(toolId: String, levels: Levels): String? {
        val tool = read("tools.get", mapOf("tool_instance_id" to toolId), "tool_instance")
        val config = tool["config"] as? Map<*, *> ?: error("tool $toolId without its config")
        return when {
            config[app.treelune.core.tools.ToolConfigSettings.VALIDATE_DATA] == true -> s.shared("validation_reason_tool").format(config["name"] as? String ?: "")
            levels.session.data -> s.shared("validation_reason_session")
            else -> null
        }
    }

    /** The zone tool [toolId] is in. */
    private suspend fun toolZone(toolId: String): String =
        read("tools.get", mapOf("tool_instance_id" to toolId), "tool_instance")["zone_id"] as? String ?: error("tool $toolId without its zone")

    /** The zone variable [variableId] is in. */
    private suspend fun variableZone(variableId: String): String =
        read("variables.get", mapOf("variable_id" to variableId), "variable")["zone_id"] as? String ?: error("variable $variableId without its zone")

    /** The tool an action names. */
    private fun toolId(action: DataCommand): String =
        action.params["tool_instance_id"] as? String ?: error(s.shared("validation_reason_no_target"))

    /**
     * The object [key] of [operation]'s result.
     *
     * @throws IllegalStateException when it cannot be read: what it says is then unknown
     */
    private suspend fun read(operation: String, params: Map<String, Any>, key: String): Map<*, *> {
        val result = coordinator.processUserAction(operation, params)
        return result.data?.get(key) as? Map<*, *>
            ?: error(result.error ?: s.shared("validation_reason_unreadable").format(params.values.joinToString()))
    }

    /** What the session adds to the protections. */
    private suspend fun loadSessionValidation(sessionId: String): SessionValidation =
        SessionValidation.fromMap(read("ai_sessions.get_session", mapOf("session_id" to sessionId), "session"))

    /**
     * [action] as the approval shows it: its description, why it is asked ([reason], or the AI's
     * request when it asked and nothing else did), and the entries it writes.
     */
    private suspend fun verbalize(action: DataCommand, reason: String?, aiRequested: Boolean): VerbalizedAction {
        val description = ActionVerbalizerHelper.verbalizeAction(action, context)

        // The entries a write proposes, shown by their fields. A value that does not read
        // is said on the card: the action itself will be refused for it.
        val (entries, entriesError) = try {
            ProposedEntries.read(action, context) to null
        } catch (e: IllegalArgumentException) {
            LogManager.aiService("ValidationResolver: proposed values of ${action.id} do not read: ${e.message}", "WARN")
            emptyList<ProposedEntry>() to s.shared("validation_values_unreadable").format(e.message ?: "")
        }

        return VerbalizedAction(
            actionId = action.id,
            description = description,
            requiresWarning = reason != null,
            validationReason = reason ?: if (aiRequested) s.shared("validation_reason_ai_request") else null,
            entries = entries,
            entriesError = entriesError
        )
    }
}

/**
 * Result of validation check
 */
sealed class ValidationResult {
    object NoValidation : ValidationResult()
    data class RequiresValidation(val context: ValidationContext) : ValidationResult()
}
