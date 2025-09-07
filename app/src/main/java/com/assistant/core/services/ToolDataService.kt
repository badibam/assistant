package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Operation
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.AppDatabase
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaValidator
import org.json.JSONObject
import java.util.*

/**
 * Service centralisé pour toutes les opérations sur tool_data
 * Remplace les services spécialisés (TrackingService, etc.)
 */
class ToolDataService(private val context: Context) : ExecutableService {

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): Operation.OperationResult {
        if (token.isCancelled) return Operation.OperationResult.cancelled()

        return try {
            when (operation) {
                "create" -> createEntry(params, token)
                "update" -> updateEntry(params, token)
                "delete" -> deleteEntry(params, token)
                "get_entries" -> getEntries(params, token)
                "get_stats" -> getStats(params, token)
                "delete_all_entries" -> deleteAllEntries(params, token)
                else -> Operation.OperationResult.error("Unknown operation: $operation")
            }
        } catch (e: Exception) {
            Operation.OperationResult.error("ToolDataService error: ${e.message}")
        }
    }

    private suspend fun createEntry(params: JSONObject, token: CancellationToken): Operation.OperationResult {
        if (token.isCancelled) return Operation.OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        val tooltype = params.optString("tooltype")
        val dataJson = params.optJSONObject("data")?.toString() ?: "{}"
        val timestamp = if (params.has("timestamp")) params.optLong("timestamp") else null
        val name = params.optString("name", null)

        if (toolInstanceId.isEmpty() || tooltype.isEmpty()) {
            return Operation.OperationResult.error("Missing required parameters: toolInstanceId, tooltype")
        }

        // Validation via ToolType
        val toolType = ToolTypeManager.getToolType(tooltype)
        if (toolType != null) {
            val dataMap = JSONObject(dataJson).let { json ->
                mutableMapOf<String, Any>().apply {
                    json.keys().forEach { key ->
                        put(key, json.get(key))
                    }
                }
            }
            
            val validation = SchemaValidator.validate(toolType, dataMap, context, useDataSchema = true)
            if (!validation.isValid) {
                return Operation.OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }

        val now = System.currentTimeMillis()
        val dataVersion = toolType?.getCurrentDataVersion() ?: 1
        val entity = ToolDataEntity(
            id = UUID.randomUUID().toString(),
            toolInstanceId = toolInstanceId,
            tooltype = tooltype,
            dataVersion = dataVersion,
            timestamp = timestamp,
            name = name,
            data = dataJson,
            createdAt = now,
            updatedAt = now
        )

        val dao = getToolDataDao()
        dao.insert(entity)

        return Operation.OperationResult.success(
            data = mapOf(
                "id" to entity.id,
                "createdAt" to entity.createdAt
            )
        )
    }

    private suspend fun updateEntry(params: JSONObject, token: CancellationToken): Operation.OperationResult {
        if (token.isCancelled) return Operation.OperationResult.cancelled()

        val entryId = params.optString("id")
        val dataJson = params.optJSONObject("data")?.toString()
        val timestamp = if (params.has("timestamp")) params.optLong("timestamp") else null
        val name = params.optString("name", null)

        if (entryId.isEmpty()) {
            return Operation.OperationResult.error("Missing required parameter: id")
        }

        val dao = getToolDataDao()
        val existingEntity = dao.getById(entryId)
            ?: return Operation.OperationResult.error("Entry not found: $entryId")

        // Validation si nouvelles données fournies
        if (dataJson != null) {
            val toolType = ToolTypeManager.getToolType(existingEntity.tooltype)
            if (toolType != null) {
                val dataMap = JSONObject(dataJson).let { json ->
                    mutableMapOf<String, Any>().apply {
                        json.keys().forEach { key ->
                            put(key, json.get(key))
                        }
                    }
                }
                
                val validation = SchemaValidator.validate(toolType, dataMap, context, useDataSchema = true)
                if (!validation.isValid) {
                    return Operation.OperationResult.error("Validation failed: ${validation.errorMessage}")
                }
            }
        }

        val updatedEntity = existingEntity.copy(
            data = dataJson ?: existingEntity.data,
            timestamp = timestamp ?: existingEntity.timestamp,
            name = name ?: existingEntity.name,
            updatedAt = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        return Operation.OperationResult.success(
            data = mapOf(
                "id" to updatedEntity.id,
                "updatedAt" to updatedEntity.updatedAt
            )
        )
    }

    private suspend fun deleteEntry(params: JSONObject, token: CancellationToken): Operation.OperationResult {
        if (token.isCancelled) return Operation.OperationResult.cancelled()

        val entryId = params.optString("id")
        if (entryId.isEmpty()) {
            return Operation.OperationResult.error("Missing required parameter: id")
        }

        val dao = getToolDataDao()
        dao.deleteById(entryId)

        return Operation.OperationResult.success()
    }

    private suspend fun getEntries(params: JSONObject, token: CancellationToken): Operation.OperationResult {
        if (token.isCancelled) return Operation.OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return Operation.OperationResult.error("Missing required parameter: toolInstanceId")
        }

        val dao = getToolDataDao()
        val entries = dao.getByToolInstance(toolInstanceId)

        return Operation.OperationResult.success(
            data = mapOf("entries" to entries.map { entity ->
                mapOf(
                    "id" to entity.id,
                    "toolInstanceId" to entity.toolInstanceId,
                    "tooltype" to entity.tooltype,
                    "dataVersion" to entity.dataVersion,
                    "timestamp" to entity.timestamp,
                    "name" to entity.name,
                    "data" to entity.data,
                    "createdAt" to entity.createdAt,
                    "updatedAt" to entity.updatedAt
                )
            })
        )
    }

    private suspend fun getStats(params: JSONObject, token: CancellationToken): Operation.OperationResult {
        if (token.isCancelled) return Operation.OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return Operation.OperationResult.error("Missing required parameter: toolInstanceId")
        }

        val dao = getToolDataDao()
        val count = dao.countByToolInstance(toolInstanceId)

        return Operation.OperationResult.success(
            data = mapOf(
                "count" to count,
                "first_entry" to null, // TODO: implémenter si nécessaire
                "last_entry" to null   // TODO: implémenter si nécessaire
            )
        )
    }

    private suspend fun deleteAllEntries(params: JSONObject, token: CancellationToken): Operation.OperationResult {
        if (token.isCancelled) return Operation.OperationResult.cancelled()

        val toolInstanceId = params.optString("toolInstanceId")
        if (toolInstanceId.isEmpty()) {
            return Operation.OperationResult.error("Missing required parameter: toolInstanceId")
        }

        val dao = getToolDataDao()
        dao.deleteByToolInstance(toolInstanceId)

        return Operation.OperationResult.success()
    }

    /**
     * Récupère le DAO unifié tool_data
     */
    private fun getToolDataDao(): BaseToolDataDao {
        return AppDatabase.getDatabase(context).toolDataDao()
    }
}