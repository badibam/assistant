package com.assistant.core.services

import org.json.JSONObject

/**
 * Turn one entry of a batch into the params of the single create or update it stands for.
 *
 * A batch is a list of entries handed one by one to createEntry or updateEntry, so what an
 * entry may carry is decided here and nowhere else. A key left out of this copy is dropped
 * without a word: the single operation never sees it, and the batch still reports success.
 * That is how custom_fields went missing from every entry the AI created in a batch, while
 * the batch update carried them all along; state was dropped the same way from both.
 */
internal object BatchEntryParams {

    /**
     * Params for createEntry. The tool instance comes from the batch command, which names it once
     * for all its entries. Its tooltype and data schema are the service's to read from the tool.
     */
    fun forCreate(entry: JSONObject, toolInstanceId: String): JSONObject =
        JSONObject().apply {
            put("tool_instance_id", toolInstanceId)
            // An id given, which the create accepts from the app's demo alone (GivenId)
            if (entry.has("id")) put("id", entry.getString("id"))
            put("data", entry.optJSONObject("data") ?: JSONObject())
            if (entry.has("extra")) put("extra", entry.getJSONObject("extra"))
            if (entry.has("state")) put("state", entry.getJSONObject("state"))
            if (entry.has("timestamp")) put("timestamp", entry.getLong("timestamp"))
            if (entry.has("name")) put("name", entry.getString("name"))
        }

    /** Params for updateEntry: only what the entry names changes. */
    fun forUpdate(entry: JSONObject, entryId: String): JSONObject =
        JSONObject().apply {
            put("id", entryId)
            if (entry.has("data")) put("data", entry.getJSONObject("data"))
            if (entry.has("extra")) put("extra", entry.getJSONObject("extra"))
            if (entry.has("state")) put("state", entry.getJSONObject("state"))
            if (entry.has("timestamp")) put("timestamp", entry.getLong("timestamp"))
            if (entry.has("name")) put("name", entry.getString("name"))
        }
}
