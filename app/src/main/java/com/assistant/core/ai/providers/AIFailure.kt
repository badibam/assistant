package com.assistant.core.ai.providers

/**
 * Why an AI call failed, decided where the failure happens instead of guessed from its wording.
 *
 * It used to be read back out of the error message: anything containing neither "provider" nor
 * "configured" was taken for a network failure, and a network failure retries the whole prompt
 * every 30 seconds for as long as the session lives. A rate limit, an overloaded provider or an
 * exhausted credit therefore billed the same call again and again, and nothing stopped it.
 *
 * Providers had begun wording their error messages so they would land on the right side of that
 * test, which is how a string meant for the user became load-bearing.
 */
enum class AIFailure {
    /**
     * Nothing reached the provider. Waiting for the network to come back and retrying is right,
     * and costs nothing while it waits.
     */
    NETWORK,

    /**
     * The request went out whole and no answer came back: the connection dropped, or the read
     * timeout expired. The provider may have billed it, so it is not retried.
     */
    LOST,

    /**
     * The provider was reached and refused this call for now: rate limit, overload, exhausted
     * credit. Retrying the same prompt bills again for the same refusal, so the session stops
     * and says why.
     */
    REFUSED,

    /**
     * Configuration, credentials, or a request the provider cannot honour as built. No retry
     * changes it; the user has to act.
     */
    CONFIG
}

/**
 * Classify a failure raised while the call was in flight.
 *
 * An I/O error before the request went out whole means the provider was never reached; one after
 * it is a lost answer (awaitReply() tells them apart). Anything else happened after contact, or
 * inside our own handling, and retrying it on a timer would repeat whatever went wrong.
 */
fun aiFailureOf(e: Throwable): AIFailure = when (e) {
    is ResponseLostException -> AIFailure.LOST
    is java.io.IOException -> AIFailure.NETWORK
    else -> AIFailure.REFUSED
}

/**
 * Classify a failure the provider answered with, from its HTTP status.
 *
 * The statuses listed are the ones a retry cannot fix: the request, the key or the account is
 * what has to change. Everything else the provider answers — 429, 5xx, timeouts it reports
 * itself — is a refusal for now, and the session stops rather than pay for it again.
 */
fun aiFailureOf(status: Int): AIFailure = when (status) {
    400, 401, 403, 404, 405, 413, 414, 422 -> AIFailure.CONFIG
    else -> AIFailure.REFUSED
}
