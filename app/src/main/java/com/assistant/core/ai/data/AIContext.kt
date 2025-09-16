package com.assistant.core.ai.data

/**
 * Contexte IA pour l'assemblage des prompts
 *
 * Contient toutes les informations contextuelles nécessaires:
 * - État de l'application
 * - Permissions
 * - Métadonnées des zones et outils
 */
data class AIContext(
    val activeZone: ZoneInfo? = null,
    val activeTool: ToolInfo? = null,
    val zones: List<ZoneInfo> = emptyList(),
    val globalPermissions: AIPermissions = AIPermissions()
) {

    /**
     * Récupère une instance d'outil par ID
     * TODO: Implémenter avec vrais services
     */
    fun getToolInstance(toolInstanceId: String): ToolInfo? {
        // Placeholder - sera implémenté avec les vrais services
        return null
    }
}

/**
 * Informations sur une zone
 */
data class ZoneInfo(
    val id: String,
    val name: String,
    val description: String,
    val permissions: Map<String, String> = emptyMap()
)

/**
 * Informations sur un outil
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

/**
 * Résultat d'assemblage de prompt
 */
data class PromptResult(
    val prompt: String,
    val tokensEstimate: Int = 0,
    val schemasIncluded: List<String> = emptyList()
)