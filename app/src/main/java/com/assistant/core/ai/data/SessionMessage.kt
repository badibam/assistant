package com.assistant.core.ai.data

/**
 * Unified session message structure for all 5 message types:
 * 1. User Chat Normal: richContent with enrichments
 * 2. User Module Response: textContent simple (response to communication modules)
 * 3. AI Chat Response: aiMessage with preText + actions + postText + modules
 * 4. User Automation Prompt: richContent (modifiable initial prompt)
 * 5. AI Automation Execution: aiMessage + executionMetadata for tracking
 * 6. AI PostText Success: textContent with postText from successful actions (UI only, excluded from prompt)
 */
data class SessionMessage(
    val id: String,
    val timestamp: Long,
    val sender: MessageSender,     // USER, AI, SYSTEM
    val richContent: RichMessage?, // User messages with enrichments
    val textContent: String?,      // Simple messages (module responses, etc.)
    val aiMessage: AIMessage?,     // Parsed AI structure for UI/logic
    val aiMessageJson: String?,    // Original AI JSON for prompt history consistency
    val systemMessage: SystemMessage?, // System messages for AI operation results
    val executionMetadata: ExecutionMetadata? = null, // For automation executions only
    val excludeFromPrompt: Boolean = false // Exclude from prompt generation (e.g., postText success messages)
)

/**
 * Metadata for automation executions
 */
data class ExecutionMetadata(
    val ruleId: String,        // Automation rule identifier
    val triggeredAt: Long,     // Execution timestamp
    val feedback: ExecutionFeedback? = null // User feedback on execution
)

/**
 * User feedback on automation executions
 */
data class ExecutionFeedback(
    val comment: String,       // Short text feedback (255 chars)
    val rating: Int,           // Rating scale 1-5 stars
    val timestamp: Long        // Feedback timestamp
)

/**
 * AI Session structure unifying chat, automation, and seed sessions
 */
data class AISession(
    val id: String,
    val name: String,
    val type: SessionType,         // CHAT, AUTOMATION, SEED
    val requireValidation: Boolean = false,      // Session-level validation toggle (user controlled)
    val waitingStateJson: String? = null,        // Persisted waiting state for app closure (null = no waiting)
    val automationId: String? = null,            // null for CHAT/SEED, automation ID for AUTOMATION
    val scheduledExecutionTime: Long? = null,    // For AUTOMATION: scheduled trigger time (not actual exec time)
    val providerId: String,        // Fixed for the session
    val providerSessionId: String, // Provider API session ID
    val createdAt: Long,
    val lastActivity: Long,
    val messages: List<SessionMessage>,
    val isActive: Boolean,
    val state: SessionState = SessionState.IDLE,        // Execution state for tracking
    val lastNetworkErrorTime: Long? = null,             // Last network error timestamp (for inactivity calculation)
    val endReason: SessionEndReason? = null             // Why session ended (for audit)
)