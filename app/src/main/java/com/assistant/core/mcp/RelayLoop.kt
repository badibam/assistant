package com.assistant.core.mcp

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The app's side of the relay's contract (docs/design/mcp-server.md, « Le contrat du relais »). */
interface RelayTransport {
    /** The oldest request waiting at the relay, or null when none came within [waitSeconds]. */
    suspend fun next(waitSeconds: Int): HttpRequest?

    /** The answer to the request [id]; dropped by the relay when its client no longer waits. */
    suspend fun reply(id: String, response: HttpResponse)
}

/** The relay refuses the app: a wrong secret or a wrong address (404 on _relay), which retrying will not mend. */
class RelayRefused(message: String) : Exception(message)

/**
 * The access open: ask the relay for the next request, answer it, again, one at a time. A network
 * failure waits and tries again, longer each time up to a minute; the relay refusing the app ends
 * the loop. It ends by itself once [idleLimit] has passed since the last activity.
 *
 * @param lastActivity The instant of the last call that did something (McpHttp's onCall), or the opening
 * @param log Where a failure is told
 */
class RelayLoop(
    private val transport: RelayTransport,
    private val handle: suspend (HttpRequest) -> HttpResponse,
    private val now: () -> Long,
    private val lastActivity: () -> Long,
    private val log: (String, Throwable?) -> Unit,
    private val idleLimit: Long = IDLE_LIMIT,
    private val waitSeconds: Int = WAIT_SECONDS,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    /**
     * Runs until the access has been idle [idleLimit] long.
     *
     * @throws RelayRefused when the relay does not recognize the app
     */
    suspend fun run() {
        var backoff = FIRST_BACKOFF
        while (now() - lastActivity() < idleLimit) {
            val request = try {
                transport.next(waitSeconds).also { backoff = FIRST_BACKOFF }
            } catch (e: RelayRefused) {
                throw e
            } catch (e: IOException) {
                log("relay unreachable, trying again in ${backoff / 1000} s: ${e.message}", e)
                pause(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
                continue
            } ?: continue

            val response = try {
                handle(request)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log("request ${request.method} ${request.path} failed: ${e.message}", e)
                HttpResponse(500)
            }
            try {
                transport.reply(request.id, response)
            } catch (e: IOException) {
                log("answer to ${request.id} lost: ${e.message}", e)
            }
        }
    }

    companion object {
        /** The access closes after this long without a call */
        const val IDLE_LIMIT = 30 * 60 * 1000L
        /** How long the relay holds a "next" before answering that nothing came, below its own REPLY_TIMEOUT */
        const val WAIT_SECONDS = 20
        const val FIRST_BACKOFF = 5_000L
        const val MAX_BACKOFF = 60_000L
    }
}

/** The relay at [base], reached with OkHttp, the app known by [secret]. */
class OkHttpRelayTransport(private val base: String, private val secret: String) : RelayTransport {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // The relay holds "next" up to the wait asked, then answers
        .readTimeout(RelayLoop.WAIT_SECONDS + 15L, TimeUnit.SECONDS)
        .build()

    override suspend fun next(waitSeconds: Int): HttpRequest? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = Request.Builder().url("$base/_relay/next?wait=$waitSeconds").header("Authorization", "Bearer $secret").get().build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> HttpRequest.fromJson(JSONObject(response.body?.string() ?: throw IOException("empty answer")))
                204 -> null
                404 -> throw RelayRefused("404")
                else -> throw IOException("relay answered ${response.code}")
            }
        }
    }

    override suspend fun reply(id: String, response: HttpResponse) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val body = response.toJson().toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$base/_relay/reply/$id").header("Authorization", "Bearer $secret").post(body).build()
        // 404: the client no longer waits, and the answer has nowhere to go
        client.newCall(request).execute().use { if (it.code !in listOf(200, 204, 404)) throw IOException("relay answered ${it.code}") }
    }
}
