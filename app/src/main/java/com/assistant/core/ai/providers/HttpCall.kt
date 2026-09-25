package com.assistant.core.ai.providers

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * What came back from a provider: the HTTP status and the whole body.
 */
internal data class HttpReply(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Run the call and suspend until its whole body has arrived.
 *
 * Cancelling the coroutine cancels the call itself: the socket is closed and the wait ends at
 * once, where `execute()` would keep its thread blocked until the answer or the read timeout.
 * The body is read inside the callback, so a cancellation also cuts a body still downloading.
 */
internal suspend fun Call.awaitReply(): HttpReply = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }

    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val reply = response.use { HttpReply(it.code, it.body?.string() ?: "") }
                continuation.resume(reply)
            } catch (e: IOException) {
                continuation.resumeWithException(e)
            }
        }
    })
}
