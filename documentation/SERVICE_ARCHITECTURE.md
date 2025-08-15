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

**Usage** : Coordinateur route vers services, services vérifient token, accèdent DB.