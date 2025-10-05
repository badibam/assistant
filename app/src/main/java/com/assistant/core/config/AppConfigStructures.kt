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
    val automationMaxCommunicationModulesRoundtrips: Int = 10,

    // ===== TIMEOUTS INACTIVITÉ (ms) =====
    val chatInactivityTimeout: Long = 5 * 60 * 1000,        // 5 min
    val automationInactivityTimeout: Long = 30 * 60 * 1000, // 30 min

    // ===== AUTOMATION : DURÉE MAX OCCUPATION SESSION (ms) =====
    // CHAT n'a pas de timeout exécution (bouton "Interrompre" dans UI)
    val automationMaxSessionDuration: Long = 10 * 60 * 1000 // 10 min
)
