# Architecture IA V2 - Event-Driven

**Date :** 16 octobre 2025
**Status :** Design approuvé - Implémentation à venir
**Migration :** Big bang

---

## 1. DÉCISIONS ARCHITECTURALES

### 1.1 Pattern Principal
**Event-Driven Architecture** avec state machine explicite

**Pourquoi :**
- Élimine 6 boucles imbriquées actuelles
- State cohérent (memory + DB synchrone)
- Testable (mock events)
- Scalable (ajout features facile)

### 1.2 Single Source of Truth
**Memory = source of truth** pendant exécution
**DB = backup** avec sync transactionnelle après chaque transition

### 1.3 Configuration
- **Retry policy :** Global uniquement
- **Validation :** Hiérarchie OR (app > zone > tool > session > AI)
- **Timeouts :**
  - Chat inactivity: 5 min
  - Automation inactivity: 2 min
  - Network retry: 30s delay, infini pour AUTOMATION

### 1.4 Persistence
- **Messages :** Storage synchrone (élimine race conditions)
- **Waiting states :** Champs dans AISessionEntity (pas table séparée)
- **State transitions :** Transaction DB atomique avec memory update

---

## 2. STATE MACHINE

### 2.1 Phases

```kotlin
enum class Phase {
    IDLE,
    EXECUTING_ENRICHMENTS,
    CALLING_AI,
    PARSING_AI_RESPONSE,
    WAITING_VALIDATION,              // CHAT only
    WAITING_COMMUNICATION_RESPONSE,  // CHAT only
    EXECUTING_DATA_QUERIES,
    EXECUTING_ACTIONS,
    WAITING_COMPLETION_CONFIRMATION, // AUTOMATION only
    WAITING_NETWORK_RETRY,           // Infini pour AUTOMATION
    RETRYING_AFTER_FORMAT_ERROR,
    RETRYING_AFTER_ACTION_FAILURE,
    COMPLETED
}
```

### 2.2 State Structure

```kotlin
data class AIState(
    val sessionId: String?,
    val phase: Phase,
    val sessionType: SessionType?,  // CHAT, AUTOMATION, SEED

    // Retry counters
    val consecutiveFormatErrors: Int = 0,
    val consecutiveActionFailures: Int = 0,
    val consecutiveDataQueries: Int = 0,
    val totalRoundtrips: Int = 0,

    // Timestamps
    val lastEventTime: Long = 0,
    val lastUserInteractionTime: Long = 0,

    // Waiting context
    val waitingContext: WaitingContext? = null
)
```

### 2.3 Transitions Clés

```
IDLE → SessionActivationRequested → EXECUTING_ENRICHMENTS
EXECUTING_ENRICHMENTS → EnrichmentsExecuted → CALLING_AI
CALLING_AI → AIResponseReceived → PARSING_AI_RESPONSE
CALLING_AI → NetworkErrorOccurred → WAITING_NETWORK_RETRY
PARSING_AI_RESPONSE → AIResponseParsed → [décision selon AIMessage]
PARSING_AI_RESPONSE → ParseErrorOccurred → [check retry limit] → CALLING_AI ou COMPLETED

Décision après parsing:
- completed=true (AUTOMATION) → WAITING_COMPLETION_CONFIRMATION
- validationRequest=true → WAITING_VALIDATION
- communicationModule (CHAT) → WAITING_COMMUNICATION_RESPONSE
- dataCommands → EXECUTING_DATA_QUERIES
- actionCommands → EXECUTING_ACTIONS
- sinon → COMPLETED

EXECUTING_ACTIONS → ActionsExecuted → [check keepControl ou AUTOMATION] → CALLING_AI ou COMPLETED
EXECUTING_ACTIONS → ActionFailureOccurred → [check retry limit] → CALLING_AI ou COMPLETED

WAITING_NETWORK_RETRY → NetworkRetryScheduled → CALLING_AI (après 30s delay)
WAITING_NETWORK_RETRY → SessionCompleted → COMPLETED (user stop/pause)

any → SessionCompleted → COMPLETED
```

### 2.4 Retry Limits

Compteurs **consécutifs** (reset si changement type):
- Format errors: maxFormatRetries
- Action failures: maxActionRetries
- Data queries: maxDataQueries

Compteur **total** (jamais reset):
- totalRoundtrips: maxAutonomousRoundtrips

Limite atteinte → transition vers COMPLETED

---

## 3. EVENTS

### 3.1 Events Complets

