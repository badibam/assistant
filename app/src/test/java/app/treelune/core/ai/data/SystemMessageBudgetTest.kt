package app.treelune.core.ai.data

import app.treelune.core.ai.providers.toPromptText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMessageBudgetTest {

    private val mark: (Int) -> String = { "[cut $it]" }

    private fun result(details: String, data: Map<String, Any>? = null, error: String? = null) = CommandResult(
        command = "imports.apply",
        status = if (error != null) CommandStatus.FAILED else CommandStatus.SUCCESS,
        details = details, data = data, error = error, isActionCommand = true
    )

    @Test
    fun `a message within the limit goes as it is`() {
        val message = SystemMessage(SystemMessageType.ACTIONS_EXECUTED, listOf(result("Import", mapOf("created" to 3))), "1 action")
        assertSame(message, message.withinChars(1000, mark))
    }

    @Test
    fun `a result past the limit is cut where the room ends, and says how much was not sent`() {
        val huge = "x".repeat(100_000)
        val message = SystemMessage(SystemMessageType.ACTIONS_EXECUTED, listOf(result("Import", error = huge)), "1 action failed")
        val cut = message.withinChars(500, mark)
        val text = cut.toPromptText()
        assertTrue(text.length < 600)
        assertTrue(text.startsWith("1 action failed"))
        val error = cut.commandResults.single().error!!
        val kept = error.substringBefore("[cut")
        assertEquals("[cut ${huge.length - kept.length}]", error.removePrefix(kept))
    }

    @Test
    fun `the results before the one cut stay whole`() {
        val first = result("Création de la zone", mapOf("id" to "z1"))
        val message = SystemMessage(SystemMessageType.ACTIONS_EXECUTED, listOf(first, result("Import", mapOf("refused" to "y".repeat(50_000)))), "2 actions")
        val cut = message.withinChars(300, mark)
        assertEquals(first, cut.commandResults[0])
        assertTrue((cut.commandResults[1].data!!["cut"] as String).contains("[cut "))
    }
}
