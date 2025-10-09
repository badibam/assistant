# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

### Session types
- **CHAT** : Conversation temps réel, queries absolues, modules communication
- **AUTOMATION** : Prompt programmable, queries relatives, feedback exécutions

### AIOrchestrator singleton
L'orchestrateur IA maintient une session active unique avec queue FIFO pour sessions en attente.

**API principale** :
- `requestSessionControl()` : Demande contrôle session (ACTIVATED/ALREADY_ACTIVE/QUEUED)
- `processUserMessage()` : Traite message user avec enrichments
- `executeAIRound(reason)` : Exécute round IA complet avec boucles autonomes
- `sendMessage()` : Wrapper processUserMessage + executeAIRound

**StateFlows observables** : `waitingState` (validation/communication), `isRoundInProgress` (protection concurrent).

## 2. Types et structures

### Types de résultats
- **OperationResult** : Services avec `.success: Boolean`
- **CommandResult** : Coordinator avec `.status: CommandStatus` (SUCCESS, FAILED, CANCELLED, CACHED)

### AISession
```kotlin
data class AISession(
    val id: String,
    val name: String,
    val type: SessionType,
    val automationId: String?,              // AUTOMATION : ID automation source, null pour CHAT
    val scheduledExecutionTime: Long?,      // AUTOMATION : timestamp référence pour résolution périodes relatives
    val providerId: String,
    val providerSessionId: String,
    val schedule: ScheduleConfig?,          // AUTOMATION uniquement
    val createdAt: Long,
    val lastActivity: Long,
    val messages: List<SessionMessage>,
    val isActive: Boolean
)
```

**scheduledExecutionTime** : Pour AUTOMATION, référence temporelle utilisée pour résolution RelativePeriod (permet traitement correct même si exécution retardée).

### SessionMessage (structure unifiée)
```kotlin
data class SessionMessage(
    val id: String,
    val timestamp: Long,
    val sender: MessageSender,         // USER, AI, SYSTEM
    val richContent: RichMessage?,     // Messages enrichis utilisateur
    val textContent: String?,          // Messages simples (réponses modules)
    val aiMessage: AIMessage?,         // Structure IA parsée pour UI
    val aiMessageJson: String?,        // JSON original pour historique prompts
    val systemMessage: SystemMessage?, // Messages système avec résultats
    val executionMetadata: ExecutionMetadata?, // Automations uniquement
    val excludeFromPrompt: Boolean = false // Exclure du prompt (messages UI uniquement)
)
```

**Pattern stockage** : Messages séparés USER → SYSTEM → AI → SYSTEM. Le provider ajuste selon ses contraintes (généralement : pas de message system dans flux des messages)

**PostText success** : Après succès des actions, si `postText` présent dans AIMessage, un message séparé est créé avec `sender=AI`, `textContent=postText`, et `excludeFromPrompt=true`. Ce message s'affiche en UI mais est exclu du prompt (le postText reste dans le message AI original pour l'historique IA).

### RichMessage et AIMessage
```kotlin
data class RichMessage(
    val segments: List<MessageSegment>,       // Text | EnrichmentBlock
    val linearText: String,                   // Calculé
    val dataCommands: List<DataCommand>       // Calculé
)

data class AIMessage(
    val preText: String,                      // Obligatoire
    val validationRequest: Boolean?,          // true = validation requise
    val dataCommands: List<DataCommand>?,     // OU actions (exclusif)
    val actionCommands: List<DataCommand>?,
    val postText: String?,
    val keepControl: Boolean?,                // true = garde la main après succès actions
    val communicationModule: CommunicationModule?
)
```

**Patterns AIMessage** : Actions (preText + validationRequest? + actionCommands + postText?), Queries (preText + dataCommands), Communication (preText + communicationModule uniquement).

**Fallback parsing** : Si parsing JSON échoue, création AIMessage avec préfixe `"ai_response_invalid_format"` + texte brut dans preText.

