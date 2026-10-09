package app.treelune.core.guide.ui

import app.treelune.core.ui.chatButtonSpace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.guide.Guide
import app.treelune.core.guide.GuideChapter
import app.treelune.core.guide.GuideChapters
import app.treelune.core.guide.GuidePart
import app.treelune.core.guide.GuidePath
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import kotlinx.coroutines.launch

/**
 * The Guide's page: one scroll, the journey's chapters numbered, then the reference's. Every
 * chapter has the same line in both parts, so that both read as the same kind of thing: its
 * icon, its title, its length, where it stands. Nothing is locked.
 */
@Composable
fun GuideScreen() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val chapters = remember { GuideChapters.all(context) }
    val scroll = rememberScrollState()
    var deeperAt by remember { mutableStateOf<Int?>(null) }
    // Opened at the reference once, after the journey
    LaunchedEffect(deeperAt) {
        val at = deeperAt ?: return@LaunchedEffect
        if (Guide.openAtDeeper) {
            Guide.openAtDeeper = false
            scroll.scrollTo(at)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).chatButtonSpace().padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        UI.PageHeader(title = s.shared("guide_title"), subtitle = s.shared("guide_description"), leftButton = ButtonAction.BACK, onLeftClick = { Navigator.pop() })
        GuidePart.entries.forEach { part ->
            val key = part.key
            Column(
                modifier = Modifier.fillMaxWidth().let { m -> if (part == GuidePart.DEEPER) m.onGloballyPositioned { deeperAt = it.positionInParent().y.toInt() } else m },
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.Card(type = CardType.SECTION_HEADER) {
                    Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.M)) {
                        UI.Text(s.shared("guide_part_$key"), TextType.HEADING)
                        UI.Text(s.shared("guide_part_${key}_help"), TextType.CAPTION)
                    }
                }
                chapters.filter { it.part == part }.forEachIndexed { i, chapter ->
                    ChapterLine(chapter, number = if (part.ordered) i + 1 else null)
                }
            }
        }
    }
}

/** A chapter's line: the same in every part, numbered in the ordered ones. */
@Composable
private fun ChapterLine(chapter: GuideChapter, number: Int?) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Box(modifier = Modifier.padding(horizontal = UI.Space.L)) {
        UI.Card(type = CardType.DEFAULT) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { Navigator.push(Place.Chapter(chapter.id)) }.padding(UI.Space.M),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.M),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UI.Icon(chapter.icon)
                Column(modifier = Modifier.weight(1f)) {
                    UI.Text((number?.let { s.shared("guide_step_number").format(it) + " " } ?: "") + chapter.title(context), TextType.STRONG, maxLines = 2)
                    UI.Text(s.shared("guide_minutes").format(chapter.minutes), TextType.CAPTION)
                }
                UI.Text(state(chapter), TextType.CAPTION, maxLines = 1)
            }
        }
    }
}

/** Where a chapter stands, said on its line. */
@Composable
private fun state(chapter: GuideChapter): String {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val at = Guide.progress?.of(chapter.id)
    return when {
        // A chapter without steps is still to be written (docs/design/user-journey.md, « Référence »)
        !chapter.isTutorial -> s.shared("guide_state_stub")
        at?.done == true -> s.shared("guide_state_done")
        at != null && chapter.isTutorial -> s.shared("guide_state_progress").format(at.step + 1, chapter.steps.size)
        else -> s.shared("guide_state_todo")
    }
}

/**
 * A chapter read in full: its introduction, each step with its explanation and its way from the
 * home screen, its end; doing it on the app, resuming it, starting it again.
 */
@Composable
fun ChapterScreen(id: String, demoInstalled: Boolean) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val chapter = remember(id) { GuideChapters.byId(context, id) }
    val title = chapter.title(context)
    SideEffect { Navigator.name(Place.Chapter(id), title) }
    val at = Guide.progress?.of(chapter.id)
    val inProgress = Guide.progress?.current == chapter.id

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).chatButtonSpace().padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        UI.PageHeader(title = title, icon = chapter.icon, leftButton = ButtonAction.BACK, onLeftClick = { Navigator.pop() })
        Column(modifier = Modifier.padding(horizontal = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(chapter.intro(context), TextType.BODY)
            if (!chapter.isTutorial) {
                UI.Text(s.shared("guide_stub"), TextType.CAPTION)
                return@Column
            }
            if (chapter.usesDemo && !demoInstalled) DemoMissingLine()
            // Doing it: from where it stands, or again once done
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                if (at?.done != true) {
                    UI.Button(type = ButtonType.PRIMARY, state = if (inProgress && Guide.progress?.bandHidden != true) ComponentState.DISABLED else ComponentState.NORMAL,
                        onClick = { Guide.begin(chapter, demoInstalled); Navigator.pop() }) {
                        UI.Text(s.shared(if (at != null) "guide_resume" else "guide_do_interactive"), TextType.LABEL)
                    }
                }
                if (at != null) {
                    UI.Button(type = ButtonType.DEFAULT, onClick = { Guide.restart(chapter, demoInstalled); Navigator.pop() }) {
                        UI.Text(s.shared("guide_restart"), TextType.LABEL)
                    }
                }
            }
            UI.Card(type = CardType.SECTION_HEADER) {
                Box(modifier = Modifier.fillMaxWidth().padding(UI.Space.M)) { UI.Text(s.shared("guide_steps"), TextType.HEADING) }
            }
            chapter.steps.forEach { step ->
                Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                    UI.Text(s.shared("guide_step_number").format(step.number) + " " + chapter.goal(context, step), TextType.STRONG)
                    UI.Text(chapter.text(context, step), TextType.BODY)
                    // The way from the home screen, for a place known without the steps before
                    Guide.target(chapter, step)?.let { place ->
                        LaunchedEffect(place) { names(context, place) }
                        val gestures = GuidePath.gestures(listOf(Place.Home), false, place, { Navigator.names[it.address] }) { key, args -> s.shared(key).format(*args) }
                        if (gestures.isNotEmpty()) UI.Text(s.shared("guide_path_from_home").format(GuidePath.sentence(gestures, s.shared("guide_path_then"))), TextType.CAPTION)
                    }
                }
            }
            chapter.outro(context)?.let { UI.Text(it, TextType.BODY) }
        }
    }
}

