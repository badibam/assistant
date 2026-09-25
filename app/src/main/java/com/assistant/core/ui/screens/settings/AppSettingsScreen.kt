package com.assistant.core.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.config.AppSettings
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
 * the service, which checks it and says what it refuses.
 */
@Composable
fun AppSettingsScreen(category: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val nodes = remember(category) { AppSettings.nodes(category, context) }

    var settings by rememberSaveable(category, stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject()) }
    var isSaving by remember { mutableStateOf(false) }

    val load = rememberLoadOnce(category) {
        val result = coordinator.processUserAction("app_config.get", mapOf("category" to category))
        @Suppress("UNCHECKED_CAST")
        val stored = result.data?.get("settings") as? Map<String, Any?>
        if (!result.isSuccess || stored == null) {
            UI.Toast(context, result.error ?: s.shared("error_load_failed"), Duration.LONG)
            return@rememberLoadOnce false
        }
        settings = JsonUtils.toJSONObject(stored)
        true
    }

    fun save() {
        isSaving = true
        scope.launch {
            val result = coordinator.processUserAction("app_config.set", mapOf("category" to category, "settings" to JsonUtils.toMap(settings)))
            isSaving = false
            if (result.isSuccess) {
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
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SettingsForm(nodes, settings, { settings = it }, context)
            }
        }

        UI.FormActions {
            UI.ActionButton(action = ButtonAction.SAVE, enabled = !isSaving && load == LoadState.LOADED, onClick = { save() })
            UI.ActionButton(action = ButtonAction.CANCEL, onClick = onBack)
        }
    }
}
