package app.treelune.core.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The access open, against a relay played by the test: each request answered, the network's failures waited out, the idle access closed. */
class RelayLoopTest {

    private var clock = 0L
    private var activity = 0L
    private val replies = mutableListOf<Pair<String, Int>>()
    private val pauses = mutableListOf<Long>()

    /** A relay handing over [script] in order, each step a request, nothing (null) or a failure; the clock moves 20 s a step. */
    private fun relay(vararg script: Any?) = object : RelayTransport {
        val steps = script.toMutableList()
        override suspend fun next(waitSeconds: Int): HttpRequest? {
            clock += 20_000
            if (steps.isEmpty()) { clock += RelayLoop.IDLE_LIMIT; return null }
            return when (val step = steps.removeAt(0)) {
                is Exception -> throw step
                else -> step as HttpRequest?
            }
        }
        override suspend fun reply(id: String, response: HttpResponse) { replies += id to response.status }
    }

    private fun request(id: String) = HttpRequest(id, "POST", "/mcp", "", emptyMap(), ByteArray(0))

    private fun loop(transport: RelayTransport, handle: suspend (HttpRequest) -> HttpResponse = { activity = clock; HttpResponse(200) }) =
        RelayLoop(transport, handle, { clock }, { activity }, { _, _ -> }, pause = { pauses += it })

    @Test
    fun eachRequestIsAnsweredInTurn() {
        runBlocking { loop(relay(request("a"), null, request("b"))).run() }
        assertEquals(listOf("a" to 200, "b" to 200), replies)
    }

    @Test
    fun aFailingHandlingStillAnswers() {
        runBlocking { loop(relay(request("a"))) { throw IllegalStateException("boom") }.run() }
        assertEquals(listOf("a" to 500), replies)
    }

    @Test
    fun theNetworkFailingIsWaitedOutLongerEachTime() {
        runBlocking { loop(relay(IOException("down"), IOException("down"), request("a"), IOException("down"))).run() }
        assertEquals(listOf("a" to 200), replies)
        assertEquals(listOf(5_000L, 10_000L, 5_000L), pauses)
    }

    @Test
    fun theRelayRefusingTheAppEndsTheLoop() {
        var thrown = false
        runBlocking { try { loop(relay(RelayRefused("404"), request("a"))).run() } catch (e: RelayRefused) { thrown = true } }
        assertTrue(thrown)
        assertTrue(replies.isEmpty())
    }

    @Test
    fun theAccessClosesAfterHalfAnHourWithoutACall() {
        // Nothing comes: steps of 20 s until the limit
        val nothing = arrayOfNulls<HttpRequest>(200)
        runBlocking { loop(relay(*nothing)).run() }
        assertTrue(clock >= RelayLoop.IDLE_LIMIT)
        assertTrue(clock < RelayLoop.IDLE_LIMIT + 40_000)
    }
}
