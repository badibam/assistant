package com.assistant.tools.tracking.timer

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*

/**
 * Gestionnaire de timers par instance d'outil
 */
class TimerManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: TimerManager? = null

        fun getInstance(): TimerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimerManager().also { INSTANCE = it }
            }
        }
    }

    // Map des états de timer par toolInstanceId
    private val _timerStates = mutableMapOf<String, MutableState<TimerState>>()

    // Map des jobs de mise à jour par toolInstanceId
    private val updateJobs = mutableMapOf<String, Job>()
    
    /**
     * Get timer state for specific tool instance
     */
    fun getTimerState(toolInstanceId: String): State<TimerState> {
        return _timerStates.getOrPut(toolInstanceId) {
            mutableStateOf(TimerState())
        }
    }

    /**
     * Start timer for specific tool instance
     */
    fun startTimer(
        activityName: String,
        toolInstanceId: String,
        onPreviousTimerUpdate: ((entryId: String, seconds: Int) -> Unit)? = null,
        onCreateNewEntry: (activityName: String) -> String // Callback that creates entry and returns ID
    ) {
        // Stop previous timer for this instance if it exists
        stopTimer(toolInstanceId) { entryId, seconds ->
            // If callback provided, use it to update previous timer
            onPreviousTimerUpdate?.invoke(entryId, seconds)
        }

        // Create entry immediately with duration = 0
        val newEntryId = onCreateNewEntry(activityName)

        // Get or create timer state for this instance
        val timerState = _timerStates.getOrPut(toolInstanceId) {
            mutableStateOf(TimerState())
        }

        // Start new timer for this instance
        timerState.value = TimerState(
            isActive = true,
            activityName = activityName,
            startTime = System.currentTimeMillis(),
            toolInstanceId = toolInstanceId,
            entryId = newEntryId,
            updateTimestamp = System.currentTimeMillis()
        )

        // Start updating every second for this instance
        startUpdateLoop(toolInstanceId)
    }
    
    /**
     * Stop timer for specific tool instance
     */
    fun stopTimer(toolInstanceId: String, onResult: (entryId: String, seconds: Int) -> Unit) {
        val timerState = _timerStates[toolInstanceId] ?: return
        val currentState = timerState.value

        if (!currentState.isActive) {
            return
        }

        // Calculate duration
        val elapsedSeconds = currentState.getElapsedSeconds()
        val entryId = currentState.entryId

        // Stop timer for this instance
        timerState.value = TimerState()
        stopUpdateLoop(toolInstanceId)

        // Return result for update
        if (entryId.isNotEmpty()) {
            onResult(entryId, elapsedSeconds)
        }
    }

    /**
     * Stop current timer manually (for UI buttons) for specific tool instance
     */
    fun stopCurrentTimer(toolInstanceId: String, onUpdate: (entryId: String, seconds: Int) -> Unit) {
        stopTimer(toolInstanceId) { entryId, seconds ->
            onUpdate(entryId, seconds)
        }
    }

    /**
     * Check if specific activity is active in specific tool instance
     */
    fun isActivityActive(toolInstanceId: String, activityName: String): Boolean {
        val timerState = _timerStates[toolInstanceId]?.value ?: return false
        return timerState.isActive && timerState.activityName == activityName
    }
    
    /**
     * Start update loop for specific tool instance
     */
    private fun startUpdateLoop(toolInstanceId: String) {
        stopUpdateLoop(toolInstanceId) // Stop previous one if it exists

        val timerState = _timerStates[toolInstanceId] ?: return

        updateJobs[toolInstanceId] = CoroutineScope(Dispatchers.Main).launch {
            while (timerState.value.isActive) {
                delay(1000) // Wait for 1 second

                // Force recomposition with new timestamp
                val current = timerState.value
                if (current.isActive) {
                    timerState.value = current.copy(
                        updateTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    /**
     * Stop update loop for specific tool instance
     */
    private fun stopUpdateLoop(toolInstanceId: String) {
        updateJobs[toolInstanceId]?.cancel()
        updateJobs.remove(toolInstanceId)
    }
}