```kotlin
sealed class AIEvent {
    // Session lifecycle
    data class SessionActivationRequested(val sessionId: String)
    data class SessionCompleted(val reason: SessionEndReason)

    // User message
    data class EnrichmentsExecuted(val results: List<CommandResult>)

    // AI interaction
    data class AIResponseReceived(val content: String)
    data class AIResponseParsed(val message: AIMessage)

    // User interactions (CHAT only)
    data class ValidationReceived(val approved: Boolean)
    data class CommunicationResponseReceived(val response: String)

    // Execution
    data class DataQueriesExecuted(val results: List<CommandResult>)
    data class ActionsExecuted(
        val results: List<CommandResult>,
        val keepControl: Boolean?
    )

    // Completion (AUTOMATION only)
    object CompletionConfirmed
    object CompletionRejected

    // Errors & Retry
    data class NetworkErrorOccurred(val attempt: Int)
    data class ParseErrorOccurred(val error: String)
    data class ActionFailureOccurred(val errors: List<CommandResult>)
    object NetworkRetryScheduled
    object RetryScheduled
    object NetworkAvailable
    data class SystemErrorOccurred(val message: String)

    // System
    object SchedulerHeartbeat  // Watchdog + scheduling (toutes les 5 min)
}
```

### 3.2 Event Flow

```
User/System action → emit(Event)
  → EventProcessor.processEvent()
  → StateRepository.emit()
  → StateMachine.transition()
  → DB transaction + memory update
  → Side effect basé sur nouvelle phase
  → emit(next Event) si nécessaire
```

---

## 4. COMPOSANTS

### 4.1 Architecture

```
AIOrchestrator (Singleton - API publique)
  ├─ AIStateRepository (state + DB sync)
  ├─ AIEventProcessor (event loop + side effects)
  │   ├─ AIClient (appels IA)
  │   ├─ PromptManager (build prompts)
  │   ├─ AIMessageRepository (persistence messages)
  │   ├─ ValidationResolver (validation cascade logic)
  │   └─ Coordinator (execute commands)
  └─ AISessionScheduler (next session + interruption logic)
      └─ AutomationScheduler (calculate next scheduled)
```

### 4.2 Responsabilités

**AIOrchestrator**
- API publique uniquement
- Délègue tout aux composants

```kotlin
fun sendMessage(sessionId: String, text: String, enrichments: List<...>)
fun activateSession(sessionId: String)
fun requestChatSession()
fun resumeWithValidation(approved: Boolean)
fun resumeWithResponse(response: String)
fun stopSession(reason: SessionEndReason)
val currentState: StateFlow<AIState>
```

**AIStateRepository**
- Single source of truth
- Transitions atomiques (memory + DB)

```kotlin
suspend fun emit(event: AIEvent): AIState
val state: StateFlow<AIState>
```

**AIEventProcessor**
- Event loop principal
- Side effects par phase
- Emit events suivants

```kotlin
fun emit(event: AIEvent)
private suspend fun processEvent(event: AIEvent)
```

**AISessionScheduler**
- Logique next session (CHAT > MANUAL > SCHEDULED)
- Logique interruption/éviction
- Calcul inactivité

```kotlin
suspend fun getNextSession(): SessionToActivate?
suspend fun requestSession(id: String, type: SessionType, trigger: ExecutionTrigger): ActivationResult
```

**AIStateMachine**
- Transitions pures (testable)
- Pas de side effects

```kotlin
fun transition(state: AIState, event: AIEvent): AIState
```

**AIMessageRepository**
- Persistence messages (synchrone)
- Observable messages flow

```kotlin
suspend fun storeMessage(sessionId: String, message: SessionMessage)
fun observeMessages(sessionId: String): Flow<List<SessionMessage>>
```

**ValidationResolver**
- Logique cascade (app > zone > tool > session > AI)
- Réutilisable (UI + EventProcessor)

```kotlin
fun needsValidation(message: AIMessage, sessionId: String): Boolean
```

---

## 5. FLOWS USE CASES

### 5.1 Chat - User Message

```
1. User sends message → AIOrchestrator.sendMessage()
2. emit(UserMessageSent)
3. Transition: IDLE → EXECUTING_ENRICHMENTS
4. Execute enrichments → emit(EnrichmentsExecuted)
5. Transition: → CALLING_AI
6. Call AI → emit(AIResponseReceived)
7. Transition: → PARSING_AI_RESPONSE
8. Parse + store → emit(AIResponseParsed)
9. Transition: → EXECUTING_ACTIONS (exemple)
10. ValidationResolver.needsValidation() → true
11. Persist waiting state in DB
12. Transition: → WAITING_VALIDATION
13. UI observe state.phase, shows dialog
14. User validates → AIOrchestrator.resumeWithValidation(true)
15. emit(ValidationReceived(true))
16. Transition: → EXECUTING_ACTIONS
17. Execute actions → emit(ActionsExecuted(keepControl=false))
18. Transition: → COMPLETED
19. Close session → emit(SchedulerHeartbeat)
20. Scheduler checks next session
```

