package com.assistant.core.ai.orchestration

/**
 * Reason for AI round execution
 * Used to track different triggers for AI processing rounds
 */
enum class RoundReason {
    USER_MESSAGE,              // Round normal après message user
    FORMAT_ERROR_CORRECTION,   // Correction après erreur format parsing
    LIMIT_NOTIFICATION,        // Notification limite atteinte
    DATA_RESPONSE,             // Retour données suite requête IA
    MANUAL_TRIGGER             // Déclenchement manuel/debug
}
