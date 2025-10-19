package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.ai.domain.AIState
import com.assistant.core.ai.data.SessionType
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager

/**
 * Provider verification utility for AI sessions.
 *
 * Verifies that the required AI provider exists and is properly configured
 * before attempting to build prompts and call the provider.
 *
 * Architecture:
 * - CHAT sessions: Use active provider (from app config)
 * - AUTOMATION sessions: Use provider specified in automation config
 */
object ProviderVerifier {

    /**
     * Result of provider verification.
     */
    data class VerificationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Verify that the provider required for this session exists and is configured.
     *
     * @param state Current AI state containing session info
     * @param context Android context for coordinator access
     * @return VerificationResult with validation status and error message if invalid
     */
    suspend fun verifyProvider(state: AIState, context: Context): VerificationResult {
        val coordinator = Coordinator(context)
        val s = Strings.`for`(context = context)
        val sessionId = state.sessionId

        if (sessionId == null) {
            LogManager.aiService("verifyProvider: No session ID in state", "ERROR")
            return VerificationResult(false, "No session ID")
        }

        try {
            // Get session to retrieve providerId
            val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf(
                "sessionId" to sessionId
            ))

            if (!sessionResult.isSuccess) {
                LogManager.aiService("verifyProvider: Failed to load session: ${sessionResult.error}", "ERROR")
                return VerificationResult(false, s.shared("ai_error_session_not_found"))
            }

            val sessionData = sessionResult.data?.get("session") as? Map<*, *>
            if (sessionData == null) {
                LogManager.aiService("verifyProvider: No session data found", "ERROR")
                return VerificationResult(false, s.shared("ai_error_session_not_found"))
            }

            // Determine which provider to verify based on session type
            val providerIdToVerify: String = when (state.sessionType) {
                SessionType.CHAT -> {
                    // For CHAT: use current active provider (may differ from session's initial provider)
                    val activeResult = coordinator.processUserAction("ai_provider_config.get_active")

                    if (!activeResult.isSuccess) {
                        LogManager.aiService("verifyProvider: Failed to get active provider", "ERROR")
                        return VerificationResult(false, s.shared("ai_error_no_provider_configured"))
                    }

                    val hasActiveProvider = activeResult.data?.get("hasActiveProvider") as? Boolean ?: false
                    if (!hasActiveProvider) {
                        LogManager.aiService("verifyProvider: No active provider configured", "WARN")
                        return VerificationResult(false, s.shared("ai_error_no_provider_configured"))
                    }

                    val activeProviderId = activeResult.data?.get("activeProviderId") as? String
                    if (activeProviderId.isNullOrEmpty()) {
                        LogManager.aiService("verifyProvider: Active provider ID is empty", "ERROR")
                        return VerificationResult(false, s.shared("ai_error_no_provider_configured"))
                    }

                    LogManager.aiService("verifyProvider: CHAT session will use active provider '$activeProviderId'", "DEBUG")
                    activeProviderId
                }

                SessionType.AUTOMATION -> {
                    // For AUTOMATION: use provider configured in automation
                    val providerId = sessionData["providerId"] as? String
                    if (providerId.isNullOrEmpty()) {
                        LogManager.aiService("verifyProvider: AUTOMATION session has no providerId", "ERROR")
                        return VerificationResult(false, s.shared("ai_error_no_provider_configured"))
                    }

                    LogManager.aiService("verifyProvider: AUTOMATION session uses provider '$providerId'", "DEBUG")
                    providerId
                }

                SessionType.SEED -> {
                    // SEED sessions should never be active
                    LogManager.aiService("verifyProvider: SEED session should not be active", "ERROR")
                    return VerificationResult(false, "SEED session cannot be executed")
                }

                null -> {
                    // No session type
                    LogManager.aiService("verifyProvider: Session has no type", "ERROR")
                    return VerificationResult(false, s.shared("ai_error_no_provider_configured"))
                }
            }

            // Verify provider is configured
            val providerResult = coordinator.processUserAction("ai_provider_config.get", mapOf(
                "providerId" to providerIdToVerify
            ))

            if (!providerResult.isSuccess) {
                LogManager.aiService("verifyProvider: Provider '$providerIdToVerify' not found in DB", "ERROR")
                return VerificationResult(false, s.shared("ai_error_provider_not_found").format(providerIdToVerify))
            }

            val isConfigured = providerResult.data?.get("isConfigured") as? Boolean ?: false
            if (!isConfigured) {
                LogManager.aiService("verifyProvider: Provider '$providerIdToVerify' not configured", "ERROR")
                return VerificationResult(false, s.shared("ai_error_provider_not_configured").format(providerIdToVerify))
            }

            // Verify provider exists in registry
            val providerRegistry = AIProviderRegistry(context)
            val provider = providerRegistry.getProvider(providerIdToVerify)
            if (provider == null) {
                LogManager.aiService("verifyProvider: Provider '$providerIdToVerify' not found in registry", "ERROR")
                return VerificationResult(false, s.shared("ai_error_provider_not_found").format(providerIdToVerify))
            }

            LogManager.aiService("verifyProvider: Provider '$providerIdToVerify' verified successfully", "DEBUG")
            return VerificationResult(true)

        } catch (e: Exception) {
            LogManager.aiService("verifyProvider: Exception during verification: ${e.message}", "ERROR", e)
            return VerificationResult(false, s.shared("ai_error_ai_query_failed").format(e.message ?: ""))
        }
    }
}
