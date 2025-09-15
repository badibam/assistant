package com.assistant.tools.notes.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.ui.components.WithSpotlight
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.tools.notes.ui.components.NoteCard
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Data class for note entries
 */
data class NoteEntry(
    val id: String,
    val content: String,
    val timestamp: Long,
    val position: Int = 0
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
    var creatingNotePosition by remember { mutableStateOf<Int?>(null) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var contextMenuNoteId by remember { mutableStateOf<String?>(null) }

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
                "toolInstanceId" to toolInstanceId,
                "limit" to 100
            )

            val result = coordinator.processUserAction("tool_data.get", params)

            if (result?.isSuccess == true) {
                val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                val entries = entriesData.mapNotNull { entry ->
                    try {
                        val map = entry as? Map<*, *> ?: return@mapNotNull null
                        val id = map["id"] as? String ?: return@mapNotNull null
                        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null

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
                            LogManager.ui("Error parsing note data: ${e.message}", "ERROR")
                            emptyMap<String, Any>()
                        }

                        val content = parsedData["content"] as? String ?: ""
                        val position = (parsedData["position"] as? Number)?.toInt() ?: 0

                        LogManager.ui("Parsing note: id=$id, timestamp=$timestamp, content=$content, position=$position")
                        NoteEntry(id, content, timestamp, position)
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

    // Parse configuration
    val config = remember(toolInstance) {
        val configJson = toolInstance?.get("config_json") as? String ?: "{}"
        try {
            JSONObject(configJson)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    // Debug state changes
    LaunchedEffect(editingNoteId, contextMenuNoteId) {
        LogManager.ui("🔵 STATE CHANGE: editingNoteId=$editingNoteId, contextMenuNoteId=$contextMenuNoteId", "DEBUG")
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
        // Layer 1: Background + Header with common spotlight (zIndex 0)
        WithSpotlight(
            isActive = false, // Background + Header never active
            editingNoteId = editingNoteId,
            contextMenuNoteId = contextMenuNoteId,
            onCloseSpotlight = {
                editingNoteId = null
                contextMenuNoteId = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                    // Tool header WITHOUT individual spotlight
                    val toolName = config.optString("name", s.tool("display_name"))
                    val toolDescription = config.optString("description", "")

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    ) {
                        UI.PageHeader(
                            title = toolName,
                            subtitle = toolDescription.takeIf { it.isNotBlank() },
                            icon = config.optString("icon_name", "note"),
                            leftButton = ButtonAction.BACK,
                            rightButton = ButtonAction.CONFIGURE,
                            onLeftClick = onNavigateBack,
                            onRightClick = onConfigureClick
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Background space (clickable through WithSpotlight)
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Layer 2: Cards with individual spotlights (zIndex 2f when active)
        if (!isLoading && toolInstance != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp)
            ) {
                // Spacer to position cards below header
                Spacer(modifier = Modifier.height(80.dp)) // Approximate header height

                // Notes grid
                if (notes.isEmpty() && creatingNotePosition == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NoteCard(
                            note = null, // Placeholder mode
                            toolInstanceId = toolInstanceId,
                            editingNoteId = editingNoteId,
                            contextMenuNoteId = contextMenuNoteId,
                            onEditingChanged = { if (!it) editingNoteId = null },
                            onContextMenuChanged = { if (!it) contextMenuNoteId = null },
                            onAddAbove = {
                                creatingNotePosition = if (notes.isEmpty()) 0 else (notes.maxOfOrNull { it.position } ?: 0) + 1
                                editingNoteId = "creating" // Activate spotlight
                            }
                        )
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(columns),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 16.dp
                    ) {
                        creatingNotePosition?.let { createPos ->
                            // Creating note - insert at specific position
                            val notesBeforeCreate = notes.filter { it.position < createPos }
                            val notesAfterCreate = notes.filter { it.position >= createPos }

                            // Notes before creation position
                            items(notesBeforeCreate, key = { "note_${it.id}" }) { note ->
                                NoteCard(
                                    note = note,
                                    toolInstanceId = toolInstanceId,
                                    showContextMenu = contextMenuNoteId == note.id,
                                    editingNoteId = editingNoteId, // États globaux
                                    contextMenuNoteId = contextMenuNoteId,
                                    onEditingChanged = { isEditing ->
                                        editingNoteId = if (isEditing) note.id else null
                                    },
                                    onContextMenuChanged = { showMenu ->
                                        contextMenuNoteId = if (showMenu) note.id else null
                                    },
                                    onNoteUpdated = { updatedNote ->
                                        notes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                                    },
                                    onMoveUp = { moveNoteUp(note, notes) { notes = it } },
                                    onMoveDown = { moveNoteDown(note, notes) { notes = it } },
                                    onAddAbove = {
                                        // Use the note's actual position, not its index in the sorted list
                                        creatingNotePosition = note.position
                                        editingNoteId = "creating" // Activate spotlight
                                    },
                                    onDelete = {
                                        coroutineScope.launch {
                                            deleteNote(coordinator, note.id) { refreshTrigger++ }
                                        }
                                    }
                                )
                            }

                            // Creation card
                            item(key = "creating") {
                                NoteCard(
                                    note = null,
                                    toolInstanceId = toolInstanceId,
                                    isCreating = true,
                                    insertPosition = createPos,
                                    editingNoteId = "creating", // Force spotlight activation
                                    contextMenuNoteId = contextMenuNoteId,
                                    onNoteCreated = {
                                        editingNoteId = null // Clear on creation
                                        creatingNotePosition = null
                                        refreshTrigger++
                                    },
                                    onCreationCancelled = {
                                        editingNoteId = null // Clear on cancel
                                        creatingNotePosition = null
                                    }
                                )
                            }

                            // Notes after creation position
                            items(notesAfterCreate, key = { "note_${it.id}" }) { note ->
                                NoteCard(
                                    note = note,
                                    toolInstanceId = toolInstanceId,
                                    showContextMenu = contextMenuNoteId == note.id,
                                    editingNoteId = editingNoteId, // États globaux
                                    contextMenuNoteId = contextMenuNoteId,
                                    onEditingChanged = { isEditing ->
                                        editingNoteId = if (isEditing) note.id else null
                                    },
                                    onContextMenuChanged = { showMenu ->
                                        contextMenuNoteId = if (showMenu) note.id else null
                                    },
                                    onNoteUpdated = { updatedNote ->
                                        notes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                                    },
                                    onMoveUp = { moveNoteUp(note, notes) { notes = it } },
                                    onMoveDown = { moveNoteDown(note, notes) { notes = it } },
                                    onAddAbove = {
                                        // Use the note's actual position, not its index in the sorted list
                                        creatingNotePosition = note.position
                                        editingNoteId = "creating" // Activate spotlight
                                    },
                                    onDelete = {
                                        coroutineScope.launch {
                                            deleteNote(coordinator, note.id) { refreshTrigger++ }
                                        }
                                    }
                                )
                            }
                        } ?: run {
                            // No creation - normal display
                            items(notes, key = { "note_${it.id}" }) { note ->
                                NoteCard(
                                    note = note,
                                    toolInstanceId = toolInstanceId,
                                    showContextMenu = contextMenuNoteId == note.id,
                                    editingNoteId = editingNoteId, // États globaux
                                    contextMenuNoteId = contextMenuNoteId,
                                    onEditingChanged = { isEditing ->
                                        editingNoteId = if (isEditing) note.id else null
                                    },
                                    onContextMenuChanged = { showMenu ->
                                        contextMenuNoteId = if (showMenu) note.id else null
                                    },
                                    onNoteUpdated = { updatedNote ->
                                        notes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                                    },
                                    onMoveUp = { moveNoteUp(note, notes) { notes = it } },
                                    onMoveDown = { moveNoteDown(note, notes) { notes = it } },
                                    onAddAbove = {
                                        // Use the note's actual position, not its index in the sorted list
                                        creatingNotePosition = note.position
                                        editingNoteId = "creating" // Activate spotlight
                                    },
                                    onDelete = {
                                        coroutineScope.launch {
                                            deleteNote(coordinator, note.id) { refreshTrigger++ }
                                        }
                                    }
                                )
                            }

                            // Placeholder at end
                            item(key = "placeholder") {
                                NoteCard(
                                    note = null, // Placeholder mode
                                    toolInstanceId = toolInstanceId,
                                    editingNoteId = editingNoteId,
                                    contextMenuNoteId = contextMenuNoteId,
                                    onEditingChanged = { if (!it) editingNoteId = null },
                                    onContextMenuChanged = { if (!it) contextMenuNoteId = null },
                                    onAddAbove = {
                                        creatingNotePosition = if (notes.isEmpty()) 0 else (notes.maxOfOrNull { it.position } ?: 0) + 1
                                        editingNoteId = "creating" // Activate spotlight
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

/**
 * Move note up in the list
 */
private fun moveNoteUp(
    note: NoteEntry,
    currentNotes: List<NoteEntry>,
    onUpdate: (List<NoteEntry>) -> Unit
) {
    val currentIndex = currentNotes.indexOf(note)
    if (currentIndex > 0) {
        val mutableNotes = currentNotes.toMutableList()
        mutableNotes.removeAt(currentIndex)
        mutableNotes.add(currentIndex - 1, note)
        onUpdate(mutableNotes)
    }
}

/**
 * Move note down in the list
 */
private fun moveNoteDown(
    note: NoteEntry,
    currentNotes: List<NoteEntry>,
    onUpdate: (List<NoteEntry>) -> Unit
) {
    val currentIndex = currentNotes.indexOf(note)
    if (currentIndex < currentNotes.size - 1) {
        val mutableNotes = currentNotes.toMutableList()
        mutableNotes.removeAt(currentIndex)
        mutableNotes.add(currentIndex + 1, note)
        onUpdate(mutableNotes)
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