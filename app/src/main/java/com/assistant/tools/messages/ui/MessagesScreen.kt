package com.assistant.tools.messages.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.*
import com.assistant.core.utils.AppConfigManager
import com.assistant.tools.messages.ui.components.EditOccurrenceDialog
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One send of this message, as the screen needs it.
 *
 * A pending occurrence carries only what was written for it; a resolved one also carries the
 * common part copied in when it went out. Both shapes are the same entry at different points
 * of its life, which is why one type covers them.
 */
data class Occurrence(
    val id: String,
    val dueAt: Long,
    val status: String,
    val commonTitle: String?,
    val commonContent: String?,
    val ownTitle: String?,
    val ownContent: String?,
    val read: Boolean,
    val archived: Boolean,
    val notificationSent: Boolean,
    val customFields: Map<String, Any?>
) {
    /** What actually went out, or would go out: the common part joined with the day's. */
    val displayTitle: String
        get() = listOfNotNull(commonTitle, ownTitle).joinToString(" · ")

    val displayContent: String?
        get() = listOfNotNull(commonContent, ownContent).joinToString("\n\n").takeIf { it.isNotEmpty() }
}

/**
 * Main screen of a Messages tool instance.
 *
 * The instance is one notification template and this screen shows what it has produced: the
 * sends already resolved, and the ones still to come. The template itself is not edited here —
 * it is the config, reached through the configure button.
 *
 * Two tabs, because the two populations answer different questions. "Reçus" is the inbox,
 * filtered the way an inbox is. "À venir" is what the recurrence has laid out ahead, each one
 * open to being written before it goes.
 */
@Composable
fun MessagesScreen(
    toolInstanceId: String,
    zoneName: String,
    onNavigateBack: () -> Unit,
    onConfigureClick: () -> Unit = {}
) {
    LogManager.ui("MessagesScreen called with toolInstanceId: $toolInstanceId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "messages", context = context) }

    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(toolInstanceId) {
        coordinator.executeWithLoading(
            operation = "tools.get",
            params = mapOf("tool_instance_id" to toolInstanceId),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            toolInstance = result.mapSingleData("tool_instance") { map -> map }
        }
    }

    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> {
                    if (event.toolInstanceId == toolInstanceId) refreshTrigger++
                }
                else -> {}
            }
        }
    }

    val config = remember(toolInstance) {
        val configJson = toolInstance?.get("config_json") as? String ?: "{}"
        try {
            JSONObject(configJson)
        } catch (e: Exception) {
            LogManager.ui("Unreadable config for $toolInstanceId: ${e.message}", "ERROR", e)
            JSONObject()
        }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            UI.Text(s.shared("tools_loading"), TextType.BODY)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            UI.PageHeader(
                title = config.optString("name", s.tool("display_name")),
                subtitle = config.optString("description", "").takeIf { it.isNotBlank() },
                icon = config.optString("icon_name", "notification"),
                leftButton = ButtonAction.BACK,
                rightButton = ButtonAction.CONFIGURE,
                onLeftClick = onNavigateBack,
                onRightClick = onConfigureClick
            )
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { UI.Text(s.tool("tab_received_messages"), TextType.BODY) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { UI.Text(s.tool("tab_upcoming"), TextType.BODY) }
            )
        }

        when (selectedTab) {
            0 -> ReceivedTab(
                toolInstanceId = toolInstanceId,
                coordinator = coordinator,
                refreshTrigger = refreshTrigger,
                onError = { errorMessage = it }
            )
            1 -> UpcomingTab(
                toolInstanceId = toolInstanceId,
                coordinator = coordinator,
                refreshTrigger = refreshTrigger,
                onError = { errorMessage = it }
            )
        }
    }
}

// ========================================
// Received: what has already been resolved
// ========================================

private enum class ReceivedFilter { UNREAD, READ, ARCHIVED, NOT_DELIVERED }

