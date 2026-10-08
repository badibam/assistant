package app.treelune.core.imports

import app.treelune.core.fields.FieldType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** What a cell gives, read in a writing: the value in its field's stored form, or why not. */
sealed interface CellRead {
    data class Value(val value: Any) : CellRead
    data class Unreadable(val reason: String) : CellRead
}

/**
 * One way a field type is written in a file (docs/DATA.md, « Import »): its name, the type it
 * writes, and how a cell reads. The list is closed: a writing absent from it is accepted nowhere,
 * and joins its type the day a real file carries it. An empty cell never reaches a writing: it is
 * no answer.
 */
enum class Writing(val type: FieldType) {
    TEXT(FieldType.TEXT),
    DECIMAL_POINT(FieldType.NUMERIC), DECIMAL_COMMA(FieldType.NUMERIC),
    SCALE_POINT(FieldType.SCALE), SCALE_COMMA(FieldType.SCALE),
    YES_NO(FieldType.BOOLEAN), TRUE_FALSE(FieldType.BOOLEAN), ONE_ZERO(FieldType.BOOLEAN), X_EMPTY(FieldType.BOOLEAN),
    DAY_MONTH_YEAR(FieldType.DATE), MONTH_DAY_YEAR(FieldType.DATE), ISO_DATE(FieldType.DATE),
    ISO_DATETIME(FieldType.DATETIME), DAY_MONTH_YEAR_TIME(FieldType.DATETIME), MONTH_DAY_YEAR_TIME(FieldType.DATETIME),
    HOURS_MINUTES(FieldType.TIME),
    DURATION_H_MIN(FieldType.DURATION), DURATION_MIN_S(FieldType.DURATION), DURATION_H_MIN_S(FieldType.DURATION),
    DURATION_COMPACT(FieldType.DURATION), DURATION_MINUTES(FieldType.DURATION), DURATION_ISO(FieldType.DURATION),
    ONE_OPTION(FieldType.CHOICE), OPTIONS_SEMICOLON(FieldType.CHOICE), OPTIONS_COMMA(FieldType.CHOICE);

