package com.assistant.core.ai.services

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.database.AIDao
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
                "list_sessions_for_automation" -> listSessionsForAutomation(params, token)
                "update_session" -> updateSession(params, token)
                "delete_session" -> deleteSession(params, token)
                "set_active_session" -> setActiveSession(params, token)

                // History operations
                "list" -> listChatSessions(params, token)
                "rename" -> renameSession(params, token)
                "delete" -> deleteChatSession(params, token)

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

                // Session state updates (V2 - managed by AIStateRepository)
                "set_end_reason" -> setEndReason(params, token)

                // SEED pre-fill management
                "clear_seed" -> clearSeed(params, token)

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
        val providerId = params.optString("provider_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_provider_id_required"))

        LogManager.aiSession("Creating AI session: name=$name, type=$type, providerId=$providerId", "DEBUG")

        // Create new session entity
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val sessionEntity = AISessionEntity(
            id = sessionId,
            name = name,
            type = SessionType.valueOf(type),
            automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }, // null for CHAT
            scheduledExecutionTime = if (params.has("scheduled_execution_time")) params.getLong("scheduled_execution_time") else null,
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
                val emptyRichMessage = RichMessage(segments = emptyList())

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
                "session_id" to sessionId,
                "name" to name,
                "type" to type,
                "provider_id" to providerId,
                "created_at" to now
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to create session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_create_session").format(e.message ?: ""))
        }
    }

    private suspend fun getSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
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
                    "require_validation" to sessionEntity.requireValidation,
                    "automation_id" to sessionEntity.automationId,
                    "seed_id" to sessionEntity.seedId,
                    "scheduled_execution_time" to sessionEntity.scheduledExecutionTime,
                    "provider_id" to sessionEntity.providerId,
                    "provider_session_id" to sessionEntity.providerSessionId,
                    "created_at" to sessionEntity.createdAt,
                    "last_activity" to sessionEntity.lastActivity,
                    "is_active" to sessionEntity.isActive,
                    // Frozen at the first call: PromptManager builds L3 from it, and a snapshot
                    // rebuilt on each call changes the prompt right after L1, where the
                    // provider's cache then stops
                    "app_state_snapshot" to sessionEntity.appStateSnapshot
                ),
                "messages" to messageEntities.map { msg ->
                    mapOf(
                        "id" to msg.id,
                        "timestamp" to msg.timestamp,
                        "sender" to msg.sender.name, // Convert enum to string
                        "rich_content_json" to msg.richContentJson,
                        "text_content" to msg.textContent,
                        "ai_message_json" to msg.aiMessageJson,
                        "system_message_json" to msg.systemMessageJson,
                        "exclude_from_prompt" to msg.excludeFromPrompt
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
                    "provider_id" to session.providerId,
                    "created_at" to session.createdAt,
                    "last_activity" to session.lastActivity,
                    "is_active" to session.isActive
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

    private suspend fun listSessionsForAutomation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val automationId = params.optString("automation_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_automation_id_required"))
        val limit = params.optInt("limit", 10)
        val page = params.optInt("page", 1)
        val startTime = if (params.has("start_time")) params.getLong("start_time") else null
        val endTime = if (params.has("end_time")) params.getLong("end_time") else null

        LogManager.aiSession("Listing sessions for automation: automationId=$automationId, limit=$limit, page=$page, startTime=$startTime, endTime=$endTime", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Calculate offset for pagination (page 1 = offset 0)
            val offset = (page - 1) * limit

            // Get paginated sessions
            val sessionEntities = dao.getSessionsForAutomationPaginated(
                automationId = automationId,
                limit = limit,
                offset = offset,
                startTime = startTime,
                endTime = endTime
            )

            // Get total count for pagination
            val totalEntries = dao.countSessionsForAutomation(
                automationId = automationId,
                startTime = startTime,
                endTime = endTime
            )

            val totalPages = if (totalEntries == 0) 0 else ((totalEntries - 1) / limit) + 1

            LogManager.aiSession("Found ${sessionEntities.size} sessions for automation $automationId (total: $totalEntries, page: $page/$totalPages)", "DEBUG")

            // Each session's cost, summed from its messages in one query for the page
            val costs = dao.getCallUsages(sessionEntities.map { it.id })
                .groupBy { it.sessionId }
                .mapValues { (_, rows) -> SessionCost.of(rows.map { it.toCallUsage() }) }

            // Map sessions to result format including all necessary fields for ExecutionCard
            val sessions = sessionEntities.map { session ->
                val cost = costs[session.id] ?: SessionCost.of(emptyList())
                mapOf(
                    "id" to session.id,
                    "name" to session.name,
                    "type" to session.type.name,
                    "automation_id" to session.automationId,
                    "scheduled_execution_time" to session.scheduledExecutionTime,
                    "provider_id" to session.providerId,
                    "provider_session_id" to session.providerSessionId,
                    "created_at" to session.createdAt,
                    "last_activity" to session.lastActivity,
                    "is_active" to session.isActive,
                    "phase" to session.phase,
                    "end_reason" to session.endReason,
                    "total_roundtrips" to session.totalRoundtrips,
                    "total_tokens" to cost.totalTokens,
                    "total_cost" to cost.totalCost,
                    "cost_is_lower_bound" to cost.isLowerBound
                )
            }

            return OperationResult.success(mapOf(
                "sessions" to sessions,
                "pagination" to mapOf(
                    "current_page" to page,
                    "total_pages" to totalPages,
                    "total_entries" to totalEntries
                )
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list sessions for automation: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_sessions").format(e.message ?: ""))
        }
    }

    private suspend fun updateSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
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
                "session_id" to sessionId,
                "name" to updatedEntity.name,
                "updated_at" to now
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_session").format(e.message ?: ""))
        }
    }

    private suspend fun deleteSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
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
                "session_id" to sessionId,
                "deleted" to true
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to delete session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_delete_session").format(e.message ?: ""))
        }
    }

    private suspend fun setActiveSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
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
                "session_id" to sessionId,
                "is_active" to true
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
                    "has_active_session" to false
                ))
            }

            // Get messages for the active session
            val messageEntities = database.aiDao().getMessagesForSession(activeSessionEntity.id)

            LogManager.aiSession("Found active session: ${activeSessionEntity.id} with ${messageEntities.size} messages", "DEBUG")

            return OperationResult.success(mapOf(
                "has_active_session" to true,
                "session_id" to activeSessionEntity.id,
                "session" to mapOf(
                    "id" to activeSessionEntity.id,
                    "name" to activeSessionEntity.name,
                    "type" to activeSessionEntity.type.name, // Convert enum to string
                    "require_validation" to activeSessionEntity.requireValidation,
                    "provider_id" to activeSessionEntity.providerId,
                    "provider_session_id" to activeSessionEntity.providerSessionId,
                    "created_at" to activeSessionEntity.createdAt,
                    "last_activity" to activeSessionEntity.lastActivity,
                    "is_active" to activeSessionEntity.isActive
                ),
                "messages" to messageEntities.map { msg ->
                    mapOf(
                        "id" to msg.id,
                        "timestamp" to msg.timestamp,
                        "sender" to msg.sender.name, // Convert enum to string
                        "rich_content_json" to msg.richContentJson,
                        "text_content" to msg.textContent,
                        "ai_message_json" to msg.aiMessageJson,
                        "system_message_json" to msg.systemMessageJson,
                        "exclude_from_prompt" to msg.excludeFromPrompt
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
                "sessions_deactivated" to true
            ))

        } catch (e: Exception) {
            LogManager.aiSession("Failed to stop active session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_stop_session"))
        }
    }

    private suspend fun createMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Extract required parameters
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
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
            val richContentJson = params.optString("rich_content")?.takeIf { it.isNotEmpty() }
            val textContent = params.optString("text_content")?.takeIf { it.isNotEmpty() }
            val aiMessageJson = params.optString("ai_message_json")?.takeIf { it.isNotEmpty() }

            // Handle SystemMessage if provided
            val systemMessageJson = if (params.has("system_message")) {
                val systemMessage = params.get("system_message") as? SystemMessage
                systemMessage?.toJson()
            } else {
                null
            }

            // Get excludeFromPrompt flag
            val excludeFromPrompt = params.optBoolean("exclude_from_prompt", false)

            // Extract token usage metrics (for AI messages only, 0 for USER/SYSTEM)
            val inputTokens = params.optInt("input_tokens", 0)
            val cacheWriteTokens = params.optInt("cache_write_tokens", 0)
            val cacheReadTokens = params.optInt("cache_read_tokens", 0)
            val outputTokens = params.optInt("output_tokens", 0)

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
                "message_id" to messageId,
                "session_id" to sessionId,
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

        val messageId = params.optString("message_id").takeIf { it.isNotEmpty() }
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
                    "session_id" to messageEntity.sessionId,
                    "timestamp" to messageEntity.timestamp,
                    "sender" to messageEntity.sender.name,
                    "rich_content_json" to messageEntity.richContentJson,
                    "text_content" to messageEntity.textContent,
                    "ai_message_json" to messageEntity.aiMessageJson,
                    "system_message_json" to messageEntity.systemMessageJson
                )
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to get message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_get_message").format(e.message ?: ""))
        }
    }

    private suspend fun listMessages(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
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
                    "rich_content_json" to msg.richContentJson,
                    "text_content" to msg.textContent,
                    "ai_message_json" to msg.aiMessageJson,
                    "system_message_json" to msg.systemMessageJson
                )
            }

            return OperationResult.success(mapOf(
                "messages" to messages,
                "count" to messages.size,
                "session_id" to sessionId
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list messages: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_messages").format(e.message ?: ""))
        }
    }

    private suspend fun updateMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("message_id").takeIf { it.isNotEmpty() }
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
            val richContentJson = params.optString("rich_content_json")
            val textContent = params.optString("text_content")
            val aiMessageJson = params.optString("ai_message_json")

            val updatedEntity = messageEntity.copy(
                richContentJson = if (richContentJson.isNotEmpty()) richContentJson else messageEntity.richContentJson,
                textContent = if (textContent.isNotEmpty()) textContent else messageEntity.textContent,
                aiMessageJson = if (aiMessageJson.isNotEmpty()) aiMessageJson else messageEntity.aiMessageJson
            )

            database.aiDao().updateMessage(updatedEntity)

            LogManager.aiSession("Successfully updated message: $messageId", "INFO")

            return OperationResult.success(mapOf(
                "message_id" to messageId,
                "updated" to true
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_message").format(e.message ?: ""))
        }
    }

    private suspend fun deleteMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("message_id").takeIf { it.isNotEmpty() }
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
                "message_id" to messageId,
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

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_session_id_required"))

        LogManager.aiSession("Getting cost for session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)

            // Get session
            val session = database.aiDao().getSession(sessionId)
                ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))

            val cost = SessionCost.of(database.aiDao().getCallUsages(listOf(sessionId)).map { it.toCallUsage() })

            LogManager.aiSession("Session cost: sessionId=$sessionId, total=\$${String.format("%.3f", cost.totalCost)}, calls with unknown cost=${cost.callsWithUnknownCost}", "DEBUG")

            // Costs count known prices only: with calls of unknown cost, they are a lower bound
            val resultMap = mapOf(
                "session_id" to sessionId,
                "total_uncached_input_tokens" to cost.uncachedInputTokens,
                "total_cache_write_tokens" to cost.cacheWriteTokens,
                "total_cache_read_tokens" to cost.cacheReadTokens,
                "total_output_tokens" to cost.outputTokens,
                "input_cost" to cost.inputCost,
                "cache_write_cost" to cost.cacheWriteCost,
                "cache_read_cost" to cost.cacheReadCost,
                "output_cost" to cost.outputCost,
                "total_cost" to cost.totalCost,
                "calls_with_unknown_cost" to cost.callsWithUnknownCost,
                "currency" to "USD"
            )

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

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val requireValidation = params.optBoolean("require_validation", false)

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
                "session_id" to sessionId,
                "require_validation" to requireValidation
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to toggle validation for session $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_toggle_validation"))
        }
    }

    /**
     * Set session end reason (COMPLETED, TIMEOUT, ERROR, CANCELLED, INTERRUPTED, NETWORK_ERROR, SUSPENDED)
     * Note: V2 architecture manages state transitions via AIStateRepository, this is kept for backward compatibility
     */
    private suspend fun setEndReason(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val endReasonString = params.optString("end_reason").takeIf { it.isNotEmpty() }
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
                "session_id" to sessionId,
                "end_reason" to endReasonString
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update session end reason for $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to update session end reason")
        }
    }

    /**
     * Clear seedId from session after SEED messages have been loaded for composer pre-fill
     */
    private suspend fun clearSeed(params: JSONObject, token: CancellationToken): OperationResult = withContext(Dispatchers.IO) {
        if (token.isCancelled) return@withContext OperationResult.cancelled()

        val sessionId = params.optString("session_id")
        if (sessionId.isEmpty()) {
            return@withContext OperationResult.error("session_id is required")
        }

        try {
            val dao = getAIDao()
            val session = dao.getSession(sessionId)
                ?: return@withContext OperationResult.error("Session not found: $sessionId")

            // Update seedId to null
            dao.updateSession(session.copy(seedId = null))

            LogManager.aiSession("Cleared seedId for session $sessionId", "DEBUG")

            return@withContext OperationResult.success(mapOf(
                "session_id" to sessionId
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to clear seedId for $sessionId: ${e.message}", "ERROR", e)
            return@withContext OperationResult.error("Failed to clear seedId")
        }
    }

    /**
     * List CHAT sessions with search and pagination (for History feature)
     * Same pattern as list_sessions_for_automation
     */
    private suspend fun listChatSessions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val search = params.optString("search").takeIf { it.isNotEmpty() }
        val limit = params.optInt("limit", 20)
        val page = params.optInt("page", 1)
        val startTime = if (params.has("start_time")) params.getLong("start_time") else null
        val endTime = if (params.has("end_time")) params.getLong("end_time") else null

        LogManager.aiSession("Listing CHAT sessions: search=$search, page=$page, limit=$limit, startTime=$startTime, endTime=$endTime", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Calculate offset for pagination (page 1 = offset 0)
            val offset = (page - 1) * limit

            // Get sessions and count
            val sessionEntities = dao.getChatSessionsWithSearch(
                search = search,
                startTime = startTime,
                endTime = endTime,
                limit = limit,
                offset = offset
            )

            val total = dao.countChatSessionsWithSearch(
                search = search,
                startTime = startTime,
                endTime = endTime
            )

            val totalPages = if (total == 0) 0 else ((total - 1) / limit) + 1

            LogManager.aiSession("Found ${sessionEntities.size} CHAT sessions (total: $total, page: $page/$totalPages)", "DEBUG")

            // Build session list with preview and message count; the previews' pointers are named
            // as their targets are now, read once for the whole page
            val enrichmentText = com.assistant.core.ai.enrichments.EnrichmentText.load(context)
            val sessions = sessionEntities.map { session ->
                // Get message count
                val messageCount = dao.getMessageCountForSession(session.id)

                // Get first user message for preview
                val firstMessage = dao.getFirstUserMessage(session.id)
                val preview = if (firstMessage?.richContentJson != null) {
                    try {
                        val text = RichMessage.fromJson(firstMessage.richContentJson)?.let { enrichmentText.display(it) } ?: ""
                        // Truncate to 60 chars
                        if (text.length > 60) {
                            text.substring(0, 60) + "..."
                        } else {
                            text
                        }
                    } catch (e: Exception) {
                        LogManager.aiSession("Failed to parse richContentJson for preview: ${e.message}", "WARN")
                        ""
                    }
                } else {
                    ""
                }

                mapOf(
                    "id" to session.id,
                    "name" to session.name,
                    "created_at" to session.createdAt,
                    "last_activity" to session.lastActivity,
                    "message_count" to messageCount,
                    "first_user_message" to preview
                )
            }

            return OperationResult.success(mapOf(
                "sessions" to sessions,
                "pagination" to mapOf(
                    "current_page" to page,
                    "total_pages" to totalPages,
                    "total_entries" to total
                )
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list CHAT sessions: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_sessions").format(e.message ?: ""))
        }
    }

    /**
     * Rename a session
     */
    private suspend fun renameSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val name = params.optString("name").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_name_required"))

        // Validate name length (max 60 chars as per FieldType.TEXT)
        if (name.length > 60) {
            return OperationResult.error(s.shared("error_validation_maxlength").format("60"))
        }

        LogManager.aiSession("Renaming session $sessionId to: $name", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Check session exists
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Update name
            dao.updateSessionName(sessionId, name)

            LogManager.aiSession("Successfully renamed session $sessionId to: $name", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "name" to name
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to rename session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_session").format(e.message ?: ""))
        }
    }

    /**
     * Delete a CHAT session (for History feature)
     * Uses CASCADE DELETE via FK constraints to automatically delete associated messages
     */
    private suspend fun deleteChatSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Deleting CHAT session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Check session exists and is CHAT type
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            if (session.type != SessionType.CHAT) {
                LogManager.aiSession("Cannot delete non-CHAT session: $sessionId (type: ${session.type})", "WARN")
                return OperationResult.error("Cannot delete non-CHAT session")
            }

            // Delete session (CASCADE DELETE will handle messages automatically)
            dao.deleteSessionById(sessionId)

            LogManager.aiSession("Successfully deleted CHAT session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "deleted" to true
            ))
        } catch (e: Exception) {
            LogManager.aiSession("Failed to delete CHAT session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_delete_session").format(e.message ?: ""))
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
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}