### 5.2 Automation - Scheduled

```
1. SchedulerWorker (5 min) → emit(SchedulerHeartbeat)
2. Scheduler checks: slot IDLE + queue empty
3. AutomationScheduler.getNextSession() → scheduled automation due
4. emit(SessionActivationRequested)
5. Transition: IDLE → EXECUTING_ENRICHMENTS
6. Copy SEED messages enrichments → execute
7. [Same flow as CHAT from step 4]
8. Actions success + AUTOMATION → keepControl implicit
9. Transition: → CALLING_AI (continue autonomous)
10. AI returns completed=true
11. Transition: → WAITING_COMPLETION_CONFIRMATION
12. System auto-confirms (1s delay) → emit(CompletionConfirmed)
13. Transition: → COMPLETED
14. Close session → emit(SchedulerHeartbeat)
```

### 5.3 Network Retry (AUTOMATION)

```
1. CALLING_AI → network error → emit(NetworkErrorOccurred)
2. Transition: → WAITING_NETWORK_RETRY
3. Persist network waiting state in DB
4. EventProcessor launches coroutine: delay(30s)
5. After delay, if still WAITING_NETWORK_RETRY:
   - emit(NetworkRetryScheduled)
6. Transition: → CALLING_AI
7. Retry call
8. If success → continue
9. If failure → repeat (infini pour AUTOMATION)
10. User can stop: emit(SessionCompleted) → break loop
```

---

## 6. SCHEDULING & INTERRUPTION

### 6.1 Session Priority

**CHAT > MANUAL > SCHEDULED**

### 6.2 Logique Interruption

**Slot libre (IDLE):**
- Activate immédiatement

**Slot occupé:**

| Active | Request | Action |
|--------|---------|--------|
| CHAT | CHAT | Replace immédiat |
| CHAT | MANUAL | Check inactivity → evict si > 2min, sinon enqueue |
| CHAT | SCHEDULED | Skip (attend prochain tick) |
| AUTOMATION | CHAT | Check inactivity → evict si > 5min, sinon enqueue |
| AUTOMATION | MANUAL | Check inactivity → evict si > 2min, sinon enqueue |
| AUTOMATION | SCHEDULED | Skip |

### 6.3 Calcul Inactivité

```kotlin
fun calculateInactivity(state: AIState): Long {
    return when (state.phase) {
        // Waiting user = inactif
        WAITING_VALIDATION,
        WAITING_COMMUNICATION_RESPONSE ->
            now() - state.lastUserInteractionTime

        // Network retry = actif (pas d'éviction)
        WAITING_NETWORK_RETRY -> 0

        // Processing = actif
        CALLING_AI,
        EXECUTING_ACTIONS,
        EXECUTING_DATA_QUERIES -> 0

        // Autres = inactif si pas d'event depuis X
        else -> now() - state.lastEventTime
    }
}
```

### 6.4 Timeouts

- `CHAT_INACTIVITY_TIMEOUT = 300_000L` (5 min)
- `AUTO_INACTIVITY_TIMEOUT = 120_000L` (2 min)

### 6.5 Heartbeat (SchedulerWorker)

Toutes les 5 min:
1. emit(SchedulerHeartbeat)
2. Si slot occupé: watchdog check (timeout selon inactivity)
3. Si slot libre + queue not empty: process queue
4. Si slot libre + queue empty: check scheduled automations

---

## 7. SIMPLIFICATIONS UI

**⚠️ DISCLAIMER:** Les exemples ci-dessous sont **conceptuels** et doivent être adaptés selon la logique UI spécifique existante.

### 7.1 Structure Unifiée

Un seul screen pour CHAT et AUTOMATION:
- Header adaptatif selon state.sessionType
- Messages list (même composant)
- Input conditionnel (CHAT only + phase appropriée)

### 7.2 UI = Pure Présentation

```kotlin
// Exemple conceptuel - adapter selon UI réelle
@Composable
fun AIScreen() {
    val state by AIOrchestrator.currentState.collectAsState()

    Column {
        AIHeader(
            sessionType = state.sessionType,
            phase = state.phase,
            onChatClick = { showChatDialog() },
            onStopClick = { AIOrchestrator.stopSession(CANCELLED) }
        )

        MessagesList(sessionId = state.sessionId)

        if (state.sessionType == CHAT && canInteract(state.phase)) {
            MessageInput(onSend = { ... })
        }
    }
}
```

### 7.3 Dialog Chat (depuis AUTOMATION)

```kotlin
// Exemple conceptuel - adapter selon UI réelle
Dialog {
    "Démarrer chat IA ?"
    - Interrompre automation → AIOrchestrator.requestChatSession()
    - Chat après automation → enqueueChat()
}

// Scheduler décide automatiquement si éviction ou enqueue
// selon inactivity threshold
```

