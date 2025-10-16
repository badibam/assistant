package com.assistant.core.ai.state

import com.assistant.core.ai.data.SessionMessage
import com.assistant.core.ai.database.AIDao
import com.assistant.core.ai.database.SessionMessageEntity
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Repository for AI message persistence with synchronous storage.
 *
 * Ensures messages are stored atomically during event processing to avoid race conditions.
 * Provides observable Flow for UI updates.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Synchronous storage (no async gaps)
 * - Observable message list for UI
 * - Type-safe conversion between domain and entity
 */
class AIMessageRepository(
    private val aiDao: AIDao
) {
    /**
     * Message cache by session ID for quick access
     */
    private val messageCache = mutableMapOf<String, MutableStateFlow<List<SessionMessage>>>()

    /**
     * Store a message synchronously.
     *
     * This is called during event processing and must complete before continuing.
     * Ensures no race conditions between message storage and state transitions.
     *
     * @param sessionId Session ID
     * @param message Message to store
     */
    suspend fun storeMessage(sessionId: String, message: SessionMessage) {
        try {
            // Convert to entity
            val entity = messageToEntity(sessionId, message)

            // Store in DB (synchronous)
            aiDao.insertMessage(entity)

            // Update cache
            updateCache(sessionId, message)

            LogManager.aiSession(
                "Message stored: session=$sessionId, sender=${message.sender}, id=${message.id}",
                "DEBUG"
            )

        } catch (e: Exception) {
            LogManager.aiSession(
                "Failed to store message: ${e.message}",
                "ERROR",
                e
            )
            throw e // Rethrow to signal failure to event processor
        }
    }

    /**
     * Delete a message (used for fallback message cleanup).
     *
     * @param sessionId Session ID
     * @param messageId Message ID to delete
     */
    suspend fun deleteMessage(sessionId: String, messageId: String) {
        try {
            val entity = aiDao.getMessage(messageId)
            if (entity != null) {
                aiDao.deleteMessage(entity)

                // Update cache
                val flow = messageCache[sessionId]
                if (flow != null) {
                    val updated = flow.value.filter { it.id != messageId }
                    flow.value = updated
                }

                LogManager.aiSession(
                    "Message deleted: session=$sessionId, id=$messageId",
                    "DEBUG"
                )
            }

        } catch (e: Exception) {
            LogManager.aiSession(
                "Failed to delete message: ${e.message}",
                "ERROR",
                e
            )
        }
    }

    /**
     * Observe messages for a session.
     *
     * Returns a Flow that emits the current message list whenever it changes.
     * UI components should collect this Flow to display messages.
     *
     * @param sessionId Session ID
     * @return Flow of message list
     */
    fun observeMessages(sessionId: String): Flow<List<SessionMessage>> {
        // Get or create cache flow for this session
        val flow = messageCache.getOrPut(sessionId) {
            MutableStateFlow(emptyList())
        }

        return flow
    }

    /**
     * Load messages from DB for a session.
     *
     * This is called on session activation to populate the cache.
     *
     * @param sessionId Session ID
     */
    suspend fun loadMessages(sessionId: String): List<SessionMessage> {
        return try {
            val entities = aiDao.getMessagesForSession(sessionId)
            val messages = entities.map { entityToMessage(it) }

            // Update cache
            val flow = messageCache.getOrPut(sessionId) {
                MutableStateFlow(messages)
            }
            flow.value = messages

            LogManager.aiSession(
                "Messages loaded: session=$sessionId, count=${messages.size}",
                "DEBUG"
            )

            messages

        } catch (e: Exception) {
            LogManager.aiSession(
                "Failed to load messages: ${e.message}",
                "ERROR",
                e
            )
            emptyList()
        }
    }

    /**
     * Clear cache for a session (cleanup after session completion).
     */
    fun clearCache(sessionId: String) {
        messageCache.remove(sessionId)
    }

    /**
     * Update message cache with new message.
     */
    private fun updateCache(sessionId: String, message: SessionMessage) {
        val flow = messageCache.getOrPut(sessionId) {
            MutableStateFlow(emptyList())
        }

        val updated = flow.value + message
        flow.value = updated
    }

    /**
     * Convert SessionMessage to SessionMessageEntity.
     */
    private fun messageToEntity(sessionId: String, message: SessionMessage): SessionMessageEntity {
        return SessionMessageEntity(
            id = message.id,
            sessionId = sessionId,
            timestamp = message.timestamp,
            sender = message.sender,
            richContentJson = message.richContent?.toJson(),
            textContent = message.textContent,
            aiMessageJson = message.aiMessageJson,
            aiMessageParsedJson = message.aiMessage?.toJson(),
            systemMessageJson = message.systemMessage?.toJson(),
            executionMetadataJson = message.executionMetadata?.toJson(),
            excludeFromPrompt = message.excludeFromPrompt
        )
    }

    /**
     * Convert SessionMessageEntity to SessionMessage.
     */
    private fun entityToMessage(entity: SessionMessageEntity): SessionMessage {
        return SessionMessage(
            id = entity.id,
            timestamp = entity.timestamp,
            sender = entity.sender,
            richContent = entity.richContentJson?.let {
                com.assistant.core.ai.data.RichMessage.fromJson(it)
            },
            textContent = entity.textContent,
            aiMessage = entity.aiMessageParsedJson?.let {
                com.assistant.core.ai.data.AIMessage.fromJson(it)
            },
            aiMessageJson = entity.aiMessageJson,
            systemMessage = entity.systemMessageJson?.let {
                com.assistant.core.ai.data.SystemMessage.fromJson(it)
            },
            executionMetadata = entity.executionMetadataJson?.let {
                // TODO: Implement ExecutionMetadata.fromJson() when ExecutionMetadata class exists
                null
            },
            excludeFromPrompt = entity.excludeFromPrompt
        )
    }
}

/**
 * Extension function to serialize RichMessage to JSON.
 * Delegates to RichMessage.toJson() method.
 */
private fun com.assistant.core.ai.data.RichMessage.toJson(): String {
    return this.toJson()
}

/**
 * Extension function to serialize SystemMessage to JSON.
 * Delegates to SystemMessage.toJson() method.
 */
private fun com.assistant.core.ai.data.SystemMessage.toJson(): String {
    return this.toJson()
}

/**
 * Extension function to serialize ExecutionMetadata to JSON.
 * TODO: Implement ExecutionMetadata class and serialization for AUTOMATION sessions.
 */
private fun com.assistant.core.ai.data.ExecutionMetadata.toJson(): String {
    return "{}" // Placeholder until ExecutionMetadata is implemented
}
