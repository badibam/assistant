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
    fun startTimer(
        activityName: String, 
        toolInstanceId: String, 
        onPreviousTimerUpdate: ((entryId: String, seconds: Int) -> Unit)? = null,
        onCreateNewEntry: (activityName: String) -> String // Callback qui crée l'entrée et retourne l'ID
    ) {
        // Arrêter le timer précédent s'il existe
        stopTimer { entryId, seconds ->
            // Si un callback est fourni, l'utiliser pour mettre à jour le timer précédent
            onPreviousTimerUpdate?.invoke(entryId, seconds)
        }
        
        // Créer immédiatement l'entrée avec durée = 0
        val newEntryId = onCreateNewEntry(activityName)
        
        // Démarrer le nouveau timer
        _timerState.value = TimerState(
            isActive = true,
            activityName = activityName,
            startTime = System.currentTimeMillis(),
            toolInstanceId = toolInstanceId,
            entryId = newEntryId,
            updateTimestamp = System.currentTimeMillis()
        )
        
        // Démarrer la mise à jour toutes les secondes
        startUpdateLoop()
    }
    
    /**
     * Arrête le timer actuel et retourne l'ID de l'entrée avec la durée en secondes
     */
    fun stopTimer(onResult: (entryId: String, seconds: Int) -> Unit) {
        val currentState = _timerState.value
        if (!currentState.isActive) {
            return
        }
        
        // Calculer la durée
        val elapsedSeconds = currentState.getElapsedSeconds()
        val entryId = currentState.entryId
        
        // Arrêter le timer
        _timerState.value = TimerState()
        stopUpdateLoop()
        
        // Retourner le résultat pour mise à jour
        if (entryId.isNotEmpty()) {
            onResult(entryId, elapsedSeconds)
        }
    }
    
    /**
     * Arrête le timer actuel manuellement (pour boutons d'interface)
     */
    fun stopCurrentTimer(onUpdate: (entryId: String, seconds: Int) -> Unit) {
        stopTimer { entryId, seconds ->
            onUpdate(entryId, seconds)
        }
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