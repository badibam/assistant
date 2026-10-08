package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * The keys renamed when the project moved every string key to snake_case.
 *
 * Renaming the code is only half of it: the same names are written inside stored JSON --
 * a session's waiting context, an AI message, a tool's config, the app settings. This map is
 * the single place that says what became what, so the database migration and the backup
 * import cannot drift apart from each other or from the code.
 *
 * It is history, not a rule. Nothing new is added here: the rule itself lives in
 * docs/reference.md and is enforced by scripts/check_key_case.py.
 */
object KeyCaseRenames {

    val MAP: Map<String, String> = mapOf(
        "actionCommands" to "action_commands",
        "activeProviderId" to "active_provider_id",
        "aiMessageId" to "ai_message_id",
        "aiMessageJson" to "ai_message_json",
        "aiMessageParsedJson" to "ai_message_parsed_json",
        "appStateSnapshot" to "app_state_snapshot",
        "automationId" to "automation_id",
        "automationMaxActionRetries" to "automation_max_action_retries",
        "automationMaxAutonomousRoundtrips" to "automation_max_autonomous_roundtrips",
        "automationMaxDataQueryIterations" to "automation_max_data_query_iterations",
        "availableNames" to "available_names",
        "cacheReadCost" to "cache_read_cost",
        "cacheReadTokens" to "cache_read_tokens",
        "cacheWriteCost" to "cache_write_cost",
        "cacheWriteTokens" to "cache_write_tokens",
        "cancelCommunication" to "cancel_communication",
        "catchUpWindowMinutes" to "catch_up_window_minutes",
        "charsPerToken" to "chars_per_token",
        "chatMaxActionRetries" to "chat_max_action_retries",
        "chatMaxAutonomousRoundtrips" to "chat_max_autonomous_roundtrips",
        "chatMaxDataQueryIterations" to "chat_max_data_query_iterations",
        "commandResults" to "command_results",
        "communicationModule" to "communication_module",
        "configJson" to "config_json",
        "costJson" to "cost_json",
        "createdAt" to "created_at",
        "currentPage" to "current_page",
        "dataCommands" to "data_commands",
        "dayOfMonth" to "day_of_month",
        "dayOfWeek" to "day_of_week",
        "daysOfWeek" to "days_of_week",
        "defaultCharsPerToken" to "default_chars_per_token",
        "defaultPromptMaxTokens" to "default_prompt_max_tokens",
        "defaultQueryMaxTokens" to "default_query_max_tokens",
        "dismissOlderInstances" to "dismiss_older_instances",
        "displayName" to "display_name",
        "docType" to "doc_type",
        "elementId" to "element_id",
        "elementType" to "element_type",
        "endDate" to "end_date",
        "endReason" to "end_reason",
        "endTime" to "end_time",
        "endTimestamp" to "end_timestamp",
        "enrichmentType" to "enrichment_type",
        "entriesPerPage" to "entries_per_page",
        "errorCount" to "error_count",
        "excludeFromPrompt" to "exclude_from_prompt",
        "executionHistoryJson" to "execution_history_json",
        "executionMetadataJson" to "execution_metadata_json",
        "executionTime" to "execution_time",
        "falseLabel" to "false_label",
        "fieldName" to "field_name",
        "fieldSpecificData" to "field_specific_data",
        "firstUserMessage" to "first_user_message",
        "formattedData" to "formatted_data",
        "groupName" to "group_name",
        "hasActiveProvider" to "has_active_provider",
        "hasActiveSession" to "has_active_session",
        "hasConfig" to "has_config",
        "inputCost" to "input_cost",
        "inputTokens" to "input_tokens",
        "isActionCommand" to "is_action_command",
        "isActive" to "is_active",
        "isConfigured" to "is_configured",
        "isEnabled" to "is_enabled",
        "isRelative" to "is_relative",
        "keepControl" to "keep_control",
        "lastActivity" to "last_activity",
        "lastEventTime" to "last_event_time",
        "lastExecutionId" to "last_execution_id",
        "lastUserInteractionTime" to "last_user_interaction_time",
        "linearText" to "linear_text",
        "maxCustomDateTime" to "max_custom_date_time",
        "maxIsNow" to "max_is_now",
        "maxLabel" to "max_label",
        "maxPeriod" to "max_period",
        "maxPeriodType" to "max_period_type",
        "maxRelativePeriod" to "max_relative_period",
        "maxTimestamp" to "max_timestamp",
        "maxValue" to "max_value",
        "messageCount" to "message_count",
        "messageId" to "message_id",
        "minCustomDateTime" to "min_custom_date_time",
        "minIsNow" to "min_is_now",
        "minLabel" to "min_label",
        "minPeriod" to "min_period",
        "minPeriodType" to "min_period_type",
        "minRelativePeriod" to "min_relative_period",
        "minTimestamp" to "min_timestamp",
        "minValue" to "min_value",
        "modelId" to "model_id",
        "operationId" to "operation_id",
        "outputCost" to "output_cost",
        "outputTokens" to "output_tokens",
        "postText" to "post_text",
        "preText" to "pre_text",
        "priceAvailable" to "price_available",
        "promptPreview" to "prompt_preview",
        "providerId" to "provider_id",
        "providerSessionId" to "provider_session_id",
        "requireValidation" to "require_validation",
        "richContent" to "rich_content",
        "richContentJson" to "rich_content_json",
        "ruleId" to "rule_id",
        "scheduleJson" to "schedule_json",
        "scheduledConfirmationTime" to "scheduled_confirmation_time",
        "scheduledExecutionTime" to "scheduled_execution_time",
        "seedId" to "seed_id",
        "seedSessionId" to "seed_session_id",
        "selectedContext" to "selected_context",
        "selectedNames" to "selected_names",
        "selectedPath" to "selected_path",
        "selectedResources" to "selected_resources",
        "selectionLevel" to "selection_level",
        "sessionId" to "session_id",
        "sessionsDeactivated" to "sessions_deactivated",
        "startDate" to "start_date",
        "startTime" to "start_time",
        "startTimestamp" to "start_timestamp",
        "successCount" to "success_count",
        "suggestedName" to "suggested_name",
        "systemManaged" to "system_managed",
        "systemMessage" to "system_message",
        "systemMessageJson" to "system_message_json",
        "textContent" to "text_content",
        "timestampSelection" to "timestamp_selection",
        "tokensJson" to "tokens_json",
        "toolInstanceId" to "tool_instance_id",
        "toolInstanceName" to "tool_instance_name",
        "toolType" to "tooltype",
        "tool_type" to "tooltype",
        "totalCacheReadTokens" to "total_cache_read_tokens",
        "totalCacheWriteTokens" to "total_cache_write_tokens",
        "totalCost" to "total_cost",
        "totalCount" to "total_count",
        "totalEntries" to "total_entries",
        "totalOutputTokens" to "total_output_tokens",
        "totalPages" to "total_pages",
        "totalRoundtrips" to "total_roundtrips",
        "totalUncachedInputTokens" to "total_uncached_input_tokens",
        "triggerIdsJson" to "trigger_ids_json",
        "triggeredAt" to "triggered_at",
        "trueLabel" to "true_label",
        "updatedAt" to "updated_at",
        "validateAppConfigChanges" to "validate_app_config_changes",
        "validateConfig" to "validate_config",
        "validateData" to "validate_data",
        "validateToolConfigChanges" to "validate_tool_config_changes",
        "validateToolDataChanges" to "validate_tool_data_changes",
        "validateZoneConfigChanges" to "validate_zone_config_changes",
        "validationContext" to "validation_context",
        "validationRequest" to "validation_request",
        "waitingContextJson" to "waiting_context_json",
        "waitingStateJson" to "waiting_state_json",
        "zoneId" to "zone_id",
        "zoneName" to "zone_name"
    )

