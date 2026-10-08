package app.treelune.core.ai.processing

import org.json.JSONException
import org.json.JSONObject

/**
 * The JSON object an AI response carries, and the text written around it.
 *
 * A response is the AI's message as one JSON object. Some models write a sentence before it, or
 * wrap it in a ```json block: the object is still the message, and what surrounds it is apart.
 * The object is taken only when the response holds exactly one: none, or several, and nothing
 * says which one is the message.
 */
object ResponseEnvelope {

    /** The one object of a response, and the text outside it with code fences left out (blank when none). */
    data class Split(val json: String, val outside: String)

    /** The response's one JSON object and what surrounds it, or null when it holds none or several. */
    fun split(response: String): Split? {
        val objects = objects(response)
        if (objects.size != 1) return null
        val (start, end) = objects.single()
        val outside = (response.substring(0, start) + "\n" + response.substring(end))
            .lines()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        return Split(response.substring(start, end), outside)
    }

    /**
     * The spans of [text] that are whole JSON objects, each starting at a brace outside any other
     * one. A brace in the prose ("starts with `{`") opens no object: a span that does not close or
     * does not read as JSON is passed over, and the search goes on from the next character.
     */
    private fun objects(text: String): List<Pair<Int, Int>> {
        val found = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                val end = closing(text, i)
                if (end != null && readsAsObject(text.substring(i, end))) {
                    found += i to end
                    i = end
                    continue
                }
            }
            i++
        }
        return found
    }

    /** The index just past the brace closing the one at [start], strings skipped; null if it never closes. */
    private fun closing(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return i + 1
            }
        }
        return null
    }

    private fun readsAsObject(candidate: String): Boolean = try {
        JSONObject(candidate)
        true
    } catch (e: JSONException) {
        false
    }
}
