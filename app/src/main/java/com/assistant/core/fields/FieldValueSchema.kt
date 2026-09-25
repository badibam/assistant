package com.assistant.core.fields

import org.json.JSONArray
import org.json.JSONObject

/**
 * The JSON schema of one field's value, from its type and config.
 *
 * Every schema an entry is held to is built from these, whoever declared the field: the core
 * (name, timestamp), the tool type (data, state) or the user (extra). See EntrySchemaGenerator.
 */
object FieldValueSchema {

    /**
     * The mark of a value stored as an instant in milliseconds. The model reads and writes it as
     * an ISO 8601 date-time (SchemaModelView for the schema, ModelValues for the values).
     */
    const val EPOCH_MILLIS = "epoch-millis"

    /**
     * The mark of a value stored as a duration in milliseconds. The model reads and writes it as
     * an ISO 8601 duration, like PT1H25M.
     */
    const val DURATION_MILLIS = "duration-millis"

    /**
     * The schema of a field's value as a reader gets it -- the AI, an entry or a setting alike:
     * what it is held to, its label as the title, and in its description what the value means
     * ([reading]) before what the field's own description says.
     */
    fun forReader(field: FieldDefinition, text: (String) -> String): JSONObject {
        val schema = of(field).put("title", field.displayName)
        val description = listOfNotNull(reading(field, text), field.description).joinToString(" ")
        if (description.isNotEmpty()) schema.put("description", description)
        return schema
    }

    /**
     * What a reader needs besides the value to understand it, from the field's settings: its
     * unit, what the bounds of a scale or the two answers of a boolean mean, a choice's options
     * and whether it is open or a ranking. Null when the value says it all.
     *
     * A value alone is 3, 5 or true; this is what makes it "3 on a scale where 5 is Very good",
     * "5 km", "true, meaning Read". The entry schema carries it, so the AI reads an entry with
     * its schema and nothing else.
     *
     * @param text The shared string of a key (s::shared)
     */
    fun reading(fieldDef: FieldDefinition, text: (String) -> String): String? {
        val config = fieldDef.config
        fun setting(key: String): String? = (config?.get(key) as? String)?.takeIf { it.isNotBlank() }
        fun bound(key: String, labelKey: String): String? {
            val number = (config?.get(key) as? Number)?.let { n ->
                if (n.toDouble() % 1.0 == 0.0) n.toLong().toString() else n.toString()
            } ?: return null
            return setting(labelKey)?.let { text("field_reading_bound").format(number, it) } ?: number
        }

        val parts = when (fieldDef.type) {
            FieldType.NUMERIC -> listOfNotNull(
                setting("unit")?.let { text("field_reading_unit").format(it) },
                NumericPrecision.decimalsOf(fieldDef).let { d ->
                    if (d == 0) text("field_reading_whole") else text("field_reading_decimals").format(d)
                }
            )
            FieldType.RANGE ->
                listOfNotNull(setting("unit")?.let { text("field_reading_unit").format(it) })
            FieldType.SCALE ->
                if (setting("min_label") == null && setting("max_label") == null) emptyList()
                else listOf(text("field_reading_scale").format(bound("min", "min_label"), bound("max", "max_label")))
            FieldType.BOOLEAN ->
                if (setting("true_label") == null && setting("false_label") == null) emptyList()
                else listOf(text("field_reading_boolean").format(setting("true_label") ?: "true", setting("false_label") ?: "false"))
            FieldType.CHOICE -> {
                val choice = ChoiceSettings.fromConfig(config)
                val options = choice.options.joinToString(", ") { option ->
                    choice.labelOf(option).takeIf { it != option }?.let { text("field_reading_bound").format(option, it) } ?: option
                }
                listOfNotNull(
                    options.takeIf { it.isNotEmpty() }?.let { text("field_reading_options").format(it) },
                    text("field_reading_open").takeIf { choice.open },
                    text("field_reading_ordered").takeIf { choice.shape == ChoiceShape.ORDERED }
                )
            }
            else -> emptyList()
        }
        return parts.joinToString(" ").ifEmpty { null }
    }

