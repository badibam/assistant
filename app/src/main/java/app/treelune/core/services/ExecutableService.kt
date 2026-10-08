package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.services.OperationResult
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
     * The operations of this service that are long — that write or read the whole of something:
     * the coordinator runs them in the app's one place for a long operation (`LongOperation`),
     * refusing one while another runs.
     */
    val longOperations: Set<String> get() = emptySet()

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
    suspend fun verbalize(
        operation: String,
        params: JSONObject,
        context: Context
    ): String
}