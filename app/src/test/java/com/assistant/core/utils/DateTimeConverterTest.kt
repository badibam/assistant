package com.assistant.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/**
 * Covers the conversion between the timestamps the app stores and the ISO 8601 strings it
 * exchanges with the interface and with the AI.
 *
 * This is the boundary where a date changes representation, and NOTES.md records the doubt
 * it needs settling: dates are sometimes a timestamp and sometimes a structure, and what
 * happens to the timezone across that line was never written down. These cases write it
 * down.
 *
 * The timezone is a parameter of every function here, so nothing has to be mocked or
 * initialised to check any of it.
 */
class DateTimeConverterTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    // ==================== Reading an ISO string ====================

    /**
     * A string that carries its own offset is read on that offset, and the app's timezone
     * is not consulted. Reading the same string in two different app timezones gives the
     * same instant, which is the point of sending the offset at all.
     */
    @Test
    fun anIsoStringWithAnOffset_isReadOnThatOffset() {
        val withOffset = "2025-03-15T14:30:00+01:00"

        val readInParis = DateTimeConverter.isoToTimestamp(withOffset, paris)
        val readInTokyo = DateTimeConverter.isoToTimestamp(withOffset, tokyo)

        assertEquals(readInParis, readInTokyo)
        assertEquals("2025-03-15T13:30:00Z", java.time.Instant.ofEpochMilli(readInParis).toString())
    }

    /**
     * A string without an offset has no instant of its own: it is read as wall-clock time
     * in the app's timezone. The same text then means two different moments depending on
     * which timezone the app is set to.
     */
    @Test
    fun anIsoStringWithoutAnOffset_isReadInTheAppsTimezone() {
        val noOffset = "2025-03-15T14:30:00"

        val readInParis = DateTimeConverter.isoToTimestamp(noOffset, paris)
        val readInTokyo = DateTimeConverter.isoToTimestamp(noOffset, tokyo)

        // Paris is UTC+1 in March, Tokyo UTC+9: eight hours between the two readings.
        assertEquals(8 * 3_600_000L, readInParis - readInTokyo)
    }

    /**
     * An unreadable string is refused rather than turned into something. This is the rule
     * the project states -- a failure is explicit or it is not -- and it is worth a test
     * because the same job is done elsewhere by silently returning the current time.
     */
    @Test
    fun anUnreadableString_isRefused() {
        val rejected = listOf("", "not a date", "15/03/2025", "2025-03-15")

        for (bad in rejected) {
            try {
                DateTimeConverter.isoToTimestamp(bad, paris)
                throw AssertionError("\"$bad\" should have been refused")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains(bad))
            }
        }
    }

    // ==================== Writing an ISO string ====================

    /** What goes out always carries its offset, so the receiver never has to guess. */
    @Test
    fun aTimestampGoesOutWithItsOffset() {
        val timestamp = DateTimeConverter.isoToTimestamp("2025-03-15T14:30:00+01:00", paris)

        assertEquals("2025-03-15T14:30:00+01:00", DateTimeConverter.timestampToISO(timestamp, paris))
        assertEquals("2025-03-15T22:30:00+09:00", DateTimeConverter.timestampToISO(timestamp, tokyo))
    }

    /** The offset written is the one in force on that date, not a fixed one for the zone. */
    @Test
    fun theOffsetWrittenFollowsDaylightSaving() {
        val winter = DateTimeConverter.isoToTimestamp("2025-01-15T12:00:00+01:00", paris)
        val summer = DateTimeConverter.isoToTimestamp("2025-07-15T12:00:00+02:00", paris)

        assertTrue(DateTimeConverter.timestampToISO(winter, paris).endsWith("+01:00"))
        assertTrue(DateTimeConverter.timestampToISO(summer, paris).endsWith("+02:00"))
    }

    /** Out and back in the same timezone returns the instant it started from. */
    @Test
    fun writingThenReadingReturnsTheSameInstant() {
        val instants = listOf(
            0L,
            1_742_042_200_000L,
            DateTimeConverter.isoToTimestamp("2024-03-31T03:30:00+02:00", paris), // clocks forward
            DateTimeConverter.isoToTimestamp("2024-10-27T02:30:00+02:00", paris)  // clocks back
        )

        for (instant in instants) {
            val roundTripped = DateTimeConverter.isoToTimestamp(
                DateTimeConverter.timestampToISO(instant, paris), paris
            )
            assertEquals(instant, roundTripped)
        }
    }

    /**
     * Seconds survive; anything finer does not. ISO_OFFSET_DATE_TIME writes the
     * milliseconds when they are there, so a timestamp carrying them comes back whole --
     * but one that lands exactly on a second is written without them, which is what the
     * round trip above relies on.
     */
    @Test
    fun millisecondsSurviveTheRoundTrip() {
        val withMillis = DateTimeConverter.isoToTimestamp("2025-03-15T14:30:00+01:00", paris) + 123L

        val written = DateTimeConverter.timestampToISO(withMillis, paris)
        assertEquals("2025-03-15T14:30:00.123+01:00", written)
        assertEquals(withMillis, DateTimeConverter.isoToTimestamp(written, paris))
    }

    // ==================== Recognising a date in a string ====================

    /** What the heuristic accepts as looking like a datetime. */
    @Test
    fun aDatetimeIsRecognisedByItsShape() {
        val recognised = listOf(
            "2025-03-15T14:30:00",
            "2025-03-15T14:30:00Z",
            "2025-03-15T14:30:00+01:00",
            "2025-03-15T14:30:00.123+01:00"
        )
        for (value in recognised) {
            assertTrue(value, DateTimeConverter.looksLikeISO8601(value))
        }
    }

    /**
     * And what it does not: a date with no time of day is not converted, which is why a
     * custom field of type DATE keeps the string it was given rather than becoming a
     * timestamp at midnight.
     */
    @Test
    fun aDateWithoutATimeIsNotRecognised() {
        val notRecognised = listOf("2025-03-15", "15/03/2025", "14:30:00", "", "today")
        for (value in notRecognised) {
            assertFalse(value, DateTimeConverter.looksLikeISO8601(value))
        }
    }

    /**
     * The string has to start with the datetime. The check reads as containsMatchIn, which
     * would look anywhere, but the pattern is anchored with ^ and Kotlin does not treat that
     * as multiline by default, so the two together mean "starts with".
     *
     * Worth pinning because the pair is easy to misread in either direction: dropping the
     * anchor, or switching to matches(), would both change which values get converted --
     * and a sentence that merely mentions a date would then be offered for conversion.
     */
    @Test
    fun aStringMustStartWithTheDatetime() {
        assertFalse(DateTimeConverter.looksLikeISO8601("seen on 2025-03-15T14:30:00 in the log"))
        assertTrue(DateTimeConverter.looksLikeISO8601("2025-03-15T14:30:00 seen in the log"))
    }

    // ==================== Walking a whole payload ====================

    /**
     * A known timestamp field is converted both ways, so a payload can be handed out as ISO
     * and taken back as timestamps without the values moving.
     */
    @Test
    fun aKnownTimestampFieldMakesTheRoundTrip() {
        val stored = JSONObject().put("timestamp", 1_742_042_200_000L).put("name", "Sport")

        val out = DateTimeConverter.timestampsToISO(stored, paris)
        assertEquals("2025-03-15T13:36:40+01:00", out.get("timestamp"))
        assertEquals("Sport", out.get("name"))

        val back = DateTimeConverter.isoToTimestamps(out, paris)
        assertEquals(1_742_042_200_000L, back.get("timestamp"))
        assertEquals("Sport", back.get("name"))
    }

    /** Every name in the known list is treated the same way, at any depth. */
    @Test
    fun theKnownNamesAreConvertedWhereverTheyAre() {
        val stored = JSONObject()
            .put("created_at", 1_742_042_200_000L)
            .put("session", JSONObject().put("last_activity", 1_742_042_200_000L))

        val out = DateTimeConverter.timestampsToISO(stored, paris)

        assertTrue(out.get("created_at") is String)
        assertTrue(out.getJSONObject("session").get("last_activity") is String)
    }

    /** An Int is treated as a timestamp too, since JSON may hand back the smaller type. */
    @Test
    fun anIntUnderAKnownNameIsConvertedAsWell() {
        val out = DateTimeConverter.timestampsToISO(JSONObject().put("timestamp", 0), paris)

        assertEquals("1970-01-01T01:00:00+01:00", out.get("timestamp"))
    }

    /** Values that are not datetimes are carried through untouched, whatever their type. */
    @Test
    fun everythingElseIsCarriedThrough() {
        val payload = JSONObject()
            .put("name", "Sport")
            .put("amount", 30)
            .put("done", true)
            .put("note", "done at the gym")

        val out = DateTimeConverter.timestampsToISO(payload, paris)
        val back = DateTimeConverter.isoToTimestamps(payload, paris)

        for (result in listOf(out, back)) {
            assertEquals("Sport", result.get("name"))
            assertEquals(30, result.get("amount"))
            assertEquals(true, result.get("done"))
            assertEquals("done at the gym", result.get("note"))
        }
    }

    /**
     * A string under a known name that is not a datetime is kept as it was: the conversion
     * is attempted and refused, and the refusal leaves the value alone rather than emptying
     * the field.
     */
    @Test
    fun anUnreadableValueUnderAKnownNameIsKept() {
        val out = DateTimeConverter.isoToTimestamps(JSONObject().put("timestamp", "whenever"), paris)

        assertEquals("whenever", out.get("timestamp"))
    }

    /** A datetime under an unknown name is converted on its shape alone, going in. */
    @Test
    fun aDatetimeUnderAnUnknownNameIsConvertedOnItsShape() {
        val payload = JSONObject().put(
            "extra",
            JSONObject().put("appointment", "2025-03-15T14:30:00+01:00")
        )

        val out = DateTimeConverter.isoToTimestamps(payload, paris)

        assertEquals(
            1_742_045_400_000L,
            out.getJSONObject("extra").get("appointment")
        )
    }

    /**
     * But it does not come back. Going in, a value is converted when its name is known OR
     * its shape matches; coming out, only when its name is known. So anything converted on
     * shape alone stays a number on the way back.
     *
     * This states what the code does today. A custom field of type DATETIME is exactly that
     * case: stored as a timestamp, handed back to the interface and to the model as a raw
     * number rather than as the ISO string it arrived as.
     */
    @Test
    fun aDatetimeConvertedOnItsShapeDoesNotComeBack() {
        val payload = JSONObject().put(
            "extra",
            JSONObject().put("appointment", "2025-03-15T14:30:00+01:00")
        )

        val asTimestamps = DateTimeConverter.isoToTimestamps(payload, paris)
        val backOut = DateTimeConverter.timestampsToISO(asTimestamps, paris)

        val value = backOut.getJSONObject("extra").get("appointment")
        assertFalse("it stays a number", value is String)
        assertEquals(1_742_045_400_000L, value)
    }

    // ==================== Inside a list ====================

    /** A datetime inside an array is converted going in, recognised by its shape. */
    @Test
    fun aDatetimeInsideAnArrayIsConvertedGoingIn() {
        val payload = JSONObject().put(
            "moments",
            JSONArray().put("2025-03-15T14:30:00+01:00").put("not a date")
        )

        val out = DateTimeConverter.isoToTimestamps(payload, paris)
        val moments = out.getJSONArray("moments")

        assertEquals(1_742_045_400_000L, moments.get(0))
        assertEquals("not a date", moments.get(1))
    }

    /**
     * And never comes back, for the same reason as above but more surely: elements of an
     * array are converted under an empty key, and an empty key is in no known-name list.
     *
     * This states what the code does today. A list of datetimes therefore survives one way
     * only, whatever the name of the field holding it.
     */
    @Test
    fun aTimestampInsideAnArrayNeverComesBack() {
        val payload = JSONObject().put("timestamp", JSONArray().put(1_742_045_400_000L))

        val out = DateTimeConverter.timestampsToISO(payload, paris)

        assertEquals(1_742_045_400_000L, out.getJSONArray("timestamp").get(0))
    }

    /** Objects inside an array are still walked, so a batch of entries is converted. */
    @Test
    fun objectsInsideAnArrayAreStillWalked() {
        val payload = JSONObject().put(
            "entries",
            JSONArray()
                .put(JSONObject().put("timestamp", 1_742_042_200_000L).put("name", "Sport"))
                .put(JSONObject().put("timestamp", 1_742_045_400_000L).put("name", "Lecture"))
        )

        val out = DateTimeConverter.timestampsToISO(payload, paris)
        val entries = out.getJSONArray("entries")

        assertEquals("2025-03-15T13:36:40+01:00", entries.getJSONObject(0).get("timestamp"))
        assertEquals("Lecture", entries.getJSONObject(1).get("name"))
    }

    // ==================== The timezone of a whole payload ====================

    /** An offset-less string in a payload is read in the timezone given to the call. */
    @Test
    fun aPayloadIsReadInTheTimezoneGiven() {
        val payload = JSONObject().put("timestamp", "2025-03-15T14:30:00")

        val inParis = DateTimeConverter.isoToTimestamps(payload, paris).get("timestamp") as Long
        val inTokyo = DateTimeConverter.isoToTimestamps(payload, tokyo).get("timestamp") as Long

        assertEquals(8 * 3_600_000L, inParis - inTokyo)
    }

    /** An empty payload comes back empty rather than failing. */
    @Test
    fun anEmptyPayloadStaysEmpty() {
        assertEquals(0, DateTimeConverter.isoToTimestamps(JSONObject(), paris).length())
        assertEquals(0, DateTimeConverter.timestampsToISO(JSONObject(), paris).length())
    }
}
