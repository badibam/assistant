package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import app.treelune.core.utils.LogManager

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
 * - transformAppConfig(json, category, fromVersion, toVersion, context)
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
                // Apply tooltype-specific transformations
                transformed = when (tooltype) {
                    "tracking" -> transformTrackingConfig(transformed, version)
                    "journal" -> transformJournalConfig(transformed, version)
                    // Add other tooltypes as needed
                    else -> transformed // No transformation for unknown tooltypes
                }

                // Apply generic custom_fields transformation (all tooltypes)
                transformed = transformCustomFields(transformed, version)

                // v29 → v30: the former default icons become Lucide names, as in the
                // installed database (all tooltypes)
                if (version == 29) FormerDefaultIcons.rename(transformed)
            }

            return transformed.toString()
        } catch (e: Exception) {
            // Not fail-safe: handing back the untransformed config would write it into a
            // database that has already been wiped, in a format nothing reads any more.
            LogManager.service("Config transformation failed for $tooltype from v$fromVersion to v$toVersion: ${e.message}", "ERROR", e)
            throw IllegalStateException("Config transformation failed for $tooltype from v$fromVersion to v$toVersion: ${e.message}", e)
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
                    "messages" -> transformMessagesData(transformed, version)
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
            throw IllegalStateException("Data transformation failed for $tooltype from v$fromVersion to v$toVersion: ${e.message}", e)
        }
    }

    /**
     * Transform app configuration JSON
     *
     * @param json The app config JSON string to transform
     * @param category The settings category the JSON belongs to (format, ai_limits...)
     * @param fromVersion Source version (from backup metadata)
     * @param toVersion Target version (current app version)
     * @param context Required from a version below 20, whose step reads the phone's format
     * @return Transformed JSON string
     */
    fun transformAppConfig(
        json: String,
        category: String,
        fromVersion: Int,
        toVersion: Int,
        context: android.content.Context? = null
    ): String {
        if (fromVersion >= toVersion) return json

        try {
            var transformed = JSONObject(json)

            // Apply sequential transformations
            for (version in fromVersion until toVersion) {
                transformed = when (version) {
                    19 -> migrateAppConfigFrom19To20(transformed, context)
                    31 -> if (category == AppSettingCategories.AI_LIMITS) AILimitsAtV32.rewrite(transformed) else transformed
                    32 -> SettingsAtV33.rewrite(category, transformed) ?: transformed
                    33 -> if (category == AppSettingCategories.AI_LIMITS) AILimitsAtV34.rewrite(transformed) else transformed
                    63 -> if (category == AppSettingCategories.AI_LIMITS) AILimitsAtV64.rewrite(transformed) else transformed
                    66 -> UiOneColumnAtV67.rewrite(category, transformed)
                    // Example future migration:
                    // 10 -> migrateAppConfigFrom10To11(transformed)
                    else -> transformed // No migrations
                }
            }

            return transformed.toString()
        } catch (e: Exception) {
            LogManager.service("App config transformation failed from v$fromVersion to v$toVersion: ${e.message}", "ERROR", e)
            throw IllegalStateException("App config transformation failed from v$fromVersion to v$toVersion: ${e.message}", e)
        }
    }

    // ============================================================
    // Private transformation functions for app config
    // ============================================================

    /**
     * Migrate app config from version 19 to 20
     * Fills null use_24_hour_format and date_format_pattern with system-detected values
     */
    private fun migrateAppConfigFrom19To20(json: JSONObject, context: android.content.Context?): JSONObject {
        try {
            // Fill null use_24_hour_format
            if (!json.has("use_24_hour_format") || json.isNull("use_24_hour_format")) {
                val systemValue = app.treelune.core.config.FormatDefaults.getSystemDefault24HourFormat(
                    requireNotNull(context) { "v19->v20 reads the phone's format and needs a Context" }
                )
                json.put("use_24_hour_format", systemValue)
                LogManager.service("JSON Transform 19->20: Filled use_24_hour_format with system value: $systemValue", "INFO")
            }

            // Fill null date_format_pattern
            if (!json.has("date_format_pattern") || json.isNull("date_format_pattern")) {
                val systemValue = app.treelune.core.config.FormatDefaults.getSystemDefaultDatePattern(
                    requireNotNull(context) { "v19->v20 reads the phone's format and needs a Context" }
                )
                json.put("date_format_pattern", systemValue)
                LogManager.service("JSON Transform 19->20: Filled date_format_pattern with system value: $systemValue", "INFO")
            }
        } catch (e: Exception) {
            LogManager.service("JSON Transform 19->20: Failed to fill null format values: ${e.message}", "ERROR", e)
            throw IllegalStateException("JSON Transform 19->20: failed to fill null format values: ${e.message}", e)
        }
        return json
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
     * Transform messages tool data
     * Handles version-specific migrations for messages data JSON
     */
    private fun transformMessagesData(json: JSONObject, version: Int): JSONObject {
        return when (version) {
            13 -> {
                // v13→v14: Remove the executions array that message entries used to embed.
                // Only reachable through an old backup: nothing writes this shape any more.
                if (json.has("executions")) {
                    LogManager.service("transformMessagesData v13->v14: Removing executions array", "INFO")
                    json.remove("executions")
                }
                json
            }
            14 -> {
                // v14→v15: Remove schema_id from data.properties for consistency with other tooltypes
                // schema_id should only exist at entry root level (systemManaged), not in data object
                val dataObject = json.optJSONObject("data")
                if (dataObject != null && dataObject.has("schema_id")) {
                    LogManager.service("transformMessagesData v14->v15: Removing schema_id from data object", "INFO")
                    dataObject.remove("schema_id")
                    json.put("data", dataObject)
                }
                json
            }
            else -> json // No migrations for this version
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
            18 -> {
                // v18→v19: Remove transcription_metadata completely (transcription system removed)
                // Transcription is now handled by external app, so metadata no longer needed
                if (json.has("transcription_metadata")) {
                    json.remove("transcription_metadata")
                    LogManager.service("transformGenericToolData v18->v19: Removed transcription_metadata", "INFO")
                } else {
                    LogManager.service("transformGenericToolData v18->v19: No transcription_metadata to remove", "DEBUG")
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
     * Transforms custom_fields array in tool config from old TEXT types to new TEXT + length.
     *
     * Migration v25 → v26: Custom fields TEXT type refactoring
     * - TEXT_SHORT → TEXT with config.length = SHORT
     * - TEXT_LONG → TEXT with config.length = LONG
     * - TEXT_UNLIMITED → TEXT with config.length = UNLIMITED
     *
     * @param json The config JSONObject containing custom_fields array
     * @param version The source version (migration applied for v25 only)
     * @return Transformed JSONObject with updated custom_fields
     */
    private fun transformCustomFields(json: JSONObject, version: Int): JSONObject {
        // v28 → v29: the dead min/max bounds of a DATE or DATETIME field leave the config, as
        // they do from the installed database. Same code both ways round, so an imported backup
        // and an upgraded install end up with the same config.
        if (version == 28) {
            val removed = DateFieldBounds.strip(json)
            if (removed > 0) {
                LogManager.service("Removed $removed dead date bound(s) for v28→v29", "DEBUG")
            }
            return json
        }

        // Only apply for v25 → v26 migration
        if (version != 25) return json

        try {
            // Check if custom_fields array exists
            if (!json.has("custom_fields")) return json

            val customFields = json.optJSONArray("custom_fields") ?: return json
            val transformedFields = org.json.JSONArray()

            for (i in 0 until customFields.length()) {
                val field = customFields.getJSONObject(i)
                val type = field.optString("type")

                when (type) {
                    "TEXT_SHORT" -> {
                        field.put("type", "TEXT")
                        val config = org.json.JSONObject()
                        config.put("length", "SHORT")
                        field.put("config", config)
                    }
                    "TEXT_LONG" -> {
                        field.put("type", "TEXT")
                        val config = org.json.JSONObject()
                        config.put("length", "LONG")
                        field.put("config", config)
                    }
                    "TEXT_UNLIMITED" -> {
                        field.put("type", "TEXT")
                        val config = org.json.JSONObject()
                        config.put("length", "UNLIMITED")
                        field.put("config", config)
                    }
                    // Other types unchanged
                }

                transformedFields.put(field)
            }

            json.put("custom_fields", transformedFields)
            LogManager.service("Transformed custom_fields for v25→v26", "DEBUG")
        } catch (e: Exception) {
            LogManager.service("Failed to transform custom_fields: ${e.message}", "ERROR", e)
            throw IllegalStateException("Failed to transform custom_fields: ${e.message}", e)
        }

        return json
    }

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
