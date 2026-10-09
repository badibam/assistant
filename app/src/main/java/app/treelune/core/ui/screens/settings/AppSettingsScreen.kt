package app.treelune.core.ui.screens.settings

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
import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.themes.Appearance
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.JsonObjectSaver
import app.treelune.core.ui.LoadState
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.rememberLoadOnce
import app.treelune.core.utils.JsonUtils
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
            val chosen = Appearance.from(settings) ?: return@LaunchedEffect
            // Another theme moves the screens to its frame (MainActivity). First the menu the
            // choice was made in closes, in the theme that drew it: Material's menu fades out over
            // a few frames, and taken off the screen by the new theme while it fades, it crashes
            // (its window gone, its position still updated). Then nothing is left focused: a
            // focus carried along points at a field the move left behind, and the next field
            // touched crashes. The same theme moves nothing, and shows a slider's value as the
            // finger moves, where a wait started again at each move would wait for it to stop.
            if (chosen.theme != CurrentTheme.themeId) {
                kotlinx.coroutines.delay(MENU_CLOSED_MS)
                focus.clearFocus(force = true)
            }
            CurrentTheme.apply(chosen)
        }
        DisposableEffect(Unit) {
            onDispose { if (!saved) Appearance.from(stored)?.let { CurrentTheme.apply(it) } }
        }
    }

    // The names the home screen's groups came with: a group renamed here is renamed in its zones
    val origins = remember(category) { app.treelune.core.fields.settings.ListOrigins() }
    // The hue shift on a slider of the colours it gives (HueSlider)
    val editors = remember(category) {
        if (category != AppSettingCategories.UI) emptyMap()
        else nodes.filterIsInstance<app.treelune.core.fields.settings.SettingNode.Field>()
            .filter { it.definition.name == AppSettings.UI_HUE_SHIFT }
            .associate { it.definition.name to HueShiftEditor(it) }
    }

    fun save() {
        isSaving = true
        scope.launch {
            val renames = if (category != AppSettingCategories.MAIN_SCREEN) emptyMap()
                else mapOf("zone_groups" to origins.renames("zone_groups", settings.optJSONArray("zone_groups")))
            val result = coordinator.processUserAction("app_config.set", mapOf("category" to category, "settings" to JsonUtils.toMap(settings), "renames" to renames))
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
            subtitle = AppSettings.description(category, context),
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack
        )

        if (load == LoadState.LOADING) {
            UI.Text(s.shared("message_loading"), TextType.BODY)
            return@Column
        }

        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L)) {
                SettingsForm(nodes, settings, { settings = it }, context, editors, origins = origins)
            }
        }

        below()

        UI.FormActions {
            UI.ActionButton(action = ButtonAction.SAVE, enabled = !isSaving && load == LoadState.LOADED, onClick = { save() })
            UI.ActionButton(action = ButtonAction.CANCEL, onClick = onBack)
        }
    }
}

/** The hue shift chosen on the theme's slider of the colours each shift gives (HueSlider). */
private class HueShiftEditor(
    private val node: app.treelune.core.fields.settings.SettingNode.Field
) : app.treelune.core.fields.settings.SettingEditor {
    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
            UI.FieldLabel(node.definition.displayName, node.required)
            val shift = ((value ?: node.default) as? Number)?.toInt() ?: AppSettings.HUE_SHIFT_RANGE.first
            CurrentTheme.current.HueSlider(shift, AppSettings.HUE_SHIFT_RANGE) { onChange(it) }
        }
    }
}

/** Past the fade-out of Material's menu (75 ms), with room for a slow frame. */
private const val MENU_CLOSED_MS = 300L
