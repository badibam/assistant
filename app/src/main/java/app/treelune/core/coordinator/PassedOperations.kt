package app.treelune.core.coordinator

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The operations that passed through the coordinator and succeeded, as they pass: asked from
 * outside (a screen, the AI, an outside client, a scheduler), never one a service runs from inside
 * its own. What the Guide's steps wait for (docs/design/user-journey.md): « add an outing » is
 * done when tool_data.create passes on that tool, asked by the user.
 */
object PassedOperations {

    /**
     * An operation done: its action (`resource.operation`), who asked, its params and what it
     * returned.
     */
    data class Passed(val action: String, val source: Source, val params: Map<String, Any?>, val result: Map<String, Any?>)

    private val passed = MutableSharedFlow<Passed>(extraBufferCapacity = 64)
    val flow: SharedFlow<Passed> = passed

    fun pass(operation: Passed) {
        passed.tryEmit(operation)
    }
}
