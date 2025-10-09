package com.assistant.core.ai.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.CommunicationModule
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*

/**
 * Communication Module Card - Inline display in message flow
 *
 * Displays communication modules (MultipleChoice, Validation) as interactive cards
 * within the AI message flow, allowing users to respond or cancel.
 *
 * Design: Card with primary color border highlight to attract attention
 *
 * @param module Communication module to display
 * @param onResponse Callback when user responds (receives response text)
 * @param onCancel Callback when user cancels the module
 */
@Composable
fun CommunicationModuleCard(
    module: CommunicationModule,
    onResponse: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Use unified InteractionCard with primary border
    InteractionCard(
        title = when (module) {
            is CommunicationModule.MultipleChoice -> s.shared("ai_module_multiple_choice_title")
            is CommunicationModule.Validation -> s.shared("ai_module_validation_title")
        },
        content = {
            // Module-specific content
            when (module) {
                is CommunicationModule.MultipleChoice -> {
                    MultipleChoiceModule(
                        module = module,
                        onResponse = onResponse,
                        onCancel = onCancel
                    )
                }
                is CommunicationModule.Validation -> {
                    ValidationModule(
                        module = module,
                        onResponse = onResponse,
                        onCancel = onCancel
                    )
                }
            }
        },
        actions = {
            // Action buttons are handled within the specific modules
            // This slot is not used for communication modules
        }
    )
}

/**
 * Multiple Choice Module - Inline display
 *
 * Displays a question with multiple options as buttons.
 * User must select one option before confirming.
 */
@Composable
private fun MultipleChoiceModule(
    module: CommunicationModule.MultipleChoice,
    onResponse: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Extract data
    val question = module.data["question"] as? String ?: s.shared("ai_module_no_question")

    // Parse options (can be List<String> or JSONArray string)
    val options = when (val optionsData = module.data["options"]) {
        is List<*> -> optionsData.mapNotNull { it as? String }
        is org.json.JSONArray -> {
            // Parse JSONArray to List<String>
            List(optionsData.length()) { i -> optionsData.getString(i) }
        }
        else -> emptyList()
    }

    // Local state for selected option
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Question
        UI.Text(
            text = question,
            type = TextType.BODY
        )

        // Options selection
        if (options.isNotEmpty()) {
            UI.Text(
                text = s.shared("ai_module_select_option"),
                type = TextType.LABEL
            )

            // Display options as buttons
            options.forEach { option ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    UI.Button(
                        type = if (selectedOption == option) ButtonType.PRIMARY else ButtonType.DEFAULT,
                        size = Size.M,
                        onClick = { selectedOption = option }
                    ) {
                        UI.Text(
                            text = option,
                            type = TextType.BODY
                        )
                    }
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button
            Box(modifier = Modifier.weight(1f)) {
                UI.ActionButton(
                    action = ButtonAction.CANCEL,
                    display = ButtonDisplay.LABEL,
                    size = Size.M,
                    onClick = onCancel
                )
            }

            // Confirm button (enabled only if option selected)
            Box(modifier = Modifier.weight(1f)) {
                UI.ActionButton(
                    action = ButtonAction.CONFIRM,
                    display = ButtonDisplay.LABEL,
                    size = Size.M,
                    enabled = selectedOption != null,
                    onClick = {
                        selectedOption?.let { onResponse(it) }
                    }
                )
            }
        }
    }
}

/**
 * Validation Module - Inline display
 *
 * Displays a confirmation message with Yes/No buttons.
 */
@Composable
private fun ValidationModule(
    module: CommunicationModule.Validation,
    onResponse: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Extract data
    val message = module.data["message"] as? String ?: s.shared("ai_module_no_message")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        UI.Text(
            text = s.shared("ai_module_validation_title"),
            type = TextType.SUBTITLE
        )

        // Message
        UI.Text(
            text = message,
            type = TextType.BODY
        )

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button
            Box(modifier = Modifier.weight(1f)) {
                UI.ActionButton(
                    action = ButtonAction.CANCEL,
                    display = ButtonDisplay.LABEL,
                    size = Size.M,
                    onClick = onCancel
                )
            }

            // Confirm button
            Box(modifier = Modifier.weight(1f)) {
                UI.ActionButton(
                    action = ButtonAction.CONFIRM,
                    display = ButtonDisplay.LABEL,
                    size = Size.M,
                    onClick = { onResponse("confirmed") }
                )
            }
        }
    }
}
