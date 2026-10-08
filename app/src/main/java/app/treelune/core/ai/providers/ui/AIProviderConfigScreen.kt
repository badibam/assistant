package app.treelune.core.ai.providers.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.providers.AIProvider
import app.treelune.core.ai.providers.ImageInput
import app.treelune.core.ai.providers.ProviderModel
import app.treelune.core.ai.providers.ReasoningSettings
import app.treelune.core.fields.settings.SettingEditor
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.fields.settings.storedField
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.Duration
import app.treelune.core.ui.JsonObjectSaver
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The config screen of any AI provider: the form of its declaration (AIProvider.getConfigSettings),
 * the API key masked, and the model chosen among those the provider lists once the settings
 * the listing needs are filled in. Its reasoning (ReasoningSettings) offers what the chosen model
 * allows, and is cleared when the model changes: what one model accepts another may refuse. The caller saves through the service, which checks the config against the schema
 * generated from the same declaration.
 *
 * @param config The stored config, "{}" when the provider has none yet
 * @param onSave Called with the config to store
 * @param onReset Called to delete the stored config; null when there is none
 */
@Composable
fun AIProviderConfigScreen(
    provider: AIProvider,
    config: String,
    onSave: (JSONObject) -> Unit,
    onCancel: () -> Unit,
    onReset: (() -> Unit)?
) {
    // The header has no back button: the back key cancels, like the form's cancel button
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val nodes = remember(provider) { provider.getConfigSettings(context) }

    var settings by rememberSaveable(config, stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject(config)) }

    // The models the key gives access to, once listed
    var models by remember { mutableStateOf<List<ProviderModel>>(emptyList()) }
    var isFetching by remember { mutableStateOf(false) }

    // The settings the listing needs, and whether they are all filled in
    val listingSettings = remember(provider) { provider.modelListingSettings() }
    fun canList() = listingSettings.all { settings.optString(it).isNotBlank() }

    fun fetchModels() {
        if (!canList()) {
            val labels = listingSettings.mapNotNull { name -> nodes.storedField(name)?.displayName }
            UI.Toast(context, s.shared("ai_provider_fill_in_first").format(labels.joinToString(", ")), Duration.SHORT)
            return
        }
        scope.launch {
            isFetching = true
            val listed = provider.listModels(JSONObject(settings.toString()))
            isFetching = false
            models = listed.models
            listed.error?.let { UI.Toast(context, s.shared("ai_provider_fetch_error").format(it), Duration.LONG) }
            // A config without a model takes the first one listed
            if (!settings.has("model")) listed.models.firstOrNull()?.let { settings = JSONObject(settings.toString()).put("model", it.id) }
            // What the list says of the model's images, kept with the config once it is saved
            if (listed.models.isNotEmpty()) settings = withListedImageInput(JSONObject(settings.toString()), listed.models)
        }
    }

    // A stored config lists its models on opening
    LaunchedEffect(Unit) {
        if (canList()) fetchModels()
    }

    // What the chosen model allows of its reasoning; null when it allows nothing or is not listed
    val reasoning = models.find { it.id == settings.optString("model") }?.reasoning

    val editors = mapOf("model" to object : SettingEditor {
        @Composable
        override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
            val label = s.shared("ai_provider_model")
            when {
                isFetching -> UI.Text(s.shared("ai_provider_fetching_models"), TextType.CAPTION)
                models.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    UI.Text(label, TextType.LABEL)
                    UI.Text((value as? String) ?: s.shared("ai_provider_no_models"), TextType.CAPTION)
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        size = Size.M,
                        state = if (canList()) ComponentState.NORMAL else ComponentState.DISABLED,
                        onClick = { fetchModels() }
                    ) { UI.Text(s.shared("ai_provider_fetch_models"), TextType.BODY) }
                }
                else -> UI.FormSelection(
                    label = label,
                    options = models.map { it.label },
                    selected = models.find { it.id == value }?.label ?: (value as? String ?: ""),
                    onSelect = { chosen -> onChange(models.find { it.label == chosen }?.id) },
                    required = true
                )
            }
        }
    }, ImageInput.READS_IMAGES to object : SettingEditor {
        // Shown, never edited: what the list said, else what a fact says, else unknown
        @Composable
        override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
            val reads = provider.readsImages(settings, context)
            UI.Text(s.shared("label_value").format(s.shared("ai_provider_reads_images"), s.shared(when (reads) {
                true -> "ai_provider_reads_images_yes"
                false -> "ai_provider_reads_images_no"
                null -> "ai_provider_reads_images_unknown"
            })), TextType.CAPTION)
        }
    }, ReasoningSettings.THINKING_OFF to object : SettingEditor {
        @Composable
        override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
            val label = s.shared("ai_provider_thinking_off")
            val off = value == true
            when {
                // Not listed: what is stored, as the model field shows it
                models.isEmpty() -> if (off) UI.Text(label, TextType.CAPTION)
                reasoning?.thinkingOff != null -> UI.Switch(checked = off, label = label, onCheckedChange = { checked ->
                    // An effort thinking off does not accept goes with the switch
                    val next = JSONObject(settings.toString()).put(ReasoningSettings.THINKING_OFF, checked)
                    val accepted = reasoning.thinkingOff.efforts
                    if (checked && accepted != null && next.optString(ReasoningSettings.EFFORT) !in accepted) next.remove(ReasoningSettings.EFFORT)
                    settings = next
                })
            }
        }
    }, ReasoningSettings.EFFORT to object : SettingEditor {
        @Composable
        override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
            val label = s.shared("ai_provider_effort")
            val off = settings.optBoolean(ReasoningSettings.THINKING_OFF)
            when {
                models.isEmpty() -> (value as? String)?.let { UI.Text(s.shared("label_value").format(label, it), TextType.CAPTION) }
                reasoning == null -> UI.Text(s.shared("ai_provider_no_reasoning"), TextType.CAPTION)
                else -> {
                    // Thinking off takes an effort among those its fact accepts, never the model's default
                    val levels = if (off) reasoning.thinkingOff?.efforts ?: reasoning.efforts else reasoning.efforts
                    val required = off || reasoning.effortRequired
                    val byDefault = s.shared("ai_provider_effort_default")
                    UI.FormSelection(
                        label = label,
                        options = if (required) levels else listOf(byDefault) + levels,
                        selected = (value as? String) ?: if (required) "" else byDefault,
                        onSelect = { chosen -> onChange(chosen.takeIf { it != byDefault }) },
                        required = required
                    )
                }
            }
        }
    })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UI.Space.L)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = provider.getDisplayName(),
            subtitle = s.shared("settings_ai_providers_config")
        )

        UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
                // A new key or address lists other models: the ones listed before no longer hold
                SettingsForm(nodes, settings, { next ->
                    if (listingSettings.any { next.optString(it) != settings.optString(it) }) models = emptyList()
                    // Another model: the reasoning chosen for the previous one may not hold, and
                    // whether it reads images is what the list says of the new one
                    if (next.optString("model") != settings.optString("model")) {
                        next.remove(ReasoningSettings.EFFORT)
                        next.remove(ReasoningSettings.THINKING_OFF)
                        withListedImageInput(next, models)
                    }
                    settings = next
                }, context, editors)
                UI.Text(provider.getConfigHelp(context), TextType.CAPTION)
            }
        }

        UI.FormActions {
            UI.ActionButton(action = ButtonAction.SAVE, onClick = { onSave(settings) })
            UI.ActionButton(action = ButtonAction.CANCEL, onClick = onCancel)
            if (onReset != null) {
                UI.ActionButton(action = ButtonAction.RESET, requireConfirmation = true, onClick = onReset)
            }
        }
    }
}

/**
 * [settings] given what [models], the list, says of its model's images: stored when the list says,
 * removed when it says nothing or does not hold the model, a fact then deciding (ImageInput).
 */
private fun withListedImageInput(settings: JSONObject, models: List<ProviderModel>): JSONObject {
    val listed = models.find { it.id == settings.optString("model") }?.readsImages
    if (listed != null) settings.put(ImageInput.READS_IMAGES, listed) else settings.remove(ImageInput.READS_IMAGES)
    return settings
}
