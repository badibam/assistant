package app.treelune.core.secrets

import app.treelune.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * The secret settings of a config (SettingNode.Field.secret) as the database holds them: sealed
 * by the service that writes the config, opened by the one that reads it (AIProviderConfigService,
 * AppConfigService). Everything else in the config is stored as it is.
 *
 * A secret is a setting of the object itself — a field of its own, of a section or of a variant's
 * case — never one inside a group or a list: [names] does not look there.
 */
object SecretSettings {

    /** The key under which a read hands over the secrets that did not open, with the config. */
    const val UNREADABLE = "unreadable_secrets"

    /** A config opened: its secrets in clear, but for those of [unreadable], left out. */
    data class Opened(val settings: JSONObject, val unreadable: List<String>)

    /** The names of the secret settings [nodes] declare, stored flat in the object they describe. */
    fun names(nodes: List<SettingNode>): List<String> = nodes.flatMap { node ->
        when (node) {
            is SettingNode.Field -> listOfNotNull(node.definition.name.takeIf { node.secret })
            is SettingNode.Variant -> listOfNotNull(node.selector.definition.name.takeIf { node.selector.secret }) +
                node.cases.values.flatMap { names(it) }
            is SettingNode.Section -> names(node.nodes)
            else -> emptyList()
        }
    }

    /** [settings] with each secret of [names] sealed; a blank one is not a secret, and stays as it is. */
    fun seal(names: List<String>, settings: JSONObject, box: SecretBox): JSONObject {
        val out = JSONObject(settings.toString())
        names.forEach { name ->
            val value = out.optString(name).takeIf { out.has(name) && it.isNotBlank() } ?: return@forEach
            out.put(name, box.seal(value))
        }
        return out
    }

    /**
     * [settings] with each secret of [names] opened. One that does not open on this phone is left
     * out and named in [Opened.unreadable], for the caller to say so: shown empty without a word,
     * it would be taken for a setting never entered.
     */
    fun open(names: List<String>, settings: JSONObject, box: SecretBox): Opened {
        val out = JSONObject(settings.toString())
        val unreadable = mutableListOf<String>()
        names.forEach { name ->
            val stored = out.optString(name).takeIf { out.has(name) && it.isNotBlank() } ?: return@forEach
            try {
                out.put(name, box.open(stored))
            } catch (e: UnreadableSecret) {
                out.remove(name)
                unreadable += name
            }
        }
        return Opened(out, unreadable)
    }
}
