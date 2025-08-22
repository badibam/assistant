# Coordinator Patterns

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

## Services intégrés
- **Core services** : ZoneService, ToolInstanceService (hardcodés)
- **Tool services** : TrackingService, etc. (découverts via ToolTypeManager)

**Règle** : Toute modification données passe par Coordinator pour cohérence IA/scheduler.
