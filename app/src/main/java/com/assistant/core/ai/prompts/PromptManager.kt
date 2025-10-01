package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.prompts.QueryDeduplicator
import com.assistant.core.ai.processing.UserCommandProcessor
import com.assistant.core.ai.enrichments.EnrichmentProcessor
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Tooltype information for Level 1 prompt documentation
 */
data class TooltypeInfo(
    val name: String,
    val description: String,
    val schemaIds: List<String>
)

/**
 * Singleton PromptManager implementing 4-level prompt system:
 * Level 1: Documentation (very stable)
 * Level 2: User context (stable)
 * Level 3: App structural state (moderately stable)
 * Level 4: Session data (volatile)
 */
object PromptManager {

    /**
     * Build complete prompt for AI session using new command pipeline
     * Progressive deduplication to maintain level structure for API caching
     */
    suspend fun buildPrompt(session: AISession, context: Context): PromptResult {
        LogManager.aiPrompt("Building 4-level prompt for session ${session.id}", "INFO")

        val s = Strings.`for`(context = context)
        val userCommandProcessor = UserCommandProcessor(context)
        val commandExecutor = CommandExecutor(context)

        // 1. Generate DataCommands for all 4 levels
        val level1Commands = buildLevel1Commands(context)
        val level2Commands = buildLevel2Commands(context)
        val level3Commands = buildLevel3Commands(context)
        val level4Commands = getLevel4Commands(session, context)

        LogManager.aiPrompt("Commands generated - L1:${level1Commands.size}, L2:${level2Commands.size}, L3:${level3Commands.size}, L4:${level4Commands.size}", "INFO")

        // 2. Progressive deduplication to maintain level boundaries for caching
        // Level 1: Execute all L1 commands
        val l1Deduplicated = QueryDeduplicator.deduplicateCommands(level1Commands)
        val l1Executable = userCommandProcessor.processCommands(l1Deduplicated)
        val l1ExecutionResult = commandExecutor.executeCommands(l1Executable, SystemMessageType.DATA_ADDED, "level1")

        // Level 2: Deduplicate L1+L2, execute only new commands
        val l2WithL1 = level1Commands + level2Commands
        val l2Deduplicated = QueryDeduplicator.deduplicateCommands(l2WithL1)
        val l2OnlyCommands = l2Deduplicated.filter { cmd -> !l1Deduplicated.any { it.id == cmd.id } }
        val l2Executable = userCommandProcessor.processCommands(l2OnlyCommands)
        val l2ExecutionResult = commandExecutor.executeCommands(l2Executable, SystemMessageType.DATA_ADDED, "level2")

        // Level 3: Deduplicate L1+L2+L3, execute only new commands
        val l3WithL1L2 = level1Commands + level2Commands + level3Commands
        val l3Deduplicated = QueryDeduplicator.deduplicateCommands(l3WithL1L2)
        val l3OnlyCommands = l3Deduplicated.filter { cmd ->
            !l1Deduplicated.any { it.id == cmd.id } && !l2Deduplicated.any { it.id == cmd.id }
        }
        val l3Executable = userCommandProcessor.processCommands(l3OnlyCommands)
        val l3ExecutionResult = commandExecutor.executeCommands(l3Executable, SystemMessageType.DATA_ADDED, "level3")

        // Level 4: Deduplicate all, execute only new commands
        val allCommands = level1Commands + level2Commands + level3Commands + level4Commands
        val allDeduplicated = QueryDeduplicator.deduplicateCommands(allCommands)
        val l4OnlyCommands = allDeduplicated.filter { cmd ->
            !l1Deduplicated.any { it.id == cmd.id } &&
            !l2Deduplicated.any { it.id == cmd.id } &&
            !l3Deduplicated.any { it.id == cmd.id }
        }
        val l4Executable = userCommandProcessor.processCommands(l4OnlyCommands)
        val l4ExecutionResult = commandExecutor.executeCommands(l4Executable, SystemMessageType.DATA_ADDED, "level4")

        // Log consolidated summary for all 4 levels
        logConsolidatedPromptSummary(
            level1Commands, l1Deduplicated, l1ExecutionResult,
            level2Commands, l2OnlyCommands, l2ExecutionResult,
            level3Commands, l3OnlyCommands, l3ExecutionResult,
            level4Commands, l4OnlyCommands, l4ExecutionResult,
            context
        )

        // 3. Collect SystemMessages from levels
        val systemMessagesStartup = mutableListOf<SystemMessage>()
        val systemMessagesLevel4 = mutableListOf<SystemMessage>()

        // L1-3 SystemMessages go to startup (stored before first message)
        if (l1ExecutionResult.systemMessage.commandResults.isNotEmpty()) {
            systemMessagesStartup.add(l1ExecutionResult.systemMessage)
        }
        if (l2ExecutionResult.systemMessage.commandResults.isNotEmpty()) {
            systemMessagesStartup.add(l2ExecutionResult.systemMessage)
        }
        if (l3ExecutionResult.systemMessage.commandResults.isNotEmpty()) {
            systemMessagesStartup.add(l3ExecutionResult.systemMessage)
        }

        // L4 SystemMessage: Build specific message for last user message enrichments
        // Reuse results from L1-L4 executions (commands may have been deduplicated)
        val newLevel4Commands = getNewLevel4Commands(session, context)
        if (newLevel4Commands.isNotEmpty()) {
            val newL4Deduplicated = QueryDeduplicator.deduplicateCommands(newLevel4Commands)
            val systemMessageForNewMessage = buildSystemMessageForCommands(
                commands = newL4Deduplicated,
                allExecutedCommands = mapOf(
                    "Level 1" to l1Deduplicated,
                    "Level 2" to l2Deduplicated,
                    "Level 3" to l3Deduplicated,
                    "Level 4" to allDeduplicated
                ),
                executionResults = listOf(l4ExecutionResult, l3ExecutionResult, l2ExecutionResult, l1ExecutionResult),
                messageType = SystemMessageType.DATA_ADDED,
                s = s
            )

            if (systemMessageForNewMessage.commandResults.isNotEmpty()) {
                systemMessagesLevel4.add(systemMessageForNewMessage)
            }
        }

        // 4. Build Level 1 static doc
        val level1StaticDoc = buildLevel1StaticDoc(context)

        // 5. Assemble results by level (maintaining structure for API caching)
        val level1Content = buildLevelContent("Level 1: System Documentation", level1StaticDoc, l1ExecutionResult.promptResults)
        val level2Content = buildLevelContent("Level 2: User Data", "", l2ExecutionResult.promptResults)
        val level3Content = buildLevelContent("Level 3: Application State", "", l3ExecutionResult.promptResults)
        val level4Content = buildLevelContent("Level 4: Session Data", "", l4ExecutionResult.promptResults)

        // 6. Return result with level contents and system messages
        // Note: History and final assembly will be done by AIOrchestrator after storing SystemMessages
        return PromptResult(
            level1Content = level1Content,
            level2Content = level2Content,
            level3Content = level3Content,
            level4Content = level4Content,
            systemMessagesStartup = systemMessagesStartup,
            systemMessagesLevel4 = systemMessagesLevel4,
            level1Tokens = estimateTokens(level1Content),
            level2Tokens = estimateTokens(level2Content),
            level3Tokens = estimateTokens(level3Content),
            level4Tokens = estimateTokens(level4Content),
            totalTokens = 0 // Will be calculated after adding history
        )
    }

