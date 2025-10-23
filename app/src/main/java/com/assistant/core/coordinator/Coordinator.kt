package com.assistant.core.coordinator

import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.commands.CommandResult
import com.assistant.core.commands.CommandStatus
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.ArrayDeque

/**
 * Represents a queued operation with its execution context
 */
data class QueuedOperation(
    val command: DispatchCommand,
    val operationId: String = UUID.randomUUID().toString(),
    val phase: Int = 1
)

/**
 * CommandDispatcher - orchestrates all operations with unified resource.operation pattern
 * Implements multi-step operations with background processing slot
 */
class Coordinator(context: Context) {
    private val _state = MutableStateFlow(CoordinatorState.IDLE)
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()
    
    // Queue system
    private val normalQueue = ArrayDeque<QueuedOperation>()
    private var backgroundSlot: QueuedOperation? = null
    private var isBackgroundSlotBusy = false
    
    private val serviceRegistry = ServiceRegistry(context)
    private val tokens = ConcurrentHashMap<String, CancellationToken>()
    
    /**
     * Process user action from UI - simple interface for UI layer
     */
    suspend fun processUserAction(action: String, params: Map<String, Any> = emptyMap()): CommandResult {
        val command = convertToDispatchCommand(action, params, Source.USER)
        val queuedOp = QueuedOperation(command)
        return enqueueAndProcess(queuedOp)
    }
    
    /**
     * Process AI command - simplified for new resource.operation format
     * AI must now send actions in format: "zones.create", "tools.update", etc.
     */
    suspend fun processAICommand(action: String, params: Map<String, Any> = emptyMap()): CommandResult {
        val command = convertToDispatchCommand(action, params, Source.AI)
        val queuedOp = QueuedOperation(command)
        return enqueueAndProcess(queuedOp)
    }
    
    /**
     * Process scheduled task - simple interface for scheduler
     */
    suspend fun processScheduledTask(task: String, params: Map<String, Any> = emptyMap()): CommandResult {
        val command = convertToDispatchCommand(task, params, Source.SCHEDULER)
        val queuedOp = QueuedOperation(command)
        return enqueueAndProcess(queuedOp)
    }
    
    /**
     * Convert action/params to DispatchCommand object
     */
    private fun convertToDispatchCommand(action: String, params: Map<String, Any>, source: Source): DispatchCommand {
        return DispatchCommand(
            action = action,
            params = params,
            source = source,
            id = null
        )
    }
    
    
    /**
     * Enqueue operation and process queue
     */
    private suspend fun enqueueAndProcess(queuedOp: QueuedOperation): CommandResult {
        normalQueue.addLast(queuedOp)
        return processQueue()
    }
    
    /**
     * Process next operation from queue - waits for coordinator to be available
     */
    private suspend fun processQueue(): CommandResult {
        // Wait for coordinator to be idle instead of rejecting
        while (_state.value != CoordinatorState.IDLE) {
            kotlinx.coroutines.delay(50) // Wait 50ms before checking again
        }
        
        val queuedOp = if (normalQueue.isNotEmpty()) {
            normalQueue.removeFirst()
        } else {
            return CommandResult(
                status = CommandStatus.ERROR,
                error = "No operations in queue"
            )
        }
        
        return executeQueuedOperation(queuedOp)
    }
    
    /**
     * Execute a queued operation
     */
    private suspend fun executeQueuedOperation(queuedOp: QueuedOperation): CommandResult {
        _state.value = CoordinatorState.OPERATION_IN_PROGRESS
        
        return try {
            val command = queuedOp.command.copy(
                params = queuedOp.command.params + mapOf(
                    "operationId" to queuedOp.operationId,
                    "phase" to queuedOp.phase
                )
            )
            
            // New unified dispatch logic
            val result = try {
                val (resource, operation) = command.parseAction()
                val service = serviceRegistry.getService(resource)
                
                if (service == null) {
                    CommandResult(
                        commandId = command.id,
                        status = CommandStatus.ERROR,
                        error = "Service not found for resource: $resource"
                    )
                } else {
                    executeServiceOperation(command, service, operation, queuedOp.phase)
                }
            } catch (e: IllegalArgumentException) {
                CommandResult(
                    commandId = command.id,
                    status = CommandStatus.UNKNOWN_ACTION,
                    error = "Invalid action format: ${command.action}"
                )
            }
            
            // Handle multi-step operations
            handleMultiStepResult(result, queuedOp)
            
            result
        } catch (e: Exception) {
            CommandResult(
                commandId = queuedOp.command.id,
                status = CommandStatus.ERROR,
                error = "Command execution failed: ${e.message}"
            )
        } finally {
            _state.value = CoordinatorState.IDLE
        }
    }
    
