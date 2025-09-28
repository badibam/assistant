package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.utils.LogManager

/**
 * User command processor for transforming DataCommands from user enrichments
 *
 * Core responsibilities:
 * - Resolve relative periods to absolute timestamps for chat context
 * - Transform UI abstractions to concrete coordinator parameters
 * - Maintain conversation context stability across days
 * - Convert enrichment parameters to service-compatible formats
 *
 * Used in chat sessions where periods like "cette semaine" should be fixed
 * to absolute timestamps to ensure conversation consistency.
 */
class UserCommandProcessor(private val context: Context) {

    /**
     * Process user commands from enrichment blocks into executable commands
     *
     * @param commands List of DataCommands from user enrichments
     * @return List of ExecutableCommands ready for coordinator dispatch
     */
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand> {
        LogManager.aiPrompt("UserCommandProcessor processing ${commands.size} user commands")

        // TODO: Implement full user command processing pipeline
        // 1. Resolve relative parameters to absolute timestamps for chat stability
        // 2. Transform UI abstractions to coordinator-compatible parameters
        // 3. Handle parameter naming differences (tool_instance_id vs toolInstanceId)
        // 4. Apply chat-specific resolution logic

        LogManager.aiPrompt("TODO: UserCommandProcessor.processCommands() - returning empty list for now")
        return emptyList()
    }
}