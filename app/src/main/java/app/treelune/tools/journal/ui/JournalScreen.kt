package app.treelune.tools.journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ui.*
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.executeWithLoading
import app.treelune.core.coordinator.mapSingleData
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DataChangeEvent
import app.treelune.tools.journal.ui.components.JournalCard
import kotlinx.coroutines.launch
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Data class for journal entries
 */
data class JournalEntry(
    val id: String,
    val title: String,  // name field
    val content: String,
    val timestamp: Long,
    val extra: Map<String, Any?> = emptyMap()
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
    onConfigureClick: () -> Unit = {},
    openEntry: app.treelune.core.tools.EntryToOpen? = null
) {
    LogManager.ui("JournalScreen called with toolInstanceId: $toolInstanceId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "journal", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // State (temporary, reloaded on recomposition)
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var entries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Navigation state for entry screen (survives rotation)
    var navigateToEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var navigateIsCreating by rememberSaveable { mutableStateOf(false) }

    /** An entry created at once with its defaults, opened to be written; a cancel discards it. */
    suspend fun createEntry() {
        // Content is optional
        val params = mapOf(
            "tool_instance_id" to toolInstanceId,
            "tooltype" to "journal",
            "name" to s.tool("placeholder_untitled"),
            "timestamp" to System.currentTimeMillis(),
            "data" to JSONObject()
        )
        val result = coordinator.processUserAction("tool_data.create", params)
        val createdId = result.data?.get("id") as? String
        if (result.isSuccess && createdId != null) {
            LogManager.ui("Created journal entry with ID: $createdId")
            navigateToEntryId = createdId
            navigateIsCreating = true
        } else {
            errorMessage = s.tool("error_entry_create")
        }
    }

    // What the tile asked to open, once: kept across recreation, so a new entry is not created twice
    var openHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openEntry) {
        if (openHandled) return@LaunchedEffect
        openHandled = true
        when (openEntry) {
            is app.treelune.core.tools.EntryToOpen.Existing -> navigateToEntryId = openEntry.id
            app.treelune.core.tools.EntryToOpen.New -> createEntry()
            null -> Unit
        }
    }

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
                "tool_instance_id" to toolInstanceId,
                "limit" to 100
            )

            val result = coordinator.processUserAction("tool_data.get", params)

            if (result?.isSuccess == true) {
                val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                val loadedEntries = entriesData.mapNotNull { entry ->
                    try {
                        val map = entry as? Map<*, *> ?: return@mapNotNull null
                        val id = map["id"] as? String ?: return@mapNotNull null
                        // Milliseconds from the service, as stored
                        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null
                        val title = map["name"] as? String ?: ""

                        // The service hands out an object; the string form stays at the database edge.
                        @Suppress("UNCHECKED_CAST")
                        val parsedData = (map["data"] as? Map<String, Any>) ?: emptyMap()

                        val content = parsedData["content"] as? String ?: ""

                        // The user's fields come from their own column, not from the data object
                        @Suppress("UNCHECKED_CAST")
                        val extra = (map["extra"] as? Map<String, Any?>) ?: emptyMap()

                        LogManager.ui("Parsing journal entry: id=$id, title=$title, timestamp=$timestamp")
                        JournalEntry(id, title, content, timestamp, extra)
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing journal entry: ${e.message}", "ERROR")
                        null
                    }
                }

                // Sort entries according to config
                val configJson = JsonUtils.toJSONObject(toolInstance?.get("config") as? Map<String, Any?> ?: emptyMap()).toString()
                val sortOrder = app.treelune.core.tools.ToolConfigSettings.read(app.treelune.tools.journal.JournalToolType, JSONObject(configJson), context).string("sort_order")

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
        JsonUtils.toJSONObject(toolInstance?.get("config") as? Map<String, Any?> ?: emptyMap())
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
                .verticalScroll(rememberScrollState()).chatButtonSpace()
                .padding(vertical = UI.Space.L)
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
                val settings = app.treelune.core.tools.ToolConfigSettings.read(app.treelune.tools.journal.JournalToolType, config, context)
                val toolName = settings.string("name")!!
                val toolDescription = settings.string("description").orEmpty()

                Column(
                    modifier = Modifier.padding(horizontal = UI.Space.L)
                ) {
                    UI.PageHeader(
                        title = toolName,
                        subtitle = toolDescription.takeIf { it.isNotBlank() },
                        icon = settings.string("icon_name")!!,
                        iconColor = app.treelune.core.themes.IconColor.of(settings.string(app.treelune.core.themes.IconColor.KEY)),
                        leftButton = ButtonAction.BACK,
                        rightButton = ButtonAction.CONFIGURE,
                        onLeftClick = onNavigateBack,
                        onRightClick = onConfigureClick
                    )
                }

                Spacer(modifier = Modifier.height(UI.Space.L))

                // Entries list or empty state
                if (entries.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UI.Space.L),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.S),
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
                            .padding(horizontal = UI.Space.L),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
                    ) {
                        entries.forEach { entry ->
                            UI.Card(type = CardType.DEFAULT) {
                                JournalCard(
                                    entryId = entry.id,
                                    timestamp = entry.timestamp,
                                    title = entry.title,
                                    content = entry.content,
                                    config = config,
                                    extra = entry.extra,
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
                    .padding(UI.Space.L),
                contentAlignment = Alignment.BottomEnd
            ) {
                UI.ActionButton(
                    action = ButtonAction.ADD,
                    display = ButtonDisplay.ICON,
                    size = Size.XL,
                    onClick = { coroutineScope.launch { createEntry() } }
                )
            }
        }
    }
}
