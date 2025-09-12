package com.assistant.core.coordinator

/**
 * Simple command for the new dispatcher
 * Format: resource.operation (e.g., "zones.create", "tracking.list")
 */
data class DispatchCommand(
    val action: String,                         // "zones.create", "tracking.add_entry" 
    val params: Map<String, Any> = emptyMap(),  // Command parameters
    val source: Source = Source.USER,           // Who initiated this command
    val id: String? = null                      // Optional command ID for tracking
) {
    
    /**
     * Parse action into resource and operation
     * @return Pair<resource, operation> or throws if invalid format
     */
    fun parseAction(): Pair<String, String> {
        val parts = action.split(".", limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid action format: '$action'. Expected: resource.operation")
        }
        return parts[0] to parts[1]
    }
    
    /**
     * Get resource name from action
     */
    fun getResource(): String = parseAction().first
    
    /**
     * Get operation name from action  
     */
    fun getOperation(): String = parseAction().second
}