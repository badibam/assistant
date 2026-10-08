package app.treelune.tools.goal

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A goal judged by counting: every node met or not, at least N children, essential ones failing
 * their parent alone; a value missing leaves it unknown until what is known settles it.
 */
class GoalJudgeTest {

    private fun criterion(name: String, essential: Boolean = false) =
        Criterion(key = name, name = name, essential = essential, entered = null, instructions = null, condition = JSONObject())

    /** Each criterion met or not, by key. */
    private fun met(vararg pairs: Pair<String, Boolean>) = pairs.associate { (key, yes) -> key to (null to if (yes) Met.YES else Met.NO) }

    @Test
    fun `all by default, at least N when said`() {
        val definition = GoalDefinition(listOf(criterion("a"), criterion("b"), criterion("c")), emptyList(), atLeast = null)
        assertEquals(Met.NO, GoalJudge.judge(definition, met("a" to true, "b" to true, "c" to false)).verdict)
        assertEquals(Met.YES, GoalJudge.judge(definition.copy(atLeast = 2), met("a" to true, "b" to true, "c" to false)).verdict)
    }

    @Test
    fun `at least 2 of 3 with A essential, A and B are enough, B and C are not`() {
        val definition = GoalDefinition(listOf(criterion("A", essential = true), criterion("B"), criterion("C")), emptyList(), atLeast = 2)
        assertEquals(Met.YES, GoalJudge.judge(definition, met("A" to true, "B" to true, "C" to false)).verdict)
        assertEquals(Met.NO, GoalJudge.judge(definition, met("A" to false, "B" to true, "C" to true)).verdict)
    }

    @Test
    fun `an essential criterion fails its sub-goal, not the goal unless the sub-goal is essential too`() {
        val sub = SubGoal("sport", listOf(criterion("run", essential = true), criterion("swim")), atLeast = 1, essential = false)
        val definition = GoalDefinition(listOf(criterion("sleep")), listOf(sub), atLeast = 1)
        val judged = GoalJudge.judge(definition, met("run" to false, "swim" to true, "sleep" to true))
        assertEquals(Met.NO, judged.subGoals["sport"])
        assertEquals(Met.YES, judged.verdict)
        assertEquals(Met.NO, GoalJudge.judge(definition.copy(subGoals = listOf(sub.copy(essential = true))), met("run" to false, "swim" to true, "sleep" to true)).verdict)
    }

    @Test
    fun `a value missing leaves the verdict unknown until the known ones settle it`() {
        val definition = GoalDefinition(listOf(criterion("a"), criterion("b")), emptyList(), atLeast = 1)
        assertEquals(Met.UNKNOWN, GoalJudge.judge(definition, met("a" to false)).verdict)
        assertEquals(Met.YES, GoalJudge.judge(definition, met("a" to true)).verdict)
    }

    @Test
    fun `a definition reads from the config with its keys, a new key is made from a name`() {
        val definition = GoalDefinition.of(JSONObject("""{"criteria":[{"key":"c_sleep","name":"Sommeil ≥ 7 h","field":{"type":"DURATION"},"condition":{"op":">=","right":{"constant":25200000}}}],
            "sub_goals":[{"name":"Sport","at_least":1,"criteria":[{"key":"c_run","name":"Course","field":{"type":"BOOLEAN"},"essential":true,"condition":{"op":"=","right":{"constant":true}}}]}],"at_least":2}"""))
        assertEquals(2, definition.allCriteria.size)
        assertEquals("c_sleep", definition.criteria.single().key)
        assertEquals("c_sommeil_7_h", Criterion.keyOf("Sommeil ≥ 7 h"))
        assertEquals(2, definition.atLeast)
        assertEquals(true, definition.subGoals.single().criteria.single().essential)
        assertEquals(app.treelune.core.fields.FieldType.DURATION, definition.criteria.single().enteredField()!!.type)
    }
}

/**
 * A criterion's key is given once, when it is created, and kept after: renaming it keeps its
 * values, a criterion made anew gets a key of its own.
 */
class GoalKeysTest {

    private fun keys(config: JSONObject): List<String> {
        val completed = GoalToolType.completeConfig(config, null)
        return GoalDefinition.of(completed).allCriteria.map { it.key }
    }

    @Test
    fun `a new criterion gets a key from its name, apart from the ones taken`() {
        val config = JSONObject("""{"criteria":[{"name":"Sport"},{"key":"c_sport","name":"Autre"}],
            "sub_goals":[{"name":"S","criteria":[{"name":"Sport"}]}]}""")
        assertEquals(listOf("c_sport_2", "c_sport", "c_sport_3"), keys(config))
    }

    @Test
    fun `a key given is kept whatever the name becomes`() {
        val config = JSONObject("""{"criteria":[{"key":"c_sommeil","name":"Dormir 7 h","field":{"type":"DURATION"},"condition":{"op":">=","right":{"constant":1}}}]}""")
        assertEquals(listOf("c_sommeil"), keys(config))
    }

    /** An entered criterion's condition is put on its own value, whatever its left side said. */
    @Test
    fun `an entered criterion's left side is its own value`() {
        val config = JSONObject("""{"criteria":[{"name":"Sport","field":{"type":"BOOLEAN"},"condition":{"op":"=","right":{"constant":true}}}]}""")
        val completed = GoalToolType.completeConfig(config, null)
        val condition = completed.getJSONArray("criteria").getJSONObject(0).getJSONObject("condition")
        assertEquals("data.c_sport", condition.getJSONObject("left").getString("field"))
    }
}