    /** [cell], not empty, read in this writing; a DATETIME without an offset is in [zone]. */
    fun read(cell: String, zone: ZoneId): CellRead {
        val c = cell.trim()
        fun no() = CellRead.Unreadable(name)
        return when (this) {
            TEXT -> CellRead.Value(c)
            DECIMAL_POINT, SCALE_POINT -> number(c, '.')?.let { CellRead.Value(it) } ?: no()
            DECIMAL_COMMA, SCALE_COMMA -> number(c, ',')?.let { CellRead.Value(it) } ?: no()
            YES_NO -> when (c.lowercase()) { "oui", "yes" -> CellRead.Value(true); "non", "no" -> CellRead.Value(false); else -> no() }
            TRUE_FALSE -> when (c.lowercase()) { "true", "vrai" -> CellRead.Value(true); "false", "faux" -> CellRead.Value(false); else -> no() }
            ONE_ZERO -> when (c) { "1" -> CellRead.Value(true); "0" -> CellRead.Value(false); else -> no() }
            X_EMPTY -> if (c.equals("x", ignoreCase = true)) CellRead.Value(true) else no()
            DAY_MONTH_YEAR -> day(c, dayFirst = true)?.let { CellRead.Value(it.toString()) } ?: no()
            MONTH_DAY_YEAR -> day(c, dayFirst = false)?.let { CellRead.Value(it.toString()) } ?: no()
            ISO_DATE -> try { CellRead.Value(LocalDate.parse(c).toString()) } catch (e: DateTimeParseException) { no() }
            ISO_DATETIME -> isoInstant(c, zone)?.let { CellRead.Value(it) } ?: no()
            DAY_MONTH_YEAR_TIME -> dayTime(c, dayFirst = true, zone)?.let { CellRead.Value(it) } ?: no()
            MONTH_DAY_YEAR_TIME -> dayTime(c, dayFirst = false, zone)?.let { CellRead.Value(it) } ?: no()
            HOURS_MINUTES -> HM.matchEntire(c)?.destructured?.let { (h, m) ->
                if (h.toInt() < 24 && m.toInt() < 60) CellRead.Value("${h.toInt()}:$m") else null
            } ?: no()
            DURATION_H_MIN -> HM.matchEntire(c)?.destructured?.let { (h, m) -> if (m.toInt() < 60) millis(h.toLong(), m.toLong(), 0) else null } ?: no()
            DURATION_MIN_S -> HM.matchEntire(c)?.destructured?.let { (m, sec) -> if (sec.toInt() < 60) millis(0, m.toLong(), sec.toLong()) else null } ?: no()
            DURATION_H_MIN_S -> HMS.matchEntire(c)?.destructured?.let { (h, m, sec) -> if (m.toInt() < 60 && sec.toInt() < 60) millis(h.toLong(), m.toLong(), sec.toLong()) else null } ?: no()
            DURATION_COMPACT -> COMPACT.matchEntire(c.lowercase().replace(" ", ""))?.destructured?.let { (h, m) ->
                if (m.isNotEmpty() && m.toInt() >= 60) null else millis(h.toLong(), m.ifEmpty { "0" }.toLong(), 0)
            } ?: no()
            DURATION_MINUTES -> MINUTES.matchEntire(c.lowercase())?.groupValues?.get(1)?.let { millis(0, it.toLong(), 0) } ?: no()
            DURATION_ISO -> try { CellRead.Value(java.time.Duration.parse(c).toMillis()) } catch (e: DateTimeParseException) { no() }
            ONE_OPTION -> CellRead.Value(c)
            OPTIONS_SEMICOLON -> CellRead.Value(c.split(';').map { it.trim() }.filter { it.isNotEmpty() })
            OPTIONS_COMMA -> CellRead.Value(c.split(',').map { it.trim() }.filter { it.isNotEmpty() })
        }
    }

    companion object {
        /** The writings of [type], in the order detection tries them; none for a type a file cannot carry. */
        fun of(type: FieldType): List<Writing> = entries.filter { it.type == type }

        private val HM = Regex("^(\\d{1,3}):(\\d{2})$")
        private val HMS = Regex("^(\\d{1,3}):(\\d{2}):(\\d{2})$")
        private val COMPACT = Regex("^(\\d{1,3})h(\\d{0,2})(?:min)?$")
        private val MINUTES = Regex("^(\\d{1,5})\\s*min$")
        private val DAY = Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})$")
        private val DAY_TIME = Regex("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?$")

        private fun number(c: String, decimal: Char): Double? {
            val other = if (decimal == '.') ',' else '.'
            if (other in c) return null
            val plain = c.replace(" ", "").replace(' '.toString(), "").replace(decimal, '.')
            return if (Regex("^-?\\d+(\\.\\d+)?$").matches(plain)) plain.toDouble() else null
        }

        private fun day(c: String, dayFirst: Boolean): LocalDate? = DAY.matchEntire(c)?.destructured?.let { (a, b, y) ->
            val (d, m) = if (dayFirst) a to b else b to a
            runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }

        private fun dayTime(c: String, dayFirst: Boolean, zone: ZoneId): Long? = DAY_TIME.matchEntire(c)?.destructured?.let { (a, b, y, h, min, sec) ->
            val (d, m) = if (dayFirst) a to b else b to a
            runCatching {
                LocalDateTime.of(y.toInt(), m.toInt(), d.toInt(), h.toInt(), min.toInt(), sec.ifEmpty { "0" }.toInt())
                    .atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }

        private fun isoInstant(c: String, zone: ZoneId): Long? =
            runCatching { OffsetDateTime.parse(c).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(c).atZone(zone).toInstant().toEpochMilli() }.getOrNull()

        private fun millis(h: Long, m: Long, s: Long): CellRead = CellRead.Value(((h * 60 + m) * 60 + s) * 1000)
    }
}
