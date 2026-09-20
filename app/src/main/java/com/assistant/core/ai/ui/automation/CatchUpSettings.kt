package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*

/**
 * What a scheduled automation does about the runs it missed while the app was closed.
 *
 * Two settings, shown together because they answer the same situation and neither follows from
 * the other: a morning briefing three hours late is worthless while a weekly summary is not,
 * and either may want every missed run or only the latest.
 *
 * Shown only when a schedule is configured; the settings mean nothing for a manual or
 * event-driven automation.
 */
@Composable
fun CatchUpSettings(
    automationId: String?,
    windowMinutes: Long?,
    unitChosen: Boolean,
    onWindowChange: (minutes: Long?, unitChosen: Boolean) -> Unit,
    runEveryMissed: Boolean,
    onRunEveryMissedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    val units = remember(context) { CatchUpUnit.labels(context) }
    val unlimitedLabel = remember(context) { s.shared("automation_catch_up_unlimited") }

    // The amount and the unit are what the form reads and writes, and they are the truth while
    // it is open: the stored window is their product, and deriving them back from it would
    // rewrite the field under the fingers -- typing 60 minutes would turn into 1 hour mid-entry.
    // They are seeded when the automation lands, which is when automationId stops being null,
    // and start empty for one that has never had a window: the setting is required and has no
    // sensible default, an hour and several weeks being equally common.
    var amount by rememberSaveable(automationId) {
        mutableStateOf(CatchUpUnit.amountOf(windowMinutes)?.toString() ?: "")
    }
    var unit by rememberSaveable(automationId) {
        mutableStateOf(
            when {
                !unitChosen -> ""
                windowMinutes == null -> unlimitedLabel
                else -> CatchUpUnit.of(windowMinutes).label(context)
            }
        )
    }

    fun publish(newAmount: String, newUnit: String) {
        if (newUnit.isEmpty()) {
            onWindowChange(null, false)
            return
        }
        if (newUnit == unlimitedLabel) {
            onWindowChange(null, true)
            return
        }
        val value = newAmount.toLongOrNull()
        val chosenUnit = CatchUpUnit.byLabel(context, newUnit)
        if (value == null || value <= 0 || chosenUnit == null) {
            // Half-typed input: the parent keeps no window, and the save refuses
            onWindowChange(null, false)
            return
        }
        onWindowChange(value * chosenUnit.minutes, true)
    }

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UI.Text(
                text = s.shared("automation_catch_up_title"),
                type = TextType.SUBTITLE
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (unit != unlimitedLabel) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = s.shared("automation_catch_up_window"),
                            value = amount,
                            onChange = {
                                amount = it
                                publish(it, unit)
                            },
                            fieldType = FieldType.NUMERIC
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormSelection(
                        label = s.shared("automation_catch_up_unit"),
                        options = units + unlimitedLabel,
                        selected = unit,
                        onSelect = {
                            unit = it
                            publish(amount, it)
                        }
                    )
                }
            }

            UI.ToggleField(
                label = s.shared("automation_catch_up_run_every"),
                checked = runEveryMissed,
                onCheckedChange = onRunEveryMissedChange,
                trueLabel = s.shared("automation_catch_up_every_occurrence"),
                falseLabel = s.shared("automation_catch_up_latest_only")
            )
        }
    }
}

/**
 * The units a catch-up window is entered in.
 *
 * Stored in minutes, like the Messages tooltype's validity window, so the two say the same thing
 * the same way. The unit is only how the number is typed and read back.
 */
enum class CatchUpUnit(private val stringKey: String, val minutes: Long) {
    MINUTES("automation_catch_up_minutes", 1L),
    HOURS("automation_catch_up_hours", 60L),
    DAYS("automation_catch_up_days", 1440L),
    WEEKS("automation_catch_up_weeks", 10080L);

    fun label(context: android.content.Context): String =
        Strings.`for`(context = context).shared(stringKey)

    companion object {
        fun labels(context: android.content.Context): List<String> =
            entries.map { it.label(context) }

        fun byLabel(context: android.content.Context, label: String): CatchUpUnit? =
            entries.firstOrNull { it.label(context) == label }

        /** The largest unit that divides the window exactly, so 10080 reads back as one week */
        fun of(minutes: Long): CatchUpUnit =
            entries.last { minutes % it.minutes == 0L }

        fun amountOf(minutes: Long?): Long? = minutes?.let { it / of(it).minutes }
    }
}
