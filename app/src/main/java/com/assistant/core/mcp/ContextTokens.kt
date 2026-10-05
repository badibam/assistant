package com.assistant.core.mcp

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The context token app_context hands out, which every other tool requires (docs/design/mcp-server.md):
 * an AI cannot act in the app before it has read what the app is. It carries the instant it was
 * handed out and the app's signature of it, so the app recognizes its own tokens without keeping
 * them; one older than [validity] is refused, and a conversation resumed later reads the context
 * again.
 *
 * @param key The app's secret, never shown: who holds it can sign
 * @param now The clock
 */
class ContextTokens(
    private val key: ByteArray,
    private val now: () -> Long,
    private val validity: Long = VALIDITY
) {
    /** A token handed out now: "<instant>.<signature>". */
    fun issue(): String {
        val at = now().toString()
        return "$at.${sign(at)}"
    }

    /** Whether [token] is one this app signed less than [validity] ago. */
    fun isValid(token: String?): Boolean {
        val (at, signature) = token?.split('.')?.takeIf { it.size == 2 } ?: return false
        val issued = at.toLongOrNull() ?: return false
        val age = now() - issued
        return age in 0 until validity && java.security.MessageDigest.isEqual(signature.toByteArray(), sign(at).toByteArray())
    }

    private fun sign(text: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(text.toByteArray()))
    }

    companion object {
        const val VALIDITY = 24 * 60 * 60 * 1000L
        private const val ALGORITHM = "HmacSHA256"
    }
}
