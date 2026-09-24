package com.assistant.tools.notes.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
import com.assistant.tools.notes.ui.components.NoteCard
import com.assistant.tools.notes.ui.components.EditNoteDialog
import kotlinx.coroutines.launch
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Data class for note entries
 */
data class NoteEntry(
    val id: String,
    val content: String,
    val timestamp: Long,
    val position: Int = 0,
    val customFields: Map<String, Any?> = emptyMap()
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
    onConfigureClick: () -> Unit = {}
) {
    LogManager.ui("NotesScreen called with toolInstanceId: $toolInstanceId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    // State
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var notes by remember { mutableStateOf<List<NoteEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
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
                        val position = (parsedData["position"] as? Number)?.toInt() ?: 0

                        // Custom fields come from their own column, not from the data object
                        @Suppress("UNCHECKED_CAST")
                        val customFields = (map["custom_fields"] as? Map<String, Any?>) ?: emptyMap()

                        LogManager.ui("Parsing note: id=$id, timestamp=$timestamp, content=$content, position=$position, customFields=${customFields.size}")
                        NoteEntry(id, content, timestamp, position, customFields)
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing note entry: ${e.message}", "ERROR")
                        null
                    }
                }

                notes = entries.sortedWith(compareBy<NoteEntry> { it.position }.thenBy { it.timestamp })
                LogManager.ui("Loaded ${notes.size} notes")
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

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Determine grid columns based on orientation
    val columns = if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 2 else 1

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrollable content (header + cards)
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
                    UI.Text(s.shared("tools_loading"), TextType.BODY)
                }
            } else if (toolInstance != null) {
                // Tool header (now scrollable)
                val toolName = config.optString("name", s.tool("display_name"))
                val toolDescription = config.optString("description", "")

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    UI.PageHeader(
                        title = toolName,
                        subtitle = toolDescription.takeIf { it.isNotBlank() },
                        icon = config.optString("icon_name", "sticky-note"),
                        leftButton = ButtonAction.BACK,
                        rightButton = ButtonAction.CONFIGURE,
                        onLeftClick = onNavigateBack,
                        onRightClick = onConfigureClick
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notes section
                if (notes.isEmpty()) {
                    // Empty state - show placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NoteCard(
                            note = null, // Placeholder mode
                            toolInstanceId = toolInstanceId,
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
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // All existing notes
                        notes.forEach { note ->
                            NoteCard(
                                note = note,
                                toolInstanceId = toolInstanceId,
                                showContextMenu = contextMenuNoteId == note.id,
                                contextMenuNoteId = contextMenuNoteId,
                                onNoteClick = { openEditDialog(note) },
                                onContextMenuChanged = { showMenu ->
                                    contextMenuNoteId = if (showMenu) note.id else null
                                },
                                // The service keeps the order: taking the position of the note above
                                // puts this one before it; the position after the note below puts
                                // it after that one. The data change notification reloads the list.
                                onMoveUp = {
                                    val above = notes.getOrNull(notes.indexOf(note) - 1)
                                    if (above != null) coroutineScope.launch {
                                        moveNote(coordinator, note, above.position)
                                    }
                                },
                                onMoveDown = {
                                    val below = notes.getOrNull(notes.indexOf(note) + 1)
                                    if (below != null) coroutineScope.launch {
                                        moveNote(coordinator, note, below.position + 1)
                                    }
                                },
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
            initialContent = dialogNote?.content ?: "",
            initialNoteId = dialogNote?.id,
            initialCustomFields = dialogNote?.customFields ?: emptyMap(),
            onConfirm = { content, position, customFields ->
                if (dialogNote == null) {
                    // Create new note
                    createNote(coordinator, toolInstanceId, content, position ?: 0, customFields) {
                        refreshTrigger++
                        showNoteDialog = false
                    }
                } else {
                    // Update existing note
                    updateNote(coordinator, dialogNote!!, content, customFields) { updatedNote ->
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
        "data" to JSONObject().apply { put("position", position) }
    ))
}

/**
 * Create a new note
 */
private suspend fun createNote(
    coordinator: Coordinator,
    toolInstanceId: String,
    content: String,
    position: Int,
    customFields: Map<String, Any?>,
    onSuccess: () -> Unit
) {
    val params = mutableMapOf<String, Any>(
        "tool_instance_id" to toolInstanceId,
        "tooltype" to "notes",
        "name" to "Note",
        "timestamp" to System.currentTimeMillis(),
        "data" to JSONObject().apply {
            put("content", content.trim())
            put("position", position)
        }
    )

    // Add custom fields if any
    if (customFields.isNotEmpty()) {
        params["custom_fields"] = JSONObject(customFields)
    }

    val result = coordinator.processUserAction("tool_data.create", params)
    if (result?.isSuccess == true) {
        onSuccess()
    }
}

/**
 * Update an existing note
 */
private suspend fun updateNote(
    coordinator: Coordinator,
    note: NoteEntry,
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

    // Add custom fields if any
    if (customFields.isNotEmpty()) {
        params["custom_fields"] = JSONObject(customFields)
    }

    val result = coordinator.processUserAction("tool_data.update", params)
    if (result?.isSuccess == true) {
        val updatedNote = note.copy(content = newContent.trim(), customFields = customFields)
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