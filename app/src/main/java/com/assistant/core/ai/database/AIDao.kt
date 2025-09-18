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

    @Query("SELECT * FROM ai_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): AISessionEntity?

    @Query("SELECT * FROM ai_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): AISessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AISessionEntity)

    @Update
    suspend fun updateSession(session: AISessionEntity)

    @Query("UPDATE ai_sessions SET isActive = 0")
    suspend fun deactivateAllSessions()

    @Query("UPDATE ai_sessions SET isActive = 1 WHERE id = :sessionId")
    suspend fun activateSession(sessionId: String)

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
}