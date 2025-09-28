package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.CommandExecutor
import com.assistant.core.ai.prompts.QueryDeduplicator
import com.assistant.core.ai.processing.UserCommandProcessor
import com.assistant.core.ai.enrichments.EnrichmentProcessor
import com.assistant.core.utils.LogManager
import org.json.JSONObject

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
     */
    suspend fun buildPrompt(session: AISession, context: Context): PromptResult {
        LogManager.aiPrompt("Building 4-level prompt for session ${session.id}")

        // TODO: Implement new command pipeline integration
        // 1. Generate DataCommands for all levels (replace DataQuery)
        // 2. Use UserCommandProcessor for Level 4 enrichment processing
        // 3. Use CommandExecutor instead of QueryExecutor
        // 4. Integrate QueryDeduplicator.deduplicateCommands()
        // 5. Extract EnrichmentBlocks from session history and process via pipeline

        LogManager.aiPrompt("TODO: PromptManager.buildPrompt() - new pipeline integration needed")

        // Temporary fallback to avoid compilation errors
        val messages = buildMessageHistory(session)
        return PromptResult(
            prompt = "TODO: Implement new command pipeline\n\n$messages",
            level1Tokens = 0,
            level2Tokens = 0,
            level3Tokens = 0,
            level4Tokens = 0,
            totalTokens = 0
        )
    }


    // === Level Command Builders ===
    // TODO: Replace DataQuery builders with DataCommand builders

    /**
     * Generate Level 1 commands: System documentation, schemas, and app config
     */
    private fun buildLevel1Commands(context: Context): List<DataCommand> {
        // TODO: Implement Level 1 command generation
        LogManager.aiPrompt("TODO: buildLevel1Commands() - stub implementation")
        return emptyList()
    }

    // TODO: Implement Level 2 and Level 3 command builders as stubs

    /**
     * Extract Level 4 commands from session message history using new pipeline
     */
    private fun getLevel4Commands(session: AISession): List<DataCommand> {
        LogManager.aiPrompt("getLevel4Commands() for session ${session.id}")

        // TODO: Implement new Level 4 pipeline
        // 1. Extract EnrichmentBlocks from session.messages
        // 2. Use EnrichmentProcessor.generateCommands() for each block
        // 3. Process through UserCommandProcessor.processCommands()
        // 4. Return final DataCommands for execution

        LogManager.aiPrompt("TODO: getLevel4Commands() - new pipeline needed")
        return emptyList()
    }


    private fun buildMessageHistory(session: AISession): String {
        return when (session.type) {
            SessionType.CHAT -> buildChatHistory(session)
            SessionType.AUTOMATION -> buildAutomationHistory(session)
        }
    }

    private fun buildChatHistory(session: AISession): String {
        LogManager.aiPrompt("Building chat history for ${session.messages.size} messages")
        return session.messages.mapNotNull { message ->
            when (message.sender) {
                MessageSender.USER -> message.textContent ?: message.richContent?.linearText
                MessageSender.AI -> message.aiMessageJson // Original JSON for consistency
                MessageSender.SYSTEM -> message.systemMessage?.summary
            }
        }.joinToString("\n")
    }

    private fun buildAutomationHistory(session: AISession): String {
        LogManager.aiPrompt("Building automation history")
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

        LogManager.aiPrompt("Assembled prompt: ${result.totalTokens} tokens (L1:${result.level1Tokens}, L2:${result.level2Tokens}, L3:${result.level3Tokens}, L4:${result.level4Tokens})")

        // Log complete final prompt with line breaks
        LogManager.aiPrompt("""
=== FINAL COMPLETE PROMPT ===

$fullPrompt

=== END FINAL PROMPT ===
        """.trimIndent())

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
        LogManager.aiPrompt("resolveRelativeParams() - stub implementation")
        // TODO: Implement in Phase 2A+ when enrichments need it
        return params // For now, return as-is
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