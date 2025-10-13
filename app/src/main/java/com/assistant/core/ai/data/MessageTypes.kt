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
    CHAT,       // Interactive conversation with user
    AUTOMATION, // Automated execution session
    SEED        // Template session for automation (not executed)
}

/**
 * Session state for tracking execution progress and distinguishing
 * real inactivity from network issues
 */
enum class SessionState {
    IDLE,                   // Waiting / inactive
    PROCESSING,             // Currently processing
    WAITING_NETWORK,        // Blocked on network availability
    WAITING_USER_RESPONSE,  // Waiting for communication module response
    WAITING_VALIDATION      // Waiting for user validation
}

/**
 * Reason why a session ended (for audit and debugging)
 */
enum class SessionEndReason {
    COMPLETED,              // AI indicated completion with completed flag
    LIMIT_REACHED,          // Autonomous loop limits exceeded
    INACTIVITY_TIMEOUT,     // Real inactivity timeout (> 10 min for AUTOMATION)
    CHAT_EVICTION,          // Evicted by CHAT request (AUTOMATION only)
    DISMISSED,              // Cancelled by dismiss parameter (older instance skipped)
    USER_CANCELLED          // Manually cancelled by user
}

enum class EnrichmentType {
    POINTER,     // 🔍 Pointer/Référencer - read-only data references
    USE,         // 📝 Utiliser - actions on existing tool data
    CREATE,      // ✨ Créer - new elements (tools, zones)
    MODIFY_CONFIG // 🔧 Modifier Config - tool configuration changes
}