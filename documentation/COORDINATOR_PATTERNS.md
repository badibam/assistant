# Coordinator Patterns

## Pattern utilisé : UI → Coordinator → Service

```kotlin
// UI appelle
coordinator.processUserAction("create->zone", mapOf("name" to name))

// Coordinateur route  
executeServiceOperation(command, "zone_service", "create")
```

## Flow utilisé
1. UI appelle `processUserAction()`
2. Coordinator génère token + route vers service
3. Service execute avec token
4. Retour `CommandResult` à UI

**Règle** : Modifications données (ou calcul qui dépend de donées) passent par Coordinator.
