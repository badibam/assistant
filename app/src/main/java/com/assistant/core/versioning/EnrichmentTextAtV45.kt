package com.assistant.core.versioning

import org.json.JSONObject

/**
 * Brings the rich content of messages to its v45 form: the segments alone, each enrichment its
 * type and config.
 *
 * Before, a message also stored the text of each block ("preview" for the screen, "prompt_preview"
 * for the AI) and the whole message as the AI read it ("linear_text"), all written when it was
 * sent: a pointer kept its target's name of that day. These texts are now written each time the
 * message is read (EnrichmentText); the stored ones go.
 *
 * Shared by the database migration and the backup import.
 */
object EnrichmentTextAtV45 {

    /** [json] without its stored texts, or null when it has none. */
    fun richContent(json: String): String? {
        val content = JSONObject(json)
        var changed = content.remove("linear_text") != null
        val segments = content.optJSONArray("segments")
        if (segments != null) {
            for (i in 0 until segments.length()) {
                val segment = segments.getJSONObject(i)
                if (segment.remove("preview") != null) changed = true
                if (segment.remove("prompt_preview") != null) changed = true
            }
        }
        return if (changed) content.toString() else null
    }

    /** Rewrites the rich content of the backup document's messages in place. */
    fun backup(data: JSONObject) {
        val messages = data.optJSONArray("session_messages") ?: return
        for (i in 0 until messages.length()) {
            val message = messages.getJSONObject(i)
            if (message.isNull("rich_content_json")) continue
            try {
                richContent(message.getString("rich_content_json"))?.let { message.put("rich_content_json", it) }
            } catch (e: Exception) {
                com.assistant.core.utils.LogManager.database("Backup import: message ${message.optString("id")} left as it was: ${e.message}", "ERROR", e)
            }
        }
    }
}
