package com.assistant.core.ai.enrichments

import com.assistant.core.ai.data.DataQuery
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Service responsible for generating summaries and conditional DataQueries from enrichment configurations
 *
 * Core logic:
 * - All enrichments generate textual summaries for AI orientation
 * - Only specific types generate DataQueries for Level 4 prompt inclusion:
 *   * 🔍 POINTER: Query if importance != 'optionnelle'
 *   * 📝 USE: Query for tool instance config
 *   * 🔧 MODIFY_CONFIG: Query for tool instance config
 *   * ✨ CREATE: No query (just orientation)
 *   * 📁 ORGANIZE: No query (just orientation)
 */
class EnrichmentSummarizer {

    /**
     * Generate human-readable summary for enrichment display in messages
     */
    fun generateSummary(type: EnrichmentType, config: String): String {
        return try {
            val configJson = JSONObject(config)

            when (type) {
                EnrichmentType.POINTER -> generatePointerSummary(configJson)
                EnrichmentType.USE -> generateUseSummary(configJson)
                EnrichmentType.CREATE -> generateCreateSummary(configJson)
                EnrichmentType.MODIFY_CONFIG -> generateModifyConfigSummary(configJson)
                EnrichmentType.ORGANIZE -> generateOrganizeSummary(configJson)
                EnrichmentType.DOCUMENT -> generateDocumentSummary(configJson)
            }
        } catch (e: Exception) {
            LogManager.coordination("Failed to generate enrichment summary: ${e.message}", "ERROR", e)
            "enrichissement invalide"
        }
    }

    /**
     * Check if enrichment should generate a DataQuery for Level 4 inclusion
     */
    fun shouldGenerateQuery(type: EnrichmentType, config: String): Boolean {
        return try {
            val configJson = JSONObject(config)

            when (type) {
                EnrichmentType.POINTER -> {
                    val importance = configJson.optString("importance", "important")
                    importance != "optionnelle"
                }
                EnrichmentType.USE, EnrichmentType.MODIFY_CONFIG -> true
                EnrichmentType.CREATE, EnrichmentType.ORGANIZE, EnrichmentType.DOCUMENT -> false
            }
        } catch (e: Exception) {
            LogManager.coordination("Failed to check query generation: ${e.message}", "ERROR", e)
            false
        }
    }

    /**
     * Generate DataQuery for prompt Level 4 inclusion
     */
    fun generateQuery(type: EnrichmentType, config: String, isRelative: Boolean = false): DataQuery? {
        if (!shouldGenerateQuery(type, config)) return null

        return try {
            val configJson = JSONObject(config)

            when (type) {
                EnrichmentType.POINTER -> generatePointerQuery(configJson, isRelative)
                EnrichmentType.USE -> generateUseQuery(configJson, isRelative)
                EnrichmentType.MODIFY_CONFIG -> generateModifyConfigQuery(configJson, isRelative)
                else -> null
            }
        } catch (e: Exception) {
            LogManager.coordination("Failed to generate enrichment query: ${e.message}", "ERROR", e)
            null
        }
    }

    // ========================================================================================
    // Summary Generation
    // ========================================================================================

    private fun generatePointerSummary(config: JSONObject): String {
        val path = config.optString("selectedPath", "")
        val selectionLevel = config.optString("selectionLevel", "")
        val fieldSpecificData = config.optJSONObject("fieldSpecificData")

        // Extract context from path
        val pathParts = path.split(".")
        val zoneContext = if (pathParts.size > 1) pathParts[1] else ""
        val toolContext = if (pathParts.size > 2) pathParts[2] else ""

        // Build base description
        val baseDescription = when (selectionLevel) {
            "ZONE" -> "données zone $zoneContext"
            "INSTANCE" -> "données $toolContext zone $zoneContext"
            "FIELD" -> "champ $toolContext zone $zoneContext"
            else -> "données"
        }

        // Add temporal context if available
        return if (fieldSpecificData?.has("timestampData") == true) {
            val timestampData = fieldSpecificData.getJSONObject("timestampData")
            val periodDescription = formatPeriodDescription(timestampData)
            "$baseDescription $periodDescription"
        } else {
            baseDescription
        }
    }

