package com.assistant.core.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where an install of the demo stands, for the screen waiting on it: the step and, for a step
 * made of many writes, how many are done out of how many. Null when no install runs.
 */
object DemoProgress {

    enum class Phase { ZONES, TOOLS, ENTRIES, GOALS, AUTOMATIONS }

    data class Step(val phase: Phase, val done: Int = 0, val total: Int = 0)

    private val current = MutableStateFlow<Step?>(null)
    val step: StateFlow<Step?> = current

    fun at(phase: Phase, done: Int = 0, total: Int = 0) { current.value = Step(phase, done, total) }

    fun end() { current.value = null }
}
