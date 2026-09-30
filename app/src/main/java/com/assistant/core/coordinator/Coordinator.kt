package com.assistant.core.coordinator

import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.commands.CommandResult
import com.assistant.core.commands.CommandStatus
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * CommandDispatcher - orchestrates all operations with unified resource.operation pattern.
 *
 * The operations given to one instance run one at a time, in the order they came: each caller
 * runs its own and gets its own result back.
 */
class Coordinator(context: Context) {
    private val lock = Mutex()

    private val serviceRegistry = ServiceRegistry(context)
    private val tokens = ConcurrentHashMap<String, CancellationToken>()
    
    /**
     * An action of the user, from a screen; called from inside an operation (a service calling
     * another), it keeps that operation's origin: the AI's import writes its lines as the AI.
     */
    suspend fun processUserAction(action: String, params: Map<String, Any?> = emptyMap()): CommandResult {
        val command = convertToDispatchCommand(action, params, kotlin.coroutines.coroutineContext[Origin]?.source ?: Source.USER)
        return execute(command)
    }
    
    /** A command of the AI, whatever runs it: a chat, or an automation the scheduler started. */
    suspend fun processAICommand(action: String, params: Map<String, Any?> = emptyMap()): CommandResult {
        val command = convertToDispatchCommand(action, params, Source.AI)
        return execute(command)
    }
    
    /** A command of [source], for a caller that runs commands of several origins (CommandExecutor). */
    suspend fun process(source: Source, action: String, params: Map<String, Any?> = emptyMap()): CommandResult = when (source) {
        Source.USER -> processUserAction(action, params)
        Source.AI -> processAICommand(action, params)
        Source.SCHEDULER -> processScheduledTask(action, params)
        Source.SYSTEM -> execute(convertToDispatchCommand(action, params, Source.SYSTEM))
    }

    /** A task of a scheduler, which no one is watching. */
    suspend fun processScheduledTask(task: String, params: Map<String, Any?> = emptyMap()): CommandResult {
        val command = convertToDispatchCommand(task, params, Source.SCHEDULER)
        return execute(command)
    }
    
    /**
     * Convert action/params to DispatchCommand object
     */
    private fun convertToDispatchCommand(action: String, params: Map<String, Any?>, source: Source): DispatchCommand {
        return DispatchCommand(
            action = action,
            params = params,
            source = source,
            id = null
        )
    }
    
    
    /** Runs [command] once the operations before it on this instance are done. */
    private suspend fun execute(command: DispatchCommand): CommandResult = lock.withLock {
        try {
            // Services receive only the command's own params: a read that refuses unknown params
            // (tool_data.get) would refuse anything added here
            try {
                val (resource, operation) = command.parseAction()
                val service = serviceRegistry.getService(resource)
                if (service == null) {
                    CommandResult(
                        status = CommandStatus.ERROR,
                        error = "Service not found for resource: $resource"
                    )
                } else {
                    executeServiceOperation(command, service, operation)
                }
            } catch (e: IllegalArgumentException) {
                CommandResult(
                    status = CommandStatus.UNKNOWN_ACTION,
                    error = "Invalid action format: ${command.action}"
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            CommandResult(
                status = CommandStatus.ERROR,
                error = "Command execution failed: ${e.message}"
            )
        }
    }

    /**
     * Generic method to execute service operations - simplified for new architecture
     */
    private suspend fun executeServiceOperation(
        command: DispatchCommand,
        service: ExecutableService,
        operation: String
    ): CommandResult {
        LogManager.coordination("executeServiceOperation: operation=$operation", "VERBOSE")
        val opId = command.id ?: "op_${System.currentTimeMillis()}"
        val token = CancellationToken()
        tokens[opId] = token
        
        return try {
            // Convert params Map to JSONObject with recursive conversion of nested structures
            // This ensures nested Maps/Lists are properly converted to JSONObject/JSONArray
            // (e.g., entries[].data becomes JSONObject instead of remaining as Map)
            val params = com.assistant.core.utils.JsonUtils.toJSONObject(command.params)

            // The origin goes with the operation, for its service and every call made from it
            val result = kotlinx.coroutines.withContext(Origin(command.source)) { service.execute(operation, params, token) }
            LogManager.coordination("Service result: success=${result.success}, error=${result.error}", "VERBOSE")
            
            CommandResult(
                status = when {
                    result.cancelled -> CommandStatus.CANCELLED
                    result.success -> CommandStatus.SUCCESS
                    else -> CommandStatus.ERROR
                },
                message = if (result.success) "Operation completed successfully" else null,
                error = result.error,
                data = result.data
            )
        } catch (e: Exception) {
            CommandResult(
                status = CommandStatus.ERROR,
                error = "Service operation failed: ${e.message}"
            )
        } finally {
            tokens.remove(opId)
        }
    }
}