    /**
     * Handle results that require additional steps
     */
    private suspend fun handleMultiStepResult(result: CommandResult, queuedOp: QueuedOperation) {
        when {
            result.requiresBackground -> {
                // Phase 1 → 2: Queue background processing
                if (isBackgroundSlotBusy) {
                    // Slot busy, re-queue at end
                    val requeued = queuedOp.copy(phase = 2)
                    normalQueue.addLast(requeued)
                } else {
                    // Start background processing
                    startBackgroundProcessing(queuedOp)
                }
            }
            
            result.requiresContinuation -> {
                // Phase 2 → 3: Queue final step
                val finalStep = queuedOp.copy(phase = 3)
                normalQueue.addLast(finalStep)
                
                // Free background slot
                isBackgroundSlotBusy = false
                backgroundSlot = null
            }
        }
    }
    
    /**
     * Start background processing in dedicated slot
     */
    private suspend fun startBackgroundProcessing(queuedOp: QueuedOperation) {
        isBackgroundSlotBusy = true
        backgroundSlot = queuedOp.copy(phase = 2)

        // Launch background processing
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val bgResult = executeQueuedOperation(backgroundSlot!!)
                LogManager.coordination("Background result: status=${bgResult.status}, requiresContinuation=${bgResult.requiresContinuation}")
                // Note: executeQueuedOperation already calls handleMultiStepResult which queues phase 3
                // No need to queue it again here - just trigger queue processing
                if (bgResult.requiresContinuation) {
                    LogManager.coordination("Phase 3 already queued by handleMultiStepResult, triggering processQueue")

                    // Process the queue to execute Phase 3
                    CoroutineScope(Dispatchers.Main).launch {
                        processQueue()
                    }
                }
            } finally {
                isBackgroundSlotBusy = false
                backgroundSlot = null
            }
        }
    }
    
    
    /**
     * Generic method to execute service operations - simplified for new architecture
     */
    private suspend fun executeServiceOperation(
        command: DispatchCommand,
        service: ExecutableService,
        operation: String,
        phase: Int = 1
    ): CommandResult {
        LogManager.coordination("executeServiceOperation: operation=$operation, params=${command.params}, phase=$phase", "VERBOSE")
        val opId = command.id ?: "op_${System.currentTimeMillis()}"
        val token = CancellationToken()
        tokens[opId] = token
        
        return try {
            val params = JSONObject().apply {
                command.params.forEach { (key, value) ->
                    // Convert value to JSON-compatible type
                    val jsonValue = when (value) {
                        is List<*> -> org.json.JSONArray(value)
                        is Map<*, *> -> JSONObject(value as Map<*, *>)
                        else -> value
                    }
                    put(key, jsonValue)
                }
                put("phase", phase)
            }
            
            LogManager.coordination("Calling service.execute with params: $params", "VERBOSE")
            val result = service.execute(operation, params, token)
            LogManager.coordination("Service result: success=${result.success}, error=${result.error}, data=${result.data}, requiresContinuation=${result.requiresContinuation}", "VERBOSE")
            
            CommandResult(
                commandId = command.id,
                status = when {
                    result.cancelled -> CommandStatus.CANCELLED
                    result.success -> CommandStatus.SUCCESS
                    else -> CommandStatus.ERROR
                },
                message = if (result.success) "Operation completed successfully" else null,
                error = result.error,
                data = result.data,
                requiresBackground = result.requiresBackground,
                requiresContinuation = result.requiresContinuation
            )
        } catch (e: Exception) {
            CommandResult(
                commandId = command.id,
                status = CommandStatus.ERROR,
                error = "Service operation failed: ${e.message}"
            )
        } finally {
            tokens.remove(opId)
        }
    }

    
    /**
     * Check if a new operation can be started
     */
    fun canAcceptNewOperation(): Boolean {
        return _state.value == CoordinatorState.IDLE
    }
    
    /**
     * Get queue status info
     */
    fun getQueueInfo(): Map<String, Any> {
        return mapOf(
            "normal_queue_size" to normalQueue.size,
            "background_slot_busy" to isBackgroundSlotBusy,
            "current_state" to _state.value.name
        )
    }
}