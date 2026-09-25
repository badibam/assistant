package com.assistant.core.ai.data

/**
 * What one message says about the cost of an AI call: its tokens and prices for an AI message,
 * or only the mark of a call that went out and whose usage never came back.
 */
data class CallUsage(
    val inputTokens: Int,
    val cacheWriteTokens: Int,
    val cacheReadTokens: Int,
    val outputTokens: Int,
    val pricing: CallPricing?,
    val usageUnknown: Boolean
)

/**
 * A session's tokens and cost, summed from its calls when read, never stored.
 *
 * Costs only count what is known. A call is of unknown cost when it went out without its usage
 * coming back, or when a category it has tokens in has no price: then the costs are a lower bound.
 */
data class SessionCost(
    val uncachedInputTokens: Int,
    val cacheWriteTokens: Int,
    val cacheReadTokens: Int,
    val outputTokens: Int,
    val inputCost: Double,
    val cacheWriteCost: Double,
    val cacheReadCost: Double,
    val outputCost: Double,
    val callsWithUnknownCost: Int
) {
    val totalTokens: Int get() = uncachedInputTokens + cacheWriteTokens + cacheReadTokens + outputTokens
    val totalCost: Double get() = inputCost + cacheWriteCost + cacheReadCost + outputCost
    val isLowerBound: Boolean get() = callsWithUnknownCost > 0

    companion object {
        fun of(calls: List<CallUsage>): SessionCost {
            var cost = SessionCost(0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0)
            for (call in calls) {
                if (call.usageUnknown) {
                    cost = cost.copy(callsWithUnknownCost = cost.callsWithUnknownCost + 1)
                    continue
                }
                val pricing = call.pricing
                // Tokens with no price make the call unknown; no tokens cost nothing at any price
                fun part(tokens: Int, price: Double?): Double? =
                    if (tokens == 0) 0.0 else price?.let { tokens * it }
                val input = part(call.inputTokens, pricing?.inputPrice)
                val cacheWrite = part(call.cacheWriteTokens, pricing?.cacheWritePrice)
                val cacheRead = part(call.cacheReadTokens, pricing?.cacheReadPrice)
                val output = part(call.outputTokens, pricing?.outputPrice)
                val unknown = listOf(input, cacheWrite, cacheRead, output).any { it == null }

                cost = cost.copy(
                    uncachedInputTokens = cost.uncachedInputTokens + call.inputTokens,
                    cacheWriteTokens = cost.cacheWriteTokens + call.cacheWriteTokens,
                    cacheReadTokens = cost.cacheReadTokens + call.cacheReadTokens,
                    outputTokens = cost.outputTokens + call.outputTokens,
                    inputCost = cost.inputCost + (input ?: 0.0),
                    cacheWriteCost = cost.cacheWriteCost + (cacheWrite ?: 0.0),
                    cacheReadCost = cost.cacheReadCost + (cacheRead ?: 0.0),
                    outputCost = cost.outputCost + (output ?: 0.0),
                    callsWithUnknownCost = cost.callsWithUnknownCost + if (unknown) 1 else 0
                )
            }
            return cost
        }
    }
}
