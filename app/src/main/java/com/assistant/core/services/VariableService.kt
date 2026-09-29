package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.VariableEntity
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.ToolFields
import com.assistant.core.fields.toFieldDefinition
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.reading.FieldReading
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.selection.TimeResolver
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.JsonUtils
import com.assistant.core.variables.Cause
import com.assistant.core.variables.Formula
import com.assistant.core.variables.FormulaType
import com.assistant.core.variables.Outcome
import com.assistant.core.variables.StoredVariable
import com.assistant.core.variables.Term
import com.assistant.core.variables.VariableDefinition
import com.assistant.core.variables.VariableEvaluator
import com.assistant.core.variables.VariableSources
import com.assistant.core.variables.VariableValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The variables of the core (docs/DATA.md, « Variables »), for the screen and the AI alike.
 *
 * - create: `zone_id`, `name`, `definition` (a constant or a formula written with names), `group`?
 * - update: `variable_id`, and any of `name`, `definition`, `group`, `zone_id`, `order_index`
 * - delete: `variable_id`; a formula naming it then fails, "variable deleted"
 * - get, list (`zone_id`), list_all: each variable with its formula written with the current
 *   names, its terms and its field; never its value, which a list would freeze
 * - evaluate: `variable_id` or `name`, `at` (a list of instants, milliseconds): a value or a
 *   failure per instant, in the same order
 *
 * A write is checked as the screen checks it: a name unique in the app that a formula can write,
 * every name of the formula a term or a variable, every term readable, no variable reading
 * itself through others; a formula's field, when not given, is deduced from what it reads.
 */
