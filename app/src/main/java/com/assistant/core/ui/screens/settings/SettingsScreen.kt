package com.assistant.core.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.grid.Grid
import com.assistant.core.strings.Strings
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.ui.*
import com.assistant.core.ui.components.GridLayout
import com.assistant.core.ui.sound.UISignal
import com.assistant.core.ui.sound.rememberUISound

/**
 * The app's settings: four sections, App, AI, Data and System,
 * each a grid of tiles, an icon and a name, two cells wide. A tile opens its screen through
 * [onOpen], by its id; what the screen is for is said at its top, not on the tile.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = s.shared("settings_title"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack
        )
        SECTIONS.forEach { (title, entries) ->
            UI.Card(type = CardType.SECTION_HEADER) {
                Box(modifier = Modifier.fillMaxWidth().padding(UI.Space.M)) {
                    UI.Text(text = s.shared(title), type = TextType.HEADING)
                }
            }
            // Two tiles a row, each two cells wide and one high
            GridLayout(
                entries.mapIndexed { i, entry -> Grid.Tile(entry.id, (i % 2) * 2, i / 2, 2, 1) },
                entries.map { false },
                edit = null
            ) { i ->
                val entry = entries[i]
                val sound = rememberUISound()
                CurrentTheme.current.ZoneCardContainer(onClick = { sound(UISignal.ENTER); onOpen(entry.id) }, onLongClick = {}) {
                    UI.TileHeader(entry.icon, null, s.shared(entry.label), waiting = false, running = false, textType = TextType.HEADING)
                }
            }
        }
    }
}

/** A tile of the settings: the id [SettingsScreen] hands back, its Lucide icon, its name's string key. */
private data class SettingsEntry(val id: String, val icon: String, val label: String)

/** The sections, their titles' string keys, and their tiles in order. */
private val SECTIONS = listOf(
    "settings_section_app" to listOf(
        SettingsEntry("ui", "palette", "settings_ui"),
        SettingsEntry("format", "calendar-clock", "settings_format")
    ),
    "settings_section_ai" to listOf(
        SettingsEntry("ai_providers", "bot", "settings_ai_providers"),
        SettingsEntry("ai_limits", "gauge", "settings_ai_limits"),
        SettingsEntry("validation", "shield-check", "settings_validation")
    ),
    "settings_section_data" to listOf(
        SettingsEntry("data", "database", "settings_data"),
        SettingsEntry("external_access", "cable", "settings_external_access")
    ),
    "settings_section_system" to listOf(
        SettingsEntry("demo", "presentation", "settings_demo"),
        SettingsEntry("logs", "scroll-text", "settings_logs")
    )
)
