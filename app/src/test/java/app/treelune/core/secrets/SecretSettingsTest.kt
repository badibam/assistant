package app.treelune.core.secrets

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The secret settings: sealed where they are stored, opened where they are read, an unreadable one said. */
class SecretSettingsTest {

    /** Seals by marking; a value sealed by "another phone" does not open. */
    private val box = object : SecretBox {
        override fun seal(plain: String) = SecretBox.PREFIX + plain.reversed()
        override fun open(stored: String): String {
            check(SecretBox.isSealed(stored))
            val body = stored.removePrefix(SecretBox.PREFIX)
            if (body.startsWith("other:")) throw UnreadableSecret(IllegalStateException("another key"))
            return body.reversed()
        }
    }

    private fun field(name: String, secret: Boolean = false) =
        SettingNode.Field(FieldDefinition(name, name, null, FieldType.TEXT, false, null), secret = secret)

    @Test
    fun names_areTheSecretsStoredFlat_inSectionsAndVariantsToo() {
        val nodes = listOf(
            field("model"),
            field("api_key", secret = true),
            SettingNode.Variant(field("mode"), mapOf(
                "a" to emptyList(),
                "b" to listOf(SettingNode.Section("relay", listOf(field("url"), field("relay_secret", secret = true))))
            )),
            SettingNode.Group("inner", "inner", listOf(field("deep", secret = true)))
        )
        assertEquals(listOf("api_key", "relay_secret"), SecretSettings.names(nodes))
    }

    @Test
    fun aSecret_isNeverStoredInClear_andReadsBackAsEntered() {
        val entered = JSONObject().put("api_key", "sk-123").put("model", "m")
        val stored = SecretSettings.seal(listOf("api_key"), entered, box)
        assertTrue(SecretBox.isSealed(stored.getString("api_key")))
        assertFalse(stored.toString().contains("sk-123"))
        assertEquals("m", stored.getString("model"))

        val opened = SecretSettings.open(listOf("api_key"), stored, box)
        assertEquals("sk-123", opened.settings.getString("api_key"))
        assertTrue(opened.unreadable.isEmpty())
    }

    @Test
    fun aBlankOrAbsentSecret_staysAsItIs() {
        val stored = SecretSettings.seal(listOf("api_key", "other"), JSONObject().put("api_key", ""), box)
        assertEquals("", stored.getString("api_key"))
        assertFalse(stored.has("other"))
    }

    @Test
    fun aSecretSealedOnAnotherPhone_isLeftOut_andNamed() {
        val stored = JSONObject().put("api_key", SecretBox.PREFIX + "other:xyz").put("model", "m")
        val opened = SecretSettings.open(listOf("api_key"), stored, box)
        assertFalse(opened.settings.has("api_key"))
        assertEquals(listOf("api_key"), opened.unreadable)
        assertEquals("m", opened.settings.getString("model"))
    }
}
