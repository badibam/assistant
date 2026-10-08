package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings a questionnaire's entries to their v65 form, where every entry has its status
 * (docs/design/entry-start-state.md). One written with no state, by the AI passing it in a chat
 * or by an import, was shown filled by the questionnaire's screen alone: it now says so in its
 * state, for the tile and the filters too. Its moment of filling is not known, and stays unset.
 *
 * Shared by the database migration and the backup import.
 */
object QuestionnaireStateAtV65 {

    const val TOOLTYPE = "questionnaire"

    /** The state an entry of a [tooltype] tool has at v65, null when [state] stays as it is. */
    fun state(tooltype: String, state: String?): String? {
        if (tooltype != TOOLTYPE) return null
        if (state != null && JSONObject(state).optString("status").isNotEmpty()) return null
        val next = if (state.isNullOrBlank()) JSONObject() else JSONObject(state)
        return next.put("status", "filled").toString()
    }

    /** Rewrites the backup document's questionnaire entries in place. */
    fun backup(data: JSONObject) {
        val entries = data.optJSONArray("tool_data") ?: return
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val current = if (entry.isNull("state")) null else entry.optString("state")
            state(entry.getString("tooltype"), current)?.let { entry.put("state", it) }
        }
    }
}
