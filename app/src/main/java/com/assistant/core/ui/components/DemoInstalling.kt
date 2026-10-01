package com.assistant.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.demo.DemoProgress
import com.assistant.core.strings.Strings
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/**
 * The screen the app shows at its start while the demo is checked: the wheel alone, and once an
 * install runs, what it does and how far it is (DemoProgress).
 */
@Composable
fun DemoInstalling() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val step by DemoProgress.step.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
            UI.LoadingIndicator()
            step?.let { current ->
                UI.Text(s.shared("demo_installing"), TextType.SUBTITLE)
                UI.Text(when (current.phase) {
                    DemoProgress.Phase.ZONES -> s.shared("demo_phase_zones")
                    DemoProgress.Phase.TOOLS -> s.shared("demo_phase_tools").format(current.done.toString(), current.total.toString())
                    DemoProgress.Phase.ENTRIES -> s.shared("demo_phase_entries").format(current.done.toString(), current.total.toString())
                    DemoProgress.Phase.GOALS -> s.shared("demo_phase_goals")
                    DemoProgress.Phase.AUTOMATIONS -> s.shared("demo_phase_automations")
                }, TextType.BODY)
            }
        }
    }
}
