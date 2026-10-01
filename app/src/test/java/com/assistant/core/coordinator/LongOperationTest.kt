package com.assistant.core.coordinator

import com.assistant.core.commands.CommandResult
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The app's one place for a long operation: one at a time, a second one refused without running,
 * the place free again at the end; a caller that goes does not stop it, and its end is given to
 * the app; a step is said only while one runs.
 */
class LongOperationTest {

    private val done = CommandResult(status = CommandStatus.SUCCESS)
    private val busy = { running: LongOperation.Running -> CommandResult(status = CommandStatus.ERROR, error = running.label) }

    @Test
    fun `a second long operation is refused while one runs, and runs once the place is free`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val first = async { LongOperation.run("demo", busy) { release.await(); done } }
        withTimeout(5_000) { LongOperation.running.first { it != null } }

        var secondRan = false
        val refused = LongOperation.run("import", busy) { secondRan = true; done }
        assertEquals(CommandStatus.ERROR, refused.status)
        assertEquals("demo", refused.error)
        assertEquals(false, secondRan)

        release.complete(Unit)
        assertEquals(CommandStatus.SUCCESS, first.await().status)
        assertNull(LongOperation.running.value)
        assertEquals(CommandStatus.SUCCESS, LongOperation.run("import", busy) { done }.status)
    }

    @Test
    fun `a caller that goes leaves the operation running, its end given to the app`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val ended = async { withTimeout(5_000) { LongOperation.unclaimed.first() } }
        val caller = launch { LongOperation.run("backup", busy) { release.await(); done } }
        withTimeout(5_000) { LongOperation.running.first { it != null } }

        caller.cancelAndJoin()
        assertEquals("backup", LongOperation.running.value?.label)

        release.complete(Unit)
        assertEquals(LongOperation.Ended("backup", done), ended.await())
        withTimeout(5_000) { LongOperation.running.first { it == null } }
        Unit
    }

    @Test
    fun `a step is said only while a long operation runs`() = runBlocking {
        assertThrows(IllegalStateException::class.java) { LongOperation.at("Lines: 1 / 2") }
        val release = CompletableDeferred<Unit>()
        val op = async { LongOperation.run("import", busy) { release.await(); done } }
        withTimeout(5_000) { LongOperation.running.first { it != null } }
        LongOperation.at("Lines: 1 / 2")
        assertEquals(LongOperation.Running("import", "Lines: 1 / 2"), LongOperation.running.value)
        release.complete(Unit)
        op.await()
        Unit
    }
}
