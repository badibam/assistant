package com.assistant.core.demo

import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo as the app ships it (docs/design/demo.md): `assets/demo/structure.json` holds the
 * parameters of the operations that build it — zones, tools, variables — under ids the app gives
 * and with no text of its own: a string written `@key` is a text, given by
 * `assets/demo/texts-<lang>.json` in the phone's language. Pure: the service reads the files and
 * hands them here, and runs what comes back through the services.
 *
 * A zone or a tool carries its place in its grid section beside its parameters, as `grid_x` and
 * `grid_y`; the installer places them once the section is built.
 *
 * @property group The name of the home screen's zone group the demo's zones stand in
 * @property zones The `zones.create` parameters of each zone, with its place
 * @property tools The `tools.create` parameters of each tool, with its place, in the order they
 *           are created: a tool another one names comes before it
 * @property variables The `variables.create` parameters of each variable, in the order they are
 *           created: a variable another one names comes before it
 */
class DemoContent(
    val group: String,
    val zones: List<JSONObject>,
    val tools: List<JSONObject>,
    val variables: List<JSONObject>
) {

    companion object {

        /** What every id the demo writes starts with, and how a reinstall finds what to remove. */
        const val PREFIX = "demo-"

        /** What every variable name of the demo ends with: a variable's name is unique in the app. */
        const val VARIABLE_SUFFIX = "_demo"

        /** The keys of a zone's or a tool's place, which are no parameter of its create. */
        val PLACE = listOf("grid_x", "grid_y")

        /**
         * The demo read from [structure], its texts given by [texts].
         *
         * @throws IllegalStateException when a text key has no text, an id lacks the prefix or a
         *         variable's name the suffix: a demo half named is not installed
         */
        fun read(structure: JSONObject, texts: JSONObject): DemoContent {
            val resolved = resolve(structure, texts) as JSONObject
            fun list(key: String) = resolved.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
            val zones = list("zones")
            val tools = list("tools")
            val variables = list("variables")
            (zones + tools + variables).forEach { item ->
                val id = item.getString("id")
                check(id.startsWith(PREFIX)) { "Demo id '$id' lacks the prefix $PREFIX" }
            }
            variables.forEach { variable ->
                val name = variable.getString("name")
                check(name.endsWith(VARIABLE_SUFFIX)) { "Demo variable '$name' lacks the suffix $VARIABLE_SUFFIX" }
            }
            return DemoContent(resolved.getString("zone_group"), zones, tools, variables)
        }

        /** [value] with each string `@key` replaced by the text of key, at any depth. */
        private fun resolve(value: Any?, texts: JSONObject): Any? = when (value) {
            is JSONObject -> JSONObject().also { out -> value.keys().forEach { out.put(it, resolve(value.get(it), texts)) } }
            is JSONArray -> JSONArray().also { out -> (0 until value.length()).forEach { out.put(resolve(value.get(it), texts)) } }
            is String -> if (value.startsWith("@")) {
                val key = value.substring(1)
                texts.optString(key).takeIf { it.isNotEmpty() } ?: error("Demo text '$key' missing")
            } else value
            else -> value
        }

        /** [item] without its place: the parameters of its create. */
        fun paramsOf(item: JSONObject): JSONObject =
            JSONObject(item.toString()).apply { PLACE.forEach { remove(it) } }
    }
}
