package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings the text fields of a tool config to their v61 form: TEXT_SHORT, TEXT_LONG and
 * TEXT_UNLIMITED become a TEXT whose settings say its length (SHORT, LONG, UNLIMITED).
 *
 * The three types left the app at backup v26, but only a backup was rewritten then: an installed
 * database kept them in its users' fields ("extra_fields"), and a tool holding one could not be
 * read at all. A backup taken from such a database carries them too, whatever version it records.
 * Shared by the database migration and the backup import.
 */
object TextLengthAtV61 {

    private val LENGTHS = mapOf(
        "TEXT_SHORT" to "SHORT",
        "TEXT_LONG" to "LONG",
        "TEXT_UNLIMITED" to "UNLIMITED"
    )

    /** [config] as it stands at v61. */
    fun config(config: JSONObject): JSONObject {
        val next = JSONObject(config.toString())
        next.optJSONArray("extra_fields")?.let { fields ->
            for (i in 0 until fields.length()) {
                val field = fields.getJSONObject(i)
                val length = LENGTHS[field.optString("type")] ?: continue
                field.put("type", "TEXT")
                val settings = field.optJSONObject("config") ?: JSONObject().also { field.put("config", it) }
                settings.put("length", length)
            }
        }
        return next
    }

    /** Rewrites the backup document's tool configs in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            instance.put("config_json", config(JSONObject(instance.getString("config_json"))).toString())
        }
    }
}
