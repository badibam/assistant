package com.assistant.core.ai.enrichments

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.EnrichmentType
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Processor responsible for handling enrichment blocks in two forms:
 * 1. Preview text generation for user interface display
 * 2. DataCommand generation for AI prompt Level 4 inclusion
 *
 * Core logic:
 * - All enrichments generate textual summaries for AI orientation
 * - Only specific types generate DataCommands for Level 4 prompt inclusion:
 * * POINTER: Always generates query
 * * USE: Query for tool instance config
 * * MODIFY_CONFIG: Query for tool instance config
 * * CREATE: No query (just orientation)
 * * ORGANIZE: No query (just orientation)
 */
class EnrichmentProcessor(
    private val context: Context,
    private val coordinator: com.assistant.core.coordinator.Coordinator? = null // Optional for schema ID resolution
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
                EnrichmentType.USE -> generateUseSummary(configJson)
                EnrichmentType.CREATE -> generateCreateSummary(configJson)
                EnrichmentType.MODIFY_CONFIG -> generateModifyConfigSummary(configJson)
            }

            LogManager.aiEnrichment("Generated summary for $type: '$summary'", "DEBUG")
            summary
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to generate enrichment summary: ${e.message}", "ERROR", e)
            s.shared("ai_enrichment_invalid")
        }
    }

    /**
     * Check if enrichment should generate a DataCommand for Level 4 inclusion
     */
    fun shouldGenerateQuery(type: EnrichmentType, config: String): Boolean {
        LogManager.aiEnrichment("EnrichmentProcessor.shouldGenerateQuery() called with type=$type", "DEBUG")

        return try {
            val shouldGenerate = when (type) {
                EnrichmentType.POINTER, EnrichmentType.USE, EnrichmentType.MODIFY_CONFIG -> {
                    LogManager.aiEnrichment("$type enrichment always generates query", "DEBUG")
                    true
                }
                EnrichmentType.CREATE -> {
                    LogManager.aiEnrichment("CREATE enrichment never generates query", "DEBUG")
                    false
                }
            }

            LogManager.aiEnrichment("shouldGenerateQuery($type) = $shouldGenerate", "DEBUG")
            shouldGenerate
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to check query generation: ${e.message}", "ERROR", e)
            false
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

        if (!shouldGenerateQuery(type, config)) {
            LogManager.aiEnrichment("Skipping query generation for $type (shouldGenerateQuery = false)", "DEBUG")
            return emptyList()
        }

        return try {
            val configJson = JSONObject(config)

            val queries = when (type) {
                EnrichmentType.POINTER -> generatePointerQueries(configJson, isRelative)
                EnrichmentType.USE -> generateUseQueries(configJson, isRelative)
                EnrichmentType.CREATE -> generateCreateQueries(configJson, isRelative)
                EnrichmentType.MODIFY_CONFIG -> generateModifyConfigQueries(configJson, isRelative)
                else -> {
                    LogManager.aiEnrichment("No query generator for type $type", "WARN")
                    emptyList()
                }
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
    // Summary Generation
    // ========================================================================================

    private fun generateUseSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("tool_instance_id", "")
        val operation = config.optString("operation", "modifier")
        // TODO: resolve tool instance name from ID for better readability
        return "$operation entrées $toolInstanceId"
    }

    private fun generateCreateSummary(config: JSONObject): String {
        val toolType = config.optString("tooltype", "outil")
        val zoneName = config.optString("zone_name", "")
        val suggestedName = config.optString("suggested_name", "")

        val name = if (suggestedName.isNotEmpty()) suggestedName else toolType
        val zone = if (zoneName.isNotEmpty()) " ${s.shared("ai_enrichment_zone_prefix")} $zoneName" else ""

        return "créer $name$zone"
    }

    private fun generateModifyConfigSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("tool_instance_id", "")
        val aspect = config.optString("aspect", "configuration")
        // TODO: resolve tool instance name from ID for better readability
        return "modifier $aspect de $toolInstanceId"
    }

    // TODO: Implement ORGANIZE enrichment type () - lower priority
    // private fun generateOrganizeSummary(config: JSONObject): String {
    // val action = config.optString("action", "organiser")
    // val elementId = config.optString("element_id", "")
    // return "$action $elementId"
    // }

    // TODO: Implement DOCUMENT enrichment type () - lower priority
    // private fun generateDocumentSummary(config: JSONObject): String {
    // val elementType = config.optString("element_type", "element")
    // val docType = config.optString("doc_type", "documentation")
    // return "$docType $elementType"
    // }

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
     * The entries are read with the pointer's filters and fields, as tool_data.get takes them;
     * a relative period or "NOW" among the values is resolved by the command transformer, at
     * each send, so an automation's pointer reads its own period at every run.
     */
    private suspend fun generatePointerQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        val pointer = PointerConfig.fromJson(config)
        val id = pointer.target.id
        LogManager.aiEnrichment("POINTER: target=${pointer.target}, config=${pointer.config}, entries=${pointer.entries}, filters=${pointer.filters}", "VERBOSE")

        return when (pointer.target.kind) {
            PointerKind.ZONE -> buildList {
                if (pointer.config) {
                    add(DataCommand(
                        id = buildQueryId("zone_config", mapOf("id" to id!!)),
                        type = "ZONE_CONFIG",
                        params = mapOf("id" to id),
                        isRelative = isRelative
                    ))
                }
                // The entries of every tool of a zone need a read across tools, which does not exist yet
                if (pointer.entries) LogManager.aiEnrichment("POINTER: the entries of a zone cannot be read yet", "WARN")
            }
            PointerKind.TOOL -> buildList {
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
                if (pointer.entries) {
                    val params = mutableMapOf<String, Any>("id" to toolInstanceId)
                    if (pointer.filters.length() > 0) params["filters"] = JsonUtils.toList(pointer.filters)
                    // The id always goes, for the AI to act on an entry it is shown
                    pointer.fields?.let { params["fields"] = (listOf("id") + it).distinct() }
                    add(DataCommand(
                        id = buildQueryId("tool_data", params),
                        type = "TOOL_DATA",
                        params = params,
                        isRelative = isRelative
                    ))
                    add(entriesSchemaQuery(toolInstanceId, isRelative))
                }
            }
            // No selector designates them yet
            PointerKind.APP, PointerKind.ENTRY -> {
                LogManager.aiEnrichment("POINTER: a ${pointer.target.kind} target cannot be read yet", "WARN")
                emptyList()
            }
        }
    }

    private suspend fun generateUseQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generateUseQueries() called with isRelative=$isRelative", "DEBUG")

        val toolInstanceId = config.optString("tool_instance_id", "")
        if (toolInstanceId.isEmpty()) return emptyList()

        val queries = mutableListOf<DataCommand>()
        val baseParams = mapOf("id" to toolInstanceId)

        // USE enrichment: TOOL_CONFIG + SCHEMA(config) + SCHEMA(data) + TOOL_DATA_SAMPLE + TOOL_STATS
        queries.add(DataCommand(
            id = buildQueryId("tool_config", baseParams),
            type = "TOOL_CONFIG",
            params = baseParams,
            isRelative = isRelative
        ))

        queries.add(configSchemaQuery(resolveTooltype(toolInstanceId), isRelative))
        queries.add(entriesSchemaQuery(toolInstanceId, isRelative))

        queries.add(DataCommand(
            id = buildQueryId("tool_data_sample", baseParams),
            type = "TOOL_DATA_SAMPLE",
            params = baseParams,
            isRelative = isRelative
        ))
        queries.add(DataCommand(
            id = buildQueryId("tool_stats", baseParams),
            type = "TOOL_STATS",
            params = baseParams,
            isRelative = isRelative
        ))

        LogManager.aiEnrichment("Generated ${queries.size} USE queries for toolInstanceId=$toolInstanceId", "DEBUG")
        return queries
    }

    private fun generateCreateQueries(config: JSONObject, isRelative: Boolean): List<DataCommand> {
        LogManager.aiEnrichment("generateCreateQueries() called with isRelative=$isRelative", "DEBUG")

        // TODO: Implement CREATE enrichment with schema-driven tooltype selection
        // - UI provides the tooltype from the tooltype selection dialog
        // - Generate SCHEMA(tooltype)

        LogManager.aiEnrichment("CREATE enrichment - STUB implementation", "DEBUG")
        return emptyList()
    }

    private suspend fun generateModifyConfigQueries(config: JSONObject, isRelative: Boolean): List<DataCommand> {
        LogManager.aiEnrichment("generateModifyConfigQueries() called with isRelative=$isRelative", "DEBUG")

        val toolInstanceId = config.optString("tool_instance_id", "")
        if (toolInstanceId.isEmpty()) return emptyList()

        val queries = mutableListOf<DataCommand>()
        val baseParams = mapOf("id" to toolInstanceId)

        // MODIFY_CONFIG enrichment: SCHEMA(config) + TOOL_CONFIG
        queries.add(configSchemaQuery(resolveTooltype(toolInstanceId), isRelative))

        queries.add(DataCommand(
            id = buildQueryId("tool_config", baseParams),
            type = "TOOL_CONFIG",
            params = baseParams,
            isRelative = isRelative
        ))

        LogManager.aiEnrichment("Generated ${queries.size} MODIFY_CONFIG queries for toolInstanceId=$toolInstanceId", "DEBUG")
        return queries
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