package com.assistant.tools.notes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.mapData
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.tools.notes.ui.components.NoteCard
import com.assistant.tools.notes.ui.components.PlaceholderCard
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class for note entries
 */
data class NoteEntry(
    val id: String,
    val content: String,
    val timestamp: Long,
    val position: Int = 0 // For manual ordering
)

/**
 * Main usage screen for Notes tool instance
 * Displays notes in adaptive grid layout with manual ordering
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

    // State
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var notes by remember { mutableStateOf<List<NoteEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showEditScreen by remember { mutableStateOf(false) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var insertPosition by remember { mutableStateOf<Int?>(null) }

    // Movement state
    var movingNoteId by remember { mutableStateOf<String?>(null) }

    // Load tool instance data
    LaunchedEffect(toolInstanceId) {
        coordinator.executeWithLoading(
            operation = "tools.get",
            params = mapOf("tool_instance_id" to toolInstanceId), // Correct parameter name for ToolInstanceService
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            toolInstance = result.mapSingleData("tool_instance") { map -> map }
        }
    }

    // Load notes data
    LaunchedEffect(toolInstance, refreshTrigger) {
        if (toolInstance != null) {
            // Use same API as Tracking
            val params = mapOf(
                "toolInstanceId" to toolInstanceId,
                "limit" to 100 // Load all notes for now
            )

            val result = coordinator.processUserAction("tool_data.get", params)

            if (result?.isSuccess == true) {
                val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                val entries = entriesData.mapNotNull { entry ->
                    try {
                        val map = entry as? Map<*, *> ?: return@mapNotNull null
                        val id = map["id"] as? String ?: return@mapNotNull null
                        val timestamp = (map["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null

                        // Handle data as JSON string (entity.data is already a JSON string)
                        val dataValue = map["data"]
                        val content = when (dataValue) {
                            is String -> {
                                try {
                                    // Parse JSON string
                                    val dataJson = JSONObject(dataValue)
                                    dataJson.optString("content", "")
                                } catch (e: Exception) {
                                    LogManager.ui("Error parsing note data JSON: ${e.message}", "ERROR")
                                    ""
                                }
                            }
                            is Map<*, *> -> dataValue["content"] as? String ?: ""
                            else -> {
                                LogManager.ui("Unexpected data type: ${dataValue?.javaClass?.name}", "WARN")
                                ""
                            }
                        }

                        LogManager.ui("Parsing note: id=$id, timestamp=$timestamp, content=$content")
                        NoteEntry(id, content, timestamp)
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing note entry: ${e.message}", "ERROR")
                        null
                    }
                }

                // Sort by timestamp for manual ordering (oldest first as base order)
                notes = entries.sortedBy { it.timestamp }
                LogManager.ui("Loaded ${notes.size} notes")
            } else {
                notes = emptyList()
                LogManager.ui("No notes found or error loading notes")
                LogManager.ui("Result status: ${result?.status}, error: ${result?.error}")
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

    // Determine grid columns based on orientation
    val columns = if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 2 else 1

    // Edit screen overlay
    if (showEditScreen) {
        NotesEditScreen(
            noteId = editingNoteId,
            toolInstanceId = toolInstanceId,
            insertPosition = insertPosition,
            onSave = {
                showEditScreen = false
                editingNoteId = null
                insertPosition = null
                refreshTrigger++
            },
            onCancel = {
                showEditScreen = false
                editingNoteId = null
                insertPosition = null
            }
        )
        return
    }

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
            // Tool header
            val toolName = config.optString("name", s.tool("display_name"))
            val toolDescription = config.optString("description", "")

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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

            // Notes grid
            if (notes.isEmpty()) {
                // Empty state - just show placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PlaceholderCard(
                        onClick = {
                            showEditScreen = true
                            editingNoteId = null
                            insertPosition = null
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
                    // Existing notes
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            isMoving = movingNoteId == note.id,
                            onClick = {
                                if (movingNoteId == null) {
                                    editingNoteId = note.id
                                    showEditScreen = true
                                }
                            },
                            onLongClick = {
                                if (movingNoteId == null) {
                                    // Show context menu
                                    // For now, implement basic actions directly
                                }
                            },
                            onStartMoving = {
                                movingNoteId = note.id
                            },
                            onMoveUp = {
                                moveNoteUp(note, notes) { newNotes ->
                                    notes = newNotes
                                    saveNotePositions(coordinator, toolInstanceId, newNotes)
                                }
                            },
                            onMoveDown = {
                                moveNoteDown(note, notes) { newNotes ->
                                    notes = newNotes
                                    saveNotePositions(coordinator, toolInstanceId, newNotes)
                                }
                            },
                            onStopMoving = {
                                movingNoteId = null
                            },
                            onAddAbove = {
                                val position = notes.indexOf(note)
                                insertPosition = position
                                showEditScreen = true
                                editingNoteId = null
                            }
                        )
                    }

                    // Placeholder card at the end
                    item(key = "placeholder") {
                        PlaceholderCard(
                            onClick = {
                                showEditScreen = true
                                editingNoteId = null
                                insertPosition = null
                            }
                        )
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
 * Save note positions (placeholder for future implementation)
 */
private fun saveNotePositions(
    coordinator: Coordinator,
    toolInstanceId: String,
    notes: List<NoteEntry>
) {
    // For now, positions are maintained by the list order
    // In future, this could update position fields in the database
    LogManager.ui("Note positions updated for tool $toolInstanceId")
}