### SystemMessage
```kotlin
data class SystemMessage(
    val type: SystemMessageType,           // DATA_ADDED, ACTIONS_EXECUTED, LIMIT_REACHED, FORMAT_ERROR, NETWORK_ERROR, SESSION_TIMEOUT
    val commandResults: List<CommandResult>,
    val summary: String,
    val formattedData: String?              // JSON résultats (queries uniquement)
)

enum class SystemMessageType {
    DATA_ADDED,          // Résultats queries → envoyé au prompt
    ACTIONS_EXECUTED,    // Résultats actions → envoyé au prompt
    LIMIT_REACHED,       // Limite atteinte → envoyé au prompt
    FORMAT_ERROR,        // Erreur de format réponse IA → envoyé au prompt pour correction
    NETWORK_ERROR,       // Erreurs réseau/HTTP/provider → filtré du prompt (audit uniquement)
    SESSION_TIMEOUT      // Timeout watchdog session → filtré du prompt (audit uniquement)
}
```

**formattedData** : Données JSON complètes formatées pour prompt. Concaténation `PromptCommandResult` avec titres. DATA_ADDED uniquement.

### Commands et PromptData
```kotlin
data class DataCommand(
    val id: String,              // Hash déterministe
    val type: String,            // TOOL_DATA, CREATE_DATA, etc.
    val params: Map<String, Any>,
    val isRelative: Boolean = false
)

data class ExecutableCommand(
    val resource: String,        // "zones", "tool_data"
    val operation: String,       // "get", "batch_create"
    val params: Map<String, Any>
)

data class PromptData(
    val level1Content: String,     // Documentation système (avec limites)
    val level2Content: String,     // User data (always_send tools)
    val level3Content: String,     // Application state
    val sessionMessages: List<SessionMessage>
)
```

### AIResponse
```kotlin
data class AIResponse(
    val success: Boolean,
    val content: String,
    val errorMessage: String? = null,
    val tokensUsed: Int = 0,
    val cacheCreationTokens: Int = 0,  // Provider-specific
    val cacheReadTokens: Int = 0,
    val inputTokens: Int = 0
)
```

## 3. Configuration IA

### AILimitsConfig
Configuration globale des limites de boucles autonomes et timeouts, intégrée dans `AppConfig`.

```kotlin
data class AILimitsConfig(
    // CHAT LIMITS
    val chatMaxDataQueryIterations: Int = 3,
    val chatMaxActionRetries: Int = 3,
    val chatMaxFormatErrorRetries: Int = 3,
    val chatMaxAutonomousRoundtrips: Int = 10,
    // AUTOMATION LIMITS
    val automationMaxDataQueryIterations: Int = 5,
    val automationMaxActionRetries: Int = 5,
    val automationMaxFormatErrorRetries: Int = 5,
    val automationMaxAutonomousRoundtrips: Int = 20,
    // CHAT : ÉVICTION PAR AUTOMATION (ms)
    val chatMaxInactivityBeforeAutomationEviction: Long = 5 * 60 * 1000,  // 5 min
    // AUTOMATION : WATCHDOG (ms)
    val automationMaxSessionDuration: Long = 10 * 60 * 1000  // 10 min
)
```

**Types de limites** :
- **DataQueryIterations** : Nombre consécutif de dataCommands IA
- **ActionRetries** : Tentatives pour actions échouées
- **FormatErrorRetries** : Tentatives pour erreurs de format (communicationModule, etc.)
- **AutonomousRoundtrips** : Limite totale tous types (sécurité)

