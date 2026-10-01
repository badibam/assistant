package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.fields.FieldType
import com.assistant.core.selection.Edge
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.TimePoint
import com.assistant.core.selection.TimeResolver
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DateUtils
import com.assistant.core.ui.FieldType as UIFieldType

/** The four ways an instant is chosen. */
private enum class InstantMode { CUSTOM, RELATIVE, NOW, NONE }

/**
 * The one input of a date or an instant in the app (docs/design/missing-tools.md, « Le temps
 * relatif »): an entry's DATE or DATETIME field, each bound of a period.
 *
 * It offers a custom date, always absolute, with the time for a DATETIME; a relative date, a unit
 * and an offset ("the day before") with its moment, the start or the end of that period; now;
 * and, for a bound, no limit.
 *
 * With a reference (an automation's scheduled time, the instant a variable is read at), a
 * relative date and now are stored as they are, resolved each time against it, and now is
 * labelled "the moment itself". Without one (a chat, an entry), the clock is the reference at the
 * moment of the choice: what is stored is the date obtained, the relative choice being only the
 * label of a fixed period, shown with the date it gives.
 *
 * @param value A TimePoint, null for none (no limit, or no answer)
 * @param precision DATE (a day, "2026-09-15") or DATETIME (milliseconds)
 * @param hasReference Whether the context resolves relative dates later, against its reference
 * @param edge The moment a relative date starts with: the start for a period's start, the end for its end
 * @param allowNone Whether "no limit" is offered, for a bound
 * @param required Whether the value must be answered; an optional one chosen can be emptied
 */
@Composable
fun InstantPicker(
    label: String,
    value: TimePoint?,
    onChange: (TimePoint?) -> Unit,
    precision: FieldType,
    hasReference: Boolean,
    edge: Edge = Edge.START,
    allowNone: Boolean = false,
    required: Boolean = false
) {
    require(precision == FieldType.DATE || precision == FieldType.DATETIME) { "an instant is a DATE or a DATETIME, not $precision" }
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // An hour is no unit for a day
    val units = if (precision == FieldType.DATE) PeriodType.entries - PeriodType.HOUR else PeriodType.entries

    // The mode and the relative date are the picker's own: without a reference, what is stored
    // is a fixed date, which no longer says how it was chosen
    var mode by rememberSaveable {
        mutableStateOf(when (value) {
            is TimePoint.Relative -> InstantMode.RELATIVE
            TimePoint.Now -> InstantMode.NOW
            null -> if (allowNone) InstantMode.NONE else InstantMode.CUSTOM
            is TimePoint.Fixed -> InstantMode.CUSTOM
        })
    }
    val initial = value as? TimePoint.Relative
    var unit by rememberSaveable { mutableStateOf(initial?.unit ?: PeriodType.DAY) }
    var offset by rememberSaveable { mutableStateOf(initial?.offset ?: 0) }
    var moment by rememberSaveable { mutableStateOf(initial?.edge ?: edge) }

    /** [point] as stored: as it is with a reference, resolved against the clock without one. */
    fun emit(point: TimePoint?) = onChange(
        if (point == null || hasReference || point is TimePoint.Fixed) point else atClock(point, precision)
    )
    fun emitRelative() = emit(TimePoint.Relative(unit, offset, moment))

    val modes = buildList {
        add(InstantMode.CUSTOM to s.shared("instant_mode_custom"))
        add(InstantMode.RELATIVE to s.shared("instant_mode_relative"))
        add(InstantMode.NOW to s.shared(if (hasReference) "instant_mode_reference" else "instant_mode_now"))
        if (allowNone) add(InstantMode.NONE to s.shared("instant_mode_none"))
    }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.FormSelection(
            label = label,
            options = modes.map { it.second },
            selected = modes.first { it.first == mode }.second,
            onSelect = { chosen ->
                mode = modes.first { it.second == chosen }.first
                when (mode) {
                    // A custom date starts at what the value stood for, the clock resolving it
                    InstantMode.CUSTOM -> onChange(value?.let { atClock(it, precision) })
                    InstantMode.RELATIVE -> emitRelative()
                    InstantMode.NOW -> emit(TimePoint.Now)
                    InstantMode.NONE -> onChange(null)
                }
            },
            required = required
        )

        when (mode) {
            InstantMode.CUSTOM -> CustomDate(value as? TimePoint.Fixed, precision, clearable = !required && !allowNone, onChange = onChange)
            InstantMode.RELATIVE -> {
                RelativeDate(unit, offset, moment, units, s,
                    onUnit = { unit = it; offset = 0; emitRelative() },
                    onOffset = { offset = it; emitRelative() },
                    onMoment = { moment = it; emitRelative() }
                )
                if (!hasReference) Resolved(value, precision, s)
            }
            InstantMode.NOW -> if (!hasReference) Resolved(value, precision, s)
            InstantMode.NONE -> Unit
        }
    }
}

/**
 * A period as two instants, from and until, each optional. With a reference, it is named once,
 * above ("Relative to: the instant read"): the labels of relative dates stay the same everywhere.
 *
 * @param reference The name of the reference the context gives, null for none (the clock)
 */
