package com.assistant.core.ai.database

import androidx.room.*

/**
 * DAO for AI sessions and messages
 */
@Dao
interface AIDao {

    // === Sessions ===

    @Query("SELECT * FROM ai_sessions ORDER BY lastActivity DESC")
    suspend fun getAllSessions(): List<AISessionEntity>

    @Query("SELECT * FROM ai_sessions WHERE isActive = 1 ORDER BY lastActivity DESC LIMIT 1")
    suspend fun getActiveSession(): AISessionEntity?

    @Query("SELECT * FROM ai_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): AISessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AISessionEntity)

    @Update
    suspend fun updateSession(session: AISessionEntity)

    @Query("UPDATE ai_sessions SET isActive = 0")
    suspend fun deactivateAllSessions()

    @Query("UPDATE ai_sessions SET isActive = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: String)

    @Query("UPDATE ai_sessions SET isActive = 1 WHERE id = :sessionId")
    suspend fun activateSession(sessionId: String)

    @Query("UPDATE ai_sessions SET lastActivity = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionActivity(sessionId: String, timestamp: Long)

    @Query("UPDATE ai_sessions SET endReason = :endReason WHERE id = :sessionId")
    suspend fun updateSessionEndReason(sessionId: String, endReason: String?)

    @Query("UPDATE ai_sessions SET tokensJson = :tokensJson, costJson = :costJson WHERE id = :sessionId")
    suspend fun updateSessionTokensAndCost(sessionId: String, tokensJson: String?, costJson: String?)

    @Query("UPDATE ai_sessions SET appStateSnapshot = :snapshot WHERE id = :sessionId")
    suspend fun updateAppStateSnapshot(sessionId: String, snapshot: String)

    @Delete
    suspend fun deleteSession(session: AISessionEntity)

    // === Messages ===

    @Query("SELECT * FROM session_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<SessionMessageEntity>

    @Query("SELECT * FROM session_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): SessionMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SessionMessageEntity)

    @Update
    suspend fun updateMessage(message: SessionMessageEntity)

    @Delete
    suspend fun deleteMessage(message: SessionMessageEntity)

    @Query("DELETE FROM session_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    // === Utility queries ===

    @Query("SELECT COUNT(*) FROM ai_sessions WHERE type = :type")
    suspend fun getSessionCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM session_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCountForSession(sessionId: String): Int

    // === Provider Configurations ===

    @Query("SELECT * FROM ai_provider_configs ORDER BY providerId ASC")
    suspend fun getAllProviderConfigs(): List<AIProviderConfigEntity>

    @Query("SELECT * FROM ai_provider_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProviderConfig(): AIProviderConfigEntity?

    @Query("SELECT * FROM ai_provider_configs WHERE providerId = :providerId")
    suspend fun getProviderConfig(providerId: String): AIProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderConfig(config: AIProviderConfigEntity)

    @Update
    suspend fun updateProviderConfig(config: AIProviderConfigEntity)

    @Query("UPDATE ai_provider_configs SET isActive = 0")
    suspend fun deactivateAllProviders()

    @Query("UPDATE ai_provider_configs SET isActive = 1 WHERE providerId = :providerId")
    suspend fun activateProvider(providerId: String)

    @Delete
    suspend fun deleteProviderConfig(config: AIProviderConfigEntity)

    @Query("DELETE FROM ai_provider_configs WHERE providerId = :providerId")
    suspend fun deleteProviderConfigById(providerId: String)

    // === Automations ===

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getAutomationById(id: String): AutomationEntity?

    @Query("SELECT * FROM automations WHERE zoneId = :zoneId ORDER BY createdAt DESC")
    suspend fun getAutomationsByZone(zoneId: String): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE seedSessionId = :seedSessionId")
    suspend fun getAutomationBySeedSession(seedSessionId: String): AutomationEntity?

    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    suspend fun getAllAutomations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE isEnabled = 1")
    suspend fun getAllEnabledAutomations(): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity)

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomationById(id: String)

    @Query("UPDATE automations SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setAutomationEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE automations SET lastExecutionId = :executionId WHERE id = :id")
    suspend fun updateAutomationLastExecution(id: String, executionId: String)

    // === Automation Scheduling Queries ===

    /**
     * Get incomplete automation session for a specific automation
     * Returns sessions with endReason IN (null, 'NETWORK_ERROR', 'SUSPENDED') that are not active
     * Used by scheduler to detect sessions to resume (crash, network timeout)
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE automationId = :automationId
          AND type = 'AUTOMATION'
          AND (endReason IS NULL OR endReason IN ('NETWORK_ERROR', 'SUSPENDED'))
          AND isActive = 0
        ORDER BY scheduledExecutionTime DESC
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
        WHERE automationId = :automationId
          AND type = 'AUTOMATION'
          AND endReason IN ('COMPLETED', 'CANCELLED', 'TIMEOUT', 'ERROR')
        ORDER BY scheduledExecutionTime DESC
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
        WHERE automationId = :automationId
          AND type = 'AUTOMATION'
          AND (:startTime IS NULL OR createdAt >= :startTime)
          AND (:endTime IS NULL OR createdAt <= :endTime)
        ORDER BY scheduledExecutionTime DESC
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
        WHERE automationId = :automationId
          AND type = 'AUTOMATION'
          AND (:startTime IS NULL OR createdAt >= :startTime)
          AND (:endTime IS NULL OR createdAt <= :endTime)
    """)
    suspend fun countSessionsForAutomation(
        automationId: String,
        startTime: Long?,
        endTime: Long?
    ): Int
}