package app.treelune.core.ui

import androidx.compose.runtime.saveable.Saver
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.fields.toJsonArray
import app.treelune.core.fields.toJson
import app.treelune.core.fields.toFieldDefinition
import app.treelune.core.fields.toFieldConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Savers for rememberSaveable, for the state types the saved-instance Bundle cannot hold.
 *
 * Screen state that the user produced (form input, selections, what is being edited) must
 * survive activity recreation, which a rotation triggers. Each saver stores its value as a
 * JSON string, the form these objects already have in the database.
 */

val JsonObjectSaver: Saver<JSONObject, String> = Saver(
    save = { it.toString() },
    restore = { JSONObject(it) }
)

val NullableJsonObjectSaver: Saver<JSONObject?, String> = Saver(
    save = { it?.toString() },
    restore = { JSONObject(it) }
)



val StringListSaver: Saver<List<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toList() }
)

val MutableStringListSaver: Saver<MutableList<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toMutableList() }
)

/** A message being composed, kept in its stored form (RichMessage JSON). */
val MessageSegmentsSaver: Saver<List<app.treelune.core.ai.data.MessageSegment>, String> = Saver(
    save = { app.treelune.core.ai.data.RichMessage(it).toJson() },
    restore = {
        app.treelune.core.ai.data.RichMessage.fromJson(it)?.segments
            ?: throw IllegalStateException("Saved message segments could not be parsed")
    }
)

val NullablePeriodSaver: Saver<app.treelune.core.ui.components.Period?, String> = Saver(
    save = { it?.let { period -> "${period.type.name}:${period.timestamp}" } },
    restore = {
        val (type, timestamp) = it.split(":", limit = 2)
        app.treelune.core.ui.components.Period(timestamp.toLong(), app.treelune.core.ui.components.PeriodType.valueOf(type))
    }
)

val PeriodSaver: Saver<app.treelune.core.ui.components.Period, String> = Saver(
    save = { "${it.type.name}:${it.timestamp}" },
    restore = {
        val (type, timestamp) = it.split(":", limit = 2)
        app.treelune.core.ui.components.Period(timestamp.toLong(), app.treelune.core.ui.components.PeriodType.valueOf(type))
    }
)




/** Custom field values: nulls are kept (an emptied field differs from an untouched one). */
val FieldValuesSaver: Saver<Map<String, Any?>, String> = Saver(
    save = { valueToJson(it).toString() },
    restore = { @Suppress("UNCHECKED_CAST") (jsonToValue(JSONObject(it)) as Map<String, Any?>) }
)

private fun valueToJson(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), valueToJson(v)) } }
    is Iterable<*> -> JSONArray().apply { value.forEach { put(valueToJson(it)) } }
    else -> value
}

private fun jsonToValue(value: Any?): Any? = when (value) {
    JSONObject.NULL -> null
    is JSONObject -> value.keys().asSequence().associateWith { jsonToValue(value.get(it)) }
    is JSONArray -> (0 until value.length()).map { jsonToValue(value.get(it)) }
    else -> value
}

/** Item properties (no nulls): same JSON form as FieldValuesSaver. */
val PropertiesSaver: Saver<Map<String, Any>, String> = Saver(
    save = { valueToJson(it).toString() },
    restore = { @Suppress("UNCHECKED_CAST") (jsonToValue(JSONObject(it)) as Map<String, Any>) }
)

/** Any kotlinx-serializable value, kept as its JSON form. */
fun <T> serializableSaver(serializer: kotlinx.serialization.KSerializer<T>): Saver<T, String> = Saver(
    save = { kotlinx.serialization.json.Json.encodeToString(serializer, it) },
    restore = { kotlinx.serialization.json.Json.decodeFromString(serializer, it) }
)
