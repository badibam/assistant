package com.assistant.core.fields.settings

import org.json.JSONObject

/**
 * A config read through its declaration: the single way code reads a setting.
 *
 * An absent setting means its declared default, or nothing when it has none: that is its meaning,
 * written once in the declaration, not a fallback each reader invents. A name the declaration
 * does not hold is a mistake in the code, and fails loudly.
 */
class SettingValues(private val nodes: List<SettingNode>, private val config: JSONObject) {

    /** The stored value of [name], or its declared default, or null. */
    fun value(name: String): Any? {
        val field = declared(name) ?: error("Setting '$name' is not declared")
        return if (config.has(name) && !config.isNull(name)) config.get(name) else field.default
    }

    fun string(name: String): String? = value(name)?.toString()

    fun number(name: String): Number? = value(name) as? Number

    /** A boolean setting, which has a value or a default: absence means its default. */
    fun boolean(name: String): Boolean = value(name) as? Boolean ?: error("Setting '$name' has neither a value nor a default")

    /** The field named [name] among the settings this config holds, its chosen variants included. */
    private fun declared(name: String): SettingNode.Field? = fieldIn(nodes, name)

    private fun fieldIn(nodes: List<SettingNode>, name: String): SettingNode.Field? {
        for (node in SettingsSchemaGenerator.flatten(nodes)) {
            when (node) {
                is SettingNode.Field -> if (node.definition.name == name) return node
                is SettingNode.Variant -> {
                    if (node.selector.definition.name == name) return node.selector
                    val option = config.optString(node.selector.definition.name).ifEmpty { node.selector.default?.toString() }
                    node.cases[option]?.let { fieldIn(it, name) }?.let { return it }
                }
                else -> Unit
            }
        }
        return null
    }
}
