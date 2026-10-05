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
    val excludeFromPrompt: Boolean = false, // Exclude from prompt generation (e.g., postText success messages)
    // A user message with images, as PromptManager prepares it for the model: its text and its
    // images in order. Null for any other message, whose text is textContent
    val promptParts: List<PromptPart>? = null,

    // Token usage metrics (for AI messages only, 0 for USER/SYSTEM)
    // Note: API providers return inputTokens as UNCACHED only. Total input = inputTokens + cacheWriteTokens + cacheReadTokens
    val inputTokens: Int = 0,           // Uncached input tokens (from API)
    val cacheWriteTokens: Int = 0,      // Cache write tokens
    val cacheReadTokens: Int = 0,       // Cache read tokens
    val outputTokens: Int = 0,          // Output tokens generated
    val pricing: CallPricing? = null,   // AI messages: the model and prices of their call
    val usageUnknown: Boolean = false   // Marks a call cut or lost after its request went out
)

/**
 * The model that answered an AI call and the prices applied to it, per token, as they were at
 * the time of the call. A null price is unknown: the call's cost is unknown if that category
 * has tokens. The cost itself is not stored: it is tokens times prices.
 */
data class CallPricing(
    val modelId: String,
    val inputPrice: Double?,
    val cacheWritePrice: Double?,
    val cacheReadPrice: Double?,
    val outputPrice: Double?
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

/** A part of what a user message says to the model: some text, or an image where the user put it. */
sealed class PromptPart {
    data class Text(val text: String) : PromptPart()
    data class Image(val imageId: String) : PromptPart()
}
