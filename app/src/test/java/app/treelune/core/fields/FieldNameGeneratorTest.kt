package app.treelune.core.fields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the technical name a custom field gets from its display name: the key its values are
 * stored under and the one the AI reads and writes, fixed once given.
 */
class FieldNameGeneratorTest {

    private fun name(displayName: String, taken: List<String> = emptyList()) =
        FieldNameGenerator.generateName(displayName, taken)

    /** Whatever the display name, the key is lowercase ASCII, starts with a letter, and has no stray underscore. */
    @Test
    fun anyDisplayName_givesAValidKey() {
        val displayNames = listOf(
            "Calories totales", "Temp. (°C)", "Nombre d'œufs", "  Multiple   Spaces  ",
            "Min / Max", "2e repas", "123", "温度", "Ł ó dź", "___", "a__b", "Æther -- Ωmega"
        )
        for (displayName in displayNames) {
            val key = name(displayName)
            assertTrue("'$displayName' gave '$key'", key.matches(Regex("[a-z][a-z0-9]*(_[a-z0-9]+)*")))
        }
    }

    @Test
    fun accentsAreDropped_andLigaturesSpelledOut() {
        assertEquals("qualite_du_sommeil", name("Qualité du sommeil"))
        assertEquals("coeur", name("Cœur"))
        assertEquals("nombre_d_oeufs", name("Nombre d'œufs"))
        assertEquals("strasse", name("Straße"))
        assertEquals("aether", name("Æther"))
    }

    /** Punctuation separates words the way a space does, and never leaves a double underscore. */
    @Test
    fun punctuation_separatesWords() {
        assertEquals("heure_de_coucher", name("Heure-de-coucher"))
        assertEquals("pre_sommeil", name("Pré-sommeil"))
        assertEquals("min_max", name("Min / Max"))
        assertEquals("temp_c", name("Temp. (°C)"))
    }

    /** A key cannot start with a digit, and a name with no letter still gets one. */
    @Test
    fun digitsAndOtherScripts_getThePrefix() {
        assertEquals("field_2e_repas", name("2e repas"))
        assertEquals("field_123", name("123"))
        assertEquals("field", name("温度"))
    }

    @Test
    fun aTakenName_getsTheNextFreeSuffix() {
        assertEquals("calories_2", name("Calories", listOf("calories")))
        assertEquals("calories_3", name("Calories", listOf("calories", "calories_2")))
        assertEquals("field_2", name("温度", listOf("field")))
    }
}
