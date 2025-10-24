package com.assistant.core.ai.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rich message structure for user messages with enrichments
 */
data class RichMessage(
    val segments: List<MessageSegment>,
    val linearText: String,             // Computed at creation for AI consumption (uses promptPreview with IDs)
    val dataCommands: List<DataCommand> // Computed at creation for prompt system
) {
    /**
     * Generate UI-friendly text without IDs (uses preview instead of promptPreview)
     */
    fun toDisplayText(): String {
        return segments.joinToString("\n") { segment ->
            when (segment) {
                is MessageSegment.Text -> segment.content
                is MessageSegment.EnrichmentBlock -> "[${segment.preview}]"
            }
        }.trim()
    }
    /**
     * Serialize RichMessage to JSON string for storage
     */
    fun toJson(): String {
        val json = JSONObject()

        // Serialize segments
        val segmentsArray = JSONArray()
        for (segment in segments) {
            val segmentJson = JSONObject()
            when (segment) {
                is MessageSegment.Text -> {
                    segmentJson.put("type", "text")
                    segmentJson.put("content", segment.content)
                }
                is MessageSegment.EnrichmentBlock -> {
                    segmentJson.put("type", "enrichment")
                    segmentJson.put("enrichmentType", segment.type.name)
                    segmentJson.put("config", segment.config)
                    segmentJson.put("preview", segment.preview)
                    segmentJson.put("promptPreview", segment.promptPreview)
                }
            }
            segmentsArray.put(segmentJson)
        }
        json.put("segments", segmentsArray)

        // Store computed fields for convenience (can be regenerated)
        json.put("linearText", linearText)

        // Note: dataCommands are NOT stored as they should be regenerated from enrichments
        // during prompt building to ensure fresh data

        return json.toString()
    }

    companion object {
        /**
         * Deserialize RichMessage from JSON string
         * Returns null if parsing fails
         */
        fun fromJson(jsonString: String): RichMessage? {
            return try {
                val json = JSONObject(jsonString)

                // Parse segments
                val segmentsArray = json.getJSONArray("segments")
                val segments = mutableListOf<MessageSegment>()

                for (i in 0 until segmentsArray.length()) {
                    val segmentJson = segmentsArray.getJSONObject(i)
                    val segmentType = segmentJson.getString("type")

                    val segment = when (segmentType) {
                        "text" -> {
                            MessageSegment.Text(
                                content = segmentJson.getString("content")
                            )
                        }
                        "enrichment" -> {
                            val enrichmentTypeStr = segmentJson.getString("enrichmentType")
                            val enrichmentType = EnrichmentType.valueOf(enrichmentTypeStr)

                            MessageSegment.EnrichmentBlock(
                                type = enrichmentType,
                                config = segmentJson.getString("config"),
                                preview = segmentJson.getString("preview"),
                                promptPreview = segmentJson.getString("promptPreview")
                            )
                        }
                        else -> null
                    }

                    segment?.let { segments.add(it) }
                }

                // Get linearText (for display purposes - could be regenerated)
                val linearText = json.optString("linearText", "")

                // dataCommands will be regenerated during prompt building
                // so we don't parse them here

                RichMessage(
                    segments = segments,
                    linearText = linearText,
                    dataCommands = emptyList() // Always empty when loading from DB
                )

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

    data class EnrichmentBlock(
        val type: EnrichmentType,
        val config: String,          // JSON configuration of the block
        val preview: String,         // Short preview for UI: "Zone : Santé"
        val promptPreview: String    // Detailed preview for prompt: "Zone : Santé (id = zone_123)"
    ) : MessageSegment()
}

