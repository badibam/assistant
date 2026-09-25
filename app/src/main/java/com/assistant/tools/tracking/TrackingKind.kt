package com.assistant.tools.tracking

import com.assistant.core.fields.FieldType
import org.json.JSONObject

/**
 * What a tracking tool follows, as its config's "type" says. It decides the field type of the
 * entries' main field, "value", and the quick way the tool offers to create an entry.
 *
 * A counter is a whole NUMERIC value entered with +/- buttons, one entry per press; a timer is a
 * DURATION entered with a stopwatch. An occurrence has no value at all: the entry is the fact
 * that something happened, its name and its moment.
 */
enum class TrackingKind(val key: String, val valueType: FieldType?) {
    NUMERIC("numeric", FieldType.NUMERIC),
    COUNTER("counter", FieldType.NUMERIC),
    SCALE("scale", FieldType.SCALE),
    CHOICE("choice", FieldType.CHOICE),
    BOOLEAN("boolean", FieldType.BOOLEAN),
    TEXT("text", FieldType.TEXT),
    TIMER("timer", FieldType.DURATION),
    OCCURRENCE("occurrence", null);

    companion object {
        fun fromKey(key: String): TrackingKind =
            entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown tracking type: $key")

        /** @throws IllegalArgumentException if the config's type is missing or unknown */
        fun of(config: JSONObject): TrackingKind = fromKey(config.getString("type"))
    }
}
