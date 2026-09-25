# Gestion des Données

Guide technique pour la navigation, validation et manipulation des données dans l'architecture Assistant.

##
## Navigation Hiérarchique

### DataNavigator

Architecture pour navigation dans les données via schémas avec chargement à la demande et résolution conditionnelle.

```kotlin
// DataNavigator.kt
class DataNavigator(private val context: Context) {
    suspend fun getRootNodes(): List<SchemaNode>
    suspend fun getChildren(path: String): List<SchemaNode>
    suspend fun getDistinctValues(path: String): ContextualDataResult
}
```

**Structure** : App → Zones → Outils → Champs avec navigation à la demande.

**Usage** : Permet de naviguer dans la structure des données sans accès direct aux données métier.

### ZoneScopeSelector

Sélecteur hiérarchique pour enrichments POINTER : Zone → Tool Instance → Context + Resources → Period (optionnel).

```kotlin
@Composable
fun ZoneScopeSelector(
    config: NavigationConfig,
    onDismiss: () -> Unit,
    onConfirm: (SelectionResult) -> Unit
)
```

**Flow de navigation** :
1. Sélection Zone (si `allowZoneSelection`)
2. Sélection Tool Instance (si `allowInstanceSelection`)
3. Sélection Context + Resources (section unifiée)
4. Sélection Period (optionnelle selon contexte)

**Simplification** : Navigation limitée à ZONE et INSTANCE (pas de field-level). Contextes explicites pour désambiguïser les requêtes.

#### NavigationConfig

```kotlin
data class NavigationConfig(
    // Level selection permissions
    val allowZoneSelection: Boolean = true, // Peut-on confirmer aux zones ?
    val allowInstanceSelection: Boolean = true, // Peut-on confirmer aux instances ?
    val allowFieldSelection: Boolean = true, // Peut-on confirmer aux champs ? (deprecated, non utilisé)
    val allowValueSelection: Boolean = true, // Naviguer vers valeurs ? (deprecated, non utilisé)

    // Context-aware selection
    val allowedContexts: List<PointerContext> = listOf(
        PointerContext.GENERIC,
        PointerContext.CONFIG,
        PointerContext.DATA
    ), // Contextes disponibles
    val defaultContext: PointerContext = PointerContext.GENERIC, // Contexte par défaut

    // UI configuration
    val title: String = "", // Titre custom ou scope_selector_title par défaut
    val useRelativeLabels: Boolean = false // true pour AUTOMATION, false pour CHAT
)
```

#### PointerContext

Contexte explicite pour désambiguïser les requêtes de données :

- **GENERIC** : Référence floue, aucun command automatique, période optionnelle pour contexte IA
- **CONFIG** : Configuration d'outils, pas de période temporelle
- **DATA** : Données métier (tool_data), période optionnelle sur `tool_data.timestamp`


#### Cas d'usage

```kotlin
// POINTER enrichment (CHAT) - Zone et instance avec tous contextes
NavigationConfig(
    allowZoneSelection = true,
    allowInstanceSelection = true,
    allowFieldSelection = false,
    allowedContexts = listOf(
        PointerContext.GENERIC,
        PointerContext.CONFIG,
        PointerContext.DATA
    ),
    defaultContext = PointerContext.GENERIC,
    useRelativeLabels = false
)

// POINTER enrichment (AUTOMATION) - Périodes relatives
NavigationConfig(
    allowZoneSelection = true,
    allowInstanceSelection = true,
    allowFieldSelection = false,
    defaultContext = PointerContext.DATA,
    useRelativeLabels = true
)

// Sélection zones seulement
NavigationConfig(
    allowZoneSelection = true,
    allowInstanceSelection = false
)

// Sélection outils uniquement
NavigationConfig(
    allowZoneSelection = false,
    allowInstanceSelection = true
)
```

#### SelectionResult

```kotlin
data class SelectionResult(
    val selectedPath: String, // Chemin complet sélectionné
    val selectionLevel: SelectionLevel, // Niveau d'arrêt (ZONE ou INSTANCE)

    // Context-aware selection
    val selectedContext: PointerContext, // Contexte sélectionné
    val selectedResources: List<String>, // Ressources cochées (ex: ["data", "data_schema"])

    // Field-level selection (deprecated, non utilisé)
    val selectedValues: List<String> = emptyList(),
    val fieldSpecificData: FieldSpecificData? = null,
    val displayChain: List<String> = emptyList() // Labels lisibles pour affichage
)
```

**Niveaux de sélection** : ZONE, INSTANCE

