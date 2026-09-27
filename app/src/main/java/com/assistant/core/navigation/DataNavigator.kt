package com.assistant.core.navigation

import com.assistant.core.utils.JsonUtils
import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import org.json.JSONObject
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DataNavigator - hierarchical navigation through the data, driven by schemas
 *
 * Walks the App -> Zones -> Tools -> Fields structure
 * with on-demand loading and resolution of conditional schemas.
 */
class DataNavigator(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val s = Strings.`for`(context = context)

    /**
     * Get the root nodes (zones)
     */
    suspend fun getRootNodes(): List<SchemaNode> {
        LogManager.coordination("DataNavigator: Getting root nodes (zones)")

        return try {
            // Load real zones via coordinator
            val result = coordinator.processUserAction("zones.list", emptyMap())

            if (result.isSuccess) {
                val zones = result.data?.get("zones") as? List<Map<String, Any>> ?: emptyList()
                zones.map { zone ->
                    val zoneId = zone["id"] as? String ?: ""
                    val zoneName = zone["name"] as? String ?: s.shared("data_navigator_unnamed_zone")

                    SchemaNode(
                        path = "zones.$zoneId",
                        displayName = zoneName,
                        type = NodeType.ZONE,
                        hasChildren = true
                    )
                }
            } else {
                LogManager.coordination("Failed to load zones: ${result.error}", "ERROR")
                emptyList()
            }
        } catch (e: Exception) {
            LogManager.coordination("Error getting root nodes: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    /**
     * Get the children of a node (the tools of a zone)
     */
    suspend fun getChildren(parentPath: String): List<SchemaNode> {
        LogManager.coordination("DataNavigator: Getting children for path: $parentPath")

        return try {
            when {
                parentPath.startsWith("zones.") -> {
                    val zoneId = parentPath.substringAfter("zones.")
                    getToolsInZone(zoneId)
                }
                else -> {
                    LogManager.coordination("Unknown parent path pattern: $parentPath", "WARN")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            LogManager.coordination("Error getting children for $parentPath: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    /**
     * Get a tool fields, as its current configuration defines them
     */
    suspend fun getFieldChildren(toolInstanceId: String): List<SchemaNode> {
        LogManager.coordination("DataNavigator: Getting field children for tool: $toolInstanceId")

        return try {
            // Load real tool instance via coordinator
            val toolInstance = getToolInstance(toolInstanceId)
            if (toolInstance == null) {
                LogManager.coordination("Tool instance not found: $toolInstanceId", "ERROR")
                return emptyList()
            }

            val toolType = ToolTypeManager.getToolType(toolInstance.toolType)
            if (toolType == null) {
                LogManager.coordination("ToolType not found: ${toolInstance.toolType}", "ERROR")
                return emptyList()
            }

            // The entry schema of this tool, its user's fields included
            val schemaContent = com.assistant.core.tools.BaseSchemas.getEntrySchemaOrThrow(
                toolType, org.json.JSONObject(toolInstance.config), toolInstanceId, context
            )

            LogManager.coordination("Resolved data schema for tool $toolInstanceId")
            return parseSchemaToFieldNodes(schemaContent, "tools.$toolInstanceId")

        } catch (e: Exception) {
            LogManager.coordination("Error getting field children for $toolInstanceId: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // Private Methods

    private suspend fun getToolsInZone(zoneId: String): List<SchemaNode> {
        return try {
            // Load real tool instances via coordinator
            val result = coordinator.processUserAction("tools.list", mapOf("zone_id" to zoneId))

            if (result.isSuccess) {
                val toolInstances = result.data?.get("tool_instances") as? List<Map<String, Any>> ?: emptyList()
                toolInstances.map { toolInstance ->
                    val instanceId = toolInstance["id"] as? String ?: ""
                    val instanceName = toolInstance["name"] as? String ?: ""
                    val toolType = toolInstance["tooltype"] as? String ?: ""

                    // Format: "Nom de l'instance (type)" or just type if no name
                    val displayName = if (instanceName.isNotBlank()) {
                        "$instanceName ($toolType)"
                    } else {
                        toolType.replaceFirstChar { it.uppercase() }
                    }

                    SchemaNode(
                        path = "tools.$instanceId",
                        displayName = displayName,
                        type = NodeType.TOOL,
                        hasChildren = true,
                        toolType = toolType
                    )
                }
            } else {
                LogManager.coordination("Failed to load tools for zone $zoneId: ${result.error}", "ERROR")
                emptyList()
            }
        } catch (e: Exception) {
            LogManager.coordination("Error loading tools for zone $zoneId: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    private suspend fun getToolInstance(toolInstanceId: String): ToolInstanceData? {
        return try {
            // Load real tool instance via coordinator
            val result = coordinator.processUserAction(
                "tools.get",
                mapOf("tool_instance_id" to toolInstanceId)
            )

            if (result.isSuccess) {
                val instance = result.data?.get("tool_instance") as? Map<String, Any>
                if (instance != null) {
                    val instanceName = instance["name"] as? String ?: ""
                    val toolType = instance["tooltype"] as? String ?: ""
                    ToolInstanceData(
                        id = instance["id"] as? String ?: toolInstanceId,
                        name = instanceName.ifBlank { toolType.replaceFirstChar { it.uppercase() } },
                        toolType = toolType,
                        config = (instance["config"] as? Map<String, Any?>)
                            ?.let { JsonUtils.toJSONObject(it).toString() } ?: "{}"
                    )
                } else {
                    LogManager.coordination("Tool instance not found in response: $toolInstanceId", "ERROR")
                    null
                }
            } else {
                LogManager.coordination("Failed to load tool instance $toolInstanceId: ${result.error}", "ERROR")
                null
            }
        } catch (e: Exception) {
            LogManager.coordination("Error loading tool instance $toolInstanceId: ${e.message}", "ERROR", e)
            null
        }
    }

    private fun parseSchemaToFieldNodes(resolvedSchema: String, basePath: String): List<SchemaNode> {
        return try {
            val schema = JSONObject(resolvedSchema)
            val properties = schema.optJSONObject("properties")

            if (properties == null) {
                LogManager.coordination("No properties found in resolved schema")
                return emptyList()
            }

            val nodes = mutableListOf<SchemaNode>()
            properties.keys().forEach { fieldName ->
                val fieldSchema = properties.getJSONObject(fieldName)
                val fieldType = fieldSchema.optString("type", "unknown")
                val description = fieldSchema.optString("description", "")

                nodes.add(SchemaNode(
                    path = "$basePath.$fieldName",
                    displayName = fieldName + if (description.isNotEmpty()) " ($description)" else "",
                    type = NodeType.FIELD,
                    hasChildren = false,
                    fieldType = fieldType
                ))
            }

            LogManager.coordination("Parsed ${nodes.size} field nodes from schema")
            nodes

        } catch (e: Exception) {
            LogManager.coordination("Error parsing schema to field nodes: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // Data class for tool instance information
    private data class ToolInstanceData(
        val id: String,
        val name: String,
        val toolType: String,
        val config: String
    )
}