# Architecture Core

Guide technique de l'architecture système centrale.

## Flow Principal : UI → CommandDispatcher → Service

### Pattern CommandDispatcher
**Principe** : Architecture unifiée resource.operation pour UI, IA, Scheduler et System.

**Format standardisé** :
- zones.create - Créer une zone
- tools.list - Lister les outils d'une zone
- tool_data.get - Récupérer des données d'outil
- app_config.get - Configuration application

### ServiceRegistry
**Services Core** : zones → ZoneService, tools → ToolInstanceService, tool_data → ToolDataService, app_config → AppConfigService, icon_preload → IconPreloadService, backup → BackupService, ai_provider_config → AIProviderConfigService, transcription → TranscriptionService, transcription_provider_config → TranscriptionProviderConfigService, notifications → NotificationService

**Services Tools** (découverte dynamique) : tracking → ToolTypeManager.getServiceForToolType()

### Dispatch des commandes
1. **Parse** : "zones.create" → resource="zones", operation="create"
2. **Resolve** : ServiceRegistry.getService("zones") → ZoneService
3. **Execute** : service.execute("create", params, token)

### Nommage des Paramètres par Service
**ATTENTION** : Chaque service utilise ses propres conventions :
- **tools.*** (ToolInstanceService) : tool_instance_id (underscore)
- **tool_data.*** (ToolDataService) : toolInstanceId (camelCase)

### Opérations CRUD Complètes ToolDataService
**Opérations disponibles** : create, update, delete, get (avec pagination), get_single (par ID), stats, delete_all, batch_create, batch_update, batch_delete

**Opérations batch** : Toutes les opérations de données IA utilisent par défaut les opérations batch pour efficacité. Les batch réutilisent la logique des opérations unitaires pour cohérence.

### Gestion des Tokens
Chaque opération reçoit automatiquement un CancellationToken unique avec création par CommandDispatcher, stockage en ConcurrentHashMap et nettoyage automatique.

### DataChangeNotifier
**Principe** : Notification réactive des modifications de données pour rechargement automatique UI.

**Events** : ZonesChanged, ToolsChanged(zoneId), ToolDataChanged(toolInstanceId, zoneId), AppConfigChanged

**Usage services** : Les services appellent automatiquement les méthodes notify* après modifications (create/update/delete)

**Usage UI** : LaunchedEffect collecte DataChangeNotifier.changes pour recharger données quand pertinent

## Scheduling Centralisé

### CoreScheduler
**Principe** : Point d'entrée unique pour scheduling AI + Tools via discovery pattern.

**Heartbeat** : Coroutine 1 min (app-open) + WorkManager 15 min (app-closed)
**Triggers** : Périodiques + événementiels (CRUD automations, CRUD messages, fin session)

**Découverte** : `ToolTypeManager.getAllToolTypes().forEach { toolType.getScheduler()?.checkScheduled() }`

### ToolScheduler Interface
```kotlin
interface ToolScheduler {
    suspend fun checkScheduled(context: Context)
}
```
Intégré à `ToolTypeContract.getScheduler(): ToolScheduler?` (défaut null)

**Extension outils** : Retourner scheduler dans ToolTypeContract, aucune modification core nécessaire.

## Discovery Pattern

Architecture sans imports hardcodés dans Core, avec ServiceRegistry centralisé.

### ServiceRegistry + ServiceFactory
Core services via mapOf avec KClass, fallback sur ToolTypeManager pour tool services.

### ToolTypeManager
API unifiée pour découverte dynamique : getServiceForToolType(), getDaoForToolType(), getToolTypeName(), getAllToolTypes()

### Extension Automatique
**Nouveau tool type** :
1. Service implémente ExecutableService
2. ToolType implémente ToolTypeContract
3. Ajouter au ToolTypeScanner
4. **Usage** : coordinator.processUserAction("{toolType}.operation", params)

Aucune modification Core nécessaire.

## Gestion des Données

**Voir DATA.md** pour navigation hiérarchique, validation et patterns de données.

## Operations Multi-Étapes

Système pour opérations lourdes en 3 phases.

