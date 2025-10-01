package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.utils.LogManager

/**
 * AI command processor for validating and processing commands from AI responses
 *
 * Core responsibilities:
 * - Validate security constraints for AI-generated commands
 * - Enforce data access limits and token management
 * - Process both dataCommands (queries) and actionCommands (actions)
 * - Apply automation-specific resolution for relative parameters
 * - Manage execution permissions and safety checks
 * - Implement cascade failure logic for action commands (stop on first action failure)
 */
class AICommandProcessor(private val context: Context) {

    /**
     * Process AI data commands (queries) with security validation
     *
     * @param commands List of DataCommands from AI for data retrieval
     * @return List of ExecutableCommands ready for coordinator dispatch
     */
    fun processDataCommands(commands: List<DataCommand>): List<ExecutableCommand> {
        LogManager.aiService("AICommandProcessor processing ${commands.size} data commands from AI", "DEBUG")

        // TODO: Add AI-specific validations for data commands
        // 1. Token limit enforcement per command (prevent excessive data loading)
        // 2. Data access permissions checking (verify AI can access requested data)
        // 3. Parameter sanitization (validate dates, IDs, limits)
        // 4. Rate limiting for repeated queries

        // Delegate transformation to shared CommandTransformer
        val executableCommands = CommandTransformer.transformToExecutable(commands, context)

        LogManager.aiService("AICommandProcessor generated ${executableCommands.size} executable data commands", "DEBUG")
        return executableCommands
    }

    /**
     * Process AI action commands with strict security validation
     *
     * IMPORTANT: Actions use cascade failure logic - if any action fails during execution,
     * all subsequent actions are cancelled to prevent inconsistent application state.
     * This is different from data commands which continue on individual failures.
     *
     * @param commands List of DataCommands from AI for action execution
     * @return List of ExecutableCommands ready for coordinator dispatch
     */
    fun processActionCommands(commands: List<DataCommand>): List<ExecutableCommand> {
        LogManager.aiService("AICommandProcessor processing ${commands.size} action commands from AI", "DEBUG")

        // TODO: Implement AI action command strict validations
        // 1. Permission level checking (autonomous/validation_required/forbidden/ask_first)
        // 2. Action scope validation (verify action targets valid resources)
        // 3. Parameter sanitization and validation (prevent malicious data)
        // 4. Rate limiting and resource protection (prevent abuse)
        // 5. Batch operation limits (max items per batch)
        // 6. CASCADE FAILURE enforcement (handled at execution level by CommandExecutor)

        val executableCommands = mutableListOf<ExecutableCommand>()

        for (command in commands) {
            try {
                val executableCommand = transformActionCommand(command)
                executableCommand?.let { executableCommands.add(it) }
            } catch (e: Exception) {
                LogManager.aiService("Failed to transform action command ${command.type}: ${e.message}", "ERROR", e)
            }
        }

        LogManager.aiService("AICommandProcessor generated ${executableCommands.size} executable action commands", "DEBUG")
        return executableCommands
    }

    // ========================================================================================
    // Private Transformation Methods
    // ========================================================================================

    /**
     * Transform action command types to executable commands
     * Maps abstract AI action types to concrete resource.operation format
     *
     * Note: Parameter naming inconsistency exists between services:
     * - tools.* uses tool_instance_id (snake_case)
     * - tool_data.* uses toolInstanceId (camelCase)
     * This will be unified in a future refactoring
     */
    private fun transformActionCommand(command: DataCommand): ExecutableCommand? {
        return when (command.type) {
            // Tool data actions - batch operations by default (per AI.md line 182)
            "CREATE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_create",
                params = command.params
            )
            "UPDATE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_update",
                params = command.params
            )
            "DELETE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_delete",
                params = command.params
            )

            // Tool instance actions
            "CREATE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "create",
                params = command.params
            )
            "UPDATE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "update",
                params = command.params
            )
            "DELETE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "delete",
                params = command.params
            )

            // Zone actions
            "CREATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "create",
                params = command.params
            )
            "UPDATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "update",
                params = command.params
            )
            "DELETE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "delete",
                params = command.params
            )

            else -> {
                LogManager.aiService("Unknown action command type: ${command.type}", "WARN")
                null
            }
        }
    }
}