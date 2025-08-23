package com.assistant.core.services

import com.assistant.core.coordinator.CancellationToken
import org.json.JSONObject

/**
 * Standard interface for all discoverable services
 * Ensures consistent execute method signature across all tool services
 */
interface ExecutableService {
    suspend fun execute(
        operation: String, 
        params: JSONObject, 
        token: CancellationToken
    ): OperationResult
}