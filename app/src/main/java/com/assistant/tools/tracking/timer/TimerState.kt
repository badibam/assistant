package com.assistant.tools.tracking.timer

/**
 * État du timer actuel
 */
data class TimerState(
    val isActive: Boolean = false,
    val activityName: String = "",
    val startTime: Long = 0L,
    val toolInstanceId: String = "",
    val entryId: String = "", // ID de l'entrée créée immédiatement
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
     * Calcule la durée écoulée en secondes
     */
    fun getElapsedSeconds(): Int {
        return (getElapsedMillis() / 1000).toInt()
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
     * Retourne les données d'affichage pour que l'UI puisse formater avec ses strings
     */
    fun getDisplayData(): TimerDisplayData {
        return if (isActive) {
            TimerDisplayData(
                activityName = activityName,
                elapsedTime = formatElapsedTime(),
                isActive = true
            )
        } else {
            TimerDisplayData(
                activityName = "",
                elapsedTime = "",
                isActive = false
            )
        }
    }
    
    /**
     * @deprecated Use getDisplayData() instead to allow UI formatting with proper strings
     */
    @Deprecated("Use getDisplayData() for proper UI string formatting")
    fun formatDisplayText(): String {
        return if (isActive) {
            "$activityName : ${formatElapsedTime()}"
        } else {
            "En cours : -"
        }
    }
}

/**
 * Données d'affichage du timer pour l'UI
 */
data class TimerDisplayData(
    val activityName: String,
    val elapsedTime: String,
    val isActive: Boolean
)