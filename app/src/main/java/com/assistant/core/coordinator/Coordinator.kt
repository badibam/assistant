package com.assistant.core.coordinator

import android.content.Context
import android.util.Log
import com.assistant.core.commands.Command
import com.assistant.core.commands.CommandResult
import com.assistant.core.commands.CommandStatus
import com.assistant.core.commands.CommandParser
import com.assistant.core.commands.ParseResult
import com.assistant.core.services.ServiceManager
import com.assistant.core.services.ZoneService
import com.assistant.core.services.ToolInstanceService
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.ToolInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.assistant.core.coordinator.CancellationToken
import java.util.ArrayDeque

/**
 * Represents a queued operation with its execution context
 */
data class QueuedOperation(
    val command: Command,
    val operationId: String = UUID.randomUUID().toString(),
    val phase: Int = 1,
    val source: OperationSource
)

/**
 * Central Coordinator - orchestrates all operations and maintains app state
 * Implements multi-step operations with background processing slot
 */
class Coordinator(context: Context) {
    private val _state = MutableStateFlow(CoordinatorState.IDLE)
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()
    
    // Queue system
    private val normalQueue = ArrayDeque<QueuedOperation>()
    private var backgroundSlot: QueuedOperation? = null
    private var isBackgroundSlotBusy = false
    
    private val commandParser = CommandParser()
    private val serviceManager = ServiceManager(context)
    private val tokens = ConcurrentHashMap<String, CancellationToken>()
    private val database = AppDatabase.getDatabase(context)
    
    /**
     * Process user action from UI - simple interface for UI layer
     */
    suspend fun processUserAction(action: String, params: Map<String, Any> = emptyMap()): CommandResult {
        val command = convertToCommand(action, params, OperationSource.USER)
        val queuedOp = QueuedOperation(command, source = OperationSource.USER)
        return enqueueAndProcess(queuedOp)
    }
    
    /**
     * Process AI command from JSON interface - handles raw JSON from AI
     */
    suspend fun processAICommand(json: String): List<CommandResult> {
        return try {
            // Parse JSON to commands
            val parseResult = commandParser.parseCommands(json)
            
            when (parseResult) {
                is ParseResult.Success -> {
                    // Execute all commands from AI
                    parseResult.data.mapIndexed { index, command ->
                        val queuedOp = QueuedOperation(command, source = OperationSource.AI)
                        enqueueAndProcess(queuedOp).copy(commandIndex = index)
                    }
                }
                is ParseResult.Failure -> {
                    listOf(CommandResult(
                        status = CommandStatus.INVALID_FORMAT,
                        error = parseResult.error
                    ))
                }
            }
        } catch (e: Exception) {
            listOf(CommandResult(
                status = CommandStatus.ERROR,
                error = "Failed to process AI command: ${e.message}"
            ))
        }
    }
    
    /**
     * Process scheduled task - simple interface for scheduler
     */
    suspend fun processScheduledTask(task: String, params: Map<String, Any> = emptyMap()): CommandResult {
        val command = convertToCommand(task, params, OperationSource.SCHEDULER)
        val queuedOp = QueuedOperation(command, source = OperationSource.SCHEDULER)
        return enqueueAndProcess(queuedOp)
    }
    
