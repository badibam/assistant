package app.treelune.core.validation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The validation library's messages reach the user and the AI with their accents: its message
 * files are ISO-8859-1, which Android, reading a bundle as UTF-8, showed as replacement characters.
 * And with their apostrophes: formatted by MessageFormat, a lone one vanished ("na pas").
 */
class ValidationMessagesTest {

    @Test
    fun frenchMessagesKeepTheirAccents() {
        val french = SchemaValidator.messageBundle(Locale.FRENCH)
        assertEquals("{0}: ne doit pas dépasser {1} caractères", french.getString("maxLength"))
    }

    @Test
    fun frenchMessagesKeepTheirApostrophes() {
        val french = SchemaValidator.messageBundle(Locale.FRENCH)
        val shown = java.text.MessageFormat.format(french.getString("enum"), "$.etiquettes[1]", "[a, b]")
        org.junit.Assert.assertTrue(shown, shown.contains("n'a pas") && shown.contains("l'énumération"))
    }
}