@Composable
private fun ReceivedTab(
    toolInstanceId: String,
    coordinator: Coordinator,
    refreshTrigger: Int,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val scope = rememberCoroutineScope()

    var filter by rememberSaveable { mutableStateOf(ReceivedFilter.UNREAD) }
    var occurrences by remember { mutableStateOf<List<Occurrence>>(emptyList()) }

    // Expired and cancelled are two different facts, so they are loaded together only under the
    // filter that asks for "what never reached me" — never merged into the inbox itself.
    LaunchedEffect(toolInstanceId, refreshTrigger, filter) {
        occurrences = if (filter == ReceivedFilter.NOT_DELIVERED) {
            (loadByStatus(context, coordinator, toolInstanceId, "expired", onError) +
                loadByStatus(context, coordinator, toolInstanceId, "cancelled", onError))
                .sortedByDescending { it.dueAt }
        } else {
            loadByStatus(context, coordinator, toolInstanceId, "sent", onError)
                .filter { occurrence ->
                    when (filter) {
                        ReceivedFilter.UNREAD -> !occurrence.read && !occurrence.archived
                        ReceivedFilter.READ -> occurrence.read && !occurrence.archived
                        ReceivedFilter.ARCHIVED -> occurrence.archived
                        ReceivedFilter.NOT_DELIVERED -> false
                    }
                }
                .sortedByDescending { it.dueAt }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.FormSelection(
            label = "",
            options = listOf(
                s.tool("filter_unread"),
                s.tool("filter_read"),
                s.tool("filter_archived"),
                s.tool("filter_not_delivered")
            ),
            selected = when (filter) {
                ReceivedFilter.UNREAD -> s.tool("filter_unread")
                ReceivedFilter.READ -> s.tool("filter_read")
                ReceivedFilter.ARCHIVED -> s.tool("filter_archived")
                ReceivedFilter.NOT_DELIVERED -> s.tool("filter_not_delivered")
            },
            onSelect = { selected ->
                filter = when (selected) {
                    s.tool("filter_read") -> ReceivedFilter.READ
                    s.tool("filter_archived") -> ReceivedFilter.ARCHIVED
                    s.tool("filter_not_delivered") -> ReceivedFilter.NOT_DELIVERED
                    else -> ReceivedFilter.UNREAD
                }
            }
        )

        if (occurrences.isEmpty()) {
            UI.Text(s.tool("empty_received_messages"), TextType.CAPTION, fillMaxWidth = true)
            return@Column
        }

        occurrences.forEach { occurrence ->
            ReceivedCard(
                occurrence = occurrence,
                s = s,
                onToggleRead = {
                    scope.launch {
                        updateFlags(context, coordinator, occurrence, read = !occurrence.read, onError = onError)
                    }
                },
                onToggleArchived = {
                    scope.launch {
                        updateFlags(context, coordinator, occurrence, archived = !occurrence.archived, onError = onError)
                    }
                }
            )
        }
    }
}

@Composable
private fun ReceivedCard(
    occurrence: Occurrence,
    s: StringsContext,
    onToggleRead: () -> Unit,
    onToggleArchived: () -> Unit
) {
    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UI.Text(formatMoment(occurrence.dueAt), TextType.CAPTION)
            UI.Text(occurrence.displayTitle, TextType.SUBTITLE)
            occurrence.displayContent?.let { UI.Text(it, TextType.BODY) }

            // Each badge states a fact the history would otherwise lose
            val badges = buildList {
                when (occurrence.status) {
                    "expired" -> add(s.tool("status_expired"))
                    "cancelled" -> add(s.tool("status_cancelled"))
                }
                if (occurrence.status == "sent" && !occurrence.notificationSent) {
                    add(s.tool("status_notification_failed"))
                }
                if (occurrence.status == "sent" && !occurrence.read) add(s.tool("badge_unread"))
            }
            if (badges.isNotEmpty()) {
                UI.Text(badges.joinToString(" · "), TextType.CAPTION)
            }

            // Only something that actually went out can be read or filed away
            if (occurrence.status == "sent") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = onToggleRead) {
                        UI.Text(
                            if (occurrence.read) s.tool("action_mark_unread") else s.tool("action_mark_read"),
                            TextType.LABEL
                        )
                    }
                    UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = onToggleArchived) {
                        UI.Text(
                            if (occurrence.archived) s.tool("action_unarchive") else s.tool("action_archive"),
                            TextType.LABEL
                        )
                    }
                }
            }
        }
    }
}

// ========================================
// Upcoming: what has been laid out ahead
// ========================================

