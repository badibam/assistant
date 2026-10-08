package app.treelune.core.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*

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
            "session_id" to sessionId
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
        // Calls of unknown cost: the costs shown only cover the others
        val callsWithUnknownCost = data["calls_with_unknown_cost"] as? Int ?: 0

        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                // Title
                UI.Text(
                    text = s.shared("ai_cost_breakdown_title"),
                    type = TextType.SUBTITLE,
                    fillMaxWidth = true
                )

                Spacer(modifier = Modifier.height(UI.Space.XS))

                // Token counts
                val totalUncachedInputTokens = data["total_uncached_input_tokens"] as? Int ?: 0
                val totalCacheWriteTokens = data["total_cache_write_tokens"] as? Int ?: 0
                val totalCacheReadTokens = data["total_cache_read_tokens"] as? Int ?: 0
                val totalOutputTokens = data["total_output_tokens"] as? Int ?: 0

                // Costs (no rounding for calculations, only for display)
                val inputCost = data["input_cost"] as? Double ?: 0.0
                val cacheWriteCost = data["cache_write_cost"] as? Double ?: 0.0
                val cacheReadCost = data["cache_read_cost"] as? Double ?: 0.0
                val outputCost = data["output_cost"] as? Double ?: 0.0
                val totalCost = data["total_cost"] as? Double ?: 0.0

                // Input row (uncached tokens, only if > 0)
                if (totalUncachedInputTokens > 0) {
                    TokenCostRow(
                        label = s.shared("ai_cost_input"),
                        tokens = totalUncachedInputTokens,
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

                Spacer(modifier = Modifier.height(UI.Space.XS))

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
                        text = formatCost(totalCost, s).let {
                            if (callsWithUnknownCost > 0) s.shared("ai_cost_at_least").format(it) else it
                        },
                        type = TextType.SUBTITLE
                    )
                }

                if (callsWithUnknownCost > 0) {
                    UI.Text(
                        text = s.shared("ai_cost_unknown_calls").format(callsWithUnknownCost),
                        type = TextType.CAPTION
                    )
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
    s: app.treelune.core.strings.StringsContext
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
private fun formatTokenCount(tokens: Int, s: app.treelune.core.strings.StringsContext): String {
    val formatted = String.format("%,d", tokens)
    return "$formatted ${s.shared("ai_cost_tokens")}"
}

/**
 * Format cost in USD with 3 decimal places (calculations stay unrounded)
 */
private fun formatCost(cost: Double, s: app.treelune.core.strings.StringsContext): String {
    val formatted = String.format("%.3f", cost)
    return "\$$formatted ${s.shared("ai_cost_currency_usd")}"
}
