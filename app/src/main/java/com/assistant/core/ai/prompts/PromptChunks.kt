package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.SessionType
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Orchestrates Level 1 prompt construction using modular chunks
 * Each chunk is stored in ai_prompt_chunks.xml with a specific importance degree
 *
 * Importance degrees:
 * - Degree 1 (ESSENTIAL): Minimum required for AI functionality (~6-8k tokens)
 * - Degree 2 (IMPORTANT): Significantly improves quality and robustness (~10-12k tokens)
 * - Degree 3 (OPTIMIZATION): Refines behavior, detailed examples (~16-20k tokens)
 */
object PromptChunks {

    /**
     * Configuration for which chunks to include based on importance degree
     */
    data class ChunkConfig(
        val includeDegree1: Boolean = true,   // ESSENTIAL (always true)
        val includeDegree2: Boolean = true,   // IMPORTANT
        val includeDegree3: Boolean = false   // OPTIMIZATION (disabled by default for token economy)
    )

    /**
     * Chunk metadata
     */
    private data class Chunk(
        val id: String,
        val degree: Int,
        val builder: suspend (Context, SessionType) -> String
    )

    /**
     * All chunks organized by degree and order
     */
    private val ALL_CHUNKS = listOf(
        // PARTIE A : INTRODUCTION & CONFIGURATION
        Chunk("intro_role", 1) { ctx, _ -> buildIntroRole(ctx) },
        Chunk("session_types", 1) { ctx, sessionType -> buildSessionTypes(ctx, sessionType) },
        Chunk("config_limits", 2) { ctx, sessionType -> buildConfigLimits(ctx, sessionType) },
        Chunk("automation_completion", 1) { ctx, sessionType -> buildAutomationCompletion(ctx, sessionType) },

        // PARTIE B : FORMAT DE RÉPONSE IA
        Chunk("response_structure", 1) { ctx, _ -> buildChunk("response_structure", ctx) },
        Chunk("response_rules", 1) { ctx, _ -> buildChunk("response_rules", ctx) },
        Chunk("response_validation_request", 1) { ctx, _ -> buildChunk("response_validation_request", ctx) },
        Chunk("response_communication_modules_intro", 1) { ctx, _ -> buildChunk("response_communication_modules_intro", ctx) },
        Chunk("response_examples", 3) { ctx, _ -> buildChunk("response_examples", ctx) },

        // PARTIE C : COMMANDES DISPONIBLES
        Chunk("commands_queries_signatures", 1) { ctx, _ -> buildChunk("commands_queries_signatures", ctx) },
        Chunk("commands_actions_signatures", 1) { ctx, _ -> buildChunk("commands_actions_signatures", ctx) },
        Chunk("commands_response_format", 2) { ctx, _ -> buildChunk("commands_response_format", ctx) },
        Chunk("commands_queries_examples", 3) { ctx, _ -> buildChunk("commands_queries_examples", ctx) },
        Chunk("commands_actions_examples", 3) { ctx, _ -> buildChunk("commands_actions_examples", ctx) },

        // PARTIE D : SYSTÈME DE VALIDATION
        Chunk("validation_schema_principle", 1) { ctx, _ -> buildChunk("validation_schema_principle", ctx) },
        Chunk("validation_schema_relation", 2) { ctx, _ -> buildChunk("validation_schema_relation", ctx) },
        Chunk("validation_strategy", 1) { ctx, _ -> buildChunk("validation_strategy", ctx) },

        // PARTIE E : GESTION TEMPORELLE
        Chunk("temporal_formats", 1) { ctx, _ -> buildChunk("temporal_formats", ctx) },
        Chunk("temporal_examples", 2) { ctx, _ -> buildChunk("temporal_examples", ctx) },

        // PARTIE F : GESTION DES ERREURS
        Chunk("errors_types", 3) { ctx, _ -> buildChunk("errors_types", ctx) },
        Chunk("errors_handling", 3) { ctx, _ -> buildChunk("errors_handling", ctx) },

        // PARTIE H : BEST PRACTICES
        Chunk("best_practices_strategy", 2) { ctx, _ -> buildChunk("best_practices_strategy", ctx) },
        Chunk("best_practices_validation", 2) { ctx, _ -> buildChunk("best_practices_validation", ctx) },

        // PARTIE I : EXEMPLES DE FLOWS
        Chunk("flow_example_simple", 3) { ctx, _ -> buildChunk("flow_example_simple", ctx) },
        Chunk("flow_example_complex", 3) { ctx, _ -> buildChunk("flow_example_complex", ctx) },
        Chunk("flow_example_errors", 3) { ctx, _ -> buildChunk("flow_example_errors", ctx) },

        // PARTIE J : TOOL TYPES (dynamic content)
        Chunk("tooltypes_list", 1) { ctx, _ -> buildTooltypesList(ctx) }
    )

