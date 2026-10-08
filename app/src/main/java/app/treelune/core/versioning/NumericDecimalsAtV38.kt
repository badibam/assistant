package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings the NUMERIC and RANGE fields of a tool config to their v38 form, where every number says
 * how many decimals it takes (0 is a whole number). One that said nothing takes 2.
 *
 * A config holds such definitions in its user's fields ("extra_fields"), and a NUMERIC one in the
 * value settings of a numeric tracking tool ("value"); a counter's are set by its code. Shared by the
 * database migration and the backup import.
 */
object NumericDecimalsAtV38 {

    private const val DECIMALS = 2

    /** [config] of a [tooltype] tool, as it stands at v38. */
    fun config(tooltype: String, config: JSONObject): JSONObject {
        val next = JSONObject(config.toString())
        next.optJSONArray("extra_fields")?.let { fields ->
            for (i in 0 until fields.length()) {
                val field = fields.getJSONObject(i)
                if (field.optString("type") in setOf("NUMERIC", "RANGE")) {
                    val settings = field.optJSONObject("config") ?: JSONObject().also { field.put("config", it) }
                    decimals(settings)
                }
            }
        }
        if (tooltype == "tracking" && next.optString("type") == "numeric") {
            decimals(next.optJSONObject("value") ?: JSONObject().also { next.put("value", it) })
        }
        return next
    }

    /** Rewrites the backup document's tool configs in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            val config = JSONObject(instance.getString("config_json"))
            instance.put("config_json", config(instance.getString("tooltype"), config).toString())
        }
    }

    private fun decimals(settings: JSONObject) {
        if (!settings.has("decimals")) settings.put("decimals", DECIMALS)
    }
}
