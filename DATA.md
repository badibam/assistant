# Gestion des Données

Guide technique pour la navigation, validation et manipulation des données dans l'architecture Assistant.

## ═══════════════════════════════════
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
    val allowZoneSelection: Boolean = true,          // Peut-on confirmer aux zones ?
    val allowInstanceSelection: Boolean = true,      // Peut-on confirmer aux instances ?
    val allowFieldSelection: Boolean = true,         // Peut-on confirmer aux champs ? (deprecated, non utilisé)
    val allowValueSelection: Boolean = true,         // Naviguer vers valeurs ? (deprecated, non utilisé)

    // Context-aware selection
    val allowedContexts: List<PointerContext> = listOf(
        PointerContext.GENERIC,
        PointerContext.CONFIG,
        PointerContext.DATA,
        PointerContext.EXECUTIONS
    ),                                               // Contextes disponibles
    val defaultContext: PointerContext = PointerContext.GENERIC,  // Contexte par défaut

    // UI configuration
    val title: String = "",                          // Titre custom ou scope_selector_title par défaut
    val useRelativeLabels: Boolean = false           // true pour AUTOMATION, false pour CHAT
)
```

#### PointerContext

Contexte explicite pour désambiguïser les requêtes de données :

- **GENERIC** : Référence floue, aucun command automatique, période optionnelle pour contexte IA
- **CONFIG** : Configuration d'outils, pas de période temporelle
- **DATA** : Données métier (tool_data), période optionnelle sur `tool_data.timestamp`
- **EXECUTIONS** : Historique d'exécutions (tool_executions), période optionnelle sur `tool_executions.executionTime`

**Filtrage dynamique** : EXECUTIONS visible uniquement si `toolType.supportsExecutions() == true`.

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
        PointerContext.DATA,
        PointerContext.EXECUTIONS
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
    val selectedPath: String,                        // Chemin complet sélectionné
    val selectionLevel: SelectionLevel,              // Niveau d'arrêt (ZONE ou INSTANCE)

    // Context-aware selection
    val selectedContext: PointerContext,             // Contexte sélectionné
    val selectedResources: List<String>,             // Ressources cochées (ex: ["data", "data_schema"])

    // Field-level selection (deprecated, non utilisé)
    val selectedValues: List<String> = emptyList(),
    val fieldSpecificData: FieldSpecificData? = null,
    val displayChain: List<String> = emptyList()     // Labels lisibles pour affichage
)
```

**Niveaux de sélection** : ZONE, INSTANCE

**Ressources par contexte** :
- GENERIC : `[]` (vide, pas de query automatique)
- CONFIG : `["config", "config_schema"]` disponibles
- DATA : `["data", "data_schema"]` disponibles, `["data"]` coché par défaut
- EXECUTIONS : `["executions", "executions_schema"]` disponibles, `["executions"]` coché par défaut

**Périodes** : Stockées dans `timestampSelection` (non visible dans SelectionResult, géré en interne par ZoneScopeSelector)

## ═══════════════════════════════════
## Validation par Schema ID

### Architecture Schema

Système de validation basé sur identifiants de schémas explicites avec objets Schema autonomes.

```kotlin
data class Schema(
    val id: String,         // "tracking_config_numeric"
    val content: String     // JSON Schema complet
)
```

### Patterns de Validation

#### Configuration d'outils

Le `schema_id` est intégré dans les données de configuration au même niveau que les champs métier :

```kotlin
val configData = mapOf(
    "schema_id" to "tracking_config_numeric",      // Pour validation
    "data_schema_id" to "tracking_data_numeric",   // Pour usage runtime
    "name" to "Mon suivi",
    "type" to "numeric"
)

// Validation via helper unifié
UI.ValidationHelper.validateAndSave(
    toolTypeName = "tracking",
    configData = configData,
    context = context,
    schemaType = "config",
    onSuccess = { configJson -> /* sauvegarde */ }
)
```

#### Données d'entrée

Le `schema_id` est passé au niveau des paramètres de service, séparé du JSON des données :