    /**
     * Rewrites the keys of a JSON document, at every depth, and leaves the values alone.
     *
     * The document is not always bare: a model's reply is stored exactly as it came, and it
     * often arrives wrapped in a markdown fence. Such a document is still converted, and
     * whatever surrounds it is put back untouched, so the stored reply stays what was said.
     * Text holding no JSON at all comes back as it was.
     */
    fun rename(json: String): String {
        if (json.isBlank()) return json

        val trimmed = json.trimStart()
        if (trimmed.startsWith("{")) return renameObject(JSONObject(json)).toString()
        if (trimmed.startsWith("[")) return renameArray(JSONArray(json)).toString()

        val start = json.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return json
        val end = json.indexOfLast { it == '}' || it == ']' }
        if (end <= start) return json

        val embedded = json.substring(start, end + 1)
        val converted = try {
            if (embedded.startsWith("{")) {
                renameObject(JSONObject(embedded)).toString()
            } else {
                renameArray(JSONArray(embedded)).toString()
            }
        } catch (e: Exception) {
            return json
        }
        return json.substring(0, start) + converted + json.substring(end + 1)
    }

    private fun renameObject(source: JSONObject): JSONObject {
        val result = JSONObject()
        for (key in source.keys()) {
            result.put(MAP[key] ?: key, renameValue(source.get(key)))
        }
        return result
    }

    private fun renameArray(source: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until source.length()) {
            result.put(renameValue(source.get(i)))
        }
        return result
    }

    private fun renameValue(value: Any?): Any? = when (value) {
        is JSONObject -> renameObject(value)
        is JSONArray -> renameArray(value)
        else -> value
    }
}
