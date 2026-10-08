package app.treelune.core.ai.enrichments

import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import org.json.JSONObject

/**
 * A POINTER enrichment as stored in a message: a selection of the core (what it designates, and
 * what narrows its entries), and what of it goes to the AI. Stored as
 * `{"selection": …, "attach": {"config", "entries"}}`: the core reads the selection without
 * knowing the pointer.
 *
 * The two choices do not depend on each other. The selection's period and filters narrow the
 * entries whether they are attached or only mentioned: a mention of narrowed entries hands the
 * AI the query without running it. A relative date in them is resolved at each send, so an
 * automation's pointer rereads its period at every run.
 *
 * @property config Whether the target's config goes with the message, its schema with it
 * @property entries Whether the designated entries go with the message, their schema with them
 */
data class PointerConfig(
    val selection: EntrySelection,
    val config: Boolean = false,
    val entries: Boolean = false
) {
    val target: Reference get() = selection.target

    /** Neither config nor entries: the AI is told of the target and reads what it wants. */
    val isMention: Boolean get() = !config && !entries

    /** Whether the entries are narrowed, by a period or a value filter. */
    val narrowed: Boolean get() = !selection.period.isEmpty || selection.filters.length() > 0

    fun toJson(): JSONObject = JSONObject()
        .put("selection", selection.toJson())
        .put("attach", JSONObject().put("config", config).put("entries", entries))

    companion object {
        /** @throws IllegalArgumentException on a period bound that does not read */
        fun fromJson(json: JSONObject, text: (String) -> String): PointerConfig {
            val attach = json.optJSONObject("attach") ?: JSONObject()
            return PointerConfig(
                selection = EntrySelection.fromJson(json.getJSONObject("selection"), text),
                config = attach.optBoolean("config", false),
                entries = attach.optBoolean("entries", false)
            )
        }

        fun fromJson(json: String, text: (String) -> String): PointerConfig = fromJson(JSONObject(json), text)
    }
}
