package com.assistant.core.selection

import com.assistant.core.fields.FieldType
import com.assistant.core.ui.components.RelativePeriod
import com.assistant.core.ui.components.getPeriodEndTimestamp
import com.assistant.core.ui.components.resolveRelativePeriod
import com.assistant.core.utils.AppConfigManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a relative date resolves against: the reference its context gives (an automation's
 * scheduled time, the end of a goal's attempt, the instant a variable is read at, the clock where
 * there is none), and the user's calendar (timezone, the hour a day starts, the first day of a
 * week).
 */
data class TimeResolver(
    val reference: Long,
    val zone: ZoneId,
    val dayStartHour: Int,
    val weekStartDay: String
) {

    /** The instant [point] designates, in milliseconds; an end is the last millisecond of its period. */
    fun instant(point: TimePoint): Long = when (point) {
        is TimePoint.Fixed -> (point.value as? Number)?.toLong()
            ?: throw IllegalArgumentException("a fixed instant is milliseconds, not ${point.value}")
        TimePoint.Now -> reference
        is TimePoint.Relative -> {
            val start = resolveRelativePeriod(RelativePeriod(point.offset, point.unit), reference, dayStartHour, weekStartDay, zone)
            if (point.edge == Edge.START) start.timestamp else getPeriodEndTimestamp(start, dayStartHour, weekStartDay, zone)
        }
    }

    /**
     * The day [point] designates, "2026-09-15". A period's start is the day it starts in; its end,
     * the eve of the day the next period starts in: with days starting at 4:00, a day runs to 3:59
     * the day after, and is still one day.
     */
    fun day(point: TimePoint): String = when (point) {
        is TimePoint.Fixed -> point.value as? String
            ?: throw IllegalArgumentException("a fixed day is \"yyyy-MM-dd\", not ${point.value}")
        TimePoint.Now -> dayOf(reference).toString()
        is TimePoint.Relative -> {
            val start = resolveRelativePeriod(RelativePeriod(point.offset, point.unit), reference, dayStartHour, weekStartDay, zone)
            val first = dayOf(start.timestamp)
            if (point.edge == Edge.START) first.toString()
            else {
                val next = resolveRelativePeriod(RelativePeriod(point.offset + 1, point.unit), reference, dayStartHour, weekStartDay, zone)
                maxOf(first, dayOf(next.timestamp).minusDays(1)).toString()
            }
        }
    }

    /** [point] in the stored form of a field of [type]: a day for a DATE, milliseconds otherwise. */
    fun resolve(point: TimePoint, type: FieldType): Any = if (type == FieldType.DATE) day(point) else instant(point)

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    companion object {
        /** Against [reference], with the calendar the user set. */
        fun at(reference: Long) = TimeResolver(
            reference = reference,
            zone = AppConfigManager.getDateTimeConfig().getZoneId(),
            dayStartHour = AppConfigManager.getDayStartHour(),
            weekStartDay = AppConfigManager.getWeekStartDay()
        )
    }
}
