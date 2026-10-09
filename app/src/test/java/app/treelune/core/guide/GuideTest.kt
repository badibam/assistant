package app.treelune.core.guide

import app.treelune.core.coordinator.Source
import app.treelune.core.navigation.Place
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Guide promises (docs/design/user-journey.md): its chapters read from their file, the
 * journey first; a step's place filled with what earlier steps kept; the demo's tutorials known
 * from their steps; the progress written and read back the same; the way to a place, from the
 * places open or from the home screen.
 */
class GuideTest {

    private val chapters = GuideChapters.parse(File("src/main/assets/guide/chapters.json").readText())

    @Test
    fun theJourneyComesFirstInItsOrder() {
        val journey = chapters.takeWhile { it.part.ordered }
        assertEquals(listOf("first_steps", "connect_ai", "build_with_ai", "automate"), journey.map { it.id })
        // The parts in their order, discovery before getting started, then going further
        assertEquals(GuidePart.entries, chapters.map { it.part }.distinct())
        assertTrue(chapters.drop(journey.size).all { it.part == GuidePart.DEEPER })
    }

    @Test
    fun everyPlaceOfTheJourneyReads() {
        val kept = mapOf("zone" to "z", "tool" to "t", "automation" to "a")
        chapters.flatMap { it.steps }.mapNotNull { it.target }.forEach { Place.of(Guide.fill(it, kept)!!) }
    }

    @Test
    fun firstStepsBeginsInTheDemoAndEndsOutsideIt() {
        val first = chapters.first { it.id == "first_steps" }
        assertTrue(first.usesDemo)
        assertEquals(listOf(true, true, true, false, false, false), first.steps.map { it.usesDemo })
        assertFalse(chapters.first { it.id == "connect_ai" }.usesDemo)
    }

    @Test
    fun aStepWaitsForWhatItSays() {
        val add = chapters.first { it.id == "first_steps" }.steps[1]
        assertEquals(StepKind.DO, add.kind)
        assertEquals(Await("tool_data.create", Source.USER, mapOf("tool_instance_id" to "demo-course-runs")), add.await)
        val byTheAi = chapters.first { it.id == "build_with_ai" }.steps[1]
        assertEquals(Source.AI, byTheAi.await!!.origin)
    }

    @Test
    fun keptNamesFillAPlaceOrLeaveItUnknown() {
        assertEquals("tool/t-1/z-1", Guide.fill("tool/{tool}/{zone}", mapOf("tool" to "t-1", "zone" to "z-1")))
        assertNull(Guide.fill("tool/{tool}/{zone}", mapOf("zone" to "z-1")))
        assertEquals("home", Guide.fill("home", emptyMap()))
    }

    @Test
    fun theProgressIsReadBackAsWritten() {
        val progress = GuideProgress(true, false, "first_steps", mapOf(
            "first_steps" to ChapterProgress("first_steps", 4, false, mapOf("zone" to "z-1")),
            "connect_ai" to ChapterProgress("connect_ai", 4, true, emptyMap())
        ))
        assertEquals(progress, GuideProgress.fromJson(JSONObject(progress.toJson().toString())))
        val none = GuideProgress(false, false, null, emptyMap())
        assertEquals(none, GuideProgress.fromJson(JSONObject(none.toJson().toString())))
    }

    private val names = mapOf<Place, String>(
        Place.Home to "Home", Place.Zone("z-run") to "Running", Place.Tool("t-runs", "z-run") to "Runs",
        Place.Zone("z-kitchen") to "Kitchen"
    )
    private fun gestures(open: List<Place>, target: Place, chat: Boolean = false) =
        GuidePath.gestures(open, chat, target, { names[it] }) { key, args -> "$key(${args.joinToString()})" }

    @Test
    fun fromTheHomeScreenTheWayGoesDown() {
        assertEquals(listOf("guide_path_zone(Running)", "guide_path_tool(Runs)"), gestures(listOf(Place.Home), Place.Tool("t-runs", "z-run")))
    }

    @Test
    fun fromElsewhereTheWayGoesUpFirst() {
        assertEquals(listOf("guide_path_back_to(Home)", "guide_path_zone(Running)"), gestures(listOf(Place.Home, Place.Zone("z-kitchen")), Place.Zone("z-run")))
        assertEquals(listOf("guide_path_tool(Runs)"), gestures(listOf(Place.Home, Place.Zone("z-run")), Place.Tool("t-runs", "z-run")))
    }

    @Test
    fun onThePlaceThereIsNoWay() {
        assertEquals(emptyList<String>(), gestures(listOf(Place.Home, Place.Zone("z-run")), Place.Zone("z-run")))
        assertEquals(emptyList<String>(), gestures(listOf(Place.Home), Place.Chat, chat = true))
        assertEquals(listOf("guide_path_chat()"), gestures(listOf(Place.Home), Place.Chat))
    }

    @Test
    fun theWayIsOneSentence() {
        assertEquals("Open the zone Running, then touch Runs", GuidePath.sentence(listOf("open the zone Running", "touch Runs"), ", then "))
    }
}
