package com.assistant.core.mcp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.mcp.McpAccess
import com.assistant.core.mcp.McpHttp
import com.assistant.core.mcp.OAuthClient
import com.assistant.core.mcp.RoomOAuthStore
import com.assistant.core.database.AppDatabase
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.screens.settings.AppSettingsScreen
import com.assistant.core.utils.DateUtils
import kotlinx.coroutines.launch

/**
 * The external access (docs/design/mcp-server.md): the relay's address and secret, the access
 * opened and closed and where it stands, the address to give the outside AI, and the clients
 * authorized, each revoked once confirmed.
 */
@Composable
fun ExternalAccessSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val store = remember { RoomOAuthStore(AppDatabase.getDatabase(context)) }
    val state by McpAccess.state.collectAsState()
    var clients by remember { mutableStateOf<List<OAuthClient>>(emptyList()) }
    var relay by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf<OAuthClient?>(null) }

    // Read again whenever the access changes: a client authorized meanwhile appears
    LaunchedEffect(state) {
        clients = store.clients()
        relay = McpAccess.relay(context)?.first
    }

    revoking?.let { client ->
        UI.Dialog(
            type = DialogType.DANGER,
            onConfirm = {
                revoking = null
                scope.launch { store.removeClient(client.id); clients = store.clients() }
            },
            onCancel = { revoking = null }
        ) {
            UI.Text(s.shared("external_access_revoke_confirm").format(client.name), TextType.BODY)
        }
    }

    AppSettingsScreen(
        category = AppSettingCategories.EXTERNAL_ACCESS,
        onBack = onBack,
        below = {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(when (val current = state) {
                    McpAccess.State.Closed -> s.shared("external_access_state_closed")
                    is McpAccess.State.Open -> current.lastCallAt?.let { s.shared("external_access_state_open_last").format(DateUtils.formatFullDateTime(it)) }
                        ?: s.shared("external_access_state_open")
                    is McpAccess.State.Failed -> s.shared("external_access_state_failed").format(current.message)
                }, TextType.BODY)
                if (state is McpAccess.State.Open) {
                    UI.Button(type = ButtonType.DEFAULT, onClick = { McpAccess.close(context) }) {
                        UI.Text(s.shared("external_access_close"), TextType.LABEL)
                    }
                } else {
                    UI.Button(type = ButtonType.PRIMARY, onClick = {
                        scope.launch {
                            if (McpAccess.relay(context) == null) UI.Toast(context, s.shared("external_access_no_relay"), com.assistant.core.ui.Duration.LONG)
                            else McpAccess.open(context)
                        }
                    }) {
                        UI.Text(s.shared("external_access_open"), TextType.LABEL)
                    }
                }
                relay?.let { UI.Text(s.shared("external_access_mcp_address").format(it + McpHttp.MCP_PATH), TextType.CAPTION) }

                UI.Text(s.shared("external_access_clients"), TextType.SUBTITLE)
                if (clients.isEmpty()) UI.Text(s.shared("external_access_clients_none"), TextType.CAPTION)
                for (client in clients) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                            UI.Text(s.shared("external_access_client_line").format(
                                client.name,
                                DateUtils.formatDateForDisplay(client.createdAt),
                                client.lastUsedAt?.let { DateUtils.formatFullDateTime(it) } ?: s.shared("external_access_client_never")
                            ), TextType.BODY)
                        }
                        UI.Button(type = ButtonType.DANGER, onClick = { revoking = client }) {
                            UI.Text(s.shared("external_access_revoke"), TextType.LABEL)
                        }
                    }
                }
            }
        }
    )
}
