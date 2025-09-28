package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.ai.utils.TokenCalculator
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes ExecutableCommands and validates result sizes with cascade failure support
 *
 * Replaces QueryExecutor as part of the command system restructure.
 * Used by both PromptManager (for Level 2/4) and AI command processing.
 *
 * Core responsibilities:
 * - Execute ExecutableCommand configurations to get actual data results
 * - Implement cascade failure logic (stop on first failure)
 * - Validate result token sizes against configured limits
 * - Log warnings for oversized results
 * - Return formatted content for prompt inclusion
 */
class CommandExecutor(private val context: Context) {

    private val coordinator = Coordinator(context)

    /**
     * Execute a list of ExecutableCommands with cascade failure support
     *
     * NEW: Implements cascade failure - if any command fails, stops execution
     * of remaining commands to prevent incomplete data sets.
     *
     * @param commands The commands to execute for this level
     * @param level The level name for logging purposes
     * @param previousCommands Commands already executed in previous levels for deduplication
     */
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        level: String = "unknown",
        previousCommands: List<ExecutableCommand> = emptyList()
    ): String {
        LogManager.aiPrompt("CommandExecutor executing ${commands.size} commands for $level")

        if (commands.isEmpty()) {
            LogManager.aiPrompt("No commands to execute, returning empty content")
            return ""
        }

        // TODO: Implement full command execution with cascade failure
        // 1. Process commands sequentially
        // 2. On first failure, stop execution (cascade failure)
        // 3. Validate token sizes for successful results
        // 4. Format results for prompt inclusion
        // 5. Handle cross-level deduplication if needed

        LogManager.aiPrompt("TODO: CommandExecutor.executeCommands() - cascade failure logic needed")
        return ""
    }

    /**
     * Execute a single ExecutableCommand through coordinator
     *
     * Routes to coordinator using resource.operation pattern and returns
     * formatted content for prompt inclusion.
     */
    private suspend fun executeCommand(command: ExecutableCommand): String? {
        LogManager.aiPrompt("Executing ExecutableCommand: resource=${command.resource}, operation=${command.operation}")

        return withContext(Dispatchers.IO) {
            try {
                // TODO: Implement single command execution
                // 1. Call coordinator.processUserAction("${command.resource}.${command.operation}", command.params)
                // 2. Check result.isSuccess
                // 3. Format result data for prompt inclusion
                // 4. Return formatted content or null on failure

                LogManager.aiPrompt("TODO: ExecutableCommand execution through coordinator")
                null

            } catch (e: Exception) {
                LogManager.aiPrompt("Command execution failed: ${e.message}", "ERROR", e)
                null
            }
        }
    }
}