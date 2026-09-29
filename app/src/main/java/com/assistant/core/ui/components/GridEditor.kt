package com.assistant.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.grid.Grid
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.CardType
import com.assistant.core.ui.Size
import com.assistant.core.ui.UI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The edit mode of a screen's grids (docs/design/grid-layout.md, « Le mode d'édition »): the
 * tools of a zone, the zones of the home screen. One group section at a time, named by its key
 * (its group, "" for the ungrouped one), and in it the tile being moved, at the places of the move
 * in progress.
 *
 * A move is written when it is validated, when another tile is touched or when the edit mode is
 * closed, in one write ([placeOperation], given [placeParams], the section's group and its
 * places); "Cancel" puts the section back as it was when the tile was touched. What is being
 * edited survives recreation.
 */
class GridEditor internal constructor(
    private val placeOperation: String,
    private val placeParams: Map<String, Any>,
    private val coordinator: Coordinator,
    private val scope: CoroutineScope,
    private val sectionTiles: (String) -> List<Grid.Tile>,
    private val onError: (String) -> Unit,
    private val section: MutableState<String?>,
    private val selected: MutableState<String?>,
    private val draft: MutableState<String?>,
    private val original: MutableState<String?>
) {
    /** The key of the section in edit mode, or null. */
    val editing: String? get() = section.value
    val selectedId: String? get() = selected.value
    val anyEditing: Boolean get() = section.value != null
    fun isEditing(key: String): Boolean = section.value == key

    /** Whether the tile touched has moved since. */
    val moving: Boolean get() = draft.value != null && draft.value != original.value

    /** The places of the section [key] while one of its tools is moved, else null (the stored ones). */
    fun places(key: String): List<Grid.Tile>? =
        if (isEditing(key)) draft.value?.let { decode(it, sectionTiles(key)) } else null

    /** What the grid of section [key] shows in edit mode, or null outside it. */
    fun gridEdit(key: String): GridEdit? =
        if (!isEditing(key)) null
        else GridEdit(places(key) ?: sectionTiles(key), selected.value) { id -> select(key, id) }

    /** The edit button of section [key]: opens its edit mode, closing another's; closes its own. */
    fun toggle(key: String) = afterCommit {
        section.value = if (section.value == key) null else key
    }

    private fun select(key: String, id: String) {
        if (selected.value == id) return
        afterCommit {
            val tiles = encode(sectionTiles(key))
            selected.value = id
            original.value = tiles
            draft.value = tiles
        }
    }

    /** The tile moved by an arrow, or nothing when it has nowhere to go that way. */
    fun move(direction: Grid.Direction) {
        val next = next(direction) ?: return
        draft.value = encode(next)
    }

    fun canMove(direction: Grid.Direction): Boolean = next(direction) != null

    private fun next(direction: Grid.Direction): List<Grid.Tile>? {
        val key = section.value ?: return null
        val id = selected.value ?: return null
        return Grid.move(places(key) ?: return null, id, direction)
    }

    /** The move written, the tile unselected; the edit mode stays. */
    fun validate() = afterCommit {}

    /** The section as it was when the tile was touched, the tile unselected. */
    fun cancel() {
        selected.value = null
        draft.value = null
        original.value = null
    }

    /** Runs [then] once the move in progress is written; a write refused keeps it, its error said. */
    private fun afterCommit(then: () -> Unit) {
        val key = section.value
        val places = key?.let { places(it) }
        if (key == null || places == null || !moving) {
            cancel()
            then()
            return
        }
        scope.launch {
            val result = coordinator.processUserAction(placeOperation, placeParams + mapOf(
                "group" to key,
                "places" to places.associate { it.id to mapOf("grid_x" to it.column, "grid_y" to it.row) }
            ))
            if (!result.isSuccess) {
                onError(result.error ?: "")
                return@launch
            }
            cancel()
            then()
        }
    }

    private fun encode(tiles: List<Grid.Tile>): String = tiles.sortedBy { it.id }.joinToString(";") { "${it.id},${it.column},${it.row}" }

    /** Places kept as text, the sizes read again from the stored tiles. */
    private fun decode(text: String, stored: List<Grid.Tile>): List<Grid.Tile> {
        val byId = stored.associateBy { it.id }
        return text.split(";").filter { it.isNotEmpty() }.mapNotNull { part ->
            val (id, column, row) = part.split(",")
            byId[id]?.copy(column = column.toInt(), row = row.toInt())
        }
    }
}

/**
 * The edit mode of a screen's grids, [sectionTiles] giving the stored tiles of a section by its
 * key, a move written by [placeOperation] with [placeParams].
 */
@Composable
fun rememberGridEditor(placeOperation: String, placeParams: Map<String, Any>, sectionTiles: (String) -> List<Grid.Tile>, onError: (String) -> Unit): GridEditor {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val section = rememberSaveable { mutableStateOf<String?>(null) }
    val selected = rememberSaveable { mutableStateOf<String?>(null) }
    val draft = rememberSaveable { mutableStateOf<String?>(null) }
    val original = rememberSaveable { mutableStateOf<String?>(null) }
    return GridEditor(placeOperation, placeParams, coordinator, scope, sectionTiles, onError, section, selected, draft, original)
}

/**
 * The bar of a move in progress, the full width under the screen: a cross of four arrows, each
 * greyed where the tile has nowhere to go, the validation in its middle, and "Cancel" beside.
 */
@Composable
fun GridEditBar(editor: GridEditor) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    UI.Card(type = CardType.DEFAULT) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            @Composable
            fun Arrow(action: ButtonAction, direction: Grid.Direction) = UI.ActionButton(
                action = action, display = ButtonDisplay.ICON, size = Size.M,
                enabled = editor.canMove(direction), onClick = { editor.move(direction) }
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Arrow(ButtonAction.UP, Grid.Direction.UP)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Arrow(ButtonAction.LEFT, Grid.Direction.LEFT)
                    UI.ActionButton(action = ButtonAction.CONFIRM, display = ButtonDisplay.ICON, size = Size.M, onClick = { editor.validate() })
                    Arrow(ButtonAction.RIGHT, Grid.Direction.RIGHT)
                }
                Arrow(ButtonAction.DOWN, Grid.Direction.DOWN)
            }
            UI.ActionButton(action = ButtonAction.CANCEL, display = ButtonDisplay.LABEL, onClick = { editor.cancel() })
        }
    }
}

/**
 * [content] faded and deaf while [faded]: the rest of the screen while a section is in edit mode.
 * Scrolling still goes through.
 */
@Composable
fun Faded(faded: Boolean, content: @Composable () -> Unit) {
    Box {
        Box(modifier = Modifier.alpha(if (faded) 0.4f else 1f)) { content() }
        if (faded) {
            Box(modifier = Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
        }
    }
}

/**
 * The buttons of a section's title line: its edit mode, on while it lasts (shown when it has
 * tiles), and adding, off while any section is in edit mode.
 */
@Composable
fun GridSectionButtons(key: String, hasTiles: Boolean, editor: GridEditor, onAdd: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hasTiles) UI.ActionButton(
            action = ButtonAction.ARRANGE,
            display = ButtonDisplay.ICON,
            size = Size.M,
            active = editor.isEditing(key),
            onClick = { editor.toggle(key) }
        )
        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.ICON,
            size = Size.M,
            enabled = !editor.anyEditing,
            onClick = onAdd
        )
    }
}
