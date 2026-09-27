package com.assistant.core.navigation

import android.content.Context
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandResult
import com.assistant.core.commands.CommandStatus
import com.assistant.core.strings.Strings

/**
 * Extensions wiring DataNavigator into the existing architecture
 */


/**
 * Extension making navigation easier from the UI
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

// Tool Instance Resolution

/**
 * Tool instance information resolved from coordinator
 */
data class ToolInstanceInfo(
    val toolType: String,
    val configJson: String
)

/**
 * Resolve tool instance ID to tool type and config JSON via coordinator
 */
suspend fun resolveToolInstance(toolInstanceId: String, context: Context): ToolInstanceInfo? = withContext(Dispatchers.IO) {
    try {
        LogManager.schema("RESOLVE: Attempting to resolve tool instance: '$toolInstanceId'")

        val coordinator = Coordinator(context)
        val result = coordinator.processUserAction(
            action = "tools.get",
            params = mapOf("tool_instance_id" to toolInstanceId)
        )

        LogManager.schema("RESOLVE: Coordinator result status: ${result.status}, error: ${result.error}")

        if (result.status != CommandStatus.SUCCESS) {
            LogManager.schema("RESOLVE: Failed to resolve tool instance $toolInstanceId: ${result.error}", "ERROR")
            return@withContext null
        }

        val toolInstance = result.data?.get("tool_instance") as? Map<String, Any>
        if (toolInstance == null) {
            LogManager.schema("RESOLVE: No tool_instance in result for $toolInstanceId", "ERROR")
            LogManager.schema("RESOLVE: Available result data keys: ${result.data?.keys}", "DEBUG")
            return@withContext null
        }

        val toolType = toolInstance["tooltype"] as? String
        val config = (toolInstance["config"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }

        LogManager.schema("RESOLVE: tooltype='$toolType', config keys=${config?.length() ?: 0}")

        if (toolType.isNullOrBlank() || config == null) {
            LogManager.schema("RESOLVE: Missing tooltype or config for $toolInstanceId", "ERROR")
            return@withContext null
        }

        LogManager.schema("RESOLVE: Successfully resolved $toolInstanceId -> tooltype: '$toolType'")
        ToolInstanceInfo(toolType, config.toString())

    } catch (e: Exception) {
        LogManager.schema("Error resolving tool instance $toolInstanceId: ${e.message}", "ERROR", e)
        null
    }
}

//
// Extensions built on the shared ToolDataEntity structure
//

/**
 * Get the fields available on a given tool
 * Combines the shared fields (name, timestamp) with the flattened JSON data fields
 */
suspend fun DataNavigator.getAvailableFields(toolPath: String, context: Context): List<SchemaNode> = withContext(Dispatchers.IO) {
    try {
        LogManager.schema("Getting available fields for tool path: $toolPath")

        val fields = mutableListOf<SchemaNode>()

        // 1. Add common fields (from ToolDataEntity structure)
        val s = Strings.`for`(context = context)
        fields.add(SchemaNode(
            path = "$toolPath.name",
            displayName = s.shared("data_navigator_entry_name_field"),
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
 * Get the data fields declared by the JSON data schema
 */
suspend fun DataNavigator.getDataFields(toolPath: String, context: Context): List<SchemaNode> = withContext(Dispatchers.IO) {
    try {
        LogManager.schema("DATA_FIELDS: Getting data fields for tool instance: '$toolPath'")

        // Resolve tool instance to get real tool type and config
        val toolInstanceInfo = resolveToolInstance(toolPath, context)
        if (toolInstanceInfo == null) {
            LogManager.schema("DATA_FIELDS: Could not resolve tool instance: '$toolPath'", "ERROR")
            return@withContext emptyList()
        }

        LogManager.schema("DATA_FIELDS: Resolved to toolType: '${toolInstanceInfo.toolType}'")

        val toolType = ToolTypeManager.getToolType(toolInstanceInfo.toolType)
        if (toolType == null) {
            LogManager.schema("DATA_FIELDS: No tool type found for: '${toolInstanceInfo.toolType}'", "ERROR")
            return@withContext emptyList()
        }

        LogManager.schema("DATA_FIELDS: Found ToolType: ${toolType::class.simpleName}")

        // The entry schema of this tool, its user's fields included
        val schemaString = com.assistant.core.tools.BaseSchemas.getEntrySchema(
            toolType, org.json.JSONObject(toolInstanceInfo.configJson), context
        )
        LogManager.schema("DATA_FIELDS: Schema string length: ${schemaString?.length ?: 0}")
        if (schemaString.isNullOrBlank()) {
            LogManager.schema("DATA_FIELDS: No data schema found for tool type: ${toolType::class.simpleName}", "ERROR")
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

            // Use ToolType's getFormFieldName for proper localized field names
            val displayName = toolType.getFormFieldName(fieldName, context)
            val hasChildren = (fieldType == "object") || fieldSchema.has("properties")

            fields.add(SchemaNode(
                path = "$toolPath.data.$fieldName",
                displayName = displayName,
                type = NodeType.FIELD,
                hasChildren = hasChildren,
                fieldType = fieldType
            ))
        }

        LogManager.schema("DATA_FIELDS: Generated ${fields.size} data fields for '$toolPath'")
        fields.forEach { field ->
            LogManager.schema("DATA_FIELDS: Field - path: '${field.path}', display: '${field.displayName}', type: '${field.fieldType}'")
        }
        fields

    } catch (e: Exception) {
        LogManager.schema("DATA_FIELDS: Error getting data fields for '$toolPath': ${e.message}", "ERROR", e)
        emptyList()
    }
}

/**
 * Get the children of a tool field, walking the JSON schema properties
 * Uses the shared ToolDataEntity structure
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

        // Extract tool instance ID from complex path
        val toolInstanceId = toolName

        // Resolve tool instance to get real tool type and config
        val toolInstanceInfo = resolveToolInstance(toolInstanceId, context)
        if (toolInstanceInfo == null) {
            LogManager.schema("Could not resolve tool instance: $toolInstanceId", "ERROR")
            return@withContext emptyList()
        }

        val toolType = ToolTypeManager.getToolType(toolInstanceInfo.toolType)
        if (toolType == null) {
            LogManager.schema("No tool type found for: ${toolInstanceInfo.toolType}", "ERROR")
            return@withContext emptyList()
        }

        // The entry schema of this tool, its user's fields included
        val schemaString = com.assistant.core.tools.BaseSchemas.getEntrySchema(
            toolType, org.json.JSONObject(toolInstanceInfo.configJson), context
        )
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

            // Use ToolType's getFormFieldName for proper localized field names
            val displayName = toolType.getFormFieldName(fieldName, context)
            val hasChildren = (fieldType == "object") || fieldSchema.has("properties")

            children.add(SchemaNode(
                path = "$toolPath.$fieldName",
                displayName = displayName,
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