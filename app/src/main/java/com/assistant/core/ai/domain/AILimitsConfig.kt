package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionType

/**
 * Configuration for AI autonomous loop limits.
 *
 * Simplified limits architecture:
 * - CHAT: No autonomous limits (user controls via interrupt)
 * - AUTOMATION: maxAutonomousRoundtrips only (global safety against infinite loops)
 *
 * Removed limits:
 * - maxFormatErrorRetries: AI should self-correct format errors without artificial limits
 * - maxDataQueryIterations: Legitimate to explore data progressively
 * - maxActionRetries: Commands can fail for various reasons, let AI adapt within roundtrips limit
 *
 * Stored in AppConfig and cached in AppConfigManager.
 */
data class AILimitsConfig(
    /** Maximum total autonomous roundtrips for CHAT sessions (Int.MAX_VALUE = no limit, user controls) */
    val chatMaxAutonomousRoundtrips: Int = Int.MAX_VALUE,

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

    companion object {
        /**
         * Default configuration matching simplified limits
         */
        fun default() = AILimitsConfig()
    }
}

/**
 * Limits for a specific session type (helper class)
 */
data class SessionLimits(
    val maxAutonomousRoundtrips: Int
)
