package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.OperationResult
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

    /**
     * Generates a human-readable description of the action (substantive form)
     *
     * Example: "Création de la zone \"Santé\""
     *
     * Usage:
     * - (a) UI validation: display action to user before execution
     * - (b) SystemMessage feedback: describe executed action in AI context
     *
     * @param operation The operation to verbalize (e.g., "create", "update", "delete")
     * @param params The operation parameters (same as execute())
     * @param context Android context for string resources
     * @return Human-readable description in substantive form
     */
    fun verbalize(
        operation: String,
        params: JSONObject,
        context: Context
    ): String
}