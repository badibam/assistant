package com.assistant.core.fields.settings

import org.json.JSONObject

/**
 * The config a declaration's defaults make: what a creation form prefills and a tool not created
 * yet is described by. A suggestion, never written by the service into a config it is given.
 */
object SettingDefaults {

    /** Every declared default of [nodes], groups nested, a variant's own under its default option. */
    fun of(nodes: List<SettingNode>): JSONObject {
        val config = JSONObject()
        SettingsSchemaGenerator.flatten(nodes).forEach { node ->
            when (node) {
                is SettingNode.Field -> node.default?.let { config.put(node.definition.name, it) }
                is SettingNode.Group -> of(node.nodes).takeIf { it.length() > 0 }?.let { config.put(node.name, it) }
                is SettingNode.Variant -> node.selector.default?.let { option ->
                    config.put(node.selector.definition.name, option)
                    val case = of(node.cases[option].orEmpty())
                    case.keys().forEach { config.put(it, case.get(it)) }
                }
                is SettingNode.ListOf, is SettingNode.Section, is SettingNode.Condition,
                is SettingNode.Term, is SettingNode.Selection, is SettingNode.Period -> Unit
            }
        }
        return config
    }
}
