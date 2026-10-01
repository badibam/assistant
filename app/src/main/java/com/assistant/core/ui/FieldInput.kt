package com.assistant.core.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.assistant.core.validation.FieldLimits

/**
 * What a text input does with its field type, whichever theme draws it: the keyboard it opens, and
 * how many characters it takes. A theme draws the input; these are not its to decide.
 */
object FieldInput {

    /** The keyboard a field of [fieldType] opens. */
    fun keyboardOptions(fieldType: FieldType): KeyboardOptions = when (fieldType) {
        FieldType.TEXT -> KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrect = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        )
        FieldType.TEXT_MEDIUM -> KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        )
        FieldType.TEXT_LONG, FieldType.TEXT_UNLIMITED -> KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Default
        )
        FieldType.NUMERIC -> KeyboardOptions(
            keyboardType = KeyboardType.Number,
            autoCorrect = false,
            imeAction = ImeAction.Next
        )
        FieldType.EMAIL -> KeyboardOptions(
            keyboardType = KeyboardType.Email,
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Next
        )
        FieldType.PASSWORD -> KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done
        )
        FieldType.SEARCH -> KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrect = true,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        )
    }

    /** How many characters a field of [fieldType] takes. */
    fun maxLength(fieldType: FieldType): Int = when (fieldType) {
        FieldType.TEXT -> FieldLimits.SHORT_LENGTH
        FieldType.TEXT_MEDIUM -> FieldLimits.MEDIUM_LENGTH
        FieldType.TEXT_LONG -> FieldLimits.LONG_LENGTH
        FieldType.TEXT_UNLIMITED -> FieldLimits.UNLIMITED_LENGTH
        FieldType.NUMERIC, FieldType.EMAIL, FieldType.PASSWORD, FieldType.SEARCH -> FieldLimits.UNLIMITED_LENGTH
    }

    /** [onChange], refusing a value longer than a field of [fieldType] takes. */
    fun limited(fieldType: FieldType, onChange: (TextFieldValue) -> Unit): (TextFieldValue) -> Unit {
        val max = maxLength(fieldType)
        return if (max < Int.MAX_VALUE) { value -> if (value.text.length <= max) onChange(value) } else onChange
    }
}
