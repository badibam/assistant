package com.assistant.core.coordinator

/**
 * Source of command execution
 * Identifies who/what initiated the command for proper handling
 */
enum class Source {
    USER,       // Actions UI directes utilisateur
    AI,         // Commandes IA (future)
    SCHEDULER,  // Tâches périodiques planifiées
    SYSTEM      // Opérations système : démarrage, migrations, maintenance
}