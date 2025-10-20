# Système IA - Documentation Technique

## 1. Architecture générale

### Pattern unifié
Toutes les interactions IA utilisent la même structure de données `SessionMessage` avec 5 variantes selon les champs remplis. Les sessions unifiées permettent réutilisation composants UI, logique persistance et transformation prompts.

### Session types
- **CHAT** : Conversation temps réel, queries absolues, modules communication
- **SEED** : Template automation (message user + enrichments), jamais exécuté
- **AUTOMATION** : Exécution autonome, queries relatives, copie messages SEED au démarrage

### Architecture Event-Driven (V2)
L'orchestrateur IA fonctionne comme une machine à états pilotée par événements. Single source of truth avec synchronisation atomique memory + DB.

**Composants principaux** :
- **AIOrchestrator** : Façade publique singleton exposant `currentState` observable
- **AIStateMachine** : Machine à états pure (transitions sans side effects)
- **AIEventProcessor** : Event loop avec side effects (DB, network, commands)
- **AIStateRepository** : Gestion atomique state memory + DB sync
- **AIMessageRepository** : Persistence messages avec cache observable
- **AISessionScheduler** : Scheduling, interruption, calcul inactivité

**API publique AIOrchestrator** :
- `currentState: StateFlow<AIState>` : État observable (phase, sessionId, counters, timestamps, waitingContext)
- `observeMessages(sessionId): Flow<List<SessionMessage>>` : Messages observables
- `sendMessage(richMessage)` : Envoyer message utilisateur
- `requestChatSession()` : Créer/activer session CHAT
- `executeAutomation(automationId)` : Lancer automation
- `stopActiveSession()` : Arrêter avec CANCELLED
- `resumeActiveSession()` : Reprendre la session
- `resumeWithValidation(approved)` : Répondre à validation
- `resumeWithResponse(response)` : Répondre à communication module

### Phase et AIState

**Phase** : 13 phases d'exécution
- `IDLE` : Pas de session active
- `EXECUTING_ENRICHMENTS` : Traitement enrichments user
- `CALLING_AI` : Appel provider IA
- `PARSING_AI_RESPONSE` : Parsing JSON réponse
- `WAITING_VALIDATION` : Attente validation user (CHAT)
- `WAITING_COMMUNICATION_RESPONSE` : Attente réponse communication module (CHAT)
- `EXECUTING_DATA_QUERIES` : Exécution data commands
- `EXECUTING_ACTIONS` : Exécution action commands
- `WAITING_COMPLETION_CONFIRMATION` : Attente confirmation completion (AUTOMATION)
- `WAITING_NETWORK_RETRY` : Attente retry réseau (AUTOMATION)
- `RETRYING_AFTER_FORMAT_ERROR` : Retry après erreur format
- `RETRYING_AFTER_ACTION_FAILURE` : Retry après échec actions
- `COMPLETED` : Session terminée

**AIState** : État complet système
```kotlin
data class AIState(
    val sessionId: String?,
    val phase: Phase,
    val sessionType: SessionType?,
    val totalRoundtrips: Int,
    val lastEventTime: Long,
    val lastUserInteractionTime: Long,
    val waitingContext: WaitingContext?
)
```

**WaitingContext** : Contextes d'attente typés
- `Validation(validationContext, cancelMessageId)` : Attente validation actions
- `Communication(communicationModule, cancelMessageId)` : Attente réponse communication
- `CompletionConfirmation(aiMessageId, scheduledConfirmationTime)` : Attente confirmation completion

### AIEvent
Événements déclenchant transitions : `SessionActivationRequested`, `UserMessageSent`, `EnrichmentsExecuted`, `AIResponseReceived`, `AIResponseParsed`, `ValidationReceived`, `CommunicationResponseReceived`, `DataQueriesExecuted`, `ActionsExecuted`, `CompletionConfirmed`, `CompletionRejected`, `NetworkErrorOccurred`, `ParseErrorOccurred`, `ActionFailureOccurred`, `NetworkRetryScheduled`, `RetryScheduled`, `NetworkAvailable`, `SystemErrorOccurred`, `SessionCompleted`, `SchedulerHeartbeat`.

