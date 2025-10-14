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
 * Reason why a session ended (for audit, debugging, and scheduler logic)
 *
 * Sessions to resume: null (crash), NETWORK_ERROR, SUSPENDED
 * Sessions completed: COMPLETED, CANCELLED, TIMEOUT, ERROR
 */
enum class SessionEndReason {
    COMPLETED,       // AI indicated completion with completed flag
    TIMEOUT,         // Watchdog timeout (inactivity without network issue)
    ERROR,           // Fatal technical error
    CANCELLED,       // User clicked stop (or CHAT evicted by AUTOMATION)
    INTERRUPTED,     // Legacy/alias for null (crash detected as orphan)
    NETWORK_ERROR,   // Timeout with network flag active (to resume)
    SUSPENDED        // User clicked pause (to resume later)
}

/**
 * Trigger type for automation executions
 * Used to distinguish manual vs scheduled vs event-triggered automations
 */
enum class ExecutionTrigger {
    SCHEDULED,  // Created by AutomationScheduler (never goes through queue)
    MANUAL,     // User clicked execute (goes to queue if slot occupied)
    EVENT       // Triggered by event (future use)
}

enum class EnrichmentType {
    POINTER,     // 🔍 Pointer/Référencer - read-only data references
    USE,         // 📝 Utiliser - actions on existing tool data
    CREATE,      // ✨ Créer - new elements (tools, zones)
    MODIFY_CONFIG // 🔧 Modifier Config - tool configuration changes
}