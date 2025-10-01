package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.prompts.QueryDeduplicator
import com.assistant.core.ai.processing.UserCommandProcessor
import com.assistant.core.ai.enrichments.EnrichmentProcessor
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

        // 3. Build Level 1 static doc
        val level1StaticDoc = buildLevel1StaticDoc(context)

        // 4. Assemble results by level (maintaining structure for API caching)
        val level1Content = buildLevelContent("Level 1: System Documentation", level1StaticDoc, l1ExecutionResult.promptResults)
        val level2Content = buildLevelContent("Level 2: User Data", "", l2ExecutionResult.promptResults)
        val level3Content = buildLevelContent("Level 3: Application State", "", l3ExecutionResult.promptResults)
        val level4Content = buildLevelContent("Level 4: Session Data", "", l4ExecutionResult.promptResults)

        // 5. Build message history
        val messages = buildMessageHistory(session)

        // 6. Assemble final prompt
        val finalPrompt = assemblePrompt(
            level1 = level1Content,
            level2 = level2Content,
            level3 = level3Content,
            level4 = level4Content,
            messages = messages,
            providerId = session.providerId
        )

        return finalPrompt
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
    private fun getLevel4Commands(session: AISession, context: Context): List<DataCommand> {
        LogManager.aiPrompt("getLevel4Commands() for session ${session.id}", "DEBUG")

        val commands = mutableListOf<DataCommand>()
        val enrichmentProcessor = EnrichmentProcessor(context)

        // Determine if we should use relative periods based on session type
        val isRelative = session.type == SessionType.AUTOMATION

        // Extract EnrichmentBlocks from all user messages
        for (message in session.messages) {
            if (message.sender != MessageSender.USER) continue
            if (message.richContent == null) continue

            // Iterate through message segments to find EnrichmentBlocks
            for (segment in message.richContent.segments) {
                if (segment is com.assistant.core.ai.data.MessageSegment.EnrichmentBlock) {
                    try {
                        // Generate commands from enrichment block
                        val enrichmentCommands = enrichmentProcessor.generateCommands(
                            type = segment.type,
                            config = segment.config,
                            isRelative = isRelative
                        )

                        commands.addAll(enrichmentCommands)
                        LogManager.aiPrompt("Generated ${enrichmentCommands.size} commands from ${segment.type} enrichment", "DEBUG")

                    } catch (e: Exception) {
                        LogManager.aiPrompt("Failed to generate commands from enrichment: ${e.message}", "ERROR", e)
                    }
                }
            }
        }

        LogManager.aiPrompt("Level 4: Generated ${commands.size} total commands from enrichments", "DEBUG")
        return commands
    }


    private fun buildMessageHistory(session: AISession): String {
        return when (session.type) {
            SessionType.CHAT -> buildChatHistory(session)
            SessionType.AUTOMATION -> buildAutomationHistory(session)
        }
    }

    private fun buildChatHistory(session: AISession): String {
        LogManager.aiPrompt("Building chat history for ${session.messages.size} messages", "DEBUG")
        return session.messages.mapNotNull { message ->
            when (message.sender) {
                MessageSender.USER -> message.textContent ?: message.richContent?.linearText
                MessageSender.AI -> message.aiMessageJson // Original JSON for consistency
                MessageSender.SYSTEM -> message.systemMessage?.summary
            }
        }.joinToString("\n")
    }

    private fun buildAutomationHistory(session: AISession): String {
        LogManager.aiPrompt("Building automation history", "DEBUG")
        // TODO: Implement according to "Send history to AI" setting
        // For now, return initial prompt + executions
        val initialPrompt = session.messages.firstOrNull { it.sender == MessageSender.USER }
            ?.richContent?.linearText ?: ""

        val executions = session.messages.filter {
            it.sender == MessageSender.AI && it.executionMetadata != null
        }.map { it.aiMessageJson ?: "" }

        return listOf(initialPrompt, *executions.toTypedArray()).joinToString("\n")
    }

    // === Assembly ===

    private fun assemblePrompt(
        level1: String,
        level2: String,
        level3: String,
        level4: String,
        messages: String,
        providerId: String
    ): PromptResult {
        val fullPrompt = when (providerId) {
            "claude" -> """
                $level1
                <cache:breakpoint>
                $level2
                <cache:breakpoint>
                $level3
                <cache:breakpoint>
                $level4

                ## Conversation History
                $messages
            """.trimIndent()
            else -> "$level1\n\n$level2\n\n$level3\n\n$level4\n\n## Conversation History\n$messages"
        }

        val result = PromptResult(
            prompt = fullPrompt,
            level1Tokens = estimateTokens(level1),
            level2Tokens = estimateTokens(level2),
            level3Tokens = estimateTokens(level3),
            level4Tokens = estimateTokens(level4),
            totalTokens = estimateTokens(fullPrompt)
        )

        LogManager.aiPrompt("Assembled prompt: ${result.totalTokens} tokens (L1:${result.level1Tokens}, L2:${result.level2Tokens}, L3:${result.level3Tokens}, L4:${result.level4Tokens})", "INFO")

        // Log complete final prompt with line breaks
        LogManager.aiPrompt("""
=== FINAL COMPLETE PROMPT ===

$fullPrompt

=== END FINAL PROMPT ===
        """.trimIndent(), "VERBOSE")

        return result
    }

    // === Utilities ===

    private fun estimateTokens(text: String): Int {
        // Rough estimation: 1 token ≈ 4 characters for most languages
        // TODO: better estimation
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
        sb.appendLine("=== PROMPT GENERATION SUMMARY ===")
        sb.appendLine()

        // Level 1
        appendLevelSummary(sb, "Level 1: System Documentation", l1Original, l1Executed, l1Result, s)
        // Level 2
        appendLevelSummary(sb, "Level 2: User Data", l2Original, l2Executed, l2Result, s)
        // Level 3
        appendLevelSummary(sb, "Level 3: Application State", l3Original, l3Executed, l3Result, s)
        // Level 4
        appendLevelSummary(sb, "Level 4: Session Data", l4Original, l4Executed, l4Result, s)

        sb.appendLine("=== END PROMPT SUMMARY ===")
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
 * Result of prompt building with token estimates
 */
data class PromptResult(
    val prompt: String,
    val level1Tokens: Int,
    val level2Tokens: Int,
    val level3Tokens: Int,
    val level4Tokens: Int,
    val totalTokens: Int
)