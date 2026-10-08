package app.treelune.core.ai.data

import app.treelune.core.ai.providers.toPromptText

/**
 * [this] as the AI reads it (toPromptText), held to [maxChars] characters: the results of
 * actions go to the AI without anyone being asked, so none may go at any length. The summary
 * and the results come first, in order, each whole while it fits; the first one that does not
 * fit has its data and its error cut where the room ends, marked by [cutMark] with the number of
 * characters not sent, and the results after it keep only their line.
 *
 * Validated as a cut, not a refusal: the actions are done, and the AI needs the start of what
 * they returned (the ids of what was created, the reasons of a refusal) to carry on.
 */
fun SystemMessage.withinChars(maxChars: Int, cutMark: (Int) -> String): SystemMessage {
    if (toPromptText().length <= maxChars) return this

    var room = maxChars - summary.length
    val results = commandResults.map { result ->
        val line = SystemMessage(type, listOf(result), "").toPromptText().length
        when {
            line <= room -> { room -= line; result }
            else -> {
                val dataText = result.data?.entries?.joinToString(", ") { (k, v) -> "$k: $v" }.orEmpty()
                val errorText = result.error.orEmpty()
                val fixed = line - dataText.length - errorText.length
                val left = (room - fixed).coerceAtLeast(0)
                room = 0
                val keptData = dataText.take(left)
                val keptError = errorText.take(left - keptData.length)
                result.copy(
                    data = if (dataText.isEmpty()) result.data
                        else mapOf("cut" to keptData + cutMark(dataText.length - keptData.length)),
                    error = if (errorText.isEmpty()) result.error
                        else keptError + cutMark(errorText.length - keptError.length)
                )
            }
        }
    }
    val data = formattedData?.let { it.take(room.coerceAtLeast(0)) + if (it.length > room) cutMark(it.length - room.coerceAtLeast(0)) else "" }
    return copy(commandResults = results, formattedData = data)
}
