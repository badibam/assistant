package app.treelune.core.ai.providers

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * What came back from a provider: the HTTP status and the whole body.
 */
internal data class HttpReply(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * The request went out whole, then the connection failed before the answer was in.
 */
class ResponseLostException(cause: IOException) :
    IOException("Request sent, response lost: ${cause.message}", cause)

/**
 * Put in the coroutine context around a provider call, it learns whether the request went out
 * whole, even when the call ends in a cancellation that carries no result.
 */
class RequestSent : AbstractCoroutineContextElement(Key) {
    private val flag = AtomicBoolean(false)
    val sent: Boolean get() = flag.get()
    internal fun mark() = flag.set(true)

    companion object Key : CoroutineContext.Key<RequestSent>
}

/**
 * Send the request and suspend until the whole body of the answer has arrived.
 *
 * Cancelling the coroutine cancels the call itself: the socket is closed and the wait ends at
 * once, where `execute()` would keep its thread blocked until the answer or the read timeout.
 * The body is read inside the callback, so a cancellation also cuts a body still downloading.
 *
 * A connection failure after the request was fully written comes back as
 * [ResponseLostException]: the provider may have received it and billed it. Before that point,
 * the plain IOException means nothing reached it.
 *
 * The body is marked one-shot, so OkHttp never resends a request it has begun to send: a request
 * sent twice may be billed twice. It still retries a dead pooled connection, before anything
 * goes out.
 */
internal suspend fun OkHttpClient.awaitReply(request: Request): HttpReply {
    val sent = AtomicBoolean(false)
    val watcher = currentCoroutineContext()[RequestSent]
    fun markSent() {
        sent.set(true)
        watcher?.mark()
    }
    // A per-call copy shares the connection pool and dispatcher; only the listener differs
    val call = newBuilder()
        .eventListener(object : EventListener() {
            override fun requestHeadersEnd(call: Call, request: Request) {
                if (request.body == null) markSent()
            }

            override fun requestBodyEnd(call: Call, byteCount: Long) {
                markSent()
            }
        })
        .build()
        .newCall(request.body?.let { request.newBuilder().method(request.method, OneShot(it)).build() } ?: request)

    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(if (sent.get()) ResponseLostException(e) else e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val reply = response.use { HttpReply(it.code, it.body?.string() ?: "") }
                    continuation.resume(reply)
                } catch (e: IOException) {
                    // The answer had begun: the request was received
                    continuation.resumeWithException(ResponseLostException(e))
                }
            }
        })
    }
}

private class OneShot(private val body: RequestBody) : RequestBody() {
    override fun contentType(): MediaType? = body.contentType()
    override fun contentLength(): Long = body.contentLength()
    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
    override fun isOneShot(): Boolean = true
}
