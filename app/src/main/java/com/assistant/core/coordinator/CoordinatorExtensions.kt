package com.assistant.core.coordinator

import com.assistant.core.utils.LogManager
import com.assistant.core.commands.CommandStatus
import com.assistant.core.commands.CommandResult
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
): CommandResult? {
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
inline fun <reified T> CommandResult.mapData(
    key: String,
    mapper: (Map<String, Any>) -> T
): List<T> {
    val data = this.data?.get(key) as? List<*> ?: emptyList<Any>()
    return data.mapNotNull { item ->
        try {
            val map = item as? Map<String, Any> ?: return@mapNotNull null
            mapper(map)
        } catch (e: Exception) {
            LogManager.coordination("Failed to map $key item: ${e.message}", "WARN", e)
            null
        }
    }
}

/**
 * Extract single object from OperationResult with safe error handling
 */
inline fun <reified T> CommandResult.mapSingleData(
    key: String,
    mapper: (Map<String, Any>) -> T
): T? {
    return try {
        val map = this.data?.get(key) as? Map<String, Any> ?: return null
        mapper(map)
    } catch (e: Exception) {
        LogManager.coordination("Failed to map single $key: ${e.message}", "WARN", e)
        null
    }
}

/**
 * Check if operation was successful
 */
val CommandResult.isSuccess: Boolean
    get() = status == CommandStatus.SUCCESS