    /**
     * The JSON schema a value of [fieldDef] is held to, from its type and config.
     * The single place a field's value schema is written, whoever declared the field.
     */
    fun of(fieldDef: FieldDefinition): JSONObject {
        return when (fieldDef.type) {
            FieldType.TEXT -> {
                JSONObject().apply {
                    put("type", "string")

                    // Get length from config (default UNLIMITED)
                    val lengthStr = fieldDef.config?.get("length") as? String
                    val length = TextLength.fromString(lengthStr)

                    // Only set maxLength if not UNLIMITED
                    if (length != TextLength.UNLIMITED) {
                        put("maxLength", length.getLimit())
                    }

                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.NUMERIC -> {
                JSONObject().apply {
                    put("type", "number")

                    val config = fieldDef.config
                    (config?.get("min") as? Number)?.let { put("minimum", it) }
                    (config?.get("max") as? Number)?.let { put("maximum", it) }

                    // Its decimals are a precision, not a rule: a write is rounded to them
                    // (NumericPrecision), so the schema holds any number
                    NumericPrecision.decimalsOf(fieldDef)

                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.SCALE -> {
                JSONObject().apply {
                    put("type", "number")

                    fieldDef.config?.let { config ->
                        (config["min"] as? Number)?.let { put("minimum", it) }
                        (config["max"] as? Number)?.let { put("maximum", it) }

                        // The stops of the slider. multipleOf counts from 0, not from min: the
                        // config validation keeps min and max on the step so the two agree.
                        val step = (config["step"] as? Number)?.toDouble() ?: 1.0
                        if (step > 0) {
                            put("multipleOf", step)
                        }
                    }

                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.CHOICE -> {
                // An open choice is held to its options too: the write that brings a new value
                // adds it to the options first, and the schema is built from that config.
                if (ChoiceSettings.fromConfig(fieldDef.config).shape.isList) {
                    // Multiple choice or ranking: array of distinct strings with enum validation.
                    // A ranking need not hold every option, so an option added later leaves the
                    // rankings already stored valid.
                    JSONObject().apply {
                        put("type", "array")

                        val itemSchema = JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(ChoiceSettings.fromConfig(fieldDef.config).options))
                        }
                        put("items", itemSchema)
                        put("uniqueItems", true)

                        if (fieldDef.description != null) {
                            put("description", fieldDef.description)
                        }
                    }
                } else {
                    // Single choice: string with enum validation
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(ChoiceSettings.fromConfig(fieldDef.config).options))

                        if (fieldDef.description != null) {
                            put("description", fieldDef.description)
                        }
                    }
                }
            }

            FieldType.BOOLEAN -> {
                JSONObject().apply {
                    put("type", "boolean")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.RANGE -> {
                JSONObject().apply {
                    put("type", "object")

                    val startSchema = JSONObject().apply {
                        put("type", "number")
                        fieldDef.config?.let { config ->
                            (config["min"] as? Number)?.let { put("minimum", it) }
                            (config["max"] as? Number)?.let { put("maximum", it) }
                        }
                    }

                    val endSchema = JSONObject().apply {
                        put("type", "number")
                        fieldDef.config?.let { config ->
                            (config["min"] as? Number)?.let { put("minimum", it) }
                            (config["max"] as? Number)?.let { put("maximum", it) }
                        }
                    }

                    val properties = JSONObject().apply {
                        put("start", startSchema)
                        put("end", endSchema)
                    }
                    put("properties", properties)

                    val required = JSONArray().apply {
                        put("start")
                        put("end")
                    }
                    put("required", required)

                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.DATE -> {
                JSONObject().apply {
                    put("type", "string")
                    put("format", "date")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.TIME -> {
                JSONObject().apply {
                    put("type", "string")
                    put("pattern", "^([01]?[0-9]|2[0-3]):[0-5][0-9]$")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.DATETIME -> {
                JSONObject().apply {
                    // Milliseconds, like every other instant the app stores. An ISO string
                    // written without an offset is wall-clock time: the same text would mean
                    // different moments in different timezones, and would sort wrong across a
                    // clock change. DATE and TIME stay strings, being a day and an hour rather
                    // than an instant.
                    put("type", "number")
                    put("minimum", 0)
                    // Marks the property as an instant, which is what lets the view handed to
                    // the model show it as ISO while the stored form stays a number. A plain
                    // NUMBER field carries no such mark and stays a number on both sides.
                    put("format", EPOCH_MILLIS)
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.DURATION -> {
                JSONObject().apply {
                    // Milliseconds, whatever the precision: the precision decides what the form
                    // offers and the screen shows, not what is stored, so a stopwatch can write
                    // the exact time it measured into a field entered in minutes.
                    put("type", "integer")
                    put("minimum", 0)
                    // Marks the property as a duration, which is what lets the view handed to
                    // the model show it in ISO 8601 (PT1H25M) while the stored form stays a number.
                    put("format", DURATION_MILLIS)
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }
        }
    }
}
