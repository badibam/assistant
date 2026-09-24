package com.assistant.core.services

import org.json.JSONObject

/**
 * Turn one entry of a batch into the params of the single create or update it stands for.
 *
 * A batch is a list of entries handed one by one to createEntry or updateEntry, so what an
 * entry may carry is decided here and nowhere else. A key left out of this copy is dropped
 * without a word: the single operation never sees it, and the batch still reports success.
 * That is how custom_fields went missing from every entry the AI created in a batch, while
 * the batch update carried them all along.
 */
internal object BatchEntryParams {

    /**
     * Params for createEntry. The tool instance and tooltype come from the batch command, which
     * names them once for all its entries.
     */
    fun forCreate(entry: JSONObject, toolInstanceId: String, tooltype: String): JSONObject =
        JSONObject().apply {
            put("tool_instance_id", toolInstanceId)
            put("tooltype", tooltype)
            put("data", entry.optJSONObject("data") ?: JSONObject())
            if (entry.has("custom_fields")) put("custom_fields", entry.getJSONObject("custom_fields"))
            if (entry.has("timestamp")) put("timestamp", entry.getLong("timestamp"))
            if (entry.has("name")) put("name", entry.getString("name"))
            if (entry.has("schema_id")) put("schema_id", entry.getString("schema_id"))
        }

    /** Params for updateEntry: only what the entry names changes. */
    fun forUpdate(entry: JSONObject, entryId: String): JSONObject =
        JSONObject().apply {
            put("id", entryId)
            if (entry.has("data")) put("data", entry.getJSONObject("data"))
            if (entry.has("custom_fields")) put("custom_fields", entry.getJSONObject("custom_fields"))
            if (entry.has("timestamp")) put("timestamp", entry.getLong("timestamp"))
            if (entry.has("name")) put("name", entry.getString("name"))
        }
}
