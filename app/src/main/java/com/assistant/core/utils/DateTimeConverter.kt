package com.assistant.core.utils

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
 * - Recursive JSON conversion for tool data
 *
 * Usage:
 * - Going in: AICommandProcessor for the payloads of the model's commands, CommandTransformer
 *   for the bounds of a period
 * - Coming out: CommandExecutor, where a result becomes the text the model reads
 * - Always pass explicit timezone from AppConfigManager.getDateTimeConfig().getZoneId()
 */
object DateTimeConverter {

    // Timestamp field names to detect automatically in JSON
    private val TIMESTAMP_FIELD_NAMES = setOf(
        "timestamp",
        "created_at",
        "updated_at",
        "last_activity",
        "scheduled_execution_time",
        "execution_time",
        "last_event_time",
        "last_user_interaction_time"
    )

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
     * Recursively convert ISO datetime strings to timestamps in JSON.
     *
     * Converts:
     * - Known timestamp field names (timestamp, created_at, etc.)
     * - Custom fields with DATETIME type (detected in custom_fields object)
     *
     * @param json JSON object potentially containing ISO datetime strings
     * @param appTimezone App timezone from config
     * @return New JSONObject with timestamps (Long) instead of ISO strings
     */
    fun isoToTimestamps(json: JSONObject, appTimezone: ZoneId): JSONObject {
        val result = JSONObject()

        json.keys().forEach { key ->
            val value = json.get(key)
            result.put(key, convertValueIsoToTimestamp(key, value, appTimezone))
        }

        return result
    }

    /**
     * Recursively convert timestamps to ISO datetime strings in JSON.
     *
     * Converts:
     * - Known timestamp field names (timestamp, created_at, etc.)
     * - Custom fields with DATETIME type (detected in custom_fields object)
     *
     * @param json JSON object potentially containing timestamps
     * @param appTimezone App timezone from config
     * @return New JSONObject with ISO strings instead of timestamps (Long)
     */
    fun timestampsToISO(json: JSONObject, appTimezone: ZoneId, alsoNamed: Set<String> = emptySet()): JSONObject {
        val result = JSONObject()

        json.keys().forEach { key ->
            val value = json.get(key)
            result.put(key, convertValueTimestampToISO(key, value, appTimezone, alsoNamed))
        }

        return result
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Convert a single value from ISO to timestamp recursively.
     * Handles objects, arrays, and primitive values.
     */
    private fun convertValueIsoToTimestamp(key: String, value: Any, appTimezone: ZoneId): Any {
        return when (value) {
            is JSONObject -> isoToTimestamps(value, appTimezone)
            is JSONArray -> convertArrayIsoToTimestamp(value, appTimezone)
            is String -> {
                // Convert if key is a known timestamp field or looks like ISO 8601
                if (shouldConvertToTimestamp(key, value)) {
                    try {
                        isoToTimestamp(value, appTimezone)
                    } catch (e: IllegalArgumentException) {
                        // Not a valid ISO string, keep as-is
                        value
                    }
                } else {
                    value
                }
            }
            else -> value
        }
    }

    /**
     * Convert a single value from timestamp to ISO recursively.
     * Handles objects, arrays, and primitive values.
     */
    private fun convertValueTimestampToISO(key: String, value: Any, appTimezone: ZoneId, alsoNamed: Set<String>): Any {
        return when (value) {
            is JSONObject -> timestampsToISO(value, appTimezone, alsoNamed)
            is JSONArray -> convertArrayTimestampToISO(value, appTimezone, alsoNamed)
            is Long -> {
                // Convert if key is a known timestamp field
                if (isTimestampField(key, alsoNamed)) {
                    timestampToISO(value, appTimezone)
                } else {
                    value
                }
            }
            is Int -> {
                // Convert if key is a known timestamp field (int might be truncated timestamp)
                if (isTimestampField(key, alsoNamed)) {
                    timestampToISO(value.toLong(), appTimezone)
                } else {
                    value
                }
            }
            else -> value
        }
    }

    /**
     * Convert array elements recursively (ISO to timestamp).
     */
    private fun convertArrayIsoToTimestamp(array: JSONArray, appTimezone: ZoneId): JSONArray {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            result.put(convertValueIsoToTimestamp("", value, appTimezone))
        }
        return result
    }

    /**
     * Convert array elements recursively (timestamp to ISO).
     */
    private fun convertArrayTimestampToISO(array: JSONArray, appTimezone: ZoneId, alsoNamed: Set<String>): JSONArray {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            result.put(convertValueTimestampToISO("", value, appTimezone, alsoNamed))
        }
        return result
    }

    /**
     * Check if a field should be converted to timestamp.
     * True if key is a known timestamp field or value looks like ISO 8601.
     */
    private fun shouldConvertToTimestamp(key: String, value: String): Boolean {
        return isTimestampField(key) || looksLikeISO8601(value)
    }

    /**
     * Check if a key names a timestamp: one of the app's own, or one the caller names.
     *
     * A caller names the extra ones because only it knows them. A DATETIME custom field is a
     * timestamp whose name the user chose, so no fixed list can hold it -- the tool's schema is
     * what says which fields are dates.
     */
    private fun isTimestampField(key: String, alsoNamed: Set<String> = emptySet()): Boolean {
        return key in TIMESTAMP_FIELD_NAMES || key in alsoNamed
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
