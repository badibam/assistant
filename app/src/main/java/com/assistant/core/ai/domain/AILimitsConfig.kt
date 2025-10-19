package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionType

/**
 * Configuration for AI autonomous loop limits.
 *
 * Simplified limits architecture (V3):
 * - CHAT: No autonomous limits (user controls via interrupt), except format errors
 * - AUTOMATION: maxAutonomousRoundtrips (global safety) + maxFormatErrorRetries (parsing bugs)
 *
 * Removed limits (covered by maxAutonomousRoundtrips):
 * - maxDataQueryIterations: Legitimate to explore data progressively
 * - maxActionRetries: Commands can fail for various reasons, let AI adapt within roundtrips limit
 *
 * Stored in AppConfig and cached in AppConfigManager.
 */
data class AILimitsConfig(
    // ==================== CHAT LIMITS ====================

    /** Maximum format error retry attempts for CHAT sessions */
    val chatMaxFormatErrorRetries: Int = 3,

    /** Maximum total autonomous roundtrips for CHAT sessions (Int.MAX_VALUE = no limit, user controls) */
    val chatMaxAutonomousRoundtrips: Int = Int.MAX_VALUE,

    // ==================== AUTOMATION LIMITS ====================

    /** Maximum format error retry attempts for AUTOMATION sessions */
    val automationMaxFormatErrorRetries: Int = 5,

    /** Maximum total autonomous roundtrips for AUTOMATION sessions (safety against infinite loops) */
    val automationMaxAutonomousRoundtrips: Int = 20
) {
    /**
     * Get limits for specific session type
     */
    fun getLimitsForSessionType(sessionType: SessionType): SessionLimits {
        return when (sessionType) {
            SessionType.CHAT -> SessionLimits(
                maxFormatErrorRetries = chatMaxFormatErrorRetries,
                maxAutonomousRoundtrips = chatMaxAutonomousRoundtrips
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxFormatErrorRetries = automationMaxFormatErrorRetries,
                maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips
            )
            SessionType.SEED -> {
                // SEED sessions are never executed, use AUTOMATION limits as fallback
                SessionLimits(
                    maxFormatErrorRetries = automationMaxFormatErrorRetries,
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
    val maxFormatErrorRetries: Int,
    val maxAutonomousRoundtrips: Int
)
