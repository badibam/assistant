package app.treelune.core.guide.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.guide.Guide
import app.treelune.core.guide.GuidePath
import app.treelune.core.guide.StepKind
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.navigation.PlaceStack
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * The band of the tutorial in progress, under every screen and in the chat. Folded, two lines and
 * an arrow: the tutorial's title, then « 2/6 · its step ». Unfolded, the step's explanation, the
 * way to its place from the screen on display, Next on a step to read, and hiding the tutorial.
 * No button takes the user to the place: the gestures are theirs to make.
 */
@Composable
fun GuideBand() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val progress = Guide.progress ?: return
    if (!progress.welcomeSeen || progress.bandHidden) return
    val (chapter, step) = Guide.current ?: return
    var open by rememberSaveable { mutableStateOf(false) }

    val target = Guide.target(chapter, step)
    // The names of the places on the way: a tool not visited yet is read once
    LaunchedEffect(target) { target?.let { names(context, it) } }
    val stack = Navigator.stack.toList()
    val chatOpen = Navigator.top == Place.Chat
    val open_ = stack.take(PlaceStack.baseIndex(stack) + 1)
    val gestures = target?.let { place ->
        GuidePath.gestures(open_, chatOpen, place, { Navigator.names[it.address] }) { key, args -> s.shared(key).format(*args) }
    }
    val here = target != null && gestures.isNullOrEmpty()

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = UI.Space.S, vertical = UI.Space.XS)) {
        UI.Card(type = CardType.DEFAULT, size = app.treelune.core.ui.Size.S) {
            Column(
                modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(UI.Space.M),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    Column(modifier = Modifier.weight(1f)) {
                        UI.Text(chapter.title(context), TextType.CAPTION, maxLines = 1)
                        UI.Text(s.shared("guide_band_step").format(step.number, chapter.steps.size, chapter.goal(context, step)), TextType.STRONG, maxLines = 2)
                    }
                    UI.Icon(if (open) "chevron-down" else "chevron-up", contentDescription = s.shared(if (open) "guide_band_collapse" else "guide_band_expand"))
                }
                if (open) {
                    UI.Text(chapter.text(context, step), TextType.BODY)
                    when {
                        here -> UI.Text(s.shared("guide_band_here"), TextType.CAPTION)
                        gestures != null -> UI.Text(s.shared("guide_band_from_here").format(GuidePath.sentence(gestures, s.shared("guide_path_then"))), TextType.CAPTION)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                        if (step.kind == StepKind.READ) {
                            val ready = target == null || here
                            UI.Button(type = ButtonType.PRIMARY, state = if (ready) app.treelune.core.ui.ComponentState.NORMAL else app.treelune.core.ui.ComponentState.DISABLED, onClick = { Guide.next() }) {
                                UI.Text(s.shared("guide_band_next"), TextType.LABEL)
                            }
                        }
                        UI.Button(type = ButtonType.DEFAULT, onClick = {
                            Guide.hide()
                            UI.Toast(context, s.shared("guide_band_hidden"), Duration.LONG)
                        }) {
                            UI.Text(s.shared("guide_band_hide"), TextType.LABEL)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The names of the places on the way to [place] that the stack has not named yet: a tool, read
 * by its id. Zones are named as the home screen reads them.
 */
suspend fun names(context: android.content.Context, place: Place) {
    val coordinator = Coordinator(context)
    PlaceStack.chain(place).filterIsInstance<Place.Tool>().filter { Navigator.names[it.address] == null }.forEach { tool ->
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to tool.id))
        ((result.data?.get("tool_instance") as? Map<*, *>)?.get("name") as? String)?.let { Navigator.name(tool, it) }
    }
    PlaceStack.chain(place).filterIsInstance<Place.SettingsPage>().forEach {
        Navigator.name(it, Strings.`for`(context = context).shared("settings_${it.id}"))
    }
}
