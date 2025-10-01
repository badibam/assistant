# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

### Flow principal
```
User message → AIOrchestrator → PromptManager → CommandExecutor → AIClient
              ↕                ↕             ↕
          AISessionService   QueryDeduplicator  AIProviderConfigService
          (via coordinator)  (prompt dedup)    (via coordinator)
```

### Session types
- **CHAT** : Conversation temps réel, queries absolues, modules communication
- **AUTOMATION** : Prompt programmable, queries relatives, feedback exécutions

## 2. Services et responsabilités

### Séparation des responsabilités selon CORE.md

**Services ExecutableService (accès DB via coordinator) :**
- `AISessionService` : CRUD sessions et messages
- `AIProviderConfigService` : CRUD configurations providers

**Classes métier pures (logique sans DB) :**
- `AIOrchestrator` : Orchestration complète du flow IA
- `AIClient` : Interface vers providers AI externes
- `PromptManager` : Génération prompts 4 niveaux avec commands unifiées
- `EnrichmentProcessor` : Génération commands depuis enrichments UI bruts
- `UserCommandProcessor` : Transformation commands user (DataCommand → ExecutableCommand)
- `AICommandProcessor` (object) : Transformation commands AI (AICommand → ExecutableCommand)
- `CommandExecutor` : **Point unique d'exécution** vers coordinator + génération SystemMessage
- `QueryDeduplicator` (object) : Déduplication cross-niveaux pour prompts

### SystemMessage Generation

**Point unique** : `CommandExecutor` est le SEUL responsable de la génération de SystemMessage pour TOUTES les sources (User, AI).

**Granularité** : 1 SystemMessage par série de commandes (pas par commande individuelle).

**Types de messages** :
- `DATA_ADDED` : Pour queries (get, list, stats) - résultats intégrés en Level 4
- `ACTIONS_EXECUTED` : Pour mutations (create, update, delete, batch_*) - visible dans historique conversation

**Flow unifié** :
```
User enrichments → UserCommandProcessor → CommandExecutor → SystemMessage
AI dataCommands → AICommandProcessor → CommandExecutor → SystemMessage
AI actionCommands → AICommandProcessor → CommandExecutor → SystemMessage
```

### AIOrchestrator (orchestrateur central)
```kotlin
class AIOrchestrator(private val context: Context) {
    suspend fun sendMessage(richMessage: RichMessage, sessionId: String): OperationResult
    suspend fun createSession(name: String, type: SessionType, providerId: String): String
    suspend fun setActiveSession(sessionId: String): OperationResult
    suspend fun loadSession(sessionId: String): AISession?
}
```

**Flow complet orchestré :**
1. Stocker message utilisateur via `AISessionService`
2. Builder prompt via `PromptManager` (génère levels 1-4 + SystemMessages)
3. Stocker SystemMessages Level4
4. Builder historique avec tous les messages
5. Assembler prompt final via `assembleFinalPrompt()`
6. Envoyer à `AIClient`
7. Stocker réponse AI

### Command Processing Pipeline
```
EnrichmentBlock (stocké) → EnrichmentProcessor → UserCommandProcessor → CommandExecutor
                                            ↓                               ↓
                                 List<DataCommand> → List<ExecutableCommand> → coordinator calls + SystemMessage

AIMessage.dataCommands → AICommandProcessor → CommandExecutor
                              ↓                   ↓
                   List<ExecutableCommand> → coordinator calls + SystemMessage
```

**Responsabilités séparées :**
- `EnrichmentProcessor` : EnrichmentBlock brut → N DataCommands
- `UserCommandProcessor` : Transformation DataCommand → ExecutableCommand (résolution périodes relatives)
- `AICommandProcessor` : Transformation AICommand → ExecutableCommand (types abstraits → resource.operation)
- `CommandExecutor` : **Point unique d'exécution** → coordinator calls + génère SystemMessage unique par série
- `QueryDeduplicator` : Déduplication cross-niveaux pour assemblage prompt final