    /**
     * Build complete Level 1 static documentation with enabled chunks
     * @param context Android context
     * @param sessionType Session type for dynamic content (limits)
     * @param config Chunk configuration (which degrees to include)
     * @return Complete L1 content as markdown string
     */
    suspend fun buildLevel1StaticDoc(
        context: Context,
        sessionType: SessionType,
        config: ChunkConfig = ChunkConfig()
    ): String {
        val sb = StringBuilder()

        // Filter chunks based on configuration
        val enabledChunks = ALL_CHUNKS.filter { chunk ->
            when (chunk.degree) {
                1 -> config.includeDegree1
                2 -> config.includeDegree2
                3 -> config.includeDegree3
                else -> false
            }
        }

        // Build each enabled chunk
        for (chunk in enabledChunks) {
            val content = chunk.builder(context, sessionType)
            if (content.isNotEmpty()) {
                sb.appendLine(content)
                sb.appendLine()
            }
        }

        return sb.toString()
    }


    // ================================================================
    // PLACEHOLDER SYSTEM
    // ================================================================

    /**
     * Replace placeholders in chunk content
     * Supported placeholders:
     * - {{SCHEMA:schema_id}} : Inserts formatted JSON schema
     *
     * @param content Chunk content with placeholders
     * @param context Android context
     * @return Content with placeholders replaced
     */
    private suspend fun replacePlaceholders(content: String, context: Context): String {
        val placeholderRegex = """\{\{SCHEMA:([^}]+)\}\}""".toRegex()
        var result = content

        placeholderRegex.findAll(content).forEach { match ->
            val schemaId = match.groupValues[1]
            LogManager.aiPrompt("Replacing placeholder for schema: $schemaId", "DEBUG")

            val schemaContent = fetchSchemaContent(schemaId, context)
            result = result.replace(match.value, schemaContent)
        }

        return result
    }

    /**
     * Fetch and format schema content for inclusion in prompt
     * @param schemaId Schema ID to fetch
     * @param context Android context
     * @return Formatted JSON schema as markdown code block
     */
    private suspend fun fetchSchemaContent(schemaId: String, context: Context): String {
        val coordinator = Coordinator(context)

        val result = coordinator.processUserAction("schemas.get", mapOf("id" to schemaId))

        if (!result.isSuccess) {
            LogManager.aiPrompt("Failed to fetch schema $schemaId: ${result.error}", "ERROR")
            return "```\nError: Schema $schemaId not found\n```"
        }

        // SchemaService returns schema_id and content directly (no wrapper)
        val schemaContent = result.data?.get("content") as? String

        if (schemaContent == null) {
            LogManager.aiPrompt("Schema $schemaId has no content", "ERROR")
            return "```\nError: Schema $schemaId has no content\n```"
        }

        // Format JSON for readability
        return try {
            val jsonObj = JSONObject(schemaContent)
            val formatted = jsonObj.toString(2) // Indent with 2 spaces
            "```json\n$formatted\n```"
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to format schema $schemaId: ${e.message}", "ERROR")
            "```json\n$schemaContent\n```"
        }
    }

    /**
     * Generic chunk builder with placeholder support
     * @param chunkName Name of the chunk in strings (without ai_chunk_ prefix)
     * @param context Android context
     * @return Chunk content with placeholders replaced
     */
    private suspend fun buildChunk(chunkName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        val content = s.shared("ai_chunk_$chunkName")
        return replacePlaceholders(content, context)
    }

    // ================================================================
    // CUSTOM CHUNK BUILDERS (with special logic)
    // ================================================================

