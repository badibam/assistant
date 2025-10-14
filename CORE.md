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
**Services Core** : zones → ZoneService, tools → ToolInstanceService, tool_data → ToolDataService, app_config → AppConfigService, icon_preload → IconPreloadService, backup → BackupService

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

## Migrations Automatiques

### Types de Migrations
- **Database** : Schéma Room découvert automatiquement par outil
- **Configuration** : Format JSON des outils
- **Application** : Données globales

### Pattern ToolType
ToolType.getDatabaseMigrations() retourne List<Migration> avec objets Migration(fromVersion, toVersion) et execSQL().

### Gestion Versions
AppVersionManager.CURRENT_APP_VERSION et build.gradle versionCode/versionName.
**Migration** : Si CURRENT_APP_VERSION > version stockée → exécution migrations

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
Smart cast automatique avec result.isSuccess.

### Pattern de commandes
coordinator.processUserAction(), processAICommand(), processScheduledTask() avec resource.operation

### Pattern d'utilisation du Coordinator - Référence
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
    errorMessage = result.error ?: "Unknown error"
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

---

*L'architecture Core garantit extensibilité et cohérence sans complexité excessive.*