### AISessionService (ExecutableService)
```kotlin
class AISessionService(context: Context) : ExecutableService {
    // Operations: create_session, get_session, set_active_session, create_message, etc.
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult
}
```

**Ressource coordinator :** `ai_sessions.operation`

### AIProviderConfigService (ExecutableService)
```kotlin
class AIProviderConfigService(context: Context) : ExecutableService {
    // Operations: get, set, list, delete, set_active, get_active
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult
}
```

**Ressource coordinator :** `ai_provider_config.operation`

### AIClient (logique métier pure)
```kotlin
class AIClient(private val context: Context) {
    suspend fun query(prompt: String, providerId: String): OperationResult
    suspend fun getAvailableProviders(): List<AIProviderInfo>
    suspend fun getActiveProviderId(): String?
}
```

Interface vers providers externes, reçoit prompt complet assemblé.

## 3. Types et Structures

### Types de Résultats (voir CORE.md)
- **OperationResult** : Services avec `.success: Boolean`
- **CommandResult** : Coordinator avec `.status: CommandStatus`

### Structures de Commands

**DataCommand** (User enrichments)
```kotlin
data class DataCommand(
    val id: String,              // Hash déterministe (type + params + isRelative)
    val type: String,            // TOOL_DATA, TOOL_CONFIG, ZONE_CONFIG, etc.
    val params: Map<String, Any>, // Paramètres absolus ou relatifs
    val isRelative: Boolean = false
)
```

**AICommand** (IA queries et actions)
```kotlin
data class AICommand(
    val id: String,
    val type: String,            // Types abstraits (voir section suivante)
    val params: JSONObject       // Doit être converti en Map pour ExecutableCommand
)
```

**ExecutableCommand** (Format unifié pour exécution)
```kotlin
data class ExecutableCommand(
    val resource: String,        // "zones", "tool_data", "tools"
    val operation: String,       // "get", "create", "batch_create"
    val params: Map<String, Any> // Paramètres résolus pour coordinator
)
```

**Conversion JSONObject ↔ Map** : Nécessaire lors de la transformation AICommand → ExecutableCommand.

### Types de Commands IA (Abstraits)

**Queries** (retournent données pour Level 4) :
- `TOOL_DATA` : Récupérer données d'outil
- `TOOL_CONFIG` : Configuration d'outil
- `TOOL_INSTANCES` : Liste outils d'une zone
- `ZONE_CONFIG` : Configuration zone
- `ZONES` : Liste zones
- `SCHEMA` : Schéma validation

**Actions** (génèrent SystemMessage visible) :
- `CREATE_DATA` : Créer données (batch par défaut)
- `UPDATE_DATA` : Modifier données (batch par défaut)
- `DELETE_DATA` : Supprimer données (batch par défaut)
- `CREATE_TOOL` : Créer outil
- `UPDATE_TOOL` : Modifier outil
- `DELETE_TOOL` : Supprimer outil
- `CREATE_ZONE` : Créer zone
- `UPDATE_ZONE` : Modifier zone
- `DELETE_ZONE` : Supprimer zone

**Note importante** : Toutes les opérations sur données sont batch par défaut (batch_create, batch_update, batch_delete).

### Singletons et Classes

| Type | Pattern | Usage |
|------|---------|-------|
| QueryDeduplicator | object | `QueryDeduplicator.deduplicateCommands()` |
| AICommandProcessor | object | `AICommandProcessor.processCommands()` |
| UserCommandProcessor | class | `UserCommandProcessor(context).processCommands()` |
| CommandExecutor | class | `CommandExecutor(context).executeCommands()` |

## 3. Structures de données unifiées

### AISession (complète)
```kotlin
data class AISession(
    val id: String,
    val name: String,
    val type: SessionType,
    val providerId: String,
    val providerSessionId: String,
    val schedule: ScheduleConfig?,        // Pour AUTOMATION seulement
    val createdAt: Long,
    val lastActivity: Long,
    val messages: List<SessionMessage>,
    val isActive: Boolean
)
```