**Ressources par contexte** :
- GENERIC : `[]` (vide, pas de query automatique)
- CONFIG : `["config", "config_schema"]` disponibles
- DATA : `["data", "data_schema"]` disponibles, `["data"]` coché par défaut

**Périodes** : Stockées dans `timestampSelection` (non visible dans SelectionResult, géré en interne par ZoneScopeSelector)

##
## Validation par schéma

Aucun schéma n'est écrit à la main ni nommé dans une donnée : ils sont générés depuis des déclarations (`docs/design/config-fields.md`, `docs/design/unified-fields.md`).

- **Entrées** : le schéma se génère depuis les champs que le type d'outil déclare (`getEntryFields`) et les champs de l'utilisateur (`extra_fields`), par `BaseSchemas.getEntrySchema`. `ToolDataService` valide toute écriture, quel que soit l'appelant, après avoir arrondi les nombres à leurs décimales (`NumericPrecision`) ; `FieldValueValidator` ajoute ce qu'un schéma ne sait pas dire (le début d'une plage avant sa fin).
- **Configs d'outil** : le schéma se génère depuis la partie commune (`ToolConfigSettings`) et la partie du type d'outil (`getConfigSettings`), par `SettingsSchemaGenerator` ; une variante (le `type` d'un suivi) devient un `oneOf`. `ToolInstanceService` valide toute écriture de config.
- **Changement de config** : ce qu'il fait aux entrées se déduit des champs d'entrée que donnent l'ancienne et la nouvelle config, `data` comme `extra` (`EntryMigration`) : une valeur qui perd son sens est retirée, une entrée qui perd une valeur obligatoire de `data` est supprimée, et le tout s'écrit dans la même transaction que la config. Retirer des valeurs ou supprimer des entrées est refusé sans `confirm_migration: true`, les comptes sous `migration` ; une entrée sans valeur pour un champ devenu obligatoire prend celle que l'appelant donne dans `fill_values` (`{"data": {"value": 3}}`), sinon le changement est refusé.
- **Zones** : le schéma `zone_config` se génère depuis `ZoneSettings` (nom, description, icône, groupe de l'écran d'accueil, groupes d'outils), et `ZoneService` valide toute écriture ; l'écran de zone est le formulaire de cette déclaration.
- **Lecture** : une config se lit par `ToolConfigSettings.read` (`SettingValues`) ; un réglage absent vaut son défaut déclaré, jamais un repli écrit sur place.
- **Face à l'IA** : un schéma se demande par `tooltype` (config), `tool_instance_id` (entrées) ou `id` (les autres : `zone_config`, `field_type_TEXT`…). Son nom calculé (`tracking_config`, `tracking_data`) ne sert qu'à ne pas renvoyer deux fois le même schéma.

Un champ marqué `"system_managed": true` est à l'app de le produire, jamais à l'appelant. Dans `data`, le service retire tout champ marqué de ce qu'on lui envoie (`SystemManagedFields`) ; à la racine, il ne lit que des paramètres nommés.

La validation que font les écrans avant d'appeler le service sert à répondre tôt dans le formulaire ; elle n'est pas la garde.

**Champ `id`** : pas `system_managed`, puisqu'une modification le nomme pour désigner l'entrée. Une création l'ignore : le service génère l'identifiant.

##
## Propagation des modifications

Une écriture passe par le service, qui écrit en base via son DAO puis signale le changement avec `DataChangeNotifier` — les écrans qui en dépendent se rechargent. Il n'y a pas de journal d'événements : aucune table n'enregistre les modifications, et l'état en base est la seule source. Ce que l'utilisateur et l'IA lisent comme un historique, ce sont les entrées elles-mêmes et les sessions, pas une suite d'événements rejouable.

### Verbalisation

Système de templates pour actions, états et résultats :

```kotlin
// Template
"[source] [verb] le titre [old_value] en [new_value]"

// Résultat
"L'IA a modifié le titre Blup en Blip"
```

**Usage** : Historique, validation utilisateur, feedback IA.

##
## Versioning et Migrations

### Sources de vérité uniques

**build.gradle.kts** :
- `versionCode` = entier monotone (10, 11, 12...) pour comparaisons
- `versionName` = string SemVer ("0.3.0", "0.4.0"...) pour affichage utilisateur
- Accessibles via `BuildConfig.VERSION_CODE` et `BuildConfig.VERSION_NAME`

**AppDatabase** :
- `@Database(version = 10)` = version schéma SQL
- `companion object { const val VERSION = 10 }` = accessible à runtime
- Indépendante de versionCode (peut rester stable entre releases)

### Types de migrations

#### Migration SQL (Type 1)

**Quand** : Schéma SQL change (ALTER TABLE, CREATE TABLE, etc.)

**Géré par** : Room Migrations manuelles dans AppDatabase.kt

**Exemple** :
```kotlin
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE zones ADD COLUMN icon TEXT")
    }
}
```

**Flow** :
1. Incrémenter `@Database(version = 11)` et `const val VERSION = 11`
2. Écrire migration SQL dans AppDatabase companion object
3. Ajouter à `.addMigrations()` dans getDatabase()
4. Room détecte et exécute automatiquement au démarrage

#### Migration JSON (Type 2)

**Quand** : Format JSON non rétrocompatible (renommage champs, restructuration)

**Géré par** : JsonTransformers centralisé

**Exemple** : `{"unit": "kg"}` → `{"measurement_unit": "kg"}`

**Flow** :
1. Écrire transformation dans JsonTransformers.kt
2. Appliquée lors import backup automatiquement
3. Optionnellement appelable dans migration Room si nécessaire

### JsonTransformers - Architecture centralisée

**Fichier unique** : `core/versioning/JsonTransformers.kt`

**Fonctions publiques** :
```kotlin
transformToolConfig(json, tooltype, fromVersion, toVersion): String
transformToolData(json, tooltype, fromVersion, toVersion): String
transformAppConfig(json, fromVersion, toVersion): String
```

**Application séquentielle** : Boucle `for (v in fromVersion until toVersion)` applique tous les transformers intermédiaires

**Exemple** :
```kotlin
private fun transformTrackingConfig(json: JSONObject, version: Int): JSONObject {
    return when (version) {
        10 -> {
            // Migrate from v10 to v11
            if (json.has("unit")) {
                json.put("measurement_unit", json.getString("unit"))
                json.remove("unit")
            }
            json
        }
        else -> json // No migration for this version
    }
}
```

**Réutilisation** : Mêmes transformers utilisés par :
- **Migration Room** : Transforme JSONs en DB au démarrage (une fois par version)
- **Import backup** : Transforme JSONs de vieux backups
- Import partiel futur

**Application au démarrage** : Changement de format JSON REQUIERT migration Room pour transformer les données existantes. Les transformers sont appelés dans la migration pour mettre à jour les JSONs stockés.

**Lifecycle** : Transformers conservés indéfiniment (support vieux backups)

### Format export/import

**Metadata dans fichiers** :
```json
{
  "metadata": {
    "export_version": 10,
    "export_timestamp": 1234567890,
    "db_schema_version": 10
  },
  "data": { ... }
}
```

**Process import** :
1. Vérifier `export_version` vs `BuildConfig.VERSION_CODE`
2. Si `export_version > VERSION_CODE` → Erreur "version trop récente"
3. Si `export_version < VERSION_CODE` → Appliquer JsonTransformers
4. Insertion données transformées

**En DB** : JSONs purs sans metadata (version dans fichiers export uniquement)

### Règles de développement

**Versioning** :
- `BuildConfig.VERSION_CODE` = source unique version app
- `AppDatabase.VERSION` = version DB (indépendante de app version)
- Pas de metadata en DB (version dans fichiers export uniquement)

**Migrations SQL** :
- Migrations manuelles explicites dans AppDatabase
- Pas de fallbackToDestructiveMigration (migrations obligatoires)
- Migrations conservées historiquement

**Transformations JSON** :
- Centralisées dans JsonTransformers.kt
- Réutilisées partout (imports + migrations Room)
- Conservées indéfiniment (historique complet)
- Application séquentielle automatique

##
## Règles de Développement

### Patterns d'Implémentation

#### Service Implementation
- Hériter `ExecutableService`
- Validation par le service contre le schéma généré (voir « Validation par schéma »)
- Logs d'erreur explicites et gestion token cancellation

#### Discovery Pattern
- Jamais d'imports hardcodés dans Core
- Services découverts via ToolTypeManager
- Extension automatique par ajout au Scanner
- ToolTypes déclarent `getEntryFields` et `getConfigSettings` ; leurs schémas sont générés

#### Data Consistency
- Écriture par le service, puis notification via `DataChangeNotifier` (pas de journal d'événements)
- Validation centralisée par les services, contre des schémas générés

---

*L'architecture de données garantit cohérence, validation automatique et extensibilité via patterns découplés.*
