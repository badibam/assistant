package app.treelune.core.services

/**
 * The entries a batch refused, said in a few lines whatever their number: each distinct reason
 * once, with how many entries it refused and the places of the first ones. A batch of 64 000
 * entries all refused for the same reason is one line, where listing each refusal made a
 * message of megabytes that went to the AI.
 */
internal object BatchRefusals {

    private const val REASONS_SHOWN = 5
    private const val PLACES_SHOWN = 5

    /** [refusals], each the entry's place in the batch and its reason, with [text] the shared strings. */
    fun summary(refusals: List<Pair<Int, String>>, text: (String) -> String): String {
        val byReason = refusals.groupBy({ it.second }, { it.first }).entries.sortedByDescending { it.value.size }
        val reasons = byReason.take(REASONS_SHOWN).joinToString("; ") { (reason, places) ->
            val shown = places.take(PLACES_SHOWN).joinToString(", ") + if (places.size > PLACES_SHOWN) "…" else ""
            text("batch_refusal_reason").format(reason, places.size, shown)
        }
        val more = (byReason.size - REASONS_SHOWN).takeIf { it > 0 }?.let { text("batch_refusal_more").format(it) } ?: ""
        return text("batch_refused_all").format(refusals.size, reasons + more)
    }
}
