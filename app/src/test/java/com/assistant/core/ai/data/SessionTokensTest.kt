package com.assistant.core.ai.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A session's cost is shown as a lower bound as long as one call went out with unknown usage.
 * The count must survive everything that rewrites the tokens, or the bound silently disappears.
 */
class SessionTokensTest {

    @Test
    fun theUnknownCallCount_survivesTheNextCallsTokens() {
        val tokens = SessionTokens()
            .addCallWithUnknownUsage()
            .addMessage(inputTokens = 100, cacheWriteTokens = 10, cacheReadTokens = 5, outputTokens = 50)

        assertEquals(1, tokens.callsWithUnknownUsage)
        assertEquals(100, tokens.totalUncachedInputTokens)
    }

    @Test
    fun theUnknownCallCount_survivesStorage() {
        val stored = SessionTokens(totalOutputTokens = 7).addCallWithUnknownUsage().addCallWithUnknownUsage()

        assertEquals(stored, SessionTokens.fromJson(stored.toJson()))
    }
}
