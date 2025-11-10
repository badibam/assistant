package com.assistant.tools.messages

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.tools.ToolScheduler
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.services.ExecutableService
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.FieldLimits
import com.assistant.core.validation.SchemaUtils
import com.assistant.tools.messages.scheduler.MessageScheduler
// import com.assistant.tools.messages.ui.MessagesConfigScreen
// import com.assistant.tools.messages.ui.MessagesScreen

/**
 * Messages Tool Type implementation
 *
 * Provides notification/reminder functionality with scheduling support.
 * Messages are data entries (templates) with execution history stored as snapshots.
 *
 * Architecture:
 * - Config: Minimal settings (default_priority, external_notifications)
 * - Data: Message templates with title, content, schedule, priority, and executions array
 * - Executions: Immutable snapshots of sent notifications (system-managed)
 *
 * Scheduling:
 * - Uses ScheduleConfig infrastructure (6 patterns: Daily, Weekly, Monthly, Yearly, etc.)
 * - MessageScheduler appends executions when scheduled_time reached
 * - Notifications sent via NotificationService
 */
object MessageToolType : ToolTypeContract {

    // ========================================
    // Metadata
    // ========================================

    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "messages", context = context)
        return s.tool("display_name")
    }

    override fun getDescription(context: Context): String {
        val s = Strings.`for`(tool = "messages", context = context)
        return s.tool("description")
    }

    override fun getDefaultIconName(): String {
        return "bell"
    }

    override fun getSuggestedIcons(): List<String> {
        return listOf("bell", "notification", "message", "alarm", "calendar-clock", "bell-ring")
    }

    override fun getAvailableOperations(): List<String> {
        return listOf(
            "get_history",
            "mark_read", "mark_archived", "stats"
        )
    }

    override fun getDefaultConfig(): String {
        return """
        {
            "name": "",
            "description": "",
            "icon_name": "bell",
            "display_mode": "LINE",
            "management": "manual",
            "validateConfig": false,
            "validateData": false,
            "always_send": false,
            "default_priority": "default",
            "external_notifications": true
        }
        """.trimIndent()
    }

    // ========================================
    // Schemas (SchemaProvider interface)
    // ========================================

    override fun getAllSchemaIds(): List<String> {
        return listOf("messages_config", "messages_data", "messages_execution")
    }

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        return when (schemaId) {
            "messages_config" -> createMessagesConfigSchema(context)
            "messages_data" -> createMessagesDataSchema(context, toolInstanceId)
            "messages_execution" -> createMessagesExecutionSchema(context)
            else -> null
        }
    }

    /**
     * Creates messages configuration schema
     * Extends base config with default_priority and external_notifications
     */
    private fun createMessagesConfigSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "messages", context = context)

        val specificSchema = """
        {
            "properties": {
                "default_priority": {
                    "type": "string",
                    "enum": ["default", "high", "low"],
                    "default": "default",
                    "description": "${s.tool("schema_config_default_priority")}"
                },
                "external_notifications": {
                    "type": "boolean",
                    "default": true,
                    "description": "${s.tool("schema_config_external_notifications")}"
                }
            },
            "required": ["default_priority", "external_notifications"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "messages_config",
            displayName = s.tool("schema_config_display_name"),
            description = s.tool("schema_config_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    /**
     * Creates messages data schema
     *
     * Uses BaseSchemas.createExtendedSchema() to combine base tool_data structure
     * (tool_instance_id, tooltype, name, timestamp) with message-specific data.
     *
     * Message-specific data (inside "data" field):
     * - schema_id: Validation schema ID
     * - title: Message title (SHORT_LENGTH)
     * - content: Message content (LONG_LENGTH, optional)
     * - schedule: ScheduleConfig (nullable, embedded via SchemaUtils)
     * - priority: Notification priority (default|high|low)
     * - triggers: Event-based triggers (STUB, always null for MVP)
     * - executions: Array of execution snapshots (systemManaged, stripped from AI commands)
     */
    private fun createMessagesDataSchema(context: Context, toolInstanceId: String?): Schema {
        val s = Strings.`for`(tool = "messages", context = context)

        // Specific schema template for message data (will be wrapped in base structure)
        // Note: "name" is stored at ToolDataEntity level, not in the data JSON
        // The data JSON only contains: content, schedule, priority, triggers, executions
        val specificSchemaTemplate = """
        {
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "Message title (stored at entity level, not in data JSON)"
                },
                "timestamp": {
                    "type": "number",
                    "description": "Creation timestamp (optional, defaults to current time)"
                },
                "data": {
                    "type": "object",
                    "description": "Message template data with executions",
                    "properties": {
                        "content": {
                            "type": "string",
                            "maxLength": ${FieldLimits.LONG_LENGTH},
                            "description": "${s.tool("schema_data_content")}"
                        },
                        "schedule": "{{SCHEDULE_CONFIG_PLACEHOLDER}}",
                        "priority": {
                            "type": "string",
                            "enum": ["default", "high", "low"],
                            "description": "${s.tool("schema_data_priority")}"
                        },
                        "triggers": {
                            "type": "null",
                            "description": "${s.tool("schema_data_triggers")}"
                        }
                    },
                    "required": ["priority"],
                    "additionalProperties": false
                }
            },
            "required": ["name", "data"]
        }
        """.trimIndent()

        // Embed ScheduleConfig schema at placeholder
        val specificSchemaWithSchedule = SchemaUtils.embedScheduleConfig(
            specificSchemaTemplate,
            "{{SCHEDULE_CONFIG_PLACEHOLDER}}",
            context
        )

        // Combine with base schema and enrich with custom fields if toolInstanceId provided
        val content = if (toolInstanceId != null) {
            BaseSchemas.createExtendedDataSchema(
                BaseSchemas.getBaseDataSchema(context),
                specificSchemaWithSchedule,
                toolInstanceId,
                context
            )
        } else {
            BaseSchemas.createExtendedSchema(
                BaseSchemas.getBaseDataSchema(context),
                specificSchemaWithSchedule
            )
        }

        return Schema(
            id = "messages_data",
            displayName = s.tool("schema_data_display_name"),
            description = s.tool("schema_data_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    /**
     * Creates messages execution schema
     * Extends base execution schema with Messages-specific snapshot_data, execution_result, metadata
     *
     * snapshot_data: Template content at execution time (title, content, priority)
     * execution_result: Execution outcome (read, archived, notification_sent)
     * metadata: Additional context (errors, retry count, etc.)
     */
    private fun createMessagesExecutionSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "messages", context = context)

        val specificSchema = """
        {
            "properties": {
                "snapshot_data": {
                    "type": "object",
                    "description": "${s.tool("schema_execution_snapshot_data")}",
                    "properties": {
                        "title": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "Message title at execution time"
                        },
                        "content": {
                            "type": "string",
                            "maxLength": ${FieldLimits.LONG_LENGTH},
                            "description": "Message content at execution time"
                        },
                        "priority": {
                            "type": "string",
                            "enum": ["default", "high", "low"],
                            "description": "Priority at execution time"
                        }
                    },
                    "required": ["title", "priority"],
                    "additionalProperties": false
                },
                "execution_result": {
                    "type": "object",
                    "description": "${s.tool("schema_execution_execution_result")}",
                    "properties": {
                        "read": {
                            "type": "boolean",
                            "description": "User has read the notification"
                        },
                        "archived": {
                            "type": "boolean",
                            "description": "User has archived the notification"
                        },
                        "notification_sent": {
                            "type": "boolean",
                            "description": "System notification was sent successfully"
                        }
                    },
                    "required": ["read", "archived", "notification_sent"],
                    "additionalProperties": false
                },
                "metadata": {
                    "type": "object",
                    "description": "${s.tool("schema_execution_metadata")}",
                    "properties": {
                        "error": {
                            "type": "string",
                            "description": "Error message if execution failed"
                        },
                        "retry_count": {
                            "type": "integer",
                            "minimum": 0,
                            "description": "Number of retry attempts"
                        }
                    },
                    "additionalProperties": true
                }
            },
            "required": ["snapshot_data", "execution_result", "metadata"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseExecutionSchema(context),
            specificSchema
        )

        return Schema(
            id = "messages_execution",
            displayName = s.tool("schema_execution_display_name"),
            description = s.tool("schema_execution_description"),
            category = SchemaCategory.TOOL_EXECUTION,
            content = content
        )
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(tool = "messages", context = context)
        return when (fieldName) {
            "title" -> s.tool("field_title")
            "content" -> s.tool("field_content")
            "default_priority" -> s.tool("field_default_priority")
            "external_notifications" -> s.tool("field_external_notifications")
            "priority" -> s.tool("field_priority")
            "schedule" -> s.tool("field_schedule")
            "triggers" -> s.tool("field_triggers")
            "executions" -> s.tool("field_executions")
            else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
        }
    }

    // ========================================
    // UI
    // ========================================

    @Composable
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?,
        initialGroup: String?
    ) {
        com.assistant.tools.messages.ui.MessagesConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            onCancel = onCancel,
            existingToolId = existingToolId,
            onDelete = onDelete,
            initialGroup = initialGroup
        )
    }

    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit
    ) {
        com.assistant.tools.messages.ui.MessagesScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }

    // ========================================
    // Discovery pattern
    // ========================================

    override fun getService(context: Context): ExecutableService {
        return MessageService(context)
    }

    override fun getDao(context: Context): Any {
        val database = com.assistant.core.database.AppDatabase.getDatabase(context)
        val baseDao = database.toolDataDao()

        // Uses generic implementation for standard message entries
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "messages")
    }

    override fun getDatabaseEntities(): List<Class<*>> {
        // Messages uses unified ToolDataEntity (no custom entity)
        return listOf(ToolDataEntity::class.java)
    }

    // ========================================
    // Data Enrichment
    // ========================================

    /**
     * Enrich message data with calculated nextExecutionTime from schedule
     *
     * Called by ToolDataService before create/update operations.
     * Calculates nextExecutionTime based on schedule pattern and adds it to the data JSON.
     *
     * @param dataJson The data JSON containing schedule configuration
     * @param name The entry name (unused for messages)
     * @param configJson The tool instance config (unused for messages)
     * @return Enriched data JSON with nextExecutionTime calculated
     */
    override fun enrichData(dataJson: String, name: String?, configJson: String?): String {
        com.assistant.core.utils.LogManager.service("MessageToolType.enrichData called for message: name=$name", "DEBUG")

        return try {
            val dataObject = org.json.JSONObject(dataJson)

            // Check if schedule exists
            val scheduleJson = dataObject.optJSONObject("schedule")
            if (scheduleJson == null || scheduleJson.toString() == "null") {
                return dataJson // No schedule, return as-is
            }

            // Parse schedule to ScheduleConfig
            val scheduleConfig = try {
                kotlinx.serialization.json.Json.decodeFromString<com.assistant.core.utils.ScheduleConfig>(scheduleJson.toString())
            } catch (e: Exception) {
                com.assistant.core.utils.LogManager.service(
                    "Failed to parse schedule config during enrichment: ${e.message}",
                    "WARN",
                    e
                )
                return dataJson // Invalid schedule, return as-is (validation will catch it)
            }

            // Calculate nextExecutionTime
            val now = System.currentTimeMillis()
            val nextExecutionTime = com.assistant.core.utils.ScheduleCalculator.calculateNextExecution(
                pattern = scheduleConfig.pattern,
                timezone = scheduleConfig.timezone,
                startDate = scheduleConfig.startDate,
                endDate = scheduleConfig.endDate,
                fromTimestamp = now
            )

            // Update schedule with calculated nextExecutionTime
            if (nextExecutionTime != null) {
                scheduleJson.put("nextExecutionTime", nextExecutionTime)
            } else {
                scheduleJson.put("nextExecutionTime", org.json.JSONObject.NULL)
                com.assistant.core.utils.LogManager.service(
                    "No future executions for schedule (end date passed or invalid pattern)",
                    "WARN"
                )
            }

            // Update data with modified schedule
            dataObject.put("schedule", scheduleJson)

            dataObject.toString()

        } catch (e: Exception) {
            com.assistant.core.utils.LogManager.service(
                "Error enriching message data with nextExecutionTime: ${e.message}",
                "ERROR",
                e
            )
            dataJson // On error, return original data
        }
    }

    // ========================================
    // Scheduling
    // ========================================

    override fun getScheduler(): ToolScheduler {
        return MessageScheduler
    }

    // ========================================
    // Execution History
    // ========================================

    override fun supportsExecutions(): Boolean {
        return true
    }
}
