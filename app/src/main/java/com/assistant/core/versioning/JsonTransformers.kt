package com.assistant.core.versioning

import org.json.JSONObject
import com.assistant.core.utils.LogManager

/**
 * Centralized JSON transformations for versioning
 *
 * Architecture:
 * - Single source of truth for all JSON migrations
 * - Reused across: Room migrations, full backup import, partial imports (future)
 * - Sequential application: transforms from version N to N+1, then N+1 to N+2, etc.
 *
 * Lifecycle:
 * - Transformers are never deleted (support old backups indefinitely)
 * - Each transformer numbered by SOURCE version (v9→v10 = case "9")
 * - Accumulation: cases 9, 19, 29... as app evolves
 *
 * Usage:
 * - transformToolConfig(json, tooltype, fromVersion, toVersion)
 * - transformToolData(json, tooltype, fromVersion, toVersion)
 * - transformAppConfig(json, fromVersion, toVersion)
 */
object JsonTransformers {

    /**
     * Transform tool instance configuration JSON
     *
     * @param json The config JSON string to transform
     * @param tooltype The tool type (tracking, journal, etc.)
     * @param fromVersion Source version (from backup metadata)
     * @param toVersion Target version (current app version)
     * @return Transformed JSON string
     */
    fun transformToolConfig(
        json: String,
        tooltype: String,
        fromVersion: Int,
        toVersion: Int
    ): String {
        if (fromVersion >= toVersion) return json

        try {
            var transformed = JSONObject(json)

            // Apply sequential transformations
            for (version in fromVersion until toVersion) {
                transformed = when (tooltype) {
                    "tracking" -> transformTrackingConfig(transformed, version)
                    "journal" -> transformJournalConfig(transformed, version)
                    // Add other tooltypes as needed
                    else -> transformed // No transformation for unknown tooltypes
                }
            }

            return transformed.toString()
        } catch (e: Exception) {
            LogManager.service("Config transformation failed for $tooltype from v$fromVersion to v$toVersion: ${e.message}", "ERROR", e)
            return json // Return original on error (fail-safe)
        }
    }

    /**
     * Transform tool data entry JSON
     *
     * @param json The data JSON string to transform
     * @param tooltype The tool type (tracking, journal, etc.)
     * @param fromVersion Source version (from backup metadata)
     * @param toVersion Target version (current app version)
     * @return Transformed JSON string
     */
    fun transformToolData(
        json: String,
        tooltype: String,
        fromVersion: Int,
        toVersion: Int
    ): String {
        if (fromVersion >= toVersion) {
            LogManager.service("transformToolData: Skipping $tooltype (fromVersion=$fromVersion >= toVersion=$toVersion)", "DEBUG")
            return json
        }

        LogManager.service("transformToolData: Starting $tooltype from v$fromVersion to v$toVersion", "INFO")

        try {
            var transformed = JSONObject(json)

            // Apply sequential transformations
            for (version in fromVersion until toVersion) {
                LogManager.service("transformToolData: Applying v$version->v${version+1} for $tooltype", "DEBUG")

                // Apply tooltype-specific transformations
                transformed = when (tooltype) {
                    "tracking" -> transformTrackingData(transformed, version)
                    "journal" -> transformJournalData(transformed, version)
                    // Add other tooltypes as needed
                    else -> transformed // No transformation for unknown tooltypes
                }

                // Apply generic transformations (all tooltypes)
                val beforeGeneric = transformed.toString()
                transformed = transformGenericToolData(transformed, version)
                val afterGeneric = transformed.toString()

                if (beforeGeneric != afterGeneric) {
                    LogManager.service("transformToolData: Generic transformation v$version->v${version+1} modified data for $tooltype", "INFO")
                }
            }

            LogManager.service("transformToolData: Completed $tooltype transformation", "DEBUG")
            return transformed.toString()
        } catch (e: Exception) {
            LogManager.service("Data transformation failed for $tooltype from v$fromVersion to v$toVersion: ${e.message}", "ERROR", e)
            return json // Return original on error (fail-safe)
        }
    }

    /**
     * Transform app configuration JSON
     *
     * @param json The app config JSON string to transform
     * @param fromVersion Source version (from backup metadata)
     * @param toVersion Target version (current app version)
     * @return Transformed JSON string
     */
    fun transformAppConfig(
        json: String,
        fromVersion: Int,
        toVersion: Int
    ): String {
        if (fromVersion >= toVersion) return json

        try {
            var transformed = JSONObject(json)

            // Apply sequential transformations
            for (version in fromVersion until toVersion) {
                transformed = when (version) {
                    // Example future migration:
                    // 10 -> migrateAppConfigFrom10To11(transformed)
                    else -> transformed // No migrations yet
                }
            }

            return transformed.toString()
        } catch (e: Exception) {
            LogManager.service("App config transformation failed from v$fromVersion to v$toVersion: ${e.message}", "ERROR", e)
            return json // Return original on error (fail-safe)
        }
    }

    // ============================================================
    // Private transformation functions per tooltype
    // ============================================================

