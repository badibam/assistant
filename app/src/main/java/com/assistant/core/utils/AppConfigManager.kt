package com.assistant.core.utils

import android.content.Context
import com.assistant.core.config.TimeConfig
import com.assistant.core.config.AILimitsConfig
import com.assistant.core.services.AppConfigService
import kotlinx.coroutines.runBlocking

/**
 * Singleton manager for app configuration with caching
 * Provides synchronous access to frequently-used config values
 */
object AppConfigManager {

    @Volatile
    private var cachedDayStartHour: Int? = null

    @Volatile
    private var cachedWeekStartDay: String? = null

    @Volatile
    private var cachedAILimits: AILimitsConfig? = null

    @Volatile
    private var isInitialized = false

    /**
     * Initialize cache from database
     * Must be called at app startup
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            val service = AppConfigService(context)
            runBlocking {
                cachedDayStartHour = service.getDayStartHour()
                cachedWeekStartDay = service.getWeekStartDay()
                cachedAILimits = service.getAILimits()
            }
            isInitialized = true
            LogManager.service("AppConfigManager initialized: dayStartHour=$cachedDayStartHour, weekStartDay=$cachedWeekStartDay, aiLimits=$cachedAILimits")
        } catch (e: Exception) {
            LogManager.service("Failed to initialize AppConfigManager: ${e.message}", "ERROR", e)
            // Set defaults as fallback
            cachedDayStartHour = 4
            cachedWeekStartDay = "monday"
            cachedAILimits = AILimitsConfig() // Use data class defaults
            isInitialized = true
        }
    }

    /**
     * Get day start hour (cached)
     * Throws IllegalStateException if not initialized
     */
    fun getDayStartHour(): Int {
        check(isInitialized) { "AppConfigManager not initialized. Call initialize(context) at app startup." }
        return cachedDayStartHour!!
    }

    /**
     * Get week start day (cached)
     * Throws IllegalStateException if not initialized
     */
    fun getWeekStartDay(): String {
        check(isInitialized) { "AppConfigManager not initialized. Call initialize(context) at app startup." }
        return cachedWeekStartDay!!
    }

    /**
     * Get time configuration (cached)
     * Throws IllegalStateException if not initialized
     */
    fun getTimeConfig(): TimeConfig {
        check(isInitialized) { "AppConfigManager not initialized. Call initialize(context) at app startup." }
        return TimeConfig(
            dayStartHour = cachedDayStartHour!!,
            weekStartDay = cachedWeekStartDay!!
        )
    }

    /**
     * Get AI limits configuration (cached)
     * Throws IllegalStateException if not initialized
     */
    fun getAILimits(): AILimitsConfig {
        check(isInitialized) { "AppConfigManager not initialized. Call initialize(context) at app startup." }
        return cachedAILimits!!
    }

    /**
     * Refresh cache from database
     * Call after config changes
     */
    fun refresh(context: Context) {
        isInitialized = false
        initialize(context)
    }

    /**
     * Clear cache
     */
    fun clear() {
        cachedDayStartHour = null
        cachedWeekStartDay = null
        cachedAILimits = null
        isInitialized = false
    }
}