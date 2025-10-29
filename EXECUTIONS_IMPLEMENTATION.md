# Tool Executions - Document d'Implémentation

## Problème Initial

Le tooltype Messages stocke l'historique d'exécution dans un array JSON (`executions`) au sein du champ `data` de chaque entrée `tool_data`. Cette architecture pose problème :

1. **Croissance illimitée** : L'historique s'accumule indéfiniment dans le JSON
2. **Impact tokens IA** : Les enrichments POINTER/USE envoient tout l'historique aux prompts
3. **Non réutilisable** : D'autres tooltypes auront besoin d'exécutions (Objectif, Questionnaire, Alerte)
4. **Requêtes limitées** : Difficile de filtrer/paginer les exécutions depuis un JSON array

## Solution : Table Unifiée `tool_executions`

### Architecture Générale

Séparation **templates/config** (dans `tool_data`) des **exécutions** (dans nouvelle table `tool_executions`).

**Analogie stricte avec tool_data** :
- Pattern config/data/execution cohérent
- Schémas base + spécifiques combinés
- Services découverts dynamiquement
- Validation centralisée via SchemaValidator

## 1. Structure Database

### ToolExecutionEntity

```kotlin
@Entity(tableName = "tool_executions")
data class ToolExecutionEntity(
    @PrimaryKey val id: String,
    val toolInstanceId: String,
    val tooltype: String,
    val templateDataId: String,        // FK vers tool_data (le template)

    // Timing
    val scheduledTime: Long?,          // Null si trigger non planifié
    val executionTime: Long,           // Timestamp réel d'exécution

    // Status
    val status: String,                // "pending"|"completed"|"failed"|"cancelled"

    // Data (JSON)
    val snapshotData: String,          // Snapshot du template au moment de l'exécution
    val executionResult: String,       // Résultat spécifique au tooltype

    // Metadata
    val triggeredBy: String,           // "SCHEDULE"|"MANUAL"|"THRESHOLD"|"EVENT"
    val metadata: String,              // JSON - Infos supplémentaires (erreurs, contexte)

    val createdAt: Long,
    val updatedAt: Long
)
```

**Index requis** :
- `toolInstanceId` (requêtes par tool)
- `templateDataId` (requêtes par template)
- `executionTime` (tri chronologique, filtres temporels)
- `status` (filtres par statut)

### ToolExecutionDao

```kotlin
interface ToolExecutionDao {
    suspend fun insert(execution: ToolExecutionEntity)
    suspend fun update(execution: ToolExecutionEntity)
    suspend fun delete(execution: ToolExecutionEntity)
    suspend fun getById(id: String): ToolExecutionEntity?

    // Queries principales
    suspend fun getByToolInstance(toolInstanceId: String): List<ToolExecutionEntity>
    suspend fun getByTemplate(templateDataId: String): List<ToolExecutionEntity>
    suspend fun getByToolInstanceAndTimeRange(
        toolInstanceId: String,
        startTime: Long,
        endTime: Long
    ): List<ToolExecutionEntity>
    suspend fun getByStatus(toolInstanceId: String, status: String): List<ToolExecutionEntity>
}
```

## 2. Schémas de Validation

### Pattern BaseSchemas Extension

Suivre exactement le même pattern que `BaseSchemas.getBaseDataSchema()`.

**Nouveau** : `BaseSchemas.getBaseExecutionSchema(context)`

Champs communs à toutes les exécutions :
- `id`, `toolInstanceId`, `tooltype`, `templateDataId`
- `scheduledTime`, `executionTime`, `status`, `triggeredBy`
- `snapshotData`, `executionResult`, `metadata`

**Schémas spécifiques** : Chaque tooltype implémentant des exécutions fournit via `ToolTypeContract` :
- `getAllSchemaIds()` retourne aussi `["messages_execution", ...]`
- `getSchema("messages_execution", context)` retourne schéma combiné (base + spécifique)

### Exemple Messages

**snapshotData** (spécifique Messages) :
```json
{
  "title": "string",
  "content": "string",
  "priority": "default|high|low"
}
```

**executionResult** (spécifique Messages) :
```json
{
  "read": "boolean",
  "archived": "boolean",
  "notification_sent": "boolean"
}
```

