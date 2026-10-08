package app.treelune.core.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * What is wrong with a value refused by a variant (a oneOf whose branches a selector's const
 * tells apart: a tracking's "type", a field definition's "type").
 *
 * The validator only says that no branch holds, which names nothing to fix. The branch the value
 * means is the one whose selector it holds: validated against that branch alone, the value gets
 * the errors that branch finds. A value holding no selector, or one no branch has, is told the
 * options. A variant inside the branch is explained the same way.
 */
object VariantErrors {

    private val mapper = ObjectMapper()
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    /** Whether [error] is a variant's refusal, which [explain] turns into its branch's errors. */
    fun isVariant(error: ValidationMessage): Boolean = error.type == "oneOf"

    /**
     * [errors] sorted: the variants' refusals explained, and the rest left for the caller.
     * The validator also reports what every branch of a refused variant found, each against a
     * branch the value may not mean ("type must be text" for a numeric one): those are dropped,
     * the branch the value means saying its own.
     *
     * @return The errors outside any refused variant, and the explanations
     */
    fun sort(errors: Collection<ValidationMessage>, schema: JSONObject, data: JsonNode): Pair<List<ValidationMessage>, List<String>> {
        val variants = errors.filter { isVariant(it) }
        val others = errors.filter { error ->
            !isVariant(error) && variants.none { error.schemaPath.startsWith(it.schemaPath + "/") }
        }
        return others to variants.flatMap { explain(it, schema, data) }.distinct()
    }

    /**
     * The errors behind [error], a variant's refusal, as messages naming their path.
     *
     * @param schema The whole schema the value was checked against
     * @param data The whole value checked
     */
    fun explain(error: ValidationMessage, schema: JSONObject, data: JsonNode): List<String> {
        val variant = at(schema, error.schemaPath.removePrefix("#").removeSuffix("/oneOf"))
            ?: return listOf(error.message)
        val branches = variant.optJSONArray("oneOf") ?: return listOf(error.message)
        val value = node(data, error.path) ?: return listOf(error.message)
        val path = error.path

        val selectors = selectors(branches)
        if (selectors.isEmpty()) return listOf(error.message)
        val branch = (0 until branches.length()).map { branches.getJSONObject(it) }.firstOrNull { branch ->
            selectors.all { key -> value.get(key)?.let { same(it, branch.getJSONObject("properties").getJSONObject(key).get("const")) } == true }
        } ?: return selectors.map { key ->
            val options = (0 until branches.length()).map { branches.getJSONObject(it).getJSONObject("properties").getJSONObject(key).get("const") }
            "$path.$key: ${value.get(key)?.let { "$it is not" } ?: "missing, must be"} one of ${options.joinToString("|")}"
        }

        // Validated alone, the branch's own paths start at "$": they are put back under the
        // value's path. Its own variants are explained in turn.
        val branchSchema = JSONObject(branch.toString())
        val found = factory.getSchema(mapper.readTree(branchSchema.toString())).validate(value)
        // Nothing wrong with the branch alone (more than one branch holds): the refusal stands
        if (found.isEmpty()) return listOf(error.message)
        val (others, explained) = sort(found, branchSchema, value)
        return (others.map { it.message } + explained)
            .map { message -> if (path == "$") message else message.replaceFirst("$", path) }
    }

    /** The properties holding a const in every branch: what tells the branches apart. */
    private fun selectors(branches: JSONArray): List<String> {
        val all = (0 until branches.length()).map { branches.getJSONObject(it).optJSONObject("properties") }
        if (all.any { it == null }) return emptyList()
        return all[0]!!.keys().asSequence().filter { key -> all.all { it!!.optJSONObject(key)?.has("const") == true } }.toList()
    }

    private fun same(node: JsonNode, const: Any): Boolean =
        if (node.isTextual) node.asText() == const.toString() else node.toString() == const.toString()

    /** The schema at a JSON pointer ("/properties/extra_fields/items"). */
    private fun at(schema: JSONObject, pointer: String): JSONObject? {
        var current: Any = schema
        pointer.split("/").filter { it.isNotEmpty() }.forEach { token ->
            current = when (val c = current) {
                is JSONObject -> c.opt(token) ?: return null
                is JSONArray -> token.toIntOrNull()?.let { c.opt(it) } ?: return null
                else -> return null
            }
        }
        return current as? JSONObject
    }

    /** The value at a validator path ("$.extra_fields[0].config"). */
    private fun node(data: JsonNode, path: String): JsonNode? {
        var current: JsonNode? = data
        Regex("""\.([^.\[]+)|\[(\d+)]""").findAll(path.removePrefix("$")).forEach { m ->
            current = if (m.groupValues[1].isNotEmpty()) current?.get(m.groupValues[1]) else current?.get(m.groupValues[2].toInt())
        }
        return current
    }
}
