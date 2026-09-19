package com.assistant.core.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.config.FormatDefaults
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

/**
 * Format and Date Settings Screen
 *
 * Configuration for date/time display and business logic:
 * - Timezone configuration (display and conversion)
 * - Locale configuration (formatting)
 * - Display format preferences (24h/12h, date patterns, separators)
 * - Business logic settings (day start hour, week start day)
 *
 * Uses AppConfigService to persist configuration in "format" category
 */
@Composable
fun FormatSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Load current configuration
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Timezone configuration
    var timezoneOverride by remember { mutableStateOf<String?>(null) }
    var useSystemTimezone by remember { mutableStateOf(true) }

    // Locale configuration
    var localeOverride by remember { mutableStateOf<String?>(null) }
    var useSystemLocale by remember { mutableStateOf(true) }

    // Display formats - nullable until loaded from DB
    var use24HourFormat by remember { mutableStateOf<Boolean?>(null) }
    var dateFormatPattern by remember { mutableStateOf<String?>(null) }
    var timeSeparator by remember { mutableStateOf(FormatDefaults.TIME_SEPARATOR) }

    // Business logic
    var dayStartHour by remember { mutableStateOf(FormatDefaults.DAY_START_HOUR) }
    var weekStartDay by remember { mutableStateOf(FormatDefaults.getWeekStartDayUppercase()) }

    // Relative label limits
    var hourLimit by remember { mutableStateOf(FormatDefaults.HOUR_LIMIT) }
    var dayLimit by remember { mutableStateOf(FormatDefaults.DAY_LIMIT) }
    var weekLimit by remember { mutableStateOf(FormatDefaults.WEEK_LIMIT) }
    var monthLimit by remember { mutableStateOf(FormatDefaults.MONTH_LIMIT) }
    var yearLimit by remember { mutableStateOf(FormatDefaults.YEAR_LIMIT) }

    // Available options
    val timezoneOptions = remember {
        listOf(
            "Europe/Paris",
            "UTC",
            "America/New_York",
            "America/Los_Angeles",
            "Asia/Tokyo",
            "Asia/Shanghai",
            "Australia/Sydney"
        )
    }

    val localeOptions = remember {
        listOf(
            "fr-FR",
            "en-US",
            "en-GB",
            "de-DE",
            "es-ES",
            "it-IT",
            "ja-JP",
            "zh-CN"
        )
    }

    val dateFormatOptions = remember {
        listOf(
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy-MM-dd"
        )
    }

    val weekStartOptions = remember {
        listOf("MONDAY", "SUNDAY", "SATURDAY")
    }

    // Load configuration
    LaunchedEffect(Unit) {
        try {
            val config = AppConfigManager.getDateTimeConfig()

            timezoneOverride = config.timezoneOverride
            useSystemTimezone = config.timezoneOverride == null

            localeOverride = config.localeOverride
            useSystemLocale = config.localeOverride == null

            use24HourFormat = config.use24HourFormat
            dateFormatPattern = config.dateFormatPattern
            timeSeparator = config.timeSeparator

            dayStartHour = config.dayStartHour
            weekStartDay = config.weekStartDay

            // Relative label limits are in AppConfig but not in DateTimeConfig
            // Use defaults for now, will be loaded on first save or if accessed directly
            // (Could add getter to AppConfigService if needed)

            isLoading = false
        } catch (e: Exception) {
            errorMessage = s.shared("settings_format_error_load").format(e.message ?: "")
            isLoading = false
        }
    }

    // Error display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Save function
    fun saveSettings() {
        coroutineScope.launch {
            try {
                val service = com.assistant.core.services.AppConfigService(context)

                // Save timezone
                service.setTimezoneOverride(if (useSystemTimezone) null else timezoneOverride)

                // Save locale (if different from current override)
                if (localeOverride != AppConfigManager.getDateTimeConfig().localeOverride) {
                    service.setLocaleOverride(if (useSystemLocale) null else localeOverride)
                }

                // Save display formats
                service.setUse24HourFormat(use24HourFormat)
                service.setDateFormatPattern(dateFormatPattern)
                service.setTimeSeparator(timeSeparator)

                // Save business logic
                service.setDayStartHour(dayStartHour)
                service.setWeekStartDay(weekStartDay)

                // Save relative label limits
                service.setRelativeLabelLimits(
                    hourLimit = hourLimit,
                    dayLimit = dayLimit,
                    weekLimit = weekLimit,
                    monthLimit = monthLimit,
                    yearLimit = yearLimit
                )

                // Refresh cache
                AppConfigManager.refresh(context)

                UI.Toast(context, s.shared("settings_saved"), Duration.SHORT)

                // Close screen after successful save
                onBack()
            } catch (e: Exception) {
                errorMessage = s.shared("settings_format_error_save").format(e.message ?: "")
            }
        }
    }

    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            UI.Text(text = s.shared("message_loading"), type = TextType.BODY)
        }
        return
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("settings_format"),
            subtitle = s.shared("settings_format_subtitle"),
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = null
        )

        // === TIMEZONE SECTION ===
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.shared("settings_format_timezone"), type = TextType.SUBTITLE)

                // Use system timezone toggle
                UI.ToggleField(
                    label = s.shared("settings_format_use_system_timezone"),
                    checked = useSystemTimezone,
                    onCheckedChange = { useSystemTimezone = it }
                )

                // Timezone selector (disabled if using system)
                if (!useSystemTimezone) {
                    UI.FormSelection(
                        label = s.shared("settings_format_timezone"),
                        options = timezoneOptions,
                        selected = timezoneOverride ?: ZoneId.systemDefault().id,
                        onSelect = { timezoneOverride = it },
                        required = true
                    )
                } else {
                    UI.Text(
                        text = s.shared("settings_format_current_timezone").format(ZoneId.systemDefault().id),
                        type = TextType.CAPTION
                    )
                }
            }
        }

        // === LOCALE SECTION ===
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.shared("settings_format_locale_section"), type = TextType.SUBTITLE)

                // Use system locale toggle
                UI.ToggleField(
                    label = s.shared("settings_format_use_system_locale"),
                    checked = useSystemLocale,
                    onCheckedChange = { useSystemLocale = it }
                )

                // Locale selector (disabled if using system)
                if (!useSystemLocale) {
                    UI.FormSelection(
                        label = s.shared("settings_format_locale"),
                        options = localeOptions,
                        selected = localeOverride ?: Locale.getDefault().toLanguageTag(),
                        onSelect = { localeOverride = it },
                        required = true
                    )
                } else {
                    UI.Text(
                        text = s.shared("settings_format_current_locale").format(Locale.getDefault().toLanguageTag()),
                        type = TextType.CAPTION
                    )
                }
            }
        }

        // === DISPLAY FORMATS SECTION ===
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.shared("settings_format_display_section"), type = TextType.SUBTITLE)

                // 24h format toggle
                if (use24HourFormat != null) {
                    UI.ToggleField(
                        label = s.shared("settings_format_24h"),
                        checked = use24HourFormat!!,
                        onCheckedChange = { use24HourFormat = it }
                    )
                } else {
                    UI.Text(text = s.shared("settings_format_24h_loading").format(s.shared("message_loading")), type = TextType.BODY)
                }

                // Date format selector
                if (dateFormatPattern != null) {
                    UI.FormSelection(
                        label = s.shared("settings_format_date_format"),
                        options = dateFormatOptions,
                        selected = dateFormatPattern!!,
                        onSelect = { dateFormatPattern = it },
                        required = false
                    )
                } else {
                    UI.Text(text = s.shared("settings_format_date_format_loading").format(s.shared("message_loading")), type = TextType.BODY)
                }

                // Time separator selector
                UI.FormSelection(
                    label = s.shared("app_config_format_time_separator"),
                    options = listOf(":", "h"),
                    selected = timeSeparator,
                    onSelect = { timeSeparator = it },
                    required = true
                )
            }
        }

        // === BUSINESS LOGIC SECTION ===
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.shared("settings_format_business_section"), type = TextType.SUBTITLE)

                // Day start hour
                UI.SliderField(
                    label = s.shared("settings_format_day_start_hour"),
                    value = dayStartHour,
                    onValueChange = { dayStartHour = it },
                    range = 0..23,
                    minLabel = s.shared("settings_format_hour_value").format(0),
                    maxLabel = s.shared("settings_format_hour_value").format(23)
                )

                // Week start day
                UI.FormSelection(
                    label = s.shared("app_config_format_week_start_day"),
                    options = weekStartOptions,
                    selected = weekStartDay,
                    onSelect = { weekStartDay = it },
                    required = true
                )
            }
        }

        // === RELATIVE LABELS SECTION ===
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.shared("settings_format_relative_section"), type = TextType.SUBTITLE)
                UI.Text(
                    text = s.shared("settings_format_relative_description"),
                    type = TextType.CAPTION
                )

                // Hour limit
                UI.SliderField(
                    label = s.shared("app_config_format_hour_limit"),
                    value = hourLimit,
                    onValueChange = { hourLimit = it },
                    range = 1..48,
                    minLabel = "1",
                    maxLabel = "48"
                )

                // Day limit
                UI.SliderField(
                    label = s.shared("app_config_format_day_limit"),
                    value = dayLimit,
                    onValueChange = { dayLimit = it },
                    range = 1..31,
                    minLabel = "1",
                    maxLabel = "31"
                )

                // Week limit
                UI.SliderField(
                    label = s.shared("app_config_format_week_limit"),
                    value = weekLimit,
                    onValueChange = { weekLimit = it },
                    range = 1..12,
                    minLabel = "1",
                    maxLabel = "12"
                )

                // Month limit
                UI.SliderField(
                    label = s.shared("app_config_format_month_limit"),
                    value = monthLimit,
                    onValueChange = { monthLimit = it },
                    range = 1..24,
                    minLabel = "1",
                    maxLabel = "24"
                )

                // Year limit
                UI.SliderField(
                    label = s.shared("app_config_format_year_limit"),
                    value = yearLimit,
                    onValueChange = { yearLimit = it },
                    range = 1..10,
                    minLabel = "1",
                    maxLabel = "10"
                )
            }
        }

        // Save button
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            UI.ActionButton(
                action = ButtonAction.SAVE,
                display = ButtonDisplay.LABEL,
                size = Size.L,
                onClick = { saveSettings() }
            )
        }
    }
}
