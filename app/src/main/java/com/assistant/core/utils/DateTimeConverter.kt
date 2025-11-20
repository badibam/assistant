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
 * - Internal storage: UTC timestamps (Long milliseconds)
 * - External interfaces (UI + AI): ISO 8601 with timezone offset
 * - Conversion in services (transparent for business logic)
 *
 * Handles:
 * - ISO 8601 parsing with or without offset
 * - Timestamp formatting with timezone offset
 * - Recursive JSON conversion for tool data
 * - Custom fields DATETIME type
 *
 * Usage:
 * - Services: Convert input ISO → timestamps before validation/persistence
 * - Services: Convert output timestamps → ISO before returning to UI/AI
 * - Always pass explicit timezone from AppConfigManager.getDateTimeConfig().getZoneId()
 */
object DateTimeConverter {

    // Timestamp field names to detect automatically in JSON
    private val TIMESTAMP_FIELD_NAMES = setOf(
        "timestamp",
        "created_at",
        "updated_at",
        "lastActivity",
        "scheduledExecutionTime",
        "nextExecutionTime",
        "executionTime",
        "lastEventTime",
        "lastUserInteractionTime"
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
    fun timestampsToISO(json: JSONObject, appTimezone: ZoneId): JSONObject {
        val result = JSONObject()

        json.keys().forEach { key ->
            val value = json.get(key)
            result.put(key, convertValueTimestampToISO(key, value, appTimezone))
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
    private fun convertValueTimestampToISO(key: String, value: Any, appTimezone: ZoneId): Any {
        return when (value) {
            is JSONObject -> timestampsToISO(value, appTimezone)
            is JSONArray -> convertArrayTimestampToISO(value, appTimezone)
            is Long -> {
                // Convert if key is a known timestamp field
                if (isTimestampField(key)) {
                    timestampToISO(value, appTimezone)
                } else {
                    value
                }
            }
            is Int -> {
                // Convert if key is a known timestamp field (int might be truncated timestamp)
                if (isTimestampField(key)) {
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
    private fun convertArrayTimestampToISO(array: JSONArray, appTimezone: ZoneId): JSONArray {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            result.put(convertValueTimestampToISO("", value, appTimezone))
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
     * Check if a key is a known timestamp field name.
     */
    private fun isTimestampField(key: String): Boolean {
        return key in TIMESTAMP_FIELD_NAMES
    }

    /**
     * Heuristic check if a string looks like ISO 8601 datetime.
     * Matches patterns like:
     * - "2025-03-15T14:30:00"
     * - "2025-03-15T14:30:00+01:00"
     * - "2025-03-15T14:30:00Z"
     */
    private fun looksLikeISO8601(value: String): Boolean {
        // ISO 8601 datetime pattern: YYYY-MM-DDTHH:MM:SS[.mmm][Z|±HH:MM]
        // Simplified regex for detection (not strict validation)
        val iso8601Pattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")
        return iso8601Pattern.containsMatchIn(value)
    }
}
