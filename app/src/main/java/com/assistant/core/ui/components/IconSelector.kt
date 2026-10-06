package com.assistant.core.ui.components

import com.assistant.core.themes.IconColor
import com.assistant.core.themes.TagColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.icons.IconIndex
import com.assistant.core.icons.Icons
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext

/** Icons shown for one search across all categories; a category shows all of its own. */
private const val SEARCH_RESULTS_SHOWN = 60

/** Icons per row of the picker's grids. */
private const val ICONS_PER_ROW = 3

/** Choices per row of the icon colour's: none and the eight colours on two rows. */
private const val COLORS_PER_ROW = 5

/** The icon a colour is shown on while the thing has none. */
private const val COLOR_SAMPLE_ICON = "circle"

/**
 * An icon's colour (docs/design/icon-colors.md): none, then each of IconColor.NAMES, every
 * choice showing [iconName] as the theme draws it in that colour, the chosen one marked. It
 * sits under the IconSelector of the same thing.
 *
 * @param current The stored colour name; null for none
 * @param iconName The thing's icon; blank while it has none
 * @param onChange The name chosen, null for none
 */
@Composable
fun IconColorSelector(current: String?, iconName: String, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val shown = iconName.ifBlank { COLOR_SAMPLE_ICON }
    val choices = listOf<String?>(null) + IconColor.NAMES
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(s.shared("label_icon_color"), TextType.LABEL)
        choices.chunked(COLORS_PER_ROW).forEach { row ->
            // The row shared in equal parts, as the icon grid's are
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                row.forEach { name ->
                    Box(modifier = Modifier.weight(1f)) {
                        UI.Button(
                            type = if (current == name) ButtonType.PRIMARY else ButtonType.DEFAULT,
                            size = Size.S,
                            onClick = { onChange(name) }
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(UI.Space.XS), contentAlignment = Alignment.Center) {
                                UI.ItemIcon(iconName = shown, color = IconColor.of(name), size = 20.dp)
                            }
                        }
                    }
                }
                repeat(COLORS_PER_ROW - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Reusable icon selector: the current icon and a button opening the picker.
 *
 * The picker offers, in order: the [suggested] icons; a search on names and tags; and, when
 * nothing is searched, Lucide's categories, each opening its grid, where the search then
 * applies within the category. The search is the one the AI's ICONS command runs, so the same
 * words find the same icons on both sides. Tags are Lucide's, in English.
 *
 * @param current Currently selected icon
 * @param color Its colour, which it is shown in; null for a neutral icon
 * @param suggested Icons offered first: the tooltype's, or a starting set for a zone
 * @param onChange Callback called when an icon is selected
 */
@Composable
fun IconSelector(
    current: String,
    color: TagColor? = null,
    suggested: List<String> = emptyList(),
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        UI.Text(s.shared("tools_config_label_icon"), TextType.LABEL)
        UI.ItemIcon(iconName = current, color = color, size = 32.dp)
        UI.ActionButton(
            action = ButtonAction.SELECT,
            onClick = { showDialog = true }
        )
    }

    if (showDialog) {
        IconPickerDialog(
            current = current,
            suggested = suggested,
            s = s,
            onPick = {
                onChange(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun IconPickerDialog(
    current: String,
    suggested: List<String>,
    s: StringsContext,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val index = remember { Icons.index(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    val words = query.split(' ', ',').filter { it.isNotBlank() }
    val openCategory = category

    UI.Dialog(
        type = DialogType.SELECTION,
        onConfirm = {},
        onCancel = onDismiss
    ) {
        // A category or a search can hold hundreds of icons: the dialog body scrolls
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            UI.Text(s.shared("tools_config_dialog_choose_icon"), TextType.SUBTITLE)

            UI.FormField(
                label = s.shared("tools_config_dialog_search"),
                value = query,
                onChange = { query = it },
                required = false
            )

            when {
                openCategory != null -> {
                    UI.ActionButton(
                        action = ButtonAction.BACK,
                        onClick = { category = null }
                    )
                    UI.Text(categoryTitle(s, openCategory), TextType.LABEL)
                    val result = index.search(words, listOf(openCategory), Int.MAX_VALUE)
                    IconGrid(result.matches.map { it.name }, current, onPick, s)
                }

                words.isNotEmpty() -> {
                    val result = index.search(words, emptyList(), SEARCH_RESULTS_SHOWN)
                    UI.Text(
                        if (result.truncated) s.shared("tools_config_dialog_results_truncated").format(result.matches.size, result.total)
                        else s.shared("tools_config_dialog_results").format(result.total),
                        TextType.CAPTION
                    )
                    IconGrid(result.matches.map { it.name }, current, onPick, s)
                }

                else -> {
                    val offered = suggested.mapNotNull { index.resolve(it) }.distinct()
                    if (offered.isNotEmpty()) {
                        UI.Text(s.shared("tools_config_dialog_suggested_icons"), TextType.LABEL)
                        IconGrid(offered, current, onPick, s)
                    }
                    UI.Text(s.shared("tools_config_dialog_categories"), TextType.LABEL)
                    CategoryList(index.categories, s) { category = it }
                }
            }
        }
    }
}

/** A category's title from the strings system: Lucide's ids, translated like any other text. */
private fun categoryTitle(s: StringsContext, id: String): String =
    s.shared("icon_category_${id.replace('-', '_')}")

@Composable
private fun CategoryList(
    categories: List<IconIndex.Category>,
    s: StringsContext,
    onOpen: (String) -> Unit
) {
    categories.forEach { category ->
        UI.Button(
            type = ButtonType.DEFAULT,
            onClick = { onOpen(category.id) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(UI.Space.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {
                UI.Icon(iconName = category.icon, size = 24.dp)
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(categoryTitle(s, category.id), TextType.BODY)
                }
                UI.Text(category.count.toString(), TextType.CAPTION)
            }
        }
    }
}

@Composable
private fun IconGrid(
    names: List<String>,
    current: String,
    onPick: (String) -> Unit,
    s: StringsContext
) {
    if (names.isEmpty()) {
        UI.Text(s.shared("tools_config_dialog_no_result"), TextType.CAPTION)
        return
    }
    names.chunked(ICONS_PER_ROW).forEach { row ->
        // The row shared in equal parts, each button filling its own: whatever a theme adds
        // around a button (a frame's border, a margin) is taken from its part, never past the row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            row.forEach { name ->
                Box(modifier = Modifier.weight(1f)) {
                    UI.Button(
                        type = if (current == name) ButtonType.PRIMARY else ButtonType.DEFAULT,
                        // The smallest padding a text button has: the name needs the width
                        size = Size.S,
                        onClick = { onPick(name) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(64.dp).padding(UI.Space.XS),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            UI.Icon(iconName = name, size = 28.dp)
                            UI.Text(
                                text = name,
                                type = TextType.CAPTION,
                                fillMaxWidth = true,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            repeat(ICONS_PER_ROW - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
