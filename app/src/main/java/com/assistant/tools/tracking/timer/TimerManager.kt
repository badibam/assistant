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
     * Démarre un timer pour une activité
     */
    fun startTimer(activityName: String, toolInstanceId: String, onPreviousTimerResult: ((minutes: Int, activityName: String) -> Unit)? = null) {
        // Arrêter le timer précédent s'il existe
        stopTimer { minutes, previousActivityName ->
            // Si un callback est fourni, l'utiliser pour sauvegarder le timer précédent
            onPreviousTimerResult?.invoke(minutes, previousActivityName)
        }
        
        // Démarrer le nouveau timer
        _timerState.value = TimerState(
            isActive = true,
            activityName = activityName,
            startTime = System.currentTimeMillis(),
            toolInstanceId = toolInstanceId,
            updateTimestamp = System.currentTimeMillis()
        )
        
        // Démarrer la mise à jour toutes les secondes
        startUpdateLoop()
    }
    
    /**
     * Arrête le timer actuel et retourne la durée en minutes
     */
    fun stopTimer(onResult: (minutes: Int, activityName: String) -> Unit) {
        val currentState = _timerState.value
        if (!currentState.isActive) {
            return
        }
        
        // Calculer la durée
        val elapsedMinutes = currentState.getElapsedMinutes()
        val activityName = currentState.activityName
        
        // Arrêter le timer
        _timerState.value = TimerState()
        stopUpdateLoop()
        
        // Retourner le résultat
        onResult(elapsedMinutes, activityName)
    }
    
    /**
     * Vérifie si une activité spécifique est active
     */
    fun isActivityActive(activityName: String): Boolean {
        return _timerState.value.isActive && _timerState.value.activityName == activityName
    }
    
    /**
     * Démarre la boucle de mise à jour toutes les secondes
     */
    private fun startUpdateLoop() {
        stopUpdateLoop() // Arrêter la précédente si elle existe
        
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (_timerState.value.isActive) {
                delay(1000) // Attendre 1 seconde
                
                // Force la recomposition avec un nouveau timestamp
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
     * Arrête la boucle de mise à jour
     */
    private fun stopUpdateLoop() {
        updateJob?.cancel()
        updateJob = null
    }
}