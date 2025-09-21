package com.assistant.core.utils

import android.util.Log

object LogManager {

    fun schema(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Schema", message, level, throwable)
    }

    fun coordination(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Coordination", message, level, throwable)
    }

    fun tracking(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Tracking", message, level, throwable)
    }

    fun database(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Database", message, level, throwable)
    }

    fun ui(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("UI", message, level, throwable)
    }

    fun service(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("Service", message, level, throwable)
    }

    fun aiSession(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AISession", message, level, throwable)
    }

    fun aiPrompt(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIPrompt", message, level, throwable)
    }

    fun aiUI(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIUI", message, level, throwable)
    }

    fun aiService(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIService", message, level, throwable)
        safeLog("Service", message, level, throwable)
    }

    fun aiEnrichment(message: String, level: String = "DEBUG", throwable: Throwable? = null) {
        safeLog("AIEnrichment", message, level, throwable)
    }

    private fun safeLog(tag: String, message: String, level: String, throwable: Throwable?) {
        try {
            when (level.uppercase()) {
                "DEBUG" -> Log.d(tag, message, throwable)
                "INFO" -> Log.i(tag, message, throwable)
                "WARN" -> Log.w(tag, message, throwable)
                "ERROR" -> Log.e(tag, message, throwable)
                else -> Log.d(tag, message, throwable)
            }
        } catch (e: Exception) {
            println("LogManager fallback - $tag: $message")
            throwable?.let { println("Exception: ${it.message}") }
        }
    }
}