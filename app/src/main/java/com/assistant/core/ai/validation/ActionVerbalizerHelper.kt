package com.assistant.core.ai.validation

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.ai.processing.AICommandProcessor
import com.assistant.core.ai.processing.CommandTransformer
import com.assistant.core.coordinator.ServiceRegistry
import com.assistant.core.services.ExecutableService
import org.json.JSONObject

/**
 * Helper for verbalizing actions via services
 * Used by ValidationResolver to generate human-readable action descriptions
 *
 * Architecture:
 * - Takes DataCommand (AI command format)
 * - Transforms to ExecutableCommand via AICommandProcessor (for actions) or CommandTransformer (for queries)
 * - Retrieves service via ServiceRegistry
 * - Calls service.verbalize() to generate description
 *
 * Usage: ValidationResolver uses this to verbalize all actions before displaying to user
 */
object ActionVerbalizerHelper {

    // Action command types that AICommandProcessor handles
    private val ACTION_TYPES = setOf(
        "CREATE_DATA", "UPDATE_DATA", "DELETE_DATA",
        "CREATE_TOOL", "UPDATE_TOOL", "DELETE_TOOL",
        "CREATE_ZONE", "UPDATE_ZONE", "DELETE_ZONE"
    )

    /**
     * Verbalizes a single action command
     *
     * @param action The DataCommand to verbalize
     * @param context Android context for string resources
     * @return Human-readable description in substantive form (e.g., "Création de la zone \"Santé\"")
     */
    suspend fun verbalizeAction(
        action: DataCommand,
        context: Context
    ): String {
        return try {
            // Transform DataCommand to ExecutableCommand
            // Use AICommandProcessor for actions, CommandTransformer for queries
            val executableCommand: ExecutableCommand? = if (action.type in ACTION_TYPES) {
                val processor = AICommandProcessor(context)
                processor.transformActionForVerbalization(action)
            } else {
                val result = CommandTransformer.transformToExecutable(listOf(action), context)
                result.executableCommands.firstOrNull()
            }

            if (executableCommand == null) {
                return "Action: ${action.type}"
            }

            // Get service for this resource using ServiceRegistry instance
            val serviceRegistry = com.assistant.core.coordinator.ServiceRegistry(context)
            val service = serviceRegistry.getService(executableCommand.resource)

            // If service implements ExecutableService, call verbalize()
            if (service is ExecutableService) {
                service.verbalize(
                    executableCommand.operation,
                    JSONObject(executableCommand.params),
                    context
                )
            } else {
                // Fallback if service doesn't implement ExecutableService
                "Action: ${action.type}"
            }
        } catch (e: Exception) {
            // Fallback on error (log and return generic description)
            com.assistant.core.utils.LogManager.aiService(
                "Failed to verbalize action ${action.type}: ${e.message}",
                "ERROR",
                e
            )
            "Action: ${action.type}"
        }
    }
}
