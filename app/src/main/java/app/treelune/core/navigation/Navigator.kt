package app.treelune.core.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.ui.sound.UISignal
import app.treelune.core.ui.sound.UISounds

/**
 * What a stack of places becomes by each move: pure, so that the moves are tested without a
 * phone. The home screen is always at the bottom and never leaves.
 */
object PlaceStack {

    /** [place] laid on top. */
    fun push(stack: List<Place>, place: Place): List<Place> = stack + place

    /** The top taken off, the home screen kept. */
    fun pop(stack: List<Place>): List<Place> = if (stack.size > 1) stack.dropLast(1) else stack

    /** Everything above [index] taken off: back to the place at [index]. */
    fun popTo(stack: List<Place>, index: Int): List<Place> = stack.take((index + 1).coerceIn(1, stack.size))

    /**
     * [place] opened from outside the screen on display. With nothing open (the home screen
     * alone: a notification, the app closed), the stack is rebuilt from its parents, so that back
     * walks up the places that hold it instead of leaving the app; over something open, it is
     * stacked alone, so that one back returns to what was open.
     */
    fun open(stack: List<Place>, place: Place): List<Place> =
        if (stack.size == 1) chain(place) else stack + place

    /** The home screen, then each parent of [place] down to it. */
    fun chain(place: Place): List<Place> =
        generateSequence(place) { it.parent() }.toList().reversed().let { if (it.first() == Place.Home) it else listOf(Place.Home) + it }

    /** Every place of zone [zoneId] taken off: the zone is gone. */
    fun dropZone(stack: List<Place>, zoneId: String): List<Place> = stack.filter { it.zoneId != zoneId }

    /** The index of the place drawn on screen: the top, or the one under the chat. */
    fun baseIndex(stack: List<Place>): Int = stack.indexOfLast { !it.overlay }

    /**
     * The breadcrumb of the place at [index]: the places under it, each with the name of its
     * parent when that parent is not right under it (stacked alone, from the chat), and then the
     * parent of the place itself when it is in the same case.
     *
     * @param name A place's name, null when not known yet
     */
    fun breadcrumb(stack: List<Place>, index: Int, name: (Place) -> String?): Breadcrumb {
        fun orphanOf(i: Int): String? {
            val parent = stack[i].parent() ?: return null
            if (i > 0 && stack[i - 1] == parent) return null
            return name(parent)
        }
        val crumbs = (0 until index).map { i -> Crumb(i, name(stack[i]), orphanOf(i)) }
        return Breadcrumb(crumbs, orphanOf(index))
    }

    /** A place of the breadcrumb: where it is in the stack, its name, its parent's when stacked alone. */
    data class Crumb(val index: Int, val name: String?, val parent: String?)

    /** The places under the one on screen, and the parent of that one when it was stacked alone. */
    data class Breadcrumb(val crumbs: List<Crumb>, val parent: String?)
}

/**
 * The app's stack of places, from the home screen at the bottom to the place on display at the
 * top: the one navigation of the app. A screen opens a place by [push] and leaves by [pop]; the
 * phone's back key pops it too. The sounds of coming and going are played here, never by the
 * gesture that asked: every way to a place sounds the same. The chat, laid over, is the
 * exception: its window plays its own (FullScreenDialog), and the stack stays silent for it.
 *
 * [names] holds each place's name by address, set by the screen that shows it (a zone's name,
 * a tool's) and read by the breadcrumb.
 */
object Navigator {

    val stack = mutableStateListOf<Place>(Place.Home)

    /** A place's name by address, as last shown. */
    val names = mutableStateMapOf<String, String>()

    /** The entry a tool place opens on, by the tool place's address: its tile's waiting entry, a notification's. */
    val openings = mutableStateMapOf<String, EntryToOpen>()

    val top: Place get() = stack.last()

    fun push(place: Place, opening: EntryToOpen? = null) {
        opening?.let { openings[place.address] = it }
        set(PlaceStack.push(stack, place))
        if (!place.overlay) UISounds.play(UISignal.ENTER)
    }

    fun pop() {
        if (stack.size == 1) return
        val leaving = top
        set(PlaceStack.pop(stack))
        if (!leaving.overlay) UISounds.play(UISignal.BACK)
    }

    /** Back to the place at [index] of the stack, from the breadcrumb. */
    fun popTo(index: Int) {
        if (index >= stack.size - 1) return
        set(PlaceStack.popTo(stack, index))
        UISounds.play(UISignal.BACK)
    }

    /** [place] opened from outside the screen on display: a notification, a tool asked for elsewhere. */
    fun open(place: Place, opening: EntryToOpen? = null) {
        opening?.let { openings[place.address] = it }
        set(PlaceStack.open(stack, place))
        if (!place.overlay) UISounds.play(UISignal.ENTER)
    }

    /** The zone [zoneId] deleted: every place of it leaves the stack. */
    fun dropZone(zoneId: String) = set(PlaceStack.dropZone(stack, zoneId))

    /** [place] gone from under the screen (a tool deleted, a zone not found): it leaves, silently. */
    fun drop(place: Place) = set(stack.filter { it != place }.ifEmpty { listOf(Place.Home) })

    fun name(place: Place, name: String) {
        if (names[place.address] != name) names[place.address] = name
    }

    /** The stack as addresses, to save across the process's death. */
    fun addresses(): ArrayList<String> = ArrayList(stack.map { it.address })

    /** The stack saved by [addresses]: written by the same version of the app, so every address reads. */
    fun restore(addresses: List<String>) {
        val places = addresses.map { Place.of(it) }
        require(places.firstOrNull() == Place.Home) { "A saved stack starts with the home screen: $addresses" }
        set(places)
    }

    private fun set(places: List<Place>) {
        // Openings of places no longer in the stack are dropped with them
        val kept = places.map { it.address }.toSet()
        openings.keys.filter { it !in kept }.forEach { openings.remove(it) }
        stack.clear()
        stack.addAll(places)
    }
}