### Architecture
**2 Canaux** : Queue normale (bloquant) + 1 slot background (calcul lourd)
**Flow** : Phase 1 (lecture) → Phase 2 (calcul background) → Phase 3 (écriture)

### Implémentation Service
Service avec tempData ConcurrentHashMap, operationId et phase dans params, OperationResult avec requiresBackground/requiresContinuation.

### Règles
- **FIFO strict** : Ordre des opérations respecté
- **1 seul slot background** : Évite surcharge système
- **Re-queue automatique** : Si slot occupé → fin de queue
- **Données temporaires** : Gérées par le service

## Règles de Développement

### Service Implementation
- Hériter ExecutableService
- Validation via SchemaValidator.validate(toolType, data, context, useDataSchema)
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

### CommandDispatcher Extensions Pattern
coordinator.executeWithLoading() avec operation, params, onLoading, onError et result.mapData().
Vérification status avec result.status == CommandStatus.SUCCESS.

### Pattern de commandes
coordinator.processUserAction(), processAICommand(), processScheduledTask() avec resource.operation

### Pattern d'utilisation du Coordinator - Référence

**Imports requis** :
```kotlin
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.strings.Strings
```

**Exemples** :
```kotlin
// Pattern basique avec gestion d'erreur (CommandResult)
val result = coordinator.processUserAction("resource.operation", mapOf(
    "param1" to value1,
    "param2" to value2
))

if (result.status == CommandStatus.SUCCESS) {
    // Traitement succès
    val data = result.data?.get("field") as? Type
} else {
    // Gestion erreur
    LogManager.service("Operation failed: ${result.error}", "ERROR")
    errorMessage = result.error ?: s.shared("error_operation_failed")
}

// Pattern UI avec loading/error states
coordinator.executeWithLoading(
    operation = "resource.operation",
    params = mapOf("id" to itemId),
    onLoading = { isLoading = it },
    onError = { error -> errorMessage = error }
)?.let { result ->
    // Traitement résultat
    data = result.mapSingleData<Type>("field")
}
```

### Pattern gestion d'erreur avec strings

**Règles** :
- JAMAIS de messages hardcodés - toujours utiliser s.shared() ou s.tool()
- Utiliser `result.error` du service (déjà traduit) en priorité
- Fournir un fallback traduit via s.shared() si result.error est null
- Pour les exceptions, préfixer avec string traduit : `"${s.shared("error_key")}: ${e.message}"`

**Exemple** :
```kotlin
try {
    val result = coordinator.processUserAction("backup.export", emptyMap())

    if (result.status == CommandStatus.SUCCESS) {
        val data = result.data?.get("json_data") as? String
        if (data != null) {
            // Success handling
            Toast.makeText(context, s.shared("backup_export_success"), Toast.LENGTH_SHORT).show()
        } else {
            errorMessage = s.shared("backup_export_no_data")
        }
    } else {
        // Service error (priorité à result.error car déjà traduit)
        errorMessage = result.error ?: s.shared("backup_export_failed")
    }
} catch (e: Exception) {
    // Exception handling avec préfixe traduit
    errorMessage = "${s.shared("backup_export_failed")}: ${e.message}"
}
```

## Validation des Données

**Voir DATA.md** pour SchemaValidator V3, validation centralisée et patterns de données.

## Système de Strings Modulaire

Architecture unifiée pour internationalisation avec discovery pattern et génération automatique.

