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
- `UserCommandProcessor` : Transformation commands user (résolution relatives)
- `AICommandProcessor` : Transformation commands AI (validation sécurité)
- `CommandExecutor` : Exécution commands vers coordinator avec échec cascade
- `QueryDeduplicator` : Déduplication cross-niveaux pour prompts

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
2. Builder prompt via `PromptManager` (pipeline command processing complet)
3. Envoyer à `AIClient`
4. Traiter réponse et stocker via `AISessionService`

### Command Processing Pipeline
```
EnrichmentBlock (stocké) → EnrichmentProcessor → UserCommandProcessor → CommandExecutor
                                            ↓
                                 List<DataCommand> → List<ExecutableCommand> → coordinator calls
```

**Responsabilités séparées :**
- `EnrichmentProcessor` : EnrichmentBlock brut → N DataCommands (cascade)
- `UserCommandProcessor` : Résolution périodes relatives, UI abstractions
- `AICommandProcessor` : Validation sécurité, limites données, token management
- `CommandExecutor` : ExecutableCommand → resource.operation + coordinator
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
    suspend fun query(promptResult: PromptResult, providerId: String): OperationResult
    suspend fun getAvailableProviders(): List<AIProviderInfo>
    suspend fun getActiveProviderId(): String?
}
```

Interface vers providers externes, utilise `AIProviderConfigService` via coordinator pour récupérer configurations.

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
data class ExecutableCommand(
    val resource: String,         // "zones", "tool_data"
    val operation: String,        // "get", "create", "update"
    val params: Map<String, Any>  // Paramètres résolus pour coordinator
)
```

**Types de commands unifiés :**
- **SYSTEM_SCHEMAS**, **SYSTEM_DOC**, **APP_CONFIG** (Level 1)
- **USER_TOOLS_CONTEXT** (Level 2)
- **APP_STATE** (Level 3)
- **ZONE_CONFIG**, **ZONE_STATS** (Level 4)
- **TOOL_CONFIG**, **TOOL_DATA_FULL**, **TOOL_DATA_SAMPLE**, **TOOL_DATA_FIELD**, **TOOL_STATS** (Level 4)

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
- **🔍 POINTER** - Référencer données → Multi-queries selon niveau sélection
- **📝 USE** - Modifier données outils → Multi-queries selon données ciblées
- **✨ CREATE** - Créer éléments → Pas de query (orientation seulement)
- **🔧 MODIFY_CONFIG** - Config outils → Multi-queries selon outils ciblés

### EnrichmentProcessor
```kotlin
class EnrichmentProcessor {
    fun generateSummary(type: EnrichmentType, config: String): String
    fun generateCommands(type: EnrichmentType, config: String, isRelative: Boolean): List<DataCommand>
}
```

### UserCommandProcessor
```kotlin
class UserCommandProcessor {
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand>
    // Résolution périodes relatives → timestamps absolus
    // UI abstractions → paramètres coordinator concrets
}
```

### AICommandProcessor
```kotlin
class AICommandProcessor {
    fun processDataCommands(commands: List<DataCommand>): List<ExecutableCommand>
    fun processActionCommands(commands: List<DataCommand>): List<ExecutableCommand>
    // Validation sécurité, limites données, token management
}
```

**Logique POINTER multi-queries selon niveau ZoneScopeSelector :**
- **ZONE** → `[ZONE_CONFIG, ZONE_STATS]`
- **INSTANCE** → `[TOOL_CONFIG, TOOL_DATA_SAMPLE]` + gestion temporelle
- **FIELD** → `[TOOL_DATA_FIELD]` + mode sample_entries + gestion temporelle

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

## 6. Système de queries et prompts

- **Level 1** : Documentation + schémas système (SYSTEM_SCHEMAS, SYSTEM_DOC, APP_CONFIG)
- **Level 2** : Contexte utilisateur dynamique (USER_TOOLS_CONTEXT)
- **Level 3** : État app complet (APP_STATE)
- **Level 4** : Enrichissements session (extraits de l'historique des messages)

### Types de commands par niveau (dont Extraction Level 4 depuis historique)
**Event sourcing Level 4 :** `EnrichmentBlock` (stocké) → `List<DataCommand>` (généré) dans pipeline. `PromptManager.getLevel4Commands()` extrait EnrichmentBlocks depuis historique messages et génère commands à la volée.

### CommandExecutor et pipeline processing
**Pipeline stateless pur** : Régénération complète à chaque message
```kotlin
// À chaque message : extraction EnrichmentBlocks → pipeline complet → prompt fresh
EnrichmentBlocks → EnrichmentProcessor → UserCommandProcessor → CommandExecutor
```

**Échec cascade :** Échec commande N → arrêt exécution N+1, N+2...

### QueryDeduplicator (conservé)
**Principe** : Déduplication incrémentale cross-niveaux pour assemblage prompt final
```kotlin
level1Content = commandExecutor.executeCommands(level1Commands, "Level1")
level2Content = commandExecutor.executeCommands(level2Commands, "Level2", previousCommands = level1Commands)
// Déduplication via QueryDeduplicator lors assemblage prompt
```

**Mécanismes de déduplication :**
1. **Hash identité** : Commands identiques supprimées (première occurrence gardée)
2. **Inclusion métier** : Commands plus générales incluent spécifiques selon règles business

### Storage Policy

**Ce qui EST stocké :**
- `AISession.messages: List<SessionMessage>`
- `SessionMessage` avec données brutes : EnrichmentBlocks dans RichMessage, aiMessageJson

**Ce qui N'EST JAMAIS stocké :**
- DataCommand (temporaire pipeline)
- ExecutableCommand (temporaire pipeline)
- Résultats commands dans prompt
- Prompt final assemblé

**Régénération complète :** Pipeline stateless à chaque message pour données toujours fraîches

**Stratégie Cache :** Envoi complet du prompt à chaque message. L'API (Claude, OpenAI, etc.) gère le cache automatiquement via préfixes identiques. **Décision architecturale** : Cohérence données > optimisation tokens.

### Dual mode résolution (CHAT absolu vs AUTOMATION relatif)
**CHAT** (`isRelative = false`) : "cette semaine" → timestamps figés absolus pour cohérence conversationnelle

**AUTOMATION** (`isRelative = true`) : "cette semaine" → paramètre relatif résolu au moment exécution via `resolveRelativeParams()`

**Token Management :** Validation individuelle des queries et validation globale du prompt. Gestion différenciée CHAT (dialogue confirmation) vs AUTOMATION (refus automatique) si dépassement.

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
