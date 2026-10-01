package com.assistant.core.coordinator

import com.assistant.core.demo.DemoContent
import org.json.JSONObject

/**
 * The id a create may be given instead of making one (docs/design/demo.md): only by the app
 * itself (Source.SYSTEM) and only with the demo's prefix, so that the demo is written through the
 * services as everything else is, under ids its reinstall finds again. Neither the AI nor a
 * screen chooses an id: from them, an id sent is refused.
 */
object GivenId {

    sealed interface Read {
        /** No id given: the service makes one. */
        object None : Read
        data class Accepted(val id: String) : Read
        /** An id given where none may be; the refusal names it. */
        data class Refused(val id: String) : Read
    }

    /** The id under "id" in [params], read against the origin of the operation running. */
    suspend fun read(params: JSONObject): Read {
        val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return Read.None
        return if (isAccepted(id, currentOrigin())) Read.Accepted(id) else Read.Refused(id)
    }

    /** Whether [id] may be given by a caller of [origin]. */
    fun isAccepted(id: String, origin: Source): Boolean =
        origin == Source.SYSTEM && id.startsWith(DemoContent.PREFIX)
}