### SessionMessage (structure unifiée)
```kotlin
data class SessionMessage(
    val id: String,
    val timestamp: Long,
    val sender: MessageSender,     // USER, AI, SYSTEM
    val richContent: RichMessage?, // Messages enrichis utilisateur
    val textContent: String?,      // Messages simples (réponses modules)
    val aiMessage: AIMessage?,     // Structure IA parsée pour UI
    val aiMessageJson: String?,    // JSON original pour historique prompts
    val systemMessage: SystemMessage?, // Messages système auto
    val executionMetadata: ExecutionMetadata? // Automations uniquement
)
```

### RichMessage (messages utilisateur avec enrichissements)
```kotlin
data class RichMessage(
    val segments: List<MessageSegment>,
    val linearText: String,           // Calculé : version textuelle pour IA
    val dataCommands: List<DataCommand>  // Calculé : commands pour prompts
)

sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()
    data class EnrichmentBlock(
        val type: EnrichmentType,
        val config: String,       // Configuration JSON
        val preview: String       // Preview lisible "données nutrition zone Santé"
    ) : MessageSegment()
}
```

### AIMessage (réponses IA structurées)
```kotlin
data class AIMessage(
    val preText: String,                              // Obligatoire
    val validationRequest: ValidationRequest?,        // Validation avant actions
    val dataCommands: List<DataCommand>?,             // OU actions (exclusif)
    val actionCommands: List<DataCommand>?,           // OU dataCommands
    val postText: String?,                            // Seulement si actions
    val communicationModule: CommunicationModule?     // Toujours en dernier
)
```

**Contrainte importante** : `dataCommands` et `actionCommands` mutuellement exclusifs.

### DataCommand (unifié)
```kotlin
data class DataCommand(
    val id: String,              // Hash déterministe de (type + params + isRelative)
    val type: String,            // Command type standardisé (voir types disponibles)
    val params: Map<String, Any>, // Paramètres absolus ou relatifs
    val isRelative: Boolean = false // true pour automation, false pour chat
)
```

### ExecutableCommand
```kotlin
```

**Types de commands utilisateur (enrichments) - voir section 3 Types et Structures**
- **SCHEMA** → id
- **TOOL_CONFIG** → id
- **TOOL_DATA** → id + périodes + filtres
- **TOOL_STATS** → id + périodes + agrégation
- **TOOL_DATA_SAMPLE** → id + limit
- **ZONE_CONFIG** → id
- **ZONES** → aucun paramètre
- **TOOL_INSTANCES** → zone_id optionnel

## 4. Communication bidirectionnelle

### Messages riches (texte + informations de données / instructions)
Les `RichMessage` combinent texte libre et blocs d'enrichissement pour créer des interactions contextuelles :

```kotlin
data class RichMessage(
    val segments: List<MessageSegment>,    // Texte + EnrichmentBlock
    val linearText: String,                // Version textuelle pour IA
    val dataCommands: List<DataCommand>    // Commands générées automatiquement
)
```

### Communication Modules
Modules de communication générés par l'IA pour obtenir des réponses structurées de l'utilisateur :

```kotlin
sealed class CommunicationModule {
    data class MultipleChoice(val question: String, val options: List<String>)
    data class Validation(val message: String)
    // TODO: Slider, DataSelector
}
```

### Validation et permissions
Système hiérarchique contrôlant les actions IA :
- `autonomous` - IA agit directement
- `validation_required` - Confirmation utilisateur obligatoire
- `forbidden` - Action interdite
- `ask_first` - Permission avant proposition

### Flow de réponse utilisateur
1. IA génère `AIMessage` avec `CommunicationModule`
2. UI affiche le module approprié (choix multiple, validation, etc.)
3. Réponse utilisateur stockée dans `SessionMessage.textContent`
4. IA traite la réponse pour actions suivantes

