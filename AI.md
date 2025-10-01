# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

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
- `CommandTransformer` (object) : Transformation commune DataCommand → ExecutableCommand (logique partagée)
- `UserCommandProcessor` : Délègue à CommandTransformer (logique user-specific si besoin)
- `AICommandProcessor` : Transformation commands IA (queries et actions séparées, validations AI futures)
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
8. Traiter commands IA via `processAICommands()` (si présentes)

### Command Processing Pipeline
```
User: EnrichmentBlock → EnrichmentProcessor → UserCommandProcessor → CommandTransformer → CommandExecutor
AI:   AIMessage → AICommandProcessor → CommandTransformer/Actions → CommandExecutor
```

**Responsabilités** : EnrichmentProcessor (UI→DataCommand), CommandTransformer (logique commune), User/AICommandProcessor (validations spécifiques), CommandExecutor (exécution + SystemMessage), QueryDeduplicator (déduplication prompts)

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

**DataCommand** (User enrichments ET commandes IA)
```kotlin
data class DataCommand(
    val id: String,              // Hash déterministe (type + params + isRelative)
    val type: String,            // TOOL_DATA, TOOL_CONFIG, CREATE_DATA, etc.
    val params: Map<String, Any>, // Paramètres absolus ou relatifs
    val isRelative: Boolean = false
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

### Types de Commands (User et IA)

| Type | Usage | Niveau |
|------|-------|--------|
| **Queries** (→ Level 4) |
| SCHEMA, TOOL_CONFIG, TOOL_DATA, TOOL_INSTANCES, ZONE_CONFIG, ZONES | Récupération données | User + AI |
| **Actions** (→ SystemMessage visible) |
| CREATE/UPDATE/DELETE_DATA | Mutations données (batch par défaut) | AI only |
| CREATE/UPDATE/DELETE_TOOL, CREATE/UPDATE/DELETE_ZONE | Mutations structure | AI only |

### Singletons et Classes

| Type | Pattern | Usage |
|------|---------|-------|
| QueryDeduplicator | object | `QueryDeduplicator.deduplicateCommands()` |
| CommandTransformer | object | `CommandTransformer.transformToExecutable()` |
| UserCommandProcessor | class | `UserCommandProcessor(context).processCommands()` |
| AICommandProcessor | class | `AICommandProcessor(context).processDataCommands()` / `.processActionCommands()` |
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

**Patterns exclusifs AIMessage** :
- **Actions** : preText + validationRequest? + actionCommands + postText?
- **Queries** : preText + dataCommands
- **Communication** : preText + communicationModule (dataCommands/actionCommands/postText NULL)


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
Modules de communication générés par l'IA pour obtenir des réponses structurées de l'utilisateur.

**Structure** (pattern analogue à tool_data) :
```kotlin
sealed class CommunicationModule {
    abstract val type: String
    abstract val data: Map<String, Any>

    data class MultipleChoice(
        override val type: String = "MultipleChoice",
        override val data: Map<String, Any>  // question, options
    ) : CommunicationModule()

