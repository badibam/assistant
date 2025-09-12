package com.assistant.core.utils

import android.content.Context
import com.assistant.core.services.AppConfigService
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * Centralized locale management utilities
 * Provides consistent app locale access with AppConfig integration
 */
object LocaleUtils {
    
    /**
     * Get app's configured locale
     * Uses AppConfig locale override if available, falls back to system locale
     * This is the centralized method that should be used throughout the app
     */
    fun getAppLocale(context: Context): Locale {
        return try {
            val appConfigService = AppConfigService(context)
            val localeOverride = runBlocking { appConfigService.getLocaleOverride() }
            
            localeOverride?.let { localeString ->
                try {
                    Locale.forLanguageTag(localeString)
                } catch (e: Exception) {
                    // Invalid locale string, fallback to system
                    Locale.getDefault()
                }
            } ?: Locale.getDefault()
            
        } catch (e: Exception) {
            // Fallback to system locale if config unavailable
            Locale.getDefault()
        }
    }
    
    /**
     * Set app locale override
     * @param context Application context
     * @param locale Locale to set, or null to use system default
     */
    suspend fun setAppLocale(context: Context, locale: Locale?) {
        val appConfigService = AppConfigService(context)
        val localeTag = locale?.toLanguageTag()
        appConfigService.setLocaleOverride(localeTag)
    }
    
    /**
     * Get available locales for the app
     * Currently returns common locales, could be extended to support user-defined ones
     */
    fun getAvailableLocales(): List<Locale> {
        return listOf(
            Locale.ENGLISH,
            Locale.FRENCH,
            Locale("es"), // Spanish
            Locale("de"), // German
            Locale("it"), // Italian
            Locale("pt"), // Portuguese
        )
    }
    
    /**
     * Get display name for a locale in current app locale
     */
    fun getLocaleDisplayName(locale: Locale, context: Context): String {
        val appLocale = getAppLocale(context)
        return locale.getDisplayName(appLocale)
    }
}