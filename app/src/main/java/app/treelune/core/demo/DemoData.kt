package app.treelune.core.demo

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The demo's entries, afloat at [now]: twelve weeks of Camille's life up to
 * this moment, in the app's time zone [zone]. Pure, and the same at every install but for the
 * dates, which slide with [now]: the numbers come from one seed.
 *
 * The weeks are counted back from the current one, which starts on Monday: week 0 is this one,
 * the rush week is [RUSH] weeks back. Nothing is dated after [now] but what is to come by nature
 * (a due date, a running stopwatch's start being before now).
 *
 * Texts stay keys (`"@key"`, or `{"@": key, "args": […]}` for a text with values), which the
 * service resolves in the phone's language as it does the structure's.
 *
 * @param hand The entries written by hand (`assets/demo/entries.json`), by tool, each placed in
 *        time as `[weeks back, ISO weekday, "HH:MM"]`
 */
class DemoData(private val now: Long, private val zone: ZoneId, private val hand: JSONObject) {

    companion object {
        /** How many weeks back the rush week is: Work, Running and Italian all show it. */
        const val RUSH = 5
        /** The weeks of history, the current one included. */
        const val WEEKS = 12
        private const val SEED = 2026L
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE

        val CLIENTS = listOf("Studio Brume", "Librairie Le Rameau", "Mairie de Villeurbanne")
    }

    private val random = java.util.Random(SEED)
    private val sessionRandom = java.util.Random(SEED + 1)
    private val today: LocalDate = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone).toLocalDate()
    private val weekStart: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** A goal attempt to open: its period, the values entered in it, and whether it is validated. */
    data class Attempt(val toolId: String, val start: Long, val end: Long, val entered: JSONObject, val status: String, val validate: Boolean)

    // ------------------------------------------------------------------ time

    /** The day [weekday] (1 = Monday) of the week [weeksBack] weeks before the current one. */
    fun day(weeksBack: Int, weekday: Int): LocalDate = weekStart.minusWeeks(weeksBack.toLong()).plusDays((weekday - 1).toLong())

    fun at(date: LocalDate, hour: Int, minute: Int): Long = date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, time: String): Long = time.split(":").let { at(date, it[0].toInt(), it[1].toInt()) }

    /** A place `[weeks back, weekday, "HH:MM"]` as an instant. */
    fun place(place: JSONArray): Long = at(day(place.getInt(0), place.getInt(1)), place.getString(2))

    /** Every day of the history up to today, oldest first. */
    private val days: List<LocalDate> = generateSequence(day(WEEKS - 1, 1)) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()

    private fun weeksBack(date: LocalDate): Int = ((weekStart.toEpochDay() - date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()) / 7).toInt()
    private fun isRush(date: LocalDate) = weeksBack(date) == RUSH
    private fun past(instant: Long) = instant <= now

    // ------------------------------------------------------------------ randomness

    private fun between(from: Double, to: Double) = from + random.nextDouble() * (to - from)
    private fun between(from: Int, to: Int) = from + random.nextInt(to - from + 1)
    private fun chance(p: Double) = random.nextDouble() < p
    private fun round1(value: Double) = (value * 10).roundToInt() / 10.0

    // ------------------------------------------------------------------ entries

    private fun text(key: String) = "@$key"
    private fun text(key: String, vararg args: Any) = JSONObject().put("@", key).put("args", JSONArray(args.map { it.toString() }))

    private fun entry(id: String, name: Any? = null, timestamp: Long? = null, data: JSONObject? = null, extra: JSONObject? = null, state: JSONObject? = null) =
        JSONObject().apply {
            put("id", id)
            name?.let { put("name", it) }
            timestamp?.let { put("timestamp", it) }
            put("data", data ?: JSONObject())
            extra?.let { put("extra", it) }
            state?.let { put("state", it) }
        }

    private fun json(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> if (v != null) put(k, v) } }

    private fun ref(id: String) = json("kind" to "ENTRY", "id" to id)

    /** The entries of each tool, in the order the tools take them: a card before the entries pointing to it. */
    fun entries(): Map<String, List<JSONObject>> = linkedMapOf<String, List<JSONObject>>().apply {
        put("demo-kitchen-foods", cards("demo-kitchen-foods"))
        put("demo-balcony-plants", plants())
        putAll(running())
        putAll(body())
        put("demo-kitchen-meals", meals())
        put("demo-kitchen-water", water())
        put("demo-work-hours", hours())
        put("demo-balcony-harvests", harvests())
        put("demo-balcony-fed", fed())
        put("demo-italian-evening", evenings())
        put("demo-work-invoicing", sends("demo-work-invoicing", "invoicing", weekdays = setOf(1), hour = 9, weeks = WEEKS, unreadLast = true))
        put("demo-balcony-watering", sends("demo-balcony-watering", "watering", weekdays = setOf(1, 3, 5, 7), hour = 19, weeks = 3, unreadLast = false))
        put("demo-balcony-feeding", monthlySends())
        for (tool in listOf("demo-kitchen-shopping", "demo-work-tasks", "demo-balcony-todo", "demo-italian-suitcase")) put(tool, listItems(tool))
        for (tool in listOf("demo-kitchen-recipes", "demo-italian-phrases")) put(tool, notes(tool))
        for (tool in listOf("demo-work-notebook", "demo-balcony-notebook", "demo-balcony-observations")) put(tool, placed(tool))
        put("demo-course-notebook", reviews())
    }

    private fun handOf(tool: String): List<JSONObject> = hand.optJSONArray(tool)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

    private fun cards(tool: String) = handOf(tool).map { entry(it.getString("id"), it.getString("name"), extra = it.getJSONObject("extra")) }

    private fun plants() = handOf("demo-balcony-plants").map { card ->
        val extra = JSONObject(card.getJSONObject("extra").toString())
        extra.put("sown_on", day(card.getInt("sown_weeks_ago"), card.getInt("sown_weekday")).toString())
        card.optJSONArray("repotted")?.let { extra.put("repotted_at", place(it)) }
        entry(card.getString("id"), card.getString("name"), extra = extra)
    }

    /** What happened in each week, the numbers the reviews and the goals read back. */
    private val runsByWeek = mutableMapOf<Int, MutableList<Double>>()
    private val sleepByDay = mutableMapOf<LocalDate, Long>()

    private fun running(): Map<String, List<JSONObject>> {
        val runs = mutableListOf<JSONObject>()
        val strength = mutableListOf<JSONObject>()
        val stretching = mutableListOf<JSONObject>()
        val sessions = mutableListOf<JSONObject>()
        var n = 0
        for (date in days) {
            val back = weeksBack(date)
            val weekday = date.dayOfWeek.value
            val rush = isRush(date)
            // Tuesday easy run, Thursday intervals or hills, Sunday long run; the rush week keeps the Tuesday alone, shorter
            val run: Triple<String, Double, Double>? = when {
                weekday == 2 -> Triple("footing", if (rush) between(4.5, 5.5) else between(7.0, 8.5), between(5.75, 6.1))
                weekday == 4 && !rush -> if (back % 3 == 0) Triple("hills", between(5.5, 6.5), between(6.0, 6.4)) else Triple("intervals", between(6.0, 7.0), between(5.1, 5.4))
                weekday == 7 && !rush -> Triple("long_run", minOf(16.0, 8.0 + 0.75 * (WEEKS - 1 - back)) + between(-0.3, 0.3), between(5.9, 6.2))
                else -> null
            }
            if (run != null) {
                val (kind, km, pace) = run
                val time = at(date, if (weekday == 4) 18 else if (weekday == 7) 9 else 7, if (weekday == 4) 30 else 0)
                if (past(time)) {
                    val distance = round1(km)
                    runsByWeek.getOrPut(back) { mutableListOf() }.add(distance)
                    val feeling = when (kind) { "intervals", "hills" -> between(2, 4); "long_run" -> between(3, 4); else -> between(3, 5) }
                    runs.add(entry("demo-e-run-${n++}", text("runs_entry_name"), time,
                        json("value" to distance, "unit" to "km"),
                        json("duration" to ((distance * pace) * MINUTE).roundToLong() / MINUTE * MINUTE, "kind" to kind, "feeling" to feeling)))
                    if (kind == "intervals") sessions.add(intervalSession("demo-e-intervals-${n++}", time, back))
                }
            }
            // Strength on Wednesdays, and every other Saturday
            if ((weekday == 3 || (weekday == 6 && back % 2 == 0)) && !rush) {
                val time = at(date, if (weekday == 3) 19 else 10, 0)
                if (past(time)) strength.add(entry("demo-e-strength-${n++}", text("strength_entry_name"), time,
                    extra = json("exercises" to text(if (weekday == 3) "strength_exercises_a" else "strength_exercises_b"))))
            }
            // Stretching most evenings, seldom in the rush week
            val stretchTime = at(date, 21, 45)
            if (past(stretchTime) && chance(0.9)) stretching.add(entry("demo-e-stretch-${n++}", text("stretching_entry_name"), stretchTime,
                json("value" to (if (rush) chance(0.2) else chance(0.8)))))
        }
        return mapOf("demo-course-runs" to runs, "demo-course-strength" to strength, "demo-course-stretching" to stretching,
            "demo-course-intervals" to sessions)
    }

    /**
     * The Intervals session of a Thursday, done at [time]: its 17 steps (warm-up, 8 fast, 7
     * recoveries, cool-down), 38 min 30 of timers and the cool-down's overtime. Two weeks differ:
     * stopped at the 6th fast one, and a recovery skipped.
     */
    private fun intervalSession(id: String, time: Long, back: Int): JSONObject {
        val steps = 17
        val stopped = back == 7
        val skipped = if (back == 4) 1 else 0
        val notDone = if (stopped) 6 else 0
        // Drawn apart, so the sessions leave the other tools' numbers as they were
        fun between(from: Int, to: Int) = from + sessionRandom.nextInt(to - from + 1)
        val length = if (stopped) between(24, 27) * MINUTE else (38 * MINUTE + 30_000 + between(1, 6) * MINUTE - skipped * 90_000)
        val paused = if (sessionRandom.nextDouble() < 0.3) between(1, 3) * MINUTE else 0L
        return entry(id, timestamp = time,
            data = json("duration" to length, "paused" to paused, "steps_done" to steps - skipped - notDone, "steps_skipped" to skipped, "steps_not_done" to notDone),
            state = json("status" to if (stopped) "stopped" else "done", "started_at" to time, "ended_at" to time + length + paused))
    }

    private fun body(): Map<String, List<JSONObject>> {
        val weight = mutableListOf<JSONObject>()
        val sleep = mutableListOf<JSONObject>()
        val mood = mutableListOf<JSONObject>()
        var n = 0
        val total = days.size.toDouble()
        days.forEachIndexed { index, date ->
            val weekday = date.dayOfWeek.value
            val rush = isRush(date)
            // Weigh-ins on Monday, Thursday and Saturday, from 71.8 down to 69.9 kg
            if (weekday in setOf(1, 4, 6)) {
                val time = at(date, 7, 15)
                if (past(time)) weight.add(entry("demo-e-weight-${n++}", text("weight_entry_name"), time,
                    json("value" to round1(71.8 - 1.9 * index / total + between(-0.3, 0.3)), "unit" to "kg"), json("weighed_at" to "07:15")))
            }
            // The night ending this morning: short in the rush week
            val wake = at(date, if (rush) 6 else 7, if (rush) 30 else 0)
            if (past(wake)) {
                val minutes = if (rush) between(340, 390) else between(400, 510)
                sleepByDay[date] = minutes * MINUTE
                val quality = when { minutes < 380 -> between(1, 2); minutes < 430 -> 3; else -> between(4, 5) }
                sleep.add(entry("demo-e-sleep-${n++}", text("sleep_entry_name"), wake, json("value" to minutes * MINUTE), json("quality" to quality)))
            }
            // The day's mood, following the night
            val evening = at(date, 21, 30)
            if (past(evening)) {
                val night = sleepByDay[date] ?: (7 * HOUR)
                val value = (if (rush) between(1, 3) else if (night < 7 * HOUR) between(2, 4) else between(3, 5))
                mood.add(entry("demo-e-mood-${n++}", text("mood_entry_name"), evening, json("value" to value)))
            }
        }
        return mapOf("demo-course-weight" to weight, "demo-course-sleep" to sleep, "demo-course-mood" to mood)
    }

    private val breakfasts = listOf(listOf("oats" to 60, "milk" to 200, "banana" to 120), listOf("skyr" to 150, "berries" to 100, "honey" to 15), listOf("bread" to 80, "eggs" to 100))
    private val lunches = listOf(listOf("rice" to 150, "chicken" to 120, "broccoli" to 150), listOf("brown_rice" to 150, "lentils" to 150, "carrots" to 100),
        listOf("pasta" to 180, "tomatoes" to 150, "comte" to 30), listOf("chickpeas" to 150, "spinach" to 100, "olive_oil" to 10))
    private val dinners = listOf(listOf("salmon" to 130, "sweet_potato" to 200, "zucchini" to 150), listOf("tofu" to 150, "rice" to 150, "carrots" to 100),
        listOf("eggs" to 120, "spinach" to 100, "bread" to 60), listOf("pasta" to 160, "tomatoes" to 120, "avocado" to 70))
    private val snacks = listOf(listOf("apple" to 150), listOf("almonds" to 25), listOf("yogurt" to 125, "honey" to 10), listOf("dark_chocolate" to 20), listOf("orange" to 130))

    private fun meals(): List<JSONObject> {
        val meals = mutableListOf<JSONObject>()
        var n = 0
        for (date in days) {
            val moments = listOfNotNull(
                Triple("breakfast", at(date, 7, 30), breakfasts.random()),
                Triple("lunch", at(date, 12, 30), lunches.random()),
                if (chance(if (isRush(date)) 0.9 else 0.5)) Triple("snack", at(date, 16, 30), snacks.random()) else null,
                Triple("dinner", at(date, 19, 45), dinners.random())
            )
            for ((moment, time, foods) in moments) {
                if (!past(time)) continue
                foods.forEachIndexed { i, (food, grams) ->
                    meals.add(entry("demo-e-meal-${n++}", text("meals_entry_$moment"), time + i * MINUTE, json("value" to moment),
                        json("food" to ref("demo-food-$food"), "quantity" to (grams * between(0.85, 1.15)).roundToInt())))
                }
            }
        }
        return meals
    }

    private fun <T> List<T>.random(): T = this[random.nextInt(size)]

    /** Glasses of water over the last two weeks, today's up to now. */
    private fun water(): List<JSONObject> {
        val glasses = mutableListOf<JSONObject>()
        var n = 0
        for (date in days.takeLast(14)) {
            val count = between(6, 8)
            for (i in 0 until count) {
                val time = at(date, 8, 0) + (i * 13 * HOUR / count) + between(0, 30) * MINUTE
                if (past(time)) glasses.add(entry("demo-e-water-${n++}", text("water_entry_name"), time, json("value" to 1)))
            }
        }
        return glasses
    }

    /** The client of a week: Studio Brume throughout, Le Rameau on its website weeks, the town hall in the rush. */
    private fun clientsOf(back: Int): List<Pair<String, String>> = when {
        back == RUSH -> listOf(CLIENTS[2] to "poster")
        back in 7..10 -> listOf(CLIENTS[0] to "identity", CLIENTS[1] to "website")
        back in 0..2 -> listOf(CLIENTS[0] to "poster", CLIENTS[1] to "website")
        else -> listOf(CLIENTS[0] to "identity")
    }

    private fun hours(): List<JSONObject> {
        val sessions = mutableListOf<JSONObject>()
        var n = 0
        val runningStart = now - 40 * MINUTE
        for (date in days) {
            val weekday = date.dayOfWeek.value
            if (weekday > 5) continue
            val back = weeksBack(date)
            val rush = back == RUSH
            // Sessions from 9:00, one after the other with a break: 2 or 3 a day, 4 long ones in the rush
            val lengths = if (rush) listOf(180, 150, 150, 120) else List(between(2, 3)) { between(90, 150) }
            var start = at(date, 9, 0)
            lengths.forEachIndexed { i, minutes ->
                val (client, project) = if (weekday == 5 && i == lengths.lastIndex && !rush) "Studio Brume" to "admin" else clientsOf(back)[i % clientsOf(back).size]
                val end = start + minutes * MINUTE
                if (end <= runningStart) sessions.add(entry("demo-e-hours-${n++}", client, start, json("value" to minutes * MINUTE),
                    json("project" to project, "billable" to (project != "admin"))))
                start = end + (if (i == 0) 75 else 20) * MINUTE
            }
        }
        // The stopwatch running for 40 minutes, on Studio Brume
        sessions.add(entry("demo-e-hours-running", CLIENTS[0], runningStart, JSONObject(), json("project" to "poster", "billable" to true),
            json("running" to json("data" to json("value" to runningStart)))))
        return sessions
    }

    private fun harvests(): List<JSONObject> {
        val picked = mutableListOf<JSONObject>()
        var n = 0
        for (date in days) {
            val back = weeksBack(date)
            if (back > 7) continue
            val weekday = date.dayOfWeek.value
            val picks = listOfNotNull(
                if (weekday == 3 || weekday == 6) "tomato" to between(120, 220) else null,
                if (weekday == 6) "basil" to between(15, 30) else null,
                if (weekday == 7 && back >= 3) "strawberry" to between(60, 100) else null,
                if (weekday == 7 && back <= 2) "chilli" to between(20, 40) else null
            )
            for ((plant, grams) in picks) {
                val time = at(date, 18, 30)
                if (past(time)) picked.add(entry("demo-e-harvest-${n++}", text("harvests_entry_name"), time, json("value" to grams, "unit" to "g"),
                    json("plant" to ref("demo-plant-$plant"))))
            }
        }
        return picked
    }

    private fun firstOfMonths(): List<LocalDate> = days.filter { it.dayOfMonth == 1 }

    private fun fed() = firstOfMonths().mapIndexedNotNull { i, date ->
        at(date, 10, 20).takeIf(::past)?.let { entry("demo-e-fed-$i", text("fed_entry_name"), it) }
    }

    private fun evenings(): List<JSONObject> {
        val phrases = hand.getJSONArray("_evening_phrases")
        val filled = mutableListOf<JSONObject>()
        // Up to yesterday: tonight's invitation is the questionnaire's own, at 20:00
        days.dropLast(1).forEachIndexed { i, date ->
            val time = at(date, 20, 0)
            filled.add(if (isRush(date) && date.dayOfWeek.value <= 6) {
                entry("demo-e-evening-$i", timestamp = time, state = json("status" to "ignored"))
            } else {
                entry("demo-e-evening-$i", timestamp = time,
                    extra = json("words" to between(4, 12), "difficulty" to between(2, 4), "phrase" to phrases.getString(i % phrases.length())),
                    state = json("status" to "filled", "filled_at" to time + between(15, 40) * MINUTE))
            })
        }
        return filled
    }

    /** The past sends of a message: [weekdays] at [hour], over the last [weeks]; read, but for the last when [unreadLast]. */
    private fun sends(tool: String, key: String, weekdays: Set<Int>, hour: Int, weeks: Int, unreadLast: Boolean): List<JSONObject> {
        val times = days.filter { it.dayOfWeek.value in weekdays && weeksBack(it) < weeks }.map { at(it, hour, 0) }.filter(::past)
        return times.mapIndexed { i, time -> sent("$tool-$i", key, time, read = !(unreadLast && i == times.lastIndex)) }
    }

    private fun monthlySends() = firstOfMonths().map { at(it, 10, 0) }.filter(::past).mapIndexed { i, time -> sent("demo-balcony-feeding-$i", "feeding", time, read = true) }

    private fun sent(id: String, key: String, time: Long, read: Boolean) = entry("demo-e-$id", text("${key}_sent"), time,
        json("common_title" to text("${key}_title"), "common_content" to text("${key}_content"), "priority" to "default"),
        state = json("status" to "sent", "notification_sent" to true, "read" to read, "archived" to false, "triggered_by" to "SCHEDULE"))

    private fun listItems(tool: String) = handOf(tool).mapNotNull { item ->
        val state = json("position" to item.getInt("position"))
        item.optJSONArray("checked")?.let(::place)?.takeIf(::past)?.let { state.put("checked_at", it) }
        val data = JSONObject()
        when (val due = item.opt("due")) {
            "in_one_minute" -> data.put("due_at", now + MINUTE)
            "this_morning" -> at(today, 9, 0).let { if (it < now) it else now - 30 * MINUTE }.also { data.put("due_at", it); state.put("due_notified", it) }
            "next_saturday" -> data.put("due_at", at(today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), 10, 0))
            is JSONArray -> place(due).also { data.put("due_at", it); if (it <= now && !state.has("checked_at")) state.put("due_notified", it) }
        }
        entry(item.getString("id"), item.getString("name"), data = data, state = state)
    }

    private fun notes(tool: String) = handOf(tool).map { note ->
        entry(note.getString("id"), data = note.getJSONObject("data"), state = json("position" to note.getInt("position")))
    }

    private fun placed(tool: String) = handOf(tool).mapNotNull { e ->
        val time = place(e.getJSONArray("at")).takeIf(::past) ?: return@mapNotNull null
        entry(e.getString("id"), e.getString("name"), time, e.getJSONObject("data"))
    }

    /** The weekly reviews of the Training notebook, each Monday at 7:05 on the week before. */
    private fun reviews(): List<JSONObject> = (1 until WEEKS).reversed().mapNotNull { back ->
        val written = at(day(back - 1, 1), 7, 5).takeIf(::past) ?: return@mapNotNull null
        val runs = runsByWeek[back] ?: emptyList()
        val nights = (1..7).mapNotNull { sleepByDay[day(back, it)] }
        val average = if (nights.isEmpty()) 0L else nights.sum() / nights.size
        val verdict = if (back == RUSH) "course_review_rush" else "course_review_good"
        entry("demo-e-review-$back", text("course_review_title", day(back, 1).toString()), written,
            json("content" to text("course_review", runs.size, round1(runs.sum()), "${average / HOUR} h ${"%02d".format(average % HOUR / MINUTE)}", text(verdict))))
    }

    /**
     * The goals' attempts, one a week: those before last validated, last week's left to validate,
     * the current one open. Running's "No pain" is ticked but in the rush week.
     */
    fun attempts(): List<Attempt> = listOf("demo-course-goal", "demo-italian-goal").flatMap { tool ->
        (WEEKS - 1 downTo 0).map { back ->
            val start = at(day(back, 1), 0, 0)
            val end = at(day(back - 1, 1), 0, 0) - 1
            val entered = if (tool == "demo-course-goal") json("c_no_pain" to (back != RUSH)) else JSONObject()
            when (back) {
                0 -> Attempt(tool, start, end, entered, "active", validate = false)
                1 -> Attempt(tool, start, end, entered, "to_validate", validate = false)
                else -> Attempt(tool, start, end, entered, "to_validate", validate = true)
            }
        }
    }
}
