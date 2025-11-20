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

    // Display formats
    var use24HourFormat by remember { mutableStateOf<Boolean?>(null) }
    var dateFormatPattern by remember { mutableStateOf<String?>(null) }
    var timeSeparator by remember { mutableStateOf(":") }

    // Business logic
    var dayStartHour by remember { mutableStateOf(4) }
    var weekStartDay by remember { mutableStateOf("MONDAY") }

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
        mapOf(
            "MONDAY" to "Lundi",
            "SUNDAY" to "Dimanche",
            "SATURDAY" to "Samedi"
        )
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

            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Erreur de chargement: ${e.message}"
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

                // Refresh cache
                AppConfigManager.refresh(context)

                UI.Toast(context, s.shared("settings_saved"), Duration.SHORT)
            } catch (e: Exception) {
                errorMessage = "Erreur de sauvegarde: ${e.message}"
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
            subtitle = "Configuration date/heure",
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
                UI.Text(text = "Fuseau horaire", type = TextType.SUBTITLE)

                // Use system timezone toggle
                UI.ToggleField(
                    label = "Utiliser fuseau système",
                    checked = useSystemTimezone,
                    onCheckedChange = { useSystemTimezone = it }
                )

                // Timezone selector (disabled if using system)
                if (!useSystemTimezone) {
                    UI.FormSelection(
                        label = "Fuseau horaire",
                        options = timezoneOptions,
                        selected = timezoneOverride ?: ZoneId.systemDefault().id,
                        onSelect = { timezoneOverride = it },
                        required = true
                    )
                } else {
                    UI.Text(
                        text = "Actuel: ${ZoneId.systemDefault().id}",
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
                UI.Text(text = "Langue et région", type = TextType.SUBTITLE)

                // Use system locale toggle
                UI.ToggleField(
                    label = "Utiliser locale système",
                    checked = useSystemLocale,
                    onCheckedChange = { useSystemLocale = it }
                )

                // Locale selector (disabled if using system)
                if (!useSystemLocale) {
                    UI.FormSelection(
                        label = "Locale",
                        options = localeOptions,
                        selected = localeOverride ?: Locale.getDefault().toLanguageTag(),
                        onSelect = { localeOverride = it },
                        required = true
                    )
                } else {
                    UI.Text(
                        text = "Actuelle: ${Locale.getDefault().toLanguageTag()}",
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
                UI.Text(text = "Formats d'affichage", type = TextType.SUBTITLE)

                // 24h format toggle
                UI.ToggleField(
                    label = "Format 24h (sinon 12h)",
                    checked = use24HourFormat ?: true,
                    onCheckedChange = { use24HourFormat = it }
                )

                // Date format selector
                UI.FormSelection(
                    label = "Format de date",
                    options = dateFormatOptions,
                    selected = dateFormatPattern ?: "dd/MM/yyyy",
                    onSelect = { dateFormatPattern = it },
                    required = false
                )

                // Time separator selector
                UI.FormSelection(
                    label = "Séparateur d'heure",
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
                UI.Text(text = "Logique métier (périodes)", type = TextType.SUBTITLE)

                // Day start hour
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UI.Text(
                        text = "Heure de début de journée: ${dayStartHour}h",
                        type = TextType.BODY
                    )
                    UI.Text(
                        text = "Pour le calcul des périodes quotidiennes",
                        type = TextType.CAPTION
                    )
                    androidx.compose.material3.Slider(
                        value = dayStartHour.toFloat(),
                        onValueChange = { dayStartHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22
                    )
                }

                // Week start day
                UI.FormSelection(
                    label = "Jour de début de semaine",
                    options = weekStartOptions.keys.toList(),
                    selected = weekStartDay,
                    onSelect = { weekStartDay = it },
                    required = true
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
