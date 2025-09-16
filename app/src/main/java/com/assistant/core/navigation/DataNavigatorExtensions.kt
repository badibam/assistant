package com.assistant.core.navigation

import android.content.Context
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Extensions pour l'intégration du DataNavigator avec l'architecture existante
 */

/**
 * Extension pour ToolTypeContract : récupération du schéma data résolu
 * ARCHITECTURE CORRECTE: Le ToolType gère sa propre logique de résolution
 */
fun ToolTypeContract.getDataSchemaResolved(configJson: String, context: Context): String? {
    // Use the new getResolvedDataSchema method - only ToolType knows its resolution logic
    return getResolvedDataSchema(configJson, context)
}

/**
 * Helper pour parser JSON vers Map
 */
private fun parseJsonToMap(jsonString: String): Map<String, Any> {
    return try {
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            map[key] = json.get(key)
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Extension pour faciliter la navigation depuis l'UI
 */
fun DataNavigator.getPathDisplayName(path: String): String {
    return when {
        path.startsWith("zones.") -> {
            val zoneName = path.substringAfter("zones.")
            "Zone: $zoneName"
        }
        path.startsWith("tools.") -> {
            val toolName = path.substringAfter("tools.").substringBefore(".")
            "Outil: $toolName"
        }
        path.contains(".") -> {
            val fieldName = path.substringAfterLast(".")
            "Champ: $fieldName"
        }
        else -> path
    }
}

/**
 * Extension pour construire des critères de filtrage basés sur le contexte
 */
suspend fun DataNavigator.buildFilterCriteria(
    selectedPath: String,
    filterType: FilterType = FilterType.ALL
): FilterCriteria {
    return when (filterType) {
        FilterType.DISTINCT_VALUES -> {
            val result = getDistinctValues(selectedPath)
            FilterCriteria(
                path = selectedPath,
                availableValues = result.data.map { it.toString() },
                resultStatus = result.status
            )
        }
        FilterType.STATS_SUMMARY -> {
            val result = getStatsSummary(selectedPath)
            FilterCriteria(
                path = selectedPath,
                statsInfo = result.data.map { it.toString() },
                resultStatus = result.status
            )
        }
        FilterType.ALL -> {
            val distinctResult = getDistinctValues(selectedPath)
            val statsResult = getStatsSummary(selectedPath)
            FilterCriteria(
                path = selectedPath,
                availableValues = distinctResult.data.map { it.toString() },
                statsInfo = statsResult.data.map { it.toString() },
                resultStatus = distinctResult.status
            )
        }
    }
}

/**
 * Types de filtrage disponibles
 */
enum class FilterType {
    DISTINCT_VALUES,  // Valeurs distinctes uniquement
    STATS_SUMMARY,    // Résumé statistique uniquement
    ALL              // Tout ce qui est disponible
}

/**
 * Critères de filtrage construits pour un path
 */
data class FilterCriteria(
    val path: String,
    val availableValues: List<String> = emptyList(),
    val statsInfo: List<String> = emptyList(),
    val resultStatus: com.assistant.core.navigation.data.DataResultStatus
)

// ═══════════════════════════════════════════════════════════════════════════════════════
// Extensions pour utiliser la structure commune ToolDataEntity
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Récupère les champs disponibles pour un outil donné
 * Combine les champs communs (name, timestamp) avec les champs du JSON 'data' aplatis
 */
suspend fun DataNavigator.getAvailableFields(toolPath: String, context: Context): List<SchemaNode> = withContext(Dispatchers.IO) {
    try {
        LogManager.schema("Getting available fields for tool path: $toolPath")

        val fields = mutableListOf<SchemaNode>()

        // 1. Add common fields (from ToolDataEntity structure)
        fields.add(SchemaNode(
            path = "$toolPath.name",
            displayName = "Nom de l'entrée",
            type = NodeType.FIELD,
            hasChildren = false,
            fieldType = "string"
        ))

        fields.add(SchemaNode(
            path = "$toolPath.timestamp",
            displayName = "Date/Heure",
            type = NodeType.FIELD,
            hasChildren = false,
            fieldType = "timestamp"
        ))

        // 2. Add data fields from JSON 'data' schema
        val dataFields = this@getAvailableFields.getDataFields(toolPath, context)
        fields.addAll(dataFields)

        LogManager.schema("Generated ${fields.size} available fields for $toolPath")
        fields

    } catch (e: Exception) {
        LogManager.schema("Error getting available fields for $toolPath: ${e.message}", "ERROR", e)
        emptyList()
    }
}

/**
 * Récupère les champs de données du schéma JSON 'data'
 */
suspend fun DataNavigator.getDataFields(toolPath: String, context: Context): List<SchemaNode> = withContext(Dispatchers.IO) {
    try {
        // TODO: Implement proper tool instance -> tool type + config mapping
        // ARCHITECTURE CORRECT FLOW:
        // 1. Extract tool_instance_id from toolPath
        // 2. Get tool instance from database with config_json
        // 3. Get toolType from tool_instance.tool_type
        // 4. Use toolType.getResolvedDataSchema(config_json, context) instead of getDataSchema

        // For now, assume tracking type as placeholder since we don't have the mapping function
        val toolType = ToolTypeManager.getToolType("tracking")
        if (toolType == null) {
            LogManager.schema("No tool type found for path: $toolPath", "ERROR")
            return@withContext emptyList()
        }

        // TODO: Replace with resolved schema once we have config_json
        // val schemaString = toolType.getResolvedDataSchema(config_json, context)
        val schemaString = toolType.getDataSchema(context)
        if (schemaString.isNullOrBlank()) {
            LogManager.schema("No data schema found for tool type: ${toolType::class.simpleName}", "ERROR")
            return@withContext emptyList()
        }

        val schema = JSONObject(schemaString)
        val properties = schema.optJSONObject("properties")
        if (properties == null) {
            LogManager.schema("No properties found in data schema", "ERROR")
            return@withContext emptyList()
        }

        // Look for 'data' property specifically (this is the JSON content)
        val dataProperty = properties.optJSONObject("data")
        if (dataProperty == null) {
            LogManager.schema("No 'data' property found in schema", "WARN")
            return@withContext emptyList()
        }

        val dataProperties = dataProperty.optJSONObject("properties")
        if (dataProperties == null) {
            LogManager.schema("No properties found in 'data' schema", "ERROR")
            return@withContext emptyList()
        }

        // Convert data properties to data fields
        val fields = mutableListOf<SchemaNode>()

        val keys = dataProperties.keys()
        while (keys.hasNext()) {
            val fieldName = keys.next()
            val fieldSchema = dataProperties.getJSONObject(fieldName)
            val fieldType = fieldSchema.optString("type", "unknown")
            val description = fieldSchema.optString("description", fieldName)
            val hasChildren = (fieldType == "object") || fieldSchema.has("properties")

            fields.add(SchemaNode(
                path = "$toolPath.data.$fieldName",
                displayName = description,
                type = NodeType.FIELD,
                hasChildren = hasChildren,
                fieldType = fieldType
            ))
        }

        LogManager.schema("Generated ${fields.size} data fields for $toolPath")
        fields

    } catch (e: Exception) {
        LogManager.schema("Error getting data fields for $toolPath: ${e.message}", "ERROR", e)
        emptyList()
    }
}

/**
 * Récupère les enfants d'un champ d'outil (navigation dans les propriétés du schéma JSON)
 * Version améliorée qui utilise la structure commune ToolDataEntity
 */
suspend fun DataNavigator.getFieldChildrenFromCommonStructure(toolPath: String, context: Context): List<SchemaNode> = withContext(Dispatchers.IO) {
    try {
        LogManager.schema("Getting field children for tool path: $toolPath")

        // If this is a root tool path, return available fields
        if (!toolPath.contains('.') || toolPath.count { it == '.' } == 1) {
            return@withContext this@getFieldChildrenFromCommonStructure.getAvailableFields(toolPath, context)
        }

        // Otherwise, navigate deeper into nested properties
        // Extract field path after tool name (e.g., "weight_tracker.data.nested_field" -> "data.nested_field")
        val toolName = toolPath.substringBefore('.')
        val fieldPath = toolPath.substringAfter("$toolName.")

        // TODO: Implement proper tool instance -> tool type + config mapping
        // ARCHITECTURE CORRECT FLOW: same as getDataFields above

        // For now, assume tracking type as placeholder
        val toolType = ToolTypeManager.getToolType("tracking")
        if (toolType == null) {
            LogManager.schema("No tool type found for path: $toolPath", "ERROR")
            return@withContext emptyList()
        }

        // TODO: Replace with resolved schema once we have config_json
        // val schemaString = toolType.getResolvedDataSchema(config_json, context)
        val schemaString = toolType.getDataSchema(context)
        if (schemaString.isNullOrBlank()) {
            LogManager.schema("No data schema found for tool type: ${toolType::class.simpleName}", "ERROR")
            return@withContext emptyList()
        }

        val schema = JSONObject(schemaString)
        val nestedSchema = navigateToNestedSchema(schema, fieldPath)
        if (nestedSchema?.has("properties") != true) {
            LogManager.schema("No nested properties found at path: $fieldPath", "ERROR")
            return@withContext emptyList()
        }

        // Convert nested properties to SchemaNode list
        val children = mutableListOf<SchemaNode>()
        val properties = nestedSchema.getJSONObject("properties")

        val keys = properties.keys()
        while (keys.hasNext()) {
            val fieldName = keys.next()
            val fieldSchema = properties.getJSONObject(fieldName)
            val fieldType = fieldSchema.optString("type", "unknown")
            val description = fieldSchema.optString("description", fieldName)
            val hasChildren = (fieldType == "object") || fieldSchema.has("properties")

            children.add(SchemaNode(
                path = "$toolPath.$fieldName",
                displayName = description,
                type = NodeType.FIELD,
                hasChildren = hasChildren,
                fieldType = fieldType
            ))
        }

        LogManager.schema("Generated ${children.size} field children for $toolPath")
        children

    } catch (e: Exception) {
        LogManager.schema("Error getting field children for $toolPath: ${e.message}", "ERROR", e)
        emptyList()
    }
}

/**
 * Navigate to a nested schema based on field path
 */
private fun navigateToNestedSchema(schema: JSONObject, fieldPath: String): JSONObject? {
    var currentSchema = schema
    val pathParts = fieldPath.split('.')

    for (part in pathParts) {
        val properties = currentSchema.optJSONObject("properties") ?: return null
        currentSchema = properties.optJSONObject(part) ?: return null
    }

    return currentSchema
}