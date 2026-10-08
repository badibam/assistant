package app.treelune.core.ai.enrichments

import android.content.Context
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.EnrichmentType
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Processor responsible for handling enrichment blocks in two forms:
 * 1. Preview text generation for user interface display
 * 2. DataCommand generation for AI prompt Level 4 inclusion
 *
 * Both types generate DataCommands for Level 4 prompt inclusion:
 * * POINTER: the target's config and/or entries, as the pointer attaches them
 * * FILE: the file read, whole or its first lines
 */
class EnrichmentProcessor(
    private val context: Context,
    private val coordinator: app.treelune.core.coordinator.Coordinator? = null // Optional for schema ID resolution
) {

    private val s = Strings.`for`(context = context)

    /**
     * Generate human-readable summary for enrichment display in messages. A pointer names its
     * target as it is now, which EnrichmentText reads: it has no summary here.
     */
    fun generateSummary(type: EnrichmentType, config: String): String {
        LogManager.aiEnrichment("EnrichmentProcessor.generateSummary() called with type=$type, config length=${config.length}", "DEBUG")

        return try {
            val configJson = JSONObject(config)

            val summary = when (type) {
                EnrichmentType.POINTER -> throw IllegalArgumentException("a pointer's text is EnrichmentText's")
                EnrichmentType.FILE -> FileEnrichment.fromJson(configJson).summary(s)
            }

            LogManager.aiEnrichment("Generated summary for $type: '$summary'", "DEBUG")
            summary
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to generate enrichment summary: ${e.message}", "ERROR", e)
            s.shared("ai_enrichment_invalid")
        }
    }

    /**
     * Generate DataCommands for prompt Level 4 inclusion (suspend version)
     * Returns multiple commands as enrichments can require both config and data
     * Requires coordinator for schema ID resolution
     */
    suspend fun generateCommands(
        type: EnrichmentType,
        config: String,
        isRelative: Boolean = false
    ): List<DataCommand> {
        LogManager.aiEnrichment("EnrichmentProcessor.generateCommands() called with type=$type, isRelative=$isRelative", "DEBUG")

        return try {
            val configJson = JSONObject(config)

            val queries = when (type) {
                EnrichmentType.POINTER -> generatePointerQueries(configJson, isRelative)
                EnrichmentType.FILE -> listOf(FileEnrichment.fromJson(configJson).query(isRelative))
            }

            LogManager.aiEnrichment("Generated ${queries.size} DataCommands for $type", "DEBUG")
            queries.forEach { query ->
                LogManager.aiEnrichment("- id='${query.id}', type='${query.type}', isRelative=${query.isRelative}", "VERBOSE")
            }

            queries
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to generate enrichment queries: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // ========================================================================================
    // Schema ID Resolution
    // ========================================================================================

    /**
     * The tooltype of the tool [toolInstanceId], for the SCHEMA command of its config.
     *
     * @throws IllegalStateException if the tool cannot be read
     */
    private suspend fun resolveTooltype(toolInstanceId: String): String {
        if (coordinator == null) {
            throw IllegalStateException("Cannot resolve the tooltype: coordinator not available")
        }
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) {
            throw IllegalStateException("Failed to fetch tool instance $toolInstanceId: ${result.error}")
        }
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
            ?: throw IllegalStateException("Tool instance $toolInstanceId not found in response")
        return toolInstance["tooltype"] as? String
            ?: throw IllegalStateException("Tool instance $toolInstanceId has no tooltype")
    }

    /** The SCHEMA command of the config of a tool of [tooltype]. */
    private fun configSchemaQuery(tooltype: String, isRelative: Boolean) = DataCommand(
        id = buildQueryId("schema_config", mapOf("tooltype" to tooltype)),
        type = "SCHEMA",
        params = mapOf("tooltype" to tooltype),
        isRelative = isRelative
    )

    /** The SCHEMA command of the entries of the tool [toolInstanceId], its user's fields included. */
    private fun entriesSchemaQuery(toolInstanceId: String, isRelative: Boolean) = DataCommand(
        id = buildQueryId("schema_data", mapOf("tool_instance_id" to toolInstanceId)),
        type = "SCHEMA",
        params = mapOf("tool_instance_id" to toolInstanceId),
        isRelative = isRelative
    )

    // ========================================================================================
    // Query Generation
    // ========================================================================================

    /**
     * The queries a POINTER sends with the message: the target's config and its schema when the
     * config is attached, its entries and their schema when they are. A mention sends none.
     *
     * The entries are read with the selection's period, filters and fields; a relative date
     * among them is resolved by the command transformer, at each send, so an automation's
     * pointer reads its own period at every run.
     */
    private suspend fun generatePointerQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        val pointer = PointerConfig.fromJson(config) { s.shared(it) }
        val id = pointer.target.id
        LogManager.aiEnrichment("POINTER: selection=${pointer.selection.toJson()}, config=${pointer.config}, entries=${pointer.entries}", "VERBOSE")
        pointer.selection.problem { s.shared(it) }?.let { throw IllegalArgumentException("POINTER: $it") }

        return when (pointer.target.kind) {
            ReferenceKind.ZONE -> buildList {
                if (pointer.config) {
                    add(DataCommand(
                        id = buildQueryId("zone_config", mapOf("id" to id!!)),
                        type = "ZONE_CONFIG",
                        params = mapOf("id" to id),
                        isRelative = isRelative
                    ))
                }
                // The entries of a zone: those of each of its tools over the period, one read per
                // tool with its schema, as a tool's pointer sends them
                if (pointer.entries) {
                    for (toolInstanceId in zoneToolIds(id!!)) addAll(entriesQueries(toolInstanceId, pointer.selection, isRelative))
                }
            }
            ReferenceKind.TOOL_INSTANCE -> buildList {
                val toolInstanceId = id!!
                if (pointer.config) {
                    val params = mapOf<String, Any>("id" to toolInstanceId)
                    add(DataCommand(
                        id = buildQueryId("tool_config", params),
                        type = "TOOL_CONFIG",
                        params = params,
                        isRelative = isRelative
                    ))
                    add(configSchemaQuery(resolveTooltype(toolInstanceId), isRelative))
                }
                if (pointer.entries) addAll(entriesQueries(toolInstanceId, pointer.selection, isRelative))
            }
            // No selector designates them yet
            ReferenceKind.APP, ReferenceKind.ENTRY, ReferenceKind.VARIABLE -> {
                LogManager.aiEnrichment("POINTER: a ${pointer.target.kind} target cannot be read yet", "WARN")
                emptyList()
            }
        }
    }

    /**
     * The entries of [toolInstanceId] with the selection's period, filters and fields, then their
     * schema. A zone's selection brings its period alone.
     */
    private fun entriesQueries(toolInstanceId: String, selection: EntrySelection, isRelative: Boolean): List<DataCommand> {
        val params = mutableMapOf<String, Any>("id" to toolInstanceId)
        if (!selection.period.isEmpty) params["period"] = JsonUtils.toMap(selection.period.toJson())
        if (selection.filters.length() > 0) params["filters"] = JsonUtils.toList(selection.filters)
        // The id always goes, for the AI to act on an entry it is shown
        selection.fields?.let { params["fields"] = (listOf("id") + it).distinct() }
        return listOf(
            DataCommand(id = buildQueryId("tool_data", params), type = "TOOL_DATA", params = params, isRelative = isRelative),
            entriesSchemaQuery(toolInstanceId, isRelative)
        )
    }

    /**
     * The tools of the zone [zoneId], in their order.
     *
     * @throws IllegalStateException if they cannot be read
     */
    private suspend fun zoneToolIds(zoneId: String): List<String> {
        val coordinator = coordinator ?: throw IllegalStateException("Cannot read the tools of a zone: coordinator not available")
        val result = coordinator.processUserAction("tools.list", mapOf("zone_id" to zoneId))
        if (!result.isSuccess) throw IllegalStateException("Tools of zone $zoneId not read: ${result.error}")
        return (result.data?.get("tool_instances") as? List<*> ?: emptyList<Any>())
            .filterIsInstance<Map<*, *>>().map { it["id"] as String }
    }

    // ========================================================================================
    // Utility Methods
    // ========================================================================================

    private fun buildQueryId(prefix: String, params: Map<String, Any>): String {
        val sortedParams = params.toSortedMap()
        val paramString = sortedParams.map { "${it.key}_${it.value}" }.joinToString(".")
        return "$prefix.$paramString"
    }
}