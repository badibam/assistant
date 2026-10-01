package com.assistant.core.coordinator

import com.assistant.core.commands.CommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one place of the app for a long operation — an operation a service declares long
 * (`ExecutableService.longOperations`): installing the demo, importing a file, a backup. One runs
 * at a time in the whole app; a second one asked meanwhile is refused, never queued. Ordinary
 * operations go on while it runs.
 *
 * It runs in the app's scope, not its caller's: a caller that goes (its screen left) does not stop
 * it, and its end is then given to the app to show once ([unclaimed]).
 */
object LongOperation {

    /** What runs: its name ([label], the service's verbalized operation), and its current step if it says one. */
    data class Running(val label: String, val step: String? = null)

    /** How a long operation ended whose caller had gone. */
    data class Ended(val label: String, val result: CommandResult)

    private val current = MutableStateFlow<Running?>(null)
    val running: StateFlow<Running?> = current

    private val ended = MutableSharedFlow<Ended>(extraBufferCapacity = 8)
    val unclaimed: SharedFlow<Ended> = ended

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Runs [block] in the place when it is free, and returns its result; the place taken, returns
     * [busy] of what runs, without running anything.
     */
    suspend fun run(label: String, busy: (Running) -> CommandResult, block: suspend () -> CommandResult): CommandResult {
        val taken = Running(label)
        if (!current.compareAndSet(null, taken)) return busy(current.value ?: taken)
        val work = scope.async {
            try { block() } finally { current.value = null }
        }
        return try {
            work.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The caller went: the operation goes on, and its end goes to the app, already there or not
            scope.launch { ended.emit(Ended(label, work.await())) }
            throw e
        }
    }

    /** The step the running operation is at, said by its service; only a long operation says one. */
    fun at(step: String) {
        current.update { checkNotNull(it) { "A step said with no long operation running: $step" }.copy(step = step) }
    }
}
