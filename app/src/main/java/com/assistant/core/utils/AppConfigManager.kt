package com.assistant.core.utils

import android.content.Context
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
            }
            isInitialized = true
            LogManager.service("AppConfigManager initialized: dayStartHour=$cachedDayStartHour, weekStartDay=$cachedWeekStartDay")
        } catch (e: Exception) {
            LogManager.service("Failed to initialize AppConfigManager: ${e.message}", "ERROR", e)
            // Set defaults as fallback
            cachedDayStartHour = 4
            cachedWeekStartDay = "monday"
            isInitialized = true
        }
    }

    /**
     * Get day start hour (cached)
     * Returns default if not initialized
     */
    fun getDayStartHour(context: Context): Int {
        if (!isInitialized) initialize(context)
        return cachedDayStartHour ?: 4
    }

    /**
     * Get week start day (cached)
     * Returns default if not initialized
     */
    fun getWeekStartDay(context: Context): String {
        if (!isInitialized) initialize(context)
        return cachedWeekStartDay ?: "monday"
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
        isInitialized = false
    }
}