package com.assistant.core.ui.screens.settings

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.LogEntry
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Time range enum for log filtering
 * Simple duration-based filtering (no Period system)
 */
enum class LogTimeRange(val durationMillis: Long) {
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    ONE_HOUR(60 * 60_000L),
    ONE_DAY(24 * 60 * 60_000L),
    ALWAYS(0L)  // 0 means no time filter
}

/**
 * Log level enum with ordinal-based filtering
 * Higher ordinal = more severe (VERBOSE < DEBUG < INFO < WARN < ERROR)
 */
/**
 * How many rows a single view loads at most.
 *
 * The table is allowed to hold far more than a screen can show, so the read is bounded rather
 * than the retention: loading everything is what used to force the table to stay tiny.
 */
private const val MAX_LOGS_SHOWN = 500

enum class LogLevel(val displayName: String) {
    VERBOSE("Verbose"),
    DEBUG("Debug"),
    INFO("Info"),
    WARN("Warn"),
    ERROR("Error")
}

/**
 * Logs screen for in-app debugging
 *
 * Features:
 * - Time range filter: 1 min | 5 min | 1h | 1 jour | toujours
 * - Level filter: Au moins (DEBUG, INFO, WARN, ERROR)
 * - Tag filter: Text input with LIKE query (e.g., "ai" matches "AI_SERVICE", "AIUI", etc.)
 * - Logs displayed newest first (ORDER BY timestamp DESC)
 *
 * UI:
 * - Filters at top (3 dropdowns: time, level, tag input)
 * - LazyColumn with log entries (card per entry)
 * - Color-coded by level
 */