## 3. Service Layer

### ToolExecutionService

Service Core découvert via `ServiceRegistry` (pattern identique à ToolDataService).

**Opérations** :
```kotlin
class ToolExecutionService(context: Context) : ExecutableService {
    // CRUD
    suspend fun create(params: JSONObject, token: CancellationToken): OperationResult
    suspend fun update(params: JSONObject, token: CancellationToken): OperationResult
    suspend fun delete(params: JSONObject, token: CancellationToken): OperationResult

    // Queries
    suspend fun get(params: JSONObject, token: CancellationToken): OperationResult
    suspend fun getSingle(params: JSONObject, token: CancellationToken): OperationResult

    // Aggregations
    suspend fun stats(params: JSONObject, token: CancellationToken): OperationResult

    // Batch (pour IA)
    suspend fun batchCreate(params: JSONObject, token: CancellationToken): OperationResult
    suspend fun batchUpdate(params: JSONObject, token: CancellationToken): OperationResult
}
```

**Commands mapping** :
- `tool_executions.create` → create()
- `tool_executions.get` → get() (avec pagination, filtres temporels, status)
- `tool_executions.update` → update()
- `tool_executions.stats` → stats()

**Params standardisés** :
- `toolInstanceId` : Requis pour get/stats
- `templateDataId` : Optionnel pour filtrer sur un template spécifique
- `startTime`, `endTime` : Filtres temporels
- `status` : Filtre par statut
- `limit`, `offset` : Pagination

## 4. Refactoring Tooltypes Existants

### Messages ToolType

**Changements schéma data** :
- Retirer champ `executions: array` du schéma `messages_data`
- Garder uniquement template : `content`, `schedule`, `priority`, `triggers`

**Nouveau schéma** :
- Ajouter `messages_execution` dans `getAllSchemaIds()`
- Implémenter `getSchema("messages_execution", context)` : base + spécifique

**Service operations** :
- Retirer `appendExecution()` (plus nécessaire)
- Refactorer `get_history`, `mark_read`, `mark_archived`, `stats` pour utiliser `tool_executions.get`

### MessageScheduler

**Changements** :
- Au lieu d'appender à JSON : appeler `coordinator.processUserAction("tool_executions.create", params)`
- `snapshotData` : snapshot du template (title, content, priority)
- `executionResult` : `{read: false, archived: false, notification_sent: true/false}`
- `status` : "pending" → "completed" ou "failed" selon résultat notification

## 5. POINTER/ZoneScopeSelector - Contextes de Sélection

### Problème

Avec les exécutions, la **sélection temporelle devient ambiguë** :
- Parle-t-on des templates (tool_data.timestamp) ?
- Ou des exécutions (tool_executions.executionTime) ?

### Solution : Contextes Explicites

**Nouveau concept** : `PointerContext`

```kotlin
enum class PointerContext {
    GENERIC,      // Référence floue (IA interprète)
    CONFIG,       // Configuration d'outils
    DATA,         // Données métier (tool_data)
    EXECUTIONS    // Exécutions planifiées (tool_executions)
}
```

### Flow UI Modifié

**Étapes ZoneScopeSelector** :
1. Sélection Zone (si `allowZoneSelection`)
2. Sélection Tool Instance (si `allowInstanceSelection`)
3. **Sélection Contexte** (GENERIC/CONFIG/DATA/EXECUTIONS)
4. Cases à cocher selon contexte (voir détails ci-dessous)
5. Sélection temporelle (si contexte != CONFIG)

### Cases à Cocher par Contexte

**Terminologie UI** : "Documentation" au lieu de "Schéma"

