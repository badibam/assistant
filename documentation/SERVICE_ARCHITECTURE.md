# Service Architecture

## Pattern utilisé : Services avec tokens

```kotlin
class ZoneService(context: Context) {
    suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult
}

// ServiceManager factory
val service = serviceManager.getService("zone_service") as ZoneService
```

## Token pattern
```kotlin
if (token.isCancelled) return OperationResult.cancelled()
```

## Services disponibles
- **ZoneService** : Gestion des zones (create, update, delete)
- **Tools via ToolTypeManager** : Découverte et métadonnées des outils
  ```kotlin
  val toolType = ToolTypeManager.getToolType("tracking")
  val displayName = ToolTypeManager.getToolTypeName("tracking")
  ```

**Usage** : Coordinateur route vers services, services vérifient token, accèdent DB.