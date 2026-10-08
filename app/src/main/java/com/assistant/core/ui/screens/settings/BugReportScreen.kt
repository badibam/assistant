package com.assistant.core.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.bugreport.BugReport
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/**
 * The bug report (docs/design/bug-report.md): a free text the user writes, then the report
 * exactly as it will go, then the share menu. What is shown is what is sent: both are
 * [BugReport.Sources.text] of the same sources and the same free text.
 */
@Composable
fun BugReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var description by rememberSaveable { mutableStateOf("") }
    var sources by remember { mutableStateOf<BugReport.Sources?>(null) }

    // Read once on opening: the lines written while the screen is open (its own) are not taken
    LaunchedEffect(Unit) { sources = BugReport.read(context) }
    val report = sources?.text(description)

    Column(modifier = Modifier.fillMaxSize().padding(vertical = UI.Space.L)) {
        UI.PageHeader(
            title = s.shared("settings_bug_report"),
            subtitle = s.shared("bug_report_purpose"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack
        )
        Spacer(modifier = Modifier.height(UI.Space.L))

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.FormField(
                label = s.shared("bug_report_description"),
                value = description,
                onChange = { description = it },
                fieldType = FieldType.TEXT_LONG,
                required = false
            )
            UI.Text(s.shared("bug_report_cleaning"), TextType.CAPTION)
            UI.Button(type = ButtonType.PRIMARY, onClick = { report?.let { share(context, it, s.shared("bug_report_subject").format(com.assistant.BuildConfig.VERSION_NAME), s.shared("bug_report_send")) } }) {
                UI.Text(s.shared("bug_report_send"), TextType.LABEL)
            }
            UI.Card(type = CardType.DEFAULT, size = Size.M) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L)) {
                    UI.Text(report ?: s.shared("message_loading"), TextType.CAPTION)
                }
            }
        }
    }
}

/** [text] handed to the app the user chooses, under [subject], the chooser titled [title]. */
private fun share(context: Context, text: String, subject: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/**
 * The screen after a crash, shown at start before anything else is set up: the date of the
 * crash, the report, and the way on into the app.
 */
@Composable
fun CrashNoticeScreen(crashTimestamp: Long, onContinue: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var reporting by rememberSaveable { mutableStateOf(false) }

    if (reporting) {
        BugReportScreen(onBack = { reporting = false })
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        UI.PageHeader(title = s.shared("crash_notice_title"))
        UI.Text(s.shared("crash_notice_text").format(com.assistant.core.utils.DateUtils.formatFullDateTime(crashTimestamp)), TextType.BODY)
        UI.Button(type = ButtonType.PRIMARY, onClick = { reporting = true }) {
            UI.Text(s.shared("crash_notice_report"), TextType.LABEL)
        }
        UI.Button(type = ButtonType.DEFAULT, onClick = onContinue) {
            UI.Text(s.shared("crash_notice_continue"), TextType.LABEL)
        }
    }
}
