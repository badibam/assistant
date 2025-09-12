package com.assistant.core.coordinator

import android.util.Log
import com.assistant.core.commands.CommandStatus
import com.assistant.core.commands.OperationResult
import kotlinx.coroutines.CancellationException

/**
 * Extension functions for Coordinator to reduce boilerplate code
 */

/**
 * Execute coordinator command with automatic loading state and error handling
 */
suspend fun Coordinator.executeWithLoading(
    operation: String,
    params: Map<String, Any> = emptyMap(),
    onLoading: (Boolean) -> Unit = {},
    onError: (String) -> Unit = {}
): OperationResult? {
    return try {
        onLoading(true)
        val result = processUserAction(operation, params)
        if (result.status != CommandStatus.SUCCESS) {
            onError(result.error ?: "Unknown error")
            null
        } else {
            result
        }
    } catch (e: CancellationException) {
        throw e // Re-throw cancellation
    } catch (e: Exception) {
        onError("Error: ${e.message}")
        null
    } finally {
        onLoading(false)
    }
}

/**
 * Map data from OperationResult to typed objects with safe error handling
 */
inline fun <reified T> OperationResult.mapData(
    key: String,
    mapper: (Map<String, Any>) -> T
): List<T> {
    val data = this.data?.get(key) as? List<*> ?: emptyList<Any>()
    return data.mapNotNull { item ->
        try {
            val map = item as? Map<String, Any> ?: return@mapNotNull null
            mapper(map)
        } catch (e: Exception) {
            Log.w("DataMapping", "Failed to map $key item: ${e.message}", e)
            null
        }
    }
}

/**
 * Extract single object from OperationResult with safe error handling
 */
inline fun <reified T> OperationResult.mapSingleData(
    key: String,
    mapper: (Map<String, Any>) -> T
): T? {
    return try {
        val map = this.data?.get(key) as? Map<String, Any> ?: return null
        mapper(map)
    } catch (e: Exception) {
        Log.w("DataMapping", "Failed to map single $key: ${e.message}", e)
        null
    }
}

/**
 * Check if operation was successful
 */
val OperationResult.isSuccess: Boolean
    get() = status == CommandStatus.SUCCESS