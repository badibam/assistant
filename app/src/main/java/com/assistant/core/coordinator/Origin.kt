package com.assistant.core.coordinator

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Who a call comes from, carried by the coroutine running it: the coordinator sets it when it runs
 * an operation, and every call made from inside that operation keeps it — an import the AI starts
 * writes its lines as the AI. A service reads it with [currentOrigin], and decides with it what
 * only a person may do.
 *
 * @property byTheApp Whether the app itself makes the call: a service from inside its own operation,
 *           a scheduler, the app's own work (Source.SYSTEM) — never a screen or the AI calling directly.
 *           What the app alone writes, a field a schema marks system-managed, comes from no other.
 */
class Origin(val source: Source, val byTheApp: Boolean = false) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<Origin>
}

/** Whether the operation running was called by the app itself (Origin.byTheApp); false outside an operation. */
suspend fun calledByTheApp(): Boolean = coroutineContext[Origin]?.byTheApp == true

/**
 * The origin of the operation running. Always set inside a service, which the coordinator runs.
 *
 * @throws IllegalStateException outside an operation of the coordinator
 */
suspend fun currentOrigin(): Source =
    coroutineContext[Origin]?.source ?: throw IllegalStateException("no origin: not inside an operation of the coordinator")
