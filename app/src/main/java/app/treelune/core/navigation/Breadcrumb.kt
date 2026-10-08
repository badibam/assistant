package app.treelune.core.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * The breadcrumb of the place on screen, null outside a place of the stack (the home screen has
 * none: nothing is before it). Read by the page header, which draws it over its title.
 */
val LocalBreadcrumb = compositionLocalOf<PlaceStack.Breadcrumb?> { null }

/**
 * One line over a page's title: the places before it, each touched to go back to it, and the
 * chat's button at the end, so that the chat opens from every place. The line never wraps: too
 * long, it shows its end, the last steps, and scrolls to its beginning. A place stacked without
 * its parent right under it carries the parent's name: « Sorties (Course) ».
 */
@Composable
fun BreadcrumbLine(breadcrumb: PlaceStack.Breadcrumb) {
    val scroll = rememberScrollState()
    // The end shown: the places just before this one
    LaunchedEffect(breadcrumb) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = UI.Space.L),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.XS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            breadcrumb.crumbs.forEach { crumb ->
                Box(modifier = Modifier.clickable { Navigator.popTo(crumb.index) }) {
                    UI.Text(crumb.name.orEmpty() + (crumb.parent?.let { " ($it)" } ?: ""), TextType.CAPTION, maxLines = 1)
                }
                UI.Icon("chevron-right", size = 16.dp)
            }
            breadcrumb.parent?.let { UI.Text("($it)", TextType.CAPTION, maxLines = 1) }
        }
        if (Navigator.top != Place.Chat) {
            UI.ActionButton(action = ButtonAction.AI_CHAT, display = ButtonDisplay.ICON, size = Size.S) {
                Navigator.push(Place.Chat)
            }
        }
    }
}
