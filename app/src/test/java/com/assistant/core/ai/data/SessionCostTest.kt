package com.assistant.core.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A session's cost is the sum of its calls, each at the prices stored with it. It is only a
 * lower bound as soon as one call's cost is unknown.
 */
class SessionCostTest {

    private fun call(
        input: Int = 0, cacheWrite: Int = 0, cacheRead: Int = 0, output: Int = 0,
        pricing: CallPricing? = CallPricing("m", 1.0, 2.0, 0.5, 4.0),
        usageUnknown: Boolean = false
    ) = CallUsage(input, cacheWrite, cacheRead, output, pricing, usageUnknown)

    /** Each call keeps its own prices: a later price change does not reach the earlier call. */
    @Test
    fun eachCall_isCostedAtItsOwnPrices() {
        val cost = SessionCost.of(listOf(
            call(input = 10, output = 1, pricing = CallPricing("old", 1.0, null, null, 4.0)),
            call(input = 10, output = 1, pricing = CallPricing("new", 3.0, null, null, 8.0))
        ))

        assertEquals(10 * 1.0 + 10 * 3.0, cost.inputCost, 0.0)
        assertEquals(4.0 + 8.0, cost.outputCost, 0.0)
        assertFalse(cost.isLowerBound)
    }

    /** OpenAI: no cache write price, and never any cache write token. */
    @Test
    fun aMissingPrice_withNoTokens_costsNothing() {
        val cost = SessionCost.of(listOf(
            call(input = 10, cacheRead = 100, output = 2, pricing = CallPricing("gpt", 1.0, null, 0.5, 4.0))
        ))

        assertEquals(10 + 50.0 + 8, cost.totalCost, 0.0)
        assertFalse(cost.isLowerBound)
    }

    @Test
    fun aMissingPrice_withTokens_makesTheTotalALowerBound() {
        val cost = SessionCost.of(listOf(
            call(input = 10, cacheWrite = 5, pricing = CallPricing("m", 1.0, null, null, null)),
            call(input = 1)
        ))

        assertEquals(1, cost.callsWithUnknownCost)
        assertEquals(10 + 1.0, cost.totalCost, 0.0)
        assertTrue(cost.isLowerBound)
    }

    /** Messages from before prices were stored have none. */
    @Test
    fun aCallWithoutPricing_isUnknown() {
        val cost = SessionCost.of(listOf(call(input = 10, output = 2, pricing = null)))

        assertEquals(12, cost.totalTokens)
        assertEquals(1, cost.callsWithUnknownCost)
    }

    /** A message with no tokens, such as a postText notice, is not a call that cost anything. */
    @Test
    fun aMessageWithoutTokens_isNotUnknown() {
        assertFalse(SessionCost.of(listOf(call(pricing = null))).isLowerBound)
    }

    @Test
    fun aCallCutAfterSending_countsAsUnknown() {
        val cost = SessionCost.of(listOf(call(input = 10), call(pricing = null, usageUnknown = true)))

        assertEquals(10.0, cost.totalCost, 0.0)
        assertEquals(1, cost.callsWithUnknownCost)
    }
}
