# Command System Restructure - Specifications

## Context
Restructuration du système de commandes IA pour séparer génération, transformation et exécution avec pipeline stateless pur.

## Data Structures

### DataCommand (unifié)
```kotlin
data class DataCommand(
    val id: String,
    val type: String,             // "ZONE_CONFIG", "TOOL_CREATE", etc.
    val params: Map<String, Any>,
    val isRelative: Boolean = false
)
```

### ExecutableCommand
```kotlin
data class ExecutableCommand(
    val resource: String,         // "zones", "tool_data"
    val operation: String,        // "get", "create", "update"
    val params: Map<String, Any>  // Paramètres résolus pour coordinator
)
```

## Pipeline Architecture

### Flow complet
```
EnrichmentBlock → EnrichmentProcessor → UserCommandProcessor → CommandExecutor
                                     ↓
                              List<DataCommand> → List<ExecutableCommand> → coordinator calls
```

### Classes et responsabilités

**EnrichmentProcessor**
- `generateCommands(type, config, isRelative): List<DataCommand>`
- Gère cascade 1 enrichment → N commands
- Résolution immédiate selon sessionType (CHAT absolu vs AUTOMATION relatif)

**UserCommandProcessor**
- `processCommands(commands: List<DataCommand>): List<ExecutableCommand>`
- Transformation UI abstractions → paramètres coordinator concrets
- Résolution périodes relatives → timestamps absolus

**AICommandProcessor**
- `processDataCommands(commands: List<DataCommand>): List<ExecutableCommand>`
- `processActionCommands(commands: List<DataCommand>): List<ExecutableCommand>`
- Validation sécurité, limites données, token management

**CommandExecutor** (renommé de QueryExecutor)
- `executeCommands(commands: List<ExecutableCommand>): CommandResults`
- ExecutableCommand → resource.operation + coordinator calls
- Échec cascade : échec commande N → arrêt exécution N+1, N+2...

## Message Structures

### RichMessage
```kotlin
data class RichMessage(
    val segments: List<MessageSegment>,
    val linearText: String,
    val dataCommands: List<DataCommand>  // Renommé de dataQueries
)
```

### AIMessage
```kotlin
data class AIMessage(
    val preText: String,
    val dataCommands: List<DataCommand>?,    // Renommé de dataRequests
    val actionCommands: List<DataCommand>?,  // Renommé de actions
    // ... autres champs inchangés
)
```

## Différences User vs AI

### User Commands
- Source : EnrichmentBlocks dans RichMessage
- Types : POINTER, USE, CREATE, MODIFY_CONFIG uniquement
- But : Fournir données contextuelles à l'IA
- **Jamais d'actions directes**

### AI Commands
- Source : AIMessage.dataCommands + AIMessage.actionCommands
- Types : Data queries + actions réelles (create, update, delete)
- But : Demander données + exécuter actions

## Storage Policy

### Ce qui EST stocké
- `AISession.messages: List<SessionMessage>`
- `SessionMessage` avec données brutes : RichMessage, aiMessageJson

### Ce qui N'EST JAMAIS stocké
- DataCommand (temporaire pipeline)
- ExecutableCommand (temporaire pipeline)
- Résultats commandes dans prompt
- Prompt final assemblé

### Régénération complète
- À chaque message : extraction EnrichmentBlocks → pipeline complet → prompt fresh
- Pas de cache, pas d'état calculé persistant
- Pipeline stateless pur

## Validation Flow
- AICommandProcessor : Validation sécurité/business (peut rejeter commandes)
- CommandExecutor : Validation technique (warnings/optimizations)
- UserCommandProcessor : Pas de validation (confiance utilisateur)