**GENERIC** :
- Aucune case à cocher (référence l'instance entière)
- Période : "Période de référence" (sélectionnable, interprétation par IA)

**CONFIG** :
- □ Configuration
- □ Documentation configuration
- Pas de période temporelle

**DATA** :
- ☑ Données (cochée par défaut, modifiable)
- □ Documentation données
- Période : "Période des données" (filtre sur `tool_data.timestamp`)

**EXECUTIONS** (uniquement si tooltype implémente executions) :
- ☑ Exécutions (cochée par défaut, modifiable)
- □ Documentation exécutions
- Période : "Période d'exécution" (filtre sur `tool_executions.executionTime`)

**Règle critique** : Seules les cases du contexte actif sont sauvegardées dans `SelectionResult`.

### NavigationConfig Extension

```kotlin
data class NavigationConfig(
    // Existant
    val allowZoneSelection: Boolean = true,
    val allowInstanceSelection: Boolean = true,
    val allowFieldSelection: Boolean = true,
    val allowValueSelection: Boolean = true,

    // Nouveau
    val allowedContexts: List<PointerContext> = listOf(GENERIC, CONFIG, DATA, EXECUTIONS),
    val defaultContext: PointerContext = GENERIC,

    // Reste inchangé
    val title: String = "",
    val showQueryPreview: Boolean = false,
    val showFieldSpecificSelectors: Boolean = true
)
```

### SelectionResult Extension

```kotlin
data class SelectionResult(
    val selectedPath: String,
    val selectionLevel: SelectionLevel,

    // Nouveau
    val selectedContext: PointerContext,
    val selectedResources: List<String>,        // ["data", "data_doc"] ou ["executions"] etc.
    val temporalFilter: TemporalFilter?,        // null pour CONFIG et GENERIC sans période

    // Existant (adapté)
    val selectedValues: List<String>,           // Valeurs de champs (si applicable)
    val fieldSpecificData: FieldSpecificData?
)

data class TemporalFilter(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val context: PointerContext  // Pour interpréter correctement (DATA vs EXECUTIONS)
)
```

**selectedResources valides par contexte** :
- GENERIC : `[]` (vide)
- CONFIG : `["config"]`, `["config_doc"]`, `["config", "config_doc"]`
- DATA : `["data"]`, `["data_doc"]`, `["data", "data_doc"]`
- EXECUTIONS : `["executions"]`, `["executions_doc"]`, `["executions", "executions_doc"]`

## 6. EnrichmentProcessor - Génération Commands

### Modifications POINTER Enrichment

**Avant** : Génère `TOOL_DATA` command → récupère config + data + stats

**Après** : Selon `selectedContext` dans enrichment config :
- CONFIG → `TOOL_CONFIG` command
- DATA → `TOOL_DATA` command
- EXECUTIONS → `TOOL_EXECUTIONS` command (nouveau)
- GENERIC → Plusieurs commands (CONFIG + DATA + EXECUTIONS si disponible)

### Nouveau Command Type

```kotlin
// Dans DataCommand
data class DataCommand(
    val id: String,
    val type: String,  // Ajouter "TOOL_EXECUTIONS"
    val params: Map<String, Any>,
    val isRelative: Boolean = false
)
```

**CommandTransformer mapping** :
- `TOOL_EXECUTIONS` → `tool_executions.get`
- Params : `toolInstanceId`, `startTime`, `endTime`, `limit`, `offset`

## 7. Migration Database

### Version Upgrade

Incrémenter `AppDatabase.VERSION` (ex: 13 → 14)

### Migration SQL

```kotlin
private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Créer table tool_executions
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS tool_executions (
                id TEXT PRIMARY KEY NOT NULL,
                toolInstanceId TEXT NOT NULL,
                tooltype TEXT NOT NULL,
                templateDataId TEXT NOT NULL,
                scheduledTime INTEGER,
                executionTime INTEGER NOT NULL,
                status TEXT NOT NULL,
                snapshotData TEXT NOT NULL,
                executionResult TEXT NOT NULL,
                triggeredBy TEXT NOT NULL,
                metadata TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)

        // 2. Créer index
        database.execSQL("CREATE INDEX idx_executions_tool ON tool_executions(toolInstanceId)")
        database.execSQL("CREATE INDEX idx_executions_template ON tool_executions(templateDataId)")
        database.execSQL("CREATE INDEX idx_executions_time ON tool_executions(executionTime)")
        database.execSQL("CREATE INDEX idx_executions_status ON tool_executions(status)")

        // 3. Migrer données existantes (Messages)
        // Récupérer toutes les entrées tool_data avec tooltype = 'messages'
        // Parser JSON executions array
        // Pour chaque execution : INSERT INTO tool_executions
        // UPDATE tool_data : retirer executions du JSON -> utiliser json transformer.
    }
}
```

**Logique migration détaillée** : Parser toutes les entrées Messages, extraire `executions` array, créer entrées `tool_executions`, nettoyer JSON `data`.

## 8. Extensibilité Future

### Tooltypes avec Exécutions

**Pour implémenter des exécutions dans un nouveau tooltype** :

1. Implémenter schéma execution dans `ToolTypeContract.getSchema()`
2. Définir structure `snapshotData` et `executionResult` spécifiques
3. Utiliser `tool_executions.create` dans scheduler/triggers
4. Pas de modification Core nécessaire (discovery pattern)

**Exemples futurs** :

**Objectif** :
- `snapshotData` : objectif + critères + poids
- `executionResult` : score, criteriaResults, achieved

**Questionnaire** :
- `snapshotData` : questions template
- `executionResult` : answers array

**Alerte** :
- `snapshotData` : règle + seuils
- `executionResult` : triggered, values, message

### ToolTypeContract Extension

**Méthode optionnelle** :
```kotlin
interface ToolTypeContract {
    // Existant...

    fun supportsExecutions(): Boolean = false  // Default false
}
```

Usage : ZoneScopeSelector affiche contexte EXECUTIONS uniquement si `toolType.supportsExecutions() == true`.

## 9. Plan d'Implémentation Recommandé

### Phase 1 : Backend Core
1. Créer `ToolExecutionEntity` + `ToolExecutionDao`
2. Créer `ToolExecutionService` avec operations CRUD
3. Ajouter à `ServiceRegistry` (resource = "tool_executions")
4. Créer `BaseSchemas.getBaseExecutionSchema()`

### Phase 2 : Messages Refactoring
5. Ajouter schéma `messages_execution` dans `MessageToolType`
6. Retirer champ `executions` du schéma `messages_data`
7. Refactorer `MessageScheduler` (utiliser `tool_executions.create`)
8. Refactorer `MessageService` operations (utiliser `tool_executions.get`)
9. Mettre à jour `MessagesScreen` UI

### Phase 3 : Migration DB
10. Créer migration SQL (table + index)
11. Implémenter logique migration données existantes
12. Tester migration sur backup

### Phase 4 : POINTER Extension
13. Ajouter enum `PointerContext`
14. Refactorer `ZoneScopeSelector` UI (sélecteur contexte + cases à cocher dynamiques)
15. Étendre `NavigationConfig` et `SelectionResult`
16. Adapter labels temporels selon contexte
17. Mettre à jour `EnrichmentProcessor` (génération commands selon contexte)
18. Adapter `CommandTransformer` (mapping TOOL_EXECUTIONS)

### Phase 5 : Tests & Polish
19. Tester compilation complète
20. Tester flow Messages complet (création → scheduling → exécution)
21. Tester POINTER avec différents contextes
22. Vérifier prompts IA (pas d'explosion tokens)

## 10. Points de Vigilance

### Cohérence Pattern

**Absolument respecter** le même pattern que `tool_data` :
- Structure Entity identique (JSON fields)
- Schémas base + spécifiques combinés
- Service operations standard (CRUD + batch)
- Validation via SchemaValidator
- Discovery pattern (pas d'imports hardcodés)

### Strings Localization

**Nouveaux strings requis** :
- `core/strings/sources/shared.xml` : labels contextes, périodes, actions executions
- `tools/messages/strings.xml` : labels spécifiques Messages executions

**Catégories** :
- `label_context_*` : generic, config, data, executions
- `label_period_*` : reference, data, execution
- `action_*` : mark_completed, mark_failed, retry_execution

### DataNavigator Extension

`DataNavigator.getRootNodes()` doit inclure "Executions" comme nœud racine si des tooltypes supportent executions.

**Structure hiérarchique** :
```
App
├── Zones
│   └── Tools
│       ├── Config
│       ├── Data
│       └── Executions (si supporté)
└── ...
```

### Backward Compatibility

Migration DB doit être **réversible conceptuellement** :
- Garder ancienne structure en commentaire
- Logger toutes les transformations
- Backup automatique avant migration