class VariableService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val dao = AppDatabase.getDatabase(context).variableDao()

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return try {
            when (operation) {
                "create" -> create(params)
                "update" -> update(params)
                "delete" -> delete(params)
                "get" -> get(params)
                "list" -> OperationResult.success(mapOf("variables" to dao.getByZone(params.optString("zone_id")).map { describe(it) }))
                "list_all" -> OperationResult.success(mapOf("variables" to dao.getAll().map { describe(it) }))
                "evaluate" -> evaluate(params)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Refused) {
            OperationResult.error(e.message ?: "")
        }
    }

    /** A write refused, with what to say. */
    private class Refused(message: String) : Exception(message)

    private suspend fun create(params: JSONObject): OperationResult {
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() } ?: throw Refused(s.shared("variable_error_param").format("zone_id"))
        if (AppDatabase.getDatabase(context).zoneDao().getZoneById(zoneId) == null) throw Refused(s.shared("service_error_zone_not_found"))
        val id = UUID.randomUUID().toString()
        val name = checkName(params.optString("name"), id)
        val definition = checkDefinition(params.optJSONObject("definition") ?: throw Refused(s.shared("variable_error_param").format("definition")), name, id)
        val now = System.currentTimeMillis()
        dao.insert(VariableEntity(
            id = id, zoneId = zoneId, name = name,
            group = params.optString("group").takeIf { it.isNotEmpty() },
            orderIndex = dao.getByZone(zoneId).size,
            definitionJson = definition.toJson().toString(),
            createdAt = now, updatedAt = now
        ))
        DataChangeNotifier.notifyVariablesChanged()
        return OperationResult.success(mapOf("variable_id" to id, "name" to name))
    }

    private suspend fun update(params: JSONObject): OperationResult {
        val existing = dao.getById(params.optString("variable_id")) ?: throw Refused(s.shared("variable_error_not_found").format(params.optString("variable_id")))
        val name = if (params.has("name")) checkName(params.optString("name"), existing.id) else existing.name
        val definition = params.optJSONObject("definition")?.let { checkDefinition(it, name, existing.id) }
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() } ?: existing.zoneId
        if (zoneId != existing.zoneId && AppDatabase.getDatabase(context).zoneDao().getZoneById(zoneId) == null) {
            throw Refused(s.shared("service_error_zone_not_found"))
        }
        dao.update(existing.copy(
            name = name,
            zoneId = zoneId,
            group = if (params.has("group")) params.optString("group").takeIf { it.isNotEmpty() } else existing.group,
            orderIndex = if (params.has("order_index")) params.getInt("order_index") else existing.orderIndex,
            definitionJson = definition?.toJson()?.toString()
                // A rename alone leaves the definition, whose field carries the name
                ?: existing.definitionJson,
            updatedAt = System.currentTimeMillis()
        ))
        DataChangeNotifier.notifyVariablesChanged()
        return OperationResult.success(mapOf("variable_id" to existing.id, "name" to name))
    }

    private suspend fun delete(params: JSONObject): OperationResult {
        val existing = dao.getById(params.optString("variable_id")) ?: throw Refused(s.shared("variable_error_not_found").format(params.optString("variable_id")))
        dao.deleteById(existing.id)
        DataChangeNotifier.notifyVariablesChanged()
        return OperationResult.success(mapOf("variable_id" to existing.id, "name" to existing.name))
    }

    private suspend fun get(params: JSONObject): OperationResult {
        val existing = dao.getById(params.optString("variable_id")) ?: throw Refused(s.shared("variable_error_not_found").format(params.optString("variable_id")))
        return OperationResult.success(mapOf("variable" to describe(existing)))
    }

    // ========================================================================================
    // Checking a write
    // ========================================================================================

    /** A name a formula can write, unique in the app. */
    private suspend fun checkName(name: String, id: String): String {
        val trimmed = name.trim()
        if (!NAME.matches(trimmed)) throw Refused(s.shared("variable_error_name").format(trimmed))
        dao.getAll().firstOrNull { it.name == trimmed && it.id != id }?.let { throw Refused(s.shared("variable_error_name_taken").format(trimmed)) }
        return trimmed
    }

    /**
     * [json] as a definition to store: its formula with every variable it names by id, its terms
     * readable, no loop, its field given or deduced.
     */
    private suspend fun checkDefinition(json: JSONObject, name: String, id: String): VariableDefinition {
        val definition = try {
            VariableDefinition.fromJson(json, name) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            throw Refused(e.message ?: "")
        }
        if (definition is VariableDefinition.Constant) return definition
        definition as VariableDefinition.Computed

        val others = dao.getAll().filter { it.id != id }
        val byName = others.associateBy { it.name }
        definition.terms.keys.firstOrNull { it in byName }?.let { throw Refused(s.shared("variable_error_term_name").format(it)) }
        definition.terms.keys.firstOrNull { !NAME.matches(it) }?.let { throw Refused(s.shared("variable_error_name").format(it)) }

        // The formula as written, then every name that is no term read as a variable's id
        val written = when (val parsed = Formula.parse(definition.formula, aliases)) {
            is Formula.Parsed.Unreadable -> throw Refused(s.shared("variable_error_formula").format(parsed.position + 1, s.shared("formula_problem_${parsed.problem.name.lowercase()}"), parsed.detail))
            is Formula.Parsed.Read -> parsed.formula
        }
        val unknown = written.names().filter { it !in definition.terms && it !in byName }
        if (unknown.isNotEmpty()) {
            throw Refused(s.shared("variable_error_unknown_names").format(unknown.distinct().joinToString(", "),
                (definition.terms.keys + byName.keys).joinToString(", ")))
        }
        val stored = written.mapNames { n -> byName[n]?.let { Formula.VariableRef(it.id) }.takeIf { n !in definition.terms } }
        stored.variableIds().filter { ref -> others.none { it.id == ref } }.takeIf { it.isNotEmpty() }
            ?.let { throw Refused(s.shared("variable_error_not_found").format(it.joinToString(", "))) }

        definition.terms.forEach { (termName, term) -> checkTerm(termName, term, others) }

        // No variable reading itself through others
        val reads = (stored.variableIds() + definition.terms.values.filterIsInstance<Term.Variable>().map { it.id }).distinct()
        loop(id, reads, others)?.let { path -> throw Refused(s.shared("variable_error_loop").format((listOf(name) + path.map { p -> others.first { it.id == p }.name } + name).joinToString(" → "))) }

        val field = if (json.has("field")) definition.field else deduce(stored, definition.terms, others, name)
        // Stored with each function under its English name, a formula per entry too
        val terms = definition.terms.mapValues { (_, term) ->
            if (term is Term.Reading && term.perEntry != null) {
                term.copy(perEntry = (Formula.parse(term.perEntry, aliases) as Formula.Parsed.Read).formula.text({ it }))
            } else term
        }
        return VariableDefinition.Computed(stored.text({ "{var:$it}" }), terms, field)
    }

    /** The functions under their names in the app's language, which a formula written on screen uses. */
    private val aliases: Map<String, Formula.Function> by lazy { Formula.Function.localized { s.shared(it) } }

    /** A term that can be read: its tool and field, a reduction its type takes, a variable that exists. */
    private suspend fun checkTerm(name: String, term: Term, others: List<VariableEntity>) {
        when (term) {
            is Term.Constant -> {}
            is Term.Variable -> if (others.none { it.id == term.id }) throw Refused(s.shared("variable_error_not_found").format(term.id))
            is Term.Reading -> {
                term.selection.problem { s.shared(it) }?.let { throw Refused(s.shared("variable_error_term").format(name) + " " + it) }
                if (term.selection.target.kind != ReferenceKind.TOOL_INSTANCE) throw Refused(s.shared("service_error_reading_source").format(term.selection.target.kind.name))
                val fields = try {
                    ToolFields.filterable(term.selection.target.id!!, context, s)
                } catch (e: IllegalStateException) {
                    throw Refused(s.shared("variable_error_term").format(name) + " " + (e.message ?: ""))
                }
                if (term.perEntry != null) {
                    if (Formula.parse(term.perEntry, aliases) is Formula.Parsed.Unreadable) throw Refused(s.shared("variable_error_term").format(name))
                    return
                }
                val field = term.field?.let { fields[it] ?: throw Refused(s.shared("service_error_filter_unknown_field").format(it, fields.keys.joinToString(", "))) }
                if (term.reduction !in Reduction.forType(field?.type)) {
                    throw Refused(s.shared("service_error_reading_reduction_type").format(term.reduction.name, field?.type?.name ?: "-",
                        Reduction.forType(field?.type).joinToString(", ") { it.name }))
                }
            }
        }
    }

    /** The variables from [reads] back to [id], if one of them leads there; null when none does. */
    private fun loop(id: String, reads: List<String>, others: List<VariableEntity>, path: List<String> = emptyList()): List<String>? {
        for (next in reads) {
            if (next == id) return path
            if (next in path) continue
            val variable = others.firstOrNull { it.id == next } ?: continue
            val found = loop(id, readsOf(variable), others, path + next)
            if (found != null) return found
        }
        return null
    }

    /** The variables a stored one reads, by id. */
    private fun readsOf(variable: VariableEntity): List<String> {
        val json = JSONObject(variable.definitionJson)
        val formula = (Formula.parse(json.optString("formula")) as? Formula.Parsed.Read)?.formula
        val terms = json.optJSONObject("terms")?.let { t -> t.keys().asSequence().mapNotNull { t.getJSONObject(it).optString("variable").takeIf { v -> v.isNotEmpty() } }.toList() } ?: emptyList()
        return (formula?.variableIds() ?: emptyList()) + terms
    }

    /** The field a formula's value takes, from what it reads. */
    private suspend fun deduce(formula: Formula, terms: Map<String, Term>, others: List<VariableEntity>, name: String): FieldDefinition {
        val leaves = mutableMapOf<Formula, FieldDefinition?>()
        suspend fun fieldOf(leaf: Formula): FieldDefinition? = when (leaf) {
            is Formula.VariableRef -> others.firstOrNull { it.id == leaf.id }?.let { stored(it).definition.field }
            is Formula.Name -> when (val term = terms[leaf.name]) {
                is Term.Reading -> when {
                    term.perEntry != null -> null
                    term.reduction == Reduction.COUNT -> FieldReading.COUNT_FIELD
                    else -> ToolFields.filterable(term.selection.target.id!!, context, s)[term.field]
                }
                is Term.Variable -> others.firstOrNull { it.id == term.id }?.let { stored(it).definition.field }
                else -> null
            }
            else -> null
        }
        (formula.names().map { Formula.Name(it) } + formula.variableIds().map { Formula.VariableRef(it) }).forEach { leaves[it] = fieldOf(it) }
        val deduced = FormulaType.deduce(formula) { leaves[it]?.takeIf { f -> f.type in VariableDefinition.VALUE_TYPES } }
        return FieldDefinition(name, name, null, deduced.type, false, deduced.config)
    }

    // ========================================================================================
    // Reading
    // ========================================================================================

    private fun stored(entity: VariableEntity): StoredVariable =
        StoredVariable(entity.id, entity.name, entity.zoneId, VariableDefinition.fromJson(JSONObject(entity.definitionJson), entity.name) { s.shared(it) })

    /**
     * The tool instances [entity] reads, through the variables it reads too, and for a formula per
     * entry the tools its references lead to: what, changing, changes its value. [ANY_TOOL] when a
     * reference may lead to any entry.
     */
    private suspend fun toolsRead(entity: VariableEntity, all: Map<String, VariableEntity>, seen: MutableSet<String> = mutableSetOf()): Set<String> {
        if (!seen.add(entity.id)) return emptySet()
        val terms = JSONObject(entity.definitionJson).optJSONObject("terms") ?: JSONObject()
        val direct = mutableSetOf<String>()
        for (key in terms.keys()) {
            val reading = terms.getJSONObject(key).optJSONObject("reading") ?: continue
            val tool = reading.optJSONObject("selection")?.optJSONObject("target")?.optString("id")?.takeIf { it.isNotEmpty() } ?: continue
            direct.add(tool)
            if (reading.optString("per_entry").isEmpty()) continue
            val config = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(tool)?.config_json?.let { JSONObject(it) } ?: continue
            config.optJSONArray("extra_fields")?.toFieldDefinitions()?.filter { it.type == com.assistant.core.fields.FieldType.REFERENCE }?.forEach { field ->
                val target = com.assistant.core.fields.ReferenceTarget.fromConfig(field.config)
                if (target.toolInstances.isEmpty()) direct.add(ANY_TOOL) else direct.addAll(target.toolInstances)
            }
        }
        return direct + readsOf(entity).mapNotNull { all[it] }.flatMap { toolsRead(it, all, seen) }
    }

    /** A variable as the screen and the AI read it: its formula with the current names, never its value. */
    private suspend fun describe(entity: VariableEntity): Map<String, Any?> {
        val definition = JSONObject(entity.definitionJson)
        val names = dao.getAll().associate { it.id to it.name }
        (Formula.parse(definition.optString("formula")) as? Formula.Parsed.Read)?.let { read ->
            definition.put("formula", read.formula.text({ names[it] ?: s.shared("pointer_target_deleted") }))
        }
        return mapOf(
            "id" to entity.id,
            "name" to entity.name,
            "zone_id" to entity.zoneId,
            "group" to entity.group,
            "order_index" to entity.orderIndex,
            "definition" to JsonUtils.toMap(definition),
            "tools_read" to toolsRead(entity, dao.getAll().associateBy { it.id }).toList()
        )
    }

    private suspend fun evaluate(params: JSONObject): OperationResult {
        val entity = params.optString("variable_id").takeIf { it.isNotEmpty() }?.let { dao.getById(it) }
            ?: params.optString("name").takeIf { it.isNotEmpty() }?.let { n -> dao.getAll().firstOrNull { it.name == n } }
            ?: throw Refused(s.shared("variable_error_not_found").format(params.optString("variable_id").ifEmpty { params.optString("name") }))
        val at = params.optJSONArray("at")?.let { a -> (0 until a.length()).map { (a.get(it) as? Number)?.toLong() ?: throw Refused(s.shared("variable_error_param").format("at")) } }
            ?: throw Refused(s.shared("variable_error_param").format("at"))
        val variable = try { stored(entity) } catch (e: IllegalArgumentException) { throw Refused(e.message ?: "") }
        val evaluator = VariableEvaluator(Sources())
        // A read that fails is the operation's error, never a value's cause
        val values = try {
            at.map { instant -> instant to evaluator.evaluate(variable, instant) }
        } catch (e: IllegalStateException) {
            return OperationResult.error(e.message ?: "")
        }
        return OperationResult.success(mapOf(
            "variable_id" to entity.id,
            "name" to entity.name,
            "field" to JsonUtils.toMap(VariableDefinition.fieldJson(variable.definition.field)),
            "values" to values.map { (instant, value) ->
                when (value) {
                    is VariableValue.Value -> mapOf("at" to instant, "value" to value.value)
                    is VariableValue.Failed -> mapOf("at" to instant, "failure" to failure(value.causes))
                }
            }
        ))
    }

    /** Causes as data, with their words for whoever shows them. */
    private fun failure(causes: List<Cause>): Map<String, Any> = mapOf(
        "causes" to causes.map { mapOf("reason" to it.reason, "term" to it.term, "field" to it.field, "entries" to it.entries) },
        "message" to causes.joinToString(" ; ") { cause ->
            val reason = s.shared("variable_cause_${cause.reason.lowercase()}").format(cause.field ?: "", cause.entries.size)
            listOfNotNull(cause.term, reason).joinToString(" : ")
        }
    )

    /** What the evaluator reads: the variables here, the entries through the core's services. */
    private inner class Sources : VariableSources {
        private val coordinator = Coordinator(context)

        override suspend fun variable(id: String): StoredVariable? = dao.getById(id)?.let { stored(it) }

        override suspend fun read(term: Term.Reading, at: Long): Outcome {
            val result = coordinator.processUserAction("readings.read", mapOf(
                "selection" to JsonUtils.toMap(term.selection.toJson()),
                "field" to term.field,
                "reduction" to term.reduction.name,
                "reference" to at
            ).filterValues { it != null }.mapValues { it.value!! })
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "readings.read")
            (result.data?.get("failure") as? Map<*, *>)?.let { f ->
                return Outcome.Failed(listOf(Cause(f["reason"] as String, field = f["field"] as? String,
                    entries = (f["entries"] as? List<*>)?.map { it.toString() } ?: emptyList())))
            }
            return (result.data?.get("value") as? Number)?.let { Outcome.Value(it.toDouble()) }
                ?: Outcome.Failed(listOf(Cause(Cause.NOT_A_NUMBER, field = term.field)))
        }

        override suspend fun entries(selection: EntrySelection, at: Long): List<Map<String, Any?>>? {
            val toolId = selection.target.id ?: return null
            if (gone("TOOL_INSTANCE", toolId)) return null
            val fields = ToolFields.filterable(toolId, context, s)
            val result = coordinator.processUserAction("tool_data.get", mapOf(
                "tool_instance_id" to toolId,
                "filters" to JsonUtils.toList(selection.storedFilters(fields, TimeResolver.at(at)) { s.shared(it) })
            ))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "tool_data.get")
            @Suppress("UNCHECKED_CAST")
            return (result.data?.get("entries") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
        }

        override suspend fun entry(id: String): Map<String, Any?>? {
            if (gone("ENTRY", id)) return null
            val result = coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to id))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "tool_data.get_single")
            @Suppress("UNCHECKED_CAST")
            return result.data?.get("entry") as? Map<String, Any?>
        }

        /**
         * Whether a thing was deleted; its read failing is an error.
         *
         * @throws IllegalStateException when it cannot be read
         */
        private suspend fun gone(kind: String, id: String): Boolean {
            val result = coordinator.processUserAction("references.names", mapOf("references" to listOf(mapOf("kind" to kind, "id" to id))))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "references.names")
            return ((result.data?.get("references") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("deleted") == true
        }
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("name")
        return when (operation) {
            "create" -> s.shared("action_verbalize_variables_create").format(name)
            "update" -> s.shared("action_verbalize_variables_update").format(name.ifEmpty { params.optString("variable_id") })
            "delete" -> s.shared("action_verbalize_variables_delete").format(params.optString("variable_id"))
            "evaluate" -> s.shared("action_verbalize_variables_evaluate").format(name.ifEmpty { params.optString("variable_id") })
            else -> s.shared("action_verbalize_variables_read")
        }
    }

    companion object {
        /** In the tools a variable reads, any tool: a reference it reads through may lead to any entry. */
        const val ANY_TOOL = "*"

        /** A name a formula can write: a letter or `_`, then letters, digits, `_`. */
        val NAME = Regex("^[\\p{L}_][\\p{L}\\p{N}_]*$")
    }
}
