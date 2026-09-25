package com.assistant.core.ai.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Stop and Interrupt rely on one promise: cancelling the coroutine that waits for a provider
 * cuts the HTTP call at once, instead of leaving it to its read timeout.
 */
class HttpCallTest {

    /** A provider that accepts the connection and never answers, like a network gone silent. */
    @Test
    fun cancelling_closesTheConnectionAtOnce() = runBlocking {
        ServerSocket(0).use { server ->
            var connectionClosed = false
            val listener = thread {
                server.accept().use { socket ->
                    // read() returns -1 once the client closes its side
                    val input = socket.getInputStream()
                    while (input.read() != -1) { }
                    connectionClosed = true
                }
            }

            val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.MINUTES).build()
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val waiting = async(Dispatchers.IO) { client.awaitReply(request) }

            delay(300)
            val cancelledAt = System.currentTimeMillis()
            withTimeout(2_000) {
                waiting.cancel()
                waiting.join()
            }
            listener.join(2_000)

            assertTrue(waiting.isCancelled)
            assertTrue("the server should see the connection closed", connectionClosed)
            assertTrue(System.currentTimeMillis() - cancelledAt < 2_000)
        }
    }

    @Test
    fun anAnswer_comesBackWithItsStatusAndBody() = runBlocking {
        ServerSocket(0).use { server ->
            thread {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (reader.readLine().isNotEmpty()) { }
                    socket.getOutputStream().write(
                        "HTTP/1.1 429 Too Many Requests\r\nContent-Length: 4\r\nConnection: close\r\n\r\nslow".toByteArray()
                    )
                }
            }

            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val reply = OkHttpClient().awaitReply(request)

            assertEquals(429, reply.code)
            assertEquals("slow", reply.body)
            assertEquals(false, reply.isSuccessful)
        }
    }

    /** The request went out whole, then the connection dropped: the answer is lost, not unsent. */
    @Test
    fun aConnectionDroppedAfterSending_isALostResponse() = runBlocking {
        ServerSocket(0).use { server ->
            thread {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (reader.readLine().isNotEmpty()) { }
                    // Closed without a word, as a network that drops the connection
                }
            }

            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val failure = runCatching { noRetry().awaitReply(request) }.exceptionOrNull()

            assertTrue("got $failure", failure is ResponseLostException)
            assertEquals(AIFailure.LOST, aiFailureOf(failure!!))
        }
    }

    /**
     * A request that went out is never sent again by OkHttp itself. OkHttp resends on a fresh
     * connection when a reused pooled one fails, even after writing the request, unless its body
     * is one-shot. The providers leave that retry on, so the first call here leaves a pooled
     * connection, and the second one dies on it after being sent.
     */
    @Test
    fun aSentRequest_isNotResentByOkHttp() = runBlocking {
        ServerSocket(0).use { server ->
            val secondRequestReceived = java.util.concurrent.atomic.AtomicInteger(0)
            val listener = thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    readRequest(input)
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".toByteArray())
                    output.flush()
                    readRequest(input)
                    secondRequestReceived.incrementAndGet()
                    // Dropped after the request arrived, as a network that loses the answer
                }
                // A resend would come in on a new connection
                server.soTimeout = 1_000
                runCatching { server.accept() }.getOrNull()?.use { socket ->
                    readRequest(socket.getInputStream())
                    secondRequestReceived.incrementAndGet()
                }
            }

            val client = OkHttpClient()
            fun post() = Request.Builder()
                .url("http://127.0.0.1:${server.localPort}/")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            assertEquals(200, client.awaitReply(post()).code)
            val failure = runCatching { client.awaitReply(post()) }.exceptionOrNull()
            listener.join(3_000)

            assertTrue("got $failure", failure is ResponseLostException)
            assertEquals(1, secondRequestReceived.get())
        }
    }

    /** Reads one request: its headers up to the blank line, then its body by Content-Length. */
    private fun readRequest(input: java.io.InputStream) {
        val headers = StringBuilder()
        while (!headers.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b == -1) return
            headers.append(b.toChar())
        }
        val length = Regex("(?i)content-length: (\\d+)").find(headers)?.groupValues?.get(1)?.toInt() ?: 0
        repeat(length) { input.read() }
    }

    /** Nobody listening: nothing was sent, so waiting for the network costs nothing. */
    @Test
    fun aConnectionRefused_isANetworkFailure() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val request = Request.Builder().url("http://127.0.0.1:$port/").build()
        val failure = runCatching { noRetry().awaitReply(request) }.exceptionOrNull()

        assertTrue("got $failure", failure is IOException && failure !is ResponseLostException)
        assertEquals(AIFailure.NETWORK, aiFailureOf(failure!!))
    }

    // OkHttp silently resends on a fresh connection when one fails; the tests watch one attempt
    private fun noRetry() = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
}
