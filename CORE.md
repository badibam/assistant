# Architecture Core

Guide technique de l'architecture système centrale.

## ═══════════════════════════════════
## Flow Principal : UI → CommandDispatcher → Service

```kotlin
// UI déclenche (nouveau pattern resource.operation)
coordinator.processUserAction("zones.create", mapOf("name" to name))

// CommandDispatcher parse et route directement
val (resource, operation) = command.parseAction()  // "zones", "create"
val service = serviceRegistry.getService(resource)  // ZoneService
service.execute(operation, params, token)           // Direct dispatch
```

### Pattern CommandDispatcher

**Principe** : Architecture unifiée `resource.operation` pour UI, IA, Scheduler et System.

**Format standardisé** :
- `zones.create` - Créer une zone
- `tools.list` - Lister les outils d'une zone  
- `tool_data.get` - Récupérer des données d'outil
- `app_config.get` - Configuration application

### ServiceRegistry

**Services Core** (ServiceRegistry) :
- `zones` → ZoneService
- `tools` → ToolInstanceService  
- `tool_data` → ToolDataService
- `app_config` → AppConfigService
- `icon_preload` → IconPreloadService
- `backup` → BackupService

**Services Tools** (découverte dynamique) :
- `tracking` → ToolTypeManager.getServiceForToolType()
- Pattern : `{toolType}` → Service générique ou spécialisé

### Dispatch des commandes

1. **Parse** : `"zones.create"` → `resource="zones"`, `operation="create"`
2. **Resolve** : `ServiceRegistry.getService("zones")` → `ZoneService`
3. **Execute** : `service.execute("create", params, token)`

### Nommage des Paramètres par Service

**ATTENTION** : Chaque service utilise ses propres conventions de nommage :

- **`tools.*`** (ToolInstanceService) : `tool_instance_id` (underscore)
- **`tool_data.*`** (ToolDataService) : `toolInstanceId` (camelCase)

```kotlin
// Récupérer instance d'outil
coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to id))

// Récupérer données de l'outil
coordinator.processUserAction("tool_data.get", mapOf("toolInstanceId" to id))
```

### Opérations CRUD Complètes ToolDataService

**Opérations disponibles** :
- `create` - Créer nouvelle entrée
- `update` - Modifier entrée existante
- `delete` - Supprimer entrée
- `get` - Récupérer toutes les entrées (avec pagination)
- **`get_single`** - Récupérer une entrée par ID ⚠️
- `stats` - Statistiques du tool
- `delete_all` - Supprimer toutes les entrées

```kotlin
// Récupérer une entrée spécifique pour édition
coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to entryId))
```

### Gestion des Tokens

```kotlin
if (token.isCancelled) return OperationResult.cancelled()
```

Chaque opération reçoit automatiquement un `CancellationToken` unique :
- **Création** : Générée par CommandDispatcher
- **Stockage** : `ConcurrentHashMap<String, CancellationToken>`
- **Nettoyage** : Suppression automatique en `finally`

## ═══════════════════════════════════  
## Discovery Pattern

Architecture sans imports hardcodés dans Core, avec ServiceRegistry centralisé.

### ServiceRegistry + ServiceFactory

```kotlin
// ServiceRegistry.kt
private val coreServices = mapOf<String, KClass<*>>(
    "zones" to ZoneService::class,
    "tools" to ToolInstanceService::class,
    "tool_data" to ToolDataService::class,
    // ...
)

fun getService(resource: String): ExecutableService? {
    // Core services via ServiceFactory
    return coreServices[resource]?.let { ServiceFactory.create(it, context) }
        // Tool services via discovery
        ?: ToolTypeManager.getServiceForToolType(resource, context)
}
```

### ToolTypeManager (inchangé)

API unifiée pour découverte dynamique :

```kotlin
// Service et DAO discovery
val service = ToolTypeManager.getServiceForToolType("tracking", context)
val dao = ToolTypeManager.getDaoForToolType("tracking", context)

// Métadonnées
val name = ToolTypeManager.getToolTypeName("tracking") // "Suivi"
val allTypes = ToolTypeManager.getAllToolTypes()
```

### Extension Automatique

