package com.assistant.core.ai.prompts

import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.prompts.PromptManager.AlwaysSendChoice
import com.assistant.core.ai.prompts.PromptManager.AlwaysSendOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What becomes of the tools sent always (docs/design/always-send.md): within the threshold they
 * go; above it a CHAT asks once and follows the answer, the others get the list.
 */
class AlwaysSendOutcomeTest {

    private fun outcome(chars: Int, type: SessionType?, choice: AlwaysSendChoice? = null) =
        PromptManager.alwaysSendOutcome(chars, threshold = 15_000, sessionType = type, choice = choice)

    @Test
    fun `within the threshold they go, whatever the session`() {
        assertEquals(AlwaysSendOutcome.SEND, outcome(15_000, SessionType.CHAT))
        assertEquals(AlwaysSendOutcome.SEND, outcome(800, SessionType.AUTOMATION))
        assertEquals(AlwaysSendOutcome.SEND, outcome(800, null))
    }

    @Test
    fun `above it a chat with no choice yet asks`() {
        assertEquals(AlwaysSendOutcome.ASK, outcome(15_001, SessionType.CHAT))
    }

    @Test
    fun `above it a chat follows the choice made`() {
        assertEquals(AlwaysSendOutcome.SEND, outcome(40_000, SessionType.CHAT, AlwaysSendChoice.ACCEPTED))
        assertEquals(AlwaysSendOutcome.LIST, outcome(40_000, SessionType.CHAT, AlwaysSendChoice.REFUSED))
    }

    @Test
    fun `above it an automation and an outside AI get the list`() {
        assertEquals(AlwaysSendOutcome.LIST, outcome(40_000, SessionType.AUTOMATION))
        assertEquals(AlwaysSendOutcome.LIST, outcome(40_000, null))
    }
}
