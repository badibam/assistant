package app.treelune.core.mcp

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The app's Tailscale node seen from Kotlin (docs/design/funnel-access.md): alive only while the
 * external access is open, its identity in the app's own files, out of every backup.
 */
object TailscaleNode {

    /** The node's name in the Tailscale account: its address is https://treelune.<tailnet>.ts.net */
    const val HOSTNAME = "treelune"

    /** A step the node waits on before its Funnel opens, shown with what to do. */
    sealed interface Step {
        data object Starting : Step
        /** Not yet in a Tailscale account: [url] is the login page */
        data class NeedsLogin(val url: String) : Step
        /** Funnel is off in the account: [url] is Tailscale's page that turns it on, HTTPS included */
        data class FunnelOff(val url: String) : Step
        /** The account's HTTPS certificates are off, and Tailscale offers no page: by hand in its console */
        data object HttpsMissing : Step
        /** The account's access policy does not grant Funnel to its members */
        data object FunnelMissing : Step
    }

    /** The node failed in a way no step in the console mends, said in its message. */
    class Failed(message: String) : Exception(message)

    /** The node's directory: its identity, certificate and technical log. */
    fun dir(context: Context) = File(context.filesDir, "tailscale")

    @Volatile private var network: ConnectivityManager.NetworkCallback? = null

    /**
     * Starts the node and waits for its Funnel, telling each step it waits on through [onStep].
     *
     * @return The node's public address (https://…, no trailing slash), or null when [limitMs]
     * passed with the node still waiting on a step
     * @throws Failed when the node fails for good
     */
    suspend fun open(context: Context, limitMs: Long, now: () -> Long, onStep: (Step) -> Unit): String? {
        val dir = dir(context).apply { mkdirs() }
        withContext(Dispatchers.IO) { FunnelNative.start(dir.absolutePath, HOSTNAME) }
        watchNetwork(context)
        val began = now()
        while (now() - began < limitMs) {
            when (val node = read()) {
                is NodeState.Ready -> return node.address
                is NodeState.Failed -> throw Failed(node.message)
                is NodeState.Waiting -> onStep(node.step)
            }
            delay(POLL_MS)
        }
        return null
    }

    /** Stops the node and stops following the network. */
    fun close(context: Context) {
        network?.let { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        network = null
        FunnelNative.stop()
    }

    /**
     * Takes the node out of the Tailscale account and erases its identity: the next opening asks
     * for a login again. The identity is erased even when the account could not be reached.
     *
     * @return The failure, when the account could not be told: the node may stay listed in its console
     */
    suspend fun logout(context: Context): String? = withContext(Dispatchers.IO) {
        FunnelNative.logout(dir(context).absolutePath, HOSTNAME).ifEmpty { null }
    }

    /**
     * Whether the node's address is in the public DNS yet: until then no client finds it. Null
     * when the node is not up.
     */
    fun published(): Boolean? = (read() as? NodeState.Ready)?.published

    /** Whether the node already holds its certificate for [address]: without it, the first call waits for one. */
    fun hasCertificate(context: Context, address: String): Boolean {
        val host = address.removePrefix("https://")
        return File(dir(context), "state/certs/$host.crt").isFile
    }

    private sealed interface NodeState {
        data class Waiting(val step: Step) : NodeState
        data class Ready(val address: String, val published: Boolean) : NodeState
        data class Failed(val message: String) : NodeState
    }

    /** The node's state, its messages handed to the app's log on the way. */
    private fun read(): NodeState {
        val json = JSONObject(FunnelNative.state())
        json.optJSONArray("log")?.let { lines ->
            for (i in 0 until lines.length()) LogManager.service("External access: ${lines.getString(i)}", "INFO")
        }
        return when (val state = json.getString("state")) {
            "ready" -> NodeState.Ready(json.getString("address"), json.optBoolean("published"))
            "needs_login" -> NodeState.Waiting(Step.NeedsLogin(json.getString("url")))
            "funnel_off" -> NodeState.Waiting(Step.FunnelOff(json.getString("url")))
            "https_missing" -> NodeState.Waiting(Step.HttpsMissing)
            "funnel_missing" -> NodeState.Waiting(Step.FunnelMissing)
            "starting" -> NodeState.Waiting(Step.Starting)
            "failed" -> NodeState.Failed(json.optString("message"))
            // Stopped under the loop's feet: closed by hand while starting
            "stopped" -> NodeState.Failed("stopped")
            else -> NodeState.Failed("unknown node state '$state'")
        }
    }

    /**
     * Tells the node each change of Android's default network, as Tailscale's own app does: on
     * Android the node re-reads its network only every ten minutes otherwise.
     */
    private fun watchNetwork(context: Context) {
        if (network != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                FunnelNative.networkChanged(linkProperties.interfaceName ?: "")
            }

            override fun onLost(network: Network) {
                FunnelNative.networkChanged("")
            }
        }
        context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
        network = callback
    }

    private const val POLL_MS = 1000L
}
