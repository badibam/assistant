package app.treelune.core.mcp

import android.os.Build
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The app's Tailscale node, in Go (app/src/main/go, docs/design/funnel-access.md), through JNI.
 * The library exists for arm64 only: [available] says whether it loaded, and nothing else here
 * may be called when it did not.
 */
object FunnelNative {

    /** Whether the library loaded; elsewhere than arm64 it is not in the APK, and only the relay is offered. */
    val available: Boolean by lazy {
        try {
            System.loadLibrary("treelune_funnel")
            true
        } catch (e: UnsatisfiedLinkError) {
            LogManager.service("External access: no Tailscale library for ${Build.SUPPORTED_ABIS.joinToString()}: ${e.message}", "INFO")
            false
        }
    }

    /** Creates the node if needed and opens its Funnel, in the background; again after a failure, tries again. */
    @JvmStatic external fun start(dir: String, hostname: String)

    /** Where the node stands, as JSON: state, url, address, message, and the messages since the last call. */
    @JvmStatic external fun state(): String

    @JvmStatic external fun stop()

    /** Takes the node out of the Tailscale account and erases it; the failure, or "". */
    @JvmStatic external fun logout(dir: String, hostname: String): String

    /** The default route's interface as Android reports it, "" when the network is lost. */
    @JvmStatic external fun networkChanged(ifName: String)

    /** The oldest request received through the Funnel, as the relay's JSON, or "" after [waitSeconds]. */
    @JvmStatic external fun next(waitSeconds: Int): String

    /** The answer to the request [id], as the relay's JSON. */
    @JvmStatic external fun reply(id: String, response: String)
}

/** The relay's contract served by the app's own Tailscale node: requests come through its Funnel. */
class FunnelTransport : RelayTransport {

    override suspend fun next(waitSeconds: Int): HttpRequest? = withContext(Dispatchers.IO) {
        FunnelNative.next(waitSeconds).takeIf { it.isNotEmpty() }?.let { HttpRequest.fromJson(JSONObject(it)) }
    }

    override suspend fun reply(id: String, response: HttpResponse) = withContext(Dispatchers.IO) {
        FunnelNative.reply(id, response.toJson().toString())
    }
}
