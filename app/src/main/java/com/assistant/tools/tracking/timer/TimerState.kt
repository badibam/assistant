package com.assistant.tools.tracking.timer

/**
 * Current timer state
 */
data class TimerState(
    val isActive: Boolean = false,
    val activityName: String = "",
    val startTime: Long = 0L,
    val toolInstanceId: String = "",
    val entryId: String = "", // ID of the immediately created entry
    val updateTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Calculate elapsed duration in milliseconds
     */
    fun getElapsedMillis(): Long {
        return if (isActive) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }
    }
    
    /**
     * Calculate elapsed duration in minutes
     */
    fun getElapsedMinutes(): Int {
        return (getElapsedMillis() / 60000).toInt()
    }
    
    /**
     * Calculate elapsed duration in seconds
     */
    fun getElapsedSeconds(): Int {
        return (getElapsedMillis() / 1000).toInt()
    }
    
    /**
     * Format elapsed time for display (ex: "2m 34s")
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
     * Returns display data so UI can format with its strings
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
 * Timer display data for UI
 */
data class TimerDisplayData(
    val activityName: String,
    val elapsedTime: String,
    val isActive: Boolean
)