**Gestion session** :
- **chatMaxInactivityBeforeAutomationEviction** : Si AUTOMATION demande la main et CHAT inactive depuis > cette durée → arrêt forcé CHAT. Si CHAT inactive < cette durée → AUTOMATION attend en queue
- **automationMaxSessionDuration** : Watchdog pour éviter boucles infinies AUTOMATION (CHAT n'a pas de timeout - arrêt via bouton UI)

**Compteurs** : Consécutifs pour DataQuery/ActionRetry/FormatError (reset si changement type), total pour AutonomousRoundtrips (jamais reset).

**API** : `AppConfigService.getAILimits()`, `AppConfigManager.getAILimits()` (cache volatile).

**Inclusion Level 1** : Limites documentées dans prompt L1 (valeurs fixes, pas de compteurs dynamiques).

## 4. Services et responsabilités

### Services ExecutableService
- `AISessionService` : CRUD sessions et messages
- `AIProviderConfigService` : CRUD configurations providers

### Classes métier pures
- `AIOrchestrator` : Orchestration flow IA (singleton)
- `AIClient` : Interface vers providers externes
- `PromptManager` : Génération prompts 3 niveaux avec PromptData
- `EnrichmentProcessor` : Génération commands depuis enrichments UI
- `CommandTransformer` (object) : Transformation DataCommand → ExecutableCommand
- `UserCommandProcessor` : Délègue à CommandTransformer
- `AICommandProcessor` : Transformation commands IA (queries et actions séparées)
- `CommandExecutor` : Point unique d'exécution + génération SystemMessage

### CommandExecutor
```kotlin
class CommandExecutor(context: Context) {
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        messageType: SystemMessageType,
        level: String
    ): CommandExecutionResult
}

data class CommandExecutionResult(
    val promptResults: List<PromptCommandResult>,
    val systemMessage: SystemMessage
)
```

**Responsabilités** : Point unique d'exécution (User/AI), appels coordinator, formatage PromptCommandResult (queries), génération SystemMessage (UN par série), NE stocke JAMAIS.

### Command Processing Pipeline
```
User: EnrichmentBlock → EnrichmentProcessor → UserCommandProcessor → CommandTransformer → CommandExecutor
AI:   AIMessage → AICommandProcessor → CommandTransformer/Actions → CommandExecutor
```

## 5. Contrôle de session

### Session active exclusive
Une seule session active à la fois (CHAT ou AUTOMATION), les autres en queue FIFO.

**Règles CHAT** :
- Switch immédiat si autre CHAT actif
- Priorité position 1 si AUTOMATION active
- Un seul CHAT en queue

**Règles AUTOMATION** :
- Si CHAT actif et inactif depuis > `chatMaxInactivityBeforeAutomationEviction` → arrêt forcé CHAT, activation AUTOMATION
- Si CHAT actif et inactif depuis < limite → queue FIFO standard
- Si AUTOMATION active → queue FIFO standard

**Persistance sessions** : Aucun timeout d'inactivité automatique. Sessions persistent jusqu'à arrêt explicite (bouton UI) ou éviction par AUTOMATION (CHAT uniquement).

**Watchdog AUTOMATION** : Flag `shouldTerminateRound` force termination si `automationMaxSessionDuration` dépassé → SESSION_TIMEOUT.

## 6. Séparation message/round

### RoundReason
`USER_MESSAGE`, `FORMAT_ERROR_CORRECTION`, `LIMIT_NOTIFICATION`, `DATA_RESPONSE`, `MANUAL_TRIGGER`.

### Méthodes principales

**processUserMessage()** : Exécute enrichments, stocke message user + SystemMessage enrichments, update lastActivityTimestamp.

**executeAIRound(reason)** : Protection concurrent (`isRoundInProgress`), build promptData, check réseau, query IA (retry si AUTOMATION), boucles autonomes, processNextInQueue(). Watchdog AUTOMATION vérifie `shouldTerminateRound` dans boucle.

**sendMessage()** : Wrapper processUserMessage + executeAIRound.

## 7. Boucles autonomes

### Architecture 4 compteurs
```kotlin
var totalRoundtrips = 0
var consecutiveDataQueries = 0
var consecutiveActionRetries = 0
var consecutiveFormatErrors = 0

val limits = getLimitsForSessionType(sessionType)
```

### Flow logique dans executeAIRound()
```
// AUTOMATION uniquement : watchdog concurrent avec flag shouldTerminateRound
while (totalRoundtrips < limits.maxAutonomousRoundtrips && !shouldTerminateRound):

  Priorité 0: FORMAT ERRORS (avant tout traitement)
    - Vérifier parseResult.formatErrors après parsing réponse IA
    - Si erreurs → vérifier limite consecutiveFormatErrors
    - Si limite atteinte → storeLimitReachedMessage + break
    - Stocker FORMAT_ERROR SystemMessage
    - Renvoyer auto à IA avec erreurs pour correction
    - Incrémenter consecutiveFormatErrors + totalRoundtrips, continue
    - Si message correctement parsé → reset consecutiveFormatErrors

  Priorité 1: COMMUNICATION MODULE
    - STOP → waitForUserResponse() (suspend via StateFlow)
    - Stocker réponse user
    - Renvoyer auto à IA
    - Incrémenter totalRoundtrips

  Priorité 2: DATA COMMANDS (queries)
    - Vérifier limite consecutiveDataQueries
    - Exécuter via AICommandProcessor → CommandExecutor
    - Stocker SystemMessage
    - Renvoyer auto à IA
    - Incrémenter consecutiveDataQueries, reset consecutiveActionRetries, totalRoundtrips++

  Priorité 3: ACTION COMMANDS (mutations)
    - ValidationResolver analyse hiérarchie configs → ValidationContext si requis
    - Créer message VALIDATION_CANCELLED fallback AVANT suspension
    - Si validation requise → STOP → waitForUserValidation(context, cancelMessageId)
    - Si refusé → garde message VALIDATION_CANCELLED + break
    - Exécuter actions via AICommandProcessor → CommandExecutor
    - Stocker SystemMessage
    - Si allSuccess:
      - Si postText présent → storePostTextMessage (excludeFromPrompt=true)
      - Si keepControl == true → renvoyer auto à IA avec résultats, reset compteurs consécutifs, totalRoundtrips++, continue
      - Sinon → break (FIN)
    - Sinon → vérifier limite consecutiveActionRetries, renvoyer IA
    - Incrémenter consecutiveActionRetries, reset consecutiveDataQueries, totalRoundtrips++

  Si aucun → break

Si totalRoundtrips >= limite → storeLimitReachedMessage (LIMIT_REACHED)
```

### Pattern StateFlow
```kotlin
// Attente validation (Phase 7: fallback message + cancelMessageId)
private suspend fun waitForUserValidation(context: ValidationContext, cancelMessageId: String): Boolean =
    suspendCancellableCoroutine { cont ->
        _waitingState.value = WaitingState.WaitingValidation(context, cancelMessageId)
        validationContinuation = cont
    }

fun resumeWithValidation(validated: Boolean) {
    if (validated) deleteMessage(cancelMessageId) // Supprime fallback si validé
    validationContinuation?.resume(validated)
    validationContinuation = null
    _waitingState.value = WaitingState.None
}

// Idem pour waitForUserResponse/resumeWithResponse avec COMMUNICATION_CANCELLED
```

**Helpers** : `createAndStoreValidationCancelledMessage()` crée fallback AVANT suspension, `storeLimitReachedMessage()` crée SystemMessage type LIMIT_REACHED.

## 8. Gestion réseau et erreurs

**NetworkUtils** : `isNetworkAvailable(context)` pour vérification connectivité (core/utils).

**Timeout HTTP** : 2 minutes (OkHttp config providers).

**AUTOMATION** : Check réseau avant appel → offline = requeue + NETWORK_ERROR. Retry 3x avec backoff (5s, 15s, 30s) si erreur → échec final = requeue + NETWORK_ERROR.

**CHAT** : Check réseau avant appel → offline = toast, session reste active. Pas de retry automatique, toast erreur, NETWORK_ERROR stocké, session reste active.

## 9. Enrichissements

### Types d'enrichissements
- **🔍 POINTER** - Référencer données (zones ou instances)
- **📝 USE** - Utiliser données d'outils (config + schemas + data + stats)
- **✨ CREATE** - Créer éléments (schemas pour tooltype)
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

**Périodes** : CHAT (isRelative=false) → timestamps absolus via Period, AUTOMATION (isRelative=true) → périodes relatives format "offset_TYPE".

**Flow** : EnrichmentProcessor → DataCommand → UserCommandProcessor → CommandTransformer → CommandExecutor → SystemMessage stocké séparément (plus dans system prompt).

### CommandTransformer
**Transformations** : SCHEMA → schemas.get, TOOL_CONFIG → tools.get, TOOL_DATA → tool_data.get (résolution périodes), ZONE_CONFIG → zones.get, ZONES → zones.list, TOOL_INSTANCES → tools.list.

### User vs AI Commands
**User** : Source EnrichmentBlocks, types POINTER/USE/CREATE/MODIFY_CONFIG uniquement, but données contextuelles, jamais d'actions.
**AI** : Source AIMessage.dataCommands + actionCommands, types queries + actions réelles, but demander données + exécuter actions.

## 10. Architecture prompts

### 2 niveaux de contexte
**Level 1: DOC** - Généré par PromptChunks avec degrés d'importance configurables. Inclut rôle IA, documentation API, **limites IA dynamiques** selon SessionType, schémas (zone, tooltypes, communication modules).
**Level 2: USER DATA** - Données tool instances avec `always_send: true`.

**APP_STATE** : Zones et tool instances disponibles via command dédiée (à la demande).
**Enrichments** : Stockés comme SessionMessage sender=SYSTEM, inclus dans l'historique.

### PromptManager.buildPromptData()
```kotlin
suspend fun buildPromptData(sessionId: String): PromptData {
    // L1-L2 régénérés à chaque appel (jamais cachés en DB)
    // L1 utilise PromptChunks avec configuration par degrés d'importance
    val level1Content = PromptChunks.buildLevel1StaticDoc(context, sessionType, config)
    // L2: USER DATA (always_send tools)
    // Note: Level 3 supprimé - IA utilise APP_STATE command à la demande

    // Filtrage messages exclus du prompt:
    // - NETWORK_ERROR et SESSION_TIMEOUT (audit uniquement)
    // - excludeFromPrompt=true (messages UI uniquement, comme postText success)
    val sessionMessages = loadMessages(sessionId)
        .filter { message ->
            val type = message.systemMessage?.type
            val isSystemError = type == SystemMessageType.NETWORK_ERROR || type == SystemMessageType.SESSION_TIMEOUT
            !isSystemError && !message.excludeFromPrompt
        }

    return PromptData(level1Content, level2Content, sessionMessages)
}
```

**Assembly** : Le prompt final est assemblé par le provider (fusion messages, application cache_control).

### Storage Policy
**Stocké** : SessionMessage (USER/AI/SYSTEM), RichMessage, SystemMessage avec résultats queries/actions (formattedData + commandResults), aiMessageJson.
**Non stocké (régénéré)** : Niveaux L1-L2, DataCommand/ExecutableCommand (temporaire), prompt final assemblé.

### Dual mode résolution
**CHAT** (isRelative=false) : Périodes absolues (Period timestamps fixes).
**AUTOMATION** (isRelative=true) : Périodes relatives (RelativePeriod "offset_TYPE") résolues via AppConfigManager.

## 11. Provider abstraction

### Signature AIProvider
```kotlin
interface AIProvider {
    fun getDisplayName(): String
    fun getConfigSchema(): String
    @Composable fun getConfigScreen(config: String, onSave: (String) -> Unit)
    suspend fun query(promptData: PromptData, config: String): AIResponse
}
```

**Responsabilités provider** : Parser config, transformer promptData (structure spécifique API), fusionner messages (contraintes alternance si applicable), appeler API HTTP, parser réponse (content, tokens, erreurs).

### Pattern extensions
**Exemple ClaudeExtensions.kt** :
```kotlin
internal fun PromptData.toClaudeJson(config: JSONObject): JsonObject
internal fun JsonElement.toClaudeAIResponse(): AIResponse
```

**Avantages** : Testable séparément, concis, logique complexe isolée.

### Fusion messages (pattern général)
Le provider fusionne USER/SYSTEM consécutifs pour respecter contraintes API.

**Exemple** : DB (1.USER "Question" → 2.SYSTEM enrichments → 3.AI réponse → 4.SYSTEM queries → 5.USER "Autre") transformé en API (1.USER ["Question", "enrichments"] → 2.ASSISTANT réponse → 3.USER ["queries", "Autre"]).

### ClaudeProvider - Cache control (spécifique)
**3 breakpoints** : L1 dernier bloc, L2 dernier bloc, dernier bloc du dernier message historique.
**Automatic prefix checking** : Messages précédents (sans cache_control) automatiquement cachés (~20 blocs avant le 3ème breakpoint).

**Structure** : system array avec L1/L2 + cache_control, messages array avec fusion USER/SYSTEM. Le dernier message est forcé en format array pour supporter cache_control sur son dernier bloc.

### Configuration
Configurations gérées par `AIProviderConfigService`, providers découverts via `AIProviderRegistry`, `AIClient` utilise coordinator (pas d'accès DB direct).

## 12. Communication modules

### Structure
```kotlin
sealed class CommunicationModule {
    abstract val type: String
    abstract val data: Map<String, Any>

    data class MultipleChoice(type: String = "MultipleChoice", data: Map<String, Any>)
    data class Validation(type: String = "Validation", data: Map<String, Any>)
}
```

**Validation** : Via `CommunicationModuleSchemas` (object) avec schémas JSON. MultipleChoice (question, options array min 2), Validation (message).

**Parsing** : Types non reconnus ou parsing échoué → module ignoré (null).

### Flow de réponse utilisateur
```kotlin
1. IA génère AIMessage avec CommunicationModule
2. AIOrchestrator met à jour _waitingState.value = WaitingState.WaitingResponse(module)
3. UI observe via collectAsState() et affiche module
4. User répond → AIOrchestrator.resumeWithResponse(userResponse)
5. Stocker réponse dans SessionMessage.textContent
6. Renvoyer automatiquement à IA
7. Continuer boucle avec nouvelle réponse

// Pattern UI
val waitingState by AIOrchestrator.waitingState.collectAsState()
when (waitingState) {
    is WaitingState.WaitingResponse -> CommunicationModuleDialog(module) { response ->
        AIOrchestrator.resumeWithResponse(response)
    }
}
```

### Validation des actions IA
**Hiérarchie OR** : app > zone > tool > session > AI request. Si UN niveau true → validation requise. `validationRequest` = Boolean dans AIMessage.

**Flow** : ValidationResolver analyse actions → génère ValidationContext (actions verbalisées + raisons + warnings config) → `waitForUserValidation(context, cancelMessageId)` suspend → UI affiche → user valide/refuse → `resumeWithValidation(validated)` reprend.

**Messages fallback** : COMMUNICATION_CANCELLED / VALIDATION_CANCELLED créés AVANT suspension (trace si fermeture app/navigation). Supprimés si user répond/valide effectivement. Communication module : message AI texte (excludeFromPrompt=true) + fallback SYSTEM. Validation : fallback SYSTEM uniquement.

**Persistance** : Pas de sérialisation WaitingState. Historique messages suffit. Coroutines scope singleton AIOrchestrator (survive navigation ChatScreen).

## 13. SystemMessages

### Génération et stockage
**Générés par** : CommandExecutor après chaque série de commandes. **Stockés comme** : SessionMessage sender=SYSTEM. **Point unique** : CommandExecutor seul responsable (User et AI).

### Types et placement
**Enrichments user** : Générés après exécution enrichments, stockés après message USER, type DATA_ADDED avec formattedData.
**AI queries** : Générés après exécution dataCommands IA, stockés après réponse AI, type DATA_ADDED avec formattedData.
**AI actions** : Générés après exécution actionCommands IA, stockés après réponse AI, type ACTIONS_EXECUTED sans formattedData.
**Limites** : Générés quand limite atteinte, type LIMIT_REACHED avec summary, pas de renvoie auto (attend message user).
**Format errors** : Générés quand parsing communicationModule échoue, type FORMAT_ERROR avec détails erreurs, renvoie auto à l'IA pour correction.
**Erreurs système** : Générés pour erreurs réseau (NETWORK_ERROR) et timeout watchdog (SESSION_TIMEOUT). Filtrés du prompt IA (audit uniquement).

### Format dans prompts
Provider décide du format d'inclusion. Généralement fusion avec messages USER consécutifs (formattedData ajouté comme content block).

**Filtrage** : NETWORK_ERROR, SESSION_TIMEOUT et messages avec `excludeFromPrompt=true` exclus du contexte IA (audit et UI uniquement).

---

*L'architecture IA garantit autonomie contrôlée, optimisation cache provider-specific, et extensibilité multi-providers.*