    /**
     * Build content for a level with its command results
     */
    private fun buildLevelContent(title: String, staticContent: String, results: List<PromptCommandResult>): String {
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
     * Build Level 1 static documentation (role, API, tooltypes list)
     */
    private fun buildLevel1StaticDoc(context: Context): String {
        LogManager.aiPrompt("Building Level 1 static documentation", "DEBUG")

        val s = com.assistant.core.strings.Strings.`for`(context = context)
        val sb = StringBuilder()

        // AI Role and introduction
        sb.appendLine("# ${s.shared("ai_doc_title")}")
        sb.appendLine()
        sb.appendLine(s.shared("ai_doc_role"))
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
     * Generate Level 1 commands: System documentation, schemas, and app config
     * Returns only DataCommand for zone schema (schema_id "zone_config")
     */
    private fun buildLevel1Commands(context: Context): List<DataCommand> {
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
     * Queries all tool instances with always_send=true and generates TOOL_DATA commands
     */
    private suspend fun buildLevel2Commands(context: Context): List<DataCommand> {
        LogManager.aiPrompt("Building Level 2 commands (USER DATA)", "DEBUG")

        val commands = mutableListOf<DataCommand>()
        val coordinator = com.assistant.core.coordinator.Coordinator(context)

        // Get all tool instances
        val result = coordinator.processUserAction("tools.list_all", emptyMap())

        if (result.status != com.assistant.core.commands.CommandStatus.SUCCESS) {
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
     * Returns commands to list all zones and their tool instances with configs
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

        // For now, we add a command to list ALL tool instances
        // In the future, we could add per-zone commands after executing zones.list
        // But that would require multi-phase execution
        commands.add(
            DataCommand(
                id = "tools_list_all",
                type = "TOOL_INSTANCES",
                params = emptyMap(), // No zone_id = list all
                isRelative = false
            )
        )

        LogManager.aiPrompt("Level 3: Generated ${commands.size} commands", "DEBUG")
        return commands
    }

    /**
     * Extract Level 4 commands from session message history using new pipeline
     * Extracts EnrichmentBlocks from user messages and generates DataCommands
     */
    /**
     * Extract enrichment commands from a single message
     * Used to get new commands for SystemMessage storage
     */
    private fun getEnrichmentCommandsFromMessage(message: SessionMessage, isRelative: Boolean, context: Context): List<DataCommand> {
        val commands = mutableListOf<DataCommand>()
        val enrichmentProcessor = EnrichmentProcessor(context)

        if (message.sender != MessageSender.USER || message.richContent == null) {
            return emptyList()
        }

        // Iterate through message segments to find EnrichmentBlocks
        for (segment in message.richContent.segments) {
            if (segment is com.assistant.core.ai.data.MessageSegment.EnrichmentBlock) {
                try {
                    val enrichmentCommands = enrichmentProcessor.generateCommands(
                        type = segment.type,
                        config = segment.config,
                        isRelative = isRelative
                    )
                    commands.addAll(enrichmentCommands)
                } catch (e: Exception) {
                    LogManager.aiPrompt("Failed to generate commands from enrichment: ${e.message}", "ERROR", e)
                }
            }
        }

        return commands
    }

    /**
     * Get Level 4 commands from all user messages (for prompt content)
     * Returns all enrichment commands across all messages for complete context
     */
    private fun getLevel4Commands(session: AISession, context: Context): List<DataCommand> {
        LogManager.aiPrompt("getLevel4Commands() for session ${session.id}", "DEBUG")

        val commands = mutableListOf<DataCommand>()
        val isRelative = session.type == SessionType.AUTOMATION

        // Extract EnrichmentBlocks from all user messages
        for (message in session.messages) {
            commands.addAll(getEnrichmentCommandsFromMessage(message, isRelative, context))
        }

        LogManager.aiPrompt("Level 4: Generated ${commands.size} total commands from all enrichments", "DEBUG")
        return commands
    }

    /**
     * Get Level 4 commands from ONLY the last user message (for SystemMessage)
     * Returns only new enrichment commands from the current message
     */
    private fun getNewLevel4Commands(session: AISession, context: Context): List<DataCommand> {
        LogManager.aiPrompt("getNewLevel4Commands() for last user message", "DEBUG")

        val isRelative = session.type == SessionType.AUTOMATION

        // Find the last USER message
        val lastUserMessage = session.messages.lastOrNull { it.sender == MessageSender.USER }
        if (lastUserMessage == null) {
            LogManager.aiPrompt("No user messages found in session", "DEBUG")
            return emptyList()
        }

        val commands = getEnrichmentCommandsFromMessage(lastUserMessage, isRelative, context)
        LogManager.aiPrompt("Level 4: Generated ${commands.size} new commands from last user message", "DEBUG")
        return commands
    }


    /**
     * Build history section from session messages (called by AIOrchestrator after storing SystemMessages)
     *
     * @param sessionId The session ID to load messages from
     * @param context Android context
     * @return Formatted history string for prompt
     */
    suspend fun buildHistorySection(sessionId: String, context: Context): String {
        LogManager.aiPrompt("Building history section for session $sessionId", "DEBUG")

        val coordinator = com.assistant.core.coordinator.Coordinator(context)
        val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("sessionId" to sessionId))

        if (!result.isSuccess) {
            LogManager.aiPrompt("Failed to load session for history: ${result.error}", "ERROR")
            return ""
        }

        // Parse session from result
        val sessionData = result.data?.get("session") as? Map<*, *>
        if (sessionData == null) {
            LogManager.aiPrompt("No session data found", "ERROR")
            return ""
        }

        val sessionType = sessionData["type"] as? String
        val messagesData: List<*> = (result.data?.get("messages") as? List<*>) ?: emptyList<Any>()

        // Log history summary
        logHistorySummary(messagesData, context)

        return when (sessionType) {
            "CHAT" -> buildChatHistory(messagesData)
            "AUTOMATION" -> buildAutomationHistory(messagesData)
            else -> buildChatHistory(messagesData)
        }
    }

    private fun buildChatHistory(messages: List<*>): String {
        LogManager.aiPrompt("Building chat history for ${messages.size} messages", "DEBUG")

        val formattedMessages = messages.mapNotNull { msg ->
            if (msg !is Map<*, *>) return@mapNotNull null

            val sender = msg["sender"] as? String
            when (sender) {
                "USER" -> {
                    // Parse richContentJson to get linearText, fallback to textContent
                    val richContentJson = msg["richContentJson"] as? String
                    val linearText = if (richContentJson != null && richContentJson.isNotEmpty()) {
                        try {
                            JSONObject(richContentJson).optString("linearText", null)
                        } catch (e: Exception) {
                            LogManager.aiPrompt("Failed to parse richContentJson: ${e.message}", "WARN")
                            null
                        }
                    } else null

                    val content = linearText ?: msg["textContent"] as? String
                    content?.let { "[USER] $it" }
                }
                "AI" -> {
                    // Use original JSON for consistency, prefixed with [AI]
                    val aiJson = msg["aiMessageJson"] as? String
                    aiJson?.let { "[AI] $it" }
                }
                "SYSTEM" -> {
                    // Parse systemMessageJson to get summary and commandResults
                    val systemMessageJson = msg["systemMessageJson"] as? String
                    if (systemMessageJson != null && systemMessageJson.isNotEmpty()) {
                        try {
                            val systemMsgObj = JSONObject(systemMessageJson)
                            val summary = systemMsgObj.optString("summary", null)
                            val commandResultsArray = systemMsgObj.optJSONArray("commandResults")

                            if (summary != null && commandResultsArray != null && commandResultsArray.length() > 0) {
                                val commandResults = (0 until commandResultsArray.length()).map { i ->
                                    commandResultsArray.getJSONObject(i).toMap()
                                }
                                formatSystemMessageForHistory(summary, commandResults)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            LogManager.aiPrompt("Failed to parse systemMessageJson: ${e.message}", "WARN")
                            null
                        }
                    } else null
                }
                else -> null
            }
        }

        return formattedMessages.joinToString("\n\n")
    }

    private fun buildAutomationHistory(messages: List<*>): String {
        LogManager.aiPrompt("Building automation history", "DEBUG")
        // TODO: Implement according to "Send history to AI" setting
        // For now, return initial prompt + executions

        val initialPrompt = messages.firstOrNull { msg ->
            (msg as? Map<*, *>)?.get("sender") == "USER"
        }?.let { msg ->
            val richContent = (msg as? Map<*, *>)?.get("richContent") as? Map<*, *>
            richContent?.get("linearText") as? String
        } ?: ""

        val executions = messages.mapNotNull { msg ->
            val msgMap = msg as? Map<*, *> ?: return@mapNotNull null
            if (msgMap["sender"] == "AI" && msgMap["executionMetadata"] != null) {
                msgMap["aiMessageJson"] as? String
            } else {
                null
            }
        }

        return listOf(initialPrompt, *executions.toTypedArray()).joinToString("\n\n")
    }

    /**
     * Format SystemMessage for history section with detailed command results
     * Format: [SYSTEM] summary + detailed command list
     */
    private fun formatSystemMessageForHistory(summary: String, commandResults: List<*>): String {
        val sb = StringBuilder()
        sb.appendLine("[SYSTEM] $summary")

        // Add detailed command results
        for (cmdResult in commandResults) {
            if (cmdResult !is Map<*, *>) continue

            val command = cmdResult["command"] as? String ?: continue
            val status = cmdResult["status"] as? String ?: "UNKNOWN"
            val details = cmdResult["details"] as? String

            val statusSymbol = when (status) {
                "SUCCESS" -> "✓"
                "FAILED" -> "✗"
                else -> "?"
            }

            if (details != null) {
                sb.appendLine("  $statusSymbol $command: $details")
            } else {
                sb.appendLine("  $statusSymbol $command")
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * Log summary of conversation history with 1 line per message
     * Format examples:
     * - [USER] Début du message... / 1 POINTER + 2 CREATE
     * - [SYSTEM] 5 operations: 4 success, 1 failed
     * - [AI] Début du message... / 1 ACTION
     */
    private fun logHistorySummary(messages: List<*>, context: Context) {
        if (messages.isEmpty()) {
            LogManager.aiPrompt("=== HISTORY SUMMARY ===\n(no messages)\n=== END HISTORY SUMMARY ===", "INFO")
            return
        }

        val sb = StringBuilder()
        sb.appendLine("=== HISTORY SUMMARY ===")
        sb.appendLine()

        for ((index, msg) in messages.withIndex()) {
            if (msg !is Map<*, *>) continue

            val sender = msg["sender"] as? String ?: "UNKNOWN"
            val line = when (sender) {
                "USER" -> {
                    // Parse richContentJson to extract preview and enrichments
                    val richContentJson = msg["richContentJson"] as? String

                    var linearText = ""
                    val enrichmentCounts = mutableMapOf<String, Int>()

                    if (richContentJson != null && richContentJson.isNotEmpty()) {
                        try {
                            val richContent = org.json.JSONObject(richContentJson)
                            linearText = richContent.optString("linearText", "")

                            // Count enrichments by type
                            val segmentsArray = richContent.optJSONArray("segments")
                            if (segmentsArray != null) {
                                for (i in 0 until segmentsArray.length()) {
                                    val segment = segmentsArray.optJSONObject(i)
                                    if (segment != null && segment.optString("type") == "enrichment") {
                                        val enrichType = segment.optString("enrichmentType", "UNKNOWN")
                                        enrichmentCounts[enrichType] = (enrichmentCounts[enrichType] ?: 0) + 1
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            LogManager.aiPrompt("Failed to parse richContentJson: ${e.message}", "WARN")
                        }
                    }

                    val preview = linearText.take(50) + if (linearText.length > 50) "..." else ""
                    val enrichmentSummary = if (enrichmentCounts.isEmpty()) {
                        ""
                    } else {
                        " / " + enrichmentCounts.entries.joinToString(" + ") { "${it.value} ${it.key}" }
                    }

                    "[USER] $preview$enrichmentSummary"
                }
                "AI" -> {
                    // Parse AI message to extract structured summary
                    val aiMessageJson = msg["aiMessageJson"] as? String

                    if (aiMessageJson != null && aiMessageJson.isNotEmpty()) {
                        try {
                            val aiMsg = JSONObject(aiMessageJson)
                            val parts = mutableListOf<String>()

                            // Extract preText
                            val preText = aiMsg.optString("preText", null)
                            if (preText != null && preText.isNotEmpty()) {
                                val preview = preText.take(50) + if (preText.length > 50) "..." else ""
                                parts.add("preText: $preview")
                            }

                            // Count dataCommands
                            val dataCommandsArray = aiMsg.optJSONArray("dataCommands")
                            if (dataCommandsArray != null && dataCommandsArray.length() > 0) {
                                parts.add("${dataCommandsArray.length()} commandes data")
                            }

                            // Count actionCommands
                            val actionCommandsArray = aiMsg.optJSONArray("actionCommands")
                            if (actionCommandsArray != null && actionCommandsArray.length() > 0) {
                                parts.add("${actionCommandsArray.length()} commandes action")
                            }

                            // Extract postText
                            val postText = aiMsg.optString("postText", null)
                            if (postText != null && postText.isNotEmpty()) {
                                val preview = postText.take(50) + if (postText.length > 50) "..." else ""
                                parts.add("postText: $preview")
                            }

                            // Extract communicationModule type
                            val commModule = aiMsg.optJSONObject("communicationModule")
                            if (commModule != null) {
                                val moduleType = commModule.optString("type", "Unknown")
                                parts.add("Module: $moduleType")
                            }

                            "[AI] ${parts.joinToString(" / ")}"
                        } catch (e: Exception) {
                            LogManager.aiPrompt("Failed to parse aiMessageJson for summary: ${e.message}", "WARN")
                            val preview = aiMessageJson.take(50) + if (aiMessageJson.length > 50) "..." else ""
                            "[AI] $preview"
                        }
                    } else {
                        "[AI] (empty message)"
                    }
                }
                "SYSTEM" -> {
                    // Parse systemMessageJson to extract summary
                    val systemMessageJson = msg["systemMessageJson"] as? String

                    var successCount = 0
                    var failedCount = 0
                    var total = 0

                    if (systemMessageJson != null && systemMessageJson.isNotEmpty()) {
                        try {
                            val systemMsg = org.json.JSONObject(systemMessageJson)
                            val commandResultsArray = systemMsg.optJSONArray("commandResults")
                            if (commandResultsArray != null) {
                                total = commandResultsArray.length()
                                for (i in 0 until commandResultsArray.length()) {
                                    val result = commandResultsArray.optJSONObject(i)
                                    when (result?.optString("status")) {
                                        "SUCCESS" -> successCount++
                                        "FAILED" -> failedCount++
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            LogManager.aiPrompt("Failed to parse systemMessageJson: ${e.message}", "WARN")
                        }
                    }

                    "[SYSTEM] $total operations: $successCount success, $failedCount failed"
                }
                else -> "[${sender}] Unknown message type"
            }

            sb.appendLine("  ${index + 1}. $line")
        }

        sb.appendLine()
        sb.appendLine("=== END HISTORY SUMMARY ===")
        LogManager.aiPrompt(sb.toString(), "INFO")
    }

    /**
     * Assemble final prompt with history (called by AIOrchestrator after storing SystemMessages)
     *
     * @param promptResult The PromptResult from buildPrompt()
     * @param history The formatted history section (includes startup messages from DB)
     * @param providerId The provider ID for cache breakpoints
     * @return Complete prompt string
     */
    fun assembleFinalPrompt(
        promptResult: PromptResult,
        history: String,
        providerId: String
    ): String {
        val fullPrompt = when (providerId) {
            "claude" -> """
                ${promptResult.level1Content}
                <cache:breakpoint>
                ${promptResult.level2Content}
                <cache:breakpoint>
                ${promptResult.level3Content}
                <cache:breakpoint>
                ${promptResult.level4Content}

                ## Conversation History
                $history
            """.trimIndent()
            else -> "${promptResult.level1Content}\n\n${promptResult.level2Content}\n\n${promptResult.level3Content}\n\n${promptResult.level4Content}\n\n## Conversation History\n$history"
        }

        val totalTokens = estimateTokens(fullPrompt)

        LogManager.aiPrompt("Assembled final prompt: $totalTokens tokens (L1:${promptResult.level1Tokens}, L2:${promptResult.level2Tokens}, L3:${promptResult.level3Tokens}, L4:${promptResult.level4Tokens}, history:${estimateTokens(history)})", "INFO")

        // Log complete final prompt with line breaks
        LogManager.aiPrompt("""
=== FINAL COMPLETE PROMPT ===

$fullPrompt

=== END FINAL PROMPT ===
        """.trimIndent(), "VERBOSE")

        return fullPrompt
    }

    // === Utilities ===

    /**
     * Estimate token count from text (public for AIOrchestrator)
     * Rough estimation: 1 token ≈ 4 characters for most languages
     * TODO: better estimation
     */
    fun estimateTokens(text: String): Int {
        return text.length / 4
    }

    /**
     * Resolve relative parameters to absolute values at execution time
     * Used for automation queries that need to adapt to current date/time
     *
     * TODO Phase 2A+: Implement full logic for all relative period types
     */
    private fun resolveRelativeParams(params: Map<String, Any>): Map<String, Any> {
        LogManager.aiPrompt("resolveRelativeParams() - stub implementation", "DEBUG")
        // TODO: Implement in Phase 2A+ when enrichments need it
        return params // For now, return as-is
    }

    /**
     * Log consolidated summary for all 4 prompt levels in a single INFO log
     * Shows commands count, deduplication, and status for each command across all levels
     */
    private fun logConsolidatedPromptSummary(
        l1Original: List<DataCommand>, l1Executed: List<DataCommand>, l1Result: CommandExecutionResult,
        l2Original: List<DataCommand>, l2Executed: List<DataCommand>, l2Result: CommandExecutionResult,
        l3Original: List<DataCommand>, l3Executed: List<DataCommand>, l3Result: CommandExecutionResult,
        l4Original: List<DataCommand>, l4Executed: List<DataCommand>, l4Result: CommandExecutionResult,
        context: Context
    ) {
        val s = Strings.`for`(context = context)
        val sb = StringBuilder()
        sb.appendLine("=== PROMPT L1-L4 SUMMARY ===")
        sb.appendLine()

        // Level 1
        appendLevelSummary(sb, "Level 1: System Documentation", l1Original, l1Executed, l1Result, s)
        // Level 2
        appendLevelSummary(sb, "Level 2: User Data", l2Original, l2Executed, l2Result, s)
        // Level 3
        appendLevelSummary(sb, "Level 3: Application State", l3Original, l3Executed, l3Result, s)
        // Level 4
        appendLevelSummary(sb, "Level 4: Session Data", l4Original, l4Executed, l4Result, s)

        sb.appendLine("=== END PROMPT L1-L4 SUMMARY ===")
        LogManager.aiPrompt(sb.toString(), "INFO")
    }

    /**
     * Helper to append a single level summary to the StringBuilder
     */
    private fun appendLevelSummary(
        sb: StringBuilder,
        levelName: String,
        originalCommands: List<DataCommand>,
        executedCommands: List<DataCommand>,
        executionResult: CommandExecutionResult,
        s: StringsContext
    ) {
        sb.appendLine("## $levelName")

        if (originalCommands.isEmpty()) {
            sb.appendLine("  (no commands)")
            sb.appendLine()
            return
        }

        // Build set of executed command IDs for quick lookup
        val executedIds = executedCommands.map { it.id }.toSet()
        val executionResults = executionResult.systemMessage.commandResults

        // Log each original command with its status
        var executedIndex = 0
        for (command in originalCommands) {
            val wasExecuted = executedIds.contains(command.id)
            val status = when {
                !wasExecuted -> s.shared("ai_prompt_duplicate_ignored")
                executedIndex < executionResults.size -> {
                    val result = executionResults[executedIndex]
                    executedIndex++
                    when (result.status) {
                        CommandStatus.SUCCESS -> "Succès"
                        CommandStatus.FAILED -> "Échec"
                        else -> "Inconnu"
                    }
                }
                else -> "Inconnu"
            }

            // Format command description
            val commandDesc = when (command.type) {
                "SCHEMA" -> "SCHEMA[${command.params["id"]}]"
                "TOOL_CONFIG" -> "TOOL_CONFIG[${command.params["id"]}]"
                "TOOL_DATA" -> "TOOL_DATA[${command.params["id"]}]"
                "ZONE_CONFIG" -> "ZONE_CONFIG[${command.params["id"]}]"
                "ZONES" -> "ZONES"
                "TOOL_INSTANCES" -> "TOOL_INSTANCES[${command.params["zone_id"] ?: "all"}]"
                else -> "${command.type}[${command.params["id"] ?: "no-id"}]"
            }

            sb.appendLine("  - $commandDesc: $status")
        }

        sb.appendLine()
    }


}

/**
 * Result of prompt building with token estimates and system messages
 */
data class PromptResult(
    val level1Content: String,
    val level2Content: String,
    val level3Content: String,
    val level4Content: String,
    val systemMessagesStartup: List<SystemMessage>,  // L1-3: stored at session start
    val systemMessagesLevel4: List<SystemMessage>,   // L4: stored after user message
    val level1Tokens: Int,
    val level2Tokens: Int,
    val level3Tokens: Int,
    val level4Tokens: Int,
    val totalTokens: Int
)

/**
 * Build SystemMessage for specific commands by reusing results from previous executions
 * Handles command inclusion logic (e.g., specific query included in broader query)
 *
 * @param commands Commands from the current message (deduplicated)
 * @param allExecutedCommands Map of level name to executed commands (for ID matching)
 * @param executionResults Execution results from L4, L3, L2, L1 (in that order)
 * @param messageType Type of system message to generate
 * @param s Strings context for localization
 */
private fun buildSystemMessageForCommands(
    commands: List<DataCommand>,
    allExecutedCommands: Map<String, List<DataCommand>>,
    executionResults: List<CommandExecutionResult>,
    messageType: SystemMessageType,
    s: StringsContext
): SystemMessage {
    val commandResults = mutableListOf<com.assistant.core.ai.data.CommandResult>()
    var successCount = 0
    var failedCount = 0

    for (cmd in commands) {
        var found = false

        // Step 1: Try to find exact match by ID using executed commands from each level
        for ((levelName, executedCommands) in allExecutedCommands) {
            val matchingCommand = executedCommands.find { it.id == cmd.id }

            if (matchingCommand != null) {
                // Found exact match - find corresponding execution result
                val levelIndex = when (levelName) {
                    "Level 4" -> 0
                    "Level 3" -> 1
                    "Level 2" -> 2
                    "Level 1" -> 3
                    else -> -1
                }

                if (levelIndex >= 0 && levelIndex < executionResults.size) {
                    val execResult = executionResults[levelIndex]

                    // Find the corresponding result (by command type)
                    val matchingResult = execResult.systemMessage.commandResults.find { result ->
                        result.command.contains(cmd.type, ignoreCase = true)
                    }

                    if (matchingResult != null) {
                        // Exact match found - reuse result as-is
                        commandResults.add(matchingResult)
                        if (matchingResult.status == CommandStatus.SUCCESS) successCount++
                        else if (matchingResult.status == CommandStatus.FAILED) failedCount++
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) {
            // Step 2: Try to find including command using QueryDeduplicator logic
            for ((levelName, executedCommands) in allExecutedCommands) {
                for (execCmd in executedCommands) {
                    // Use QueryDeduplicator to check if execCmd includes cmd
                    // Note: This currently returns false (not implemented), but when implemented
                    // it will properly detect command inclusion
                    val isIncluded = QueryDeduplicator.commandIncludes(execCmd, cmd)

                    if (isIncluded) {
                        // Command is included in broader command - reference it
                        commandResults.add(
                            com.assistant.core.ai.data.CommandResult(
                                command = cmd.type,
                                status = CommandStatus.CACHED,
                                details = "Résultats inclus dans commande précédente ($levelName): ${execCmd.type}"
                            )
                        )
                        found = true
                        break
                    }
                }

                if (found) break
            }
        }

        if (!found) {
            // No match found at all - should not happen in normal flow
            commandResults.add(
                com.assistant.core.ai.data.CommandResult(
                    command = cmd.type,
                    status = CommandStatus.FAILED,
                    details = "Command result not found in execution history"
                )
            )
            failedCount++
        }
    }

    // Generate summary using same logic as CommandExecutor
    val summary = when (messageType) {
        SystemMessageType.DATA_ADDED -> {
            when {
                commandResults.isEmpty() -> s.shared("ai_system_no_commands")
                failedCount == 0 -> s.shared("ai_system_queries_success").format(successCount)
                successCount == 0 -> s.shared("ai_system_queries_all_failed").format(failedCount)
                else -> s.shared("ai_system_queries_partial").format(successCount, failedCount)
            }
        }
        SystemMessageType.ACTIONS_EXECUTED -> {
            when {
                commandResults.isEmpty() -> s.shared("ai_system_no_actions")
                failedCount == 0 -> s.shared("ai_system_actions_success").format(successCount)
                successCount == 0 -> s.shared("ai_system_actions_all_failed").format(failedCount)
                else -> s.shared("ai_system_actions_partial").format(successCount, failedCount)
            }
        }
    }

    return SystemMessage(
        type = messageType,
        commandResults = commandResults,
        summary = summary
    )
}

/**
 * Extension function to convert JSONObject to Map
 */
private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = get(key)
        map[key] = when (value) {
            JSONObject.NULL -> null
            else -> value
        } ?: ""
    }
    return map
}