## 2. Types et structures

### Types de résultats
- **OperationResult** : Services avec `.success: Boolean`
- **CommandResult** : Coordinator avec `.status: CommandStatus` (SUCCESS, FAILED, CANCELLED, CACHED)

### AISessionEntity (DB)
```kotlin
data class AISessionEntity(
    val id: String,
    val name: String,
    val type: SessionType,
    val requireValidation: Boolean,
    val phase: String,                      // Phase actuelle (serialized)
    val waitingContextJson: String?,        // WaitingContext (serialized)
    val totalRoundtrips: Int,
    val lastEventTime: Long,
    val lastUserInteractionTime: Long,
    val automationId: String?,
    val scheduledExecutionTime: Long?,
    val providerId: String,
    val providerSessionId: String,
    val createdAt: Long,
    val lastActivity: Long,
    val isActive: Boolean,
    val endReason: SessionEndReason?,
    val tokensUsed: String?                 // JSON tokens breakdown
)
```

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

**Pattern stockage** : Messages séparés USER → SYSTEM → AI → SYSTEM. Le provider ajuste selon ses contraintes.

**PostText success** : Après succès des actions, si `postText` présent dans AIMessage, un message séparé est créé avec `sender=AI`, `textContent=postText`, et `excludeFromPrompt=true`.

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
    val communicationModule: CommunicationModule?,
    val completed: Boolean?                   // true = travail terminé (AUTOMATION uniquement)
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
    val cacheWriteTokens: Int = 0,  // Cache write/creation tokens (generic, all providers)
    val cacheReadTokens: Int = 0,   // Cache read tokens (generic, all providers)
    val inputTokens: Int = 0         // Uncached input tokens
)
```

## 3. Configuration IA

### AILimitsConfig
Configuration globale des limites de boucles autonomes, intégrée dans `AppConfig`.

```kotlin
data class AILimitsConfig(
    val chatMaxAutonomousRoundtrips: Int = Int.MAX_VALUE,
    val automationMaxAutonomousRoundtrips: Int = 20
)
```

**Limite unique** :
- **AutonomousRoundtrips** : Sécurité anti-boucle infinie (AUTOMATION uniquement, CHAT = Int.MAX_VALUE)

**Compteur** : `totalRoundtrips` (jamais reset, compte tous les allers-retours).

**Rationale** : CHAT contrôlé par utilisateur (interrupt). AUTOMATION nécessite sécurité autonome. Pas de limites artificielles sur format errors ou action failures - l'IA doit auto-corriger. maxRoundtrips suffit comme filet de sécurité global.

**API** : `AppConfigService.getAILimits()`, `AppConfigManager.getAILimits()` (cache volatile).

**Inclusion Level 1** : Limites documentées dans prompt L1.

## 4. Composants et responsabilités

### Repositories
- **AIStateRepository** : Gestion atomique AIState (memory + DB sync), conversion Entity ↔ Domain, initialisation from DB
- **AIMessageRepository** : Persistence synchrone messages, cache observable par session, conversion Entity ↔ Domain

### Processing
- **AIEventProcessor** : Event loop avec side effects (executeEnrichments, callAI, parseAIResponse, executeDataQueries, executeActions)
- **AISessionScheduler** : Queue sessions, calcul inactivité, détection timeout, éviction/reprise sessions

### Coordination
- **AIOrchestrator** : Façade publique singleton, délégation aux composants spécialisés
- **AIClient** : Interface vers providers externes
- **PromptManager** : Génération prompts niveaux L1-L2
- **EnrichmentProcessor** : Génération commands depuis enrichments UI
- **CommandTransformer** : Transformation DataCommand → ExecutableCommand
- **CommandExecutor** : Point unique exécution + génération SystemMessage
- **ValidationResolver** : Résolution hiérarchie validation (app > zone > tool > session > AI request)

### Command Processing Pipeline
```
User: EnrichmentBlock → EnrichmentProcessor → CommandTransformer → CommandExecutor
AI:   AIMessage → CommandTransformer → CommandExecutor
```

## 5. Contrôle de session

### Session active exclusive
Une seule session active à la fois (CHAT ou AUTOMATION). `AISessionScheduler` gère queue et activation.

**Règles activation** :
- **CHAT** : Interruption immédiate si autre session active (même AUTOMATION)
- **AUTOMATION MANUAL** : Queue si slot occupé, activation FIFO
- **AUTOMATION SCHEDULED** : Création à la demande par `tick()` si slot libre + queue vide

**Scheduling** : Tick périodique (5 min) + événementiel (CRUD automations, fin session).

**Inactivité** : Calcul via `AIState.calculateInactivity()`, phases actives (CALLING_AI, EXECUTING_*) ne timeout jamais, phases waiting utilisent `lastUserInteractionTime`.

**SUSPENSION (système)** :
- `endReason = SUSPENDED`, libère le slot
- Session évincée pour laisser place à CHAT
- Reprend automatiquement quand slot libre (scheduler)

## 6. Automations

### Concept
Sessions AUTOMATION créées depuis template SEED (message user + enrichments). À chaque déclenchement : copie messages SEED → nouvelle session AUTOMATION → activation.

### Déclenchement
**ExecutionTrigger** distingue origine :
- **MANUAL** : User clique Execute → `executeAutomation(id)` → queue si slot occupé
- **SCHEDULED** : tick() calcule prochaine execution → créé uniquement si slot libre + queue vide
- **EVENT** : Future (triggers non planifiés)

**Triggers tick()** :
- Périodique : SchedulerWorker (5 min)
- Événementiel : CRUD automations (create/update/enable/disable)
- Fin session : scheduler déclenché après libération slot

### Architecture pull-based
```kotlin
tick() {
  if (slotOccupé) return
  if (queueNotEmpty) processQueue()
  else {
    nextSession = AutomationScheduler.getNextSession()  // Calcul dynamique
    if (nextSession) executeAutomation(id)
  }
}
```

**AutomationScheduler** : Helper pur de calcul. Trouve sessions incomplètes (endReason null/NETWORK_ERROR/SUSPENDED) OU prochaine execution depuis historique.

### Spécificités AUTOMATION vs CHAT

**Flag completed** : IA signale fin avec `completed: true` → phase `WAITING_COMPLETION_CONFIRMATION` → `CompletionConfirmed` event → `endReason=COMPLETED`.

**Continuation automatique** : Après succès actions, AUTOMATION continue automatiquement (pas de keepControl requis).

**Réseau** : Retry infini avec delay 30s si offline. Phase `WAITING_NETWORK_RETRY` (watchdog ne timeout pas).

**Communication modules** : Interdits pour AUTOMATION (validationRequest, communicationModule ignorés).

### Arrêt AUTOMATION
**UI boutons** :
- **STOP** : `stopActiveSession()` → `endReason=CANCELLED` (ne reprendra pas)

**Arrêt automatique** :
- **completed=true** : IA termine son travail → AWAITING_SESSION_CLOSURE (5s) → COMPLETED
- **Limite roundtrips** : maxAutonomousRoundtrips dépassé → AWAITING_SESSION_CLOSURE (5s) → LIMIT_REACHED
- **Erreur provider/système** : Erreur configuration provider ou erreur système → AWAITING_SESSION_CLOSURE (5s) → ERROR
- **Watchdog** : Inactivité réelle sans attente réseau → AWAITING_SESSION_CLOSURE (5s) → TIMEOUT

**CHAT** : Ne se ferme JAMAIS automatiquement, retourne toujours à IDLE (sauf erreurs réseau/provider/système). Limite roundtrips = Int.MAX_VALUE.

### Reprise sessions
Détection automatique sessions orphelines par AutomationScheduler :
- **endReason null** : Crash/interruption → reprise transparente
- **NETWORK_ERROR** : Échec réseau → reprise avec retry
- **SUSPENDED** : Éviction système → reprise quand slot libre

**Transparence** : Pas de message système, IA ne sait pas qu'elle reprend (continue naturellement).

### SessionEndReason
Raison d'arrêt session (audit + logique reprise) :
- **COMPLETED** : IA a terminé (completed=true)
- **LIMIT_REACHED** : Limite maxAutonomousRoundtrips atteinte
- **ERROR** : Erreur provider/système
- **TIMEOUT** : Watchdog inactivité sans attente réseau
- **CANCELLED** : User STOP (ne reprend pas)
- **SUSPENDED** : Éviction système (reprend plus tard)
- **NETWORK_ERROR** : Échec réseau (reprend avec retry)
- **null** : Crash/interruption (reprend)

## 7. Event loop et boucles autonomes

### Architecture event-driven

**AIEventProcessor** traite événements séquentiellement avec side effects :
1. Écoute événements via channel
2. Pour chaque event : `AIStateMachine.transition()` → nouveau state
3. `AIStateRepository.updateState()` → sync memory + DB atomique
4. Side effects selon event (appels DB, network, coordinator)

**Boucles autonomes** : Gérées par compteur `totalRoundtrips` dans `AIState` et limites `AILimitsConfig`. Machine à états incrémente automatiquement le compteur.

### Flow logique principal

```
Event UserMessageSent:
  → transition EXECUTING_ENRICHMENTS
  → side effect: executeEnrichments()
  → emit EnrichmentsExecuted

