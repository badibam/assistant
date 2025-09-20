package com.assistant.core.ai.services

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.database.AIDao
import com.assistant.core.ai.database.AIDatabase
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.ai.database.SessionMessageEntity
import com.assistant.core.ai.prompts.PromptManager
import com.assistant.core.ai.utils.TokenCalculator
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.OperationResult
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.ExecutableService
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Central orchestrator for AI sessions, managing the complete flow from user message to AI response
 *
 * Primary responsibilities:
 * - Session CRUD operations and state management
 * - Enrichment queries integration to Level 4
 * - Message storage and retrieval
 * - Full orchestration: user message → prompt building → AI call → response processing
 * - Token validation and limits checking
 */
class AISessionManager(private val context: Context) : ExecutableService {

    private val dao = AIDatabase.getDatabase(context).aiDao()
    private val coordinator = Coordinator(context)

    // ========================================================================================
    // Main Public API
    // ========================================================================================

    /**
     * Send user message to AI - complete flow orchestration
     * 1. Add enrichment queries to Level 4 (with validation)
     * 2. Store user message in session
     * 3. Build prompt via PromptManager
     * 4. Send to AI service
     * 5. Process response and store AI message
     */
    suspend fun sendMessage(richMessage: RichMessage, sessionId: String): OperationResult {
        LogManager.aiSession("AISessionManager.sendMessage() called for session $sessionId")

        return try {
            // 1. Add enrichment queries to Level 4 (with validation)
            val queryValidationResult = addEnrichmentsToSession(sessionId, richMessage.dataQueries)
            if (!queryValidationResult.isSuccess) {
                return queryValidationResult
            }

            // 2. Store user message in session
            val userMessage = SessionMessage(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sender = MessageSender.USER,
                richContent = richMessage,
                textContent = null,
                aiMessage = null,
                aiMessageJson = null,
                executionMetadata = null
            )
            storeMessage(sessionId, userMessage)

            // 3. Load session and build prompt
            val session = loadSession(sessionId)
            if (session == null) {
                return OperationResult.error("Session not found: $sessionId")
            }

            val promptResult = PromptManager.buildPrompt(session, context)
            LogManager.aiSession("Prompt built: ${promptResult.totalTokens} tokens")

            // 4. Token validation before sending
            val tokenLimit = TokenCalculator.getTokenLimit(context, isQuery = false, isTotal = true)
            if (promptResult.totalTokens > tokenLimit) {
                // TODO: Handle token limit exceeded (dialog for CHAT, auto-refuse for AUTOMATION)
                LogManager.aiSession("Token limit exceeded: ${promptResult.totalTokens} > $tokenLimit", "WARN")
                return OperationResult.error("Token limit exceeded: ${promptResult.totalTokens} tokens")
            }

            // 5. Send to AI service
            val aiService = AIService(context)
            val aiResponse = aiService.query(promptResult, session.providerId)

            if (aiResponse.isSuccess) {
                // Process successful AI response
                val responseData = aiResponse.data as Map<String, Any>
                val aiMessage = responseData["aiMessage"] as AIMessage
                val aiMessageJson = responseData["aiMessageJson"] as String

                // Store AI response message
                val aiResponseMessage = SessionMessage(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sender = MessageSender.AI,
                    richContent = null,
                    textContent = null,
                    aiMessage = aiMessage,
                    aiMessageJson = aiMessageJson,
                    executionMetadata = null
                )
                storeMessage(sessionId, aiResponseMessage)

                // TODO: Process AI actions if present
                // TODO: Process AI dataRequests if present

                LogManager.aiSession("Message processing completed successfully")
                OperationResult.success(mapOf(
                    "promptTokens" to promptResult.totalTokens,
                    "level4Queries" to richMessage.dataQueries.size,
                    "aiResponse" to aiMessage,
                    "tokensUsed" to (responseData["tokensUsed"] as? Int ?: 0)
                ))
            } else {
                LogManager.aiSession("AI service returned error: ${aiResponse.errorMessage}", "ERROR")
                OperationResult.error("AI service error: ${aiResponse.errorMessage}")
            }

        } catch (e: Exception) {
            LogManager.aiSession("Failed to send message: ${e.message}", "ERROR", e)
            OperationResult.error("Failed to send message: ${e.message}")
        }
    }

    /**
     * Create new AI session
     */
    suspend fun createSession(
        name: String = "New Discussion",
        type: SessionType = SessionType.CHAT,
        providerId: String = "claude" // TODO: Get from app config
    ): String {
        LogManager.aiSession("Creating new AI session: type=$type, provider=$providerId")

        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val sessionEntity = AISessionEntity(
            id = sessionId,
            name = name,
            type = type.name,
            providerId = providerId,
            providerSessionId = null, // TODO: Create provider session
            scheduleConfigJson = null,
            queryListsJson = createEmptyQueryLists(),
            isActive = false,
            createdAt = now,
            lastActivity = now
        )

        s

        LogManager.aiSession("Created session $sessionId")
        return sessionId
    }

