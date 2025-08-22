package com.assistant.core.coordinator

import android.content.Context
import com.assistant.core.commands.Command
import com.assistant.core.commands.CommandResult
import com.assistant.core.commands.CommandStatus
import com.assistant.core.commands.CommandParser
import com.assistant.core.commands.ParseResult
import com.assistant.core.services.ServiceManager
import com.assistant.core.services.ZoneService
import com.assistant.core.services.ToolInstanceService
import com.assistant.core.services.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Central Coordinator - orchestrates all operations and maintains app state
 * Implements the single-operation principle: only one data-modifying operation at a time
 */
class Coordinator(context: Context) {
    private val _state = MutableStateFlow(CoordinatorState.IDLE)
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()
    
    private var currentOperation: Operation? = null
    private val operationQueue = mutableListOf<Operation>()
    private val commandParser = CommandParser()
    private val serviceManager = ServiceManager(context)
    private val tokens = ConcurrentHashMap<String, CancellationToken>()
    
    /**
     * Process user action from UI - simple interface for UI layer
     */
    suspend fun processUserAction(action: String, params: Map<String, Any> = emptyMap()): CommandResult {
        val command = convertToCommand(action, params, OperationSource.USER)
        return executeCommand(command)
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
                        executeCommand(command.copy()).copy(commandIndex = index)
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
        return executeCommand(command)
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
     * Execute a command - unified internal execution
     */
    private suspend fun executeCommand(command: Command): CommandResult {
        _state.value = CoordinatorState.OPERATION_IN_PROGRESS
        
        return try {
            // TODO: Route command to appropriate handler based on action pattern
            when {
                command.action.startsWith("execute->tools->") -> handleToolCommand(command)
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
        } catch (e: Exception) {
            CommandResult(
                commandId = command.id,
                status = CommandStatus.ERROR,
                error = "Command execution failed: ${e.message}"
            )
        } finally {
            _state.value = CoordinatorState.IDLE
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
     * Generic method to execute service operations - eliminates duplication
     */
    private suspend fun executeServiceOperation(
        command: Command,
        serviceName: String,
        operation: String
    ): CommandResult {
        val opId = command.id ?: "op_${System.currentTimeMillis()}"
        val token = CancellationToken()
        tokens[opId] = token
        
        return try {
            val service = serviceManager.getService(serviceName)
                ?: return CommandResult(
                    commandId = command.id,
                    status = CommandStatus.ERROR,
                    error = "Service not found: $serviceName"
                )
            
            val params = JSONObject().apply {
                command.params.forEach { (key, value) ->
                    put(key, value)
                }
            }
            
            val result = when (service) {
                is ZoneService -> service.execute(operation, params, token)
                is ToolInstanceService -> service.execute(operation, params, token)
                // Tool services follow same pattern - they all have execute() method
                else -> {
                    // Try to call execute method via reflection for tool services
                    try {
                        val executeMethod = service.javaClass.getMethod(
                            "execute", 
                            String::class.java, 
                            JSONObject::class.java, 
                            CancellationToken::class.java
                        )
                        executeMethod.invoke(service, operation, params, token) as OperationResult
                    } catch (e: Exception) {
                        OperationResult.error("Unknown service type or invalid service interface: $serviceName - ${e.message}")
                    }
                }
            }
            
            CommandResult(
                commandId = command.id,
                status = when {
                    result.cancelled -> CommandStatus.CANCELLED
                    result.success -> CommandStatus.SUCCESS
                    else -> CommandStatus.ERROR
                },
                message = result.error ?: "Operation completed successfully",
                data = result.data
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
            command.action == "create->tracking_data" -> executeServiceOperation(command, "tracking_service", "create")
            else -> CommandResult(
                commandId = command.id,
                status = CommandStatus.SUCCESS,
                message = "Create command (TODO: implement other types)"
            )
        }
    }
    
    private suspend fun handleGetCommand(command: Command): CommandResult {
        return CommandResult(
            commandId = command.id,
            status = CommandStatus.SUCCESS,
            message = "Get command (TODO: implement)"
        )
    }
    
    private suspend fun handleUpdateCommand(command: Command): CommandResult {
        return when {
            command.action == "update->zone" -> executeServiceOperation(command, "zone_service", "update")
            command.action == "update->tool_instance" -> executeServiceOperation(command, "tool_instance_service", "update")
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
     * Get current operation info
     */
    fun getCurrentOperation(): Operation? = currentOperation
}