package com.assistant.core.ai.data

import org.json.JSONArray
import org.json.JSONObject

/** Whether a command reads the data or changes it. */
enum class CommandKind { QUERY, ACTION }

/**
 * One parameter of a command, at the top of its params: its name, the JSON type its value takes
 * ("string", "integer", "number", "boolean", "array", "object"), and whether it is required.
 * What lies inside an array or an object is the business of the transformer and the service
 * the command goes to, which know the tool it names.
 */
data class CommandParam(val name: String, val type: String, val required: Boolean = false)

/**
 * A command the AI may send: its type, what it does to the data, and its parameters.
 *
 * @property docKey The string (ai_prompt_chunks.xml) holding its text for the model: what it
 *   does, each parameter in a "- `name` (type, optional|**required**)" line, its notes. Two
 *   commands may share one text (START_DURATION and STOP_DURATION).
 * @property writesEntries Whether it writes values into a tool's entries, and so waits on that
 *   tool's entries schema
 */
data class AICommand(
    val type: String,
    val kind: CommandKind,
    val params: List<CommandParam>,
    val docKey: String = "ai_command_${type.lowercase()}",
    val writesEntries: Boolean = false
) {
    /** Its params as a JSON schema: each parameter typed, the required ones listed, no other one accepted. */
    fun schema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply { params.forEach { put(it.name, JSONObject().put("type", it.type)) } })
        val required = params.filter { it.required }.map { it.name }
        if (required.isNotEmpty()) put("required", JSONArray(required))
        put("additionalProperties", false)
    }

    /**
     * What is wrong with [given] at the top of its params, one problem per line: a parameter it
     * does not take, one required and missing, one of the wrong type. Empty when nothing is.
     *
     * A parameter it does not take is refused rather than dropped: dropped, the command would
     * run without it, and a period the AI believed set would read the whole history.
     *
     * @param message The text of a problem, from its string key and its arguments
     */
    fun problems(given: Map<String, Any?>, message: (String, Array<Any>) -> String): List<String> {
        val known = params.associateBy { it.name }
        val names = params.joinToString(", ") { it.name }.ifEmpty { "-" }
        return buildList {
            for (name in given.keys - known.keys) add(message("service_error_param_unknown", arrayOf(name, names)))
            for (param in params) {
                val value = given[param.name]
                when {
                    value == null -> if (param.required) add(message("ai_error_param_missing", arrayOf(param.name)))
                    !holds(value, param.type) -> add(message("ai_error_param_type", arrayOf(param.name, param.type)))
                }
            }
        }
    }

    /** Whether [value], as a JSON reader hands it over, is of [type]; a whole number read as 1.0 is an integer. */
    private fun holds(value: Any, type: String): Boolean = when (type) {
        "string" -> value is String
        "integer" -> value is Number && value.toDouble() % 1.0 == 0.0
        "number" -> value is Number
        "boolean" -> value is Boolean
        "array" -> value is List<*> || value is JSONArray
        "object" -> value is Map<*, *> || value is JSONObject
        else -> throw IllegalStateException("Unknown parameter type $type")
    }
}

/**
 * The commands the AI may send, each declared once. What reads them: the forced response schema
 * (the types it lists), AICommandProcessor (the params it checks), the L1 prompt (the texts it
 * assembles, in this order), and the actions' own lists (writing entries, verbalized).
 * AICommandsTest holds each text to its declaration.
 */
object AICommands {

    private fun p(name: String, type: String, required: Boolean = false) = CommandParam(name, type, required)
    private fun query(type: String, vararg params: CommandParam) = AICommand(type, CommandKind.QUERY, params.toList())
    private fun action(type: String, vararg params: CommandParam, docKey: String? = null, writesEntries: Boolean = false) =
        AICommand(type, CommandKind.ACTION, params.toList(), docKey ?: "ai_command_${type.lowercase()}", writesEntries)

    val ALL: List<AICommand> = listOf(
        query("TOOL_DATA",
            p("id", "string", true), p("fields", "array", true), p("period", "object"), p("filters", "array"),
            p("limit", "integer"), p("page", "integer"), p("running", "boolean")),
        query("TOOL_CONFIG", p("id", "string", true)),
        query("TOOL_INSTANCES", p("zone_id", "string")),
        query("ZONE_CONFIG", p("id", "string", true)),
        query("ZONES"),
        query("APP_STATE"),
        query("VARIABLES", p("zone_id", "string")),
        query("READING", p("variable", "string", true), p("at", "array", true)),
        query("FILE", p("id", "string", true), p("start_line", "integer"), p("lines", "integer")),
        query("IMPORT_PLAN", p("file", "string", true), p("tool_instance_id", "string", true)),
        query("CURRENT_DATETIME"),
        query("SCHEMA", p("tooltype", "string"), p("tool_instance_id", "string"), p("id", "string")),
        query("ICONS", p("categories", "array"), p("query", "array")),

        action("CREATE_DATA", p("tool_instance_id", "string", true), p("entries", "array", true), writesEntries = true),
        action("UPDATE_DATA", p("tool_instance_id", "string", true), p("entries", "array", true), writesEntries = true),
        action("DELETE_DATA", p("tool_instance_id", "string", true), p("ids", "array", true)),
        action("START_DURATION", p("tool_instance_id", "string", true), p("id", "string", true), p("field", "string", true),
            docKey = "ai_command_duration", writesEntries = true),
        action("STOP_DURATION", p("tool_instance_id", "string", true), p("id", "string", true), p("field", "string", true),
            docKey = "ai_command_duration", writesEntries = true),
        action("TOOL_OPERATION", p("tool_instance_id", "string", true), p("operation", "string", true), p("params", "object"),
            writesEntries = true),
        action("CREATE_TOOL", p("zone_id", "string", true), p("tooltype", "string", true), p("config", "object", true)),
        action("UPDATE_TOOL",
            p("tool_instance_id", "string", true), p("config", "object"), p("zone_id", "string"),
            p("confirm_migration", "boolean"), p("fill_values", "object")),
        action("DELETE_TOOL", p("tool_instance_id", "string", true)),
        action("CREATE_ZONE",
            p("name", "string", true), p("description", "string"), p("icon_name", "string"),
            p("group", "string"), p("display_mode", "string"), p("tool_groups", "array")),
        action("UPDATE_ZONE",
            p("zone_id", "string", true), p("name", "string"), p("description", "string"), p("icon_name", "string"),
            p("group", "string"), p("display_mode", "string"), p("tool_groups", "array")),
        action("DELETE_ZONE", p("zone_id", "string", true)),
        action("CREATE_VARIABLE", p("zone_id", "string", true), p("name", "string", true), p("group", "string"), p("definition", "object", true)),
        action("UPDATE_VARIABLE",
            p("variable_id", "string", true), p("name", "string"), p("definition", "object"), p("group", "string"), p("zone_id", "string")),
        action("DELETE_VARIABLE", p("variable_id", "string", true)),
        action("IMPORT_DATA", p("file", "string", true), p("tool_instance_id", "string", true), p("columns", "array", true))
    )

    private val byType = ALL.associateBy { it.type }

    fun find(type: String): AICommand? = byType[type]

    val queries: List<AICommand> get() = ALL.filter { it.kind == CommandKind.QUERY }
    val actions: List<AICommand> get() = ALL.filter { it.kind == CommandKind.ACTION }

    /** The texts of [commands], each once, in their order: two commands sharing a text give it once. */
    fun docKeys(commands: List<AICommand>): List<String> = commands.map { it.docKey }.distinct()
}
