package com.assistant.core.ai.data

import android.content.Context
import com.assistant.core.ai.providers.AIProviderRegistry
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.ScheduleSettings
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.SettingsSchemaGenerator
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory

/**
 * An automation's settings, declared with the fields (docs/DATA.md): its name,
 * the provider it runs with, its group, whether it is on, its schedule, and what it does about
 * the runs it missed. Not its zone (its place), its seed session (its message) nor its history.
 *
 * AutomationService checks every write against the schema generated from here, on the settings
 * as they are about to be stored.
 */
object AutomationSettings {

    /** Catch-up choices: missed runs are caught up to a delay, or whatever their delay. */
    const val LIMITED = "limited"
    const val UNLIMITED = "unlimited"

    const val SCHEMA_ID = "automation_config"

    /**
     * What a scheduled automation does about the runs it missed, stored under "catch_up": the
     * choice is required, with no default -- an hour and several weeks are equally common --, and a
     * limited catch-up says its delay.
     */
    fun catchUpNodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        val limits = listOf(LIMITED, UNLIMITED)
        return listOf(
            SettingNode.Variant(
                selector = SettingNode.Field(FieldDefinition("limit", s.shared("automation_catch_up_window"), null, FieldType.CHOICE, false,
                    mapOf("options" to ChoiceSettings.storedOptions(limits, mapOf(
                        LIMITED to s.shared("automation_catch_up_limited"),
                        UNLIMITED to s.shared("automation_catch_up_unlimited"))))), required = true),
                cases = mapOf(
                    LIMITED to listOf(SettingNode.Field(FieldDefinition("window", s.shared("automation_catch_up_delay"), null, FieldType.DURATION, false,
                        mapOf("precision" to "MINUTE", "form" to "COMPOSED")), required = true)),
                    UNLIMITED to emptyList()
                )
            ),
            SettingNode.Field(FieldDefinition("dismiss_older_instances", s.shared("automation_catch_up_run_every"), null, FieldType.BOOLEAN, false,
                mapOf("true_label" to s.shared("automation_catch_up_latest_only"), "false_label" to s.shared("automation_catch_up_every_occurrence"))),
                default = false)
        )
    }

    fun nodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        val providers = AIProviderRegistry(context).getAllProviders()
        return listOf(
            SettingNode.Field(FieldDefinition("name", s.shared("label_name"), null, FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name)), required = true),
            SettingNode.Field(FieldDefinition("provider_id", s.shared("label_ai_provider"), null, FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(providers.map { it.getProviderId() },
                    providers.associate { it.getProviderId() to it.getDisplayName() }))), required = true),
            // One of the tool groups of the automation's zone
            SettingNode.Field(FieldDefinition("group", s.shared("label_group"), null, FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name))),
            SettingNode.Field(FieldDefinition("is_enabled", s.shared("automation_is_enabled"), null, FieldType.BOOLEAN, false, null), default = true),
            // Absent: the automation runs on demand only
            SettingNode.Group("schedule", s.shared("automation_schedule"), ScheduleSettings.nodes(s::shared)),
            // Present with the schedule, and only with it (AutomationService)
            SettingNode.Group("catch_up", s.shared("automation_catch_up_title"), catchUpNodes(context))
        )
    }

    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = SCHEMA_ID,
            displayName = s.shared("automation_display_name"),
            description = "",
            category = SchemaCategory.AUTOMATION_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(context), s::shared).toString()
        )
    }
}
