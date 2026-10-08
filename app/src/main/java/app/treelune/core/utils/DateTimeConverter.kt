package app.treelune.core.utils

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Centralized date/time conversion utilities.
 *
 * Architecture:
 * - Everywhere in the app: UTC timestamps (Long milliseconds) -- interface, dispatcher,
 *   services, database
 * - Facing the model only: ISO 8601 with timezone offset, beside the relative periods it writes
 * - Conversion at that boundary and nowhere else
 *
 * Handles:
 * - ISO 8601 parsing with or without offset
 * - Timestamp formatting with timezone offset
 * - Recursive JSON conversion of the results that are not a tool's entries (ModelValues
 *   converts those, by their schema)
 *
 * Usage:
 * - Going in: CommandTransformer for the bounds of a period, and ModelValues for an entry
 * - Coming out: CommandExecutor, where a result becomes the text the model reads
 * - Always pass explicit timezone from AppConfigManager.getDateTimeConfig().getZoneId()
 */
object DateTimeConverter {

    /**
     * The names under which a result that is not a tool's entries carries an instant: a zone's
     * or a tool's created_at and updated_at, the current date's timestamp. An entry's dates are
     * found by its schema instead (ModelValues), which knows its fixed and user fields.
     */
    private val TIMESTAMP_FIELD_NAMES = setOf("timestamp", "created_at", "updated_at")

    /**
     * Parse ISO 8601 string to UTC timestamp.
     *
     * Accepts two formats:
     * - With offset: "2025-03-15T14:30:00+01:00" → parses explicit offset
     * - Without offset: "2025-03-15T14:30:00" → assumes appTimezone
     *
     * @param iso ISO 8601 string (with or without offset)
     * @param appTimezone App timezone from config (used if no offset in ISO)
     * @return UTC timestamp in milliseconds
     * @throws IllegalArgumentException if ISO format is invalid
     */
    fun isoToTimestamp(iso: String, appTimezone: ZoneId): Long {
        return try {
            // Try parsing with offset first (ISO 8601 with timezone)
            val zonedDateTime = try {
                ZonedDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } catch (e: DateTimeParseException) {
                // No offset found, parse as local datetime in appTimezone
                val localDateTime = java.time.LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                localDateTime.atZone(appTimezone)
            }

            // Convert to UTC epoch milliseconds
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid ISO 8601 format: $iso", e)
        }
    }

    /**
     * Format UTC timestamp to ISO 8601 string with timezone offset.
     *
     * Always includes offset for clarity and safety.
     * Example: 1710460800000 → "2025-03-15T14:30:00+01:00"
     *
     * @param timestamp UTC timestamp in milliseconds
     * @param appTimezone App timezone from config (for display)
     * @return ISO 8601 string with timezone offset
     */
    fun timestampToISO(timestamp: Long, appTimezone: ZoneId): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = instant.atZone(appTimezone)
        return zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /**
     * Recursively convert, in a result that is not a tool's entries, the instants carried under
     * the known names into ISO 8601 strings.
     *
     * @param json JSON object potentially containing timestamps
     * @param appTimezone App timezone from config
     * @return New JSONObject with ISO strings instead of timestamps
     */
    fun timestampsToISO(json: JSONObject, appTimezone: ZoneId): JSONObject {
        val result = JSONObject()

        json.keys().forEach { key ->
            result.put(key, convertValueTimestampToISO(key, json.get(key), appTimezone))
        }

        return result
    }

    private fun convertValueTimestampToISO(key: String, value: Any, appTimezone: ZoneId): Any {
        return when (value) {
            is JSONObject -> timestampsToISO(value, appTimezone)
            is JSONArray -> {
                val result = JSONArray()
                for (i in 0 until value.length()) {
                    result.put(convertValueTimestampToISO("", value.get(i), appTimezone))
                }
                result
            }
            is Long, is Int -> if (key in TIMESTAMP_FIELD_NAMES) timestampToISO((value as Number).toLong(), appTimezone) else value
            else -> value
        }
    }

    /**
     * Heuristic check if a string looks like ISO 8601 datetime.
     * Matches patterns like:
     * - "2025-03-15T14:30:00"
     * - "2025-03-15T14:30:00+01:00"
     * - "2025-03-15T14:30:00Z"
     */
    fun looksLikeISO8601(value: String): Boolean {
        // ISO 8601 datetime pattern: YYYY-MM-DDTHH:MM:SS[.mmm][Z|±HH:MM]
        // Simplified regex for detection (not strict validation)
        val iso8601Pattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")
        return iso8601Pattern.containsMatchIn(value)
    }
}
