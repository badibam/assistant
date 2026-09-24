package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionType
import org.json.JSONObject

/**
 * Configuration for AI autonomous loop limits.
 *
 * One limit per session type: how many times the AI may be called on its own. The count
 * restarts at every user intervention in a CHAT, so the limit stops an AI that keeps calling
 * itself -- each call is paid for -- without bounding a conversation. An AUTOMATION has nobody
 * to intervene and counts its whole session.
 *
 * These defaults are the only ones: a fresh install or a reset writes them to the database
 * through toSettingsJson(), and fromSettingsJson() reads them back.
 */
data class AILimitsConfig(
    /** Maximum AI calls in a row for CHAT sessions, counted from the last user intervention */
    val chatMaxAutonomousRoundtrips: Int = 10,

    /** Maximum total autonomous roundtrips for AUTOMATION sessions (safety against infinite loops) */
    val automationMaxAutonomousRoundtrips: Int = 20
) {
    /**
     * Get limits for specific session type
     */
    fun getLimitsForSessionType(sessionType: SessionType): SessionLimits {
        return when (sessionType) {
            SessionType.CHAT -> SessionLimits(
                maxAutonomousRoundtrips = chatMaxAutonomousRoundtrips
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips
            )
            SessionType.SEED -> {
                // SEED sessions are never executed, use AUTOMATION limits as fallback
                SessionLimits(
                    maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips
                )
            }
        }
    }

    /** The ai_limits settings as stored in the database */
    fun toSettingsJson(): String = JSONObject().apply {
        put(KEY_CHAT, chatMaxAutonomousRoundtrips)
        put(KEY_AUTOMATION, automationMaxAutonomousRoundtrips)
    }.toString()

    companion object {
        const val KEY_CHAT = "chat_max_autonomous_roundtrips"
        const val KEY_AUTOMATION = "automation_max_autonomous_roundtrips"

        fun default() = AILimitsConfig()

        /**
         * Read the stored ai_limits settings. Both keys are required: a missing one throws
         * rather than standing for a default, since the database is written from default()
         * and migrated to carry both.
         */
        fun fromSettingsJson(settings: JSONObject) = AILimitsConfig(
            chatMaxAutonomousRoundtrips = settings.getInt(KEY_CHAT),
            automationMaxAutonomousRoundtrips = settings.getInt(KEY_AUTOMATION)
        )
    }
}

/**
 * Limits for a specific session type (helper class)
 */
data class SessionLimits(
    val maxAutonomousRoundtrips: Int
)
