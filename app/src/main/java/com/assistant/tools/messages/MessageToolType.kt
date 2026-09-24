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
 * One instance = one notification template. Its successive sends are its occurrences,
 * stored as ordinary tool_data entries — there is no separate execution plane.
 *
 * Architecture:
 * - Config = the template: the invariant part of every send (common_title, priority),
 *   the recurrence that spawns occurrences, and the channel settings.
 * - Data = the occurrences: one entry per send, carrying the part written for that day
 *   (title, content, custom field values) plus, once sent, the invariant part copied in.
 *
 * The two parts compose, they never override each other, so no "which one wins" rule
 * exists anywhere. The invariant part is copied AT SEND TIME and never at creation:
 * a pending occurrence is an intention, not an event, so two competing copies of the
 * template never coexist, and editing the template applies to everything not yet sent.
 *
 * Occurrence lifecycle (data.status):
 * - pending: created ahead of time by the scheduler, holds only its own part
 * - sent: went out, holds the copied invariant part and the send result
 * - expired: its time passed beyond validity_window_minutes while the app was off
 * - cancelled: its time arrived while the template was disabled — a decision, not a miss
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
        return listOf("bell", "bell-ring", "message-circle", "alarm-clock", "calendar-clock")
    }

    override fun getAvailableOperations(): List<String> {
        // Reading occurrences and flagging them read or archived go through tool_data, like
        // any other entry. Sending now is the only thing that is not plain data editing.
        return listOf("execute")
    }

    override fun getDefaultConfig(): String {
        return """
        {
            "name": "",
            "description": "",
            "icon_name": "bell",
            "display_mode": "LINE",
            "management": "manual",
            "validate_config": false,
            "validate_data": false,
            "always_send": false,
            "enabled": true,
            "priority": "default",
            "external_notifications": true,
            "creation_horizon_days": 2,
            "validity_window_minutes": 60
        }
        """.trimIndent()
    }

    // ========================================
    // Schemas (SchemaProvider interface)
    // ========================================

    override fun getAllSchemaIds(): List<String> {
        return listOf("messages_config", "messages_data")
    }

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        return when (schemaId) {
            "messages_config" -> createMessagesConfigSchema(context)
            "messages_data" -> createMessagesDataSchema(context, toolInstanceId)
            else -> null
        }
    }

    /**
     * Creates messages configuration schema — the template itself.
     *
     * Holds the invariant part of every send (common_title, priority), the recurrence
     * that spawns occurrences, and the two knobs that govern their lifecycle.
     *
     * common_title is deliberately distinct from the base "name": name is how you find
     * the instance in its zone, common_title is what shows up on the lock screen. Nothing
     * forces them to match, and forcing it would be paid for later. Optional — when absent
     * it contributes nothing and the notification carries only the occurrence's own title.
     *
     * priority lives here and ONLY here. It describes the channel — how insistently this
     * stream is allowed to interrupt — not the individual send. Keeping a copy on the
     * occurrence too would recreate the default/override pair this design rules out.
     *
     * schedule is optional: without it nothing fires on its own and the instance is a pure
     * notification channel, fed on demand by the user or the AI.
     */
    private fun createMessagesConfigSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "messages", context = context)

        val specificSchemaTemplate = """
        {
            "properties": {
                "enabled": {
                    "type": "boolean",
                    "default": true,
                    "description": "${s.tool("schema_config_enabled")}"
                },
                "common_title": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.tool("schema_config_common_title")}"
                },
                "common_content": {
                    "type": "string",
                    "maxLength": ${FieldLimits.LONG_LENGTH},
                    "description": "${s.tool("schema_config_common_content")}"
                },
                "priority": {
                    "type": "string",
                    "enum": ["default", "high", "low"],
                    "default": "default",
                    "description": "${s.tool("schema_config_priority")}"
                },
                "external_notifications": {
                    "type": "boolean",
                    "default": true,
                    "description": "${s.tool("schema_config_external_notifications")}"
                },
                "schedule": "{{SCHEDULE_CONFIG_PLACEHOLDER}}",
                "creation_horizon_days": {
                    "type": "integer",
                    "minimum": 1,
                    "default": 2,
                    "description": "${s.tool("schema_config_creation_horizon_days")}"
                },
                "validity_window_minutes": {
                    "type": "integer",
                    "minimum": 0,
                    "default": 60,
                    "description": "${s.tool("schema_config_validity_window_minutes")}"
                }
            },
            "required": ["enabled", "priority", "external_notifications", "creation_horizon_days", "validity_window_minutes"]
        }
        """.trimIndent()

        // Reuse the shared ScheduleConfig schema rather than restating its six patterns
        val embedded = SchemaUtils.embedScheduleConfig(
            specificSchemaTemplate,
            "{{SCHEDULE_CONFIG_PLACEHOLDER}}",
            context
        )
        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            embedded
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
     * Creates messages data schema — one entry per occurrence (one send).
     *
     * An occurrence has a lifecycle, so the schema's requirements depend on its status.
     * While pending it carries ONLY the part written for that day; the invariant part is
     * copied in at send time. Requiring the invariant part unconditionally would leave
     * half-empty entries with no way to tell them from complete ones.
     *
     * The conditional block lives inside the "data" property on purpose:
     * BaseSchemas.createExtendedSchema merges root properties and required verbatim, so an
     * allOf written here survives the merge untouched.
     *
     * The entry's timestamp IS the due time, not its creation time. Time is the only
     * ordering axis tool_data has, so timestamping a pending occurrence at creation would
     * pile every one of them at the same instant and scramble the inbox. The moment the
     * entry was actually written stays in updated_at, which makes a separate scheduled_time
     * field a pure duplicate — hence its absence.
     *
     * Custom field values are raw, written per occurrence, and validated through the
     * standard enrichment (createExtendedDataSchema) like every other tooltype.
     */
    private fun createMessagesDataSchema(context: Context, toolInstanceId: String?): Schema {
        val s = Strings.`for`(tool = "messages", context = context)

        // "name" is stored at ToolDataEntity level, not inside the data JSON
        val specificSchema = """
        {
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "Occurrence label (stored at entity level, not in data JSON)"
                },
                "timestamp": {
                    "type": "number",
                    "description": "When this occurrence is due (and, for a manual send, when it went out)"
                },
                "data": {
                    "type": "object",
                    "description": "One send of this message",
                    "properties": {
                        "status": {
                            "type": "string",
                            "enum": ["pending", "sent", "expired", "cancelled"],
                            "description": "${s.tool("schema_data_status")}"
                        },
                        "title": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_title")}"
                        },
                        "content": {
                            "type": "string",
                            "maxLength": ${FieldLimits.LONG_LENGTH},
                            "description": "${s.tool("schema_data_content")}"
                        },
                        "common_title": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_common_title")}"
                        },
                        "common_content": {
                            "type": "string",
                            "maxLength": ${FieldLimits.LONG_LENGTH},
                            "description": "${s.tool("schema_data_common_content")}"
                        },
                        "priority": {
                            "type": "string",
                            "enum": ["default", "high", "low"],
                            "description": "${s.tool("schema_data_priority")}"
                        },
                        "notification_sent": {
                            "type": "boolean",
                            "description": "${s.tool("schema_data_notification_sent")}"
                        },
                        "read": {
                            "type": "boolean",
                            "description": "${s.tool("schema_data_read")}"
                        },
                        "archived": {
                            "type": "boolean",
                            "description": "${s.tool("schema_data_archived")}"
                        },
                        "triggered_by": {
                            "type": "string",
                            "enum": ["SCHEDULE", "MANUAL"],
                            "description": "${s.tool("schema_data_triggered_by")}"
                        }
                    },
                    "required": ["status", "triggered_by"],
                    "additionalProperties": false,
                    "allOf": [
                        {
                            "if": {
                                "properties": { "status": { "const": "sent" } },
                                "required": ["status"]
                            },
                            "then": {
                                "required": ["priority", "notification_sent", "read", "archived"]
                            }
                        }
                    ]
                }
            },
            "required": ["name", "data"]
        }
        """.trimIndent()

        // Enrich with this instance's custom field definitions when we know which instance
        val content = if (toolInstanceId != null) {
            BaseSchemas.createExtendedDataSchema(
                BaseSchemas.getBaseDataSchema(context),
                specificSchema,
                toolInstanceId,
                context
            )
        } else {
            BaseSchemas.createExtendedSchema(
                BaseSchemas.getBaseDataSchema(context),
                specificSchema
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

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(tool = "messages", context = context)
        return when (fieldName) {
            "title" -> s.tool("field_title")
            "content" -> s.tool("field_content")
            "enabled" -> s.tool("field_enabled")
            "common_title" -> s.tool("field_common_title")
            "common_content" -> s.tool("field_common_content")
            "external_notifications" -> s.tool("field_external_notifications")
            "priority" -> s.tool("field_priority")
            "schedule" -> s.tool("field_schedule")
            "creation_horizon_days" -> s.tool("field_creation_horizon_days")
            "validity_window_minutes" -> s.tool("field_validity_window_minutes")
            "status" -> s.tool("field_status")
            "notification_sent" -> s.tool("field_notification_sent")
            "read" -> s.tool("field_read")
            "archived" -> s.tool("field_archived")
            "triggered_by" -> s.tool("field_triggered_by")
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
    // Scheduling
    // ========================================

    override fun getScheduler(): ToolScheduler {
        return MessageScheduler
    }
}
