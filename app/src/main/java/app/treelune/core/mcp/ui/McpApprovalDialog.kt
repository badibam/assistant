package app.treelune.core.mcp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.mcp.McpAccess
import app.treelune.core.mcp.OAuthServer
import app.treelune.core.mcp.PendingAuthorization
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * Over every screen, while a request for access waits for its code (McpAccess.request): who asks,
 * as it declares itself, where the answer goes, and the field for the code its page shows. The
 * right code allows; a wrong one says how many tries are left; leaving the window refuses.
 */
@Composable
fun McpApprovalDialog() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val request by McpAccess.request.collectAsState()
    val waiting = request ?: return
    var typed by remember(waiting.requestId) { mutableStateOf("") }
    var wrong by remember(waiting.requestId) { mutableStateOf<Int?>(null) }
    val oauth = remember { McpAccess.oauth(context) }

    fun ended(outcome: PendingAuthorization?) {
        when (outcome?.outcome) {
            PendingAuthorization.Outcome.APPROVED -> UI.Toast(context, s.shared("external_access_request_allowed"), Duration.LONG)
            PendingAuthorization.Outcome.WAITING -> wrong = OAuthServer.MAX_ATTEMPTS - outcome.attempts
            else -> UI.Toast(context, s.shared("external_access_request_ended"), Duration.LONG)
        }
    }

    UI.Dialog(
        type = DialogType.SELECTION,
        onConfirm = {},
        onCancel = { ended(oauth.refuse(waiting.requestId)) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(s.shared("external_access_request_title"), TextType.TITLE)
            UI.Text(s.shared("external_access_request_text").format(waiting.client.name), TextType.BODY)
            UI.Text(s.shared("external_access_request_declared").format(waiting.client.name, java.net.URI(waiting.redirectUri).host ?: waiting.redirectUri), TextType.CAPTION)
            UI.Text(s.shared("external_access_request_not_me"), TextType.CAPTION)
            UI.FormField(
                label = s.shared("external_access_request_code"),
                value = typed,
                onChange = { typed = it.filter(Char::isDigit).take(OAuthServer.CODE_DIGITS) },
                fieldType = FieldType.NUMERIC,
                required = true
            )
            wrong?.let { UI.Text(s.shared("external_access_request_wrong").format(it), TextType.CAPTION) }
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Button(type = ButtonType.DEFAULT, onClick = { ended(oauth.refuse(waiting.requestId)) }) {
                    UI.Text(s.shared("external_access_request_refuse"), TextType.LABEL)
                }
                UI.Button(type = ButtonType.PRIMARY, onClick = { if (typed.length == OAuthServer.CODE_DIGITS) ended(oauth.submitCode(waiting.requestId, typed)) }) {
                    UI.Text(s.shared("external_access_request_allow"), TextType.LABEL)
                }
            }
        }
    }
}
