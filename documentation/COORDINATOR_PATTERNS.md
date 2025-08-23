# Coordinator & Service Architecture

## Pattern : UI → Coordinator → Service

```kotlin
// UI appelle
coordinator.processUserAction("create->zone", mapOf("name" to name))

// Coordinateur route  
executeServiceOperation(command, "zone_service", "create")
```

## Flow
1. UI → `processUserAction()`
2. Coordinator → génère token + route vers service découvert
3. Service → execute avec token  
4. Retour `CommandResult` à UI

## ServiceManager avec discovery
```kotlin
// Core services (hardcodés)
"zone_service" -> ZoneService(context)
"tool_instance_service" -> ToolInstanceService(context)

// Tool services (découverts)
else -> ToolTypeManager.getServiceForToolType(toolTypeId, context)
```

## Services disponibles
- **Core** : ZoneService, ToolInstanceService (hardcodés)
- **Tool types** : TrackingService, etc. (découverts via ToolTypeManager)

## Token pattern
```kotlin
if (token.isCancelled) return OperationResult.cancelled()
```

**Règle** : Toute modification données passe par Coordinator pour cohérence IA/scheduler.
