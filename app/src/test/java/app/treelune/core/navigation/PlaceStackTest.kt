package app.treelune.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the stack of places promises (docs/design/navigation.md): every place read back from its
 * address; a place opened with nothing open rebuilt from its parents, opened over something
 * stacked alone; the breadcrumb naming the parent of a place stacked without it; a deleted zone
 * taking its places with it.
 */
class PlaceStackTest {

    private val home = Place.Home
    private val course = Place.Zone("z-course")
    private val runs = Place.Tool("t-runs", "z-course")
    private val kitchen = Place.Zone("z-kitchen")

    private val everyPlace = listOf(
        Place.Home, Place.HomeConfig, Place.Settings, Place.SettingsPage("ai_providers"),
        Place.CreateZone(null), Place.CreateZone("Vie / perso"), course, Place.ZoneConfig("z-course"), runs,
        Place.ToolConfig("z-course", "tracking", "t-runs", null), Place.ToolConfig("z-course", "chart", null, "Analyse"),
        Place.Variable("z-course", null, "Corps"), Place.Variable("z-course", "v-1", null),
        Place.Automation("a-1", "z-course"), Place.Execution("s-1", "a-1", "z-course"), Place.Seed("s-2", "z-course"),
        Place.Chat
    )

    @Test
    fun everyPlaceIsReadBackFromItsAddress() {
        everyPlace.forEach { assertEquals(it, Place.of(it.address)) }
    }

    @Test
    fun aGroupNameWithASlashStaysOneParameter() {
        assertEquals(Place.CreateZone("Vie / perso"), Place.of(Place.CreateZone("Vie / perso").address))
    }

    @Test
    fun openedWithNothingOpenTheStackIsRebuiltFromTheParents() {
        assertEquals(listOf(home, course, runs), PlaceStack.open(listOf(home), runs))
        assertEquals(
            listOf(home, course, Place.Automation("a-1", "z-course"), Place.Execution("s-1", "a-1", "z-course")),
            PlaceStack.open(listOf(home), Place.Execution("s-1", "a-1", "z-course"))
        )
    }

    @Test
    fun openedOverSomethingThePlaceIsStackedAlone() {
        val stack = listOf(home, kitchen, Place.Chat)
        assertEquals(listOf(home, kitchen, Place.Chat, runs), PlaceStack.open(stack, runs))
        // One back returns to the chat
        assertEquals(stack, PlaceStack.pop(PlaceStack.open(stack, runs)))
    }

    @Test
    fun theHomeScreenNeverLeaves() {
        assertEquals(listOf(home), PlaceStack.pop(listOf(home)))
        assertEquals(listOf(home), PlaceStack.popTo(listOf(home, course, runs), -1))
    }

    @Test
    fun theBreadcrumbGoesBackToAnyPlaceUnder() {
        assertEquals(listOf(home, course), PlaceStack.popTo(listOf(home, course, runs, Place.ToolConfig("z-course", "tracking", "t-runs", null)), 1))
    }

    @Test
    fun theChatIsLaidOverThePlaceUnderIt() {
        assertEquals(1, PlaceStack.baseIndex(listOf(home, kitchen, Place.Chat)))
        assertEquals(3, PlaceStack.baseIndex(listOf(home, kitchen, Place.Chat, runs)))
    }

    @Test
    fun aPlaceStackedWithoutItsParentNamesIt() {
        val names = mapOf(home to "Accueil", kitchen to "Cuisine", Place.Chat to "Conversation", runs to "Sorties", course to "Course")
        val stack = listOf(home, kitchen, Place.Chat, runs)
        val onRuns = PlaceStack.breadcrumb(stack, 3) { names[it] }
        assertEquals(listOf("Accueil", "Cuisine", "Conversation"), onRuns.crumbs.map { it.name })
        assertEquals(listOf(null, null, null), onRuns.crumbs.map { it.parent })
        assertEquals("Course", onRuns.parent)

        // Further on, the place stacked alone carries its parent in the line
        val config = Place.ToolConfig("z-course", "tracking", "t-runs", null)
        val onConfig = PlaceStack.breadcrumb(stack + config, 4) { names[it] }
        assertEquals("Course", onConfig.crumbs[3].parent)
        assertNull(onConfig.parent)
    }

    @Test
    fun aPlaceUnderItsParentNamesNothingMore() {
        val crumb = PlaceStack.breadcrumb(listOf(home, course, runs), 2) { "x" }
        assertEquals(listOf(null, null), crumb.crumbs.map { it.parent })
        assertNull(crumb.parent)
    }

    @Test
    fun aDeletedZoneTakesItsPlacesWithIt() {
        val stack = listOf(home, kitchen, Place.Chat, course, runs, Place.ToolConfig("z-course", "tracking", "t-runs", null))
        assertEquals(listOf(home, kitchen, Place.Chat), PlaceStack.dropZone(stack, "z-course"))
    }
}
