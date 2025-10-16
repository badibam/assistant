package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionType

/**
 * Configuration for AI autonomous loop limits.
 *
 * These limits prevent infinite loops and control AI autonomy.
 * Different limits apply to CHAT vs AUTOMATION sessions.
 *
 * Stored in AppConfig and cached in AppConfigManager.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Consecutive counters reset when different counter type increments
 * - Total roundtrips never reset during session
 * - Limits checked after each event that increments counters
 */
data class AILimitsConfig(
    // ==================== CHAT LIMITS ====================

    /** Maximum consecutive data query iterations for CHAT sessions */
    val chatMaxDataQueryIterations: Int = 3,

    /** Maximum action retry attempts for CHAT sessions */
    val chatMaxActionRetries: Int = 3,

    /** Maximum format error retry attempts for CHAT sessions */
    val chatMaxFormatErrorRetries: Int = 3,

    /** Maximum total autonomous roundtrips for CHAT sessions */
    val chatMaxAutonomousRoundtrips: Int = 10,

    // ==================== AUTOMATION LIMITS ====================

    /** Maximum consecutive data query iterations for AUTOMATION sessions */
    val automationMaxDataQueryIterations: Int = 5,

    /** Maximum action retry attempts for AUTOMATION sessions */
    val automationMaxActionRetries: Int = 5,

    /** Maximum format error retry attempts for AUTOMATION sessions */
    val automationMaxFormatErrorRetries: Int = 5,

    /** Maximum total autonomous roundtrips for AUTOMATION sessions */
    val automationMaxAutonomousRoundtrips: Int = 20
) {
    /**
     * Get limits for specific session type
     */
    fun getLimitsForSessionType(sessionType: SessionType): SessionLimits {
        return when (sessionType) {
            SessionType.CHAT -> SessionLimits(
                maxDataQueryIterations = chatMaxDataQueryIterations,
                maxActionRetries = chatMaxActionRetries,
                maxFormatErrorRetries = chatMaxFormatErrorRetries,
                maxAutonomousRoundtrips = chatMaxAutonomousRoundtrips
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxDataQueryIterations = automationMaxDataQueryIterations,
                maxActionRetries = automationMaxActionRetries,
                maxFormatErrorRetries = automationMaxFormatErrorRetries,
                maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips
            )
            SessionType.SEED -> {
                // SEED sessions are never executed, use AUTOMATION limits as fallback
                SessionLimits(
                    maxDataQueryIterations = automationMaxDataQueryIterations,
                    maxActionRetries = automationMaxActionRetries,
                    maxFormatErrorRetries = automationMaxFormatErrorRetries,
                    maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips
                )
            }
        }
    }

    companion object {
        /**
         * Default configuration matching specs
         */
        fun default() = AILimitsConfig()
    }
}

/**
 * Limits for a specific session type (helper class)
 */
data class SessionLimits(
    val maxDataQueryIterations: Int,
    val maxActionRetries: Int,
    val maxFormatErrorRetries: Int,
    val maxAutonomousRoundtrips: Int
)
