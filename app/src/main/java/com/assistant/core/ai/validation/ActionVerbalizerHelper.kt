package com.assistant.core.ai.validation

import android.content.Context
import com.assistant.core.ai.data.DataCommand
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
 * - Transforms to ExecutableCommand via CommandTransformer
 * - Retrieves service via ServiceRegistry
 * - Calls service.verbalize() to generate description
 *
 * Usage: ValidationResolver uses this to verbalize all actions before displaying to user
 */
object ActionVerbalizerHelper {

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
            val executableCommands = CommandTransformer.transformToExecutable(listOf(action), context)

            if (executableCommands.isEmpty()) {
                return "Action: ${action.type}"
            }

            val executableCommand = executableCommands.first()

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