Event EnrichmentsExecuted:
  → transition CALLING_AI
  → side effect: callAI()
  → emit AIResponseReceived

Event AIResponseReceived:
  → transition PARSING_AI_RESPONSE
  → side effect: parseAIResponse()
  → emit AIResponseParsed

Event AIResponseParsed:
  → decision tree (completed? validation? communication? data? actions?)
  → transition vers phase appropriée

Event DataQueriesExecuted:
  → transition CALLING_AI, emit nouveau round

Event ActionsExecuted:
  → si allSuccess + (keepControl OR AUTOMATION): transition CALLING_AI
  → sinon (CHAT sans keepControl): transition IDLE
  → si échec actions: émet ActionFailureOccurred

Event ActionFailureOccurred:
  → transition RETRYING_AFTER_ACTION_FAILURE (pas de limite, IA doit adapter sa stratégie)

Event ParseErrorOccurred:
  → transition RETRYING_AFTER_FORMAT_ERROR (pas de limite, IA doit auto-corriger)

Event NetworkErrorOccurred:
  → si CHAT: transition IDLE (session reste active, user doit réessayer manuellement)
  → si AUTOMATION: transition WAITING_NETWORK_RETRY (retry automatique avec délai)
```

### Validation et Communication

**Validation** :
- `ValidationResolver` analyse hiérarchie (app > zone > tool > session > AI request)
- Si requis : `WaitingContext.Validation` créé avec `ValidationContext` + `cancelMessageId`
- Phase `WAITING_VALIDATION`
- Fallback message SYSTEM créé AVANT suspension
- UI observe `aiState.waitingContext` et affiche inline dans dernier message AI
- User répond : `resumeWithValidation(approved)` → `ValidationReceived` event
- Si refusé : garde message fallback, transition COMPLETED
- Si approuvé : supprime message fallback, exécute actions

**Communication** :
- Phase `WAITING_COMMUNICATION_RESPONSE`
- `WaitingContext.Communication` créé avec module + cancelMessageId
- Fallback message AI créé AVANT suspension (excludeFromPrompt=true)
- UI observe `aiState.waitingContext` et affiche inline dans dernier message AI
- User répond : `resumeWithResponse(response)` → `CommunicationResponseReceived` event
- Stocke réponse, supprime fallback, renvoie à IA

## 8. Gestion réseau et erreurs

**NetworkUtils** : `isNetworkAvailable(context)` pour vérification connectivité (core/utils).

**Timeout HTTP** : 2 minutes (OkHttp config providers).

**AUTOMATION** :
- Check réseau avant appel → offline = phase `WAITING_NETWORK_RETRY`
- Delay 30s + retry infini
- Watchdog ne timeout pas pendant retry réseau
- Retry jusqu'à réseau disponible OU user STOP

**CHAT** :
- Check réseau avant appel → offline = toast + `NETWORK_ERROR` event
- Pas de retry automatique
- Session reste active

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

**Flow** : EnrichmentProcessor → DataCommand → CommandTransformer → CommandExecutor → SystemMessage.

### CommandTransformer
**Transformations** : SCHEMA → schemas.get, TOOL_CONFIG → tools.get, TOOL_DATA → tool_data.get (résolution périodes), ZONE_CONFIG → zones.get, ZONES → zones.list, TOOL_INSTANCES → tools.list.

### User vs AI Commands
**User** : Source EnrichmentBlocks, types POINTER/USE/CREATE/MODIFY_CONFIG uniquement, but données contextuelles, jamais d'actions.
**AI** : Source AIMessage.dataCommands + actionCommands, types queries + actions réelles, but demander données + exécuter actions.

## 10. Architecture prompts

### 2 niveaux de contexte
**Level 1: DOC** - Généré par PromptChunks avec degrés d'importance configurables. Inclut rôle IA, documentation API, **limites IA dynamiques** selon SessionType, schémas (zone, tooltypes, communication modules). Pour AUTOMATION : documentation flag `completed: true` obligatoire + continuation automatique après succès actions.
**Level 2: USER DATA** - Données tool instances avec `always_send: true`.

**APP_STATE** : Zones et tool instances disponibles via command dédiée (à la demande).
**Enrichments** : Stockés comme SessionMessage sender=SYSTEM, inclus dans l'historique.

### PromptManager.buildPromptData()
```kotlin
suspend fun buildPromptData(sessionId: String): PromptData {
    // L1-L2 régénérés à chaque appel (jamais cachés en DB)
    val level1Content = PromptChunks.buildLevel1StaticDoc(context, sessionType, config)
    // L2: USER DATA (always_send tools)

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
// Pattern UI
val aiState by AIOrchestrator.currentState.collectAsState()

// Affichage inline dans ChatMessageBubble (dernier message AI uniquement)
if (isLastAIMessage && aiState.waitingContext is WaitingContext.Communication) {
    val ctx = aiState.waitingContext as WaitingContext.Communication
    CommunicationModuleCard(
        module = ctx.communicationModule,
        onResponse = { response ->
            AIOrchestrator.resumeWithResponse(response)
        }
    )
}
```

**Flow** :
1. IA génère AIMessage avec CommunicationModule
2. Event processor crée `WaitingContext.Communication`
3. State transition → `WAITING_COMMUNICATION_RESPONSE`
4. UI observe `aiState.waitingContext` et affiche inline
5. User répond → `resumeWithResponse(response)`
6. Event `CommunicationResponseReceived` → stocker réponse
7. Transition `CALLING_AI` → renvoyer à IA

### Validation des actions IA
**Hiérarchie OR** : app > zone > tool > session > AI request. Si UN niveau true → validation requise. `validationRequest` = Boolean dans AIMessage.

**Flow** : ValidationResolver analyse actions → génère ValidationContext (actions verbalisées + raisons + warnings config) → `WaitingContext.Validation` créé → UI affiche inline → user valide/refuse → `resumeWithValidation(validated)`.

**Messages fallback** : COMMUNICATION_CANCELLED / VALIDATION_CANCELLED créés AVANT suspension (trace si fermeture app/navigation). Supprimés si user répond/valide effectivement.

**Persistance** : `WaitingContext` serialisé en JSON dans DB (champ `waitingContextJson`).

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

*L'architecture IA V2 event-driven garantit cohérence state, recovery automatique, autonomie contrôlée et extensibilité.*
