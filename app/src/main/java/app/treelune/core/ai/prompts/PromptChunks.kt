package app.treelune.core.ai.prompts

import android.content.Context
import app.treelune.core.ai.data.AICommand
import app.treelune.core.ai.data.AICommands
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.SessionType
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.runBlocking
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
    ) {
        fun includes(degree: Int): Boolean = when (degree) {
            1 -> includeDegree1
            2 -> includeDegree2
            3 -> includeDegree3
            else -> false
        }
    }

    /**
     * Chunk metadata. A shared chunk says what the app is (its zones, fields, schemas, dates,
     * tool types), true for any AI that works in it; the others say how the built-in AI talks to
     * the app (its JSON envelope, its session, its examples written in that envelope).
     */
    private data class Chunk(
        val id: String,
        val degree: Int,
        val shared: Boolean = false,
        val builder: suspend (Context, SessionType) -> String
    )

    /**
     * All chunks organized by degree and order
     */
    private val ALL_CHUNKS = listOf(
        // PARTIE A : INTRODUCTION & CONFIGURATION
        Chunk("intro_role", 1) { ctx, _ -> buildIntroRole(ctx) },
        Chunk("environment", 1, shared = true) { ctx, _ -> buildChunk("environment", ctx) },
        Chunk("session_types", 1) { ctx, sessionType -> buildSessionTypes(ctx, sessionType) },
        Chunk("automation_completion", 1) { ctx, sessionType -> buildAutomationCompletion(ctx, sessionType) },

        // PARTIE B : FORMAT DE RÉPONSE IA
        Chunk("response_structure", 1) { ctx, _ -> buildChunk("response_structure", ctx) },
        Chunk("response_rules", 1) { ctx, _ -> buildChunk("response_rules", ctx) },
        Chunk("response_validation_request", 1) { ctx, _ -> buildChunk("response_validation_request", ctx) },
        Chunk("response_communication_modules_intro", 1) { ctx, _ -> buildChunk("response_communication_modules_intro", ctx) },
        Chunk("response_examples", 3) { ctx, _ -> buildChunk("response_examples", ctx) },

        // PARTIE C : COMMANDES DISPONIBLES
        Chunk("commands_queries", 1) { ctx, _ -> buildCommandsChunk("commands_queries_intro", AICommands.queries, ctx) },
        Chunk("commands_actions", 1) { ctx, _ -> buildCommandsChunk("commands_actions_intro", AICommands.actions, ctx) },
        Chunk("commands_response_format", 2) { ctx, _ -> buildChunk("commands_response_format", ctx) },
        Chunk("extra", 1, shared = true) { ctx, _ -> buildCustomFieldsChunk(ctx) },
        Chunk("commands_queries_examples", 3) { ctx, _ -> buildChunk("commands_queries_examples", ctx) },
        Chunk("commands_actions_examples", 3) { ctx, _ -> buildChunk("commands_actions_examples", ctx) },

        // PARTIE D : SYSTÈME DE VALIDATION
        Chunk("validation_schema_principle", 1, shared = true) { ctx, _ -> buildChunk("validation_schema_principle", ctx) },
        Chunk("validation_schema_ids", 1, shared = true) { ctx, _ -> runBlocking { buildSystemSchemaIds(ctx) } },
        Chunk("validation_strategy", 1, shared = true) { ctx, _ -> buildChunk("validation_strategy", ctx) },
        // The session holds back a command on entries whose schema it has not sent yet
        Chunk("validation_schema_enforced", 1) { ctx, _ -> buildChunk("validation_schema_enforced", ctx) },
        Chunk("validation_system_managed", 1, shared = true) { ctx, _ -> buildChunk("validation_system_managed", ctx) },

        // PART E: TIME HANDLING
        Chunk("temporal_formats", 1, shared = true) { ctx, _ -> buildChunk("temporal_formats", ctx) },
        Chunk("temporal_examples", 2, shared = true) { ctx, _ -> buildChunk("temporal_examples", ctx) },

        // PART F: ERROR HANDLING
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
        Chunk("tooltypes_list", 1, shared = true) { ctx, _ -> buildTooltypesList(ctx) }
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

        val enabledChunks = ALL_CHUNKS.filter { config.includes(it.degree) }

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


    /** A command's own text (AICommand.docKey), its placeholders filled: what an outside AI reads of it as a tool. */
    suspend fun commandText(context: Context, command: AICommand): String =
        replacePlaceholders(Strings.`for`(context = context).shared(command.docKey), context)

    /**
     * What the app is, for an AI that works in it from outside (app_context): the shared chunks
     * at the degrees [config] enables, in the L1's order. The commands are not in it: an outside
     * AI reads each one as it is offered to it.
     */
    suspend fun buildAppNotions(context: Context, config: ChunkConfig = ChunkConfig()): String =
        ALL_CHUNKS.filter { it.shared && config.includes(it.degree) }
            .map { it.builder(context, SessionType.CHAT) }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

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

        // Written in the notation the prompt's legend describes, as every schema the model reads
        return SchemaNotation.render(SchemaModelView.forModel(JSONObject(schemaContent), AppConfigManager.getDateTimeConfig().getZoneId()))
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

    /** A family of commands: what they share, then each one's text in the order AICommands declares them. */
    private suspend fun buildCommandsChunk(introName: String, commands: List<AICommand>, context: Context): String {
        val s = Strings.`for`(context = context)
        val texts = listOf(s.shared("ai_chunk_$introName")) + AICommands.docKeys(commands).map { s.shared(it) }
        return replacePlaceholders(texts.joinToString("\n\n"), context)
    }

    /** The field types, each with the description its schema carries, listed for the prompt. */
    private fun buildCustomFieldTypesList(context: Context): String {
        return app.treelune.core.fields.FieldType.entries.joinToString("\n") { type ->
            val schema = app.treelune.core.fields.settings.FieldTypeSchemas.getSchema("field_type_${type.name}", context, null)
            val description = schema?.description ?: type.name
            "- **${type.name}** : $description"
        }
    }

    /**
     * Build custom fields chunk with dynamic type list
     */
    private suspend fun buildCustomFieldsChunk(context: Context): String {
        val s = Strings.`for`(context = context)
        val content = s.shared("ai_chunk_custom_fields")
        val typesList = buildCustomFieldTypesList(context)
        val definition = SchemaNotation.render(SchemaModelView.forModel(
            JSONObject(app.treelune.core.fields.settings.FieldTypeSchemas.anyDefinition(context)),
            AppConfigManager.getDateTimeConfig().getZoneId()
        ))

        return content.replace("{{CUSTOM_FIELD_TYPES}}", typesList).replace("{{FIELD_DEFINITION}}", "```\n$definition\n```")
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

        return Strings.`for`(context = context).shared("ai_chunk_session_types").format(currentMode)
    }

    /**
     * The names of the schemas the AI asks for by name: neither a tool's config nor its entries,
     * which it asks for by tool type or tool (SCHEMA), nor the app's settings, which no command
     * of the AI reads or writes.
     */
    private suspend fun buildSystemSchemaIds(context: Context): String {
        val result = Coordinator(context).processUserAction("schemas.list", emptyMap())
        if (!result.isSuccess) {
            throw IllegalStateException("Cannot list the schemas for the prompt: ${result.error}")
        }
        @Suppress("UNCHECKED_CAST")
        val allSchemaIds = result.data?.get("schema_ids") as? List<String> ?: emptyList()
        val named = allSchemaIds.filter { schemaId ->
            schemaId == app.treelune.core.schemas.ZoneSettings.SCHEMA_ID ||
                schemaId == app.treelune.core.ai.data.CommunicationModules.SCHEMA_ID ||
                schemaId.startsWith("field_type_")
        }.sorted()
        return Strings.`for`(context = context).shared("ai_chunk_system_schema_ids")
            .format(named.joinToString("\n") { "- `$it`" })
    }

    /** The tool types, each with its description. */
    private fun buildTooltypesList(context: Context): String {
        val s = Strings.`for`(context = context)
        val sb = StringBuilder()

        sb.appendLine(s.shared("ai_chunk_tooltypes_header"))
        sb.appendLine()

        val allToolTypes = ToolTypeManager.getAllToolTypes()
        for ((name, toolType) in allToolTypes) {
            sb.appendLine("### $name")
            sb.appendLine(toolType.getDescription(context))
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

        val s = Strings.`for`(context = context)
        return s.shared("ai_chunk_automation_completion")
    }
}
