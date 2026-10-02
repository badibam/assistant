package com.assistant.core.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.config.AppSettings
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.themes.Appearance
import com.assistant.core.themes.CurrentTheme
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.settings.SettingsForm
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.CardType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.JsonObjectSaver
import com.assistant.core.ui.LoadState
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.rememberLoadOnce
import com.assistant.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The screen of one settings category (AppSettings): the form of its declaration, saved through
 * the service, which checks it and says what it refuses. The interface's look is shown as it is
 * chosen, before it is saved.
 *
 * @param below What a category shows under its form, beside its settings
 */
@Composable
fun AppSettingsScreen(
    category: String,
    onBack: () -> Unit,
    below: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val nodes = remember(category) { AppSettings.nodes(category, context) }

    var settings by rememberSaveable(category, stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject()) }
    var isSaving by remember { mutableStateOf(false) }
    // The settings as stored, which the interface's look goes back to when it is left unsaved
    var stored by rememberSaveable(category, stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject()) }
    var saved by remember { mutableStateOf(false) }

    val load = rememberLoadOnce(category) {
        val result = coordinator.processUserAction("app_config.get", mapOf("category" to category))
        @Suppress("UNCHECKED_CAST")
        val read = result.data?.get("settings") as? Map<String, Any?>
        if (!result.isSuccess || read == null) {
            UI.Toast(context, result.error ?: s.shared("error_load_failed"), Duration.LONG)
            return@rememberLoadOnce false
        }
        settings = JsonUtils.toJSONObject(read)
        stored = JsonUtils.toJSONObject(read)
        true
    }

    // The interface's look shows as it is chosen, and goes back to the stored one when the screen
    // is left without saving; a save applies the stored one, now the chosen one (AppConfigManager)
    if (category == AppSettingCategories.UI && load == LoadState.LOADED) {
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        LaunchedEffect(settings) {
            // Once the menu the choice was made in has closed, in the theme that drew it: Material's
            // menu fades out over a few frames, and taken off the screen by the new theme while it
            // fades, it crashes (its window gone, its position still updated)
            kotlinx.coroutines.delay(MENU_CLOSED_MS)
            // Nothing focused when the screens move to another theme's frame (MainActivity): a
            // focus carried along points at a field the move left behind, and the next field
            // touched crashes
            focus.clearFocus(force = true)
            Appearance.from(settings)?.let { CurrentTheme.apply(it) }
        }
        DisposableEffect(Unit) {
            onDispose { if (!saved) Appearance.from(stored)?.let { CurrentTheme.apply(it) } }
        }
    }

    fun save() {
        isSaving = true
        scope.launch {
            val result = coordinator.processUserAction("app_config.set", mapOf("category" to category, "settings" to JsonUtils.toMap(settings)))
            isSaving = false
            if (result.isSuccess) {
                saved = true
                UI.Toast(context, s.shared("settings_saved"), Duration.SHORT)
                onBack()
            } else {
                UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = AppSettings.title(category, context),
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack
        )

        if (load == LoadState.LOADING) {
            UI.Text(s.shared("message_loading"), TextType.BODY)
            return@Column
        }

        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L)) {
                SettingsForm(nodes, settings, { settings = it }, context)
            }
        }

        below()

        UI.FormActions {
            UI.ActionButton(action = ButtonAction.SAVE, enabled = !isSaving && load == LoadState.LOADED, onClick = { save() })
            UI.ActionButton(action = ButtonAction.CANCEL, onClick = onBack)
        }
    }
}

/** Past the fade-out of Material's menu (75 ms), with room for a slow frame. */
private const val MENU_CLOSED_MS = 300L
