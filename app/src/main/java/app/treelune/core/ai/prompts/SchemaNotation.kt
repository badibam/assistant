package app.treelune.core.ai.prompts

import app.treelune.core.fields.FieldValueSchema
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import org.json.JSONArray
import org.json.JSONObject

/**
 * A JSON schema written for the model to read: one line per value, nested by indentation, each
 * value with its label and its description once.
 *
 * The app validates against the JSON schema itself; this is only what the model reads of it. It
 * carries the same information in a fraction of the size: a variant (a oneOf whose branches a
 * selector's const tells apart) says once what its options share and then what each adds,
 * where the JSON form repeats the whole object in every branch, and the JSON Schema vocabulary
 * around each value (properties, required lists, additionalProperties) gives way to a legend
 * the prompt states once (ai_chunk_validation_schema_principle).
 *
 * It reads the model's view of a schema (SchemaModelView.forModel): dates and durations are
 * already ISO 8601 strings there.
 *
 * Every keyword a schema holds is either written or deliberately implied; an unknown one fails
 * rather than vanishes from what the model reads (UnknownKeyword).
 */
object SchemaNotation {

    private const val FIELD_DEFINITIONS = SettingsSchemaGenerator.FIELD_DEFINITIONS

    /** What a keyword is to the notation. */
    private val WRITTEN = setOf(
        "type", "properties", "required", "title", "description", "default", "enum", "const",
        "oneOf", "items", "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems",
        "uniqueItems", "multipleOf", "pattern", "format", "system_managed", SettingsSchemaGenerator.SECRET, FIELD_DEFINITIONS
    )

    /** Implied by the legend: only the keys listed are accepted. */
    private val IMPLIED = setOf("additionalProperties")

    /** A keyword the notation does not know: a schema holding one is not sent half-read. */
    class UnknownKeyword(keyword: String, path: String) :
        IllegalStateException("Schema keyword '$keyword' at $path has no notation for the model")

    /** [schema], an object's schema, as the notation. */
    fun render(schema: JSONObject): String = objectLines(schema, "", "$").joinToString("\n")

    private fun objectLines(schema: JSONObject, indent: String, path: String): List<String> {
        check(schema, path)
        schema.optJSONArray("oneOf")?.let { return variantLines(it, indent, path) }
        val properties = schema.optJSONObject("properties") ?: return emptyList()
        val required = stringSet(schema.optJSONArray("required"))
        return properties.keys().asSequence().toList().flatMap { name ->
            valueLines(name, properties.getJSONObject(name), name in required, indent, "$path.$name")
        }
    }

    /**
     * A oneOf: when one property holds a const in every branch, it is the selector, and what
     * every branch holds alike is written once, the rest under "if <selector> = <option>".
     * Otherwise each branch is written whole.
     */
    private fun variantLines(branches: JSONArray, indent: String, path: String): List<String> {
        val all = (0 until branches.length()).map { branches.getJSONObject(it) }
        all.forEachIndexed { i, branch -> check(branch, "$path.oneOf[$i]") }
        val props = all.map { it.optJSONObject("properties") }
        if (props.any { it == null }) return oneOfWhole(all, indent, path)
        val selector = props[0]!!.keys().asSequence().firstOrNull { key ->
            props.all { it!!.optJSONObject(key)?.has("const") == true }
        } ?: return oneOfWhole(all, indent, path)

        val requireds = all.map { stringSet(it.optJSONArray("required")) }
        fun same(key: String): Boolean {
            val first = props[0]!!.optJSONObject(key)?.toString() ?: return false
            return props.all { it!!.optJSONObject(key)?.toString() == first } &&
                requireds.all { (key in it) == (key in requireds[0]) }
        }
        val shared = props[0]!!.keys().asSequence().filter { it != selector && same(it) }.toList()

        val lines = mutableListOf<String>()
        shared.forEach { lines += valueLines(it, props[0]!!.getJSONObject(it), it in requireds[0], indent, "$path.$it") }

        // The selector, with every option
        val selectorSchema = JSONObject(props[0]!!.getJSONObject(selector).toString())
        selectorSchema.remove("const")
        selectorSchema.put("enum", JSONArray(props.map { it!!.getJSONObject(selector).get("const") }))
        lines += valueLines(selector, selectorSchema, true, indent, "$path.$selector")

        all.forEachIndexed { i, branch ->
            val option = props[i]!!.getJSONObject(selector).get("const")
            val own = props[i]!!.keys().asSequence().filter { it != selector && it !in shared }.toList()
            lines += "$indent  if $selector = $option:" + if (own.isEmpty()) " nothing more" else ""
            own.forEach { key ->
                lines += valueLines(key, props[i]!!.getJSONObject(key), key in requireds[i], "$indent    ", "$path.$key")
            }
            // A variant inside this option's own settings
            branch.optJSONArray("oneOf")?.let { lines += variantLines(it, "$indent    ", path) }
        }
        return lines
    }

