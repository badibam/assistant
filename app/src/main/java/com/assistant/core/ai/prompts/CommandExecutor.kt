package com.assistant.core.ai.prompts

import com.assistant.core.utils.JsonUtils
import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.ServiceRegistry
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.ReferenceName
import com.assistant.core.fields.loadReferenceNames
import com.assistant.core.services.ExecutableService
import com.assistant.core.strings.Strings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Result of executing a single command for prompt formatting
 */
data class PromptCommandResult(
    val dataTitle: String,         // Title/header for data section in prompt
    val formattedData: String      // JSON formatted data for prompt
)

/**
 * The results of one batch as the section the model reads: a heading and the data under it.
 *
 * A command that failed, or a schema already sent, carries neither title nor data. Left in,
 * it printed an empty "# " heading among the real ones.
 */
fun List<PromptCommandResult>.toPromptSection(): String =
    filter { it.dataTitle.isNotEmpty() || it.formattedData.isNotEmpty() }
        .joinToString("\n\n") { "# ${it.dataTitle}\n${it.formattedData}" }

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
    private val s = Strings.`for`(context = context)

    /**
     * What tells two schemas apart, for not sending the model one it already has: the entries
     * of a tool are one schema per tool ("entries:<id>"), since its user's fields are in it; any
     * other schema is told by its name ("tracking_config", "zone_config").
     */
    private fun schemaKey(schemaId: String?, toolInstanceId: String?): String? =
        toolInstanceId?.let { "entries:$it" } ?: schemaId

    /** [schemaKey] of the schema a schemas.get command asks for, by what it names. */
    private fun requestedSchemaKey(params: Map<String, Any?>): String? {
        val tooltype = params["tooltype"] as? String
        return schemaKey(params["id"] as? String ?: tooltype?.let { "${it}_config" }, params["tool_instance_id"] as? String)
    }

    /**
     * Execute commands and return complete result with prompt data + SystemMessage
     *
     * @param commands The commands to execute
     * @param messageType Type of SystemMessage (DATA_ADDED or ACTIONS_EXECUTED)
     * @param level The level name for logging purposes
     * @param sessionId Session ID for schema deduplication (null = no deduplication)
     * @param origin Who the commands come from: the AI's, the user's pointers, the app's prompt
     * @return CommandExecutionResult with prompt data and system message
     */
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        messageType: SystemMessageType,
        origin: com.assistant.core.coordinator.Source,
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

        // A query waits on the entries schema of the tool it reads, unless a SCHEMA command of
        // the same batch asks for it. Those keys are only looked at here: the batch tracker
        // fills as the SCHEMA commands run, so that they run rather than being taken as cached.
        if (sessionId != null) {
            val queriedTools = commands
                .filter { it.resource == "tool_data" && it.operation == "get" }
                .mapNotNull { it.params["tool_instance_id"] as? String ?: it.params["id"] as? String }
            val askedInBatch = commands
                .filter { it.resource == "schemas" && it.operation == "get" }
                .mapNotNull { requestedSchemaKey(it.params) }
            val missingSchemas = missingEntrySchemas(queriedTools, historicalSchemas + askedInBatch)

            if (missingSchemas.isNotEmpty()) {
                LogManager.aiPrompt("Schema verification failed: ${missingSchemas.size} schemas missing", "WARN")
                return CommandExecutionResult(
                    promptResults = emptyList(),
                    systemMessage = schemaRequiredMessage(missingSchemas, "ai_schema_required_summary")
                )
            }
        }

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
                val deduplicationKey = requestedSchemaKey(command.params)

                if (deduplicationKey != null) {

                    // Check inter-message deduplication (historical)
                    val isDuplicatedFromHistory = deduplicationKey in historicalSchemas

                    // Check intra-message deduplication (current batch)
                    val isDuplicatedInBatch = deduplicationKey in currentBatchSchemas

                    if (isDuplicatedFromHistory || isDuplicatedInBatch) {
                        // Schema already retrieved - create CACHED result
                        LogManager.aiPrompt("Schema $deduplicationKey already included - creating CACHED result", "DEBUG")

                        val cachedResult = InternalCommandResult(
                            promptResult = PromptCommandResult("", ""),  // No prompt data for cached
                            commandResult = com.assistant.core.ai.data.CommandResult(
                                command = "${command.resource}.${command.operation}",
                                status = CommandStatus.CACHED,
                                details = s.shared("ai_schema_already_included").format(deduplicationKey),
                                data = null,
                                error = null,
                                isActionCommand = false  // schemas.get is a query
                            )
                        )

                        promptResults.add(cachedResult.promptResult)
                        commandResults.add(cachedResult.commandResult)
                        successCount++
                        LogManager.aiPrompt("Command ${index + 1} cached (schema $deduplicationKey)", "DEBUG")
                        continue  // Skip execution, move to next command
                    } else {
                        // Not a duplicate - will execute normally, add to current batch tracker
                        currentBatchSchemas.add(deduplicationKey)
                    }
                }
            }

            // Execute command normally (not a duplicate or not a schema)
            val result = executeCommand(command, origin)

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
     * The services check what they are given, whoever the caller.
     */
    private suspend fun executeCommand(command: ExecutableCommand, origin: com.assistant.core.coordinator.Source): InternalCommandResult? {
        LogManager.aiPrompt("Executing ExecutableCommand: resource=${command.resource}, operation=${command.operation}, isActionCommand=${command.isActionCommand}", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                val commandString = "${command.resource}.${command.operation}"

                val paramsMap = command.params

                LogManager.aiPrompt("Calling coordinator: $commandString with ${paramsMap.size} params", "VERBOSE")

                // Note: Coordinator.executeServiceOperation() handles recursive Map→JSON conversion
                val result = coordinator.process(origin, commandString, paramsMap)

                if (result.isSuccess) {
                    val data = result.data ?: emptyMap()

                    // For actions, data may be empty or minimal (just success/ID)
                    // For queries, empty data is unusual
                    if (data.isEmpty() && !command.isActionCommand) {
                        LogManager.aiPrompt("Query succeeded but returned empty data", "WARN")

                        // Generate description for empty query result
                        val description = userDetails(command, data).ifEmpty { s.shared("ai_system_query_no_data") }

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
                    // A result that cannot be put in the model's form fails the command out loud:
                    // handed raw, its dates would reach the model as milliseconds, unannounced
                    val formattedData = if (command.isActionCommand) "" else try {
                        formatResultData(command, data)
                    } catch (e: Exception) {
                        LogManager.aiPrompt("Result of $commandString could not be formatted: ${e.message}", "ERROR", e)
                        return@withContext InternalCommandResult(
                            promptResult = PromptCommandResult("", ""),
                            commandResult = com.assistant.core.ai.data.CommandResult(
                                command = commandString,
                                status = CommandStatus.FAILED,
                                details = dataTitle.ifEmpty { null },
                                data = null,
                                error = s.shared("ai_error_result_unformattable").format(e.message ?: ""),
                                isActionCommand = false
                            )
                        )
                    }

                    // Get verbalized description for all commands
                    // - Action commands: use service verbalization (e.g., "Création de la zone \"Santé\"")
                    // - Query commands: a line for the user (userDetails), the data's header being the AI's
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
                        userDetails(command, data).ifEmpty { null }
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
    private suspend fun getVerbalizedDescription(command: ExecutableCommand): String {
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

                // A group left behind by a change of zone (Groups)
                data["group_emptied"]?.let { filtered["group_emptied"] = it }

                if (filtered.isEmpty()) null else filtered
            }
            "batch_create", "batch_update", "batch_delete" -> {
                // Batch operations: their counts, and the ids of the entries created, for the AI
                // to act on what it just wrote without reading it back
                val filtered = mutableMapOf<String, Any>()

                data["created_count"]?.let { filtered["created_count"] = it }
                if (operation == "batch_create") data["ids"]?.let { filtered["ids"] = it }
                // The user's fields the entries created leave empty, each with how many
                data["extra_left_empty"]?.let { filtered["extra_left_empty"] = it }
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
     * - schemas: keep schema_id + tool_instance_id (for data/execution schema deduplication)
     * - tools/zones: keep id, name, count fields
     * - Remove large nested objects and arrays
     */
    private fun filterQueryResultData(resource: String, data: Map<String, Any>): Map<String, Any>? {
        return when (resource) {
            "schemas" -> {
                // Schemas: keep schema_id + tool_instance_id for deduplication, remove large 'content'
                // tool_instance_id is needed for data/execution schemas (custom_fields enrichment)
                val filtered = mutableMapOf<String, Any>()
                data["schema_id"]?.let { filtered["schema_id"] = it }
                data["tool_instance_id"]?.let { filtered["tool_instance_id"] = it }
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
                data["tooltype"]?.let { filtered["tooltype"] = it }
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
            SystemMessageType.SCHEMA_REQUIRED -> {
                // SCHEMA_REQUIRED messages provide their own summary directly
                // This case should not be reached in normal flow
                "Data schema required"
            }
            SystemMessageType.DATA_AWAITING_CONFIRMATION, SystemMessageType.DATA_REFUSED, SystemMessageType.TEXT_OUTSIDE_JSON,
            SystemMessageType.EMPTY_ANSWER ->
                // Written by AIEventProcessor with its own summary, never generated here
                throw IllegalStateException("$type is set by AIEventProcessor, not generated by CommandExecutor")
            SystemMessageType.NETWORK_ERROR, SystemMessageType.SESSION_TIMEOUT, SystemMessageType.INTERRUPTED, SystemMessageType.PROVIDER_ERROR -> {
                // These messages should never reach here (filtered from prompts, audit only)
                // But provide fallback just in case
                s.shared("ai_error_system_generic")
            }
        }
    }

    /**
     * What the user reads of a query in the chat: for entries, the tool and how many were read;
     * otherwise the data's header. The header of entries is written for the AI (filters, fields)
     * and stays in the prompt.
     */
    private suspend fun userDetails(command: ExecutableCommand, data: Map<String, Any>): String {
        if (command.resource != "tool_data") return generateDataTitle(command, data)
        val toolInstanceId = command.params["tool_instance_id"] as? String ?: command.params["id"] as? String
            ?: return generateDataTitle(command, data)
        val count = data["count"] as? Int ?: (data["entries"] as? List<*>)?.size ?: 0
        return s.shared("ai_data_user_summary").format(resolveToolInstanceName(toolInstanceId), count)
    }

    /**
     * Generate title/header for data section in prompt
     * Creates descriptive title with context (tool name, filters, period, etc.)
     * Returns empty string for action commands
     *
     * Always states the result count (even if 0) and the exact query (filters, fields): the AI
     * knows what the data covers, and has no reason to ask for it again
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
                    // Note: UserCommandProcessor transforms "id" → "tool_instance_id"
                    LogManager.aiPrompt("tool_data command params keys: ${command.params.keys}", "VERBOSE")
                    LogManager.aiPrompt("tool_instance_id=${command.params["tool_instance_id"]}, id=${command.params["id"]}", "VERBOSE")

                    val toolInstanceId = command.params["tool_instance_id"] as? String
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
                    headerParts.add("=== ${s.shared("ai_data_header_tool").format(toolName)} ===")
                    headerParts.add(s.shared("ai_data_result_count").format(count))

                    // The filters, a period included, written as the model writes them
                    val filters = command.params["filters"] as? List<*>
                    if (filters.isNullOrEmpty()) {
                        headerParts.add(s.shared("ai_data_filters_none"))
                    } else {
                        headerParts.add(s.shared("ai_data_filters").format(describeFilters(command, filters)))
                    }

                    val limit = command.params["limit"] as? Int
                    if (limit != null) {
                        headerParts.add(s.shared("ai_data_limit").format(limit))
                    }

                    // Fields included (dynamic from command params)
                    val requestedFields = command.params["fields"] as? List<*>
                    val fieldsDisplay = if (requestedFields != null && requestedFields.isNotEmpty()) {
                        requestedFields.joinToString(", ")
                    } else {
                        // Fallback for backward compatibility (no fields param = all fields)
                        s.shared("ai_data_fields_all")
                    }
                    headerParts.add(s.shared("ai_data_fields").format(fieldsDisplay))

                    // An empty result says so, so that it does not read as a failure
                    if (count == 0) headerParts.add(s.shared("ai_data_no_results_warning"))

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
                "icons" -> when (command.operation) {
                    "overview" -> "Icons: overview"
                    else -> {
                        val query = (command.params["query"] as? List<*>)?.joinToString(", ")
                        val categories = (command.params["categories"] as? List<*>)?.joinToString(", ")
                        listOfNotNull(
                            query?.let { "matching $it" },
                            categories?.let { "in $it" }
                        ).joinToString(" ", prefix = "Icons ")
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
        val reordered = mutableMapOf<String, Any>()
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()

        // A schema is read in its notation, not as the JSON the app validates against
        (data["content"] as? String)?.takeIf { command.resource == "schemas" }?.let { content ->
            return "schema_id: ${data["schema_id"]}\n" + schemaForModel(content) + operationsForModel(data["tooltype"] as? String)
        }

        // Extract metadata keys first based on command type
        when (command.resource) {
            "tool_data" -> {
                // Metadata: toolInstanceName, count
                // Bulk data: entries (with parsed data JSON)
                data["tool_instance_name"]?.let { reordered["tool_instance_name"] = it }
                data["count"]?.let { reordered["count"] = it }

                // The entries' dates and durations in ISO 8601, found by the tool's entry
                // schema: its fixed fields, the user's and the core's alike; each reference with
                // the current name of what it designates
                data["entries"]?.let { entries ->
                    val schema = runBlocking { loadEntrySchema(command) }
                    val converted = (entries as List<*>).map { ModelValues.toModel(it, schema, timezone)!! }
                    val references = converted.flatMap { ModelValues.references(it, schema) }.distinct()
                    val names = runBlocking { loadReferenceNames(references, context) }
                        .mapValues { (_, name) -> (name as? ReferenceName.Named)?.name }
                    reordered["entries"] = converted.map { ModelValues.withReferenceNames(it, schema, names)!! }
                }

                // Add pagination if present
                data["pagination"]?.let { reordered["pagination"] = it }
            }
            "tools" -> {
                // Config or list
                data["id"]?.let { reordered["id"] = it }
                data["name"]?.let { reordered["name"] = it }
                data["tooltype"]?.let { reordered["tooltype"] = it }

                // A config's dates and durations in ISO 8601, found by its tooltype's config schema
                data["tool_instance"]?.let { reordered["tool_instance"] = configForModel(it, timezone)!! }
                (data["tool_instances"] as? List<*>)?.let { list -> reordered["tool_instances"] = list.map { configForModel(it, timezone) } }

                // Add remaining fields (except tool_instance if already processed)
                data.forEach { (key, value) ->
                    if (key !in reordered) reordered[key] = value
                }
            }
            // A variable's values: each instant in ISO 8601, a duration too
            "readings", "variables" -> {
                val duration = (data["field"] as? Map<*, *>)?.get("type") == "DURATION"
                (data["values"] as? List<*>)?.let { values ->
                    reordered["values"] = values.map { row ->
                        (row as Map<*, *>).entries.associate { (key, value) ->
                            key.toString() to when {
                                key == "at" && value is Number -> DateTimeConverter.timestampToISO(value.toLong(), timezone)
                                key == "value" && duration && value is Number -> java.time.Duration.ofMillis(value.toLong()).toString()
                                else -> value
                            }
                        }
                    }
                }
                // A variable's terms: the fixed bounds of their periods in ISO 8601
                (data["variables"] as? List<*>)?.let { list -> reordered["variables"] = list.map { variableForModel(it, timezone) } }
                data["variable"]?.let { reordered["variable"] = variableForModel(it, timezone)!! }
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

        // This is where a result becomes the text the model reads, so this is where the
        // milliseconds everything else speaks turn into the ISO 8601 the model does
        // (docs/design/date-boundary.md). Entries were converted above by their schema;
        // any other result carries its dates under the few names DateTimeConverter knows.
        val json = org.json.JSONObject(reordered as Map<*, *>)
        return if (command.resource == "tool_data") json.toString(2)
        else DateTimeConverter.timestampsToISO(json, timezone).toString(2)
    }

    /** A variable as the model reads it: the fixed bounds of its terms' periods in ISO 8601. */
    private fun variableForModel(variable: Any?, timezone: java.time.ZoneId): Any? {
        val row = variable as? Map<*, *> ?: return variable
        val definition = row["definition"] as? Map<*, *> ?: return variable
        val terms = definition["terms"] as? Map<*, *> ?: return variable
        fun iso(bound: Any?) = if (bound is Number) DateTimeConverter.timestampToISO(bound.toLong(), timezone) else bound
        val converted = terms.entries.associate { (name, term) ->
            val reading = (term as? Map<*, *>)?.get("reading") as? Map<*, *>
            val selection = reading?.get("selection") as? Map<*, *>
            val period = selection?.get("period") as? Map<*, *>
            name to if (period == null) term else mapOf("reading" to reading + ("selection" to selection + ("period" to period.mapValues { iso(it.value) })))
        }
        return row + ("definition" to definition + ("terms" to converted))
    }

    /**
     * A schema as the model reads it: its dates and durations in ISO 8601 (SchemaModelView),
     * written in the notation (SchemaNotation).
     */
    /**
     * The operations the tool type [tooltype] declares (ToolOperation), as the model reads them
     * after a tool's entries schema: each one's name, its sentence and its parameters' schema.
     * Empty when there is no tool type or it declares none.
     */
    private fun operationsForModel(tooltype: String?): String {
        val toolType = tooltype?.let { com.assistant.core.tools.ToolTypeManager.getToolType(it) } ?: return ""
        val operations = toolType.getOperations(context)
        if (operations.isEmpty()) return ""
        return buildString {
            append("\n\n## ").append(s.shared("ai_chunk_tool_operations_header"))
            for (operation in operations) {
                append("\n\n### ").append(operation.name).append("\n").append(operation.description)
                if (operation.params.isNotEmpty()) {
                    val schema = com.assistant.core.tools.ToolOperations.schema(tooltype, operation, context)
                    append("\n").append(schemaForModel(schema.content))
                }
            }
        }
    }

    private fun schemaForModel(content: String): String =
        SchemaNotation.render(SchemaModelView.forModel(JSONObject(content), AppConfigManager.getDateTimeConfig().getZoneId()))

    /** A tool instance of a result, its config in the form the model reads. */
    private fun configForModel(instance: Any?, timezone: java.time.ZoneId): Any? {
        val map = instance as? Map<*, *> ?: return instance
        val config = map["config"] ?: return instance
        val tooltype = map["tooltype"] as? String ?: return instance
        val toolType = com.assistant.core.tools.ToolTypeManager.getToolType(tooltype)
            ?: throw IllegalStateException("Unknown tooltype $tooltype")
        val schema = JSONObject(com.assistant.core.tools.ToolConfigSettings.schema(toolType, "${tooltype}_config", context).content)
        return map.toMutableMap().apply { put("config", ModelValues.toModel(config, schema, timezone)) }
    }

    /**
     * The entry schema of the tool a tool_data command reads, generated from its current config:
     * what the result's dates and durations are converted by on the way to the model.
     *
     * @throws IllegalStateException when the tool or its fields cannot be read
     */
    /**
     * The filters of a TOOL_DATA query as the model writes them, their instants and durations in
     * ISO 8601 by the tool's entry schema: "timestamp >= 2026-09-19T00:00:00+02:00".
     */
    private suspend fun describeFilters(command: ExecutableCommand, filters: List<*>): String {
        val schema = loadEntrySchema(command)
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        return filters.joinToString(", ") { raw ->
            val filter = (raw as? Map<*, *>)?.let { map -> JSONObject(map.entries.associate { it.key.toString() to it.value }) } ?: return@joinToString raw.toString()
            val path = com.assistant.core.conditions.Conditions.fieldOf(filter) ?: ""
            val leaf = schema.optJSONObject("properties")?.let { properties ->
                if ('.' in path) properties.optJSONObject(path.substringBefore('.'))
                    ?.optJSONObject("properties")?.optJSONObject(path.substringAfter('.'))
                else properties.optJSONObject(path)
            }
            fun model(value: Any?) = if (leaf != null) ModelValues.toModel(value, leaf, zone) else value
            val shown = when (val right = com.assistant.core.conditions.Conditions.right(filter)) {
                com.assistant.core.conditions.Conditions.Right.None -> ""
                is com.assistant.core.conditions.Conditions.Right.Written -> when (val value = right.value) {
                    is org.json.JSONArray -> (0 until value.length()).map { model(JsonUtils.toValue(value.get(it))) }.toString()
                    else -> model(value).toString()
                }
                is com.assistant.core.conditions.Conditions.Right.NotWritten -> right.raw.toString()
            }
            "$path ${filter.optString("op")} $shown".trim()
        }
    }

    private suspend fun loadEntrySchema(command: ExecutableCommand): JSONObject {
        val toolInstanceId = command.params["tool_instance_id"] as? String
            ?: throw IllegalStateException("No tool_instance_id in ${command.resource}.${command.operation}")
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val toolType = (toolInstance?.get("tooltype") as? String)?.let { com.assistant.core.tools.ToolTypeManager.getToolType(it) }
        if (!result.isSuccess || config == null || toolType == null) {
            throw IllegalStateException("Cannot read the tool $toolInstanceId: ${result.error}")
        }
        return JSONObject(com.assistant.core.tools.BaseSchemas.getEntrySchemaOrThrow(toolType, config, toolInstanceId, context))
    }

    /**
     * Data class for missing schema information with full schema content
     */
    private data class MissingSchemaInfo(
        val schemaId: String,
        val toolInstanceId: String,
        val schemaContent: String,  // Full JSON schema content
        val tooltype: String?       // Whose operations come with the schema
    )

    /**
     * The SCHEMA_REQUIRED message for the AI's writes: the entries schemas of the tools among
     * [toolInstanceIds] the model has not received in [sessionId], or null when it holds them all.
     *
     * A write waits on the schema as a query does. A value that fits the schema can still be
     * wrong in meaning — a unit, the ends of a scale, what an option stands for — and only the
     * schema tells it. The caller checks before the user is asked to validate the writes.
     */
    suspend fun schemaRequiredForWrites(toolInstanceIds: Collection<String>, sessionId: String): SystemMessage? {
        val missingSchemas = missingEntrySchemas(toolInstanceIds, loadHistoricalSchemas(sessionId))
        if (missingSchemas.isEmpty()) return null
        LogManager.aiPrompt("Writes held back: ${missingSchemas.size} entries schemas missing", "WARN")
        return schemaRequiredMessage(missingSchemas, "ai_schema_required_for_writes_summary")
    }

    /**
     * The SCHEMA_REQUIRED message holding [missingSchemas], its summary worded by [summaryKey].
     * Each schema is recorded as a schemas.get result, so the session counts it as sent.
     */
    private suspend fun schemaRequiredMessage(missingSchemas: List<MissingSchemaInfo>, summaryKey: String): SystemMessage {
        val schemaSummary = missingSchemas.joinToString("\n") { schema ->
            "- ${schema.schemaId} (tool instance: ${schema.toolInstanceId})"
        }
        val summary = s.shared(summaryKey).format(missingSchemas.size, schemaSummary)

        val schemasText = StringBuilder()
        for (schema in missingSchemas) {
            schemasText.appendLine("## Schema: ${schema.schemaId}")
            schemasText.appendLine("Tool Instance: ${schema.toolInstanceId}")
            schemasText.appendLine()
            schemasText.appendLine(schemaForModel(schema.schemaContent) + operationsForModel(schema.tooltype))
            schemasText.appendLine()
        }
        LogManager.aiPrompt("SCHEMA_REQUIRED formattedData length: ${schemasText.length}", "DEBUG")

        // Verbalized by SchemaService, as a SCHEMA command's result is
        val schemaService = com.assistant.core.services.SchemaService(context)
        val schemaCommandResults = missingSchemas.map { schema ->
            val params = JSONObject().put("tool_instance_id", schema.toolInstanceId)
            com.assistant.core.ai.data.CommandResult(
                command = "schemas.get",
                status = CommandStatus.SUCCESS,
                details = schemaService.verbalize("get", params, context),
                data = mapOf(
                    "schema_id" to schema.schemaId,
                    "tool_instance_id" to schema.toolInstanceId
                ),
                error = null,
                isActionCommand = false
            )
        }

        return SystemMessage(
            type = SystemMessageType.SCHEMA_REQUIRED,
            commandResults = schemaCommandResults,
            summary = summary,
            formattedData = schemasText.toString()
        )
    }

    /**
     * The entries schemas of the tools among [toolInstanceIds] whose key ("entries:<id>") is not
     * in [knownSchemas], fetched: the schemas a command on those tools waits on.
     *
     * @throws UnreadableSchema when one of them cannot be read: the commands waiting on it go no
     *   further, and the reason reaches the model (a parse error for writes, which it answers
     *   by correcting them; a system error for queries)
     */
    private suspend fun missingEntrySchemas(
        toolInstanceIds: Collection<String>,
        knownSchemas: Set<String>
    ): List<MissingSchemaInfo> {
        val missingSchemas = mutableListOf<MissingSchemaInfo>()

        for (toolInstanceId in toolInstanceIds.distinct()) {
            // The entries schema of this tool, its user's fields included
            val deduplicationKey = "entries:$toolInstanceId"
            val isAvailable = deduplicationKey in knownSchemas
            LogManager.aiPrompt("Schema availability for $deduplicationKey: $isAvailable", "DEBUG")
            if (isAvailable) continue

            val schemaResult = coordinator.processUserAction("schemas.get", mapOf("tool_instance_id" to toolInstanceId))
            val schemaContent = (schemaResult.data?.get("content") as? String)?.takeIf { it.isNotEmpty() }
            if (!schemaResult.isSuccess || schemaContent == null) {
                throw UnreadableSchema(toolInstanceId, schemaResult.error ?: "no content")
            }
            missingSchemas.add(MissingSchemaInfo(
                schemaId = schemaResult.data?.get("schema_id") as? String ?: deduplicationKey,
                toolInstanceId = toolInstanceId,
                schemaContent = schemaContent,
                tooltype = schemaResult.data?.get("tooltype") as? String
            ))
        }

        return missingSchemas
    }

    /** The entries schema of the tool [toolInstanceId] could not be read, for [reason]. */
    class UnreadableSchema(toolInstanceId: String, reason: String) :
        IllegalStateException("Cannot read the entries schema of tool $toolInstanceId: $reason")

    /**
     * Load historical schema deduplication keys from previous messages in the session
     * Returns Set of deduplication keys that have been successfully retrieved before
     *
     * For config schemas: returns schema_id only
     * For data/execution schemas: returns "schema_id:toolInstanceId" (composite key)
     *
     * Scans all SystemMessages with type DATA_ADDED and extracts schema_id + toolInstanceId from
     * CommandResults where command == "schemas.get" and status == SUCCESS
     */
    private suspend fun loadHistoricalSchemas(sessionId: String): Set<String> {
        return try {
            // Create repository locally to access message history
            val appDatabase = com.assistant.core.database.AppDatabase.getDatabase(context)
            val aiDao = appDatabase.aiDao()
            val messageRepository = com.assistant.core.ai.state.AIMessageRepository(aiDao)

            val messages = messageRepository.loadMessages(sessionId)
            val schemaKeys = mutableSetOf<String>()

            LogManager.aiPrompt("loadHistoricalSchemas: Found ${messages.size} messages in session", "DEBUG")

            for ((index, message) in messages.withIndex()) {
                LogManager.aiPrompt("Message $index: sender=${message.sender}, hasSystemMessage=${message.systemMessage != null}", "VERBOSE")

                // Check SystemMessages with DATA_ADDED or SCHEMA_REQUIRED type
                val systemMessage = message.systemMessage
                val relevantTypes = listOf(
                    com.assistant.core.ai.data.SystemMessageType.DATA_ADDED,
                    com.assistant.core.ai.data.SystemMessageType.SCHEMA_REQUIRED
                )
                if (systemMessage != null && systemMessage.type in relevantTypes) {
                    LogManager.aiPrompt("Found ${systemMessage.type} message with ${systemMessage.commandResults.size} results", "VERBOSE")

                    // Check all command results in this system message
                    for ((cmdIndex, commandResult) in systemMessage.commandResults.withIndex()) {
                        LogManager.aiPrompt("  Result $cmdIndex: command=${commandResult.command}, status=${commandResult.status}, hasData=${commandResult.data != null}", "VERBOSE")

                        // Look for successful schema.get commands
                        if (commandResult.command == "schemas.get" &&
                            commandResult.status == com.assistant.core.ai.data.CommandStatus.SUCCESS) {
                            // Extract schema_id and toolInstanceId from result data
                            val schemaId = commandResult.data?.get("schema_id") as? String
                            val toolInstanceId = commandResult.data?.get("tool_instance_id") as? String
                            LogManager.aiPrompt("    schema.get found, schema_id=$schemaId, toolInstanceId=$toolInstanceId, data keys=${commandResult.data?.keys}", "DEBUG")

                            val deduplicationKey = schemaKey(schemaId, toolInstanceId)
                            if (deduplicationKey != null) {
                                schemaKeys.add(deduplicationKey)
                                LogManager.aiPrompt("Found historical schema: $deduplicationKey", "VERBOSE")
                            } else {
                                LogManager.aiPrompt("schema.get returned no schema_id, data keys=${commandResult.data?.keys}", "WARN")
                            }
                        }
                    }
                }
            }

            LogManager.aiPrompt("Loaded ${schemaKeys.size} historical schema keys from session $sessionId", "DEBUG")
            schemaKeys
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to load historical schemas: ${e.message}", "WARN", e)
            emptySet()
        }
    }
}