@Composable
fun LogsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Filter states
    var selectedTimeRange by rememberSaveable { mutableStateOf(LogTimeRange.ONE_HOUR) }
    var selectedMinLevel by rememberSaveable { mutableStateOf(LogLevel.DEBUG) }
    var tagFilter by rememberSaveable { mutableStateOf("") }

    // Logs data
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load logs function
    val loadLogs = {
        coroutineScope.launch {
            isLoading = true
            try {
                val database = AppDatabase.getDatabase(context)
                val sinceTimestamp = if (selectedTimeRange == LogTimeRange.ALWAYS) {
                    0L
                } else {
                    System.currentTimeMillis() - selectedTimeRange.durationMillis
                }

                // Tag pattern: empty = all (%), otherwise add % for LIKE query
                val tagPattern = if (tagFilter.isBlank()) "%" else "%${tagFilter}%"

                // The level filter belongs in the query: filtering after a LIMIT would cut the
                // rows on their way in, and asking for errors alone would return almost none.
                val levels = LogLevel.entries
                    .filter { it.ordinal >= selectedMinLevel.ordinal }
                    .map { it.name }

                logs = database.logDao().getLogsFiltered(
                    sinceTimestamp = sinceTimestamp,
                    tagPattern = tagPattern,
                    levels = levels,
                    limit = MAX_LOGS_SHOWN
                )

                isLoading = false
            } catch (e: Exception) {
                LogManager.ui("Failed to load logs: ${e.message}", "ERROR", e)
                errorMessage = "${s.shared("error_unknown")}: ${e.message}"
                isLoading = false
            }
        }
    }

    // Load logs on filter change
    LaunchedEffect(selectedTimeRange, selectedMinLevel, tagFilter) {
        loadLogs()
    }

    // Error display (toast pattern)
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            UI.Toast(context, error, Duration.LONG)
            errorMessage = null
        }
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        // Header with back button (not scrollable)
        UI.PageHeader(
            title = s.shared("settings_logs"),
            subtitle = "${logs.size} ${s.shared("logs_count")}",
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = null
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Filters section (scrollable with content)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Filters card
            UI.Card(
                type = CardType.DEFAULT,
                size = Size.M
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Time range filter
                    val timeRangeOptions = LogTimeRange.entries.map { range ->
                        when (range) {
                            LogTimeRange.ONE_MINUTE -> "1 ${s.shared("time_minute")}"
                            LogTimeRange.FIVE_MINUTES -> "5 ${s.shared("time_minutes")}"
                            LogTimeRange.ONE_HOUR -> "1 ${s.shared("time_hour")}"
                            LogTimeRange.ONE_DAY -> "1 ${s.shared("time_day")}"
                            LogTimeRange.ALWAYS -> s.shared("time_always")
                        }
                    }
                    val selectedTimeRangeString = when (selectedTimeRange) {
                        LogTimeRange.ONE_MINUTE -> "1 ${s.shared("time_minute")}"
                        LogTimeRange.FIVE_MINUTES -> "5 ${s.shared("time_minutes")}"
                        LogTimeRange.ONE_HOUR -> "1 ${s.shared("time_hour")}"
                        LogTimeRange.ONE_DAY -> "1 ${s.shared("time_day")}"
                        LogTimeRange.ALWAYS -> s.shared("time_always")
                    }

                    UI.FormSelection(
                        label = s.shared("logs_filter_time"),
                        options = timeRangeOptions,
                        selected = selectedTimeRangeString,
                        onSelect = { selected ->
                            val index = timeRangeOptions.indexOf(selected)
                            if (index >= 0) {
                                selectedTimeRange = LogTimeRange.entries[index]
                            }
                        },
                        required = false
                    )

                    // Level filter
                    val levelOptions = LogLevel.entries.map { "${s.shared("logs_level_at_least")} ${it.displayName}" }
                    val selectedLevelString = "${s.shared("logs_level_at_least")} ${selectedMinLevel.displayName}"

                    UI.FormSelection(
                        label = s.shared("logs_filter_level"),
                        options = levelOptions,
                        selected = selectedLevelString,
                        onSelect = { selected ->
                            val index = levelOptions.indexOf(selected)
                            if (index >= 0) {
                                selectedMinLevel = LogLevel.entries[index]
                            }
                        },
                        required = false
                    )

                    // Tag filter
                    UI.FormField(
                        label = s.shared("logs_filter_tag"),
                        value = tagFilter,
                        onChange = { tagFilter = it },
                        fieldType = FieldType.TEXT,
                        required = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logs list
            when {
                isLoading -> {
                    UI.Text(
                        text = s.shared("message_loading"),
                        type = TextType.BODY,
                        fillMaxWidth = true
                    )
                }
                logs.isEmpty() -> {
                    UI.Text(
                        text = s.shared("logs_no_logs"),
                        type = TextType.BODY,
                        fillMaxWidth = true
                    )
                }
                else -> {
                    // Display logs (newest first already from query ORDER BY timestamp DESC)
                    logs.forEach { log ->
                        LogEntryCard(log = log, s = s)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Single log entry card
 * Color-coded by level
 */
@Composable
private fun LogEntryCard(log: LogEntry, s: com.assistant.core.strings.StringsContext) {
    UI.Card(
        type = CardType.DEFAULT,
        size = Size.S
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: timestamp + level + tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Level badge (color-coded) - Use native Compose Text for color support
                val levelColor = when (log.level) {
                    "VERBOSE" -> Color(0xFF9E9E9E)  // Light gray
                    "DEBUG" -> Color(0xFF757575)     // Gray
                    "INFO" -> Color(0xFF2196F3)      // Blue
                    "WARN" -> Color(0xFFFFA500)      // Orange
                    "ERROR" -> Color(0xFFF44336)     // Red
                    else -> Color.Black
                }

                Text(
                    text = log.level,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                UI.Text(
                    text = "[${log.tag}]",
                    type = TextType.CAPTION
                )

                Spacer(modifier = Modifier.weight(1f))

                // Relative time
                val relativeTime = com.assistant.core.utils.FormatUtils.formatRelativeTimePast(log.timestamp, LocalContext.current)
                UI.Text(
                    text = relativeTime,
                    type = TextType.CAPTION
                )
            }

            // Message
            UI.Text(
                text = log.message,
                type = TextType.BODY
            )

            // Throwable (if any)
            log.throwableMessage?.let { throwable ->
                UI.Text(
                    text = throwable,
                    type = TextType.CAPTION
                )
            }
        }
    }
}
