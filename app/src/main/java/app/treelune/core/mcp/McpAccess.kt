package app.treelune.core.mcp

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.treelune.MainActivity
import app.treelune.core.config.AppSettings
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.icons.Icons
import app.treelune.core.notifications.NotificationChannels
import app.treelune.core.notifications.NotificationService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.SecureRandom

/**
 * The external access, for the whole app (docs/design/mcp-server.md, « L'accès ouvert »): opened
 * by hand, it runs the relay's loop in a foreground service (McpAccessService) until half an hour
 * passes without a call, the person closes it, or the relay refuses the app. The requests come
 * through the app's own Tailscale node or through a relay, as the access mode says
 * (docs/design/funnel-access.md). It holds the OAuth server, whose request waiting for its code
 * the screens show (McpApprovalDialog).
 */
object McpAccess {

    /** Where the access stands. */
    sealed interface State {
        data object Closed : State
        /** The Tailscale node waits on [step] before its address exists */
        data class Preparing(val step: TailscaleNode.Step) : State
        /** Open at [address] (https://…, no trailing slash) since [openedAt]; [lastCallAt] the last call that did something */
        data class Open(val address: String, val openedAt: Long, val lastCallAt: Long?) : State
        /** Closed by a failure, said in [message] */
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Closed)
    val state: StateFlow<State> = _state

    private val _published = MutableStateFlow<Boolean?>(null)
    /**
     * Whether the Tailscale node's address is in the public DNS yet, while the access is open
     * through it; null otherwise. A client finds the address only once it is.
     */
    val published: StateFlow<Boolean?> = _published

    private val _request = MutableStateFlow<PendingAuthorization?>(null)
    /** The request for access waiting for its code on this phone, if one is. */
    val request: StateFlow<PendingAuthorization?> = _request

    @Volatile private var oauthServer: OAuthServer? = null

    /** The OAuth server, one for the app: a request waiting for its code outlives the screens. */
    fun oauth(context: Context): OAuthServer = oauthServer ?: synchronized(this) {
        oauthServer ?: OAuthServer(
            store = RoomOAuthStore(AppDatabase.getDatabase(context)),
            prompt = NotificationPrompt(context.applicationContext),
            now = System::currentTimeMillis
        ).also { oauthServer = it }
    }

    /** The access mode as set (AppSettings.ACCESS_MODE): Tailscale or relay. */
    suspend fun mode(context: Context): String {
        val result = Coordinator(context).processUserAction("app_config.get", mapOf("category" to AppSettingCategories.EXTERNAL_ACCESS))
        val settings = result.data?.get("settings") as? Map<*, *>
        return settings?.get(AppSettings.ACCESS_MODE) as? String
            ?: throw IllegalStateException("External access settings without an access mode")
    }

