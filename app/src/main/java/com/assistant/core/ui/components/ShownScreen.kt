package com.assistant.core.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A screen that only answers the touches made once it shows. A touch made while the app was
 * still busy opening it (a second touch on the tile that opened it) waits in line and would
 * land on what this screen drew at that place: it is dropped instead, judged by the instant it
 * was made, so a touch made after the screen shows is never delayed.
 */
@Composable
fun ShownScreen(content: @Composable () -> Unit) {
    // The instant its first frame was drawn; until then, every touch is from before
    var shownAt by remember { mutableLongStateOf(Long.MAX_VALUE) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        shownAt = SystemClock.uptimeMillis()
    }
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.uptimeMillis < shownAt }) event.changes.forEach { it.consume() }
                }
            }
        }
    ) {
        content()
    }
}
