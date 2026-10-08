package app.treelune.core.ai.data

/**
 * AI context used to assemble prompts
 *
 * Holds every piece of contextual information needed:
 * - État de l'application
 * - Permissions
 * - Zone and tool metadata
 */
data class AIContext(
    val activeZone: ZoneInfo? = null,
    val activeTool: ToolInfo? = null,
    val zones: List<ZoneInfo> = emptyList(),
    val globalPermissions: AIPermissions = AIPermissions()
) {

    /**
     * Get a tool instance by ID
     * TODO: implement against the real services
     */
    fun getToolInstance(toolInstanceId: String): ToolInfo? {
        // Placeholder - to be implemented against the real services
        return null
    }
}

/**
 * What is known about a zone
 */
data class ZoneInfo(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Map<String, String> = emptyMap()
)

/**
 * What is known about a tool
 */
data class ToolInfo(
    val id: String,
    val name: String,
    val toolType: String,
    val config: String = "{}"
)

/**
 * Permissions IA globales
 */
data class AIPermissions(
    val createTools: String = "autonomous",
    val deleteData: String = "validation_required",
    val modifyConfig: String = "autonomous",
    val accessData: String = "autonomous"
)

