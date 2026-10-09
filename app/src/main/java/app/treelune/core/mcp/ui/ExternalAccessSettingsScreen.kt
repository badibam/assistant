package app.treelune.core.mcp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.mcp.McpAccess
import app.treelune.core.mcp.McpHttp
import app.treelune.core.mcp.OAuthClient
import app.treelune.core.mcp.RoomOAuthStore
import app.treelune.core.mcp.TailscaleNode
import app.treelune.core.config.AppSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.screens.settings.AppSettingsScreen
import app.treelune.core.utils.DateUtils
import kotlinx.coroutines.launch

/**
 * The external access (docs/design/mcp-server.md, docs/design/funnel-access.md): the way in,
 * Tailscale or a relay, and the relay's address and secret; the access opened and closed and
 * where it stands -- with Tailscale, the one step its node reports missing and what to do; the
 * address to give the outside AI; the clients authorized, each revoked once confirmed.
 */
@Composable
fun ExternalAccessSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val store = remember { RoomOAuthStore(AppDatabase.getDatabase(context)) }
    val state by McpAccess.state.collectAsState()
    val published by McpAccess.published.collectAsState()
    var clients by remember { mutableStateOf<List<OAuthClient>>(emptyList()) }
    var relay by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf<OAuthClient?>(null) }
    // The client whose access is being set, and that access as it is being edited
    var editingAccess by remember { mutableStateOf<OAuthClient?>(null) }
    var accessDraft by remember { mutableStateOf(app.treelune.core.access.AccessMask()) }
    var mode by remember { mutableStateOf<String?>(null) }
    var loggingOut by remember { mutableStateOf(false) }

    // Read again whenever the access changes: a client authorized meanwhile appears
    LaunchedEffect(state) {
        clients = store.clients()
        relay = McpAccess.relay(context)?.first
        mode = McpAccess.mode(context)
    }

    if (loggingOut) {
        UI.Dialog(
            type = DialogType.DANGER,
            onConfirm = {
                loggingOut = false
                scope.launch {
                    val failure = TailscaleNode.logout(context)
                    UI.Toast(context, failure?.let { s.shared("external_access_tailscale_logout_failed").format(it) }
                        ?: s.shared("external_access_tailscale_logout_done"), app.treelune.core.ui.Duration.LONG)
                }
            },
            onCancel = { loggingOut = false }
        ) {
            UI.Text(s.shared("external_access_tailscale_logout_confirm"), TextType.BODY)
        }
    }

    editingAccess?.let { client ->
        UI.Dialog(
            type = DialogType.EDIT,
            onConfirm = {
                val chosen = accessDraft
                editingAccess = null
                scope.launch { app.treelune.core.mcp.McpClientAccess.write(context, client.id, chosen) }
            },
            onCancel = { editingAccess = null }
        ) {
            androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                app.treelune.core.access.ui.AccessCard(
                    s.shared("external_access_client_access_title").format(client.name),
                    s.shared("external_access_client_access_help"),
                    accessDraft, { accessDraft = it }
                )
            }
        }
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
                when (val current = state) {
                    McpAccess.State.Closed -> UI.Text(s.shared("external_access_state_closed"), TextType.BODY)
                    is McpAccess.State.Preparing -> TailscaleStep(current.step, s)
                    is McpAccess.State.Open -> {
                        UI.Text(current.lastCallAt?.let { s.shared("external_access_state_open_last").format(DateUtils.formatFullDateTime(it)) }
                            ?: s.shared("external_access_state_open"), TextType.BODY)
                        when (published) {
                            false -> UI.Text(s.shared("external_access_publishing"), TextType.CAPTION)
                            true -> {
                                UI.Text(s.shared("external_access_published"), TextType.CAPTION)
                                if (current.lastCallAt == null && !TailscaleNode.hasCertificate(context, current.address)) {
                                    UI.Text(s.shared("external_access_first_call"), TextType.CAPTION)
                                }
                            }
                            // The relay's address: there as soon as the relay is
                            null -> {}
                        }
                    }
                    is McpAccess.State.Failed -> UI.Text(s.shared("external_access_state_failed").format(current.message), TextType.BODY)
                }
                if (state is McpAccess.State.Open || state is McpAccess.State.Preparing) {
                    UI.Button(type = ButtonType.DEFAULT, onClick = { McpAccess.close(context) }) {
                        UI.Text(s.shared("external_access_close"), TextType.LABEL)
                    }
                } else {
                    UI.Button(type = ButtonType.PRIMARY, onClick = {
                        scope.launch {
                            if (McpAccess.mode(context) == AppSettings.ACCESS_MODE_RELAY && McpAccess.relay(context) == null) {
                                UI.Toast(context, s.shared("external_access_no_relay"), app.treelune.core.ui.Duration.LONG)
                            } else McpAccess.open(context)
                        }
                    }) {
                        UI.Text(s.shared("external_access_open"), TextType.LABEL)
                    }
                }
                // The address to give: the node's once it is up, the relay's as set
                val address = (state as? McpAccess.State.Open)?.address ?: relay.takeIf { mode == AppSettings.ACCESS_MODE_RELAY }
                address?.let { UI.Text(s.shared("external_access_mcp_address").format(it + McpHttp.MCP_PATH), TextType.CAPTION) }
                if (mode == AppSettings.ACCESS_MODE_TAILSCALE && (state is McpAccess.State.Closed || state is McpAccess.State.Failed)
                    && TailscaleNode.dir(context).resolve("state").exists()) {
                    UI.Button(type = ButtonType.DANGER, onClick = { loggingOut = true }) {
                        UI.Text(s.shared("external_access_tailscale_logout"), TextType.LABEL)
                    }
                }

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
                        UI.Button(type = ButtonType.DEFAULT, onClick = {
                            scope.launch {
                                accessDraft = app.treelune.core.mcp.McpClientAccess.read(context, client.id)
                                editingAccess = client
                            }
                        }) {
                            UI.Text(s.shared("external_access_client_access"), TextType.LABEL)
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

/** The block that grants Funnel to the account's members, to paste at the top level of its access policy. */
private const val FUNNEL_POLICY = """"nodeAttrs": [
  { "target": ["autogroup:member"], "attr": ["funnel"] }
],"""

private const val CONSOLE_DNS = "https://login.tailscale.com/admin/dns"
private const val CONSOLE_POLICY = "https://login.tailscale.com/admin/acls/file"

/**
 * The step the Tailscale node waits on, with what to do: the page to open. The node tries again by
 * itself every few seconds, and the access opens once the step is done.
 */
@Composable
private fun TailscaleStep(step: TailscaleNode.Step, s: app.treelune.core.strings.StringsContext) {
    val context = LocalContext.current
    when (step) {
        TailscaleNode.Step.Starting -> UI.Text(s.shared("external_access_preparing"), TextType.BODY)
        is TailscaleNode.Step.NeedsLogin -> {
            UI.Text(s.shared("external_access_needs_login"), TextType.BODY)
            UI.Button(type = ButtonType.PRIMARY, onClick = { openLink(context, step.url) }) {
                UI.Text(s.shared("external_access_login"), TextType.LABEL)
            }
        }
        is TailscaleNode.Step.FunnelOff -> {
            UI.Text(s.shared("external_access_funnel_off"), TextType.BODY)
            UI.Button(type = ButtonType.PRIMARY, onClick = { openLink(context, step.url) }) {
                UI.Text(s.shared("external_access_enable_funnel"), TextType.LABEL)
            }
        }
        TailscaleNode.Step.HttpsMissing -> {
            UI.Text(s.shared("external_access_https_missing"), TextType.BODY)
            UI.Button(type = ButtonType.DEFAULT, onClick = { openLink(context, CONSOLE_DNS) }) {
                UI.Text(s.shared("external_access_open_dns"), TextType.LABEL)
            }
        }
        TailscaleNode.Step.FunnelMissing -> {
            UI.Text(s.shared("external_access_funnel_missing"), TextType.BODY)
            UI.Text(FUNNEL_POLICY, TextType.CAPTION)
            UI.Button(type = ButtonType.DEFAULT, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Tailscale policy", FUNNEL_POLICY))
                UI.Toast(context, s.shared("external_access_policy_copied"), app.treelune.core.ui.Duration.SHORT)
            }) {
                UI.Text(s.shared("external_access_copy_policy"), TextType.LABEL)
            }
            UI.Button(type = ButtonType.DEFAULT, onClick = { openLink(context, CONSOLE_POLICY) }) {
                UI.Text(s.shared("external_access_open_policy"), TextType.LABEL)
            }
        }
    }
}

/** Opens [url] in the browser. */
private fun openLink(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
