package app.treelune.core.ai.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The message an AI response carries: its one JSON object, taken out of the sentence or the code
 * block around it, and never guessed among several.
 */
class ResponseEnvelopeTest {

    private val message = """{"pre_text": "Je crée la zone.", "action_commands": [{"type": "CREATE_ZONE", "params": {"name": "A"}}]}"""

    @Test
    fun `a response that is the object alone has nothing outside`() {
        assertEquals(ResponseEnvelope.Split(message, ""), ResponseEnvelope.split(message))
    }

    @Test
    fun `a code fence around the object is not text outside`() {
        assertEquals(ResponseEnvelope.Split(message, ""), ResponseEnvelope.split("```json\n$message\n```"))
    }

    @Test
    fun `a sentence before the object is set apart`() {
        val split = ResponseEnvelope.split("Je commence par créer la zone.\n\n```json\n$message\n```")
        assertEquals(message, split?.json)
        assertEquals("Je commence par créer la zone.", split?.outside)
    }

    @Test
    fun `a brace in the prose opens no object`() {
        val split = ResponseEnvelope.split("Ma réponse commence par `{` cette fois.\n$message")
        assertEquals(message, split?.json)
        assertEquals("Ma réponse commence par `{` cette fois.", split?.outside)
    }

    @Test
    fun `braces inside strings belong to the object`() {
        val withBraces = """{"pre_text": "Une accolade } et une autre {", "keep_control": true}"""
        assertEquals(withBraces, ResponseEnvelope.split("Voici : $withBraces")?.json)
    }

    @Test
    fun `no object is no message`() {
        assertNull(ResponseEnvelope.split("Je réfléchis encore."))
    }

    @Test
    fun `two objects are no message, since nothing says which one is`() {
        assertNull(ResponseEnvelope.split("$message\n$message"))
    }

    @Test
    fun `an object that does not close is no message`() {
        assertNull(ResponseEnvelope.split("""{"pre_text": "coupé"""))
    }
}
