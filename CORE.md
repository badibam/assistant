# Architecture Core

Guide technique de l'architecture système centrale.

## ═══════════════════════════════════
## Flow Principal : UI → Coordinator → Service

```kotlin
// UI déclenche
coordinator.processUserAction("create->zone", mapOf("name" to name))

// Coordinator route automatiquement  
executeServiceOperation(command, "zone_service", "create")

// Service exécute avec validation
service.execute("create", params, cancellationToken)
```

### Pattern de Coordination

**Principe** : Toute modification passe par le Coordinator pour cohérence IA/scheduler.

**Services Core** (hardcodés) :
- `zone_service` → ZoneService  
- `tool_instance_service` → ToolInstanceService

**Services Tools** (découverts dynamiquement) :
- `tracking_service` → TrackingService
- Pattern : `{toolType}_service` → ToolTypeManager.getServiceForToolType()

### Gestion des Tokens

```kotlin
if (token.isCancelled) return OperationResult.cancelled()
```

Chaque opération reçoit un token d'annulation.

## ═══════════════════════════════════  
## Discovery Pattern

Architecture sans imports hardcodés dans Core.

### ServiceManager Générique

```kotlin
// Core services
"zone_service" -> ZoneService(context)
"tool_instance_service" -> ToolInstanceService(context)

// Tool services (discovery)
else -> {
    val toolTypeId = serviceName.removeSuffix("_service")
    ToolTypeManager.getServiceForToolType(toolTypeId, context)
}
```

### ToolTypeManager

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

Aucune modification Core nécessaire.

## ═══════════════════════════════════
## Gestion des Données

### Event Sourcing

Toutes les modifications passent par des événements :

- Logging automatique des modifications
- Cohérence sans synchronisation manuelle  
- Historique pour IA et audit

### Schémas JSON Auto-descriptifs

```kotlin
// Validation et navigation IA sans accès aux données
val schema = toolType.getConfigSchema()
val validation = toolType.validateData(data, operation)
```

### Databases Standalone

**Structure** : 1 database par tool type pour discovery pure
- TrackingDatabase → TrackingData
- JournalDatabase → JournalData  
- Pas de foreign keys, indices de performance seulement

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

## ═══════════════════════════════════
## Validation JSON Schema V3

Architecture centralisée de validation basée sur JSON Schema avec traduction automatique.

### Structure Validation

```
app/src/main/java/com/assistant/core/validation/
├── SchemaValidator.kt           ← API principale
├── ValidationErrorProcessor.kt  ← Traitement erreurs  
├── SchemaProvider.kt            ← Interface schémas
└── ValidationResult.kt          ← Data class résultat
```

### API Unifiée

```kotlin
// Usage standard pour tous types d'outils
val toolType = ToolTypeManager.getToolType("tracking")
val result = SchemaValidator.validate(toolType, data, context, useDataSchema = true)

if (result.isValid) {
    // Validation réussie
} else {
    // Erreur traduite en français : result.errorMessage
}
```

### Interface SchemaProvider

Tous les ToolTypes implémentent SchemaProvider :

```kotlin
interface SchemaProvider {
    fun getConfigSchema(): String        // Schéma configuration outil
    fun getDataSchema(): String?         // Schéma données métier
    fun getFormFieldName(String): String // Traductions champs
}
```

### Fonctionnalités Automatiques

- **Filtrage valeurs vides** : Supprime `""` et `null` avant validation
- **Traduction erreurs** : Messages français avec noms traduits  
- **Schémas conditionnels** : Support `allOf/if/then` natif
- **Cache performance** : Schémas mis en cache automatiquement

### Types de Validation

```kotlin
// Validation configuration outil (création/modification)
SchemaValidator.validate(toolType, configData, context, useDataSchema = false)

// Validation données métier (entries)  
SchemaValidator.validate(toolType, entryData, context, useDataSchema = true)
```

## SchemaValidator V3 + Schémas Externes 🆕

**Pattern Validation Unifié** : Validation au clic + Toast + Schémas JSON externes

```kotlin
val handleSave = {
    val validation = SchemaValidator.validate(provider, data, context)
    if (validation.isValid) {
        // Sauvegarder
    } else {
        Toast.makeText(context, validation.errorMessage, LENGTH_LONG).show()
    }
}
```

**Architecture** : Schémas externalisés en objets Kotlin

## ═══════════════════════════════════
## Système de Strings Modulaire

Architecture unifiée pour internationalisation avec discovery pattern et génération automatique.

### Structure
- **Sources** : `core/strings/sources/shared.xml` + `tools/*/strings.xml`
- **Génération** : Script Gradle → `res/values/tool_strings_generated.xml`
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
- **content_*** : app_title, tool_description, unnamed, etc.
- **month_*** : january, february, march, etc.

### Échappement Automatique
Le script Gradle gère automatiquement :
- Apostrophes : `L'année` → `L\'année`
- Guillemets : `"texte"` → `\"texte\"`
- Placeholders : `%s` → `%1$s`, `%d` → `%1$d`

## ═══════════════════════════════════

---

*L'architecture Core garantit extensibilité et cohérence sans complexité excessive.*
