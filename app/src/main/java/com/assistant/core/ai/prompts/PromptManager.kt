package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.processing.UserCommandProcessor
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager

/**
 * Singleton PromptManager implementing 3-level prompt system
 * Level 1: System documentation (very stable, includes AI limits)
 * Level 2: User data (stable, always_send tools)
 * Level 3: Application state (moderately stable, zones + tool instances)
 *
 * Level 4 has been REMOVED - enrichments are now stored as separate SystemMessages
 * and fused by the provider during prompt construction.
 */
object PromptManager {

    /**
     * Build prompt data for AI session
     * Generates L1-L3 (always fresh, never stored) and loads session messages
     *
     * @param sessionId The session ID to load messages from
     * @return PromptData ready for provider transformation
     */
    suspend fun buildPromptData(sessionId: String, context: Context): PromptData {
        LogManager.aiPrompt("Building prompt data for session $sessionId", "INFO")

        val coordinator = Coordinator(context)
        val commandExecutor = CommandExecutor(context)
        val userCommandProcessor = UserCommandProcessor(context)

        // 1. Load session to determine type
        val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))
        if (!sessionResult.isSuccess) {
            LogManager.aiPrompt("Failed to load session: ${sessionResult.error}", "ERROR")
            throw IllegalStateException("Failed to load session $sessionId: ${sessionResult.error}")
        }

        val sessionData = sessionResult.data?.get("session") as? Map<*, *>
            ?: throw IllegalStateException("No session data found")

        val sessionTypeStr = sessionData["type"] as? String ?: "CHAT"
        val sessionType = SessionType.valueOf(sessionTypeStr)

        // 2. Build Level 1 (DOC + limits) using PromptChunks
        LogManager.aiPrompt("Building Level 1 (DOC)", "DEBUG")

        // Generate complete documentation with PromptChunks (schemas included via placeholders)
        val level1Content = PromptChunks.buildLevel1StaticDoc(
            context = context,
            sessionType = sessionType,
            config = PromptChunks.ChunkConfig(
                includeDegree1 = true,
                includeDegree2 = true,
                includeDegree3 = false  // Disabled for token economy
            )
        )

        // 3. Build Level 2 (USER DATA - always_send tools)
        LogManager.aiPrompt("Building Level 2 (USER DATA)", "DEBUG")
        val level2Commands = buildLevel2Commands(context)
        val level2Executable = userCommandProcessor.processCommands(level2Commands)
        val level2Result = commandExecutor.executeCommands(
            commands = level2Executable,
            messageType = SystemMessageType.DATA_ADDED,
            level = "L2"
        )
        val level2Content = formatLevel("Level 2: User Data", "", level2Result.promptResults)

        // Note: Level 3 has been removed - AI will use APP_STATE command when needed

        // 5. Load session messages (raw, provider will transform them)
        val messagesData = sessionResult.data?.get("messages") as? List<*> ?: emptyList<Any>()
        val allMessages = parseSessionMessages(messagesData)

        // 6. Filter out messages excluded from prompt:
        //    - NETWORK_ERROR, SESSION_TIMEOUT, and INTERRUPTED (audit only)
        //    - excludeFromPrompt=true (UI-only messages like postText success)
        val sessionMessages = allMessages.filter { message ->
            val type = message.systemMessage?.type
            val isSystemError = type == SystemMessageType.NETWORK_ERROR ||
                               type == SystemMessageType.SESSION_TIMEOUT ||
                               type == SystemMessageType.INTERRUPTED
            !isSystemError && !message.excludeFromPrompt
        }

        val filtered = allMessages.size - sessionMessages.size
        if (filtered > 0) {
            LogManager.aiPrompt("Filtered $filtered messages from prompt", "DEBUG")
        }

        LogManager.aiPrompt("Prompt data built: L1=${estimateTokens(level1Content)} tokens, L2=${estimateTokens(level2Content)} tokens, ${sessionMessages.size} messages", "INFO")

        return PromptData(
            level1Content = level1Content,
            level2Content = level2Content,
            sessionMessages = sessionMessages
        )
    }

    /**
     * Parse raw message data from DB into SessionMessage objects
     */
    private fun parseSessionMessages(messagesData: List<*>): List<SessionMessage> {
        return messagesData.mapNotNull { msg ->
            if (msg !is Map<*, *>) return@mapNotNull null

            val id = msg["id"] as? String ?: return@mapNotNull null
            val timestamp = (msg["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null
            val senderStr = msg["sender"] as? String ?: return@mapNotNull null
            val sender = try {
                MessageSender.valueOf(senderStr)
            } catch (e: Exception) {
                LogManager.aiPrompt("Invalid sender: $senderStr", "WARN")
                return@mapNotNull null
            }

            // Parse richContent if present
            val richContent = (msg["richContentJson"] as? String)?.let { json ->
                try {
                    parseRichMessage(json)
                } catch (e: Exception) {
                    LogManager.aiPrompt("Failed to parse richContentJson: ${e.message}", "WARN")
                    null
                }
            }

            // Parse aiMessage if present
            val aiMessage = (msg["aiMessageJson"] as? String)?.let { json ->
                try {
                    parseAIMessage(json)
                } catch (e: Exception) {
                    LogManager.aiPrompt("Failed to parse aiMessageJson: ${e.message}", "WARN")
                    null
                }
            }

            // Parse systemMessage if present
            val systemMessage = (msg["systemMessageJson"] as? String)?.let { json ->
                try {
                    parseSystemMessage(json)
                } catch (e: Exception) {
                    LogManager.aiPrompt("Failed to parse systemMessageJson: ${e.message}", "WARN")
                    null
                }
            }

            SessionMessage(
                id = id,
                timestamp = timestamp,
                sender = sender,
                richContent = richContent,
                textContent = msg["textContent"] as? String,
                aiMessage = aiMessage,
                aiMessageJson = msg["aiMessageJson"] as? String,
                systemMessage = systemMessage,
                executionMetadata = null, // TODO: Parse when implementing automation
                excludeFromPrompt = msg["excludeFromPrompt"] as? Boolean ?: false
            )
        }
    }

    /**
     * Parse RichMessage from JSON
     * TODO: Implement full deserialization when needed
     */
    private fun parseRichMessage(json: String): RichMessage {
        // Stub for now - provider only needs linearText
        val jsonObj = org.json.JSONObject(json)
        val linearText = jsonObj.optString("linearText", "")

        return RichMessage(
            segments = emptyList(), // Provider doesn't need segments
            linearText = linearText,
            dataCommands = emptyList() // Provider doesn't need dataCommands
        )
    }

    /**
     * Parse AIMessage from JSON
     * TODO: Implement full deserialization when needed
     */
    private fun parseAIMessage(json: String): AIMessage {
        // Stub for now - provider only needs JSON string
        return AIMessage(
            preText = "",
            validationRequest = null,
            dataCommands = null,
            actionCommands = null,
            postText = null,
            keepControl = null,
            communicationModule = null
        )
    }

    /**
     * Parse SystemMessage from JSON
     */
    private fun parseSystemMessage(json: String): SystemMessage {
        val jsonObj = org.json.JSONObject(json)
        val typeStr = jsonObj.optString("type", "DATA_ADDED")
        val type = try {
            SystemMessageType.valueOf(typeStr)
        } catch (e: Exception) {
            SystemMessageType.DATA_ADDED
        }

        val summary = jsonObj.optString("summary", "")
        val formattedData = jsonObj.optString("formattedData", null)

        // Parse commandResults
        val commandResultsArray = jsonObj.optJSONArray("commandResults")
        val commandResults = mutableListOf<CommandResult>()
        if (commandResultsArray != null) {
            for (i in 0 until commandResultsArray.length()) {
                val resultObj = commandResultsArray.optJSONObject(i) ?: continue
                val command = resultObj.optString("command", "")
                val statusStr = resultObj.optString("status", "FAILED")
                val status = try {
                    CommandStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    CommandStatus.FAILED
                }
                val details = resultObj.optString("details", null)

                commandResults.add(
                    CommandResult(
                        command = command,
                        status = status,
                        details = details
                    )
                )
            }
        }

        return SystemMessage(
            type = type,
            commandResults = commandResults,
            summary = summary,
            formattedData = formattedData
        )
    }

    /**
     * Format level content with title, static doc, and command results
     */
    private fun formatLevel(title: String, staticContent: String, results: List<PromptCommandResult>): String {
        val sb = StringBuilder()
        sb.appendLine("## $title")
        sb.appendLine()

        if (staticContent.isNotEmpty()) {
            sb.appendLine(staticContent)
            sb.appendLine()
        }

        for (result in results) {
            if (result.dataTitle.isNotEmpty()) {
                sb.appendLine("### ${result.dataTitle}")
                sb.appendLine()
            }
            if (result.formattedData.isNotEmpty()) {
                sb.appendLine(result.formattedData)
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    // === Level Command Builders ===
    // Note: Level 1 now uses PromptChunks - no commands needed

    /**
     * Generate Level 2 commands: User data (tool_data with always_send flag)
     */
    private suspend fun buildLevel2Commands(context: Context): List<DataCommand> {
        LogManager.aiPrompt("Building Level 2 commands (USER DATA)", "DEBUG")

        val commands = mutableListOf<DataCommand>()
        val coordinator = Coordinator(context)

        // Get all tool instances
        val result = coordinator.processUserAction("tools.list_all", emptyMap())

        if (!result.isSuccess) {
            LogManager.aiPrompt("Failed to retrieve tool instances for Level 2: ${result.error}", "WARN")
            return emptyList()
        }

        val toolInstancesData = result.data?.get("tool_instances") as? List<*>
        if (toolInstancesData == null) {
            LogManager.aiPrompt("No tool instances found for Level 2", "DEBUG")
            return emptyList()
        }

        // Filter tool instances with always_send=true
        for (toolInstanceMap in toolInstancesData) {
            if (toolInstanceMap !is Map<*, *>) continue

            val toolInstanceId = toolInstanceMap["id"] as? String ?: continue
            val configJson = toolInstanceMap["config_json"] as? String ?: continue

            try {
                val config = org.json.JSONObject(configJson)
                val alwaysSend = config.optBoolean("always_send", false)

                if (alwaysSend) {
                    // Generate TOOL_DATA command for this instance (all data, no filters)
                    commands.add(
                        DataCommand(
                            id = "tool_data_always_send_$toolInstanceId",
                            type = "TOOL_DATA",
                            params = mapOf("id" to toolInstanceId),
                            isRelative = false
                        )
                    )
                    LogManager.aiPrompt("Added always_send tool instance to Level 2: $toolInstanceId", "DEBUG")
                }
            } catch (e: Exception) {
                LogManager.aiPrompt("Failed to parse config for tool instance $toolInstanceId: ${e.message}", "WARN")
            }
        }

        LogManager.aiPrompt("Level 2: Generated ${commands.size} commands for always_send tool instances", "DEBUG")
        return commands
    }


    // === Utilities ===

    /**
     * Estimate token count from text
     * Rough estimation: 1 token ≈ 4 characters for most languages
     */
    fun estimateTokens(text: String): Int {
        return text.length / 4
    }
}
