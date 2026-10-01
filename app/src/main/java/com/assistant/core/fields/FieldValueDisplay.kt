package com.assistant.core.fields

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.assistant.core.strings.Strings
import com.assistant.core.themes.TagColor
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI

/**
 * Shows one value the way its field type shows it, wherever it appears: tool screens, history,
 * communication modules, validation requests, command details.
 *
 * Most types are their short text form (FieldDefinition.formatValue). Three draw more than text:
 * a SCALE is a gauge between its bounds, a colored CHOICE is tags, a ranking is its options
 * numbered in order.
 *
 * @param fieldDef The field the value belongs to
 * @param value The stored value; null shows "no value"
 * @param context Android context for strings
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldValue(
    fieldDef: FieldDefinition,
    value: Any?,
    context: Context
) {
    val s = Strings.`for`(context = context)

    if (value == null) {
        UI.Text(text = s.shared("label_no_value"), type = TextType.CAPTION)
        return
    }

    when (fieldDef.type) {
        FieldType.SCALE -> {
            val number = (value as? Number)?.toDouble()
            val min = (fieldDef.config?.get("min") as? Number)?.toDouble()
            val max = (fieldDef.config?.get("max") as? Number)?.toDouble()
            // A scale's config always has both bounds; a value that is not a number is shown as
            // stored, so the reader sees what is actually recorded.
            if (number == null || min == null || max == null || max <= min) {
                UI.Text(text = value.toString(), type = TextType.BODY)
                return
            }
            val step = (fieldDef.config?.get("step") as? Number)?.toDouble() ?: 1.0
            val decimals = com.assistant.core.ui.SliderSteps.decimals(min, step)
            fun shown(n: Double) = String.format(java.util.Locale.getDefault(), "%.${decimals}f", n)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
            ) {
                UI.Text(text = shown(number), type = TextType.BODY)
                UI.Gauge(fraction = ((number - min) / (max - min)).toFloat())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    UI.Text(
                        text = (fieldDef.config?.get("min_label") as? String) ?: shown(min),
                        type = TextType.CAPTION
                    )
                    UI.Text(
                        text = (fieldDef.config?.get("max_label") as? String) ?: shown(max),
                        type = TextType.CAPTION
                    )
                }
            }
        }

        FieldType.CHOICE -> {
            val settings = ChoiceSettings.fromConfig(fieldDef.config)
            val chosen = if (settings.shape.isList) (value as? List<*>)?.map { it.toString() } ?: emptyList()
                         else listOf(value.toString())

            when {
                settings.shape == ChoiceShape.ORDERED -> Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                    chosen.forEachIndexed { index, option ->
                        UI.Text(
                            text = s.shared("field_choice_rank").format((index + 1).toString(), settings.labelOf(option)),
                            type = TextType.BODY
                        )
                    }
                }

                // A choice with colors shows every value as a tag; one added by an open
                // vocabulary, which has no color yet, shows in grey.
                settings.colors.isNotEmpty() -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.XS),
                    verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                ) {
                    chosen.forEach { option ->
                        UI.Tag(text = settings.labelOf(option), color = settings.colors[option] ?: TagColor.GREY)
                    }
                }

                else -> UI.Text(text = fieldDef.formatValue(value, context), type = TextType.BODY)
            }
        }

        FieldType.REFERENCE -> ReferenceValue(value, context)

        else -> UI.Text(text = fieldDef.formatValue(value, context), type = TextType.BODY)
    }
}
