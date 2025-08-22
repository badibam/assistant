# Architecture Discovery - Services et Données

## Principe

**Discovery pure** : `ToolTypeManager` découvre services et DAOs dynamiquement, sans imports hardcodés dans Core.

## Pattern Discovery

### ToolTypeContract étendu
```kotlin
interface ToolTypeContract {
    fun getService(context: Context): Any?           // TrackingService, etc.
    fun getDao(context: Context): Any?               // TrackingDao, etc.  
    fun getDatabaseEntities(): List<Class<*>>        // TrackingData, etc.
}
```

### ServiceManager générique
```kotlin
// "tracking_service" → "tracking" → ToolTypeManager.getServiceForToolType("tracking", context)
val toolTypeId = serviceName.removeSuffix("_service")
return ToolTypeManager.getServiceForToolType(toolTypeId, context)
```

### Coordinator avec reflection
```kotlin
// Services découverts appellent execute() via reflection
val executeMethod = service.javaClass.getMethod("execute", ...)
executeMethod.invoke(service, operation, params, token)
```

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

**Nouveau tool type** = implémenter `ToolTypeContract` + ajouter au scanner → discovery automatique.

**Aucune modification** Core nécessaire.