package app.treelune.core.validation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The validation library's messages reach the user and the AI with their accents: its message
 * files are ISO-8859-1, which Android, reading a bundle as UTF-8, showed as replacement characters.
 */
class ValidationMessagesTest {

    @Test
    fun frenchMessagesKeepTheirAccents() {
        val french = SchemaValidator.messageBundle(Locale.FRENCH)
        assertEquals("{0}: ne doit pas dépasser {1} caractères", french.getString("maxLength"))
    }
}