    /**
     * Transform tracking tool configuration
     * Handles version-specific migrations for tracking config JSON
     */
    private fun transformTrackingConfig(json: JSONObject, version: Int): JSONObject {
        return when (version) {
            // Example future migration:
            // 10 -> {
            //     // Migrate from v10 to v11
            //     if (json.has("unit")) {
            //         json.put("measurement_unit", json.getString("unit"))
            //         json.remove("unit")
            //     }
            //     json
            // }
            else -> json // No migrations yet
        }
    }

    /**
     * Transform tracking tool data
     * Handles version-specific migrations for tracking data JSON
     */
    private fun transformTrackingData(json: JSONObject, version: Int): JSONObject {
        return when (version) {
            // Example future migration:
            // 10 -> {
            //     // Migrate data structure from v10 to v11
            //     json
            // }
            else -> json // No migrations yet
        }
    }

    /**
     * Transform journal tool configuration
     * Handles version-specific migrations for journal config JSON
     */
    private fun transformJournalConfig(json: JSONObject, version: Int): JSONObject {
        return when (version) {
            // Future migrations will be added here
            else -> json // No migrations yet
        }
    }

    /**
     * Transform journal tool data
     * Handles version-specific migrations for journal data JSON
     */
    private fun transformJournalData(json: JSONObject, version: Int): JSONObject {
        return when (version) {
            // Future migrations will be added here
            else -> json // No migrations yet
        }
    }

    /**
     * Transform generic tool data (applies to ALL tooltypes)
     * Handles cross-tooltype migrations like transcription metadata cleanup
     */
    private fun transformGenericToolData(json: JSONObject, version: Int): JSONObject {
        return when (version) {
            12 -> {
                // v12→v13: Clean segments_texts from transcription_metadata
                // This applies to all tools with transcribed fields (journal, tracking, notes, etc.)
                val metadata = json.optJSONObject("transcription_metadata")
                if (metadata != null) {
                    LogManager.service("transformGenericToolData v12->v13: Found transcription_metadata, cleaning segments_texts", "INFO")
                    var cleanedFieldsCount = 0
                    val fieldNames = metadata.keys()
                    while (fieldNames.hasNext()) {
                        val fieldName = fieldNames.next()
                        val fieldMetadata = metadata.optJSONObject(fieldName)
                        // Remove segments_texts if present (duplicates full text already in field)
                        if (fieldMetadata?.has("segments_texts") == true) {
                            fieldMetadata.remove("segments_texts")
                            cleanedFieldsCount++
                            LogManager.service("transformGenericToolData v12->v13: Cleaned segments_texts from field '$fieldName'", "DEBUG")
                        }
                    }
                    if (cleanedFieldsCount > 0) {
                        LogManager.service("transformGenericToolData v12->v13: Cleaned $cleanedFieldsCount field(s)", "INFO")
                    }
                } else {
                    LogManager.service("transformGenericToolData v12->v13: No transcription_metadata found", "DEBUG")
                }
                json
            }
            else -> json // No generic migrations for this version
        }
    }

    // Add more tooltype-specific transformers as needed:
    // - transformGoalConfig/Data
    // - transformChartConfig/Data
    // - transformListConfig/Data
    // etc.

    // ============================================================
    // Utility functions
    // ============================================================

    /**
     * Fix SchedulePattern type serialization format (v10 → v11)
     * Transforms old format to new format with @SerialName
     *
     * Old: "type":"com.assistant.core.utils.SchedulePattern.SpecificDates"
     * New: "type":"SpecificDates"
     *
     * Used by:
     * - Automation schedules (AutomationEntity.schedule column)
     * - Messages tool data (ToolDataEntity.data column with schedule field)
     * - Any JSON containing ScheduleConfig
     *
     * @param json The JSON string potentially containing SchedulePattern
     * @return Transformed JSON string with fixed type names
     */
    fun fixSchedulePatternTypes(json: String): String {
        if (!json.contains("com.assistant.core.utils.SchedulePattern")) {
            return json // No transformation needed
        }

        return json
            .replace("\"type\":\"com.assistant.core.utils.SchedulePattern.DailyMultiple\"", "\"type\":\"DailyMultiple\"")
            .replace("\"type\":\"com.assistant.core.utils.SchedulePattern.WeeklySimple\"", "\"type\":\"WeeklySimple\"")
            .replace("\"type\":\"com.assistant.core.utils.SchedulePattern.MonthlyRecurrent\"", "\"type\":\"MonthlyRecurrent\"")
            .replace("\"type\":\"com.assistant.core.utils.SchedulePattern.WeeklyCustom\"", "\"type\":\"WeeklyCustom\"")
            .replace("\"type\":\"com.assistant.core.utils.SchedulePattern.YearlyRecurrent\"", "\"type\":\"YearlyRecurrent\"")
            .replace("\"type\":\"com.assistant.core.utils.SchedulePattern.SpecificDates\"", "\"type\":\"SpecificDates\"")
    }
}
