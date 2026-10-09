package app.treelune.core.coordinator

import app.treelune.core.strings.StringsContext

/**
 * What protects against the AI (docs/design/validation.md: a zone's protection, a tool's) is
 * changed by a person, or by the app itself (a restore, the demo): the AI, an AI outside the app
 * or a scheduled task that tries is refused, whatever else it may change. Left unchanged, it may
 * be sent back as it is by anyone.
 */
object Protections {

    /**
     * The refusal of protection [label] going from [old] to [new], null when allowed.
     *
     * @param label The protection's name as the screens show it, for the message
     */
    suspend fun refusal(label: String, old: Boolean, new: Boolean, s: StringsContext): String? =
        if (old == new || byAPerson()) null
        else s.shared("service_error_protection_user_only").format(label)

    /** Whether the operation running comes from a person or from the app itself. */
    suspend fun byAPerson(): Boolean = currentOrigin() in BY_A_PERSON

    private val BY_A_PERSON = setOf(Source.USER, Source.SYSTEM)
}
