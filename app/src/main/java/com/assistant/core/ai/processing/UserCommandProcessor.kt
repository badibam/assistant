package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.utils.LogManager

/**
 * User command processor for transforming DataCommands from user enrichments
 *
 * Core responsibilities:
 * - Log user-specific command processing
 * - Delegate transformation to CommandTransformer (shared logic)
 * - Add user-specific validation if needed in future
 *
 * Used in chat sessions where periods like "cette semaine" are fixed
 * to absolute timestamps to ensure conversation consistency.
 */
class UserCommandProcessor(private val context: Context) {

    /**
     * Process user commands from enrichment blocks into executable commands
     *
     * @param commands List of DataCommands from user enrichments
     * @return List of ExecutableCommands ready for coordinator dispatch (errors logged but not returned for user commands)
     */
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand> {
        LogManager.aiPrompt("UserCommandProcessor processing ${commands.size} user commands", "DEBUG")

        // Delegate to shared transformer
        val result = CommandTransformer.transformToExecutable(commands, context)

        // Log errors for user commands (user can see results directly, no retry mechanism)
        if (result.errors.isNotEmpty()) {
            LogManager.aiPrompt("UserCommandProcessor errors: ${result.errors.joinToString("; ")}", "WARN")
        }

        LogManager.aiPrompt("UserCommandProcessor generated ${result.executableCommands.size} executable commands", "DEBUG")
        return result.executableCommands
    }
}