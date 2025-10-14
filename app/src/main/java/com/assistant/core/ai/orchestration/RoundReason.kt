package com.assistant.core.ai.orchestration

/**
 * Reason for AI round execution
 * Used to track different triggers for AI processing rounds
 */
enum class RoundReason {
    USER_MESSAGE,                // Round normal après message user
    FORMAT_ERROR_CORRECTION,     // Correction après erreur format parsing
    LIMIT_NOTIFICATION,          // Notification limite atteinte
    DATA_RESPONSE,               // Retour données suite requête IA
    MANUAL_TRIGGER,              // Déclenchement manuel/debug
    AUTOMATION_START,            // Démarrage session automation (nouvelle session)
    AUTOMATION_RESUME_CRASH,     // Reprise après crash (endReason=null)
    AUTOMATION_RESUME_NETWORK,   // Reprise après timeout réseau (endReason=NETWORK_ERROR)
    AUTOMATION_RESUME_SUSPENDED  // Reprise après pause user (endReason=SUSPENDED)
}
