package com.assistant.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Where a screen's stored content stands: still loading, loaded, or failed to load.
 */
enum class LoadState { LOADING, LOADED, FAILED }

/**
 * Loads a screen's stored content once per screen, not once per composition.
 *
 * An effect runs again when the activity is recreated, which a rotation does: a plain
 * LaunchedEffect that fills the form would then overwrite the edits rememberSaveable just
 * restored. Here the load is skipped once it has succeeded, and runs again when [keys] change.
 *
 * [load] returns true on success. The result drives the screen: show the content only past
 * LOADING, and enable save only on LOADED, since the defaults shown before a load or after a
 * failed one would overwrite what is stored.
 */
@Composable
fun rememberLoadOnce(vararg keys: Any?, load: suspend () -> Boolean): LoadState {
    var loaded by rememberSaveable(*keys) { mutableStateOf(false) }
    var failed by remember(*keys) { mutableStateOf(false) }

    LaunchedEffect(*keys) {
        if (loaded) return@LaunchedEffect
        failed = false
        if (load()) loaded = true else failed = true
    }

    return when {
        loaded -> LoadState.LOADED
        failed -> LoadState.FAILED
        else -> LoadState.LOADING
    }
}

/**
 * Runs [onChange] when [value] differs from the one last seen, typically to go back to the
 * first page when a filter changes.
 *
 * Not on the first composition, and not after a rotation: a plain LaunchedEffect keyed on the
 * filters would run in both cases and discard the restored page.
 */
@Composable
fun OnChangedEffect(value: String, onChange: () -> Unit) {
    var lastSeen by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(value) {
        if (lastSeen != null && lastSeen != value) onChange()
        lastSeen = value
    }
}