@Composable
private fun UpcomingTab(
    toolInstanceId: String,
    coordinator: Coordinator,
    refreshTrigger: Int,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val scope = rememberCoroutineScope()

    var occurrences by remember { mutableStateOf<List<Occurrence>>(emptyList()) }
    var editing by remember { mutableStateOf<Occurrence?>(null) }

    LaunchedEffect(toolInstanceId, refreshTrigger) {
        occurrences = loadByStatus(context, coordinator, toolInstanceId, "pending", onError).sortedBy { it.dueAt }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (occurrences.isEmpty()) {
            UI.Text(s.tool("empty_upcoming"), TextType.CAPTION, fillMaxWidth = true)
            return@Column
        }

        occurrences.forEach { occurrence ->
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.Text(s.tool("occurrence_due_at").format(formatMoment(occurrence.dueAt)), TextType.CAPTION)

                    // A pending occurrence shows only what was written for it. The common part is
                    // not shown as if it were already copied in, because it is not: it is read
                    // from the template at send time, so editing the template still reaches this.
                    val ownParts = listOfNotNull(occurrence.ownTitle, occurrence.ownContent)
                    if (ownParts.isEmpty()) {
                        UI.Text(s.tool("occurrence_nothing_written"), TextType.CAPTION)
                    } else {
                        occurrence.ownTitle?.let { UI.Text(it, TextType.SUBTITLE) }
                        occurrence.ownContent?.let { UI.Text(it, TextType.BODY) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UI.ActionButton(
                            action = ButtonAction.EDIT,
                            display = ButtonDisplay.ICON,
                            onClick = { editing = occurrence }
                        )
                        UI.ActionButton(
                            action = ButtonAction.DELETE,
                            display = ButtonDisplay.ICON,
                            requireConfirmation = true,
                            confirmMessage = s.tool("delete_occurrence_confirm"),
                            onClick = {
                                scope.launch {
                                    val result = coordinator.processUserAction(
                                        "tool_data.delete",
                                        mapOf("id" to occurrence.id)
                                    )
                                    if (!result.isSuccess) {
                                        onError(result.error ?: s.tool("error_delete"))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    editing?.let { occurrence ->
        EditOccurrenceDialog(
            toolInstanceId = toolInstanceId,
            occurrence = occurrence,
            onDismiss = { editing = null },
            onSaved = { editing = null },
            onError = onError
        )
    }
}

// ========================================
// Reading and writing occurrences
// ========================================

/**
 * Loads the occurrences of one instance in a given state.
 *
 * Goes through the status filter rather than pulling everything and sorting it out here: the
 * history of a long-running reminder is unbounded, and the screen only ever shows one state.
 */
private suspend fun loadByStatus(
    context: android.content.Context,
    coordinator: Coordinator,
    toolInstanceId: String,
    status: String,
    onError: (String) -> Unit
): List<Occurrence> {
    val result = coordinator.processUserAction(
        "tool_data.get",
        mapOf("toolInstanceId" to toolInstanceId, "status" to status)
    )

    if (!result.isSuccess) {
        LogManager.ui("Failed to load $status occurrences: ${result.error}", "ERROR")
        onError(result.error ?: Strings.`for`(tool = "messages", context = context).tool("error_load_occurrences"))
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    val entries = (result.data?.get("entries") as? List<Map<String, Any>>) ?: emptyList()
    val timezone = AppConfigManager.getDateTimeConfig().getZoneId()

    return entries.mapNotNull { entry ->
        val id = entry["id"] as? String ?: return@mapNotNull null
        val iso = entry["timestamp"] as? String ?: return@mapNotNull null
        val dataJson = entry["data"] as? String ?: return@mapNotNull null

        try {
            val data = JSONObject(dataJson)
            val customFields = (entry["custom_fields"] as? String)?.let { JSONObject(it).toValueMap() } ?: emptyMap()

            Occurrence(
                id = id,
                dueAt = DateTimeConverter.isoToTimestamp(iso, timezone),
                status = data.optString("status", "pending"),
                commonTitle = data.optString("common_title").takeIf { it.isNotEmpty() },
                commonContent = data.optString("common_content").takeIf { it.isNotEmpty() },
                ownTitle = data.optString("title").takeIf { it.isNotEmpty() },
                ownContent = data.optString("content").takeIf { it.isNotEmpty() },
                read = data.optBoolean("read", false),
                archived = data.optBoolean("archived", false),
                notificationSent = data.optBoolean("notification_sent", true),
                customFields = customFields
            )
        } catch (e: Exception) {
            LogManager.ui("Unreadable occurrence $id, skipped: ${e.message}", "ERROR", e)
            null
        }
    }
}

/**
 * Flips a read or archived flag on a resolved occurrence.
 *
 * A plain tool_data.update, like correcting any other entry — the occurrence is ordinary data,
 * so it needs no dedicated service operation to be marked read.
 */
private suspend fun updateFlags(
    context: android.content.Context,
    coordinator: Coordinator,
    occurrence: Occurrence,
    read: Boolean? = null,
    archived: Boolean? = null,
    onError: (String) -> Unit
) {
    val data = JSONObject().apply {
        read?.let { put("read", it) }
        archived?.let { put("archived", it) }
    }

    val result = coordinator.processUserAction(
        "tool_data.update",
        mapOf("id" to occurrence.id, "data" to data)
    )

    if (!result.isSuccess) {
        LogManager.ui("Failed to update occurrence ${occurrence.id}: ${result.error}", "ERROR")
        onError(result.error ?: Strings.`for`(tool = "messages", context = context).tool("error_mark_read"))
    }
}

/** Recursive JSONObject to a plain map, for custom field values. */
private fun JSONObject.toValueMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    keys().forEach { key ->
        map[key] = when (val value = get(key)) {
            is JSONObject -> value.toValueMap()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

/** A moment as the user reads it, in the timezone the app is configured for. */
private fun formatMoment(timestamp: Long): String {
    val zone = AppConfigManager.getDateTimeConfig().getZoneId()
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(zone)
        .format(Instant.ofEpochMilli(timestamp))
}
