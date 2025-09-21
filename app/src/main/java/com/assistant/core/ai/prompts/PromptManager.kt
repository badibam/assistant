package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.QueryExecutor
import com.assistant.core.ai.prompts.QueryDeduplicator
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
     * Build complete prompt for AI session using unified QueryExecutor for all levels
     */
    suspend fun buildPrompt(session: AISession, context: Context): PromptResult {
        LogManager.aiPrompt("Building 4-level prompt for session ${session.id}")

        val queryExecutor = QueryExecutor(context)

        // Generate queries for all levels
        val level1Queries = buildLevel1Queries(context)
        val level2Queries = buildLevel2Queries(context)
        val level3Queries = buildLevel3Queries(context)
        val level4Queries = getLevel4Queries(session)

        LogManager.aiPrompt("Generated queries (L1:${level1Queries.size}, L2:${level2Queries.size}, L3:${level3Queries.size}, L4:${level4Queries.size})")

        // Execute with incremental deduplication across levels
        val level1Content = queryExecutor.executeQueries(level1Queries, "Level1")
        val level2Content = queryExecutor.executeQueries(level2Queries, "Level2", previousQueries = level1Queries)
        val level3Content = queryExecutor.executeQueries(level3Queries, "Level3", previousQueries = level1Queries + level2Queries)
        val level4Content = queryExecutor.executeQueries(level4Queries, "Level4", previousQueries = level1Queries + level2Queries + level3Queries)

        val messages = buildMessageHistory(session)

        return assemblePrompt(level1Content, level2Content, level3Content, level4Content, messages, session.providerId)
    }


    // === Level Query Builders ===

    /**
     * Generate Level 1 queries: System documentation, schemas, and app config
     */
    private fun buildLevel1Queries(context: Context): List<DataQuery> {
        LogManager.aiPrompt("Building Level 1 queries (System)")

        return listOf(
            DataQuery(
                id = "system_doc",
                type = "SYSTEM_DOC",
                params = emptyMap(),
                isRelative = false
            ),
            DataQuery(
                id = "system_schemas",
                type = "SYSTEM_SCHEMAS",
                params = emptyMap(),
                isRelative = false
            ),
            DataQuery(
                id = "app_config",
                type = "APP_CONFIG",
                params = emptyMap(),
                isRelative = false
            )
        )
    }

    /**
     * Generate Level 2 queries: User tools marked for AI context
     */
    private fun buildLevel2Queries(context: Context): List<DataQuery> {
        LogManager.aiPrompt("Building Level 2 queries (User Context)")

        // TODO: Query actual tools with include_in_ai_context=true
        // For now, return single query that will get tools via coordinator
        return listOf(
            DataQuery(
                id = "user_tools_context",
                type = "USER_TOOLS_CONTEXT",
                params = mapOf("include_in_ai_context" to true),
                isRelative = false
            )
        )
    }

    /**
     * Generate Level 3 queries: Current app state, zones, and tool instances
     */
    private fun buildLevel3Queries(context: Context): List<DataQuery> {
        LogManager.aiPrompt("Building Level 3 queries (App State)")

        return listOf(
            DataQuery(
                id = "app_state",
                type = "APP_STATE",
                params = emptyMap(),
                isRelative = false
            )
        )
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

    // Level 2 queries are now generated dynamically in buildUserContext()
    // No longer stored or retrieved from session

    private fun getLevel4Queries(session: AISession): List<DataQuery> {
        // TODO: Parse session.level4QueriesJson as List<DataQuery>
        // For now, return empty list
        return emptyList()
    }

    private fun estimateTokens(text: String): Int {
        // Rough estimation: 1 token ≈ 4 characters for most languages
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