```kotlin
val params = mapOf(
    "toolInstanceId" to toolInstanceId,
    "tooltype" to "tracking",
    "schema_id" to "tracking_data_numeric",   // Pour validation service
    "data" to JSONObject(dataJson)            // JSON propre sans schema_id
)

coordinator.processUserAction("tool_data.create", params)
```

### Validation Service

ToolDataService récupère le `schema_id` depuis les paramètres et l'ajoute à la structure de validation :

```kotlin
val schemaId = params.optString("schema_id")
if (schemaId.isNotEmpty()) {
    fullDataMap["schema_id"] = schemaId  // Ajout au niveau racine
}
val schema = toolType.getSchema(schemaId, context)
SchemaValidator.validate(schema, fullDataMap, context)
```

### Schémas de Base

BaseSchemas définit les champs communs incluant les identifiants de schémas :

**Configuration** : `schema_id`, `data_schema_id`, `name`, `description`, `management`, `display_mode`, etc.

**Données** : `schema_id`, `tool_instance_id`, `tooltype`, `name`, `timestamp`, `created_at`, etc.

### Validation Partielle (Updates)

**Principe** : UPDATEs peuvent fournir uniquement les champs modifiés. Mode `partialValidation` ignore les contraintes `required`.

**UI vs IA** :
- **UI** : Charge entrée complète → envoie toutes données → `partialValidation = false`
- **IA** : Envoie uniquement champs modifiés → `partialValidation = true`

**Implémentation** :
```kotlin
// ActionValidator détecte automatiquement
val partialValidation = operation in listOf("update", "batch_update")
SchemaValidator.validate(schema, data, context, partialValidation)
```

Mode partial retire `required` arrays du schéma (récursivement), valide types/formats des champs présents. Service merge avec données existantes.

**Champ `id`** : NOT systemManaged (nécessaire pour identifier l'entrée). Strippé manuellement pour CREATE_DATA uniquement.

### ValidationHelper

API centralisée pour validation de configuration avec extraction automatique du schema_id :

```kotlin
object ValidationHelper {
    fun validateAndSave(
        toolTypeName: String,
        configData: Map<String, Any>,
        context: Context,
        schemaType: String,
        onSuccess: (String) -> Unit
    ): Boolean
}
```

## ═══════════════════════════════════
## Event Sourcing

Toutes les modifications passent par des événements pour garantir cohérence et traçabilité.

### Avantages

- Logging automatique des modifications possible
- Cohérence sans synchronisation manuelle
- Historique pour IA et audit

### Schémas Auto-descriptifs

```kotlin
// Récupération et validation via schema ID
val schema = toolType.getSchema(schemaId, context)
val validation = SchemaValidator.validate(schema, data, context)
```

### Verbalisation

Système de templates pour actions, états et résultats :

```kotlin
// Template
"[source] [verb] le titre [old_value] en [new_value]"

// Résultat
"L'IA a modifié le titre Blup en Blip"
```

**Usage** : Historique, validation utilisateur, feedback IA.

## ═══════════════════════════════════
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

## ═══════════════════════════════════
## Règles de Développement

### Patterns d'Implémentation

#### Service Implementation
- Hériter `ExecutableService`
- **Configs** : utiliser `UI.ValidationHelper.validateAndSave()` avec schema_id dans les données
- **Données** : ajouter `schema_id` aux paramètres service, récupérer via `params.optString("schema_id")`
- Validation directe via `SchemaValidator.validate(schema, data, context)`
- Logs d'erreur explicites et gestion token cancellation

#### Schema ID Management
- **Configuration** : `schema_id` et `data_schema_id` ajoutés automatiquement lors du nettoyage des données
- **Données d'entrée** : `schema_id` calculé selon pattern `${tooltype}_data_${type}` dans InputManager
- **Service validation** : schema_id ajouté à `fullDataMap` au niveau racine pour validation

#### Discovery Pattern
- Jamais d'imports hardcodés dans Core
- Services découverts via ToolTypeManager
- Extension automatique par ajout au Scanner
- ToolTypes implémentent `getSchema(schemaId, context): Schema?`

#### Data Consistency
- Event sourcing obligatoire pour modifications
- Validation centralisée via objets Schema explicites
- Schémas autonomes avec ID déterministes

---

*L'architecture de données garantit cohérence, validation automatique et extensibilité via patterns découplés.*