    /** The relay's address and secret as set, or null while either is missing or the address is not https. */
    suspend fun relay(context: Context): Pair<String, String>? {
        val result = Coordinator(context).processUserAction("app_config.get", mapOf("category" to AppSettingCategories.EXTERNAL_ACCESS))
        val settings = result.data?.get("settings") as? Map<*, *> ?: return null
        // Parsed rather than compared: a scheme or a host typed with a capital is the same address,
        // written back in lower case
        val url = (settings[AppSettings.RELAY_URL] as? String)?.trim()?.toHttpUrlOrNull()?.takeIf { it.isHttps }
            ?.toString()?.trimEnd('/') ?: return null
        val secret = (settings[AppSettings.RELAY_SECRET] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return url to secret
    }

    /** Opens the access: the service starts, and runs the loop. */
    fun open(context: Context) {
        if (_state.value is State.Open || _state.value is State.Preparing) return
        ContextCompat.startForegroundService(context, Intent(context, McpAccessService::class.java))
    }

    /** Closes the access: the service stops, and the relay's next requests find no one. */
    fun close(context: Context) {
        context.startService(Intent(context, McpAccessService::class.java).setAction(McpAccessService.ACTION_CLOSE))
    }

    /**
     * The access open, until it closes: run by McpAccessService on its own scope.
     * Sets the state as it goes, and back to closed (or failed) at the end.
     */
    internal suspend fun run(context: Context, onActivity: (Long) -> Unit) {
        val s = Strings.`for`(context = context)
        val tailscale = mode(context) == AppSettings.ACCESS_MODE_TAILSCALE
        try {
            val (base, transport) = if (tailscale) {
                // A setting from another phone, on one without the library: said, never swapped for the relay
                if (!FunnelNative.available) {
                    _state.value = State.Failed(s.shared("external_access_tailscale_unavailable"))
                    return
                }
                val address = try {
                    TailscaleNode.open(context, RelayLoop.IDLE_LIMIT, System::currentTimeMillis) { _state.value = State.Preparing(it) }
                } catch (e: TailscaleNode.Failed) {
                    LogManager.service("External access: the Tailscale node failed: ${e.message}", "WARN", e)
                    _state.value = State.Failed(e.message ?: "")
                    return
                } ?: run {
                    // Half an hour waiting on a step: closed as an idle access is
                    _state.value = State.Closed
                    return
                }
                _published.value = false
                address to FunnelTransport()
            } else {
                val (base, secret) = relay(context) ?: run {
                    _state.value = State.Failed(s.shared("external_access_no_relay"))
                    return
                }
                base to OkHttpRelayTransport(base, secret)
            }
            coroutineScope {
                // Until Tailscale has published the address, no client can reach it: the screen says so
                val watch = if (tailscale) launch {
                    while (_published.value != true) {
                        _published.value = TailscaleNode.published() ?: false
                        delay(PUBLICATION_POLL_MS)
                    }
                } else null
                serve(context, s, base, transport, onActivity)
                watch?.cancel()
            }
        } finally {
            _published.value = null
            if (tailscale && FunnelNative.available) TailscaleNode.close(context)
            if (_state.value is State.Open || _state.value is State.Preparing) _state.value = State.Closed
        }
    }

    /** The access open at [base]: MCP and OAuth served through [transport] until the loop ends. */
    private suspend fun serve(context: Context, s: app.treelune.core.strings.StringsContext, base: String, transport: RelayTransport, onActivity: (Long) -> Unit) {
        val openedAt = System.currentTimeMillis()
        var lastActivity = openedAt
        _state.value = State.Open(base, openedAt, null)
        val server = McpServer(AppMcpBackend(context), ContextTokens(contextKey(context), System::currentTimeMillis), versionName(context))
        val http = McpHttp(base, oauth(context), server, s::shared, log = { LogManager.service("External access: $it", "INFO") }) {
            lastActivity = System.currentTimeMillis()
            _state.value = State.Open(base, openedAt, lastActivity)
            onActivity(lastActivity)
        }
        try {
            RelayLoop(
                transport = transport,
                handle = http::handle,
                now = System::currentTimeMillis,
                lastActivity = { lastActivity },
                log = { message, e -> LogManager.service("External access: $message", "WARN", e) }
            ).run()
            _state.value = State.Closed
        } catch (e: RelayRefused) {
            _state.value = State.Failed(s.shared("external_access_relay_refused"))
        } finally {
            if (_state.value is State.Open) _state.value = State.Closed
        }
    }

    /**
     * The key that signs the context tokens (ContextTokens): drawn once, kept in the app's own
     * files, never in the database, so neither a backup nor anyone reading one carries it; out of
     * Android's own backup too (res/xml/backup_rules.xml, data_extraction_rules.xml).
     */
    @Synchronized
    fun contextKey(context: Context): ByteArray {
        val file = File(context.filesDir, "mcp/context_key")
        if (file.exists()) return file.readBytes()
        file.parentFile?.mkdirs()
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // Written whole or not at all
        val part = File(file.parentFile, "context_key.part")
        part.writeBytes(key)
        if (!part.renameTo(file)) throw IllegalStateException("Cannot keep the context key")
        return key
    }

    private fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** A request for access told on the phone: kept for the screens, and notified so the person comes. */
    private class NotificationPrompt(private val context: Context) : ApprovalPrompt {
        override fun ask(request: PendingAuthorization) {
            _request.value = request
            val s = Strings.`for`(context = context)
            val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
            val pending = PendingIntent.getActivity(context, REQUEST_NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_HIGH)
                .setSmallIcon(Icons.lucideDrawable(context, NotificationService.APP_ICON) ?: android.R.drawable.ic_dialog_info)
                .setContentTitle(s.shared("external_access_request_title"))
                .setContentText(s.shared("external_access_request_text").format(request.client.name))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(REQUEST_NOTIFICATION_ID, notification)
        }

        override fun withdraw(requestId: String) {
            if (_request.value?.requestId == requestId) _request.value = null
            context.getSystemService(NotificationManager::class.java).cancel(REQUEST_NOTIFICATION_ID)
        }
    }

    private const val REQUEST_NOTIFICATION_ID = 0x4d43 // "MC"

    /** How often the screen's "address published" is read from the node, which asks the DNS itself every 10 s */
    private const val PUBLICATION_POLL_MS = 2_000L
}
