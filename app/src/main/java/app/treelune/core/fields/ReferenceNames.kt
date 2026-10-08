package app.treelune.core.fields

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.selection.Reference

/** What a reference designates as shown: its current name, or null once it was deleted. */
sealed interface ReferenceName {
    data class Named(val name: String) : ReferenceName
    data object Deleted : ReferenceName
}

/**
 * The current names of [references], read through references.names.
 *
 * @throws IllegalStateException when they cannot be read: a read that fails is never taken for a deletion
 */
suspend fun loadReferenceNames(references: Collection<Reference>, context: Context): Map<Reference, ReferenceName> {
    if (references.isEmpty()) return emptyMap()
    val result = Coordinator(context).processUserAction(
        "references.names",
        mapOf("references" to references.map { mapOf("kind" to it.kind.name, "id" to it.id) })
    )
    if (!result.isSuccess) throw IllegalStateException("Reference names not read: ${result.error}")
    return (result.data?.get("references") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().associate { row ->
        val reference = ReferenceTarget.referenceOf(row) ?: throw IllegalStateException("unreadable reference in the names read: $row")
        reference to ((row["name"] as? String)?.let { ReferenceName.Named(it) } ?: ReferenceName.Deleted)
    }
}