## 5. Enrichissements et composition des messages

### Types d'enrichissements
- **🔍 POINTER** - Référencer données (zones ou instances)
- **📝 USE** - Utiliser données d'outils (config + schemas + data sample + stats)
- **✨ CREATE** - Créer éléments (schemas pour type d'outil)
- **🔧 MODIFY_CONFIG** - Modifier config outils (schema + config actuelle)

### EnrichmentProcessor
```kotlin
class EnrichmentProcessor {
    fun generateSummary(type: EnrichmentType, config: String): String
    fun generateCommands(
        type: EnrichmentType,
        config: String,
        isRelative: Boolean,
        dayStartHour: Int,
        weekStartDay: String
    ): List<DataCommand>
}
```

**Responsabilités périodes :**
- **CHAT** (`isRelative=false`) : Calcule timestamps absolus (début + fin période) via Period objects
- **AUTOMATION** (`isRelative=true`) : Encode périodes relatives format "offset_TYPE" (ex: "-1_WEEK")

### UserCommandProcessor
```kotlin
class UserCommandProcessor(private val context: Context) {
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand>
}
```

**Transformations par type :**
- `TOOL_CONFIG` → `tools.get` (id → tool_instance_id)
- `TOOL_DATA` → `tool_data.get` (résolution périodes relatives si `isRelative=true`)
- `TOOL_STATS` → `tool_data.stats` (agrégation données)
- `TOOL_DATA_SAMPLE` → `tool_data.get` (limit + orderBy recent)
- `ZONE_CONFIG` → `zones.get` (id → zone_id)
- `ZONES` → `zones.list`
- `TOOL_INSTANCES` → `tools.list` (avec zone_id) ou `tools.list_all` (sans zone_id)

**Résolution périodes relatives :** Parse "offset_TYPE" → timestamps absolus via `resolveRelativePeriod()` + `AppConfigManager`

### AICommandProcessor (object)
```kotlin
object AICommandProcessor {
    suspend fun processCommands(
        commands: List<AICommand>,
        context: Context
    ): List<ExecutableCommand>
}
```

**Responsabilités :**
- Transformation types abstraits (TOOL_DATA, CREATE_DATA) → resource.operation format
- Conversion JSONObject params → Map<String, Any>
- Mapping vers opérations batch par défaut pour données (batch_create, batch_update, batch_delete)

**Note** : Pas d'exécution, seulement transformation. L'exécution et la génération de SystemMessage sont gérées par CommandExecutor.

**Logique enrichissement par type :**

**POINTER** :
- Zone → `ZONE_CONFIG` + `TOOL_INSTANCES`
- Instance → `SCHEMA(config)` + `SCHEMA(data)` + `TOOL_CONFIG` + `TOOL_DATA_SAMPLE` + optionnellement `TOOL_DATA` réelles (toggle "inclure données")

**CREATE** : `SCHEMA(config_schema_id)` + `SCHEMA(data_schema_id)` pour type d'outil

**MODIFY_CONFIG** : `SCHEMA(config_schema_id)` + `TOOL_CONFIG(tool_instance_id)`

**USE** : `TOOL_CONFIG` + `SCHEMA(config)` + `SCHEMA(data)` + `TOOL_DATA_SAMPLE` + `TOOL_STATS`

### Composition via RichComposer
Le `RichComposer` permet à l'utilisateur de combiner texte et enrichissements, générant automatiquement `linearText` et `dataCommands` via `EnrichmentProcessor.generateCommands()`.

### Différences User vs AI Commands

**User Commands** :
- Source : EnrichmentBlocks dans RichMessage
- Types : POINTER, USE, CREATE, MODIFY_CONFIG uniquement
- But : Fournir données contextuelles à l'IA
- **Jamais d'actions directes**

**AI Commands** :
- Source : AIMessage.dataCommands + AIMessage.actionCommands
- Types : Data queries + actions réelles (create, update, delete)
- But : Demander données + exécuter actions

## 6. SystemMessages

### Structure
```kotlin
data class SystemMessage(
    val type: SystemMessageType,           // DATA_ADDED ou ACTIONS_EXECUTED
    val commandResults: List<CommandResult>,
    val summary: String                    // Résumé pour affichage
)
```

### Génération et stockage
- **Générés par** : `CommandExecutor` après chaque série de commandes
- **Stockés comme** : `SessionMessage` avec `sender=SYSTEM`
- **Sérialisation** : `SystemMessage.toJson()` / `fromJson()`

### Types et placement
**SystemMessages Startup (L1-3)** :
- Générés à la création de session via `createSession()`
- Stockés en premier dans l'historique
- Documentent le chargement initial (zones, schémas, configs)

**SystemMessages Level4** :
- Générés après chaque message user avec enrichissements
- Stockés après le message user
- Documentent les données chargées pour ce message spécifique

**SystemMessages AI** (non implémenté) :
- Générés après exécution de `dataCommands`/`actionCommands`
- Stockés après la réponse AI

### Format dans les prompts
```
[SYSTEM] summary
  ✓ command: details
  ✗ command: error
```

## 7. Architecture des niveaux de prompts

**Level 1: DOC** - Documentation système statique
- Rôle IA + intro application
- Documentation API (format réponse, commandes, schema_ids)
- Schéma zone complet + liste tous schema_ids
- Tooltypes (nom + description) + leurs schema_ids

**Level 2: USER DATA** - Données utilisateur systématiques
- Config IA utilisateur (non implémenté)
- Données complètes des tool instances avec `always_send: true` (champ BaseSchemas)
- Si outil a `always_send=true` → données incluses automatiquement en contexte permanent

**Level 3: APP STATE** - État application complet
- Toutes les zones avec configs + tool instances avec configs

**Level 4: SPECIFIC DATA** - Données ciblées
- Résultats enrichissements utilisateur
- Résultats commandes IA précédentes

### Pipeline de génération et assemblage

**PromptManager.buildPrompt()** :
1. Génère commands pour levels 1-4
2. Exécute via CommandExecutor (génère SystemMessages)
3. Retourne `PromptResult` avec levels + systemMessages (startup + level4)

**PromptManager.buildHistorySection()** :
- Charge tous les messages de session (incluant SystemMessages stockés)
- Formate pour inclusion dans prompt

**PromptManager.assembleFinalPrompt()** :
- Assemble levels 1-4 + historique
- Insère cache breakpoints pour Claude

**Flow complet** :
```
buildPrompt() → store SystemMessages → buildHistorySection() → assembleFinalPrompt()
```

### CommandExecutor
```kotlin
class CommandExecutor(private val context: Context) {
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        messageType: SystemMessageType,
        level: String
    ): CommandExecutionResult
}

data class PromptCommandResult(
    val dataTitle: String,       // Titre section données prompt
    val formattedData: String    // JSON avec métadonnées en premier
)

data class CommandExecutionResult(
    val promptResults: List<PromptCommandResult>,  // Pour intégration prompt
    val systemMessage: SystemMessage               // UN message pour toute la série
)
```

**Responsabilités** :
- **Point unique d'exécution** : Toutes les commands (User/AI) passent par CommandExecutor
- **Exécution** : Appels coordinator pour chaque ExecutableCommand
- **Formatage** : Création PromptCommandResult pour queries (dataTitle + formattedData)
- **SystemMessage** : Génération d'UN seul message pour la série complète
- **Tracking** : Compte success/failure pour chaque command

**Formatage par opération :**
- **Queries** (get, list, stats) : PromptCommandResult avec données formatées JSON + SystemMessage type DATA_ADDED
- **Actions** (create, update, delete, batch_*) : SystemMessage type ACTIONS_EXECUTED uniquement (pas de PromptCommandResult)

**SystemMessage granularité** : 1 message agrégé pour toute la série (ex: "3 queries executed, 150 data points added" ou "Created 50 data points in 2 tools")

### QueryDeduplicator (object)
**Principe** : Déduplication progressive pour maintenir les breakpoints de cache API

**Architecture critique** : La séparation en 4 niveaux de prompt est essentielle pour le cache API Claude. Chaque niveau doit contenir uniquement les commands qui lui sont propres (pas déjà dans les niveaux précédents).

**Déduplication progressive** :
```kotlin
// Level 1
val l1Deduplicated = QueryDeduplicator.deduplicateCommands(level1Commands)
val l1Executable = userCommandProcessor.processCommands(l1Deduplicated)
val l1Result = commandExecutor.executeCommands(l1Executable, DATA_ADDED, "level1")

// Level 2: Seulement commands NON présentes en L1
val l2OnlyCommands = l2Deduplicated.filter { cmd ->
    !l1Deduplicated.any { it.id == cmd.id }
}
val l2Executable = userCommandProcessor.processCommands(l2OnlyCommands)
val l2Result = commandExecutor.executeCommands(l2Executable, DATA_ADDED, "level2")

// Level 3: Seulement commands NON présentes en L1+L2
// Level 4: Seulement commands NON présentes en L1+L2+L3
```

**Importance** : Cette séparation stricte permet à l'API Claude de cacher chaque niveau indépendamment. Casser cette séparation invalide le cache et augmente les coûts de tokens.

**Mécanismes :**
1. **Hash identité** : Commands identiques supprimées (première occurrence gardée)
2. **Inclusion métier** : Commands générales incluent spécifiques selon règles business
3. **Filtrage progressif** : Chaque niveau exclut les commands des niveaux précédents

### Storage Policy

**Stocké en DB :**
- `AISession.messages: List<SessionMessage>`
- RichMessage (JSON complet avec segments)
- SystemMessage (JSON avec commandResults)
- aiMessageJson (réponses IA brutes)

**Non stocké (régénéré) :**
- DataCommand, ExecutableCommand (pipeline temporaire)
- Résultats d'exécution des commands
- Prompt final assemblé

**Régénération** : Pipeline stateless garantit données fraîches à chaque prompt

### Dual mode résolution (CHAT absolu vs AUTOMATION relatif)

**Types de périodes :**
```kotlin
data class Period(val timestamp: Long, val type: PeriodType)  // Période absolue (point dans le temps)
data class RelativePeriod(val offset: Int, val type: PeriodType)  // Offset depuis maintenant
```

**CHAT** (`isRelative=false`) :
- "cette semaine" → `Period` avec timestamps absolus (début + fin calculés via `getPeriodEndTimestamp()`)
- Stocké dans `timestampSelection.minPeriod` / `maxPeriod`
- Garantit cohérence conversation sur plusieurs jours

**AUTOMATION** (`isRelative=true`) :
- "cette semaine" → `RelativePeriod(offset=0, type=WEEK)` → encodé "0_WEEK"
- Stocké dans `timestampSelection.minRelativePeriod` / `maxRelativePeriod`
- Résolu à l'exécution via `resolveRelativePeriod()` + `AppConfigManager`

**AppConfigManager :** Singleton cache pour `dayStartHour` et `weekStartDay` (initialisé au démarrage app)

**Token Management :** Validation globale prompt par PromptManager. Gestion différenciée CHAT (dialogue confirmation) vs AUTOMATION (refus automatique) si dépassement.

## 7. Providers

### AIProvider interface
```kotlin
interface AIProvider {
    fun getDisplayName(): String
    fun getConfigSchema(): String
    @Composable fun getConfigScreen(config: String, onSave: (String) -> Unit)
    suspend fun query(prompt: String, config: String): AIResponse
}
```

### Configuration et découverte
- Configurations gérées par `AIProviderConfigService` via coordinator
- Providers découverts via `AIProviderRegistry`
- `AIClient` utilise coordinator pour récupérer configs (pas d'accès direct DB)
