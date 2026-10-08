package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.treelune.core.coordinator.LongOperation
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * The screen the app shows at its start while the demo is checked: the wheel alone, and once an
 * install runs, what it does and how far it is.
 */
@Composable
fun DemoInstalling() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LongOperationProgress()
    }
}

/**
 * The wheel, and under it, while a long operation runs (LongOperation), what it is and its step:
 * on the screen that started it.
 */
@Composable
fun LongOperationProgress() {
    val running by LongOperation.running.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
        UI.LoadingIndicator()
        running?.let { current ->
            UI.Text(current.label, TextType.SUBTITLE)
            current.step?.let { UI.Text(it, TextType.BODY) }
        }
    }
}

/** A thin band over every screen while a long operation runs: what it is, and its step. Nothing otherwise. */
@Composable
fun LongOperationBar() {
    val running by LongOperation.running.collectAsState()
    val current = running ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = UI.Space.L, vertical = UI.Space.XS),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UI.LoadingIndicator(Size.XS)
        UI.Text(listOfNotNull(current.label, current.step).joinToString(" · "), TextType.CAPTION)
    }
}
