package com.assistant.core.ai

import android.content.Context
import com.assistant.core.ai.enrichments.Enrichment
import com.assistant.core.ai.data.AIContext
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * PromptManager - Assemblage dynamique des prompts IA
 *
 * Responsabilités:
 * - Assembler base prompt + contexte + enrichissements
 * - Gérer cache de session pour éviter redondances
 * - Substituer les slots dans les templates
 */
class PromptManager(private val context: Context) {

    // Cache de session pour éviter redondances documentation
    private val sessionSchemaCache = mutableSetOf<String>()
    private val configCache = mutableMapOf<String, String>() // id → hash

    /**
     * Assemble le prompt complet pour l'IA
     */
    fun assemblePrompt(
        userMessage: String,
        enrichments: List<Enrichment>,
        aiContext: AIContext
    ): String {
        LogManager.coordination("Assembling prompt with ${enrichments.size} enrichments")

        // 1. Base prompt + contexte (toujours inclus)
        val basePrompt = buildBasePrompt(aiContext)

        // 2. Documentation des enrichissements (si pas déjà envoyée)
        val documentationSection = buildDocumentationSection(enrichments, aiContext)

        // 3. Message utilisateur avec enrichissements inline
        val processedMessage = processUserMessageWithEnrichments(userMessage, enrichments)

        // 4. Assemblage final
        val assembledPrompt = StringBuilder().apply {
            append(basePrompt)
            append("\n\n")
            if (documentationSection.isNotEmpty()) {
                append("=== DOCUMENTATION ===\n")
                append(documentationSection)
                append("\n=== /DOCUMENTATION ===\n\n")
            }
            append("Message utilisateur:\n")
            append(processedMessage)
        }.toString()

        LogManager.coordination("Prompt assembled: ${assembledPrompt.length} characters")
        return assembledPrompt
    }

    /**
     * Reset du cache pour nouvelle session
     */
    fun clearCache() {
        sessionSchemaCache.clear()
        configCache.clear()
        LogManager.coordination("PromptManager cache cleared")
    }

    /**
     * Invalide le cache pour une config spécifique
     */
    fun invalidateConfig(configId: String) {
        configCache.remove(configId)
        LogManager.coordination("Config cache invalidated for: $configId")
    }

    // ═══ Private Methods ═══

    private fun buildBasePrompt(aiContext: AIContext): String {
        val template = getBaseTemplate()

        return template
            .replace("{{CONTEXT}}", buildContextSection(aiContext))
            .replace("{{PERMISSIONS}}", buildPermissionsSection(aiContext))
            .replace("{{METADATA}}", buildMetadataSection(aiContext))
    }

    private fun buildContextSection(aiContext: AIContext): String {
        val context = StringBuilder()

        // Zone active
        aiContext.activeZone?.let { zone ->
            context.append("Zone active: ${zone.name} (${zone.description})\n")
        }

        // Outil en cours
        aiContext.activeTool?.let { tool ->
            context.append("Outil en cours: ${tool.name}\n")
        }

        return context.toString()
    }

    private fun buildPermissionsSection(aiContext: AIContext): String {
        return """
        Permissions globales:
        - Création outils: ${aiContext.globalPermissions.createTools}
        - Suppression données: ${aiContext.globalPermissions.deleteData}
        - Modification config: ${aiContext.globalPermissions.modifyConfig}
        """.trimIndent()
    }

    private fun buildMetadataSection(aiContext: AIContext): String {
        val metadata = StringBuilder()

        metadata.append("Zones existantes:\n")
        aiContext.zones.forEach { zone ->
            metadata.append("- ${zone.name}: ${zone.description}\n")
        }

        metadata.append("\nCommandes disponibles: zones.*, tools.*, tool_data.*\n")
        metadata.append("Navigation API: metadata.get_schemas, zones.list, tools.list\n")

        return metadata.toString()
    }

    private fun buildDocumentationSection(enrichments: List<Enrichment>, aiContext: AIContext): String {
        val documentation = StringBuilder()
        val schemasToInclude = mutableSetOf<String>()

        // 1. Schemas des enrichissements
        enrichments.forEach { enrichment ->
            val schemaKey = enrichment::class.simpleName ?: "Unknown"
            if (!sessionSchemaCache.contains(schemaKey)) {
                schemasToInclude.add(schemaKey)
                documentation.append("## ${schemaKey}\n")
                documentation.append(enrichment.getSchema())
                documentation.append("\n\n")
            }
        }

        // 2. Configs des instances référencées (avec cache par hash)
        enrichments.forEach { enrichment ->
            when (enrichment) {
                is Enrichment.Pointer -> {
                    // Parse selectedPath to extract zone/tool info if needed
                    // For now, we can skip detailed config inclusion for POINTER
                    // since it's mainly for referencing existing data
                }
                // TODO: Add other enrichment types when implemented
                else -> {} // Pas de config spécifique à inclure
            }
        }

        // Mise à jour du cache
        sessionSchemaCache.addAll(schemasToInclude)

        return documentation.toString()
    }

    private fun addZoneConfigToDoc(zone: Any, documentation: StringBuilder, schemasToInclude: MutableSet<String>) {
        val configKey = "Zone_${zone.hashCode()}"
        val currentHash = zone.toString().hashCode().toString() // Simplified

        if (configCache[configKey] != currentHash) {
            documentation.append("## Configuration Zone ${zone}\n")
            documentation.append("Zone config details...\n\n") // TODO: Real zone config
            configCache[configKey] = currentHash
        }
    }

    private fun addToolConfigToDoc(toolInstance: Any, documentation: StringBuilder, schemasToInclude: MutableSet<String>) {
        val configKey = "ToolInstance_${toolInstance.hashCode()}"
        val currentHash = toolInstance.toString().hashCode().toString() // Simplified

        if (configCache[configKey] != currentHash) {
            documentation.append("## Configuration Outil ${toolInstance}\n")
            documentation.append("Tool config details...\n\n") // TODO: Real tool config
            configCache[configKey] = currentHash
        }
    }

    private fun processUserMessageWithEnrichments(userMessage: String, enrichments: List<Enrichment>): String {
        // TODO: Logique de traitement des enrichissements inline
        // Pour l'instant, retourne le message original
        // Enrichments seront traités pour montrer: [TYPE: label | params...]
        return userMessage
    }

    private fun getBaseTemplate(): String {
        return """
        Tu es l'assistant IA de l'application. Tu réponds en JSON structuré selon le format défini.

        {{CONTEXT}}

        {{PERMISSIONS}}

        {{METADATA}}
        """.trimIndent()
    }
}