**Nouveau tool type** :
1. Service implémente `ExecutableService`
2. ToolType implémente `ToolTypeContract` 
3. Ajouter au `ToolTypeScanner`
4. **Usage** : `coordinator.processUserAction("{toolType}.operation", params)`

Aucune modification Core nécessaire.

## ═══════════════════════════════════
## Gestion des Données

**Voir DATA.md** pour navigation hiérarchique, validation et patterns de données.

## ═══════════════════════════════════
## Operations Multi-Étapes

Système pour opérations lourdes en 3 phases.

### Architecture

**2 Canaux** : 
- Queue normale (bloquant)
- 1 slot background (calcul lourd)

**Flow** : Phase 1 (lecture) → Phase 2 (calcul background) → Phase 3 (écriture)

### Implémentation Service

```kotlin
class HeavyService(context: Context) : ExecutableService {
    private val tempData = ConcurrentHashMap<String, Any>()
    
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        val operationId = params.optString("operationId")
        val phase = params.optInt("phase", 1)
        
        return when (operation) {
            "heavy_calc" -> when (phase) {
                1 -> {
                    val data = loadData()
                    tempData[operationId] = data
                    OperationResult.success(requiresBackground = true)
                }
                2 -> {
                    val result = heavyCalculation(tempData[operationId])  
                    tempData[operationId] = result
                    OperationResult.success(requiresContinuation = true)
                }
                3 -> {
                    saveResult(tempData[operationId])
                    tempData.remove(operationId)
                    OperationResult.success()
                }
            }
        }
    }
}
```

### Règles

- **FIFO strict** : Ordre des opérations respecté
- **1 seul slot background** : Évite surcharge système  
- **Re-queue automatique** : Si slot occupé → fin de queue
- **Données temporaires** : Gérées par le service

## ═══════════════════════════════════
## Migrations Automatiques

### Types de Migrations

**Database** : Schéma Room découvert automatiquement par outil
**Configuration** : Format JSON des outils  
**Application** : Données globales

### Pattern ToolType

```kotlin
override fun getDatabaseMigrations(): List<Migration> {
    return listOf(TRACKING_MIGRATION_2_3)
}

val TRACKING_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tool_data ADD COLUMN category TEXT DEFAULT ''")
    }
}
```

### Gestion Versions

```kotlin
// AppVersionManager.kt
const val CURRENT_APP_VERSION = 2

// build.gradle.kts  
versionCode = 2
versionName = "0.1.1"
```

**Migration** : Si `CURRENT_APP_VERSION` > version stockée → exécution migrations

## ═══════════════════════════════════
## Règles de Développement

### Service Implementation

- Hériter `ExecutableService`
- Validation via `SchemaValidator.validate(toolType, data, context, useDataSchema)`
- Logs d'erreur explicites
- Gestion token cancellation

### Discovery Pattern

- Jamais d'imports hardcodés dans Core
- Services découverts via ToolTypeManager
- Extension automatique par ajout au Scanner

### Data Consistency  

- Event sourcing obligatoire pour modifications
- **Validation centralisée** : SchemaValidator pour config/data
- Schémas JSON pour validation automatique
- Standalone databases pour discovery

### CommandDispatcher Extensions Pattern

```kotlin
// Utiliser les extensions pour réduire boilerplate
coordinator.executeWithLoading(
    operation = "tool_data.get",  // Nouveau pattern resource.operation
    params = mapOf("tool_instance_id" to toolId),
    onLoading = { isLoading = it },
    onError = { errorMessage = it }
)?.let { result ->
    data = result.mapData("entries") { map -> DataEntry(map) }
}

// Smart cast automatique avec result.isSuccess
when {
    result.isSuccess -> { /* succès garanti */ }
    else -> { /* erreur */ }
}
```

### Pattern de commandes

```kotlin

coordinator.processUserAction("zones.create", params)
coordinator.processAICommand("tools.list", params)
coordinator.processScheduledTask("backup.create", params)
```

## ═══════════════════════════════════
## Validation des Données

**Voir DATA.md** pour SchemaValidator V3, validation centralisée et patterns de données.

