package com.assistant.core.ai.data

/**
 * Types and enums shared across AI message system
 */

enum class MessageSender {
    USER,
    AI,
    SYSTEM
}

enum class SessionType {
    CHAT,
    AUTOMATION
}

enum class EnrichmentType {
    POINTER,     // 🔍 Pointer/Référencer - read-only data references
    USE,         // 📝 Utiliser - actions on existing tool data
    CREATE,      // ✨ Créer - new elements (tools, zones)
    MODIFY_CONFIG // 🔧 Modifier Config - tool configuration changes
}

enum class ValidationStatus {
    PENDING,
    CONFIRMED,
    REFUSED
}