    private fun generateUseSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("toolInstanceId", "")
        val operation = config.optString("operation", "modifier")
        // TODO: resolve tool instance name from ID for better readability
        return "$operation entrées $toolInstanceId"
    }

    private fun generateCreateSummary(config: JSONObject): String {
        val toolType = config.optString("toolType", "outil")
        val zoneName = config.optString("zoneName", "")
        val suggestedName = config.optString("suggestedName", "")

        val name = if (suggestedName.isNotEmpty()) suggestedName else toolType
        val zone = if (zoneName.isNotEmpty()) " zone $zoneName" else ""

        return "créer $name$zone"
    }

    private fun generateModifyConfigSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("toolInstanceId", "")
        val aspect = config.optString("aspect", "configuration")
        // TODO: resolve tool instance name from ID for better readability
        return "modifier $aspect de $toolInstanceId"
    }

    private fun generateOrganizeSummary(config: JSONObject): String {
        val action = config.optString("action", "organiser")
        val elementId = config.optString("elementId", "")
        // TODO: resolve element name from ID for better readability
        return "$action $elementId"
    }

    private fun generateDocumentSummary(config: JSONObject): String {
        val elementType = config.optString("elementType", "élément")
        val docType = config.optString("docType", "documentation")
        return "$docType $elementType"
    }

    // ========================================================================================
    // Query Generation
    // ========================================================================================

    private fun generatePointerQuery(config: JSONObject, isRelative: Boolean): DataQuery {
        val path = config.optString("selectedPath", "")
        val selectionLevel = config.optString("selectionLevel", "")
        val fieldSpecificData = config.optJSONObject("fieldSpecificData")

        // Build query parameters based on selection level and data
        val params = mutableMapOf<String, Any>()

        // Extract zone and tool info from path
        val pathParts = path.split(".")
        if (pathParts.size > 1) params["zone"] = pathParts[1]
        if (pathParts.size > 2) params["toolInstanceId"] = pathParts[2]
        if (pathParts.size > 3) params["field"] = pathParts[3]

        // Add temporal parameters if present
        fieldSpecificData?.optJSONObject("timestampData")?.let { timestampData ->
            if (isRelative) {
                // TODO: Implement relative period encoding for automation
                params["period"] = "current_selection"
            } else {
                // Use absolute timestamps for chat consistency
                val startTs = timestampData.optLong("startTimestamp", 0)
                val endTs = timestampData.optLong("endTimestamp", 0)
                if (startTs > 0) params["startTimestamp"] = startTs
                if (endTs > 0) params["endTimestamp"] = endTs
            }
        }

        // Generate unique query ID
        val queryId = buildQueryId("pointer_data", params)

        return DataQuery(
            id = queryId,
            type = "TOOL_DATA",
            params = params,
            isRelative = isRelative
        )
    }

    private fun generateUseQuery(config: JSONObject, isRelative: Boolean): DataQuery {
        val toolInstanceId = config.optString("toolInstanceId", "")

        val params = mapOf(
            "toolInstanceId" to toolInstanceId,
            "includeConfig" to true
        )

        val queryId = buildQueryId("use_tool", params)

        return DataQuery(
            id = queryId,
            type = "TOOL_CONFIG",
            params = params,
            isRelative = isRelative
        )
    }

    private fun generateModifyConfigQuery(config: JSONObject, isRelative: Boolean): DataQuery {
        val toolInstanceId = config.optString("toolInstanceId", "")

        val params = mapOf(
            "toolInstanceId" to toolInstanceId,
            "includeSchema" to true
        )

        val queryId = buildQueryId("modify_config", params)

        return DataQuery(
            id = queryId,
            type = "TOOL_CONFIG",
            params = params,
            isRelative = isRelative
        )
    }

    // ========================================================================================
    // Utility Methods
    // ========================================================================================

    private fun formatPeriodDescription(timestampData: JSONObject): String {
        val startTs = timestampData.optLong("startTimestamp", 0)
        val endTs = timestampData.optLong("endTimestamp", 0)

        if (startTs == 0L && endTs == 0L) return ""

        // TODO: Implement proper period formatting based on timestamps
        // For now, return basic description
        return if (startTs == endTs) {
            "période spécifique"
        } else {
            "période étendue"
        }
    }

    private fun buildQueryId(prefix: String, params: Map<String, Any>): String {
        val sortedParams = params.toSortedMap()
        val paramString = sortedParams.map { "${it.key}_${it.value}" }.joinToString(".")
        return "$prefix.$paramString"
    }
}