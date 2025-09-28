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
     * Execute a list of ExecutableCommands and collect results
     *
     * Executes all commands regardless of individual failures (no cascade failure for data queries).
     * Cascade failure is handled separately in AICommandProcessor for action commands.
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

        val results = mutableListOf<String>()
        var successCount = 0

        for ((index, command) in commands.withIndex()) {
            LogManager.aiPrompt("Executing command ${index + 1}/${commands.size}: ${command.resource}.${command.operation}")

            val result = executeCommand(command)

            if (result != null) {
                results.add(result)
                successCount++
                LogManager.aiPrompt("Command ${index + 1} succeeded")
            } else {
                LogManager.aiPrompt("Command ${index + 1} failed - continuing with remaining commands")
            }
        }

        // TODO: Validate token sizes for results
        // TODO: Handle cross-level deduplication with previousCommands
        // TODO: Format results properly for prompt inclusion

        val combinedResult = results.joinToString("\n\n")
        LogManager.aiPrompt("CommandExecutor completed for $level: $successCount/${commands.size} commands successful, ${combinedResult.length} chars total")

        return combinedResult
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
                val commandString = "${command.resource}.${command.operation}"
                val paramsMap = command.params
                val paramsJson = org.json.JSONObject()
                paramsMap.forEach { (key, value) -> paramsJson.put(key, value) }

                LogManager.aiPrompt("Calling coordinator: $commandString with params: $paramsJson")

                val result = coordinator.processUserAction(commandString, paramsMap)

                if (result.isSuccess) {
                    // TODO: Format result data properly for prompt inclusion
                    // For now, return basic JSON representation
                    val data = result.data
                    if (data != null && data.isNotEmpty()) {
                        val formattedResult = formatResultForPrompt(command, data)
                        LogManager.aiPrompt("Command succeeded, formatted result: ${formattedResult.length} chars")
                        return@withContext formattedResult
                    } else {
                        LogManager.aiPrompt("Command succeeded but returned empty data")
                        return@withContext ""
                    }
                } else {
                    LogManager.aiPrompt("Command failed: ${result.error}", "WARN")
                    return@withContext null
                }

            } catch (e: Exception) {
                LogManager.aiPrompt("Command execution failed: ${e.message}", "ERROR", e)
                return@withContext null
            }
        }
    }

    /**
     * Format command result for prompt inclusion
     * TODO: Implement proper formatting based on command type and data structure
     */
    private fun formatResultForPrompt(command: ExecutableCommand, data: Map<String, Any>): String {
        // TODO: Implement command-specific formatting
        // - SCHEMA results: format as readable schema definition
        // - TOOL_DATA results: format as structured data with metadata
        // - CONFIG results: format as key-value pairs
        // - LIST results: format as bulleted lists

        LogManager.aiPrompt("formatResultForPrompt() - STUB implementation for ${command.resource}.${command.operation}")

        // Basic JSON formatting for now
        return try {
            org.json.JSONObject(data).toString(2)
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to format result: ${e.message}", "WARN")
            data.toString()
        }
    }
}