package com.assistant.core.fields.settings

import org.json.JSONArray
import org.json.JSONObject

/** What a config becomes when a variant changes option: the form's switch, kept apart to be tested. */
object SettingVariants {

    /**
     * [config], the object stored under [level], once [variant] moves to option [to]: what the new
     * option does not declare leaves, since it belonged to the former one -- a whole setting, or the
     * part of a setting both options have that only the former understood (a shortcut's value) --,
     * and the new option's defaults come in where nothing is stored.
     */
    fun switched(config: JSONObject, level: List<SettingNode>, variant: SettingNode.Variant, to: String): JSONObject {
        val next = JSONObject(config.toString()).put(variant.selector.definition.name, to)
        val defaults = SettingDefaults.of(variant.cases[to].orEmpty())
        defaults.keys().forEach { if (!next.has(it)) next.put(it, defaults.get(it)) }
        return declared(level, next)
    }

    /** What of [config] the [nodes] declare, the variants read with the options [config] holds. */
    fun declared(nodes: List<SettingNode>, config: JSONObject): JSONObject {
        val kept = JSONObject()
        fun keep(level: List<SettingNode>) {
            SettingsSchemaGenerator.flatten(level).forEach { node ->
                when (node) {
                    is SettingNode.Field -> config.opt(node.definition.name)?.let { kept.put(node.definition.name, it) }
                    is SettingNode.Group -> config.optJSONObject(node.name)?.let { kept.put(node.name, declared(node.nodes, it)) }
                    is SettingNode.ListOf -> config.optJSONArray(node.name)?.let { list ->
                        kept.put(node.name, when (val item = node.item) {
                            is SettingNode.Item.Of -> JSONArray((0 until list.length()).map { i ->
                                list.optJSONObject(i)?.let { declared(item.nodes, it) } ?: list.get(i)
                            })
                            is SettingNode.Item.Value -> list
                        })
                    }
                    is SettingNode.Variant -> {
                        val selector = node.selector.definition.name
                        config.opt(selector)?.let { kept.put(selector, it) }
                        val option = config.optString(selector).ifEmpty { node.selector.default?.toString() }
                        keep(node.cases[option].orEmpty())
                    }
                    is SettingNode.Section -> Unit
                }
            }
        }
        keep(nodes)
        return kept
    }
}