### 7.4 Bénéfices

- UI observe state, pas de logique métier
- State change → recompose automatiquement
- Pas de sync manuel UI/logic
- 1 seul point de décision (scheduler)

---

## 8. PERSISTANCE & RECOVERY

### 8.1 AISessionEntity

```kotlin
data class AISessionEntity(
    val id: String,
    val type: SessionType,
    val automationId: String?,
    val scheduledExecutionTime: Long?,
    val isActive: Boolean,
    val phase: String,  // Phase enum as string
    val endReason: String?,

    // Waiting state persistence
    val waitingStateType: String?,  // "VALIDATION", "COMMUNICATION", null
    val waitingStateData: String?,  // JSON context

    // Retry counters
    val consecutiveFormatErrors: Int,
    val consecutiveActionFailures: Int,
    val consecutiveDataQueries: Int,
    val totalRoundtrips: Int,

    // Timestamps
    val lastEventTime: Long,
    val lastUserInteractionTime: Long,

    // Metadata
    val createdAt: Long,
    val lastActivity: Long
)
```

### 8.2 Recovery après Crash

1. App restart
2. Load active session from DB (isActive=1)
3. Restore AIState from entity
4. Si waiting state: restore context
5. Continue exécution (event processor reprend)

### 8.3 Automation Resume

AutomationScheduler détecte sessions orphelines:
- `endReason = null` → crash
- `endReason = NETWORK_ERROR` → network failure
- `endReason = SUSPENDED` → user pause

Resume automatique via scheduler.

---

## 9. ERROR HANDLING

### 9.1 Side Effect Errors

```kotlin
try {
    val results = executeActions()
    emit(ActionsExecuted(results))
} catch (e: Exception) {
    emit(SystemErrorOccurred(e.message))
    // Transition → COMPLETED avec error reason
}
```

### 9.2 Network Errors

- CHAT: Immediate failure, no retry
- AUTOMATION: Infinite retry (30s delay), resumable

### 9.3 Parse Errors

- Retry avec limite (consecutiveFormatErrors)
- Store error system message
- Renvoyer à IA pour correction

### 9.4 Action Failures

- Retry avec limite (consecutiveActionFailures)
- Store results system message
- Renvoyer à IA avec erreurs

---

## 10. MIGRATION

### 10.1 Stratégie

**Big Bang** - Remplacement complet

### 10.2 Ordre Implémentation

1. **State Machine + Events** (domain pure)
2. **AIStateRepository** (state + DB sync)
3. **AIStateMachine** (transitions)
4. **AIEventProcessor** (event loop)
5. **Composants helpers** (Scheduler, MessageRepository, ValidationResolver)
6. **AIOrchestrator** (API publique)
7. **UI adaptation** (observe state)
8. **Migration DB** (add new fields to AISessionEntity)
9. **Tests** (unit + integration)
10. **Déploiement**

### 10.3 Risques

- Migration complexe (changement architectural majeur)
- Tests exhaustifs requis
- Période d'instabilité possible

### 10.4 Mitigation

- Tests unitaires state machine (transitions pures)
- Tests integration event processor
- Tests E2E flows critiques (chat, automation, network retry)
- Beta test avant production

---

## 11. AVANTAGES vs ARCHITECTURE ACTUELLE

### 11.1 Problèmes Résolus

| Problème Actuel | Solution V2 |
|-----------------|-------------|
| 6 boucles imbriquées | 1 event loop simple |
| Memory vs DB race | Transaction atomique |
| Infinite network retry | Event-based avec delay |
| Recursive tick | Event emission (pas de récursion) |
| State inconsistency | Single source of truth |
| Continuation leak | Persist waiting state en DB |
| Message storage race | Synchrone dans event processor |
| Validation ambiguity | ValidationResolver centralisé |

### 11.2 Bénéfices

- **Testabilité:** State machine pure, events mockables
- **Maintenabilité:** Responsabilités claires, code découplé
- **Scalabilité:** Ajout features = nouveaux events/phases
- **Debuggabilité:** Event log = trace complète
- **Robustesse:** Recovery automatique après crash
- **Simplicité UI:** Observer pattern, pas de logique métier

---

## 12. PROCHAINES ÉTAPES

1. Review document avec équipe
2. Valider design decisions
3. Créer tickets implémentation
4. Commencer par State Machine + Events (domain pure)
5. Tests unitaires au fur et à mesure
6. Migration progressive des composants
7. Tests integration
8. Beta test
9. Production

---

**Document créé le 16 octobre 2025**
**Brainstorming session avec Simon**
**Architecture approuvée pour implémentation**
