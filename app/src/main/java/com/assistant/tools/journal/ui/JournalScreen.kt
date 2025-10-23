package com.assistant.tools.journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
import com.assistant.tools.journal.ui.components.JournalCard
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Data class for journal entries
 */
data class JournalEntry(
    val id: String,
    val title: String,  // name field
    val content: String,
    val timestamp: Long
)

/**
 * Main usage screen for Journal tool instance
 * Displays journal entries sorted chronologically
 * with button to create new entries
 */
@Composable
fun JournalScreen(
    toolInstanceId: String,
    zoneName: String,
    onNavigateBack: () -> Unit,
    onConfigureClick: () -> Unit = {}
) {
    LogManager.ui("JournalScreen called with toolInstanceId: $toolInstanceId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "journal", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // State
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Navigation state for entry screen
    var navigateToEntryId by remember { mutableStateOf<String?>(null) }
    var navigateIsCreating by remember { mutableStateOf(false) }

    // Load tool instance data
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

    // Load journal entries
    LaunchedEffect(toolInstance, refreshTrigger) {
        if (toolInstance != null) {
            val params = mapOf(
                "toolInstanceId" to toolInstanceId,
                "limit" to 100
            )

            val result = coordinator.processUserAction("tool_data.get", params)

            if (result?.isSuccess == true) {
                val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                val loadedEntries = entriesData.mapNotNull { entry ->
                    try {
                        val map = entry as? Map<*, *> ?: return@mapNotNull null
                        val id = map["id"] as? String ?: return@mapNotNull null
                        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null
                        val title = map["name"] as? String ?: ""

                        // Parse data field (can be String or Map)
                        val dataValue = map["data"]
                        val parsedData = try {
                            when (dataValue) {
                                is Map<*, *> -> dataValue as Map<String, Any>
                                is String -> {
                                    val dataJson = JSONObject(dataValue)
                                    mutableMapOf<String, Any>().apply {
                                        dataJson.keys().forEach { key -> put(key, dataJson.get(key)) }
                                    }
                                }
                                else -> emptyMap()
                            }
                        } catch (e: Exception) {
                            LogManager.ui("Error parsing journal data: ${e.message}", "ERROR")
                            emptyMap<String, Any>()
                        }

                        val content = parsedData["content"] as? String ?: ""

                        LogManager.ui("Parsing journal entry: id=$id, title=$title, timestamp=$timestamp")
                        JournalEntry(id, title, content, timestamp)
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing journal entry: ${e.message}", "ERROR")
                        null
                    }
                }

                // Sort entries according to config
                val configJson = toolInstance?.get("config_json") as? String ?: "{}"
                val config = try { JSONObject(configJson) } catch (e: Exception) { JSONObject() }
                val sortOrder = config.optString("sort_order", "descending")

                entries = if (sortOrder == "ascending") {
                    loadedEntries.sortedBy { it.timestamp }
                } else {
                    loadedEntries.sortedByDescending { it.timestamp }
                }

                LogManager.ui("Loaded ${entries.size} journal entries")
            } else {
                entries = emptyList()
                LogManager.ui("No entries found or error loading entries")
            }
        }
    }

    // Observe data changes and refresh entries automatically
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> {
                    // Only refresh if the change affects this tool instance
                    if (event.toolInstanceId == toolInstanceId) {
                        refreshTrigger++
                    }
                }
                else -> {} // Ignore other events
            }
        }
    }

    // Parse configuration
    val config = remember(toolInstance) {
        val configJson = toolInstance?.get("config_json") as? String ?: "{}"
        try {
            JSONObject(configJson)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Navigation to entry screen
    if (navigateToEntryId != null) {
        JournalEntryScreen(
            entryId = navigateToEntryId!!,
            toolInstanceId = toolInstanceId,
            isCreating = navigateIsCreating,
            onNavigateBack = {
                navigateToEntryId = null
                navigateIsCreating = false
                refreshTrigger++
            }
        )
        return
    }

    // Main screen content
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    UI.Text(s.tool("loading_entries"), TextType.BODY)
                }
            } else if (toolInstance != null) {
                // Tool header
                val toolName = config.optString("name", s.tool("display_name"))
                val toolDescription = config.optString("description", "")

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    UI.PageHeader(
                        title = toolName,
                        subtitle = toolDescription.takeIf { it.isNotBlank() },
                        icon = config.optString("icon_name", "book-open"),
                        leftButton = ButtonAction.BACK,
                        rightButton = ButtonAction.CONFIGURE,
                        onLeftClick = onNavigateBack,
                        onRightClick = onConfigureClick
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Entries list or empty state
                if (entries.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UI.Text(
                            text = s.tool("no_entries"),
                            type = TextType.SUBTITLE
                        )
                        UI.Text(
                            text = s.tool("no_entries_hint"),
                            type = TextType.CAPTION
                        )
                    }
                } else {
                    // Entries list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        entries.forEach { entry ->
                            UI.Card(type = CardType.DEFAULT) {
                                JournalCard(
                                    entryId = entry.id,
                                    timestamp = entry.timestamp,
                                    title = entry.title,
                                    content = entry.content,
                                    onClick = {
                                        navigateToEntryId = entry.id
                                        navigateIsCreating = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating action button for creating new entry
        if (!isLoading && toolInstance != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                UI.ActionButton(
                    action = ButtonAction.ADD,
                    display = ButtonDisplay.ICON,
                    size = Size.XL,
                    onClick = {
                        coroutineScope.launch {
                            // Create entry immediately in DB with default values
                            // Note: content is optional and will be filled by user typing or transcription
                            val params = mapOf(
                                "toolInstanceId" to toolInstanceId,
                                "tooltype" to "journal",
                                "schema_id" to "journal_data",
                                "name" to s.tool("placeholder_untitled"),
                                "timestamp" to System.currentTimeMillis(),
                                "data" to JSONObject()  // Empty data object - content is optional
                            )

                            val result = coordinator.processUserAction("tool_data.create", params)
                            if (result?.isSuccess == true) {
                                val createdId = result.data?.get("id") as? String
                                if (createdId != null) {
                                    LogManager.ui("Created journal entry with ID: $createdId")
                                    navigateToEntryId = createdId
                                    navigateIsCreating = true
                                } else {
                                    errorMessage = s.tool("error_entry_create")
                                }
                            } else {
                                errorMessage = s.tool("error_entry_create")
                            }
                        }
                    }
                )
            }
        }
    }
}
