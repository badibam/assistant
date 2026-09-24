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
    val automationMaxAutonomousRoundtrips: Int = 20,

    /**
     * Size, in characters of the text the AI receives, above which data asked for in a CHAT
     * waits for the user's confirmation. 15 000 is above the chat data of the 2026-09-18 backup
     * but its largest send (median 4 400, 90th percentile 13 000, maximum 33 000).
     */
    val chatMaxDataChars: Int = 15_000,

    /**
     * Same, for an AUTOMATION, where data above it is refused outright: nobody is there to
     * confirm. 100 000 is over twice what the automations of the 2026-09-18 backup send
     * (about 43 000 on nearly every run).
     */
    val automationMaxDataChars: Int = 100_000
) {
    /**
     * Get limits for specific session type
     */
    fun getLimitsForSessionType(sessionType: SessionType): SessionLimits {
        return when (sessionType) {
            SessionType.CHAT -> SessionLimits(
                maxAutonomousRoundtrips = chatMaxAutonomousRoundtrips,
                maxDataChars = chatMaxDataChars
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips,
                maxDataChars = automationMaxDataChars
            )
            SessionType.SEED -> {
                // SEED sessions are never executed, use AUTOMATION limits as fallback
                SessionLimits(
                    maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips,
                    maxDataChars = automationMaxDataChars
                )
            }
        }
    }

    /** The ai_limits settings as stored in the database */
    fun toSettingsJson(): String = JSONObject().apply {
        put(KEY_CHAT, chatMaxAutonomousRoundtrips)
        put(KEY_AUTOMATION, automationMaxAutonomousRoundtrips)
        put(KEY_CHAT_DATA, chatMaxDataChars)
        put(KEY_AUTOMATION_DATA, automationMaxDataChars)
    }.toString()

    companion object {
        const val KEY_CHAT = "chat_max_autonomous_roundtrips"
        const val KEY_AUTOMATION = "automation_max_autonomous_roundtrips"
        const val KEY_CHAT_DATA = "chat_max_data_chars"
        const val KEY_AUTOMATION_DATA = "automation_max_data_chars"

        fun default() = AILimitsConfig()

        /**
         * Read the stored ai_limits settings. Every key is required: a missing one throws
         * rather than standing for a default, since the database is written from default()
         * and migrated to carry them all.
         */
        fun fromSettingsJson(settings: JSONObject) = AILimitsConfig(
            chatMaxAutonomousRoundtrips = settings.getInt(KEY_CHAT),
            automationMaxAutonomousRoundtrips = settings.getInt(KEY_AUTOMATION),
            chatMaxDataChars = settings.getInt(KEY_CHAT_DATA),
            automationMaxDataChars = settings.getInt(KEY_AUTOMATION_DATA)
        )
    }
}

/**
 * Limits for a specific session type (helper class)
 */
data class SessionLimits(
    val maxAutonomousRoundtrips: Int,
    /** Characters of data text above which a CHAT asks for confirmation and an AUTOMATION refuses */
    val maxDataChars: Int
)