    /**
     * Convert action/params to Command object (internal translation)
     */
    private fun convertToCommand(action: String, params: Map<String, Any>, source: OperationSource): Command {
        return Command(
            action = action,
            params = params,
            id = null,
            description = null, // Only AI provides description
            reason = null       // Only AI provides reason
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
            
            val result = when {
                command.action.startsWith("execute->tools->") -> handleToolCommand(command)
                command.action.startsWith("execute->service->") -> handleServiceCommand(command)
                command.action.startsWith("create->") -> handleCreateCommand(command)
                command.action.startsWith("get->") -> handleGetCommand(command)
                command.action.startsWith("update->") -> handleUpdateCommand(command)
                command.action.startsWith("delete->") -> handleDeleteCommand(command)
                else -> CommandResult(
                    commandId = command.id,
                    status = CommandStatus.UNKNOWN_ACTION,
                    error = "Unknown action pattern: ${command.action}"
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
                Log.d("Coordinator", "Background result: status=${bgResult.status}, requiresContinuation=${bgResult.requiresContinuation}")
                if (bgResult.requiresContinuation) {
                    // Queue final step
                    val finalStep = queuedOp.copy(phase = 3)
                    normalQueue.addLast(finalStep)
                    Log.d("Coordinator", "Phase 3 queued: ${finalStep.command.action} phase=${finalStep.phase}")
                    
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
    
    // TODO: Implement command handlers
    private suspend fun handleToolCommand(command: Command): CommandResult {
        return CommandResult(
            commandId = command.id,
            status = CommandStatus.SUCCESS,
            message = "Tool command executed (TODO: implement actual routing)"
        )
    }
    
    /**
     * Handle direct service commands: execute->service->service_name->operation
     */
    private suspend fun handleServiceCommand(command: Command): CommandResult {
        // Parse: execute->service->icon_preload_service->preload_theme_icons
        val parts = command.action.split("->")
        if (parts.size != 4) {
            return CommandResult(
                commandId = command.id,
                status = CommandStatus.ERROR,
                error = "Invalid service command format. Expected: execute->service->service_name->operation"
            )
        }
        
        val serviceName = parts[2]
        val operation = parts[3]
        
        return executeServiceOperation(command, serviceName, operation)
    }
    
    /**
     * Generic method to execute service operations - eliminates duplication
     */
    private suspend fun executeServiceOperation(
        command: Command,
        serviceName: String,
        operation: String
    ): CommandResult {
        android.util.Log.d("Coordinator", "executeServiceOperation: service=$serviceName, operation=$operation, params=${command.params}")
        val opId = command.id ?: "op_${System.currentTimeMillis()}"
        val token = CancellationToken()
        tokens[opId] = token
        
        return try {
            val service = serviceManager.getService(serviceName)
            android.util.Log.d("Coordinator", "Service retrieved: $service")
            if (service == null) {
                android.util.Log.e("Coordinator", "Service not found: $serviceName")
                return CommandResult(
                    commandId = command.id,
                    status = CommandStatus.ERROR,
                    error = "Service not found: $serviceName"
                )
            }
            
            val params = JSONObject().apply {
                command.params.forEach { (key, value) ->
                    put(key, value)
                }
            }
            
            android.util.Log.d("Coordinator", "Calling service.execute with params: $params")
            val result = when (service) {
                is ZoneService -> service.execute(operation, params, token)
                is ToolInstanceService -> service.execute(operation, params, token)
                is ExecutableService -> service.execute(operation, params, token)
                else -> OperationResult.error("Service does not implement ExecutableService interface: $serviceName")
            }
            android.util.Log.d("Coordinator", "Service result: success=${result.success}, error=${result.error}, data=${result.data}, requiresContinuation=${result.requiresContinuation}")
            
            CommandResult(
                commandId = command.id,
                status = when {
                    result.cancelled -> CommandStatus.CANCELLED
                    result.success -> CommandStatus.SUCCESS
                    else -> CommandStatus.ERROR
                },
                message = result.error ?: "Operation completed successfully",
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

    private suspend fun handleCreateCommand(command: Command): CommandResult {
        return when {
            command.action == "create->zone" -> executeServiceOperation(command, "zone_service", "create")
            command.action == "create->tool_instance" -> executeServiceOperation(command, "tool_instance_service", "create")
            command.action == "create->tool_data" -> executeServiceOperation(command, "tool_data_service", "create")
            else -> CommandResult(
                commandId = command.id,
                status = CommandStatus.SUCCESS,
                message = "Create command (TODO: implement other types)"
            )
        }
    }
    
    private suspend fun handleGetCommand(command: Command): CommandResult {
        return when {
            command.action == "get->zones" -> executeServiceOperation(command, "zone_service", "get_all")
            command.action == "get->tool_instances" -> executeServiceOperation(command, "tool_instance_service", "get_by_zone")
            command.action == "get->tool_instance" -> executeServiceOperation(command, "tool_instance_service", "get_by_id")
            command.action == "get->tool_data" -> {
                val operation = command.params["operation"] as? String ?: "get_entries"
                executeServiceOperation(command, "tool_data_service", operation)
            }
            command.action == "get->app_config" -> {
                Log.d("CONFIGDEBUG", "Coordinator handling get->app_config command: ${command.params}")
                executeServiceOperation(command, "app_config_service", "get_config")
            }
            else -> CommandResult(
                commandId = command.id,
                status = CommandStatus.SUCCESS,
                message = "Get command (TODO: implement other types)"
            )
        }
    }
    
    private suspend fun handleUpdateCommand(command: Command): CommandResult {
        return when {
            command.action == "update->zone" -> executeServiceOperation(command, "zone_service", "update")
            command.action == "update->tool_instance" -> executeServiceOperation(command, "tool_instance_service", "update")
            command.action == "update->tool_data" -> executeServiceOperation(command, "tool_data_service", "update")
            else -> CommandResult(
                commandId = command.id,
                status = CommandStatus.SUCCESS,
                message = "Update command (TODO: implement other types)"
            )
        }
    }
    
    private suspend fun handleDeleteCommand(command: Command): CommandResult {
        return when {
            command.action == "delete->zone" -> executeServiceOperation(command, "zone_service", "delete")
            command.action == "delete->tool_instance" -> executeServiceOperation(command, "tool_instance_service", "delete")
            command.action == "delete->tool_data" -> {
                val operation = command.params["operation"] as? String ?: "delete"
                executeServiceOperation(command, "tool_data_service", operation)
            }
            else -> CommandResult(
                commandId = command.id,
                status = CommandStatus.SUCCESS,
                message = "Delete command (TODO: implement other types)"
            )
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