/** A tutorial that uses the demo, while it is not installed: said, with its reinstall. */
@Composable
private fun DemoMissingLine() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    UI.Text(s.shared("guide_needs_demo"), TextType.WARNING)
    UI.Button(type = ButtonType.DEFAULT, state = if (working) ComponentState.LOADING else ComponentState.NORMAL, onClick = {
        if (working) return@Button
        working = true
        scope.launch {
            val result = Coordinator(context).processUserAction("demo.install", emptyMap())
            working = false
            UI.Toast(context, if (result.isSuccess) s.shared("demo_installed") else result.error ?: s.shared("error_operation_failed"), Duration.LONG)
        }
    }) {
        UI.Text(s.shared("guide_reinstall_demo"), TextType.LABEL)
    }
}

/**
 * The first-launch screen, once: the app's promise, the demo waiting, and two ways in — the
 * first tutorial, or the app alone, the Guide's book then marked on the home screen.
 */
@Composable
fun WelcomeScreen(demoInstalled: Boolean) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val first = remember { GuideChapters.journey(context).first() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UI.Space.XL),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UI.Icon("trees", size = androidx.compose.ui.unit.Dp(64f))
        UI.Text(s.shared("app_name"), TextType.TITLE)
        UI.Text(s.shared("guide_welcome_promise"), TextType.BODY, fillMaxWidth = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (demoInstalled) UI.Text(s.shared("guide_welcome_demo"), TextType.CAPTION, fillMaxWidth = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        UI.Button(type = ButtonType.PRIMARY, onClick = {
            Guide.welcomeDone()
            Guide.begin(first, demoInstalled)
        }) {
            UI.Text(s.shared("guide_welcome_start").format(first.title(context)), TextType.LABEL)
        }
        UI.Button(type = ButtonType.DEFAULT, onClick = {
            Guide.welcomeDone()
            UI.Toast(context, s.shared("guide_welcome_where"), Duration.LONG)
        }) {
            UI.Text(s.shared("guide_welcome_explore"), TextType.LABEL)
        }
    }
}

/**
 * What comes after a tutorial: during the journey, its next tutorial; after it, the Guide's
 * reference, in the order one likes. « Later » says where the rest waits.
 */
@Composable
fun GuideEndDialog(chapter: GuideChapter, demoInstalled: Boolean) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val next = Guide.nextOfJourney()
    val close = { Guide.ended = null }
    UI.ConfirmDialog(
        title = s.shared("guide_end_title").format(chapter.title(context)),
        message = (chapter.outro(context)?.let { "$it\n\n" } ?: "") + (if (next == null) s.shared("guide_end_journey_done") else ""),
        confirmText = if (next != null) s.shared("guide_end_continue").format(next.title(context)) else s.shared("guide_end_open"),
        cancelText = s.shared("guide_end_later"),
        onConfirm = {
            close()
            if (next != null) Guide.begin(next, demoInstalled)
            else {
                Guide.openAtDeeper = true
                if (Navigator.top != Place.Guide) Navigator.push(Place.Guide)
            }
        },
        onDismiss = {
            close()
            UI.Toast(context, s.shared("guide_end_where"), Duration.LONG)
        }
    )
}

/**
 * A tutorial's start: what it is about, and where it goes on — the band at the bottom of the
 * screen, which says the step to do and unfolds to explain it. Later hides it, saying where it waits.
 */
@Composable
fun GuideStartDialog(chapter: GuideChapter) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    UI.ConfirmDialog(
        title = chapter.title(context),
        message = chapter.intro(context) + "\n\n" + s.shared("guide_start_band"),
        confirmText = s.shared("guide_start_go"),
        cancelText = s.shared("guide_end_later"),
        onConfirm = { Guide.introducing = null },
        onDismiss = {
            Guide.introducing = null
            Guide.hide()
            UI.Toast(context, s.shared("guide_band_hidden"), Duration.LONG)
        }
    )
}

/** A tutorial asked for while the demo it begins in is not installed: reinstall it, or skip those steps. */
@Composable
fun GuideDemoMissingDialog(chapter: GuideChapter) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    UI.ConfirmDialog(
        title = s.shared("guide_demo_missing_title"),
        message = s.shared("guide_demo_missing_message"),
        confirmText = s.shared("guide_reinstall_demo"),
        cancelText = s.shared("guide_demo_skip"),
        onConfirm = {
            Guide.demoMissingFor = null
            scope.launch {
                val result = Coordinator(context).processUserAction("demo.install", emptyMap())
                if (result.isSuccess) Guide.begin(chapter, demoInstalled = true)
                else UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
            }
        },
        onDismiss = { Guide.skipDemo(chapter) }
    )
}
