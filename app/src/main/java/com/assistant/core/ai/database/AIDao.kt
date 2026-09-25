package com.assistant.core.ai.database

import androidx.room.*

/**
 * DAO for AI sessions and messages
 */
@Dao
interface AIDao {

    // === Sessions ===

    @Query("SELECT * FROM ai_sessions ORDER BY last_activity DESC")
    suspend fun getAllSessions(): List<AISessionEntity>

    @Query("SELECT * FROM ai_sessions WHERE is_active = 1 ORDER BY last_activity DESC LIMIT 1")
    suspend fun getActiveSession(): AISessionEntity?

    @Query("SELECT * FROM ai_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): AISessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AISessionEntity)

    @Update
    suspend fun updateSession(session: AISessionEntity)

    @Query("UPDATE ai_sessions SET is_active = 0")
    suspend fun deactivateAllSessions()

    @Query("UPDATE ai_sessions SET is_active = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: String)

    @Query("UPDATE ai_sessions SET is_active = 1 WHERE id = :sessionId")
    suspend fun activateSession(sessionId: String)

    @Query("UPDATE ai_sessions SET last_activity = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionActivity(sessionId: String, timestamp: Long)

    @Query("UPDATE ai_sessions SET end_reason = :endReason WHERE id = :sessionId")
    suspend fun updateSessionEndReason(sessionId: String, endReason: String?)

    @Query("UPDATE ai_sessions SET app_state_snapshot = :snapshot WHERE id = :sessionId")
    suspend fun updateAppStateSnapshot(sessionId: String, snapshot: String)

    @Delete
    suspend fun deleteSession(session: AISessionEntity)

    // === Messages ===

    /** What the messages of these sessions say about their AI calls' cost: see SessionCost */
    @Query("""
        SELECT session_id, input_tokens, cache_write_tokens, cache_read_tokens, output_tokens,
               model_id, input_price, cache_write_price, cache_read_price, output_price, usage_unknown
        FROM session_messages
        WHERE session_id IN (:sessionIds) AND (sender = 'AI' OR usage_unknown = 1)
    """)
    suspend fun getCallUsages(sessionIds: List<String>): List<CallUsageRow>

    @Query("SELECT * FROM session_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<SessionMessageEntity>

    @Query("SELECT * FROM session_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): SessionMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SessionMessageEntity)

    @Update
    suspend fun updateMessage(message: SessionMessageEntity)

    @Delete
    suspend fun deleteMessage(message: SessionMessageEntity)

    @Query("DELETE FROM session_messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    // === Utility queries ===

    @Query("SELECT COUNT(*) FROM ai_sessions WHERE type = :type")
    suspend fun getSessionCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM session_messages WHERE session_id = :sessionId")
    suspend fun getMessageCountForSession(sessionId: String): Int

    // === Provider Configurations ===

    @Query("SELECT * FROM ai_provider_configs ORDER BY provider_id ASC")
    suspend fun getAllProviderConfigs(): List<AIProviderConfigEntity>

    @Query("SELECT * FROM ai_provider_configs WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveProviderConfig(): AIProviderConfigEntity?

    @Query("SELECT * FROM ai_provider_configs WHERE provider_id = :providerId")
    suspend fun getProviderConfig(providerId: String): AIProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderConfig(config: AIProviderConfigEntity)

    @Update
    suspend fun updateProviderConfig(config: AIProviderConfigEntity)

    @Query("UPDATE ai_provider_configs SET is_active = 0")
    suspend fun deactivateAllProviders()

    @Query("UPDATE ai_provider_configs SET is_active = 1 WHERE provider_id = :providerId")
    suspend fun activateProvider(providerId: String)

    @Delete
    suspend fun deleteProviderConfig(config: AIProviderConfigEntity)

    @Query("DELETE FROM ai_provider_configs WHERE provider_id = :providerId")
    suspend fun deleteProviderConfigById(providerId: String)

    // === Automations ===

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getAutomationById(id: String): AutomationEntity?

    @Query("SELECT * FROM automations WHERE zone_id = :zoneId ORDER BY created_at DESC")
    suspend fun getAutomationsByZone(zoneId: String): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE seed_session_id = :seedSessionId")
    suspend fun getAutomationBySeedSession(seedSessionId: String): AutomationEntity?

    @Query("SELECT * FROM automations ORDER BY created_at DESC")
    suspend fun getAllAutomations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE is_enabled = 1")
    suspend fun getAllEnabledAutomations(): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity)

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomationById(id: String)

    @Query("UPDATE automations SET is_enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setAutomationEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE automations SET last_execution_id = :executionId WHERE id = :id")
    suspend fun updateAutomationLastExecution(id: String, executionId: String)

    // === History Queries ===

    /**
     * List CHAT sessions with search and pagination
     * Search in session name and message content (LIKE NOCASE)
     * Filter by period (optional) and only completed sessions (endReason IS NOT NULL)
     */
    @Query("""
        SELECT DISTINCT s.* FROM ai_sessions s
        LEFT JOIN session_messages m ON m.session_id = s.id
        WHERE s.type = 'CHAT'
          AND s.end_reason IS NOT NULL
          AND (:search IS NULL OR :search = '' OR
               s.name LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.rich_content_json LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.text_content LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.ai_message_json LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:startTime IS NULL OR s.created_at >= :startTime)
          AND (:endTime IS NULL OR s.created_at <= :endTime)
        ORDER BY s.created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getChatSessionsWithSearch(
        search: String?,
        startTime: Long?,
        endTime: Long?,
        limit: Int,
        offset: Int
    ): List<AISessionEntity>

    /**
     * Count CHAT sessions matching search and period criteria
     * Used for pagination calculation
     */
    @Query("""
        SELECT COUNT(DISTINCT s.id) FROM ai_sessions s
        LEFT JOIN session_messages m ON m.session_id = s.id
        WHERE s.type = 'CHAT'
          AND s.end_reason IS NOT NULL
          AND (:search IS NULL OR :search = '' OR
               s.name LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.rich_content_json LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.text_content LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.ai_message_json LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:startTime IS NULL OR s.created_at >= :startTime)
          AND (:endTime IS NULL OR s.created_at <= :endTime)
    """)
    suspend fun countChatSessionsWithSearch(
        search: String?,
        startTime: Long?,
        endTime: Long?
    ): Int

    /**
     * Get first user message for a session (for preview)
     * Returns the first message with sender = 'USER'
     */
    @Query("""
        SELECT * FROM session_messages
        WHERE session_id = :sessionId AND sender = 'USER'
        ORDER BY timestamp ASC
        LIMIT 1
    """)
    suspend fun getFirstUserMessage(sessionId: String): SessionMessageEntity?

    /**
     * Update session name (for rename operation)
     */
    @Query("UPDATE ai_sessions SET name = :name WHERE id = :sessionId")
    suspend fun updateSessionName(sessionId: String, name: String)

    /**
     * Delete session by ID
     * Messages are deleted automatically via ON DELETE CASCADE FK constraint
     */
    @Query("DELETE FROM ai_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    // === Automation Scheduling Queries ===

    /**
     * Get ALL incomplete automation sessions (for any automation, enabled or disabled)
     * Returns sessions with endReason IN (null, 'NETWORK_ERROR', 'SUSPENDED') that are not active
     * Used by scheduler to detect all sessions to resume (crash, network timeout, suspended by CHAT)
     * Ordered by scheduledExecutionTime ASC (oldest first)
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE type = 'AUTOMATION'
          AND (end_reason IS NULL OR end_reason IN ('NETWORK_ERROR', 'SUSPENDED'))
          AND is_active = 0
        ORDER BY scheduled_execution_time ASC
    """)
    suspend fun getAllIncompleteAutomationSessions(): List<AISessionEntity>

    /**
     * Get incomplete automation session for a specific automation
     * Returns sessions with endReason IN (null, 'NETWORK_ERROR', 'SUSPENDED') that are not active
     * Used by scheduler to detect sessions to resume (crash, network timeout)
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND (end_reason IS NULL OR end_reason IN ('NETWORK_ERROR', 'SUSPENDED'))
          AND is_active = 0
        ORDER BY scheduled_execution_time DESC
        LIMIT 1
    """)
    suspend fun getIncompleteAutomationSession(automationId: String): AISessionEntity?

    /**
     * Get last completed automation session for a specific automation
     * Returns sessions with endReason IN ('COMPLETED', 'CANCELLED', 'TIMEOUT', 'ERROR')
     * Used by scheduler to calculate next expected execution time
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND end_reason IN ('COMPLETED', 'CANCELLED', 'TIMEOUT', 'ERROR')
        ORDER BY scheduled_execution_time DESC
        LIMIT 1
    """)
    suspend fun getLastCompletedAutomationSession(automationId: String): AISessionEntity?

    /**
     * Get sessions for automation with pagination and optional time filtering
     * Returns sessions ordered by scheduledExecutionTime DESC (most recent first)
     * Used by AutomationScreen to display execution history
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND (:startTime IS NULL OR created_at >= :startTime)
          AND (:endTime IS NULL OR created_at <= :endTime)
        ORDER BY scheduled_execution_time DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getSessionsForAutomationPaginated(
        automationId: String,
        limit: Int,
        offset: Int,
        startTime: Long?,
        endTime: Long?
    ): List<AISessionEntity>

    /**
     * Count sessions for automation with optional time filtering
     * Used by AutomationScreen for pagination calculation
     */
    @Query("""
        SELECT COUNT(*) FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND (:startTime IS NULL OR created_at >= :startTime)
          AND (:endTime IS NULL OR created_at <= :endTime)
    """)
    suspend fun countSessionsForAutomation(
        automationId: String,
        startTime: Long?,
        endTime: Long?
    ): Int
}

/** One message's share of its session's cost, as getCallUsages reads it */
data class CallUsageRow(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "input_tokens") val inputTokens: Int,
    @ColumnInfo(name = "cache_write_tokens") val cacheWriteTokens: Int,
    @ColumnInfo(name = "cache_read_tokens") val cacheReadTokens: Int,
    @ColumnInfo(name = "output_tokens") val outputTokens: Int,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "input_price") val inputPrice: Double?,
    @ColumnInfo(name = "cache_write_price") val cacheWritePrice: Double?,
    @ColumnInfo(name = "cache_read_price") val cacheReadPrice: Double?,
    @ColumnInfo(name = "output_price") val outputPrice: Double?,
    @ColumnInfo(name = "usage_unknown") val usageUnknown: Boolean
) {
    fun toCallUsage() = com.assistant.core.ai.data.CallUsage(
        inputTokens = inputTokens,
        cacheWriteTokens = cacheWriteTokens,
        cacheReadTokens = cacheReadTokens,
        outputTokens = outputTokens,
        pricing = modelId?.let {
            com.assistant.core.ai.data.CallPricing(it, inputPrice, cacheWritePrice, cacheReadPrice, outputPrice)
        },
        usageUnknown = usageUnknown
    )
}
