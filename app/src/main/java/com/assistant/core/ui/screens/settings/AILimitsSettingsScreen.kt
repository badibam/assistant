package com.assistant.core.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.domain.AILimitsConfig
import com.assistant.core.schemas.AppConfigSchemaProvider
import com.assistant.core.services.AppConfigService
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.AppConfigManager
import kotlinx.coroutines.launch

/**
 * AI limits settings: how many times in a row the AI may call itself, per session type.
 *
 * Every call is paid for; the limit is what stops an AI that keeps calling itself. A CHAT counts
 * from the user's last intervention, an AUTOMATION over its whole session. The slider bounds are
 * the schema's, so a value the screen offers is one the save accepts.
 */
@Composable
fun AILimitsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var chatLimit by rememberSaveable { mutableStateOf(AILimitsConfig.default().chatMaxAutonomousRoundtrips) }
    var automationLimit by rememberSaveable { mutableStateOf(AILimitsConfig.default().automationMaxAutonomousRoundtrips) }

    val configLoad = rememberLoadOnce(Unit) {
        try {
            val limits = AppConfigManager.getAILimits()
            chatLimit = limits.chatMaxAutonomousRoundtrips
            automationLimit = limits.automationMaxAutonomousRoundtrips
            true
        } catch (e: Exception) {
            errorMessage = s.shared("settings_ai_limits_error_load").format(e.message ?: "")
            false
        }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    fun saveSettings() {
        coroutineScope.launch {
            try {
                AppConfigService(context).setAILimits(
                    AILimitsConfig(
                        chatMaxAutonomousRoundtrips = chatLimit,
                        automationMaxAutonomousRoundtrips = automationLimit
                    )
                )
                // The AI reads its limits from this cache at every step
                AppConfigManager.refresh(context)
                UI.Toast(context, s.shared("settings_saved"), Duration.SHORT)
                onBack()
            } catch (e: Exception) {
                errorMessage = s.shared("settings_ai_limits_error_save").format(e.message ?: "")
            }
        }
    }

    if (configLoad == LoadState.LOADING) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            UI.Text(text = s.shared("message_loading"), type = TextType.BODY)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.PageHeader(
            title = s.shared("settings_ai_limits"),
            subtitle = s.shared("settings_ai_limits_description"),
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = null
        )

        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val chatRange = AppConfigSchemaProvider.AI_LIMITS_CHAT_RANGE
                UI.SliderField(
                    label = s.shared("app_config_ai_limits_chat"),
                    value = chatLimit,
                    onValueChange = { chatLimit = it },
                    range = chatRange,
                    minLabel = chatRange.first.toString(),
                    maxLabel = chatRange.last.toString()
                )
                UI.Text(text = s.shared("settings_ai_limits_chat_help"), type = TextType.CAPTION)
            }
        }

        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val automationRange = AppConfigSchemaProvider.AI_LIMITS_AUTOMATION_RANGE
                UI.SliderField(
                    label = s.shared("app_config_ai_limits_automation"),
                    value = automationLimit,
                    onValueChange = { automationLimit = it },
                    range = automationRange,
                    minLabel = automationRange.first.toString(),
                    maxLabel = automationRange.last.toString()
                )
                UI.Text(text = s.shared("settings_ai_limits_automation_help"), type = TextType.CAPTION)
            }
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            UI.ActionButton(
                action = ButtonAction.SAVE,
                display = ButtonDisplay.LABEL,
                size = Size.L,
                enabled = configLoad == LoadState.LOADED,
                onClick = { saveSettings() }
            )
        }
    }
}
