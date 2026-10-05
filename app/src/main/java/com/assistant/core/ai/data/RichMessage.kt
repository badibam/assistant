package com.assistant.core.ai.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A user message as written: text and enrichment blocks, in order.
 *
 * Only what the user chose is stored. The text of a block (a pointer's target by its name, what
 * goes with it) is written each time it is read, for the screen or for the AI (EnrichmentText):
 * stored, it would keep a renamed tool's old name and never tell a deleted one.
 */
data class RichMessage(
    val segments: List<MessageSegment>
) {
    /** Whether the message says nothing: no text, no enrichment, no image. */
    val isEmpty: Boolean
        get() = segments.none { it !is MessageSegment.Text || it.content.isNotBlank() }

    /** The ids of the images it carries, in order. */
    val imageIds: List<String>
        get() = segments.filterIsInstance<MessageSegment.Image>().map { it.imageId }

    /**
     * Serialize RichMessage to JSON string for storage
     */
    fun toJson(): String {
        val segmentsArray = JSONArray()
        for (segment in segments) {
            val segmentJson = JSONObject()
            when (segment) {
                is MessageSegment.Text -> {
                    segmentJson.put("type", "text")
                    segmentJson.put("content", segment.content)
                }
                is MessageSegment.Image -> {
                    segmentJson.put("type", "image")
                    segmentJson.put("image_id", segment.imageId)
                }
                is MessageSegment.EnrichmentBlock -> {
                    segmentJson.put("type", "enrichment")
                    segmentJson.put("enrichment_type", segment.type.name)
                    segmentJson.put("config", segment.config)
                }
            }
            segmentsArray.put(segmentJson)
        }
        return JSONObject().put("segments", segmentsArray).toString()
    }

    companion object {
        /**
         * Deserialize RichMessage from JSON string
         * Returns null if parsing fails
         */
        fun fromJson(jsonString: String): RichMessage? {
            return try {
                val segmentsArray = JSONObject(jsonString).getJSONArray("segments")
                val segments = (0 until segmentsArray.length()).mapNotNull { i ->
                    val segmentJson = segmentsArray.getJSONObject(i)
                    when (segmentJson.getString("type")) {
                        "text" -> MessageSegment.Text(content = segmentJson.getString("content"))
                        "image" -> MessageSegment.Image(imageId = segmentJson.getString("image_id"))
                        "enrichment" -> MessageSegment.EnrichmentBlock(
                            type = EnrichmentType.valueOf(segmentJson.getString("enrichment_type")),
                            config = segmentJson.getString("config")
                        )
                        else -> null
                    }
                }
                RichMessage(segments)
            } catch (e: Exception) {
                // Log error but don't crash - return null for graceful handling
                null
            }
        }
    }
}

/**
 * Message segments - either text or enrichment blocks
 */
sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()

    /** An enrichment as the user set it: its type and its JSON configuration. */
    data class EnrichmentBlock(
        val type: EnrichmentType,
        val config: String
    ) : MessageSegment()

    /**
     * An image the user joined, where they put it: « this one [photo] costs less than that one
     * [photo] » depends on the order. Its file is kept with the session (AttachedImages); it goes
     * to the model as an image, never as text.
     */
    data class Image(val imageId: String) : MessageSegment()
}
