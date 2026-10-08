package app.treelune.tools.notes.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.ui.*
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.executeWithLoading
import app.treelune.core.coordinator.mapSingleData
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DataChangeEvent
import app.treelune.tools.notes.ui.components.NoteCard
import app.treelune.tools.notes.ui.components.EditNoteDialog
import kotlinx.coroutines.launch
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Data class for note entries
 */
data class NoteEntry(
    val id: String,
    val title: String?, // The entry's name; shown only while the tool's notes take titles
    val content: String,
    val timestamp: Long,
    val position: Int = 0,
    val extra: Map<String, Any?> = emptyMap()
)

/**
 * Main usage screen for Notes tool instance
 * Displays notes in adaptive grid layout with inline creation
 */
@Composable
fun NotesScreen(
    toolInstanceId: String,
    zoneName: String,
    onNavigateBack: () -> Unit,
    onConfigureClick: () -> Unit = {},
    openEntry: app.treelune.core.tools.EntryToOpen? = null
) {
    LogManager.ui("NotesScreen called with toolInstanceId: $toolInstanceId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // State
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var notes by remember { mutableStateOf<List<NoteEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var notesLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var contextMenuNoteId by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showNoteDialog by rememberSaveable { mutableStateOf(false) }
    // The note being edited is kept by id and resolved from the loaded notes, so the dialog
    // survives a rotation. null = creation mode
    var dialogNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    val dialogNote = dialogNoteId?.let { id -> notes.find { it.id == id } }
    var dialogPosition by rememberSaveable { mutableStateOf<Int?>(null) }

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

    // Load notes data
    LaunchedEffect(toolInstance, refreshTrigger) {
        if (toolInstance != null) {
            val params = mapOf(
                "tool_instance_id" to toolInstanceId,
                "limit" to 100
            )

            val result = coordinator.processUserAction("tool_data.get", params)

            if (result?.isSuccess == true) {
                val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                val entries = entriesData.mapNotNull { entry ->
                    try {
                        val map = entry as? Map<*, *> ?: return@mapNotNull null
                        val id = map["id"] as? String ?: return@mapNotNull null
                        // Milliseconds from the service, as stored
                        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null

                        // The service hands out an object; the string form stays at the database edge.
                        @Suppress("UNCHECKED_CAST")
                        val parsedData = (map["data"] as? Map<String, Any>) ?: emptyMap()

                        val content = parsedData["content"] as? String ?: ""
                        val title = (map["name"] as? String)?.takeIf { it.isNotBlank() }
                        @Suppress("UNCHECKED_CAST")
                        val state = (map["state"] as? Map<String, Any>) ?: emptyMap()
                        val position = (state["position"] as? Number)?.toInt() ?: 0

                        // Custom fields come from their own column, not from the data object
                        @Suppress("UNCHECKED_CAST")
                        val customFields = (map["extra"] as? Map<String, Any?>) ?: emptyMap()

                        LogManager.ui("Parsing note: id=$id, timestamp=$timestamp, content=$content, position=$position, customFields=${customFields.size}")
                        NoteEntry(id, title, content, timestamp, position, customFields)
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing note entry: ${e.message}", "ERROR")
                        null
                    }
                }

                notes = entries.sortedWith(compareBy<NoteEntry> { it.position }.thenBy { it.timestamp })
                LogManager.ui("Loaded ${notes.size} notes")
                notesLoaded = true
            } else {
                notes = emptyList()
                LogManager.ui("No notes found or error loading notes")
            }
        }
    }

    // Observe data changes and refresh notes automatically
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
    val titles = remember(config) { toolInstance != null && app.treelune.tools.notes.NotesToolType.hasTitles(config, context) }

    // Helper functions for dialog management
    fun openEditDialog(note: NoteEntry) {
        contextMenuNoteId = null // Close any open menu
        dialogNoteId = note.id
        dialogPosition = null
        showNoteDialog = true
    }

    fun openCreateDialog(position: Int) {
        contextMenuNoteId = null // Close any open menu
        dialogNoteId = null // null = creation mode
        dialogPosition = position
        showNoteDialog = true
    }

    // What the tile asked to open, once: a new note last, or a note once the notes are loaded.
    // Kept across recreation, so the dialog is not opened again over one the user closed.
    var openHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openEntry, notes, notesLoaded) {
        if (openHandled || !notesLoaded) return@LaunchedEffect
        when (openEntry) {
            app.treelune.core.tools.EntryToOpen.New -> openCreateDialog((notes.maxOfOrNull { it.position } ?: -1) + 1)
            is app.treelune.core.tools.EntryToOpen.Existing -> openEditDialog(notes.find { it.id == openEntry.id } ?: return@LaunchedEffect)
            null -> Unit
        }
        openHandled = true
    }

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrollable content (header + cards)
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
                    UI.Text(s.shared("tools_loading"), TextType.BODY)
                }
            } else if (toolInstance != null) {
                // Tool header (now scrollable)
                val settings = app.treelune.core.tools.ToolConfigSettings.read(app.treelune.tools.notes.NotesToolType, config, context)
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

                // Notes section
                if (notes.isEmpty()) {
                    // Empty state - show placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UI.Space.L),
                        contentAlignment = Alignment.Center
                    ) {
                        NoteCard(
                            note = null, // Placeholder mode
                            toolInstanceId = toolInstanceId,
                            config = config,
                            contextMenuNoteId = contextMenuNoteId,
                            onNoteClick = { }, // Placeholder doesn't have click
                            onContextMenuChanged = { },
                            onAddAbove = {
                                openCreateDialog(0) // Create at position 0
                            }
                        )
                    }
                } else {
                    // Notes list with simplified logic
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UI.Space.L),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
                    ) {
                        // All existing notes. The service keeps the order: a note written at the
                        // position of another goes before it, so a note moved up takes the position
                        // of the one it lands on, and a note moved down the position after it. The
                        // data change notification reloads the list.
                        UI.ReorderableColumn(
                            items = notes,
                            key = { it.id },
                            spacing = UI.Space.L,
                            onMove = { from, to ->
                                val note = notes[from]
                                val position = if (to < from) notes[to].position else notes[to].position + 1
                                coroutineScope.launch { moveNote(coordinator, note, position) }
                            }
                        ) { _, note ->
                            NoteCard(
                                note = note,
                                toolInstanceId = toolInstanceId,
                                config = config,
                                titles = titles,
                                showContextMenu = contextMenuNoteId == note.id,
                                contextMenuNoteId = contextMenuNoteId,
                                onNoteClick = { openEditDialog(note) },
                                onContextMenuChanged = { showMenu ->
                                    contextMenuNoteId = if (showMenu) note.id else null
                                },
                                dragHandle = { DragHandle() },
                                onAddAbove = {
                                    openCreateDialog(note.position)
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        deleteNote(coordinator, note.id) { refreshTrigger++ }
                                    }
                                }
                            )
                        }

                        // Placeholder at end for creating new notes
                        NoteCard(
                            note = null, // Placeholder mode
                            toolInstanceId = toolInstanceId,
                            config = config,
                            contextMenuNoteId = contextMenuNoteId,
                            onNoteClick = { }, // Placeholder doesn't have click
                            onContextMenuChanged = { },
                            onAddAbove = {
                                val nextPosition = if (notes.isEmpty()) 0 else (notes.maxOfOrNull { it.position } ?: 0) + 1
                                openCreateDialog(nextPosition)
                            }
                        )
                    }
                }
            }
        }

        // Edit/Create Note Dialog
        EditNoteDialog(
            isVisible = showNoteDialog,
            toolInstanceId = toolInstanceId,
            isCreating = dialogNote == null,
            insertPosition = dialogPosition,
            titles = titles,
            initialTitle = dialogNote?.title ?: "",
            initialContent = dialogNote?.content ?: "",
            initialNoteId = dialogNote?.id,
            initialCustomFields = dialogNote?.extra ?: emptyMap(),
            onConfirm = { title, content, position, customFields ->
                if (dialogNote == null) {
                    // Create new note
                    createNote(coordinator, toolInstanceId, title, content, position ?: 0, customFields) {
                        refreshTrigger++
                        showNoteDialog = false
                    }
                } else {
                    // Update existing note
                    updateNote(coordinator, dialogNote!!, title, content, customFields) { updatedNote ->
                        notes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                        showNoteDialog = false
                    }
                }
                true // Always return success for now
            },
            onCancel = {
                showNoteDialog = false
                dialogNoteId = null
                dialogPosition = null
            }
        )
    }

}

