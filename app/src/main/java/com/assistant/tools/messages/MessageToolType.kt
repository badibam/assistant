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
import com.assistant.tools.messages.ui.MessagesConfigScreen
import com.assistant.tools.messages.ui.MessagesScreen

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
            "create", "update", "delete",
            "get", "get_single", "get_history",
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
        return listOf("messages_config", "messages_data")
    }

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return when (schemaId) {
            "messages_config" -> createMessagesConfigSchema(context)
            "messages_data" -> createMessagesDataSchema(context)
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
     * IMPORTANT: Does NOT extend BaseDataSchema because structure is different:
     * - Uses "title" instead of "name"
     * - No tool_instance_id, tooltype, timestamp (these are in MessageData entity, not JSON)
     * - Contains executions array with systemManaged flag
     * - Contains schedule (embedded via placeholder replacement)
     *
     * Structure:
     * - schema_id: Validation schema ID
     * - title: Message title (SHORT_LENGTH)
     * - content: Message content (LONG_LENGTH, optional)
     * - schedule: ScheduleConfig (nullable, embedded via SchemaUtils)
     * - priority: Notification priority (default|high|low)
     * - triggers: Event-based triggers (STUB, always null for MVP)
     * - executions: Array of execution snapshots (systemManaged, stripped from AI commands)
     */
    private fun createMessagesDataSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "messages", context = context)

        // Template with placeholder for schedule embedding
        val templateSchema = """
        {
            "type": "object",
            "properties": {
                "schema_id": {
                    "type": "string",
                    "const": "messages_data",
                    "description": "${s.shared("tools_base_schema_data_schema_id")}"
                },
                "title": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.tool("schema_data_title")}"
                },
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
                },
                "executions": {
                    "type": "array",
                    "systemManaged": true,
                    "description": "${s.tool("schema_data_executions")}",
                    "default": [],
                    "items": {
                        "type": "object",
                        "properties": {
                            "scheduled_time": {
                                "type": "integer",
                                "minimum": 0,
                                "description": "${s.tool("schema_data_execution_scheduled_time")}"
                            },
                            "sent_at": {
                                "type": "integer",
                                "minimum": 0,
                                "description": "${s.tool("schema_data_execution_sent_at")}"
                            },
                            "status": {
                                "type": "string",
                                "enum": ["pending", "sent", "failed"],
                                "description": "${s.tool("schema_data_execution_status")}"
                            },
                            "title_snapshot": {
                                "type": "string",
                                "description": "${s.tool("schema_data_execution_title_snapshot")}"
                            },
                            "content_snapshot": {
                                "type": "string",
                                "description": "${s.tool("schema_data_execution_content_snapshot")}"
                            },
                            "read": {
                                "type": "boolean",
                                "description": "${s.tool("schema_data_execution_read")}"
                            },
                            "archived": {
                                "type": "boolean",
                                "description": "${s.tool("schema_data_execution_archived")}"
                            }
                        },
                        "required": ["scheduled_time", "status", "title_snapshot", "read", "archived"],
                        "additionalProperties": false
                    }
                }
            },
            "required": ["schema_id", "title", "priority", "triggers", "executions"],
            "additionalProperties": false
        }
        """.trimIndent()

        // Embed ScheduleConfig schema at placeholder
        val content = SchemaUtils.embedScheduleConfig(
            templateSchema,
            "{{SCHEDULE_CONFIG_PLACEHOLDER}}",
            context
        )

        return Schema(
            id = "messages_data",
            displayName = s.tool("schema_data_display_name"),
            description = s.tool("schema_data_description"),
            category = SchemaCategory.TOOL_DATA,
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
        onDelete: (() -> Unit)?
    ) {
        MessagesConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            onCancel = onCancel,
            existingToolId = existingToolId,
            onDelete = onDelete
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
        MessagesScreen(
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
        TODO("MessageService not implemented yet")
        // return MessageService(context)
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
    // Scheduling
    // ========================================

    override fun getScheduler(): ToolScheduler {
        return MessageScheduler
    }
}
