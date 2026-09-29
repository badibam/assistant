package com.assistant.core.tools

import androidx.compose.runtime.saveable.Saver

/**
 * What a tool's screen opens at once, as its tool type understands opening it: one of its
 * entries (touched on its tile, or the oldest waiting when the tile was touched), or a new one
 * (a tile's button that writes one: a journal entry, a note, a record).
 */
sealed interface EntryToOpen {
    data class Existing(val id: String) : EntryToOpen
    data object New : EntryToOpen

    companion object {
        /** Kept across recreation as "new" or "entry:" and the id. */
        val Saver: Saver<EntryToOpen?, String> = Saver(
            save = { when (it) { New -> "new"; is Existing -> "entry:${it.id}"; null -> null } },
            restore = { if (it == "new") New else Existing(it.removePrefix("entry:")) }
        )
    }
}
