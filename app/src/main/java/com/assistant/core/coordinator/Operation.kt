package com.assistant.core.coordinator

import java.util.UUID

/**
 * Represents an operation being executed by the Coordinator
 */
data class Operation(
    val id: String = UUID.randomUUID().toString(),
    val sourceType: String,     // "tool" or "service"
    val sourceId: String,       // tool instance ID or service name
    val operation: String,      // operation name (e.g., "add_entry", "backup")
    val params: Map<String, Any>, // operation parameters
    val initiatedBy: OperationSource,
    val createdAt: Long = System.currentTimeMillis(),
    val priority: OperationPriority = OperationPriority.NORMAL
)

/**
 * Priority levels for operations
 */
enum class OperationPriority {
    LOW,        // Background tasks, can be deferred
    NORMAL,     // Standard operations
    HIGH,       // AI responses, user-triggered actions
    CRITICAL    // System-critical operations, cannot be interrupted
}