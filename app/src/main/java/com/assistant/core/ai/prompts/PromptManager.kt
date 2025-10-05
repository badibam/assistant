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

        // 2. Build Level 1 (DOC + limits)
        LogManager.aiPrompt("Building Level 1 (DOC)", "DEBUG")
        val level1Commands = buildLevel1Commands(sessionType, context)
        val level1Executable = userCommandProcessor.processCommands(level1Commands)
        val level1Result = commandExecutor.executeCommands(
            commands = level1Executable,
            messageType = SystemMessageType.DATA_ADDED,
            level = "L1"
        )
        val level1Content = formatLevel("Level 1: System Documentation", buildLevel1StaticDoc(sessionType, context), level1Result.promptResults)

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

        // 4. Build Level 3 (APP STATE - zones + tools)
        LogManager.aiPrompt("Building Level 3 (APP STATE)", "DEBUG")
        val level3Commands = buildLevel3Commands(context)
        val level3Executable = userCommandProcessor.processCommands(level3Commands)
        val level3Result = commandExecutor.executeCommands(
            commands = level3Executable,
            messageType = SystemMessageType.DATA_ADDED,
            level = "L3"
        )
        val level3Content = formatLevel("Level 3: Application State", "", level3Result.promptResults)

        // 5. Load session messages (raw, provider will transform them)
        val messagesData = sessionResult.data?.get("messages") as? List<*> ?: emptyList<Any>()
        val allMessages = parseSessionMessages(messagesData)

        // 6. Filter out NETWORK_ERROR and SESSION_TIMEOUT messages (audit only, not sent to AI)
        val sessionMessages = allMessages.filter { message ->
            val type = message.systemMessage?.type
            type != SystemMessageType.NETWORK_ERROR && type != SystemMessageType.SESSION_TIMEOUT
        }

        val filtered = allMessages.size - sessionMessages.size
        if (filtered > 0) {
            LogManager.aiPrompt("Filtered $filtered system error messages from prompt", "DEBUG")
        }

        LogManager.aiPrompt("Prompt data built: L1=${estimateTokens(level1Content)} tokens, L2=${estimateTokens(level2Content)} tokens, L3=${estimateTokens(level3Content)} tokens, ${sessionMessages.size} messages", "INFO")

        return PromptData(
            level1Content = level1Content,
            level2Content = level2Content,
            level3Content = level3Content,
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
                executionMetadata = null // TODO: Parse when implementing automation
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

    /**
     * Build Level 1 static documentation with AI limits
     */
    private fun buildLevel1StaticDoc(sessionType: SessionType, context: Context): String {
        LogManager.aiPrompt("Building Level 1 static documentation with AI limits", "DEBUG")

        val s = Strings.`for`(context = context)
        val sb = StringBuilder()

        // AI Role and introduction
        sb.appendLine("# ${s.shared("ai_doc_title")}")
        sb.appendLine()
        sb.appendLine(s.shared("ai_doc_role"))
        sb.appendLine()

        // AI Configuration Limits
        val aiLimits = AppConfigManager.getAILimits()
        val limits = when (sessionType) {
            SessionType.CHAT -> mapOf(
                "maxDataQueryIterations" to aiLimits.chatMaxDataQueryIterations,
                "maxActionRetries" to aiLimits.chatMaxActionRetries,
                "maxAutonomousRoundtrips" to aiLimits.chatMaxAutonomousRoundtrips,
                "maxCommunicationModules" to aiLimits.chatMaxCommunicationModulesRoundtrips
            )
            SessionType.AUTOMATION -> mapOf(
                "maxDataQueryIterations" to aiLimits.automationMaxDataQueryIterations,
                "maxActionRetries" to aiLimits.automationMaxActionRetries,
                "maxAutonomousRoundtrips" to aiLimits.automationMaxAutonomousRoundtrips,
                "maxCommunicationModules" to aiLimits.automationMaxCommunicationModulesRoundtrips
            )
        }

        sb.appendLine("## AI Configuration Limits")
        sb.appendLine()
        sb.appendLine("You are operating under the following limits in this session (${sessionType.name} mode):")
        sb.appendLine("- Maximum consecutive data queries: ${limits["maxDataQueryIterations"]}")
        sb.appendLine("- Maximum action retry attempts: ${limits["maxActionRetries"]}")
        sb.appendLine("- Maximum autonomous roundtrips: ${limits["maxAutonomousRoundtrips"]}")
        sb.appendLine("- Maximum communication module roundtrips: ${limits["maxCommunicationModules"]}")
        sb.appendLine()
        sb.appendLine("If you exceed these limits, the system will stop and notify the user.")
        sb.appendLine("The limits help prevent infinite loops and ensure optimal user experience.")
        sb.appendLine()

        // API Documentation
        sb.appendLine("## ${s.shared("ai_doc_command_title")}")
        sb.appendLine()
        sb.appendLine(s.shared("ai_doc_command_format"))
        sb.appendLine("- ${s.shared("ai_doc_command_format_example")}")
        sb.appendLine("- ${s.shared("ai_doc_command_resources")}")
        sb.appendLine()
        sb.appendLine("### ${s.shared("ai_doc_response_title")}")
        sb.appendLine(s.shared("ai_doc_response_structure"))
        sb.appendLine("- ${s.shared("ai_doc_response_pretext")}")
        sb.appendLine("- ${s.shared("ai_doc_response_validation")}")
        sb.appendLine("- ${s.shared("ai_doc_response_datacommands")}")
        sb.appendLine("- ${s.shared("ai_doc_response_actioncommands")}")
        sb.appendLine("- ${s.shared("ai_doc_response_posttext")}")
        sb.appendLine("- ${s.shared("ai_doc_response_module")}")
        sb.appendLine()

        // Available Tooltypes
        sb.appendLine("## ${s.shared("ai_doc_tooltypes_title")}")
        sb.appendLine()

        val allToolTypes = ToolTypeManager.getAllToolTypes()
        for ((tooltypeName, toolType) in allToolTypes) {
            val description = toolType.getDescription(context)
            val schemaIds = ToolTypeManager.getSchemaIdsForTooltype(tooltypeName)

            sb.appendLine("### $tooltypeName")
            sb.appendLine("**${s.shared("ai_doc_tooltype_description")}**: $description")
            sb.appendLine("**${s.shared("ai_doc_tooltype_schemas")}**: ${schemaIds.joinToString(", ")}")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Generate Level 1 commands: Zone schema only
     */
    private fun buildLevel1Commands(sessionType: SessionType, context: Context): List<DataCommand> {
        LogManager.aiPrompt("Building Level 1 commands (DOC)", "DEBUG")

        val commands = mutableListOf<DataCommand>()

        // Add command to fetch zone schema (fundamental schema)
        commands.add(
            DataCommand(
                id = "schema_zone_config",
                type = "SCHEMA",
                params = mapOf("id" to "zone_config"),
                isRelative = false
            )
        )

        LogManager.aiPrompt("Level 1: Generated ${commands.size} commands", "DEBUG")
        return commands
    }

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

    /**
     * Generate Level 3 commands: App structural state (zones + tool instances)
     */
    private suspend fun buildLevel3Commands(context: Context): List<DataCommand> {
        LogManager.aiPrompt("Building Level 3 commands (APP STATE)", "DEBUG")

        val commands = mutableListOf<DataCommand>()

        // Command to list all zones
        commands.add(
            DataCommand(
                id = "zones_list",
                type = "ZONES",
                params = emptyMap(),
                isRelative = false
            )
        )

        // Command to list all tool instances
        commands.add(
            DataCommand(
                id = "tools_list_all",
                type = "TOOL_INSTANCES",
                params = emptyMap(),
                isRelative = false
            )
        )

        LogManager.aiPrompt("Level 3: Generated ${commands.size} commands", "DEBUG")
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