    data class Validation(
        override val type: String = "Validation",
        override val data: Map<String, Any>  // message
    ) : CommunicationModule()
}
```

**Validation** : Via `CommunicationModuleSchemas` (object) avec schémas JSON pour chaque type
- `getSchema(type, context)` retourne Schema validé
- MultipleChoice : question (string), options (array min 2)
- Validation : message (string)

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

### CommandTransformer (helper partagé)
```kotlin
object CommandTransformer {
    fun transformToExecutable(
        commands: List<DataCommand>,
        context: Context
    ): List<ExecutableCommand>
}
```

**Responsabilités** :
- Transformation pure type → resource.operation
- Résolution périodes relatives (isRelative=true)
- Mapping paramètres selon service (tool_instance_id vs toolInstanceId)
- Utilisé par UserCommandProcessor ET AICommandProcessor

**Transformations** :
- SCHEMA → schemas.get
- TOOL_CONFIG → tools.get (id → tool_instance_id)
- TOOL_DATA → tool_data.get (id → toolInstanceId, résolution périodes)
- ZONE_CONFIG → zones.get (id → zone_id)
- ZONES → zones.list
- TOOL_INSTANCES → tools.list (avec zone_id) ou tools.list_all (sans zone_id)

### UserCommandProcessor
```kotlin
class UserCommandProcessor(private val context: Context) {
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand>
}
```

**Responsabilités** :
- Logging user-specific
- Délègue transformation à CommandTransformer
- Validation user-specific future si besoin

### AICommandProcessor
```kotlin
class AICommandProcessor(private val context: Context) {
    fun processDataCommands(commands: List<DataCommand>): List<ExecutableCommand>
    fun processActionCommands(commands: List<DataCommand>): List<ExecutableCommand>
}
```

**Responsabilités data queries** :
- Validations AI futures (token limits, permissions, rate limiting)
- Délègue transformation à CommandTransformer

**Responsabilités actions** :
- Validations strictes futures (permissions, scope, sanitization)
- Transformation action types → resource.operation :
  - CREATE_DATA → tool_data.batch_create
  - UPDATE_DATA → tool_data.batch_update
  - DELETE_DATA → tool_data.batch_delete
  - CREATE_TOOL → tools.create
  - UPDATE_TOOL → tools.update
  - DELETE_TOOL → tools.delete
  - CREATE_ZONE → zones.create
  - UPDATE_ZONE → zones.update
  - DELETE_ZONE → zones.delete

**Note** : Pas d'exécution (délégué à CommandExecutor)

**Enrichissements** : POINTER (référencer zone/instance), USE (config+schemas+data+stats), CREATE (schemas pour nouveau type), MODIFY_CONFIG (schema+config actuelle). RichComposer génère automatiquement linearText et dataCommands.

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

## 5bis. Traitement des réponses IA

### AIOrchestrator.processAICommands()
Appelé automatiquement après réception et stockage de l'AIMessage.

**Flow** :
1. Si `dataCommands` présent → AICommandProcessor.processDataCommands() → CommandExecutor → store SystemMessage
2. Si `actionCommands` présent → vérifier validationRequest → exécuter ou attendre validation
3. SystemMessages générés stockés automatiquement via storeSystemMessage()

**Exécution data queries** :
```
AICommandProcessor.processDataCommands()
  → CommandTransformer.transformToExecutable()
  → CommandExecutor.executeCommands(messageType=DATA_ADDED)
  → SystemMessage stocké
```

**Exécution actions** :
```
AICommandProcessor.processActionCommands()
  → transformActionCommand() (mapping types actions)
  → CommandExecutor.executeCommands(messageType=ACTIONS_EXECUTED)
  → SystemMessage stocké
```

**Validation actions** :
- Si `validationRequest` NULL → exécution autonome directe
- Si `validationRequest` présent → attente confirmation user (TODO: flow validation interactif - store request, wait user input, execute on confirm)

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

**SystemMessages AI** :
- Générés par AIOrchestrator.processAICommands() après exécution des commandes IA
- Stockés après la réponse AI via storeSystemMessage()
- Type DATA_ADDED pour queries, ACTIONS_EXECUTED pour mutations

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
**Principe** : Déduplication progressive pour maintenir breakpoints cache API. Chaque niveau contient uniquement commands non présentes dans niveaux précédents.

**Mécanismes** : Hash identité (commands identiques supprimées), inclusion métier (commands générales incluent spécifiques), filtrage progressif (L2 exclut L1, L3 exclut L1+L2, etc.)

**Importance** : Séparation stricte = cache API optimal. Violation = coûts tokens augmentés.

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

**CHAT** (`isRelative=false`) : Périodes absolues (Period avec timestamps fixes) pour cohérence conversation multi-jours
**AUTOMATION** (`isRelative=true`) : Périodes relatives (RelativePeriod encodé "offset_TYPE") résolues à l'exécution via AppConfigManager

**Token Management** : Validation globale par PromptManager. CHAT → dialogue confirmation, AUTOMATION → refus automatique si dépassement.

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
