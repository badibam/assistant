package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.prompts.QueryExecutor
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
     * Build complete prompt for AI session
     */
    suspend fun buildPrompt(session: AISession, context: Context): PromptResult {
        LogManager.aiPrompt("Building 4-level prompt for session ${session.id}")

        val queryExecutor = QueryExecutor(context)

        val level1 = buildDocumentation()
        val level2 = buildUserContext(context) // Generated dynamically from current user state
        val level3 = buildAppState(context)
        val level4 = queryExecutor.executeQueries(getLevel4Queries(session), "Level4")
        val messages = buildMessageHistory(session)

        return assemblePrompt(level1, level2, level3, level4, messages, session.providerId)
    }


    // === Level Builders ===

    private fun buildDocumentation(): String {
        return """
        # AI Assistant Role and Response Format

        You are an intelligent assistant for a personal productivity and health tracking app.
        You help users manage their data, create insights, and automate workflows.

        ## Available Commands
        - zones.* : Zone management (create, list, update, delete)
        - tools.* : Tool instance management
        - tool_data.* : Tool data operations (create, update, delete, get)
        - data_navigator.explore : Hierarchical data exploration

        ## Response Format
        Always respond with valid JSON matching AIMessage structure:
        {
          "preText": "Analysis or introduction (required)",
          "validationRequest": {"message": "Confirmation needed?"},
          "dataRequests": [{"id": "query_id", "type": "ZONE_DATA", "params": {...}}],
          "actions": [{"id": "action_id", "command": "zones.create", "params": {...}}],
          "postText": "Follow-up text (if actions present)",
          "communicationModule": {"type": "MultipleChoice", "question": "...", "options": [...]}
        }

        IMPORTANT: Either dataRequests OR actions, never both simultaneously.
        """.trimIndent()
    }

    private suspend fun buildUserContext(context: Context): String {
        // Level 2: Generate queries dynamically from tools with include_in_ai_context=true
        LogManager.aiPrompt("Building user context for Level 2")

        // TODO: Query tools with include_in_ai_context=true via coordinator
        // For now, return placeholder
        return """
        # User Context (Level 2)

        [Tools marked for AI context will be queried dynamically here]
        """.trimIndent()
    }

    private suspend fun buildAppState(context: Context): String {
        // TODO: Query current zones, tools, permissions via CommandDispatcher
        LogManager.aiPrompt("Building app state for Level 3")
        return """
        # Current App State

        ## Zones: 2 zones configured
        - Health Zone: 3 tools active
        - Productivity Zone: 1 tool active

        ## Global AI Permissions
        - Zone creation: autonomous
        - Tool configuration: validation_required
        - Data modification: autonomous

        ## Available Tool Types
        - tracking: Data tracking with various value types
        - More tool types to be added...
        """.trimIndent()
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