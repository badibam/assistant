package app.treelune.core.ai.providers

import app.treelune.core.ai.data.SystemMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers a system message on its way from the database to the model: read back from its
 * stored JSON, then written as the text every provider sends.
 *
 * The replay of the L1 prompt created a zone, then a tool, and each time the model asked for
 * the id it had just been given. The id was stored with the result; the prompt was built from
 * a second parser that did not read is_action_command back, and an action's data is only
 * written for an action.
 */
class SystemMessagePromptTextTest {

    /** The message as the database held it after CREATE_ZONE in that replay. */
    private val storedAfterCreateZone = """
        {
          "type": "ACTIONS_EXECUTED",
          "summary": "1 action(s) exécutée(s) avec succès",
          "command_results": [
            {
              "command": "zones.create",
              "status": "SUCCESS",
              "details": "Création de la zone \"Test L1\"",
              "data": { "zone_id": "8bf97c51-55a9-422d-b699-4e4d956e69f6", "name": "Test L1" },
              "is_action_command": true
            }
          ]
        }
    """

    @Test
    fun anActionTellsTheModelTheIdOfWhatItCreated() {
        val message = SystemMessage.fromJson(storedAfterCreateZone)
        assertNotNull(message)

        val text = message!!.toPromptText()

        assertTrue(text, text.contains("zone_id: 8bf97c51-55a9-422d-b699-4e4d956e69f6"))
    }

    /** A query's data travels in formatted_data; its result line does not repeat it. */
    @Test
    fun aQueryLineCarriesNoData() {
        val stored = """
            {
              "type": "DATA_ADDED",
              "summary": "1 requête(s) de données ajoutée(s) au contexte",
              "formatted_data": "# All zones\n{ }",
              "command_results": [
                { "command": "zones.list", "status": "SUCCESS", "details": "All zones",
                  "data": { "count": 6 }, "is_action_command": false }
              ]
            }
        """

        val text = SystemMessage.fromJson(stored)!!.toPromptText()

        assertFalse(text, text.contains("count: 6"))
        assertTrue(text, text.contains("# All zones"))
    }
}