    /**
     * Load session with full message history
     */
    suspend fun loadSession(sessionId: String): AISession? {
        LogManager.aiSession("Loading session $sessionId")

        return withContext(Dispatchers.IO) {
            try {
                val sessionEntity = dao.getSession(sessionId) ?: return@withContext null
                val messageEntities = dao.getMessagesForSession(sessionId)

                LogManager.aiSession("Loaded session with ${messageEntities.size} messages")

                AISession(
                    id = sessionEntity.id,
                    name = sessionEntity.name,
                    type = SessionType.valueOf(sessionEntity.type),
                    providerId = sessionEntity.providerId,
                    providerSessionId = sessionEntity.providerSessionId,
                    scheduleConfig = sessionEntity.scheduleConfigJson?.let { parseScheduleConfig(it) },
                    queryListsJson = sessionEntity.queryListsJson,
                    isActive = sessionEntity.isActive,
                    createdAt = sessionEntity.createdAt,
                    lastActivity = sessionEntity.lastActivity,
                    messages = messageEntities.map { parseSessionMessage(it) }
                )
            } catch (e: Exception) {
                LogManager.aiSession("Failed to load session: ${e.message}", "ERROR", e)
                null
            }
        }
    }

    /**
     * Set session as active (deactivate others)
     */
    suspend fun setActiveSession(sessionId: String): OperationResult {
        LogManager.aiSession("Setting active session: $sessionId")

        return try {
            withContext(Dispatchers.IO) {
                // Deactivate all sessions
                dao.deactivateAllSessions()
                // Activate target session
                dao.setSessionActive(sessionId, true)
            }

            LogManager.aiSession("Session $sessionId set as active")
            OperationResult.success()
        } catch (e: Exception) {
            LogManager.aiSession("Failed to set active session: ${e.message}", "ERROR", e)
            OperationResult.error("Failed to set active session: ${e.message}")
        }
    }

    // ========================================================================================
    // Query Management (Level 4)
    // ========================================================================================

    /**
     * Add enrichment queries to session Level 4 with validation and deduplication
     */
    suspend fun addEnrichmentsToSession(sessionId: String, dataQueries: List<DataQuery>): OperationResult {
        LogManager.aiSession("Adding ${dataQueries.size} enrichment queries to session $sessionId Level 4")

        if (dataQueries.isEmpty()) {
            LogManager.aiSession("No queries to add, skipping")
            return OperationResult.success()
        }

        return try {
            // Load current session queries
            val currentQueryLists = getSessionQueryLists(sessionId)
            val existingLevel4Ids = currentQueryLists.level4Queries.map { it.id }.toSet()

            // Deduplicate by ID
            val newQueries = dataQueries.filter { query ->
                !existingLevel4Ids.contains(query.id)
            }

            if (newQueries.isEmpty()) {
                LogManager.aiSession("All queries already exist in Level 4, no new queries added")
                return OperationResult.success()
            }

            LogManager.aiSession("Adding ${newQueries.size} new queries (${dataQueries.size - newQueries.size} duplicates filtered)")

            // TODO: Token validation for new queries
            // For each new query, estimate token cost and validate against limits

            // Add new queries to Level 4
            val updatedLevel4 = currentQueryLists.level4Queries + newQueries
            val updatedQueryLists = SessionQueryLists(
                level2Queries = currentQueryLists.level2Queries,
                level4Queries = updatedLevel4
            )

            // Update session
            updateSessionQueries(sessionId, updatedQueryLists)

            LogManager.aiSession("Successfully added ${newQueries.size} queries to Level 4")
            OperationResult.success(mapOf("newQueries" to newQueries.size))

        } catch (e: Exception) {
            LogManager.aiSession("Failed to add enrichments to session: ${e.message}", "ERROR", e)
            OperationResult.error("Failed to add enrichments: ${e.message}")
        }
    }

    // ========================================================================================
    // Private Implementation
    // ========================================================================================

    private suspend fun storeMessage(sessionId: String, message: SessionMessage) {
        LogManager.aiSession("Storing message in session $sessionId")

        val messageEntity = SessionMessageEntity(
            id = message.id,
            sessionId = sessionId,
            timestamp = message.timestamp,
            sender = message.sender.name,
            richContentJson = message.richContent?.let { serializeRichMessage(it) },
            textContent = message.textContent,
            aiMessageJson = message.aiMessageJson,
            executionMetadataJson = message.executionMetadata?.let { serializeExecutionMetadata(it) }
        )

        withContext(Dispatchers.IO) {
            dao.insertMessage(messageEntity)
            // Update session last activity
            dao.updateSessionActivity(sessionId, System.currentTimeMillis())
        }
    }

    private suspend fun getSessionQueryLists(sessionId: String): SessionQueryLists {
        val session = withContext(Dispatchers.IO) {
            dao.getSession(sessionId)
        } ?: throw IllegalArgumentException("Session not found: $sessionId")

        return parseSessionQueryLists(session.queryListsJson)
    }

