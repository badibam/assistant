package com.assistant.core.services

import androidx.room.withTransaction
import android.content.Context
import com.assistant.core.config.DateTimeConfig
import com.assistant.core.grid.Groups
import com.assistant.core.ai.domain.AILimitsConfig
import com.assistant.core.config.ValidationConfig
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.config.AppSettingsDefaults
import com.assistant.core.config.AppSettings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.JsonUtils
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.strings.Strings
import org.json.JSONObject
import org.json.JSONArray

/**
 * Centralized service for application configuration management
 * Provides typed access to parameters stored by category in database
 */
class AppConfigService(private val context: Context) : ExecutableService {

    private val database = AppDatabase.getDatabase(context)
    private val settingsDao = database.appSettingsCategoryDao()
    private val s = Strings.`for`(context = context)

    // ===== Reading =====

    suspend fun getWeekStartDay(): String = getFormatSettings().getString("week_start_day")

    suspend fun getDayStartHour(): Int = getFormatSettings().getInt("day_start_hour")

    /** The locale the app formats with, or null to follow the phone. */
    suspend fun getLocaleOverride(): String? =
        AppSettings.read(AppSettingCategories.FORMAT, getFormatSettings(), context).string("locale_override")

    /** Relative label limits for period display, required by the format declaration. */
    suspend fun getRelativeLabelLimits(): JSONObject = getFormatSettings().getJSONObject("relative_label_limits")

    suspend fun getZoneGroups(): List<String> {
        val groupsJson = readSettings(AppSettingCategories.MAIN_SCREEN).getJSONArray("zone_groups")
        return (0 until groupsJson.length()).map { groupsJson.getString(it) }
    }

    private suspend fun getFormatSettings(): JSONObject = readSettings(AppSettingCategories.FORMAT)

    /**
     * Comprehensive date/time configuration, read through the format declaration: an absent
     * override follows the phone.
     *
     * Note: week_start_day is stored lowercase in DB but returned uppercase for DayOfWeek compatibility.
     */
    suspend fun getDateTimeConfig(): DateTimeConfig {
        val settings = AppSettings.read(AppSettingCategories.FORMAT, getFormatSettings(), context)
        return DateTimeConfig(
            timezoneOverride = settings.string("timezone_override"),
            localeOverride = settings.string("locale_override"),
            use24HourFormat = settings.value("use_24_hour_format") as Boolean?,
            dateFormatPattern = settings.string("date_format_pattern"),
            timeSeparator = settings.string("time_separator")!!,
            dayStartHour = settings.number("day_start_hour")!!.toInt(),
            weekStartDay = settings.string("week_start_day")!!.uppercase()
        )
    }

    suspend fun getAILimits(): AILimitsConfig =
        AILimitsConfig.fromSettingsJson(readSettings(AppSettingCategories.AI_LIMITS))

    /** Whether the theme's interface sounds play. */
    suspend fun getUISounds(): Boolean =
        readSettings(AppSettingCategories.UI).getBoolean(com.assistant.core.config.AppSettings.UI_SOUNDS)

    /** The interface's look: its theme, palette family, mode and size step. */
    suspend fun getUIAppearance(): com.assistant.core.themes.Appearance =
        com.assistant.core.themes.Appearance.from(readSettings(AppSettingCategories.UI))
            ?: error("The interface settings lack part of the appearance")

    /**
     * Get structured validation configuration
     * Hierarchy: app > tool > session > AI request (OR logic)
     */
    suspend fun getValidationConfig(): ValidationConfig =
        ValidationConfig.fromSettingsJson(readSettings(AppSettingCategories.VALIDATION_CONFIG))

    // ===== Writing =====

