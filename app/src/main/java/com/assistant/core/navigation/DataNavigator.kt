package com.assistant.core.navigation

import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.ContextualDataResult
import com.assistant.core.navigation.data.DataResultStatus
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaResolver
import com.assistant.core.utils.LogManager
import org.json.JSONObject
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DataNavigator - Navigation hiérarchique dans les données via schémas
 *
 * Permet de naviguer dans la structure App → Zones → Outils → Champs
 * avec chargement à la demande et résolution des schémas conditionnels.
 */
class DataNavigator(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val s = Strings.`for`(context = context)

    /**
     * Récupère les nœuds racine (zones)
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
                LogManager.coordination("Failed to load zones: ${result.message}", "ERROR")
                emptyList()
            }
        } catch (e: Exception) {
            LogManager.coordination("Error getting root nodes: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    /**
     * Récupère les enfants d'un nœud (outils d'une zone)
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
     * Récupère les champs d'un outil selon sa configuration actuelle
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

            // Récupérer le data_schema_id directement depuis la config
            val configMap = parseJsonToMap(toolInstance.config)
            val dataSchemaId = configMap["data_schema_id"]?.toString()

            if (dataSchemaId.isNullOrEmpty()) {
                LogManager.coordination("No data_schema_id found in config for tool $toolInstanceId", "WARN")
                return emptyList()
            }

            val dataSchema = toolType.getSchema(dataSchemaId, context)
            if (dataSchema == null) {
                LogManager.coordination("No data schema found for schemaId: $dataSchemaId", "WARN")
                return emptyList()
            }

            LogManager.coordination("Resolved data schema for tool $toolInstanceId")
            return parseSchemaToFieldNodes(dataSchema.content, "tools.$toolInstanceId")

        } catch (e: Exception) {
            LogManager.coordination("Error getting field children for $toolInstanceId: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // ═══ Bridge vers données réelles ═══

    /**
     * Récupère les valeurs distinctes d'un champ (avec garde-fous)
     */
    suspend fun getDistinctValues(path: String): ContextualDataResult = withContext(Dispatchers.IO) {
        LogManager.coordination("DataNavigator: Getting distinct values for path: $path")

        try {
            // Parse path to extract tool_instance_id and field
            // Path format: "instance_123.data.weight" or "instance_123.name" or "instance_123.timestamp"
            val pathParts = path.split(".")
            if (pathParts.isEmpty()) {
                return@withContext ContextualDataResult(
                    status = DataResultStatus.ERROR,
                    message = "Invalid path format: $path"
                )
            }

            val toolInstanceId = pathParts[0]
            val fieldPath = if (pathParts.size > 1) pathParts.drop(1).joinToString(".") else ""

            LogManager.coordination("DataNavigator: Resolved path - instance: $toolInstanceId, field: $fieldPath")

            // Use coordinator to get all tool data entries
            val coordinator = Coordinator(context)
            val result = coordinator.processUserAction(
                action = "tool_data.get",
                params = mapOf("toolInstanceId" to toolInstanceId)
            )

            if (result.status != CommandStatus.SUCCESS) {
                LogManager.coordination("Failed to get tool data for $path: ${result.error}", "ERROR")
                return@withContext ContextualDataResult(
                    status = DataResultStatus.ERROR,
                    message = result.error ?: "Unknown error"
                )
            }

            // Extract entries and get distinct values for the specified field
            val entries = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
            val distinctValues = mutableSetOf<String>()

            entries.forEach { entry ->
                val entryMap = entry as? Map<*, *>
                if (entryMap != null) {
                    val value = extractFieldValue(entryMap, fieldPath)
                    if (value != null && value.isNotBlank()) {
                        distinctValues.add(value)
                    }
                }
            }

            val values = distinctValues.toList().sorted()

            LogManager.coordination("DataNavigator: Found ${values.size} distinct values for $path")

            ContextualDataResult(
                status = DataResultStatus.OK,
                data = values,
                totalCount = values.size
            )

        } catch (e: Exception) {
            LogManager.coordination("Error getting distinct values for $path: ${e.message}", "ERROR", e)
            ContextualDataResult(
                status = DataResultStatus.ERROR,
                message = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Extract field value from entry map based on field path
     */
    private fun extractFieldValue(entryMap: Map<*, *>, fieldPath: String): String? {
        return when (fieldPath) {
            "name" -> entryMap["name"]?.toString()
            "timestamp" -> entryMap["timestamp"]?.toString()
            else -> {
                // For data.* fields, navigate into the data JSON
                if (fieldPath.startsWith("data.")) {
                    val dataField = fieldPath.substringAfter("data.")
                    val dataJson = entryMap["data"]?.toString()
                    if (dataJson != null) {
                        try {
                            val jsonObject = JSONObject(dataJson)
                            jsonObject.opt(dataField)?.toString()
                        } catch (e: Exception) {
                            LogManager.coordination("Error parsing data JSON for field $dataField: ${e.message}", "WARN")
                            null
                        }
                    } else null
                } else null
            }
        }
    }

    /**
     * Récupère un échantillon de données
     */
    suspend fun getDataSample(path: String, limit: Int = 10): ContextualDataResult {
        LogManager.coordination("DataNavigator: Getting data sample for path: $path (limit: $limit)")

        return try {
            // TODO: Implémenter vraie logique
            ContextualDataResult(
                status = DataResultStatus.OK,
                data = listOf("sample1", "sample2"),
                totalCount = 2
            )
        } catch (e: Exception) {
            LogManager.coordination("Error getting data sample for $path: ${e.message}", "ERROR", e)
            ContextualDataResult(
                status = DataResultStatus.ERROR,
                message = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Récupère un résumé statistique
     */
    suspend fun getStatsSummary(path: String): ContextualDataResult {
        LogManager.coordination("DataNavigator: Getting stats summary for path: $path")

        return try {
            // TODO: Implémenter vraie logique
            ContextualDataResult(
                status = DataResultStatus.OK,
                data = listOf("min: 0", "max: 100", "avg: 50"),
                totalCount = 3
            )
        } catch (e: Exception) {
            LogManager.coordination("Error getting stats summary for $path: ${e.message}", "ERROR", e)
            ContextualDataResult(
                status = DataResultStatus.ERROR,
                message = e.message ?: "Unknown error"
            )
        }
    }

    // ═══ Private Methods ═══

    private suspend fun getToolsInZone(zoneId: String): List<SchemaNode> {
        return try {
            // Load real tool instances via coordinator
            val result = coordinator.processUserAction("tools.list", mapOf("zone_id" to zoneId))

            if (result.isSuccess) {
                val toolInstances = result.data?.get("tool_instances") as? List<Map<String, Any>> ?: emptyList()
                toolInstances.map { toolInstance ->
                    val instanceId = toolInstance["id"] as? String ?: ""
                    val instanceName = toolInstance["name"] as? String ?: ""
                    val toolType = toolInstance["tool_type"] as? String ?: ""

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
                LogManager.coordination("Failed to load tools for zone $zoneId: ${result.message}", "ERROR")
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
                    val toolType = instance["tool_type"] as? String ?: ""
                    ToolInstanceData(
                        id = instance["id"] as? String ?: toolInstanceId,
                        name = instanceName.ifBlank { toolType.replaceFirstChar { it.uppercase() } },
                        toolType = toolType,
                        config = instance["config_json"] as? String ?: "{}"
                    )
                } else {
                    LogManager.coordination("Tool instance not found in response: $toolInstanceId", "ERROR")
                    null
                }
            } else {
                LogManager.coordination("Failed to load tool instance $toolInstanceId: ${result.message}", "ERROR")
                null
            }
        } catch (e: Exception) {
            LogManager.coordination("Error loading tool instance $toolInstanceId: ${e.message}", "ERROR", e)
            null
        }
    }

    private fun parseJsonToMap(jsonString: String): Map<String, Any> {
        return try {
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, Any>()
            json.keys().forEach { key ->
                map[key] = json.get(key)
            }
            map
        } catch (e: Exception) {
            LogManager.coordination("Error parsing JSON to map: ${e.message}", "ERROR", e)
            emptyMap()
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