    private fun oneOfWhole(branches: List<JSONObject>, indent: String, path: String): List<String> =
        branches.flatMapIndexed { i, branch ->
            listOf("$indent  one of (${i + 1}):") + objectLines(branch, "$indent    ", "$path.oneOf[$i]")
        }

    /** One value under [name], then what it holds, indented. */
    private fun valueLines(name: String, schema: JSONObject, required: Boolean, indent: String, path: String): List<String> {
        check(schema, path)
        val head = "$indent$name${if (required) "*" else ""}"
        val text = text(schema)
        val constraints = constraints(schema, required)

        if (schema.optBoolean(FIELD_DEFINITIONS)) {
            return listOf("$head: list of field definitions (see Fields)$constraints$text")
        }
        return when {
            schema.optString("type") == "array" -> {
                val items = schema.optJSONObject("items") ?: JSONObject()
                check(items, "$path[]")
                if (isObject(items)) listOf("$head: list of$constraints$text") + objectLines(items, "$indent  ", "$path[]")
                else listOf("$head: list of ${kind(items)}${constraints(items)}${text(items)}$constraints$text")
            }
            isObject(schema) -> listOf("$head:$constraints$text") + objectLines(schema, "$indent  ", path)
            else -> listOf("$head: ${kind(schema)}$constraints$text")
        }
    }

    /** An object written property by property; a reference is written as one value. */
    private fun isObject(schema: JSONObject) =
        (schema.has("properties") || schema.has("oneOf")) && schema.optString("format") != FieldValueSchema.REFERENCE

    /** What kind of value: its options, its format, or its type. */
    private fun kind(schema: JSONObject): String {
        schema.optJSONArray("enum")?.let { options -> return (0 until options.length()).joinToString("|") { options.get(it).toString() } }
        if (schema.has("const")) return "always ${schema.get("const")}"
        when (schema.optString("format")) {
            "date-time" -> return "ISO 8601 date-time with offset"
            "duration" -> return "ISO 8601 duration"
            "date" -> return "date YYYY-MM-DD"
            FieldValueSchema.REFERENCE -> {
                val kinds = schema.optJSONObject("properties")?.optJSONObject("kind")?.optJSONArray("enum")
                    ?.let { options -> (0 until options.length()).joinToString("|") { options.getString(it) } } ?: "any"
                return "reference {kind: $kinds, id}"
            }
        }
        return when (val type = schema.opt("type")) {
            is JSONArray -> (0 until type.length()).joinToString("|") { type.getString(it) }
            null -> "any"
            else -> type.toString()
        }
    }

    /** The bounds and flags of a value, in brackets. */
    private fun constraints(schema: JSONObject, required: Boolean = false): String {
        val bits = mutableListOf<String>()
        schema.opt("minimum")?.let { bits += "min $it" }
        schema.opt("maximum")?.let { bits += "max $it" }
        schema.opt("multipleOf")?.let { bits += "step $it" }
        schema.opt("minLength")?.let { bits += "min length $it" }
        schema.opt("maxLength")?.let { bits += "max length $it" }
        schema.opt("minItems")?.let { bits += "at least $it" }
        schema.opt("maxItems")?.let { bits += "at most $it" }
        if (schema.optBoolean("uniqueItems")) bits += "distinct"
        schema.opt("pattern")?.let { bits += "pattern $it" }
        // An optional value's default is what its absence means; a required one's only what to
        // write when nothing else is meant, since its absence is refused
        schema.opt("default")?.let { bits += (if (required) "suggested " else "default ") + if (it is String) "\"$it\"" else it.toString() }
        if (schema.optBoolean("system_managed")) bits += "written by the app, never sent"
        if (schema.optBoolean("secret")) bits += "secret, never shown"
        return if (bits.isEmpty()) "" else " [${bits.joinToString(", ")}]"
    }

    /** Its label and description, after a dash. */
    private fun text(schema: JSONObject): String {
        val parts = listOf(schema.optString("title"), schema.optString("description")).filter { it.isNotBlank() }
        return if (parts.isEmpty()) "" else " — " + parts.joinToString(". ")
    }

    /** Fails on a keyword the notation neither writes nor implies. */
    private fun check(schema: JSONObject, path: String) {
        schema.keys().forEach { key ->
            if (key !in WRITTEN && key !in IMPLIED) throw UnknownKeyword(key, path)
        }
    }

    private fun stringSet(array: JSONArray?): Set<String> =
        if (array == null) emptySet() else (0 until array.length()).map { array.getString(it) }.toSet()
}
