package com.assistant.core.ai.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
            val waiting = async(Dispatchers.IO) { client.newCall(request).awaitReply() }

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
            val reply = OkHttpClient().newCall(request).awaitReply()

            assertEquals(429, reply.code)
            assertEquals("slow", reply.body)
            assertEquals(false, reply.isSuccessful)
        }
    }
}