@Composable
fun PeriodPicker(
    period: EntryPeriod,
    onChange: (EntryPeriod) -> Unit,
    precision: FieldType,
    reference: String?
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        reference?.let { UI.Text(text = s.shared("instant_reference").format(it), type = TextType.CAPTION) }
        InstantPicker(
            label = s.shared("period_from"),
            value = period.start,
            onChange = { onChange(period.copy(start = it)) },
            precision = precision,
            hasReference = reference != null,
            edge = Edge.START,
            allowNone = true
        )
        InstantPicker(
            label = s.shared("period_until"),
            value = period.end,
            onChange = { onChange(period.copy(end = it)) },
            precision = precision,
            hasReference = reference != null,
            edge = Edge.END,
            allowNone = true
        )
    }
}

/** [point] resolved against the clock, in the stored form of [precision]. */
private fun atClock(point: TimePoint, precision: FieldType): TimePoint.Fixed =
    point as? TimePoint.Fixed ?: TimePoint.Fixed(TimeResolver.at(System.currentTimeMillis()).resolve(point, precision))

/** A fixed date in words, as the user reads it: "15/09/2026", or with its time. */
private fun shown(fixed: TimePoint.Fixed, precision: FieldType): String = when (val v = fixed.value) {
    is Number -> if (precision == FieldType.DATE) DateUtils.formatDateForDisplay(v.toLong()) else DateUtils.formatFullDateTime(v.toLong())
    is String -> DateUtils.parseIso8601Date(v)?.let { DateUtils.formatDateForDisplay(it) } ?: v
    else -> v.toString()
}

/** The date a choice gave, without a reference: what is stored. */
@Composable
private fun Resolved(value: TimePoint?, precision: FieldType, s: StringsContext) {
    val fixed = value as? TimePoint.Fixed ?: return
    UI.Text(text = s.shared("instant_resolved").format(shown(fixed, precision)), type = TextType.CAPTION)
}

/** A unit, an offset stepped by arrows ("the day before"), and the moment of that period. */
@Composable
private fun RelativeDate(
    unit: PeriodType,
    offset: Int,
    moment: Edge,
    units: List<PeriodType>,
    s: StringsContext,
    onUnit: (PeriodType) -> Unit,
    onOffset: (Int) -> Unit,
    onMoment: (Edge) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UI.ActionButton(action = ButtonAction.LEFT, display = ButtonDisplay.ICON, size = Size.M, onClick = { onOffset(offset - 1) })
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            UI.Text(text = generateRelativePeriodLabel(RelativePeriod(offset, unit), s), type = TextType.BODY)
        }
        UI.ActionButton(action = ButtonAction.RIGHT, display = ButtonDisplay.ICON, size = Size.M, onClick = { onOffset(offset + 1) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        fun unitLabel(u: PeriodType) = s.shared("period_type_${u.name.lowercase()}")
        Box(modifier = Modifier.weight(1f)) {
            UI.FormSelection(label = s.shared("instant_unit"), options = units.map(::unitLabel), selected = unitLabel(unit),
                onSelect = { l -> onUnit(units.first { unitLabel(it) == l }) }, required = true)
        }
        val moments = listOf(Edge.START to s.shared("instant_edge_start"), Edge.END to s.shared("instant_edge_end"))
        Box(modifier = Modifier.weight(1f)) {
            UI.FormSelection(label = s.shared("instant_edge"), options = moments.map { it.second }, selected = moments.first { it.first == moment }.second,
                onSelect = { l -> onMoment(moments.first { it.second == l }.first) }, required = true)
        }
    }
}

/**
 * A date picked on the calendar, with its time for a DATETIME: a day ("2026-09-15") or
 * milliseconds. An unreadable pick leaves the value as it was, rather than recording today.
 */
@Composable
private fun CustomDate(fixed: TimePoint.Fixed?, precision: FieldType, clearable: Boolean, onChange: (TimePoint?) -> Unit) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val millis: Long? = when (val v = fixed?.value) {
        is Number -> v.toLong()
        is String -> DateUtils.parseIso8601Date(v)
        else -> null
    }
    val displayDate = millis?.let { DateUtils.formatDateForDisplay(it) } ?: ""
    val displayTime = if (precision == FieldType.DATETIME) millis?.let { DateUtils.formatTimeForDisplay(it) } ?: "" else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            UI.FormField(label = "", value = displayDate, onChange = {}, fieldType = UIFieldType.TEXT, required = false,
                readonly = true, onClick = { showDatePicker = true })
        }
        if (precision == FieldType.DATETIME) {
            Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = "", value = displayTime, onChange = {}, fieldType = UIFieldType.TEXT, required = false,
                    readonly = true, onClick = { showTimePicker = true })
            }
        }
        if (clearable && fixed != null) {
            UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { onChange(null) })
        }
    }

    if (showDatePicker) {
        UI.DatePicker(
            selectedDate = displayDate.ifEmpty { DateUtils.getTodayFormatted() },
            onDateSelected = { date ->
                if (precision == FieldType.DATE) {
                    DateUtils.parseDateForFilter(date)?.let { onChange(TimePoint.Fixed(DateUtils.timestampToIso8601Date(it))) }
                } else {
                    DateUtils.combineDateTime(date, displayTime.ifEmpty { "00:00" })?.let { onChange(TimePoint.Fixed(it)) }
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        UI.TimePicker(
            selectedTime = displayTime.ifEmpty { DateUtils.getCurrentTimeFormatted() },
            onTimeSelected = { time ->
                DateUtils.combineDateTime(displayDate.ifEmpty { DateUtils.getTodayFormatted() }, time)?.let { onChange(TimePoint.Fixed(it)) }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
