# Architecture Discovery - Services et Données

## Principe

**Discovery pure** : `ToolTypeManager` découvre services et DAOs dynamiquement, sans imports hardcodés dans Core.

## Pattern Discovery

### Interfaces standardisées
- `ToolTypeContract` et `ExecutableService` : voir TOOL_ARCHITECTURE.md

### ServiceManager générique
```kotlin
// "tracking_service" → "tracking" → ToolTypeManager.getServiceForToolType("tracking", context)
val toolTypeId = serviceName.removeSuffix("_service")
return ToolTypeManager.getServiceForToolType(toolTypeId, context)
```

### Coordinator integration
- Pattern UI → Coordinator → Service : voir COORDINATOR_PATTERNS.md

## Standalone Databases

### Structure
- **TrackingDatabase** → TrackingData (toutes instances tracking)
- **JournalDatabase** → JournalData (futures instances journal)
- **1 database par tool type** pour discovery pure

### Foreign Keys supprimées
```kotlin
@Entity(
    tableName = "tracking_data", 
    indices = [Index(value = ["tool_instance_id"])]  // Performance sans FK
)
```

## Flux Complet

1. `UI` → `coordinator.processUserAction("create->tracking_data", params)`
2. `Coordinator` → `serviceManager.getService("tracking_service")`  
3. `ServiceManager` → `ToolTypeManager.getServiceForToolType("tracking")`
4. `TrackingService` → `ToolTypeManager.getDaoForToolType("tracking")`
5. `TrackingDao` → `TrackingDatabase.trackingDao().insertEntry()`

## Extension

**Nouveau tool type** : 
1. Service implémente `ExecutableService`
2. ToolType implémente `ToolTypeContract` 
3. Ajouter au `ToolTypeScanner`

→ **Discovery automatique, aucune modification Core nécessaire.**