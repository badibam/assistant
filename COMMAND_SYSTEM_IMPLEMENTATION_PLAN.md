# Plan d'Implémentation - Command System Restructure

## Vue d'ensemble
Transformation du système actuel `DataQuery` vers pipeline unifié `DataCommand → ExecutableCommand` avec processors spécialisés.

## 1. Structures de données

### À CRÉER

**DataCommand.kt** (remplace DataQuery)
```kotlin
data class DataCommand(
    val id: String,
    val type: String,
    val params: Map<String, Any>,
    val isRelative: Boolean = false
)
```

**ExecutableCommand.kt** (nouveau)
```kotlin
data class ExecutableCommand(
    val resource: String,
    val operation: String,
    val params: Map<String, Any>
)
```

### À MODIFIER

**RichMessage.kt**
- `dataQueries: List<DataQuery>` → `dataCommands: List<DataCommand>`

**AIMessage.kt**
- `dataRequests: List<DataQuery>?` → `dataCommands: List<DataCommand>?`
- `actions: List<AIAction>?` → `actionCommands: List<DataCommand>?`

**SessionMessage.kt**
- Pas de changement structure (garde richContent avec EnrichmentBlocks bruts)

## 2. Processors

### À CRÉER

**UserCommandProcessor.kt**
```kotlin
class UserCommandProcessor {
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand>
    // Résolution périodes relatives → timestamps absolus
    // UI abstractions → paramètres coordinator concrets
}
```

**AICommandProcessor.kt**
```kotlin
class AICommandProcessor {
    fun processDataCommands(commands: List<DataCommand>): List<ExecutableCommand>
    fun processActionCommands(commands: List<DataCommand>): List<ExecutableCommand>
    // Validation sécurité, limites données, token management
}
```

### À MODIFIER

**EnrichmentProcessor.kt**
- `generateQueries()` → `generateCommands()`
- Return type: `List<DataQuery>` → `List<DataCommand>`

## 3. Execution et orchestration

### À RENOMMER/MODIFIER

**QueryExecutor.kt** → **CommandExecutor.kt**
- Renommer classe et méthodes
- `executeQueries()` → `executeCommands()`
- Input type: `List<DataQuery>` → `List<ExecutableCommand>`
- Ajouter échec cascade logic
- Supprimer déduplication cross-levels (moved to PromptManager)

### À CONSERVER

**QueryDeduplicator.kt**
- Garder pour déduplication cross-niveaux prompts
- Adapter pour `DataCommand` instead of `DataQuery`

## 4. PromptManager

### À MODIFIER

**PromptManager.kt**
- `getLevel4Queries()` → `getLevel4Commands()`
- Pipeline intégration:
  1. Extract EnrichmentBlocks from session history
  2. `EnrichmentProcessor.generateCommands()`
  3. `UserCommandProcessor.processCommands()`
  4. `CommandExecutor.executeCommands()`
- Garder QueryDeduplicator pour assemblage final prompt

## 5. AIOrchestrator

### À MODIFIER

**AIOrchestrator.kt**
- Parsing methods: `parseRichMessageFromJson()` pour `dataCommands`
- Parsing methods: `parseAIMessageFromJson()` pour `dataCommands/actionCommands`
- Flow orchestration reste identique (via PromptManager)

## 6. UI Components

### À MODIFIER

**RichComposer.kt**
- Generation: `EnrichmentProcessor.generateQueries()` → `generateCommands()`
- Return: `RichMessage` with `dataCommands`

## 7. Database/Persistence

### PAS DE CHANGEMENT
- Database schemas restent identiques
- Stockage: EnrichmentBlocks bruts dans SessionMessage
- Pas de persistance DataCommand/ExecutableCommand

## 8. Migration des références

### À RENOMMER GLOBALEMENT
- `DataQuery` → `DataCommand` (dans types, variables, comments)
- `QueryExecutor` → `CommandExecutor`
- `executeQueries` → `executeCommands`
- `dataQueries` → `dataCommands`
- `dataRequests` → `dataCommands`
- `generateQueries` → `generateCommands`

### IMPORTS À AJUSTER
- Tous les imports `QueryExecutor` → `CommandExecutor`
- Tous les imports `DataQuery` → `DataCommand`

## 9. Testing

### À ADAPTER
- Tests QueryExecutor → CommandExecutor
- Tests DataQuery → DataCommand
- Tests pipeline processors (nouveau)
- Tests échec cascade (nouveau)

## 10. Ordre d'implémentation suggéré

### Phase 1: Structures de base
1. Créer DataCommand.kt
2. Créer ExecutableCommand.kt
3. Modifier RichMessage.kt, AIMessage.kt

### Phase 2: Processors
4. Créer UserCommandProcessor.kt
5. Créer AICommandProcessor.kt
6. Modifier EnrichmentProcessor.kt

### Phase 3: Execution
7. Renommer QueryExecutor → CommandExecutor
8. Implémenter échec cascade
9. Adapter QueryDeduplicator pour DataCommand

### Phase 4: Integration
10. Modifier PromptManager pipeline
11. Modifier AIOrchestrator parsing
12. Modifier RichComposer

### Phase 5: Testing & cleanup
13. Adapter tous les tests
14. Cleanup imports/références
15. Validation flow end-to-end

## Notes importantes

- **Pas de migration DB** : EnrichmentBlocks stockés restent identiques
- **Pipeline stateless** : Génération temporaire à chaque prompt
- **Déduplication conservée** : QueryDeduplicator pour prompts cross-niveaux
- **Échec cascade** : Arrêt sur première commande failed dans CommandExecutor