### Structure
- **Sources** : core/strings/sources/shared.xml + tools/*/strings.xml
- **Génération** : Script Gradle → res/values/strings_generated.xml
- **API** : Strings.for(context) pour shared, Strings.for(tool = "tracking", context) pour tools

### Usage
Import Strings, puis s.shared("action_save") ou s.tool("display_name") avec fallback shared toujours accessible.

### Catégories Shared
- **action_*** : save, create, cancel, delete, validate, confirm, etc.
- **label_*** : name_zone, description, icon, enabled, disabled, etc.
- **message_*** : loading_tools, validation_error, no_zones_created, etc.
- **period_*** : today, yesterday, this_week, last_month, etc.
- **content_*** : app_name, tool_description, unnamed, etc.
- **month_*** : january, february, march, etc.

### Échappement Automatique
Script Gradle gère apostrophes, guillemets et conversion placeholders (%s → %1$s).

### Strings Android
**Format requis** : Placeholders numérotés %1$s, %2$s et pas %s
## AppConfigManager - Configuration Globale Cachée

Singleton cache pour paramètres applicatifs globaux utilisés fréquemment.

**Paramètres** : `dayStartHour`, `weekStartDay`

**API** :
```kotlin
AppConfigManager.initialize(context)  // MainActivity.onCreate
val dayStartHour = AppConfigManager.getDayStartHour()
val weekStartDay = AppConfigManager.getWeekStartDay()
AppConfigManager.refresh(context)  // Après modif config
```

**Règles** :
- Initialize obligatoire au démarrage
- Getters throws `IllegalStateException` si non initialisé
- Pas de fallback en dur (échec explicite)
- Extension : getter dans AppConfigService + cache volatile + initialize + getter public

## Types de Résultats

### OperationResult vs CommandResult

| Type | Package | Champs clés | Usage |
|------|---------|-------------|-------|
| OperationResult | services | `.success: Boolean`<br>`.data: Map?`<br>`.error: String?` | Services ExecutableService |
| CommandResult | commands | `.status: CommandStatus`<br>`.data: Map?`<br>`.error: String?` | Coordinator.processUserAction() |

**Pattern correct** :
```kotlin
// Services (OperationResult)
val result = service.execute(...)
if (result.success) { ... }

// Coordinator (CommandResult)
val result = coordinator.processUserAction(...)
if (result.status == CommandStatus.SUCCESS) { ... }
```

## Système de Logs Unifié

Architecture centralisée pour tous les logs du projet avec tags structurés et gestion d'erreurs robuste.

### LogManager Centralisé
**Emplacement** : core/utils/LogManager.kt

**Méthodes disponibles** : aiSession, aiPrompt, aiUI, aiService, aiEnrichment, schema, coordination, tracking, database, service, ui

### API Standardisée
LogManager.schema(), .coordination(), .tracking(), .database() etc. avec niveau DEBUG par défaut, niveaux INFO/WARN/ERROR et throwable optionnel.

### Gestion d'Erreurs Robuste
- try/catch automatique avec fallback println() pour tests unitaires
- Throwable optionnel avec stack trace
- Compatible Android et tests

### Guide de Debugging Service Resolution
**Problèmes courants** :
1. **"Service not found"** : Ajouter logs dans service
2. **"Tool instance ID is required"** : Vérifier tool_instance_id vs toolInstanceId
3. **Mauvais routing** : CommandDispatcher logs automatiquement
4. **LaunchedEffect ne se redéclenche pas** : Ajouter TOUTES les variables vérifiées aux dépendances

## Système de Transcription

Architecture provider-based pour transcription audio offline/online.

### TranscriptionProvider Pattern
Interface avec discovery via TranscriptionProviderRegistry. Providers implémentent `getConfigScreen()`, `downloadModel()`, `transcribe()`.

### Auto-Retry Startup
MainActivity.retryPendingTranscriptions() scan tool_data au démarrage pour relancer transcriptions pending via TranscriptionService multi-step.

### Provider Config Service
TranscriptionProviderConfigService gère configurations providers (get, set, set_active, list, delete) avec pattern identique à AIProviderConfigService.

### TranscribableTextField Pattern
Composable UI avec auto-transcription intégrée pour réutilisabilité sans duplication.

**API simplifiée** :
```kotlin
TranscribableTextField(
    label, value, onChange, audioFilePath,
    transcriptionStatus, modelName,
    // Auto-transcription
    autoTranscribe = true,
    transcriptionContext = TranscriptionContext(entryId, toolInstanceId, tooltype, fieldName),
    onTranscriptionStatusChange = { status -> ... }
)
```

**Logique centralisée** : Gère permissions audio, enregistrement, conversion segments→JSON, appel coordinator, gestion erreurs. Tools utilisent simplement `transcriptionContext` + callback status.

**TranscriptionContext** : Data class (entryId, toolInstanceId, tooltype, fieldName) passée pour déclenchement automatique post-recording.

---

*L'architecture Core garantit extensibilité et cohérence sans complexité excessive.*
