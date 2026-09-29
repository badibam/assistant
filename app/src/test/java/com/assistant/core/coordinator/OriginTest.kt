package com.assistant.core.coordinator

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The origin of a call goes with the coroutine running it, into every call made from it; outside
 * an operation of the coordinator there is none, and asking for it is an error, never a guess.
 */
class OriginTest {

    @Test
    fun `the origin set is the one read, in nested calls too`() = runBlocking {
        withContext(Origin(Source.AI)) {
            assertEquals(Source.AI, currentOrigin())
            withContext(kotlinx.coroutines.Dispatchers.Default) { assertEquals(Source.AI, currentOrigin()) }
        }
    }

    @Test
    fun `without an operation there is no origin`() {
        assertThrows(IllegalStateException::class.java) { runBlocking { currentOrigin() } }
    }
}