    /**
     * Replace the settings of [category] with [settings], once they are checked against the
     * schema generated from the category's declaration (AppSettings). The cached settings are
     * read again, so what is stored is what the app uses.
     *
     * The home screen's groups are held by the zones: a group renamed in [renames] (former name →
     * new name) is renamed in the zones that hold it, a group removed while a zone holds it is
     * refused (Groups).
     *
     * @return The error to hand back, or null once stored
     */
    suspend fun setSettings(category: String, settings: JSONObject, renames: Map<String, String> = emptyMap()): String? {
        if (category !in AppSettings.CATEGORIES) return s.shared("service_error_unknown_category").format(category)
        val validation = SchemaValidator.validate(AppSettings.schema(category, context), JsonUtils.toMap(settings), context)
        if (!validation.isValid) return validation.errorMessage ?: s.shared("message_validation_error_simple")

        val previous = readSettings(category) // a category never written gets its row first
        fun groups(json: JSONObject) = json.optJSONArray("zone_groups")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val change = Groups.Change(groups(previous), groups(settings), renames)
        if (category != AppSettingCategories.MAIN_SCREEN && renames.isNotEmpty()) return s.shared("service_error_group_renames")
        if (category == AppSettingCategories.MAIN_SCREEN) {
            Groups.changeRefusal(change, database.zoneDao().getAllZones().map { it.name to it.group }, s)?.let { return it }
        }

        // A zone whose group is renamed takes the new name; one whose group the home screen gains
        // or loses changes section, and so grid: the settings, the names and the places they
        // change are one write
        val regrouped = database.withTransaction {
            val moved = if (category != AppSettingCategories.MAIN_SCREEN) emptyList() else {
                val zones = database.zoneDao().getAllZones().map { zone ->
                    val group = change.held(zone.group)
                    if (group == zone.group) zone else zone.copy(group = group).also { database.zoneDao().updateZone(it) }
                }
                if (change.beforeRenamed == change.after) emptyList()
                else com.assistant.core.grid.ZonePositions.regroup(zones, change.beforeRenamed, change.after)
            }
            moved.forEach { database.zoneDao().updatePosition(it.id, it.grid_x, it.grid_y) }
            settingsDao.updateSettings(category, settings.toString())
            moved
        }
        AppConfigManager.refresh(context)
        DataChangeNotifier.notifyAppConfigChanged()
        if (regrouped.isNotEmpty() || renames.isNotEmpty()) DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Updated settings of category $category")
        return null
    }

    /**
     * A settings category as stored. A category with no row yet gets its defaults written
     * (AppSettingsDefaults), which is what a first launch looks like. A stored row that is
     * not readable JSON throws: replacing it with the defaults would overwrite what the
     * database holds, and the failure would go unseen.
     */
    private suspend fun readSettings(category: String): JSONObject {
        val stored = settingsDao.getSettingsJsonForCategory(category)
        if (stored != null) return JSONObject(stored)

        LogManager.service("No $category settings found, writing the defaults", "INFO")
        val defaults = AppSettingsDefaults.forCategory(category, context)
        settingsDao.insertOrUpdateSettings(AppSettingsCategory(category = category, settings = defaults))
        return JSONObject(defaults)
    }

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        return when (operation) {
            "get" -> {
                val category = params.optString("category")
                if (category !in AppSettings.CATEGORIES) {
                    LogManager.service("Unknown category: $category", "WARN")
                    return OperationResult.error(s.shared("service_error_unknown_category").format(category))
                }
                OperationResult.success(mapOf("settings" to JsonUtils.toMap(readSettings(category))))
            }
            "set" -> {
                val category = params.optString("category")
                val settings = params.optJSONObject("settings")
                    ?: return OperationResult.error(s.shared("ai_error_param_config_required"))
                setSettings(category, settings, Groups.renames(params, "zone_groups"))?.let { return OperationResult.error(it) }
                OperationResult.success(mapOf("category" to category))
            }
            "get_current_datetime" -> {
                // Milliseconds, as everything inside speaks: CommandExecutor turns the timestamp
                // into the ISO 8601 the model reads, in the app's timezone
                val currentTimestamp = System.currentTimeMillis()
                OperationResult.success(mapOf("timestamp" to currentTimestamp))
            }
            else -> {
                LogManager.service("Unknown operation: $operation", "WARN")
                OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        }
    }

    /**
     * Verbalize AppConfig operation
     * Format: substantive form (e.g., "Modification de la configuration de l'application")
     * Usage: (a) UI validation display, (b) SystemMessage feedback
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "get" -> {
                // get is a read operation, not typically verbalized for validation
                s.shared("action_verbalize_unknown")
            }
            else -> {
                // Any write operation to app config
                s.shared("action_verbalize_update_app_config")
            }
        }
    }

}