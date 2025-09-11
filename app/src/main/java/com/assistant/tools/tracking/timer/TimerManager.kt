package com.assistant.tools.tracking.timer

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*

/**
 * Gestionnaire global du timer - Singleton
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
    
    private val _timerState = mutableStateOf(TimerState())
    val timerState: State<TimerState> = _timerState
    
    private var updateJob: Job? = null
    
    /**
     * Start timer for activity
     */
    fun startTimer(
        activityName: String, 
        toolInstanceId: String, 
        onPreviousTimerUpdate: ((entryId: String, seconds: Int) -> Unit)? = null,
        onCreateNewEntry: (activityName: String) -> String // Callback that creates entry and returns ID
    ) {
        // Stop previous timer if it exists
        stopTimer { entryId, seconds ->
            // If callback provided, use it to update previous timer
            onPreviousTimerUpdate?.invoke(entryId, seconds)
        }
        
        // Create entry immediately with duration = 0
        val newEntryId = onCreateNewEntry(activityName)
        
        // Start new timer
        _timerState.value = TimerState(
            isActive = true,
            activityName = activityName,
            startTime = System.currentTimeMillis(),
            toolInstanceId = toolInstanceId,
            entryId = newEntryId,
            updateTimestamp = System.currentTimeMillis()
        )
        
        // Start updating every second
        startUpdateLoop()
    }
    
    /**
     * Stop current timer and return entry ID with duration in seconds
     */
    fun stopTimer(onResult: (entryId: String, seconds: Int) -> Unit) {
        val currentState = _timerState.value
        if (!currentState.isActive) {
            return
        }
        
        // Calculate duration
        val elapsedSeconds = currentState.getElapsedSeconds()
        val entryId = currentState.entryId
        
        // Stop timer
        _timerState.value = TimerState()
        stopUpdateLoop()
        
        // Return result for update
        if (entryId.isNotEmpty()) {
            onResult(entryId, elapsedSeconds)
        }
    }
    
    /**
     * Stop current timer manually (for UI buttons)
     */
    fun stopCurrentTimer(onUpdate: (entryId: String, seconds: Int) -> Unit) {
        stopTimer { entryId, seconds ->
            onUpdate(entryId, seconds)
        }
    }
    
    /**
     * Check if specific activity is active
     */
    fun isActivityActive(activityName: String): Boolean {
        return _timerState.value.isActive && _timerState.value.activityName == activityName
    }
    
    /**
     * Start update loop every second
     */
    private fun startUpdateLoop() {
        stopUpdateLoop() // Stop previous one if it exists
        
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (_timerState.value.isActive) {
                delay(1000) // Wait for 1 second
                
                // Force recomposition with new timesatamp
                val current = _timerState.value
                if (current.isActive) {
                    _timerState.value = current.copy(
                        updateTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }
    
    /**
     * Stop update loop
     */
    private fun stopUpdateLoop() {
        updateJob?.cancel()
        updateJob = null
    }
}