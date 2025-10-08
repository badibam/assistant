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
    val chatMaxFormatErrorRetries: Int = 3,
    val chatMaxAutonomousRoundtrips: Int = 10,

    // ===== AUTOMATION LIMITS =====
    val automationMaxDataQueryIterations: Int = 5,
    val automationMaxActionRetries: Int = 5,
    val automationMaxFormatErrorRetries: Int = 5,
    val automationMaxAutonomousRoundtrips: Int = 20,

    // ===== CHAT : DURÉE MAX INACTIVITÉ AVANT ÉVICTION PAR AUTOMATION (ms) =====
    // Si AUTOMATION demande la main et CHAT inactive depuis > cette durée → arrêt forcé CHAT
    // Si CHAT inactive depuis < cette durée → AUTOMATION attend en queue
    val chatMaxInactivityBeforeAutomationEviction: Long = 5 * 60 * 1000, // 5 min

    // ===== AUTOMATION : DURÉE MAX OCCUPATION SESSION (ms) =====
    // Watchdog pour éviter boucles infinies (CHAT n'a pas de timeout - bouton UI)
    val automationMaxSessionDuration: Long = 10 * 60 * 1000 // 10 min
)

/**
 * AI action validation configuration
 * Hierarchy: app > zone > tool > session > AI request (OR logic)
 */
data class ValidationConfig(
    val validateAppConfigChanges: Boolean = false,      // Modif config app
    val validateZoneConfigChanges: Boolean = false,     // Modif config zones
    val validateToolConfigChanges: Boolean = false,     // Modif config outils
    val validateToolDataChanges: Boolean = false        // Modif données outils
)
