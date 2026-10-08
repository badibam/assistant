package app.treelune.core.fields

import app.treelune.tools.messages.MessageToolType
import app.treelune.tools.questionnaire.QuestionnaireToolType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers how an entry starts in a tool type whose entries live by a status
 * (docs/design/entry-start-state.md): the creator gives the status, the tool type sets the rest
 * or refuses, and an entry nothing would ever pick up is never written.
 */
class EntryStartTest {

    private val now = 1_791_455_460_000L
    private val goal = EntryStart(emptyList(), refusal = "opened by the goal")

    private fun state(json: String) = JSONObject(json)

    private fun written(decision: EntryStart.Decision): JSONObject? =
        (decision as EntryStart.Decision.Write).state

    @Test
    fun withoutDeclaration_theStateGoesAsItCame() {
        assertEquals("""{"position":2}""", written(EntryStart.decide(null, state("""{ "position": 2 }"""), byTheApp = false, now))?.toString())
        assertNull(written(EntryStart.decide(null, null, byTheApp = false, now)))
    }

    /** The message the AI wrote on 2026-10-08 with no state, which the scheduler never saw. */
    @Test
    fun aMessageWithoutStatus_isRefused_whoeverWritesIt() {
        assertTrue(EntryStart.decide(MessageToolType.START, null, byTheApp = false, now) is EntryStart.Decision.NoStatus)
        assertTrue(EntryStart.decide(MessageToolType.START, state("{}"), byTheApp = false, now) is EntryStart.Decision.NoStatus)
        // An import runs inside its operation, as the app: a line with no status is refused all the same
        assertTrue(EntryStart.decide(MessageToolType.START, null, byTheApp = true, now) is EntryStart.Decision.NoStatus)
    }

    @Test
    fun aMessageGivenPending_isOneToSend_placedByHand() {
        val set = written(EntryStart.decide(MessageToolType.START, state("""{ "status": "pending" }"""), byTheApp = false, now))!!

        assertEquals("pending", set.getString("status"))
        assertEquals("MANUAL", set.getString("triggered_by"))
    }

    @Test
    fun aStatusTheToolDoesNotStartWith_isRefused_namingWhatWasGiven() {
        val refused = EntryStart.decide(MessageToolType.START, state("""{ "status": "sent" }"""), byTheApp = false, now)

        assertEquals("sent", (refused as EntryStart.Decision.NoStatus).given)
    }

    @Test
    fun theRestOfTheState_isTheApps() {
        val refused = EntryStart.decide(MessageToolType.START, state("""{ "status": "pending", "triggered_by": "SCHEDULE" }"""), byTheApp = false, now)

        assertEquals(listOf("triggered_by"), (refused as EntryStart.Decision.OtherFields).fields)
    }

    /** A scheduler, the tool's own operation, the demo: they write the whole state they mean. */
    @Test
    fun whatTheAppWritesItself_keepsItsState() {
        val scheduled = state("""{ "status": "pending", "triggered_by": "SCHEDULE" }""")

        assertEquals(scheduled.toString(), written(EntryStart.decide(MessageToolType.START, scheduled, byTheApp = true, now)).toString())
    }

    @Test
    fun aQuestionnairePassed_isFilledNow_oneToFillLater_isNot() {
        val filled = written(EntryStart.decide(QuestionnaireToolType.START, state("""{ "status": "filled" }"""), byTheApp = false, now))!!
        val toFill = written(EntryStart.decide(QuestionnaireToolType.START, state("""{ "status": "to_fill" }"""), byTheApp = false, now))!!

        assertEquals(now, filled.getLong("filled_at"))
        assertFalse(toFill.has("filled_at"))
        // Ignoring is a gap chosen on one the app planned, never a start
        assertTrue(EntryStart.decide(QuestionnaireToolType.START, state("""{ "status": "ignored" }"""), byTheApp = false, now) is EntryStart.Decision.NoStatus)
    }

    @Test
    fun aToolThatMakesItsEntriesItself_refusesEveryOtherCreation_butItsOwn() {
        val refused = EntryStart.decide(goal, state("""{ "status": "active" }"""), byTheApp = false, now)

        assertEquals("opened by the goal", (refused as EntryStart.Decision.NoStatus).start.refusal)
        assertEquals("active", written(EntryStart.decide(goal, state("""{ "status": "active" }"""), byTheApp = true, now))?.getString("status"))
    }

    /** What the AI reads: the status given at creation, the rest written by the app. */
    @Test
    fun theSchema_letsTheStatusBeGiven_andNothingElseOfTheState() {
        fun stateSchema(start: EntryStart?) = JSONObject(EntrySchemaGenerator.generate(EntryFields(
            state = listOf(
                StateField(FieldDefinition("status", "status", null, FieldType.TEXT, false, null), filterable = true),
                StateField(FieldDefinition("triggered_by", "triggered_by", null, FieldType.TEXT, false, null), filterable = true)
            ),
            start = start
        ), emptyList()) { it }).getJSONObject("properties").getJSONObject("state")

        val messages = stateSchema(MessageToolType.START)
        assertFalse(messages.optBoolean("system_managed"))
        assertFalse(messages.getJSONObject("properties").getJSONObject("status").optBoolean("system_managed"))
        assertTrue(messages.getJSONObject("properties").getJSONObject("triggered_by").optBoolean("system_managed"))

        assertTrue(stateSchema(goal).optBoolean("system_managed"))
        assertTrue(stateSchema(goal).getString("description").endsWith("opened by the goal"))
        assertTrue(stateSchema(null).optBoolean("system_managed"))
    }
}
