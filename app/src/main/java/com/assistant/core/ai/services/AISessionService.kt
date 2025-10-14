package com.assistant.core.ai.services

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.database.AIDao
import com.assistant.core.ai.utils.SessionCostCalculator
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
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

                // Cost calculation
                "get_cost" -> getSessionCost(params, token)

                // Validation toggle
                "toggle_validation" -> toggleValidation(params, token)

                // Session state updates (for AUTOMATION)
                "update_state" -> updateSessionState(params, token)
                "update_last_network_error_time" -> updateLastNetworkErrorTime(params, token)
                "update_end_reason" -> updateEndReason(params, token)
                "reset_session_state" -> resetSessionState(params, token)

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
            automationId = params.optString("automationId").takeIf { it.isNotEmpty() }, // null for CHAT
            scheduledExecutionTime = if (params.has("scheduledExecutionTime")) params.getLong("scheduledExecutionTime") else null,
            providerId = providerId,
            providerSessionId = "",
            createdAt = now,
            lastActivity = now,
            isActive = false // Will be activated separately
        )

        // Insert into database
        try {
            val database = AppDatabase.getDatabase(context)
            database.aiDao().insertSession(sessionEntity)

            LogManager.aiSession("Successfully created session: $sessionId", "INFO")

            // For SEED sessions, create an empty USER message as template placeholder
            if (type == "SEED") {
                val emptyRichMessage = RichMessage(
                    segments = emptyList(),
                    linearText = "",
                    dataCommands = emptyList()
                )

                val messageEntity = SessionMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    timestamp = now,
                    sender = MessageSender.USER,
                    richContentJson = emptyRichMessage.toJson(),
                    textContent = null,
                    aiMessageJson = null,
                    aiMessageParsedJson = null,
                    systemMessageJson = null,
                    executionMetadataJson = null,
                    excludeFromPrompt = false,
                    inputTokens = 0,
                    cacheWriteTokens = 0,
                    cacheReadTokens = 0,
                    outputTokens = 0
                )

                database.aiDao().insertMessage(messageEntity)
                LogManager.aiSession("Created empty USER message for SEED session: $sessionId", "DEBUG")
            }

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
                    "requireValidation" to sessionEntity.requireValidation,
                    "automationId" to sessionEntity.automationId,
                    "scheduledExecutionTime" to sessionEntity.scheduledExecutionTime,
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
                        "systemMessageJson" to msg.systemMessageJson,
                        "excludeFromPrompt" to msg.excludeFromPrompt
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
                "sessionId" to activeSessionEntity.id,
                "session" to mapOf(
                    "id" to activeSessionEntity.id,
                    "name" to activeSessionEntity.name,
                    "type" to activeSessionEntity.type.name, // Convert enum to string
                    "requireValidation" to activeSessionEntity.requireValidation,
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
                        "systemMessageJson" to msg.systemMessageJson,
                        "excludeFromPrompt" to msg.excludeFromPrompt
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

            // Get excludeFromPrompt flag
            val excludeFromPrompt = params.optBoolean("excludeFromPrompt", false)

            // Extract token usage metrics (for AI messages only, 0 for USER/SYSTEM)
            val inputTokens = params.optInt("inputTokens", 0)
            val cacheWriteTokens = params.optInt("cacheWriteTokens", 0)
            val cacheReadTokens = params.optInt("cacheReadTokens", 0)
            val outputTokens = params.optInt("outputTokens", 0)

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
                executionMetadataJson = null, // TODO: Implement automation metadata
                excludeFromPrompt = excludeFromPrompt,
                // Token usage metrics for cost calculation
                inputTokens = inputTokens,
                cacheWriteTokens = cacheWriteTokens,
                cacheReadTokens = cacheReadTokens,
                outputTokens = outputTokens
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
            val richContentJson = params.optString("richContentJson")
            val textContent = params.optString("textContent")
            val aiMessageJson = params.optString("aiMessageJson")

            val updatedEntity = messageEntity.copy(
                richContentJson = if (richContentJson.isNotEmpty()) richContentJson else messageEntity.richContentJson,
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
     * Get session cost calculation
     * Calculates total cost for a session based on token usage and LiteLLM pricing
     */
    private suspend fun getSessionCost(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_session_id_required"))

        LogManager.aiSession("Getting cost for session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)

            // Get session
            val session = database.aiDao().getSession(sessionId)
                ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))

            // Get all messages for session
            val messages = database.aiDao().getMessagesForSession(sessionId)

            // Get provider configuration to extract model ID
            val coordinator = Coordinator(context)
            val configResult = coordinator.processUserAction("ai_provider_config.get", mapOf(
                "providerId" to session.providerId
            ))

            if (!configResult.isSuccess) {
                LogManager.aiSession("Failed to get provider config for ${session.providerId}: ${configResult.error}", "ERROR")
                return OperationResult.error(s.shared("error_model_id_extraction_failed"))
            }

            val providerConfigJson = configResult.data?.get("config") as? String
            if (providerConfigJson.isNullOrEmpty()) {
                LogManager.aiSession("Provider config is empty for ${session.providerId}", "ERROR")
                return OperationResult.error(s.shared("error_model_id_extraction_failed"))
            }

            // Extract model ID from config JSON
            val configJson = JSONObject(providerConfigJson)
            val modelId = configJson.optString("model").takeIf { it.isNotEmpty() }
            if (modelId == null) {
                LogManager.aiSession("Model ID not found in provider config for ${session.providerId}", "ERROR")
                return OperationResult.error(s.shared("error_model_id_extraction_failed"))
            }

            // Calculate cost using SessionCostCalculator
            val cost = SessionCostCalculator.calculateSessionCost(
                messages = messages,
                providerId = session.providerId,
                modelId = modelId
            )

            if (cost == null) {
                LogManager.aiSession("Cost calculation failed for session $sessionId", "ERROR")
                return OperationResult.error(s.shared("error_cost_calculation_failed"))
            }

            LogManager.aiSession("Session cost calculated: sessionId=$sessionId, total=\$${String.format("%.3f", cost.totalCost ?: 0.0)}, priceAvailable=${cost.priceAvailable}", "INFO")

            // Build map without null values for costs if price unavailable
            val resultMap = buildMap<String, Any> {
                put("sessionId", sessionId)
                put("modelId", cost.modelId)
                put("totalUncachedInputTokens", cost.totalUncachedInputTokens)
                put("totalCacheWriteTokens", cost.totalCacheWriteTokens)
                put("totalCacheReadTokens", cost.totalCacheReadTokens)
                put("totalOutputTokens", cost.totalOutputTokens)
                put("priceAvailable", cost.priceAvailable)
                put("currency", "USD")

                // Only include cost fields if price is available
                if (cost.priceAvailable) {
                    cost.inputCost?.let { put("inputCost", it) }
                    cost.cacheWriteCost?.let { put("cacheWriteCost", it) }
                    cost.cacheReadCost?.let { put("cacheReadCost", it) }
                    cost.outputCost?.let { put("outputCost", it) }
                    cost.totalCost?.let { put("totalCost", it) }
                }
            }

            return OperationResult.success(resultMap)

        } catch (e: Exception) {
            LogManager.aiSession("Failed to get session cost: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_cost_calculation_failed"))
        }
    }

    /**
     * Toggle validation requirement for a session
     */
    private suspend fun toggleValidation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val requireValidation = params.optBoolean("requireValidation", false)

        LogManager.aiSession("Toggling validation for session $sessionId: $requireValidation", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Get session
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Update requireValidation field
            val updatedSession = session.copy(requireValidation = requireValidation)
            dao.updateSession(updatedSession)

            LogManager.aiSession("Successfully toggled validation for session $sessionId: $requireValidation", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "requireValidation" to requireValidation
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to toggle validation for session $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_toggle_validation"))
        }
    }

    /**
     * Update session state (IDLE, PROCESSING, WAITING_NETWORK, WAITING_USER_RESPONSE, WAITING_VALIDATION)
     */
    private suspend fun updateSessionState(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val stateString = params.optString("state").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error("Parameter 'state' is required")

        LogManager.aiSession("Updating session state for $sessionId: $stateString", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Get session
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Update state field
            val updatedSession = session.copy(state = stateString)
            dao.updateSession(updatedSession)

            LogManager.aiSession("Successfully updated session state for $sessionId: $stateString", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "state" to stateString
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update session state for $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to update session state")
        }
    }

    /**
     * Update last network error time (for inactivity calculation)
     */
    private suspend fun updateLastNetworkErrorTime(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val timestamp = params.optLong("timestamp", System.currentTimeMillis())

        LogManager.aiSession("Updating last network error time for $sessionId: $timestamp", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Get session
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Update lastNetworkErrorTime field
            val updatedSession = session.copy(lastNetworkErrorTime = timestamp)
            dao.updateSession(updatedSession)

            LogManager.aiSession("Successfully updated last network error time for $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "lastNetworkErrorTime" to timestamp
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update last network error time for $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to update last network error time")
        }
    }

    /**
     * Update session end reason (COMPLETED, LIMIT_REACHED, INACTIVITY_TIMEOUT, CHAT_EVICTION, DISMISSED, USER_CANCELLED)
     */
    private suspend fun updateEndReason(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val endReasonString = params.optString("endReason").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error("Parameter 'endReason' is required")

        LogManager.aiSession("Updating session end reason for $sessionId: $endReasonString", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Get session
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Update endReason field
            val updatedSession = session.copy(endReason = endReasonString)
            dao.updateSession(updatedSession)

            LogManager.aiSession("Successfully updated session end reason for $sessionId: $endReasonString", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "endReason" to endReasonString
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update session end reason for $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to update session end reason")
        }
    }

    /**
     * Reset session state and endReason (called when session is reactivated)
     * Sets state to IDLE and endReason to null
     */
    private suspend fun resetSessionState(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("sessionId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Resetting session state and endReason for $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Get session
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Reset state to IDLE and endReason to null
            val updatedSession = session.copy(
                state = SessionState.IDLE.name,
                endReason = null,
                lastNetworkErrorTime = null  // Also reset network error time
            )
            dao.updateSession(updatedSession)

            LogManager.aiSession("Successfully reset session state for $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "sessionId" to sessionId,
                "state" to SessionState.IDLE.name
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to reset session state for $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to reset session state")
        }
    }

    /**
     * Get AI database DAO
     */
    private fun getAIDao(): AIDao {
        return AppDatabase.getDatabase(context).aiDao()
    }

    /**
     * Verbalize AI session operation
     * AI session management is typically not exposed to AI actions
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}