## ═══════════════════════════════════
## Système de Strings Modulaire

Architecture unifiée pour internationalisation avec discovery pattern et génération automatique.

### Structure
- **Sources** : `core/strings/sources/shared.xml` + `tools/*/strings.xml`
- **Génération** : Script Gradle → `res/values/strings_generated.xml`
- **API** : `Strings.for(context)` pour shared, `Strings.for(tool = "tracking", context)` pour tools

### Usage
```kotlin
import com.assistant.core.strings.Strings

// Strings shared
val s = remember { Strings.`for`(context = context) }
s.shared("action_save")           // shared_action_save
s.shared("label_name_zone")       // shared_label_name_zone

// Strings tool-specific
val s = remember { Strings.`for`(tool = "tracking", context = context) }
s.tool("display_name")            // tracking_display_name  
s.shared("action_cancel")         // shared_action_cancel (toujours accessible)
```

### Catégories Shared
- **action_*** : save, create, cancel, delete, validate, confirm, etc.
- **label_*** : name_zone, description, icon, enabled, disabled, etc.
- **message_*** : loading_tools, validation_error, no_zones_created, etc.
- **period_*** : today, yesterday, this_week, last_month, etc.
- **content_*** : app_name, tool_description, unnamed, etc.
- **month_*** : january, february, march, etc.

### Échappement Automatique
Le script Gradle gère automatiquement :
- Apostrophes : `L'année` → `L\'année`
- Guillemets : `"texte"` → `\"texte\"`
- Placeholders : `%s` → `%1$s`, `%d` → `%1$d`

### Strings Android
**Format requis** : Placeholders numérotés `%1$s`, `%2$s` et pas `%s`
```xml
<!-- Correct -->
<string name="type_change">Le type passe de "%1$s" à "%2$s"</string>
```
## ═══════════════════════════════════
## Système de Logs Unifié

Architecture centralisée pour tous les logs du projet avec tags structurés et gestion d'erreurs robuste.

### LogManager Centralisé

**Emplacement** : `core/utils/LogManager.kt`

**Tags disponibles** :
- `Schema` - Validation JSON Schema et schémas
- `Coordination` - CommandDispatcher et orchestration
- `Tracking` - Tool type tracking et données métier
- `Database` - Base de données et migrations
- `UI` - Interface utilisateur
- `Service` - Services core et discovery

### API Standardisée

```kotlin
// Niveau DEBUG par défaut
LogManager.schema("Schema validation start")

// Avec niveau spécifique
LogManager.coordination("Operation failed", "ERROR", throwable)

// Niveaux : DEBUG (défaut), INFO, WARN, ERROR
LogManager.tracking("Invalid data format", "WARN")
LogManager.database("Migration completed", "INFO")
```

### Gestion d'Erreurs Robuste

- **try/catch** automatique avec fallback `println()` pour tests unitaires
- **Throwable optionnel** : Inclut stack trace complète si fournie
- **Compatibility** : Fonctionne en environnement Android et tests

### Guide de Debugging Service Resolution

**Problèmes courants et diagnostics** :

1. **"Service not found"**
   ```kotlin
   // Ajouter logs dans le service
   LogManager.service("Service called: $operation with params: $params")
   ```

2. **"Tool instance ID is required"**
   - Vérifier noms des paramètres : `tool_instance_id` vs `toolInstanceId`
   - Logs ToolDataService affichent les paramètres reçus

3. **Mauvais routing entre services**
   ```kotlin
   // CommandDispatcher logs automatiquement
   LogManager.coordination("Routing $resource.$operation to ${service::class.simpleName}")
   ```

4. **LaunchedEffect ne se redéclenche pas**
   - Ajouter TOUTES les variables vérifiées dans le scope aux dépendances
   ```kotlin
   // ❌ INCORRECT
   LaunchedEffect(toolInstanceId) {
       if (toolInstance != null) { ... }
   }

   // ✅ CORRECT
   LaunchedEffect(toolInstanceId, toolInstance) {
       if (toolInstance != null) { ... }
   }
   ```

## ═══════════════════════════════════

---

*L'architecture Core garantit extensibilité et cohérence sans complexité excessive.*
