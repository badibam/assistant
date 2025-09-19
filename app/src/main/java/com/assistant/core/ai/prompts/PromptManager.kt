package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.database.SessionQueryLists
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
        LogManager.service("Building 4-level prompt for session ${session.id}")

        val level1 = buildDocumentation()
        val level2 = executeQueries(getLevel2Queries(session), context)
        val level3 = buildAppState(context)
        val level4 = executeQueries(getLevel4Queries(session), context)
        val messages = buildMessageHistory(session)

        return assemblePrompt(level1, level2, level3, level4, messages, session.providerId)
    }

    /**
     * Add enrichments from RichMessage to session Level 4
     */
    suspend fun addEnrichmentsFromMessage(
        sessionId: String,
        richMessage: RichMessage,
        context: Context
    ) {
        LogManager.service("Adding enrichments from message to session $sessionId Level 4")
        // TODO: Load session, add queries to Level 4, save back
        // Deduplication by DataQuery.id
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

    private suspend fun buildAppState(context: Context): String {
        // TODO: Query current zones, tools, permissions via CommandDispatcher
        LogManager.service("Building app state for Level 3")
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

    private suspend fun executeQueries(queryIds: List<String>, context: Context): String {
        if (queryIds.isEmpty()) return ""

        LogManager.service("Executing ${queryIds.size} queries")
        val results = mutableListOf<String>()
        val failedQueries = mutableListOf<String>()

        queryIds.forEach { queryId ->
            try {
                // TODO: Execute query via DataNavigator
                // Parse queryId to reconstruct DataQuery
                // Execute and collect results
                results.add("Query result for $queryId")
                LogManager.service("Successfully executed query: $queryId")
            } catch (e: Exception) {
                LogManager.service("Failed to execute query: $queryId - ${e.message}", "ERROR")
                failedQueries.add(queryId)
            }
        }

        if (failedQueries.isNotEmpty()) {
            LogManager.service("Removing ${failedQueries.size} failed queries from session", "WARN")
            // TODO: Remove failed queries from session
            // TODO: Notify user about removed data
        }

        return results.joinToString("\n\n")
    }

    private fun buildMessageHistory(session: AISession): String {
        return when (session.type) {
            SessionType.CHAT -> buildChatHistory(session)
            SessionType.AUTOMATION -> buildAutomationHistory(session)
        }
    }

    private fun buildChatHistory(session: AISession): String {
        LogManager.service("Building chat history for ${session.messages.size} messages")
        return session.messages.mapNotNull { message ->
            when (message.sender) {
                MessageSender.USER -> message.textContent ?: message.richContent?.linearText
                MessageSender.AI -> message.aiMessageJson // Original JSON for consistency
                MessageSender.SYSTEM -> message.systemMessage?.summary
            }
        }.joinToString("\n")
    }

    private fun buildAutomationHistory(session: AISession): String {
        LogManager.service("Building automation history")
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

        LogManager.service("Assembled prompt: ${result.totalTokens} tokens (L1:${result.level1Tokens}, L2:${result.level2Tokens}, L3:${result.level3Tokens}, L4:${result.level4Tokens})")
        return result
    }

    // === Utilities ===

    private fun getLevel2Queries(session: AISession): List<String> {
        // TODO: Parse session.queryListsJson and extract level2Queries
        return emptyList()
    }

    private fun getLevel4Queries(session: AISession): List<String> {
        // TODO: Parse session.queryListsJson and extract level4Queries
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
        LogManager.service("resolveRelativeParams() - stub implementation")
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