    /**
     * Build intro role chunk
     */
    private suspend fun buildIntroRole(context: Context): String =
        buildChunk("intro_role", context)

    /**
     * Build session types explanation chunk
     * Explains the distinction between CHAT and AUTOMATION sessions
     */
    private fun buildSessionTypes(context: Context, sessionType: SessionType): String {
        val currentMode = when (sessionType) {
            SessionType.CHAT -> "CHAT"
            SessionType.AUTOMATION -> "AUTOMATION"
            SessionType.SEED -> throw IllegalStateException("Cannot build session types for SEED session type")
        }

        return """
## Type de session actuelle : $currentMode

**CHAT** : Conversation interactive avec l'utilisateur.
- Communication modules et Validation Request autorisés
- Pas de flag `completed` (l'utilisateur contrôle la fin)

**AUTOMATION** : Exécution autonome programmée.
- Communication modules et Validation Request **interdits** (exécution autonome)
- **DOIT** utiliser `"completed": true` pour terminer
- `preText` reste obligatoire, `postText` optionnel
""".trimIndent()
    }

    /**
     * Build config limits chunk with dynamic values based on SessionType
     */
    private fun buildConfigLimits(context: Context, sessionType: SessionType): String {
        val s = Strings.`for`(context = context)
        val aiLimits = AppConfigManager.getAILimits()

        val (mode, roundtrips) = when (sessionType) {
            SessionType.CHAT -> listOf(
                "CHAT",
                aiLimits.chatMaxAutonomousRoundtrips
            )
            SessionType.AUTOMATION -> listOf(
                "AUTOMATION",
                aiLimits.automationMaxAutonomousRoundtrips
            )
            SessionType.SEED -> {
                // SEED sessions are never executed, should never generate prompts
                throw IllegalStateException("Cannot build config limits for SEED session type")
            }
        }

        return String.format(
            s.shared("ai_chunk_config_limits"),
            mode, roundtrips
        )
    }

    /**
     * Build tooltypes list with dynamic content from ToolTypeManager
     */
    private fun buildTooltypesList(context: Context): String {
        val s = Strings.`for`(context = context)
        val sb = StringBuilder()

        sb.appendLine(s.shared("ai_chunk_tooltypes_header"))
        sb.appendLine()

        val allToolTypes = ToolTypeManager.getAllToolTypes()
        for ((name, toolType) in allToolTypes) {
            val description = toolType.getDescription(context)
            val schemaIds = ToolTypeManager.getSchemaIdsForTooltype(name)

            sb.appendLine("### $name")
            sb.appendLine("- **Description** : $description")
            sb.appendLine("- **Schémas disponibles** : `${schemaIds.joinToString("`, `")}`")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Build automation completion instructions (AUTOMATION sessions only)
     * Explains how to signal work completion using the completed flag
     * CRITICAL: Must be very explicit about when to use completed flag
     */
    private fun buildAutomationCompletion(context: Context, sessionType: SessionType): String {
        // Only include for AUTOMATION sessions
        if (sessionType != SessionType.AUTOMATION) {
            return ""
        }

        return """
## AUTOMATION : Signaler la fin du travail

Le flag `"completed": true` indique que TOUTE l'automation est terminée.

**❌ NE PAS utiliser `"completed": true` après chaque étape intermédiaire**
**✅ UTILISER `"completed": true` UNIQUEMENT quand ton travail complet est terminé**

### Exemples d'usage INCORRECT :
- Après avoir collecté des données → **NON**
- Après avoir créé un outil → **NON**
- Entre deux actions → **NON**

### Exemple d'usage CORRECT :
- Toutes les données collectées ET analysées ET rapport créé → **OUI**

### Gestion des interruptions réseau/erreurs :
Si tu rencontres des problèmes réseau ou erreurs techniques, continue de travailler normalement.
Le système gère automatiquement les interruptions et tu reprendras exactement où tu t'es arrêté.

### Exemple :
```json
{
  "preText": "Analyse terminée. Toutes les tâches ont été accomplies : données collectées, rapport créé et graphiques générés.",
  "completed": true
}
```

Sans ce flag, l'automation continue jusqu'aux limites de boucles.
""".trimIndent()
    }
}
