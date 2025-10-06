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
            // Replace simple escaped characters, but keep double escapes for debug
            // Strategy: protect double escapes, replace simple escapes, restore double escapes
            val readableMessage = message
                .replace("\\\\n", "\uE000")     // Protect \\n (double escape) with placeholder
                .replace("\\\\\"", "\uE001")    // Protect \\\" (double escape) with placeholder
                .replace("\\n", "\n")           // Replace \n (simple escape) with real newline
                .replace("\\\"", "\"")          // Replace \" (simple escape) with real quote
                .replace("\uE000", "\\\\n")     // Restore \\n (double escape)
                .replace("\uE001", "\\\\\"")    // Restore \\\" (double escape)

            when (level.uppercase()) {
                "VERBOSE" -> Log.v(tag, readableMessage, throwable)
                "DEBUG" -> Log.d(tag, readableMessage, throwable)
                "INFO" -> Log.i(tag, readableMessage, throwable)
                "WARN" -> Log.w(tag, readableMessage, throwable)
                "ERROR" -> Log.e(tag, readableMessage, throwable)
                else -> Log.d(tag, readableMessage, throwable)
            }
        } catch (e: Exception) {
            val readableMessage = message
                .replace("\\\\n", "\uE000")
                .replace("\\\\\"", "\uE001")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\uE000", "\\\\n")
                .replace("\uE001", "\\\\\"")
            println("LogManager fallback - $tag: $readableMessage")
            throwable?.let { println("Exception: ${it.message}") }
        }
    }
}