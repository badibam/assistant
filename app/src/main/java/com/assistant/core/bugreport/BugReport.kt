package com.assistant.core.bugreport

import android.content.Context
import android.os.Build
import com.assistant.core.database.entities.LogEntry
import com.assistant.core.utils.DateTimeConverter
import java.time.ZoneId

/**
 * What a report replaces in the text it takes from the app (messages, stack traces): an
 * identifier by its number in the report, a quoted text by a mark. Mechanical, not a promise of
 * anonymity: the report's screen shows the result word for word.
 *
 * One cleaner per report, so that an identifier keeps its number in all of it: "the same tool"
 * can be followed from one line to another without knowing which.
 */
class ReportCleaner {
    private val numbers = mutableMapOf<String, Int>()

    fun clean(text: String): String {
        val withoutIds = UUID.replace(text) { match -> "#" + numbers.getOrPut(match.value.lowercase()) { numbers.size + 1 } }
        return QUOTED.replace(withoutIds, QUOTED_MARK)
    }

    companion object {
        private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        // Quoted on one line: a quote mark left alone (an apostrophe in a sentence) stays
        private val QUOTED = Regex("'[^'\\n]*'|\"[^\"\\n]*\"")
        const val QUOTED_MARK = "«text»"
    }
}

/**
 * The bug report: the one text the report's screen shows and the share menu sends.
 */
object BugReport {

    /** How far back the log lines go. */
    const val WINDOW_MILLIS = 2 * 60 * 60_000L

    /** The most log lines a report holds. */
    const val MAX_LINES = 300

    /** The levels a report takes: the others copy services' results and prompts. */
    val LEVELS = listOf("WARN", "ERROR")

    /**
     * The most characters a report holds: a text shared through an Intent above ~1 MB is
     * refused by Android, and a messaging app does no better. The oldest lines go first.
     */
    const val MAX_LENGTH = 200_000

    /** What the note on the lines left out may take. */
    private const val LEFT_OUT_NOTE_ROOM = 100

    /** The phone and the app, as a report states them. */
    data class Device(
        val versionName: String,
        val versionCode: Int,
        val manufacturer: String,
        val model: String,
        val android: String,
        val sdk: Int,
        val language: String,
        val theme: String
    )

    /** The lines a report takes among [logs], read at [now]: its levels, its window, the newest [MAX_LINES], oldest first. */
    fun selectLines(logs: List<LogEntry>, now: Long): List<LogEntry> =
        logs.filter { it.level in LEVELS && it.timestamp >= now - WINDOW_MILLIS && it.timestamp <= now }
            .sortedByDescending { it.timestamp }
            .take(MAX_LINES)
            .reversed()

    /**
     * The report's text. [description] goes as written; the crash and the lines are cleaned.
     * Lines that would take it over [MAX_LENGTH] are left out, oldest first, and their number said.
     */
    fun build(description: String, device: Device, crash: CrashFile.Crash?, lines: List<LogEntry>, zone: ZoneId): String {
        val cleaner = ReportCleaner()
        fun date(timestamp: Long) = DateTimeConverter.timestampToISO(timestamp, zone)

        val head = buildString {
            append("# Bug report\n")
            if (description.isNotBlank()) append("\n## What happened\n\n").append(description.trim()).append("\n")
            append("\n## Device\n\n")
            append("App: ${device.versionName} (${device.versionCode})\n")
            append("Phone: ${device.manufacturer} ${device.model}\n")
            append("Android: ${device.android} (API ${device.sdk})\n")
            append("Language: ${device.language}\n")
            append("Theme: ${device.theme}\n")
            if (crash != null) {
                append("\n## Last crash\n\n")
                append("${date(crash.timestamp)}, app ${crash.versionName} (${crash.versionCode})\n\n")
                append(cleaner.clean(crash.stackTrace).trimEnd()).append("\n")
            }
            append("\n## Log (WARN and ERROR, last 2 hours)\n")
        }

        val written = lines.map { line ->
            buildString {
                append("\n${date(line.timestamp)} ${line.level} ${line.tag}: ${cleaner.clean(line.message)}\n")
                line.throwableMessage?.let { append(cleaner.clean(it).trimEnd()).append("\n") }
            }
        }
        // The newest lines kept first, within what is left once the head and the note on the
        // lines left out are written
        var room = MAX_LENGTH - head.length - LEFT_OUT_NOTE_ROOM
        val kept = written.asReversed().takeWhile { room -= it.length; room >= 0 }.asReversed()
        val left = written.size - kept.size

        return buildString {
            append(head)
            if (written.isEmpty()) append("\n(none)\n")
            if (left > 0) append("\n($left older lines left out: the report's size)\n")
            kept.forEach { append(it) }
        }
    }

    /** The phone and the app now. */
    fun device(context: Context): Device = Device(
        versionName = com.assistant.BuildConfig.VERSION_NAME,
        versionCode = com.assistant.BuildConfig.VERSION_CODE,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        android = Build.VERSION.RELEASE,
        sdk = Build.VERSION.SDK_INT,
        language = context.resources.configuration.locales[0].toLanguageTag(),
        theme = com.assistant.core.themes.CurrentTheme.current.javaClass.simpleName
    )

    /** What a report is made of, read once; its text follows the free text as it is written. */
    data class Sources(val device: Device, val crash: CrashFile.Crash?, val lines: List<LogEntry>, val zone: ZoneId) {
        fun text(description: String): String = build(description, device, crash, lines, zone)
    }

    /** The report's sources as they stand now. */
    suspend fun read(context: Context): Sources {
        val now = System.currentTimeMillis()
        val logs = com.assistant.core.database.AppDatabase.getDatabase(context).logDao().getLogsFiltered(
            sinceTimestamp = now - WINDOW_MILLIS, tagPattern = "%", levels = LEVELS, limit = MAX_LINES
        )
        val zone = com.assistant.core.utils.AppConfigManager.getDateTimeConfig().getZoneId()
        return Sources(device(context), CrashFile.read(context), selectLines(logs, now), zone)
    }
}
