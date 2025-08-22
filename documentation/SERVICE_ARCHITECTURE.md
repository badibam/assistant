# Service Architecture

## Pattern : Services avec tokens + discovery

```kotlin
class ZoneService(context: Context) {
    suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult
}
```

## ServiceManager avec discovery
```kotlin
// Core services (hardcodés)
"zone_service" -> ZoneService(context)
"tool_instance_service" -> ToolInstanceService(context)

// Tool services (découverts)
else -> ToolTypeManager.getServiceForToolType(toolTypeId, context)
```

## Services disponibles
- **Core** : ZoneService, ToolInstanceService
- **Tool types** : TrackingService (découvert), JournalService (futur), etc.

## Token pattern
```kotlin
if (token.isCancelled) return OperationResult.cancelled()
```

**Usage** : Coordinator → ServiceManager → discovery → service.execute()