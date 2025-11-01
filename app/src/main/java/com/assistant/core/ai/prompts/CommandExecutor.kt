package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.ServiceRegistry
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.ExecutableService
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of executing a single command for prompt formatting
 */
data class PromptCommandResult(
    val dataTitle: String,         // Title/header for data section in prompt
    val formattedData: String      // JSON formatted data for prompt
)

/**
 * Complete execution result including prompt data and system message
 */
data class CommandExecutionResult(
    val promptResults: List<PromptCommandResult>,  // For prompt inclusion
    val systemMessage: SystemMessage                // For conversation history
)

/**
 * Executes ExecutableCommands and formats results for prompt inclusion and conversation history
 *
 * Unified command executor used by:
 * - PromptManager (user enrichments Level 2/4)
 * - AICommandProcessor (AI data/action commands)
 *
 * Core responsibilities:
 * - Execute ExecutableCommand configurations via coordinator
 * - Format results as JSON with metadata first for prompt inclusion
 * - Generate SystemMessage with aggregate results for conversation history
 * - Track success/failure status for each command
 * - Deduplicate schema queries across session history and within current batch
 */
class CommandExecutor(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val validator = ActionValidator(context)
    private val s = Strings.`for`(context = context)

    /**
     * Execute commands and return complete result with prompt data + SystemMessage
     *
     * @param commands The commands to execute
     * @param messageType Type of SystemMessage (DATA_ADDED or ACTIONS_EXECUTED)
     * @param level The level name for logging purposes
     * @param sessionId Session ID for schema deduplication (null = no deduplication)
     * @return CommandExecutionResult with prompt data and system message
     */
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        messageType: SystemMessageType,
        level: String = "unknown",
        sessionId: String? = null
    ): CommandExecutionResult {
        LogManager.aiPrompt("CommandExecutor executing ${commands.size} commands for $level (sessionId=$sessionId)", "DEBUG")

        // Load historical schemas if sessionId provided (for deduplication)
        val historicalSchemas = if (sessionId != null) {
            loadHistoricalSchemas(sessionId)
        } else {
            emptySet()
        }
        LogManager.aiPrompt("Loaded ${historicalSchemas.size} historical schemas for deduplication", "DEBUG")

        // Track schemas executed in current batch (for intra-batch deduplication)
        val currentBatchSchemas = mutableSetOf<String>()

        if (commands.isEmpty()) {
            LogManager.aiPrompt("No commands to execute, returning empty result", "DEBUG")
            return CommandExecutionResult(
                promptResults = emptyList(),
                systemMessage = SystemMessage(
                    type = messageType,
                    commandResults = emptyList(),
                    summary = s.shared("ai_system_no_commands")
                )
            )
        }

        val promptResults = mutableListOf<PromptCommandResult>()
        val commandResults = mutableListOf<com.assistant.core.ai.data.CommandResult>()
        var successCount = 0
        var failedCount = 0

        for ((index, command) in commands.withIndex()) {
            LogManager.aiPrompt("Executing command ${index + 1}/${commands.size}: ${command.resource}.${command.operation}", "DEBUG")

            // Check for schema deduplication BEFORE execution
            if (command.resource == "schemas" && command.operation == "get" && sessionId != null) {
                val schemaId = command.params["id"] as? String
                if (schemaId != null) {
                    // Check inter-message deduplication (historical)
                    val isDuplicatedFromHistory = schemaId in historicalSchemas

                    // Check intra-message deduplication (current batch)
                    val isDuplicatedInBatch = schemaId in currentBatchSchemas

                    if (isDuplicatedFromHistory || isDuplicatedInBatch) {
                        // Schema already retrieved - create CACHED result
                        LogManager.aiPrompt("Schema $schemaId already included - creating CACHED result", "DEBUG")

                        val cachedResult = InternalCommandResult(
                            promptResult = PromptCommandResult("", ""),  // No prompt data for cached
                            commandResult = com.assistant.core.ai.data.CommandResult(
                                command = "${command.resource}.${command.operation}",
                                status = CommandStatus.CACHED,
                                details = s.shared("ai_schema_already_included").format(schemaId),
                                data = null,
                                error = null,
                                isActionCommand = false  // schemas.get is a query
                            )
                        )

                        promptResults.add(cachedResult.promptResult)
                        commandResults.add(cachedResult.commandResult)
                        successCount++
                        LogManager.aiPrompt("Command ${index + 1} cached (schema $schemaId)", "DEBUG")
                        continue  // Skip execution, move to next command
                    } else {
                        // Not a duplicate - will execute normally, add to current batch tracker
                        currentBatchSchemas.add(schemaId)
                    }
                }
            }

            // Execute command normally (not a duplicate or not a schema)
            val result = executeCommand(command)

            if (result != null) {
                promptResults.add(result.promptResult)
                commandResults.add(result.commandResult)
                if (result.commandResult.status == CommandStatus.SUCCESS) {
                    successCount++
                    LogManager.aiPrompt("Command ${index + 1} succeeded", "DEBUG")
                } else {
                    failedCount++
                    LogManager.aiPrompt("Command ${index + 1} failed: ${result.commandResult.details}", "WARN")
                }
            } else {
                failedCount++
                commandResults.add(
                    com.assistant.core.ai.data.CommandResult(
                        command = "${command.resource}.${command.operation}",
                        status = CommandStatus.FAILED,
                        details = "Execution failed",
                        data = null,
                        error = "Internal execution error",
                        isActionCommand = command.isActionCommand
                    )
                )
                LogManager.aiPrompt("Command ${index + 1} failed - continuing with remaining commands", "WARN")
            }
        }

        // Generate summary based on message type
        val summary = generateSummary(messageType, successCount, failedCount)

        LogManager.aiPrompt("CommandExecutor completed for $level: $successCount succeeded, $failedCount failed", "INFO")

        return CommandExecutionResult(
            promptResults = promptResults,
            systemMessage = SystemMessage(
                type = messageType,
                commandResults = commandResults,
                summary = summary
            )
        )
    }

    /**
     * Internal result combining prompt data and command status
     */
    private data class InternalCommandResult(
        val promptResult: PromptCommandResult,
        val commandResult: com.assistant.core.ai.data.CommandResult
    )

    /**
     * Execute a single ExecutableCommand through coordinator
     *
     * Routes to coordinator using resource.operation pattern and returns
     * InternalCommandResult with prompt data and command status.
     *
     * Validates action commands before execution via ActionValidator.
     */
    private suspend fun executeCommand(command: ExecutableCommand): InternalCommandResult? {
        LogManager.aiPrompt("Executing ExecutableCommand: resource=${command.resource}, operation=${command.operation}, isActionCommand=${command.isActionCommand}", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                val commandString = "${command.resource}.${command.operation}"

                // Validate command before execution
                val validationResult = validator.validate(command)
                if (!validationResult.isValid) {
                    val errorMessage = validationResult.errorMessage ?: s.shared("message_validation_error_simple")
                    LogManager.aiPrompt("Command validation failed: $errorMessage", "WARN")

                    // Get verbalized description (even for validation failures)
                    val verbalizedDescription = getVerbalizedDescription(command)

                    return@withContext InternalCommandResult(
                        promptResult = PromptCommandResult("", ""),
                        commandResult = com.assistant.core.ai.data.CommandResult(
                            command = commandString,
                            status = CommandStatus.FAILED,
                            details = verbalizedDescription,
                            data = null,
                            error = errorMessage,
                            isActionCommand = command.isActionCommand
                        )
                    )
                }

                val paramsMap = command.params

                LogManager.aiPrompt("Calling coordinator: $commandString with ${paramsMap.size} params", "VERBOSE")

                // Note: Coordinator.executeServiceOperation() handles recursive Map→JSON conversion
                val result = coordinator.processUserAction(commandString, paramsMap)

                if (result.isSuccess) {
                    val data = result.data ?: emptyMap()

                    // For actions, data may be empty or minimal (just success/ID)
                    // For queries, empty data is unusual
                    if (data.isEmpty() && !command.isActionCommand) {
                        LogManager.aiPrompt("Query succeeded but returned empty data", "WARN")

                        // Generate description for empty query result
                        val dataTitle = generateDataTitle(command, data)
                        val description = dataTitle.ifEmpty { s.shared("ai_system_query_no_data") }

                        return@withContext InternalCommandResult(
                            promptResult = PromptCommandResult("", ""),
                            commandResult = com.assistant.core.ai.data.CommandResult(
                                command = commandString,
                                status = CommandStatus.SUCCESS,
                                details = description,
                                data = null,
                                error = null
                            )
                        )
                    }

                    val dataTitle = generateDataTitle(command, data)
                    val formattedData = if (command.isActionCommand) "" else formatResultData(command, data)

                    // Get verbalized description for all commands
                    // - Action commands: use service verbalization (e.g., "Création de la zone \"Santé\"")
                    // - Query commands: use generated data title (e.g., "Data from tool 'Poids' (5 records)")
                    val verbalizedDescription = if (command.isActionCommand) {
                        // Enrich params with name from result for delete operations
                        // This allows verbalize() to access the name even after the entity is deleted
                        val enrichedCommand = if (command.operation == "delete" && data.containsKey("name")) {
                            command.copy(params = command.params + ("name" to data["name"]!!))
                        } else {
                            command
                        }
                        getVerbalizedDescription(enrichedCommand)
                    } else {
                        dataTitle.ifEmpty { null }
                    }

                    // Store data appropriately based on command type:
                    // - Actions: filter to minimal data (ID, name)
                    // - Queries: filter to essential metadata (exclude large content fields like schema.content)
                    //   Keeps only what's needed for deduplication (e.g., schema_id) to avoid DB bloat
                    val storedData = if (command.isActionCommand) {
                        filterActionResultData(command.operation, data)
                    } else {
                        filterQueryResultData(command.resource, data)
                    }

                    LogManager.aiPrompt("Command succeeded", "DEBUG")
                    val cmdResult = com.assistant.core.ai.data.CommandResult(
                        command = commandString,
                        status = CommandStatus.SUCCESS,
                        details = verbalizedDescription,
                        data = storedData,
                        error = null,
                        isActionCommand = command.isActionCommand
                    )
                    LogManager.aiPrompt("Created CommandResult: command=$commandString, isActionCommand=${cmdResult.isActionCommand}, data=${cmdResult.data}", "DEBUG")
                    return@withContext InternalCommandResult(
                        promptResult = PromptCommandResult(dataTitle, formattedData),
                        commandResult = cmdResult
                    )
                } else {
                    LogManager.aiPrompt("Command failed: ${result.error}", "WARN")

                    // Get verbalized description (even for failures)
                    val verbalizedDescription = getVerbalizedDescription(command)

                    return@withContext InternalCommandResult(
                        promptResult = PromptCommandResult("", ""),
                        commandResult = com.assistant.core.ai.data.CommandResult(
                            command = commandString,
                            status = CommandStatus.FAILED,
                            details = verbalizedDescription,
                            data = null,
                            error = result.error,
                            isActionCommand = command.isActionCommand
                        )
                    )
                }

            } catch (e: Exception) {
                LogManager.aiPrompt("Command execution failed: ${e.message}", "ERROR", e)
                return@withContext null
            }
        }
    }

    /**
     * Get verbalized description from service for action command
     * Returns substantive form description (e.g., "Création de la zone \"Santé\"")
     */
    private fun getVerbalizedDescription(command: ExecutableCommand): String {
        return try {
            val serviceRegistry = ServiceRegistry(context)
            val service = serviceRegistry.getService(command.resource)

            if (service is ExecutableService) {
                val paramsJson = org.json.JSONObject(command.params)
                service.verbalize(command.operation, paramsJson, context)
            } else {
                // Fallback if service doesn't implement ExecutableService
                "${command.resource}.${command.operation}"
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to verbalize command ${command.resource}.${command.operation}: ${e.message}", "WARN", e)
            "${command.resource}.${command.operation}"
        }
    }

    /**
     * Filter action result data according to rules:
     * - create/update: keep only name + id fields
     * - delete: return null (no data needed)
     * - batch_*: keep only count fields (*_count)
     * - Remove all timestamp fields (created_at, updated_at, deleted_at, createdAt, updatedAt)
     */
    private fun filterActionResultData(operation: String, data: Map<String, Any>): Map<String, Any>? {
        return when (operation) {
            "delete" -> {
                // Delete operations: no data needed
                null
            }
            "create", "update" -> {
                // Create/Update: keep only name + id fields (zone_id, tool_instance_id, id, name, tooltype)
                val filtered = mutableMapOf<String, Any>()

                // ID fields (various naming patterns)
                data["zone_id"]?.let { filtered["zone_id"] = it }
                data["tool_instance_id"]?.let { filtered["tool_instance_id"] = it }
                data["id"]?.let { filtered["id"] = it }

                // Name/type fields
                data["name"]?.let { filtered["name"] = it }
                data["tooltype"]?.let { filtered["tooltype"] = it }

                if (filtered.isEmpty()) null else filtered
            }
            "batch_create", "batch_update", "batch_delete" -> {
                // Batch operations: keep only count fields
                val filtered = mutableMapOf<String, Any>()

                data["created_count"]?.let { filtered["created_count"] = it }
                data["failed_count"]?.let { filtered["failed_count"] = it }
                data["updated_count"]?.let { filtered["updated_count"] = it }
                data["deleted_count"]?.let { filtered["deleted_count"] = it }

                if (filtered.isEmpty()) null else filtered
            }
            else -> {
                // Unknown operation: pass through without filtering
                data
            }
        }
    }

    /**
     * Filter query result data to essential metadata (avoid DB bloat)
     *
     * Removes large content fields while keeping identifiers needed for deduplication:
     * - schemas: keep schema_id only (remove large 'content' JSON)
     * - tools/zones: keep id, name, count fields
     * - Remove large nested objects and arrays
     */
    private fun filterQueryResultData(resource: String, data: Map<String, Any>): Map<String, Any>? {
        return when (resource) {
            "schemas" -> {
                // Schemas: keep only schema_id for deduplication, remove large 'content'
                val filtered = mutableMapOf<String, Any>()
                data["schema_id"]?.let { filtered["schema_id"] = it }
                if (filtered.isEmpty()) null else filtered
            }
            "zones", "tools", "tool_data" -> {
                // Tools/Zones/Data: keep IDs, names, counts - remove large objects/arrays
                val filtered = mutableMapOf<String, Any>()

                // ID fields
                data["id"]?.let { filtered["id"] = it }
                data["zone_id"]?.let { filtered["zone_id"] = it }
                data["tool_instance_id"]?.let { filtered["tool_instance_id"] = it }

                // Name/type fields
                data["name"]?.let { filtered["name"] = it }
                data["tool_type"]?.let { filtered["tool_type"] = it }
                data["tooltype"]?.let { filtered["tooltype"] = it }

                // Count fields
                data["count"]?.let { filtered["count"] = it }
                data["total_count"]?.let { filtered["total_count"] = it }

                if (filtered.isEmpty()) null else filtered
            }
            else -> {
                // Unknown resource: filter conservatively (keep scalar values only)
                val filtered = mutableMapOf<String, Any>()
                data.forEach { (key, value) ->
                    // Keep only scalar values (not maps or lists)
                    if (value !is Map<*, *> && value !is List<*> && value !is Array<*>) {
                        filtered[key] = value
                    }
                }
                if (filtered.isEmpty()) null else filtered
            }
        }
    }

    /**
     * Generate human-readable summary for SystemMessage
     */
    private fun generateSummary(type: SystemMessageType, successCount: Int, failedCount: Int): String {
        return when (type) {
            SystemMessageType.DATA_ADDED -> {
                when {
                    failedCount == 0 -> s.shared("ai_system_queries_success").format(successCount)
                    successCount == 0 -> s.shared("ai_system_queries_all_failed").format(failedCount)
                    else -> s.shared("ai_system_queries_partial").format(successCount, failedCount)
                }
            }
            SystemMessageType.ACTIONS_EXECUTED -> {
                when {
                    failedCount == 0 -> s.shared("ai_system_actions_success").format(successCount)
                    successCount == 0 -> s.shared("ai_system_actions_all_failed").format(failedCount)
                    else -> s.shared("ai_system_actions_partial").format(successCount, failedCount)
                }
            }
            SystemMessageType.LIMIT_REACHED -> {
                // LIMIT_REACHED messages should provide their own summary directly
                // This case should not be reached in normal flow
                "Limit reached"
            }
            SystemMessageType.FORMAT_ERROR -> {
                // FORMAT_ERROR messages provide their own summary with error details
                // This case should not be reached in normal flow
                "Format error"
            }
            SystemMessageType.COMMUNICATION_CANCELLED -> {
                // COMMUNICATION_CANCELLED messages provide their own summary directly
                // This case should not be reached in normal flow
                "Communication cancelled"
            }
            SystemMessageType.VALIDATION_CANCELLED -> {
                // VALIDATION_CANCELLED messages provide their own summary directly
                // This case should not be reached in normal flow
                "Validation cancelled"
            }
            SystemMessageType.COMPLETED_CONFIRMATION -> {
                // COMPLETED_CONFIRMATION messages provide their own summary directly
                // This case should not be reached in normal flow
                "Completion confirmation request"
            }
            SystemMessageType.NETWORK_ERROR, SystemMessageType.SESSION_TIMEOUT, SystemMessageType.INTERRUPTED, SystemMessageType.PROVIDER_ERROR -> {
                // These messages should never reach here (filtered from prompts, audit only)
                // But provide fallback just in case
                s.shared("ai_error_system_generic")
            }
        }
    }

    /**
     * Generate title/header for data section in prompt
     * Creates descriptive title with context (tool name, filters, period, etc.)
     * Returns empty string for action commands
     *
     * IMPORTANT: Always indicates result count (even if 0) and exact query parameters
     * for AI to trust the data and avoid redundant queries
     */
    private suspend fun generateDataTitle(command: ExecutableCommand, data: Map<String, Any>): String {
        // No title needed for action commands
        if (command.operation in listOf("create", "update", "delete", "batch_create", "batch_update", "batch_delete")) {
            return ""
        }

        return try {
            when (command.resource) {
                "tool_data" -> {
                    // Resolve tool instance name from ID in command params
                    // Note: UserCommandProcessor transforms "id" → "toolInstanceId"
                    LogManager.aiPrompt("tool_data command params keys: ${command.params.keys}", "VERBOSE")
                    LogManager.aiPrompt("toolInstanceId=${command.params["toolInstanceId"]}, id=${command.params["id"]}", "VERBOSE")

                    val toolInstanceId = command.params["toolInstanceId"] as? String
                        ?: command.params["id"] as? String

                    LogManager.aiPrompt("Resolved toolInstanceId: $toolInstanceId", "VERBOSE")

                    val toolName = if (toolInstanceId != null) {
                        val name = resolveToolInstanceName(toolInstanceId)
                        LogManager.aiPrompt("Resolved tool name: $name", "VERBOSE")
                        name
                    } else {
                        LogManager.aiPrompt("No toolInstanceId found in params!", "WARN")
                        "unknown tool"
                    }

                    val count = data["count"] as? Int
                        ?: (data["entries"] as? List<*>)?.size
                        ?: 0

                    // Build comprehensive header with ALL query details
                    val headerParts = mutableListOf<String>()

                    // Main title with result count (ALWAYS shown, even if 0)
                    headerParts.add("=== ${s.shared("ai_data_header_trusted")}: ${s.shared("ai_data_header_tool").format(toolName)} ===")
                    headerParts.add(s.shared("ai_data_result_count").format(count))

                    // Period info if present
                    val startTime = command.params["startTime"] as? Long
                    val endTime = command.params["endTime"] as? Long
                    if (startTime != null && endTime != null) {
                        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(startTime))
                        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(endTime))
                        headerParts.add(s.shared("ai_data_period_range").format(startDate, endDate))
                    } else if (startTime != null) {
                        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(startTime))
                        headerParts.add(s.shared("ai_data_period_from").format(startDate))
                    } else if (endTime != null) {
                        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(endTime))
                        headerParts.add(s.shared("ai_data_period_until").format(endDate))
                    } else {
                        headerParts.add(s.shared("ai_data_period_all_time"))
                    }

                    val limit = command.params["limit"] as? Int
                    if (limit != null) {
                        headerParts.add(s.shared("ai_data_limit").format(limit))
                    }

                    val offset = command.params["offset"] as? Int
                    if (offset != null) {
                        headerParts.add(s.shared("ai_data_offset").format(offset))
                    }

                    // Build exact query as AI would write it (TOOL_DATA format)
                    val aiQueryParams = mutableMapOf<String, Any>()
                    aiQueryParams["id"] = toolInstanceId ?: "unknown"
                    if (startTime != null || endTime != null) {
                        val period = mutableMapOf<String, Any>()
                        if (startTime != null) period["start"] = startTime
                        if (endTime != null) period["end"] = endTime
                        aiQueryParams["period"] = period
                    }
                    if (limit != null) aiQueryParams["limit"] = limit
                    if (offset != null) aiQueryParams["offset"] = offset

                    val aiQueryJson = org.json.JSONObject(aiQueryParams as Map<*, *>).toString()
                    headerParts.add(s.shared("ai_data_exact_query").format("TOOL_DATA", aiQueryJson))

                    // Fields included (standard fields for tool_data)
                    headerParts.add(s.shared("ai_data_fields_tool_data"))

                    // Confidence message
                    if (count == 0) {
                        headerParts.add(s.shared("ai_data_no_results_warning"))
                    } else {
                        headerParts.add(s.shared("ai_data_complete_dataset"))
                    }

                    headerParts.joinToString("\n")
                }
                "tool_executions" -> {
                    // Resolve tool instance name from ID in command params
                    val toolInstanceId = command.params["toolInstanceId"] as? String
                        ?: command.params["id"] as? String

                    val toolName = if (toolInstanceId != null) {
                        resolveToolInstanceName(toolInstanceId)
                    } else {
                        "unknown tool"
                    }

                    val count = data["count"] as? Int
                        ?: (data["executions"] as? List<*>)?.size
                        ?: 0

                    // Build comprehensive header with ALL query details
                    val headerParts = mutableListOf<String>()

                    // Main title with result count (ALWAYS shown, even if 0)
                    headerParts.add("=== ${s.shared("ai_data_header_trusted")}: ${s.shared("ai_data_header_executions").format(toolName)} ===")
                    headerParts.add(s.shared("ai_data_result_count_executions").format(count))

                    // Period info if present (on execution_time field)
                    val startTime = command.params["startTime"] as? Long
                    val endTime = command.params["endTime"] as? Long
                    if (startTime != null && endTime != null) {
                        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(startTime))
                        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(endTime))
                        headerParts.add(s.shared("ai_data_period_range").format(startDate, endDate))
                    } else if (startTime != null) {
                        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(startTime))
                        headerParts.add(s.shared("ai_data_period_from").format(startDate))
                    } else if (endTime != null) {
                        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(endTime))
                        headerParts.add(s.shared("ai_data_period_until").format(endDate))
                    } else {
                        headerParts.add(s.shared("ai_data_period_all_time"))
                    }

                    // Status filter if present
                    val status = command.params["status"] as? String
                    if (status != null) {
                        headerParts.add(s.shared("ai_data_status_filter").format(status))
                    }

                    // Template filter if present
                    val templateDataId = command.params["templateDataId"] as? String
                    if (templateDataId != null) {
                        headerParts.add(s.shared("ai_data_template_filter").format(templateDataId))
                    }

                    val limit = command.params["limit"] as? Int
                    if (limit != null) {
                        headerParts.add(s.shared("ai_data_limit").format(limit))
                    }

                    val offset = command.params["offset"] as? Int
                    if (offset != null) {
                        headerParts.add(s.shared("ai_data_offset").format(offset))
                    }

                    // Build exact query as AI would write it (TOOL_EXECUTIONS format)
                    val aiQueryParams = mutableMapOf<String, Any>()
                    aiQueryParams["id"] = toolInstanceId ?: "unknown"
                    if (startTime != null || endTime != null) {
                        val period = mutableMapOf<String, Any>()
                        if (startTime != null) period["start"] = startTime
                        if (endTime != null) period["end"] = endTime
                        aiQueryParams["period"] = period
                    }
                    if (status != null) aiQueryParams["status"] = status
                    if (templateDataId != null) aiQueryParams["templateDataId"] = templateDataId
                    if (limit != null) aiQueryParams["limit"] = limit
                    if (offset != null) aiQueryParams["offset"] = offset

                    val aiQueryJson = org.json.JSONObject(aiQueryParams as Map<*, *>).toString()
                    headerParts.add(s.shared("ai_data_exact_query").format("TOOL_EXECUTIONS", aiQueryJson))

                    // Fields included
                    headerParts.add(s.shared("ai_data_fields_executions"))

                    // Confidence message
                    if (count == 0) {
                        headerParts.add(s.shared("ai_data_no_results_executions_warning"))
                    } else {
                        headerParts.add(s.shared("ai_data_complete_dataset"))
                    }

                    headerParts.joinToString("\n")
                }
                "schemas" -> {
                    val schemaId = data["schema_id"] as? String ?: "unknown"
                    "Schema: $schemaId"
                }
                "tools" -> {
                    when (command.operation) {
                        "get" -> {
                            // ToolInstanceService.handleGetById returns data["tool_instance"]["name"]
                            val toolInstance = data["tool_instance"] as? Map<*, *>
                            val toolName = toolInstance?.get("name") as? String ?: "unknown"
                            "Configuration for tool '$toolName'"
                        }
                        "list" -> {
                            val zoneId = command.params["zone_id"] as? String
                            if (zoneId != null) {
                                val zoneName = resolveZoneName(zoneId)
                                "Tool instances in zone '$zoneName'"
                            } else {
                                "All tool instances"
                            }
                        }
                        "list_all" -> "All tool instances"
                        else -> ""
                    }
                }
                "zones" -> {
                    when (command.operation) {
                        "get" -> {
                            // ZoneService.handleGet returns data["zone"]["name"]
                            val zone = data["zone"] as? Map<*, *>
                            val zoneName = zone?.get("name") as? String ?: "unknown"
                            "Configuration for zone '$zoneName'"
                        }
                        "list" -> "All zones"
                        else -> ""
                    }
                }
                else -> {
                    "Data from ${command.resource}.${command.operation}"
                }
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to generate data title: ${e.message}", "WARN")
            ""
        }
    }

    /**
     * Resolve tool instance name from ID via coordinator
     */
    private suspend fun resolveToolInstanceName(toolInstanceId: String): String {
        return try {
            val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))

            if (result.isSuccess) {
                // tools.get returns { "tool_instance": { "name": "...", ... } }
                val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
                val name = toolInstance?.get("name") as? String
                name ?: "unknown tool"
            } else {
                LogManager.aiPrompt("Failed to resolve tool instance name for $toolInstanceId: ${result.error}", "WARN")
                "unknown tool"
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Error resolving tool instance name: ${e.message}", "WARN", e)
            "unknown tool"
        }
    }

    /**
     * Resolve zone name from ID via coordinator
     */
    private suspend fun resolveZoneName(zoneId: String): String {
        return try {
            val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
            if (result.isSuccess) {
                // zones.get returns { "zone": { "name": "...", ... } }
                val zone = result.data?.get("zone") as? Map<*, *>
                val name = zone?.get("name") as? String
                name ?: "unknown zone"
            } else {
                LogManager.aiPrompt("Failed to resolve zone name for $zoneId: ${result.error}", "WARN")
                "unknown zone"
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Error resolving zone name: ${e.message}", "WARN")
            "unknown zone"
        }
    }

    /**
     * Format result data as JSON with metadata first
     * Reorganizes data to put important metadata before bulk data
     */
    private fun formatResultData(command: ExecutableCommand, data: Map<String, Any>): String {
        return try {
            val reordered = mutableMapOf<String, Any>()

            // Extract metadata keys first based on command type
            when (command.resource) {
                "tool_data" -> {
                    // Metadata: toolInstanceName, count
                    // Bulk data: entries (with parsed data JSON)
                    data["toolInstanceName"]?.let { reordered["toolInstanceName"] = it }
                    data["count"]?.let { reordered["count"] = it }

                    // Parse data field in each entry from string to JSON
                    val entries = data["entries"] as? List<*>
                    if (entries != null) {
                        val parsedEntries = entries.map { entry ->
                            val entryMap = entry as? Map<*, *>
                            if (entryMap != null) {
                                val dataStr = entryMap["data"] as? String
                                if (dataStr != null) {
                                    try {
                                        val parsedData = org.json.JSONObject(dataStr)
                                        val modifiedEntry = entryMap.toMutableMap()
                                        modifiedEntry["data"] = parsedData
                                        modifiedEntry
                                    } catch (e: Exception) {
                                        // If parsing fails, keep as string
                                        LogManager.aiPrompt("Failed to parse data field in entry: ${e.message}", "WARN", e)
                                        entryMap
                                    }
                                } else {
                                    entryMap
                                }
                            } else {
                                entry
                            }
                        }
                        reordered["entries"] = parsedEntries
                    }

                    // Add pagination if present
                    data["pagination"]?.let { reordered["pagination"] = it }
                }
                "schemas" -> {
                    // Schema data: parse content as JSON instead of keeping it as escaped string
                    data["schema_id"]?.let { reordered["schema_id"] = it }

                    // Parse content string as JSON for readable prompt formatting
                    val contentStr = data["content"] as? String
                    if (contentStr != null) {
                        try {
                            reordered["content"] = org.json.JSONObject(contentStr)
                        } catch (e: Exception) {
                            // If parsing fails, keep as string
                            reordered["content"] = contentStr
                        }
                    }
                }
                "tools" -> {
                    // Config or list
                    data["id"]?.let { reordered["id"] = it }
                    data["name"]?.let { reordered["name"] = it }
                    data["toolType"]?.let { reordered["toolType"] = it }

                    // Parse config_json string as JSON for readable prompt formatting
                    val toolInstance = data["tool_instance"] as? Map<*, *>
                    val configJsonStr = toolInstance?.get("config_json") as? String
                    if (configJsonStr != null) {
                        try {
                            val parsedConfig = org.json.JSONObject(configJsonStr)
                            // Replace the string with parsed JSON in tool_instance
                            val modifiedToolInstance = toolInstance.toMutableMap()
                            modifiedToolInstance["config_json"] = parsedConfig
                            reordered["tool_instance"] = modifiedToolInstance
                        } catch (e: Exception) {
                            // If parsing fails, keep as string
                            LogManager.aiPrompt("Failed to parse config_json in tools: ${e.message}", "WARN", e)
                            data["tool_instance"]?.let { reordered["tool_instance"] = it }
                        }
                    }

                    // Add remaining fields (except tool_instance if already processed)
                    data.forEach { (key, value) ->
                        if (key !in reordered) reordered[key] = value
                    }
                }
                "zones" -> {
                    // List or single zone
                    data["id"]?.let { reordered["id"] = it }
                    data["name"]?.let { reordered["name"] = it }
                    // Add remaining fields
                    data.forEach { (key, value) ->
                        if (key !in reordered) reordered[key] = value
                    }
                }
                else -> {
                    // Default: keep original order
                    reordered.putAll(data)
                }
            }

            // Add any remaining keys not yet added
            data.forEach { (key, value) ->
                if (key !in reordered) reordered[key] = value
            }

            org.json.JSONObject(reordered as Map<*, *>).toString(2)
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to format result data: ${e.message}", "WARN")
            org.json.JSONObject(data).toString(2)
        }
    }

    /**
     * Load historical schema IDs from previous messages in the session
     * Returns Set of schema_id that have been successfully retrieved before
     *
     * Scans all SystemMessages with type DATA_ADDED and extracts schema_id from
     * CommandResults where command == "schemas.get" and status == SUCCESS
     */
    private suspend fun loadHistoricalSchemas(sessionId: String): Set<String> {
        return try {
            // Create repository locally to access message history
            val appDatabase = com.assistant.core.database.AppDatabase.getDatabase(context)
            val aiDao = appDatabase.aiDao()
            val messageRepository = com.assistant.core.ai.state.AIMessageRepository(aiDao)

            val messages = messageRepository.loadMessages(sessionId)
            val schemaIds = mutableSetOf<String>()

            LogManager.aiPrompt("loadHistoricalSchemas: Found ${messages.size} messages in session", "DEBUG")

            for ((index, message) in messages.withIndex()) {
                LogManager.aiPrompt("Message $index: sender=${message.sender}, hasSystemMessage=${message.systemMessage != null}", "VERBOSE")

                // Only check SystemMessages with DATA_ADDED type
                val systemMessage = message.systemMessage
                if (systemMessage?.type == com.assistant.core.ai.data.SystemMessageType.DATA_ADDED) {
                    LogManager.aiPrompt("Found DATA_ADDED message with ${systemMessage.commandResults.size} results", "VERBOSE")

                    // Check all command results in this system message
                    for ((cmdIndex, commandResult) in systemMessage.commandResults.withIndex()) {
                        LogManager.aiPrompt("  Result $cmdIndex: command=${commandResult.command}, status=${commandResult.status}, hasData=${commandResult.data != null}", "VERBOSE")

                        // Look for successful schema.get commands
                        if (commandResult.command == "schemas.get" &&
                            commandResult.status == com.assistant.core.ai.data.CommandStatus.SUCCESS) {
                            // Extract schema_id from result data
                            val schemaId = commandResult.data?.get("schema_id") as? String
                            LogManager.aiPrompt("    schema.get found, schema_id=$schemaId, data keys=${commandResult.data?.keys}", "DEBUG")

                            if (schemaId != null) {
                                schemaIds.add(schemaId)
                                LogManager.aiPrompt("Found historical schema: $schemaId", "VERBOSE")
                            } else {
                                LogManager.aiPrompt("    WARNING: schema_id is null! data=${ commandResult.data}", "WARN")
                            }
                        }
                    }
                }
            }

            LogManager.aiPrompt("Loaded ${schemaIds.size} historical schemas from session $sessionId", "DEBUG")
            schemaIds
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to load historical schemas: ${e.message}", "WARN", e)
            emptySet()
        }
    }
}