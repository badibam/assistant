package com.assistant.core.ai.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The app's copy of the providers' facts, as the config screen and the requests read it: what a
 * model offers of its reasoning comes from there, and a model no fact covers offers nothing.
 */
class ProviderFactsTest {

    private val facts = ProviderFacts.parse(File("src/main/assets/facts.json").readText(Charsets.UTF_8))

    /** Sonnet 5.5 turns thinking off by between_tools, and only up to high. */
    @Test
    fun sonnet55_turnsThinkingOffByBetweenTools() {
        assertEquals(ThinkingOff("between_tools", listOf("low", "medium", "high")), facts.thinkingOff("anthropic", "claude-sonnet-5-5"))
    }

    /** Opus 5.5 cannot: its fact says so, and the screen offers no thinking off. */
    @Test
    fun opus55_cannotTurnThinkingOff() {
        assertEquals(ThinkingOff(null, null), facts.thinkingOff("anthropic", "claude-opus-5-5"))
        assertNull(ReasoningSettings.of(facts, "anthropic", "claude-opus-5-5", emptyList(), effortRequired = false))
    }

    /** An OpenAI model's levels are facts, "none" among them where it has it. */
    @Test
    fun openAIEfforts_comeFromTheFacts() {
        val reasoning = ReasoningSettings.of(facts, "openai", "gpt-5.5", null, effortRequired = false)!!
        assertEquals(listOf("none", "low", "medium", "high", "xhigh"), reasoning.efforts)
        assertNull(reasoning.thinkingOff)
    }

    /** A model no fact covers has no reasoning setting. */
    @Test
    fun anUnknownModel_offersNothing() {
        assertNull(facts.effortLevels("openai", "gpt-unknown"))
        assertNull(facts.thinkingOff("anthropic", "claude-unknown"))
        assertNull(ReasoningSettings.of(facts, "openai", "gpt-unknown", null, effortRequired = false))
    }

    /**
     * Whether a model reads images: gpt-5.5 is named by two input facts, its images' and the PDF
     * one, and only the first says anything of images.
     */
    @Test
    fun imageInput_isReadFromTheFactSayingIt() {
        assertEquals(true, facts.readsImages("openai", "gpt-5.5"))
        assertNull(facts.readsImages("openai", "gpt-unknown"))
        assertNull(facts.readsImages("anthropic", "claude-opus-5-5"))
    }

    /** What the model list stored wins over the facts; nothing known is null, and refused. */
    @Test
    fun imageInput_storedByTheList_thenTheFacts() {
        val s: (String) -> String = { it }
        assertEquals(false, ImageInput.readsImages(org.json.JSONObject("""{"model": "gpt-5.5", "reads_images": false}"""), facts, "openai"))
        assertEquals(true, ImageInput.readsImages(org.json.JSONObject("""{"model": "gpt-5.5"}"""), facts, "openai"))
        assertNull(ImageInput.readsImages(org.json.JSONObject("""{"model": "local-model"}"""), facts, null))
        assertNull(ImageInput.refusal(0, null, s))
        assertNull(ImageInput.refusal(3, true, s))
        assertEquals("ai_image_error_model_does_not_read", ImageInput.refusal(1, false, s))
        assertEquals("ai_image_error_model_unknown", ImageInput.refusal(1, null, s))
    }
}
