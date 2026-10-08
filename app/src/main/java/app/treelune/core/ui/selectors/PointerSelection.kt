package app.treelune.core.ui.selectors

import app.treelune.core.ai.enrichments.PointerConfig
import app.treelune.core.selection.ReferenceKind
import org.json.JSONObject

/**
 * What the pointer selector holds while a pointer is built: the selection of entries (SelectionDraft)
 * and what is attached, a zone's or a tool's config and its entries. Narrowing the entries counts
 * whether they are attached or only mentioned.
 */
data class PointerSelection(
    val draft: SelectionDraft = SelectionDraft(),
    val config: Boolean = false,
    val entries: Boolean = false
) {
    /** A pointer can be made once a zone or a tool is reached. */
    val complete: Boolean get() = draft.level == ReferenceKind.ZONE || draft.level == ReferenceKind.TOOL_INSTANCE

    /** The pointer to store: a selection of the core, its period apart from its conditions. */
    fun pointer(): PointerConfig = PointerConfig(selection = draft.selection(), config = config, entries = entries)

    fun toJson(): String = JSONObject()
        .put("draft", draft.toJson())
        .put("config", config)
        .put("entries", entries)
        .toString()

    companion object {
        fun fromJson(saved: String): PointerSelection {
            val json = JSONObject(saved)
            return PointerSelection(
                draft = SelectionDraft.fromJson(json.getJSONObject("draft")),
                config = json.getBoolean("config"),
                entries = json.getBoolean("entries")
            )
        }
    }
}
