package com.assistant.core.fields

import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import org.json.JSONObject

/**
 * What a REFERENCE field accepts, from its config: `{"target": {"kinds": [...], "tool_instances": [...]}}`.
 *
 * [kinds] are the kinds of thing a value may designate; [toolInstances], with ENTRY among the
 * kinds, restricts the entries to those of these tool instances ("aliment": the entries of
 * Aliments and Recipes), and the input then lists their entries. Empty, any entry is accepted.
 */
data class ReferenceTarget(val kinds: Set<ReferenceKind>, val toolInstances: List<String>) {

    /** Whether [reference] is of a kind this field takes. Where an entry lives is checked apart, it needs the database. */
    fun acceptsKind(reference: Reference): Boolean = reference.kind in kinds

    /** Whether an entry of [toolInstanceId] may be designated. */
    fun acceptsEntryOf(toolInstanceId: String): Boolean = toolInstances.isEmpty() || toolInstanceId in toolInstances

    companion object {
        const val TARGET = "target"
        const val KINDS = "kinds"
        const val TOOL_INSTANCES = "tool_instances"

        /** The target of a field's [config]; no kind when the config says none, which its validation refuses. */
        fun fromConfig(config: Map<String, Any>?): ReferenceTarget {
            val target = config?.get(TARGET) as? Map<*, *>
            val kinds = (target?.get(KINDS) as? List<*>)
                ?.mapNotNull { name -> ReferenceKind.entries.firstOrNull { it.name == name } }?.toSet()
                ?: emptySet()
            val instances = (target?.get(TOOL_INSTANCES) as? List<*>)?.mapNotNull { referenceOf(it)?.id ?: it as? String } ?: emptyList()
            return ReferenceTarget(kinds, instances)
        }

        /**
         * The reference a stored value holds, as a map (an entry read back) or a JSON object; null
         * when it is none. The tool instances of a target are stored as references too, being the
         * values of a REFERENCE setting.
         */
        fun referenceOf(value: Any?): Reference? {
            val kindName: Any?
            val id: Any?
            when (value) {
                is Map<*, *> -> { kindName = value["kind"]; id = value["id"] }
                is JSONObject -> { kindName = value.opt("kind"); id = value.opt("id") }
                else -> return null
            }
            val kind = ReferenceKind.entries.firstOrNull { it.name == kindName } ?: return null
            return runCatching { Reference(kind, (id as? String)?.takeIf { it.isNotEmpty() }) }.getOrNull()
        }
    }
}
