package com.assistant.core.config

/**
 * Time-related configuration
 */
data class TimeConfig(
    val dayStartHour: Int = 4,
    val weekStartDay: String = "MONDAY"
)

/**
 * AI autonomous loop limits configuration
 * Separate limits for CHAT vs AUTOMATION sessions
 */
data class AILimitsConfig(
    // ===== CHAT LIMITS =====
    val chatMaxDataQueryIterations: Int = 3,
    val chatMaxActionRetries: Int = 3,
    val chatMaxAutonomousRoundtrips: Int = 10,
    val chatMaxCommunicationModulesRoundtrips: Int = 5,

    // ===== AUTOMATION LIMITS =====
    val automationMaxDataQueryIterations: Int = 5,
    val automationMaxActionRetries: Int = 5,
    val automationMaxAutonomousRoundtrips: Int = 20,
    val automationMaxCommunicationModulesRoundtrips: Int = 10
)
