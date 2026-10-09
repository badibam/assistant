package app.treelune.core.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What seals a secret setting (an API key, the relay's secret) before it is stored, and opens it
 * when it is read: the database never holds one in clear (SecretSettings).
 */
interface SecretBox {

    /** [plain], sealed: the text the database stores in its place. */
    fun seal(plain: String): String

    /**
     * [stored], opened. Throws [UnreadableSecret] when it was sealed and does not open here — the
     * key that sealed it is not this phone's (a backup restored from another), or is gone.
     */
    fun open(stored: String): String

    companion object {
        /** What starts every sealed value: a stored secret without it was never sealed. */
        const val PREFIX = "sealed:"

        /** Whether [stored] is a sealed value. */
        fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

        /** The box of the app, its key in the Android Keystore. */
        fun of(): SecretBox = KeystoreSecretBox
    }
}

/** A sealed secret that does not open on this phone. */
class UnreadableSecret(cause: Throwable) : Exception("A sealed secret does not open with this phone's key", cause)

/**
 * The app's box: AES-GCM, its key made in the Android Keystore on first use and never leaving it
 * (as Saylune's SecretStore). A sealed value is the prefix, then the IV and the ciphertext in base64.
 *
 * The key cannot be exported: a sealed value, in a backup or in Android's own, opens on this phone
 * alone. Restored elsewhere it is unreadable, and said so — never taken for an empty setting.
 */
private object KeystoreSecretBox : SecretBox {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "treelune.secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Held for the life of the process: every fetch is a round trip to the Keystore. */
    private val key: SecretKey by lazy { loadKey() }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return SecretBox.PREFIX + Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    override fun open(stored: String): String {
        check(SecretBox.isSealed(stored)) { "A secret setting stored without being sealed" }
        return try {
            val raw = Base64.decode(stored.removePrefix(SecretBox.PREFIX), Base64.NO_WRAP)
            require(raw.size > IV_BYTES) { "A sealed secret too short to hold its IV" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            throw UnreadableSecret(e)
        }
    }

    private fun loadKey(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
