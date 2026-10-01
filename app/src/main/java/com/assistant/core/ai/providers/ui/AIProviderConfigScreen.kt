package com.assistant.core.ai.providers.ui

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
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.providers.AIProvider
import com.assistant.core.ai.providers.ProviderModel
import com.assistant.core.fields.settings.SettingEditor
import com.assistant.core.fields.settings.SettingsForm
import com.assistant.core.fields.settings.storedField
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.ComponentState
import com.assistant.core.ui.Duration
import com.assistant.core.ui.JsonObjectSaver
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The config screen of any AI provider: the form of its declaration (AIProvider.getConfigSettings),
 * the API key masked, and the model chosen among those the provider lists once the settings
 * the listing needs are filled in. The caller saves through the service, which checks the config against the schema
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
        }
    }

    // A stored config lists its models on opening
    LaunchedEffect(Unit) {
        if (canList()) fetchModels()
    }

    val editors = mapOf("model" to object : SettingEditor {
        @Composable
        override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
            val label = s.shared("ai_provider_model")
            when {
                isFetching -> UI.Text(s.shared("ai_provider_fetching_models"), TextType.CAPTION)
                models.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.PageHeader(
            title = provider.getDisplayName(),
            subtitle = s.shared("settings_ai_providers_config")
        )

        UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // A new key or address lists other models: the ones listed before no longer hold
                SettingsForm(nodes, settings, { next ->
                    if (listingSettings.any { next.optString(it) != settings.optString(it) }) models = emptyList()
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
