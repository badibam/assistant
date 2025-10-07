package com.assistant.core.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*

/**
 * Display cost breakdown for an AI session
 *
 * Shows:
 * - Total tokens (input, cache write, cache read, output)
 * - Cost breakdown by token type
 * - Total session cost in USD
 * - "Price unavailable" message if model pricing not found
 *
 * Usage:
 * ```
 * SessionCostDisplay(sessionId = "session-id-123")
 * ```
 */
@Composable
fun SessionCostDisplay(sessionId: String) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)
    val coordinator = remember { Coordinator(context) }

    var costData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Load cost data
    LaunchedEffect(sessionId) {
        isLoading = true
        val result = coordinator.processUserAction("ai_sessions.get_cost", mapOf(
            "sessionId" to sessionId
        ))

        if (result.isSuccess) {
            costData = result.data
        } else {
            errorMessage = result.error ?: s.shared("error_cost_calculation_failed")
        }
        isLoading = false
    }

    // Toast for errors
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) {
            UI.Toast(context, errorMessage, Duration.SHORT)
            errorMessage = ""
        }
    }

    // Loading state
    if (isLoading) {
        UI.Text(
            text = s.shared("ai_cost_loading"),
            type = TextType.CAPTION
        )
        return
    }

    // Display cost data
    costData?.let { data ->
        val priceAvailable = data["priceAvailable"] as? Boolean ?: false

        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                UI.Text(
                    text = s.shared("ai_cost_breakdown_title"),
                    type = TextType.SUBTITLE,
                    fillMaxWidth = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (!priceAvailable) {
                    // Price not available
                    UI.Text(
                        text = s.shared("ai_cost_unavailable"),
                        type = TextType.BODY
                    )
                } else {
                    // Token counts
                    val totalInputTokens = data["totalInputTokens"] as? Int ?: 0
                    val totalCacheWriteTokens = data["totalCacheWriteTokens"] as? Int ?: 0
                    val totalCacheReadTokens = data["totalCacheReadTokens"] as? Int ?: 0
                    val totalOutputTokens = data["totalOutputTokens"] as? Int ?: 0
                    val regularInputTokens = data["regularInputTokens"] as? Int ?: 0

                    // Costs
                    val inputCost = data["inputCost"] as? Double ?: 0.0
                    val cacheWriteCost = data["cacheWriteCost"] as? Double ?: 0.0
                    val cacheReadCost = data["cacheReadCost"] as? Double ?: 0.0
                    val outputCost = data["outputCost"] as? Double ?: 0.0
                    val totalCost = data["totalCost"] as? Double ?: 0.0

                    // Regular input row (only if > 0)
                    if (regularInputTokens > 0) {
                        TokenCostRow(
                            label = s.shared("ai_cost_input"),
                            tokens = regularInputTokens,
                            cost = inputCost,
                            s = s
                        )
                    }

                    // Cache write row (only if > 0)
                    if (totalCacheWriteTokens > 0) {
                        TokenCostRow(
                            label = s.shared("ai_cost_cache_write"),
                            tokens = totalCacheWriteTokens,
                            cost = cacheWriteCost,
                            s = s
                        )
                    }

                    // Cache read row (only if > 0)
                    if (totalCacheReadTokens > 0) {
                        TokenCostRow(
                            label = s.shared("ai_cost_cache_read"),
                            tokens = totalCacheReadTokens,
                            cost = cacheReadCost,
                            s = s
                        )
                    }

                    // Output row
                    if (totalOutputTokens > 0) {
                        TokenCostRow(
                            label = s.shared("ai_cost_output"),
                            tokens = totalOutputTokens,
                            cost = outputCost,
                            s = s
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Total cost row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        UI.Text(
                            text = s.shared("ai_cost_total"),
                            type = TextType.SUBTITLE
                        )
                        UI.Text(
                            text = formatCost(totalCost, s),
                            type = TextType.SUBTITLE
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single row displaying token count and cost
 */
@Composable
private fun TokenCostRow(
    label: String,
    tokens: Int,
    cost: Double,
    s: com.assistant.core.strings.StringsContext
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Label and token count
        Box(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = "$label: ${formatTokenCount(tokens, s)}",
                type = TextType.BODY
            )
        }

        // Cost
        Box {
            UI.Text(
                text = formatCost(cost, s),
                type = TextType.BODY
            )
        }
    }
}

/**
 * Format token count with thousands separator and "tokens" suffix
 */
private fun formatTokenCount(tokens: Int, s: com.assistant.core.strings.StringsContext): String {
    val formatted = String.format("%,d", tokens)
    return "$formatted ${s.shared("ai_cost_tokens")}"
}

/**
 * Format cost in USD with 6 decimal places
 */
private fun formatCost(cost: Double, s: com.assistant.core.strings.StringsContext): String {
    val formatted = String.format("%.6f", cost)
    return "\$$formatted ${s.shared("ai_cost_currency_usd")}"
}
