package com.assistant.core.navigation

import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.ContextualDataResult
import com.assistant.core.navigation.data.DataResultStatus
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaResolver
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * DataNavigator - Navigation hiérarchique dans les données via schémas
 *
 * Permet de naviguer dans la structure App → Zones → Outils → Champs
 * avec chargement à la demande et résolution des schémas conditionnels.
 */
class DataNavigator(private val context: Context) {

    private val coordinator = Coordinator(context)

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
                    val zoneName = zone["name"] as? String ?: "Zone sans nom"

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

            // Résolution du schéma data selon config actuelle
            val dataSchema = toolType.getSchema("data", context)
            if (dataSchema == null) {
                LogManager.coordination("No data schema found for toolType: ${toolInstance.toolType}", "WARN")
                return emptyList()
            }
            val configMap = parseJsonToMap(toolInstance.config)
            val resolvedSchema = SchemaResolver.resolve(dataSchema, configMap)

            LogManager.coordination("Resolved data schema for tool $toolInstanceId")
            return parseSchemaToFieldNodes(resolvedSchema, "tools.$toolInstanceId")

        } catch (e: Exception) {
            LogManager.coordination("Error getting field children for $toolInstanceId: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // ═══ Bridge vers données réelles ═══

    /**
     * Récupère les valeurs distinctes d'un champ (avec garde-fous)
     */
    suspend fun getDistinctValues(path: String): ContextualDataResult {
        LogManager.coordination("DataNavigator: Getting distinct values for path: $path")

        return try {
            // TODO: Implémenter vraie logique avec garde-fous
            // Pour l'instant, toujours OK
            ContextualDataResult(
                status = DataResultStatus.OK,
                data = listOf("exemple1", "exemple2", "exemple3"),
                totalCount = 3
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