/**
 * Move a note to [position]; the service moves the others around it
 */
private suspend fun moveNote(coordinator: Coordinator, note: NoteEntry, position: Int) {
    coordinator.processUserAction("tool_data.update", mapOf(
        "id" to note.id,
        "state" to JSONObject().apply { put("position", position) }
    ))
}

/**
 * Create a new note
 */
private suspend fun createNote(
    coordinator: Coordinator,
    toolInstanceId: String,
    title: String?,
    content: String,
    position: Int,
    customFields: Map<String, Any?>,
    onSuccess: () -> Unit
) {
    val params = mutableMapOf<String, Any>(
        "tool_instance_id" to toolInstanceId,
        "tooltype" to "notes",
        "timestamp" to System.currentTimeMillis(),
        "data" to JSONObject().apply {
            put("content", content.trim())
        },
        "state" to JSONObject().apply {
            put("position", position)
        }
    )

    title?.let { params["name"] = it }

    // Add custom fields if any
    if (customFields.isNotEmpty()) {
        params["extra"] = JSONObject(customFields)
    }

    val result = coordinator.processUserAction("tool_data.create", params)
    if (result?.isSuccess == true) {
        onSuccess()
    }
}

/**
 * Update an existing note. [title] is null when the notes take no title, its name then left as
 * it is; a title emptied clears it.
 */
private suspend fun updateNote(
    coordinator: Coordinator,
    note: NoteEntry,
    title: String?,
    newContent: String,
    customFields: Map<String, Any?>,
    onSuccess: (NoteEntry) -> Unit
) {
    val params = mutableMapOf<String, Any>(
        "id" to note.id,
        "data" to JSONObject().apply {
            put("content", newContent.trim())
        }
    )

    title?.let { params["name"] = it.ifEmpty { JSONObject.NULL } }

    // The user's fields, those emptied sent as null to be cleared
    params["extra"] = app.treelune.core.fields.extraForUpdate(note.extra, customFields)

    val result = coordinator.processUserAction("tool_data.update", params)
    if (result?.isSuccess == true) {
        val updatedNote = note.copy(title = if (title == null) note.title else title.ifEmpty { null }, content = newContent.trim(), extra = customFields)
        onSuccess(updatedNote)
    }
}

/**
 * Delete a note
 */
private suspend fun deleteNote(
    coordinator: Coordinator,
    noteId: String,
    onSuccess: () -> Unit
) {
    val params = mapOf("id" to noteId)
    val result = coordinator.processUserAction("tool_data.delete", params)
    if (result?.isSuccess == true) {
        onSuccess()
    }
}