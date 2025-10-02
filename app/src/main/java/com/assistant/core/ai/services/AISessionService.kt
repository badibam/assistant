package com.assistant.core.ai.services

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.database.AIDao
import com.assistant.core.database.AppDatabase
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.ai.database.SessionMessageEntity
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Pure AI session database service (ExecutableService)
 *
 * Responsibilities:
 * - Session CRUD operations via CommandDispatcher
 * - Message CRUD operations
 * - Database-only operations following CORE.md patterns
 *
 * Available operations:
 * - ai_sessions.create, .get, .list, .update, .delete, .set_active
 * - ai_messages.create, .get, .list, .update, .delete
 */
class AISessionService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("AISessionService.execute - operation: $operation", "DEBUG")

        return try {
            when (operation) {
                // Session operations
                "create_session" -> createSession(params, token)
                "get_session" -> getSession(params, token)
                "get_active_session" -> getActiveSession(params, token)
                "stop_active_session" -> stopActiveSession(params, token)
                "list_sessions" -> listSessions(params, token)
                "update_session" -> updateSession(params, token)
                "delete_session" -> deleteSession(params, token)
                "set_active_session" -> setActiveSession(params, token)

                // Message operations
                "create_message" -> createMessage(params, token)
                "get_message" -> getMessage(params, token)
                "list_messages" -> listMessages(params, token)
                "update_message" -> updateMessage(params, token)
                "delete_message" -> deleteMessage(params, token)

                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            LogManager.aiSession("AISessionService.execute - Error: ${e.message}", "ERROR", e)
            OperationResult.error(s.shared("service_error_ai_session").format(e.message ?: ""))
        }
    }

    // TODO: Implement all CRUD operations
    private suspend fun createSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val name = params.optString("name").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_name_required"))
        val type = params.optString("type").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_type_required"))
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_provider_id_required"))

        LogManager.aiSession("Creating AI session: name=$name, type=$type, providerId=$providerId", "DEBUG")

        // Create new session entity
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val sessionEntity = AISessionEntity(
            id = sessionId,
            name = name,
            type = SessionType.valueOf(type),
            providerId = providerId,
            providerSessionId = "",
            scheduleConfigJson = null, // No schedule for regular sessions
            createdAt = now,
            lastActivity = now,
            isActive = false // Will be activated separately
        )

        // Insert into database
        try {
            val database = AppDatabase.getDatabase(context)
            database.aiDao().insertSession(sessionEntity)

            LogManager.aiSession("Successfully created session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "name" to name,
                "type" to type,
                "providerId" to providerId,
                "createdAt" to now
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to create session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_create_session").format(e.message ?: ""))
        }
    }

    private suspend fun getSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Getting AI session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntity = database.aiDao().getSession(sessionId)

            if (sessionEntity == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Get messages for this session
            val messageEntities = database.aiDao().getMessagesForSession(sessionId)

            LogManager.aiSession("Found session $sessionId with ${messageEntities.size} messages", "DEBUG")

            return OperationResult.success(mapOf(
                "session" to mapOf(
                    "id" to sessionEntity.id,
                    "name" to sessionEntity.name,
                    "type" to sessionEntity.type.name, // Convert enum to string
                    "providerId" to sessionEntity.providerId,
                    "providerSessionId" to sessionEntity.providerSessionId,
                    "createdAt" to sessionEntity.createdAt,
                    "lastActivity" to sessionEntity.lastActivity,
                    "isActive" to sessionEntity.isActive
                ),
                "messages" to messageEntities.map { msg ->
                    mapOf(
                        "id" to msg.id,
                        "timestamp" to msg.timestamp,
                        "sender" to msg.sender.name, // Convert enum to string
                        "richContentJson" to msg.richContentJson,
                        "textContent" to msg.textContent,
                        "aiMessageJson" to msg.aiMessageJson,
                        "systemMessageJson" to msg.systemMessageJson
                    )
                }
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to get session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_load_session").format(e.message ?: ""))
        }
    }

    private suspend fun listSessions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("Listing all sessions", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntities = database.aiDao().getAllSessions()

            LogManager.aiSession("Found ${sessionEntities.size} sessions", "DEBUG")

            val sessions = sessionEntities.map { session ->
                mapOf(
                    "id" to session.id,
                    "name" to session.name,
                    "type" to session.type.name,
                    "providerId" to session.providerId,
                    "createdAt" to session.createdAt,
                    "lastActivity" to session.lastActivity,
                    "isActive" to session.isActive
                )
            }

            return OperationResult.success(mapOf(
                "sessions" to sessions,
                "count" to sessions.size
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list sessions: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_sessions").format(e.message ?: ""))
        }
    }

    private suspend fun updateSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Updating session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntity = database.aiDao().getSession(sessionId)

            if (sessionEntity == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Extract fields to update (name is the main updatable field)
            val name = params.optString("name")
            val now = System.currentTimeMillis()

            val updatedEntity = sessionEntity.copy(
                name = if (name.isNotEmpty()) name else sessionEntity.name,
                lastActivity = now
            )

            database.aiDao().updateSession(updatedEntity)

            LogManager.aiSession("Successfully updated session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "name" to updatedEntity.name,
                "updatedAt" to now
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_session").format(e.message ?: ""))
        }
    }

    private suspend fun deleteSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Deleting session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntity = database.aiDao().getSession(sessionId)

            if (sessionEntity == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Delete all messages for this session first
            database.aiDao().deleteMessagesForSession(sessionId)

            // Delete the session
            database.aiDao().deleteSession(sessionEntity)

            LogManager.aiSession("Successfully deleted session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "deleted" to true
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to delete session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_delete_session").format(e.message ?: ""))
        }
    }

    private suspend fun setActiveSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Setting active session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Check if session exists
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Deactivate all sessions first
            dao.deactivateAllSessions()

            // Activate the target session
            dao.activateSession(sessionId)

            LogManager.aiSession("Successfully set active session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "isActive" to true
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to set active session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_set_active_session").format(e.message ?: ""))
        }
    }

    private suspend fun getActiveSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("Getting active session", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val activeSessionEntity = database.aiDao().getActiveSession()

            if (activeSessionEntity == null) {
                LogManager.aiSession("No active session found", "DEBUG")
                return OperationResult.success(mapOf(
                    "hasActiveSession" to false
                ))
            }

            // Get messages for the active session
            val messageEntities = database.aiDao().getMessagesForSession(activeSessionEntity.id)

            LogManager.aiSession("Found active session: ${activeSessionEntity.id} with ${messageEntities.size} messages", "DEBUG")

            return OperationResult.success(mapOf(
                "hasActiveSession" to true,
                "session" to mapOf(
                    "id" to activeSessionEntity.id,
                    "name" to activeSessionEntity.name,
                    "type" to activeSessionEntity.type.name, // Convert enum to string
                    "providerId" to activeSessionEntity.providerId,
                    "providerSessionId" to activeSessionEntity.providerSessionId,
                    "createdAt" to activeSessionEntity.createdAt,
                    "lastActivity" to activeSessionEntity.lastActivity,
                    "isActive" to activeSessionEntity.isActive
                ),
                "messages" to messageEntities.map { msg ->
                    mapOf(
                        "id" to msg.id,
                        "timestamp" to msg.timestamp,
                        "sender" to msg.sender.name, // Convert enum to string
                        "richContentJson" to msg.richContentJson,
                        "textContent" to msg.textContent,
                        "aiMessageJson" to msg.aiMessageJson,
                        "systemMessageJson" to msg.systemMessageJson
                    )
                }
            ))

        } catch (e: Exception) {
            LogManager.aiSession("Failed to get active session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_get_active_session").format(e.message ?: ""))
        }
    }

    private suspend fun stopActiveSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("Stopping active session (deactivating all sessions)", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Deactivate all sessions
            dao.deactivateAllSessions()

            LogManager.aiSession("Successfully stopped active session", "INFO")
            return OperationResult.success(mapOf(
                "sessionsDeactivated" to true
            ))

        } catch (e: Exception) {
            LogManager.aiSession("Failed to stop active session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_stop_session"))
        }
    }

    private suspend fun createMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Extract required parameters
        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val senderString = params.optString("sender").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_sender_required"))
        val timestamp = params.optLong("timestamp", System.currentTimeMillis())

        // Parse sender enum
        val sender = try {
            MessageSender.valueOf(senderString)
        } catch (e: Exception) {
            return OperationResult.error(s.shared("ai_error_invalid_sender").format(senderString))
        }

        LogManager.aiSession("Creating message: sessionId=$sessionId, sender=$sender, timestamp=$timestamp", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageId = UUID.randomUUID().toString()

            // Extract content based on message type
            // richContent is already serialized JSON from RichMessage.toJson()
            val richContentJson = params.optString("richContent")?.takeIf { it.isNotEmpty() }
            val textContent = params.optString("textContent")?.takeIf { it.isNotEmpty() }
            val aiMessageJson = params.optString("aiMessageJson")?.takeIf { it.isNotEmpty() }

            // Handle SystemMessage if provided
            val systemMessageJson = if (params.has("systemMessage")) {
                val systemMessage = params.get("systemMessage") as? SystemMessage
                systemMessage?.toJson()
            } else {
                null
            }

            // Create message entity
            val messageEntity = SessionMessageEntity(
                id = messageId,
                sessionId = sessionId,
                timestamp = timestamp,
                sender = sender,
                richContentJson = richContentJson, // Store RichMessage JSON as-is (already serialized)
                textContent = textContent,
                aiMessageJson = aiMessageJson,
                aiMessageParsedJson = null, // TODO: Parse AIMessage when implementing
                systemMessageJson = systemMessageJson,
                executionMetadataJson = null // TODO: Implement automation metadata
            )

            // Insert message
            database.aiDao().insertMessage(messageEntity)

            // Update session last activity
            database.aiDao().updateSessionActivity(sessionId, timestamp)

            LogManager.aiSession("Successfully created message: $messageId for session $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "messageId" to messageId,
                "sessionId" to sessionId,
                "timestamp" to timestamp,
                "sender" to sender.name
            ))

        } catch (e: Exception) {
            LogManager.aiSession("Failed to create message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_create_message").format(e.message ?: ""))
        }
    }

    private suspend fun getMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("messageId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_message_id_required"))

        LogManager.aiSession("Getting message: $messageId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntity = database.aiDao().getMessage(messageId)

            if (messageEntity == null) {
                LogManager.aiSession("Message not found: $messageId", "WARN")
                return OperationResult.error(s.shared("ai_error_message_not_found").format(messageId))
            }

            return OperationResult.success(mapOf(
                "message" to mapOf(
                    "id" to messageEntity.id,
                    "sessionId" to messageEntity.sessionId,
                    "timestamp" to messageEntity.timestamp,
                    "sender" to messageEntity.sender.name,
                    "richContentJson" to messageEntity.richContentJson,
                    "textContent" to messageEntity.textContent,
                    "aiMessageJson" to messageEntity.aiMessageJson,
                    "systemMessageJson" to messageEntity.systemMessageJson
                )
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to get message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_get_message").format(e.message ?: ""))
        }
    }

    private suspend fun listMessages(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Listing messages for session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntities = database.aiDao().getMessagesForSession(sessionId)

            LogManager.aiSession("Found ${messageEntities.size} messages for session $sessionId", "DEBUG")

            val messages = messageEntities.map { msg ->
                mapOf(
                    "id" to msg.id,
                    "timestamp" to msg.timestamp,
                    "sender" to msg.sender.name,
                    "richContentJson" to msg.richContentJson,
                    "textContent" to msg.textContent,
                    "aiMessageJson" to msg.aiMessageJson,
                    "systemMessageJson" to msg.systemMessageJson
                )
            }

            return OperationResult.success(mapOf(
                "messages" to messages,
                "count" to messages.size,
                "sessionId" to sessionId
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list messages: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_messages").format(e.message ?: ""))
        }
    }

    private suspend fun updateMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("messageId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_message_id_required"))

        LogManager.aiSession("Updating message: $messageId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntity = database.aiDao().getMessage(messageId)

            if (messageEntity == null) {
                LogManager.aiSession("Message not found: $messageId", "WARN")
                return OperationResult.error(s.shared("ai_error_message_not_found").format(messageId))
            }

            // Extract fields to update
            val textContent = params.optString("textContent")
            val aiMessageJson = params.optString("aiMessageJson")

            val updatedEntity = messageEntity.copy(
                textContent = if (textContent.isNotEmpty()) textContent else messageEntity.textContent,
                aiMessageJson = if (aiMessageJson.isNotEmpty()) aiMessageJson else messageEntity.aiMessageJson
            )

            database.aiDao().updateMessage(updatedEntity)

            LogManager.aiSession("Successfully updated message: $messageId", "INFO")

            return OperationResult.success(mapOf(
                "messageId" to messageId,
                "updated" to true
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_message").format(e.message ?: ""))
        }
    }

    private suspend fun deleteMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("messageId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_message_id_required"))

        LogManager.aiSession("Deleting message: $messageId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntity = database.aiDao().getMessage(messageId)

            if (messageEntity == null) {
                LogManager.aiSession("Message not found: $messageId", "WARN")
                return OperationResult.error(s.shared("ai_error_message_not_found").format(messageId))
            }

            database.aiDao().deleteMessage(messageEntity)

            LogManager.aiSession("Successfully deleted message: $messageId", "INFO")

            return OperationResult.success(mapOf(
                "messageId" to messageId,
                "deleted" to true
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to delete message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_delete_message").format(e.message ?: ""))
        }
    }

    /**
     * Get AI database DAO
     */
    private fun getAIDao(): AIDao {
        return AppDatabase.getDatabase(context).aiDao()
    }
}