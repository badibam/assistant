package com.assistant.core.ai.enrichments

import com.assistant.core.ai.data.DataCommand
import com.assistant.core.strings.StringsContext
import org.json.JSONObject

/**
 * A FILE enrichment: a text file joined to a message (docs/design/missing-tools.md, « L'import »),
 * kept by the files service under [fileId]. [whole] sends its whole text with the message;
 * otherwise its first [PREVIEW_LINES] lines, the AI reading the rest with FILE if it needs to.
 * Stored as `{"file_id", "name", "line_count", "whole"}`.
 */
data class FileEnrichment(val fileId: String, val name: String, val lineCount: Int, val whole: Boolean = true) {

    fun toJson(): JSONObject = JSONObject()
        .put("file_id", fileId).put("name", name).put("line_count", lineCount).put("whole", whole)

    /** The block's text: "File: aliments.csv (412 lines), whole" or "…, preview only". */
    fun summary(s: StringsContext): String =
        s.shared(if (whole) "file_summary_whole" else "file_summary_preview").format(name, lineCount)

    /** What goes with the message: the FILE read, whole or its first lines, which carries its id, name and type. */
    fun query(isRelative: Boolean) = DataCommand(
        id = "file.$fileId" + if (whole) "" else ".preview",
        type = "FILE",
        params = if (whole) mapOf("id" to fileId) else mapOf("id" to fileId, "start_line" to 1, "lines" to PREVIEW_LINES),
        isRelative = isRelative
    )

    companion object {
        /** The lines a preview holds: enough to see a CSV's header and the look of its values. */
        const val PREVIEW_LINES = 20

        fun fromJson(json: JSONObject) = FileEnrichment(
            fileId = json.getString("file_id"),
            name = json.getString("name"),
            lineCount = json.getInt("line_count"),
            whole = json.optBoolean("whole", true)
        )

        fun fromJson(config: String) = fromJson(JSONObject(config))
    }
}
