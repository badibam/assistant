package com.assistant.core.ai.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelPriceManagerTest {

    private val list = """
        {
          "gpt-5.4": {
            "input_cost_per_token": 2.5e-06,
            "output_cost_per_token": 1.5e-05,
            "cache_read_input_token_cost": 2.5e-07
          }
        }
    """.trimIndent()

    /** OpenAI has no cache write price: it must read as unknown, not as free. */
    @Test
    fun aMissingPrice_isUnknownNotZero() {
        val price = ModelPriceManager.parse(list).getValue("gpt-5.4")

        assertEquals(2.5e-06, price.inputCostPerToken!!, 0.0)
        assertEquals(2.5e-07, price.cacheReadCostPerToken!!, 0.0)
        assertNull(price.cacheWriteCostPerToken)
    }

    /**
     * gpt-5.4 as LiteLLM lists it: dearer above 272k input tokens, for the input, the cache read
     * and the output. The _batches price prices a mode the app does not use.
     */
    private val tiered = """
        {
          "gpt-5.4": {
            "input_cost_per_token": 2.5e-06,
            "output_cost_per_token": 1.5e-05,
            "cache_read_input_token_cost": 2.5e-07,
            "input_cost_per_token_above_272k_tokens": 5e-06,
            "output_cost_per_token_above_272k_tokens": 2.25e-05,
            "cache_read_input_token_cost_above_272k_tokens": 5e-07,
            "input_cost_per_token_above_272k_tokens_batches": 2.5e-06
          }
        }
    """.trimIndent()

    @Test
    fun aCallAboveATier_takesTheTiersPrices() {
        val pricing = ModelPriceManager.parse(tiered).getValue("gpt-5.4").forCall(inputTokens = 272_001)

        assertEquals(5e-06, pricing.inputPrice!!, 0.0)
        assertEquals(5e-07, pricing.cacheReadPrice!!, 0.0)
        assertEquals(2.25e-05, pricing.outputPrice!!, 0.0)
        assertNull(pricing.cacheWritePrice)
    }

    @Test
    fun aCallAtTheThreshold_keepsTheBasePrices() {
        val pricing = ModelPriceManager.parse(tiered).getValue("gpt-5.4").forCall(inputTokens = 272_000)

        assertEquals(2.5e-06, pricing.inputPrice!!, 0.0)
        assertEquals(1.5e-05, pricing.outputPrice!!, 0.0)
    }

    /** Started without network, in the background or not: the copy kept on the phone answers. */
    @Test
    fun theCopyOnThePhone_givesPricesWithoutDownloading() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("prices").toFile()
        File(dir, "model_prices.json").writeText(list) // fresh: no download is due
        ModelPriceManager.forgetForTest()

        val price = ModelPriceManager.getModelPrice(dir, "gpt-5.4")

        assertEquals(1.5e-05, price!!.outputCostPerToken!!, 0.0)
        dir.deleteRecursively()
    }
}
