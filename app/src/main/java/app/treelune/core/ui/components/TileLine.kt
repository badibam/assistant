package app.treelune.core.ui.components

import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * An item of a tile's body (TileGrid), in three places: [leading] on the left (a checkbox, an
 * icon), the text in the middle, [trailing] on the right (a value, buttons, or both). Both sides
 * are optional and keep the size they ask for; the middle takes what is left, a space apart from
 * each, and is the only part an ellipsis cuts.
 *
 * The middle is [text] on up to [maxLines] lines, two by default, the most a slot of the grid
 * holds with air around it; or, given [secondary], [text] on one line and [secondary] under it,
 * in the dim caption.
 * Everything is centred vertically on the slot.
 */
@Composable
fun TileLine(
    text: String,
    modifier: Modifier = Modifier,
    type: TextType = TextType.BODY,
    secondary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    maxLines: Int = MAX_LINES
) = TileLine(modifier, leading, trailing) {
    if (secondary == null) UI.Text(text, type, maxLines = maxLines)
    else Column {
        UI.Text(text, type, maxLines = 1)
        UI.Text(secondary, TextType.CAPTION, maxLines = 1)
    }
}

/**
 * The same item with its own [middle], for what is not a plain text (a tag): it is given the
 * width the sides leave and must keep to two lines itself.
 */
@Composable
fun TileLine(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    middle: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        // Weighted, so measured after the sides: they keep their size, the text takes the rest
        Box(modifier = Modifier.weight(1f)) { middle() }
        trailing?.invoke(this)
    }
}

/** The most lines an item's text takes: what a slot of the grid holds with air around it. */
private const val MAX_LINES = 2