    private suspend fun updateSessionQueries(sessionId: String, queryLists: SessionQueryLists) {
        val queryListsJson = serializeSessionQueryLists(queryLists)

        withContext(Dispatchers.IO) {
            dao.updateSessionQueries(sessionId, queryListsJson)
        }

        LogManager.aiSession("Updated session queries: ${queryLists.level2Queries.size} L2, ${queryLists.level4Queries.size} L4")
    }

    // ========================================================================================
    // JSON Serialization
    // ========================================================================================

    private fun createEmptyQueryLists(): String {
        return JSONObject().apply {
            put("level2Queries", JSONArray())
            put("level4Queries", JSONArray())
        }.toString()
    }

    private fun parseSessionQueryLists(json: String): SessionQueryLists {
        val jsonObj = JSONObject(json)

        val level2Queries = parseDataQueries(jsonObj.optJSONArray("level2Queries"))
        val level4Queries = parseDataQueries(jsonObj.optJSONArray("level4Queries"))

        return SessionQueryLists(level2Queries, level4Queries)
    }

    private fun serializeSessionQueryLists(queryLists: SessionQueryLists): String {
        return JSONObject().apply {
            put("level2Queries", serializeDataQueries(queryLists.level2Queries))
            put("level4Queries", serializeDataQueries(queryLists.level4Queries))
        }.toString()
    }

    private fun parseDataQueries(jsonArray: JSONArray?): List<DataQuery> {
        if (jsonArray == null) return emptyList()

        return (0 until jsonArray.length()).mapNotNull { index ->
            try {
                val queryJson = jsonArray.getJSONObject(index)
                DataQuery(
                    id = queryJson.getString("id"),
                    type = queryJson.getString("type"),
                    params = parseParams(queryJson.getJSONObject("params")),
                    isRelative = queryJson.optBoolean("isRelative", false)
                )
            } catch (e: Exception) {
                LogManager.aiSession("Failed to parse DataQuery at index $index: ${e.message}", "ERROR")
                null
            }
        }
    }

    private fun serializeDataQueries(queries: List<DataQuery>): JSONArray {
        val array = JSONArray()
        queries.forEach { query ->
            array.put(JSONObject().apply {
                put("id", query.id)
                put("type", query.type)
                put("params", serializeParams(query.params))
                put("isRelative", query.isRelative)
            })
        }
        return array
    }

    private fun parseParams(paramsJson: JSONObject): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        paramsJson.keys().forEach { key ->
            params[key] = paramsJson.get(key)
        }
        return params
    }

    private fun serializeParams(params: Map<String, Any>): JSONObject {
        val json = JSONObject()
        params.forEach { (key, value) ->
            json.put(key, value)
        }
        return json
    }

    private fun serializeRichMessage(richMessage: RichMessage): String {
        // TODO: Implement RichMessage serialization
        return JSONObject().apply {
            put("linearText", richMessage.linearText)
            put("segmentsCount", richMessage.segments.size)
            put("queriesCount", richMessage.dataQueries.size)
        }.toString()
    }

    private fun parseSessionMessage(entity: SessionMessageEntity): SessionMessage {
        // TODO: Implement full SessionMessage parsing from entity
        return SessionMessage(
            id = entity.id,
            timestamp = entity.timestamp,
            sender = MessageSender.valueOf(entity.sender),
            richContent = null, // TODO: Parse richContentJson
            textContent = entity.textContent,
            aiMessage = null, // TODO: Parse aiMessageJson
            aiMessageJson = entity.aiMessageJson,
            executionMetadata = null // TODO: Parse executionMetadataJson
        )
    }

    private fun parseScheduleConfig(json: String): ScheduleConfig {
        // TODO: Implement ScheduleConfig parsing
        return ScheduleConfig()
    }

    private fun serializeExecutionMetadata(metadata: ExecutionMetadata): String {
        // TODO: Implement ExecutionMetadata serialization
        return "{}"
    }

    override suspend fun execute(operation: String, params: JSONObject, token: com.assistant.core.coordinator.CancellationToken): OperationResult {
        return when (operation) {
            "send_message" -> {
                val sessionId = params.getString("sessionId")
                // TODO: Parse RichMessage from params
                OperationResult.error("Not implemented yet")
            }
            "create_session" -> {
                val name = params.optString("name", "New Discussion")
                val type = SessionType.valueOf(params.optString("type", "CHAT"))
                val sessionId = createSession(name, type)
                OperationResult.success(mapOf("sessionId" to sessionId))
            }
            "set_active" -> {
                val sessionId = params.getString("sessionId")
                setActiveSession(sessionId)
            }
            else -> OperationResult.error("Unknown operation: $operation")
        }
    }
}

/**
 * Container for session query lists (Level 2 and Level 4)
 */
data class SessionQueryLists(
    val level2Queries: List<DataQuery>,
    val level4Queries: List<DataQuery>
)