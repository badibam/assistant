package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings the stored schedules to their v51 form: a schedule is its pattern alone, without the
 * instants it ran between ("start_date", "end_date"). Where a schedule is stored: an automation's
 * schedule_json, and the "schedule" of a Messages, Goal or Questionnaire config.
 *
 * Shared by the database migration and the backup import.
 */
object ScheduleDatesAtV51 {

    /** The tool types whose config holds a schedule under "schedule". */
    val SCHEDULED_TOOLTYPES = setOf("messages", "goal", "questionnaire")

    private val DATES = listOf("start_date", "end_date")

    /** The JSON of a schedule, without its dates. */
    fun schedule(json: String): String {
        val schedule = JSONObject(json)
        DATES.forEach { schedule.remove(it) }
        return schedule.toString()
    }

    /** The JSON of a [tooltype] config, its schedule without dates. */
    fun toolConfig(tooltype: String, json: String): String {
        if (tooltype !in SCHEDULED_TOOLTYPES) return json
        val config = JSONObject(json)
        val schedule = config.optJSONObject("schedule") ?: return json
        DATES.forEach { schedule.remove(it) }
        return config.toString()
    }

    /** Rewrites the backup document's automations and tool configs in place. */
    fun backup(data: JSONObject) {
        data.optJSONArray("automations")?.let { automations ->
            for (i in 0 until automations.length()) {
                val automation = automations.getJSONObject(i)
                val json = automation.optString("schedule_json").takeIf { !automation.isNull("schedule_json") && it.isNotEmpty() }
                    ?: continue
                automation.put("schedule_json", schedule(json))
            }
        }
        data.optJSONArray("tool_instances")?.let { tools ->
            for (i in 0 until tools.length()) {
                val tool = tools.getJSONObject(i)
                tool.put("config_json", toolConfig(tool.getString("tooltype"), tool.getString("config_json")))
            }
        }
    }
}
