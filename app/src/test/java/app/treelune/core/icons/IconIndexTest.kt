package app.treelune.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the icon vocabulary as the app ships it: assets/icons/index.json, read as the app
 * reads it.
 *
 * The search is the one the picker and the AI's ICONS command both run, so a case here is what
 * a user typing a word, or the model asking for one, gets back.
 */
class IconIndexTest {

    private val index = IconIndex.parse(File("src/main/assets/icons/index.json").readText())

    // ==================== Names ====================

    /** A current Lucide name designates its icon. */
    @Test
    fun aCurrentNameResolvesToItself() {
        assertEquals("scale", index.resolve("scale"))
    }

    /**
     * A name Lucide gave an icon before renaming it still finds that icon: stored before an
     * update of Lucide, or learned by the model before the rename, it does not break.
     */
    @Test
    fun aFormerNameResolvesToTheIconItBecame() {
        assertEquals("chart-bar", index.resolve("bar-chart-horizontal"))
    }

    /** A name that never existed designates nothing. The prompt's old example was one. */
    @Test
    fun anUnknownNameResolvesToNothing() {
        assertNull(index.resolve("currency_euro"))
        assertNull(index.resolve("note"))
    }

    /** The names the tooltypes use by default are all Lucide's. */
    @Test
    fun theTooltypesDefaultIconsExist() {
        listOf("book-open", "bell", "sticky-note", "activity").forEach {
            assertEquals(it, index.resolve(it))
        }
    }

    // ==================== Search ====================

    /** The icon named by the word comes first. */
    @Test
    fun anExactNameComesFirst() {
        val result = index.search(listOf("scale"), emptyList(), 10)
        assertEquals("scale", result.matches.first().name)
    }

    /** Tags find what the name does not say: "balance" is one of scale's. */
    @Test
    fun tagsFindWhatTheNameDoesNotSay() {
        val result = index.search(listOf("balance"), emptyList(), 100)
        assertTrue(result.matches.any { it.name == "scale" })
    }

    /** Several words are synonyms: an icon matching any of them is found. */
    @Test
    fun wordsAreAlternatives() {
        val one = index.search(listOf("weight"), emptyList(), Int.MAX_VALUE).total
        val both = index.search(listOf("weight", "balance"), emptyList(), Int.MAX_VALUE).total
        assertTrue(both >= one)
    }

    /** A category narrows the search to its icons. */
    @Test
    fun aCategoryNarrowsTheSearch() {
        val result = index.search(emptyList(), listOf("weather"), Int.MAX_VALUE)
        assertTrue(result.total > 0)
        assertTrue(result.matches.all { "weather" in it.categories })
    }

    /**
     * Past the limit the result says how many there were and where: a search too wide to list
     * still tells the model how to narrow it.
     */
    @Test
    fun aTruncatedSearchSaysHowManyThereWereAndWhere() {
        val result = index.search(listOf("arrow"), emptyList(), 30)
        assertEquals(30, result.matches.size)
        assertTrue(result.truncated)
        assertTrue(result.total > 30)
        assertTrue(result.countByCategory.getValue("arrows") > 0)
    }

    /** Every category is one the index knows, with the icon that stands for it. */
    @Test
    fun everyCategoryHasItsIcon() {
        assertFalse(index.categories.isEmpty())
        index.categories.forEach {
            assertEquals(it.icon, index.resolve(it.icon))
            assertTrue(index.isCategory(it.id))
        }
    }
}
