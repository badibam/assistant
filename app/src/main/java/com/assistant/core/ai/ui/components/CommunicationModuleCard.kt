package com.assistant.core.ai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.ai.data.CommunicationModule
import com.assistant.core.ai.data.CommunicationModules
import com.assistant.core.fields.FieldValue
import com.assistant.core.fields.defaultValues
import com.assistant.core.fields.settings.SettingsForm
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Communication Module Card - inline in the message flow
 *
 * The fields the AI asks for, each entered with the input of its field type (SettingsForm, as
 * any declaration), under the AI's preText which holds the question. Confirming sends the
 * answer, its dates and durations in the form the AI reads; it is possible once every field
 * the module needs has a value its field takes. A module without fields is a confirmation.
 *
 * Below the form, two ways out of it: a note added to the answer, sent with it; and a reply by
 * message, which frees the composer while the form stays answerable. Sending that message is
 * what replaces the form, so choosing it costs nothing.
 *
 * @param module Communication module to display, checked when the AI's answer was parsed
 * @param onResponse Receives the answer as the AI will read it, and the note added, if any
 * @param onCancel Callback when user cancels the module
 * @param freeReply Whether the user chose to reply by message
 * @param onFreeReply Frees the composer for that message
 */
@Composable
fun CommunicationModuleCard(
    module: CommunicationModule,
    onResponse: (answer: String, note: String?) -> Unit,
    onCancel: () -> Unit,
    freeReply: Boolean,
    onFreeReply: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // The answer as stored values, kept across a rotation as its JSON text; it starts with the
    // default values the AI gave its fields, the answer it suggests
    var answerJson by rememberSaveable {
        mutableStateOf(JsonUtils.toJSONObject(module.fields.map { it.definition }.defaultValues()).toString())
    }
    val answer = remember(answerJson) { JSONObject(answerJson) }
    val isComplete = remember(answerJson) { CommunicationModules.checkAnswer(module, answer, context).isValid }
    // The note added to the answer: null while the user has not asked for one
    var note by rememberSaveable { mutableStateOf<String?>(null) }

    UI.InteractionCard(
        title = s.shared(if (module.fields.isEmpty()) "ai_module_validation_title" else "ai_module_question_title"),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {
                if (module.fields.isNotEmpty()) {
                    SettingsForm(
                        nodes = module.fields,
                        config = answer,
                        onChange = { answerJson = it.toString() },
                        context = context
                    )
                }

                note?.let { text ->
                    UI.FormField(
                        label = s.shared("ai_module_note"),
                        value = text,
                        onChange = { note = it },
                        fieldType = FieldType.TEXT_MEDIUM,
                        required = false
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.ActionButton(
                            action = ButtonAction.CANCEL,
                            display = ButtonDisplay.LABEL,
                            size = Size.M,
                            onClick = onCancel
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        UI.ActionButton(
                            action = ButtonAction.CONFIRM,
                            display = ButtonDisplay.LABEL,
                            size = Size.M,
                            enabled = isComplete,
                            onClick = {
                                onResponse(
                                    if (module.fields.isEmpty()) CONFIRMED
                                    else CommunicationModules.answerForModel(module, answer, context).toString(),
                                    note?.trim()?.takeIf { it.isNotEmpty() }
                                )
                            }
                        )
                    }
                }

                // The other ways to answer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note == null) {
                        UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = { note = "" }) {
                            UI.Text(text = s.shared("ai_module_add_note"), type = TextType.CAPTION)
                        }
                    }
                    if (!freeReply) {
                        UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = onFreeReply) {
                            UI.Text(text = s.shared("ai_module_free_reply"), type = TextType.CAPTION)
                        }
                    }
                }
                if (freeReply) {
                    UI.Text(text = s.shared("ai_module_free_reply_hint"), type = TextType.CAPTION)
                }
            }
        },
        actions = {
            // The buttons are inside the content, next to the fields they send
        }
    )
}

/** What a module without fields answers when the user confirms: read by the AI. */
const val CONFIRMED = "confirmed"

/**
 * The user's answer to a module, shown in the history under the AI's question: each field by
 * its label and the display of its field type, or the confirmation for a module without fields.
 *
 * @param module The module answered
 * @param answer The answer as the AI read it, after the response prefix
 */
@Composable
fun CommunicationAnswer(module: CommunicationModule, answer: String) {
    val context = LocalContext.current

    if (module.fields.isEmpty()) {
        UI.Text(text = Strings.`for`(context = context).shared("ai_module_confirmed"), type = TextType.BODY)
        return
    }

    val values = remember(answer) {
        try {
            CommunicationModules.answerFromModel(module, JSONObject(answer), context)
        } catch (e: Exception) {
            // Stored by the app from a checked answer: one that does not read is shown as stored
            LogManager.aiUI("Communication answer does not read: ${e.message}", "WARN")
            null
        }
    }
    if (values == null) {
        UI.Text(text = answer, type = TextType.BODY)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        module.fields.forEach { field ->
            UI.Text(text = field.definition.displayName, type = TextType.LABEL)
            FieldValue(field.definition, JsonUtils.toValue(values.opt(field.definition.name)), context)
        }
    }
}
