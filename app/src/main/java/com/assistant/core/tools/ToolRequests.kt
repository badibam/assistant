package com.assistant.core.tools

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Asks for a tool opened from any screen, on one of its entries as its tool type opens one: a
 * chart's hole leads to the entry to correct. The main screen, which holds the zones, opens the
 * tool's zone and the tool in it, as a notification does.
 */
object ToolRequests {
    private val _requests = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 1)
    val requests: SharedFlow<Pair<String, String?>> = _requests.asSharedFlow()

    /** Opens the tool [toolInstanceId], on [entryId] when given. */
    fun open(toolInstanceId: String, entryId: String? = null) {
        _requests.tryEmit(toolInstanceId to entryId)
    }
}
