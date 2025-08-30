package com.assistant.tools.tracking.timer

/**
 * État du timer actuel
 */
data class TimerState(
    val isActive: Boolean = false,
    val activityName: String = "",
    val startTime: Long = 0L,
    val toolInstanceId: String = "",
    val updateTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Calcule la durée écoulée en millisecondes
     */
    fun getElapsedMillis(): Long {
        return if (isActive) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }
    }
    
    /**
     * Calcule la durée écoulée en minutes
     */
    fun getElapsedMinutes(): Int {
        return (getElapsedMillis() / 60000).toInt()
    }
    
    /**
     * Formate le temps écoulé pour affichage (ex: "2m 34s")
     */
    fun formatElapsedTime(): String {
        val totalSeconds = (getElapsedMillis() / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        
        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
    
    /**
     * Formate l'affichage complet pour la barre (ex: "Lecture : 2m 34s")
     */
    fun formatDisplayText(): String {
        return if (isActive) {
            "$activityName : ${formatElapsedTime()}"
        } else {
            "En cours : -"
        }
    }
}