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
- `resumeWithResponse(response, note)` : Répondre à communication module, avec la précision ajoutée

### Phase et AIState

**Phase** : 13 phases d'exécution
- `IDLE` : Pas de session active
- `EXECUTING_ENRICHMENTS` : Traitement enrichments user
- `CALLING_AI` : Appel provider IA
- `PARSING_AI_RESPONSE` : Parsing JSON réponse
- `WAITING_VALIDATION` : Attente validation user (CHAT)
- `WAITING_COMMUNICATION_RESPONSE` : Attente réponse communication module (CHAT)
- `WAITING_DATA_CONFIRMATION` : Attente de l'utilisateur sur des données au-delà du seuil de taille (CHAT)
- `EXECUTING_DATA_QUERIES` : Exécution data commands
- `EXECUTING_ACTIONS` : Exécution action commands
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

**WaitingContext** : Contextes d'attente typés, tenus dans l'état seulement, jamais stockés
- `Validation(validationContext)` : Attente validation actions
- `Communication(communicationModule, aiMessageId)` : Attente réponse communication

### AIEvent
Événements déclenchant transitions : `SessionActivationRequested`, `UserMessageSent`, `EnrichmentsExecuted`, `AIResponseReceived`, `AIResponseParsed`, `ValidationReceived`, `DataConfirmationRequested`, `DataConfirmationReceived`, `CommunicationResponseReceived`, `DataQueriesExecuted`, `ActionsExecuted`, `NetworkErrorOccurred`, `ParseErrorOccurred`, `ActionFailureOccurred`, `NetworkRetryScheduled`, `RetryScheduled`, `NetworkAvailable`, `SystemErrorOccurred`, `SessionCompleted`, `SchedulerHeartbeat`.

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
    val phase: String, // Phase actuelle (serialized)
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
    val tokensUsed: String? // JSON tokens breakdown
)
```

### SessionMessage (structure unifiée)
```kotlin
data class SessionMessage(
    val id: String,
    val timestamp: Long,
    val sender: MessageSender, // USER, AI, SYSTEM
    val richContent: RichMessage?, // Messages enrichis utilisateur
    val textContent: String?, // Messages simples (réponses modules)
    val aiMessage: AIMessage?, // Structure IA parsée pour UI
    val aiMessageJson: String?, // JSON original pour historique prompts
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
    val segments: List<MessageSegment> // Text | EnrichmentBlock(type, config) : rien d'autre n'est stocké
)

data class AIMessage(
    val preText: String, // Obligatoire
    val validationRequest: Boolean?, // true = validation requise
    val dataCommands: List<DataCommand>?, // OU actions (exclusif)
    val actionCommands: List<DataCommand>?,
    val postText: String?,
    val keepControl: Boolean?, // true = garde la main après succès actions
    val communicationModule: CommunicationModule?,
    val completed: Boolean? // true = travail terminé (AUTOMATION uniquement)
)
```

**Patterns AIMessage** : Actions (preText + validationRequest? + actionCommands + postText?), Queries (preText + dataCommands), Communication (preText + communicationModule uniquement).

**Fallback parsing** : Si parsing JSON échoue, création AIMessage avec préfixe `"ai_response_invalid_format"` + texte brut dans preText.

### SystemMessage
```kotlin
data class SystemMessage(
    val type: SystemMessageType, // DATA_ADDED, ACTIONS_EXECUTED, LIMIT_REACHED, FORMAT_ERROR, NETWORK_ERROR, SESSION_TIMEOUT
    val commandResults: List<CommandResult>,
    val summary: String,
    val formattedData: String? // JSON résultats (queries uniquement)
)

enum class SystemMessageType {
    DATA_ADDED, // Résultats queries → envoyé au prompt
    ACTIONS_EXECUTED, // Résultats actions → envoyé au prompt
    LIMIT_REACHED, // Limite atteinte → envoyé au prompt
    FORMAT_ERROR, // Erreur de format réponse IA → envoyé au prompt pour correction
    SCHEMA_REQUIRED, // Schémas des entrées qu'une requête ou une écriture attend → envoyé au prompt, commandes non exécutées
    NETWORK_ERROR, // Erreurs réseau/HTTP → filtré du prompt, visible UI (audit + transparence)
    PROVIDER_ERROR, // Provider non configuré/invalide → filtré du prompt, visible UI (audit + transparence)
    SESSION_TIMEOUT // Timeout watchdog session → filtré du prompt, visible UI (audit + transparence)
}
```

**formattedData** : Données JSON complètes formatées pour prompt. Concaténation `PromptCommandResult` avec titres. DATA_ADDED uniquement.

### Commands et PromptData
```kotlin
data class DataCommand(
    val id: String, // Hash déterministe
    val type: String, // TOOL_DATA, CREATE_DATA, etc.
    val params: Map<String, Any?>, // un null demande de vider le champ, à tout niveau
    val isRelative: Boolean = false
)

data class ExecutableCommand(
    val resource: String, // "zones", "tool_data"
    val operation: String, // "get", "batch_create"
    val params: Map<String, Any?>
)

data class PromptData(
    val level1Content: String, // Documentation système (avec limites)
    val level2Content: String, // User data (always_send tools)
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
    val cacheWriteTokens: Int = 0, // Cache write/creation tokens (generic, all providers)
    val cacheReadTokens: Int = 0, // Cache read tokens (generic, all providers)
    val inputTokens: Int = 0 // Uncached input tokens
)
```

## 3. Configuration IA

### AILimitsConfig
Configuration globale des limites de l'IA, stockée dans la catégorie de réglages `ai_limits` et réglable dans l'écran des limites IA.

```kotlin
data class AILimitsConfig(
    val chatMaxAutonomousRoundtrips: Int = 10,
    val automationMaxAutonomousRoundtrips: Int = 20,
    val chatMaxDataChars: Int = 15_000,
    val automationMaxDataChars: Int = 100_000
)
```

Les seuils de taille des données sont décrits avec l'attente de confirmation, section « Validation et Communication ».

**Limite d'appels** :
- **AutonomousRoundtrips** : nombre d'appels à l'IA d'affilée. En CHAT, la limite atteinte rend la main à l'utilisateur (retour à IDLE, la session reste ouverte) ; en AUTOMATION, elle ferme la session (`LIMIT_REACHED`).

**Compteur** : `totalRoundtrips` compte les appels à l'IA depuis la dernière intervention de l'utilisateur — message, réponse à une question ou à une validation, annulation, interruption. Chacune le remet à zéro : la limite borne ce que l'IA fait seule, jamais la longueur d'une conversation. Une AUTOMATION, sans personne pour intervenir, compte donc sa session entière.

**Rationale** : chaque appel est facturé, et rien d'autre n'arrête une IA qui s'appelle en boucle — en CHAT comme en AUTOMATION. Pas de limites séparées sur les erreurs de format ou les échecs d'action : l'IA doit s'auto-corriger, et la limite d'allers-retours suffit comme filet.

**Stockage** : les valeurs par défaut de `AILimitsConfig` sont les seules. Une installation neuve ou une réinitialisation les écrit via `toSettingsJson()` ; `fromSettingsJson()` exige les quatre clés et échoue si l'une manque.

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
- **ValidationResolver** : Résolution hiérarchie validation (app > tool > session > AI request)

### Command Processing Pipeline
```
User: EnrichmentBlock → EnrichmentProcessor → CommandTransformer → CommandExecutor
AI: AIMessage → CommandTransformer → CommandExecutor
```

**Opérations d'un type d'outil** : `TOOL_OPERATION` (`tool_instance_id`, `operation`, `params`) devient `{tooltype}.{operation}` dans `AICommandProcessor`, le type lu depuis l'outil. Les paramètres passent en millisecondes puis sont vérifiés contre le schéma généré de leur déclaration (`ToolOperations.schema`) ; une opération inconnue est refusée avec la liste de celles que le type déclare. L'IA lit ces opérations après le schéma des entrées d'un outil (`CommandExecutor.operationsForModel`) : le prompt L1 ne décrit que la commande, jamais le catalogue.

## 5. Contrôle de session

### Session active exclusive
Une seule session active à la fois (CHAT ou AUTOMATION). `AISessionScheduler` gère queue et activation.

**Règles activation** :
- **CHAT** : Interruption immédiate si autre session active (même AUTOMATION)
- **AUTOMATION MANUAL** : Queue si slot occupé, activation FIFO
- **AUTOMATION SCHEDULED** : Création à la demande par `tick()` si slot libre + queue vide

**Scheduling** : Double heartbeat (1 min coroutine app-open + 15 min WorkManager app-closed) + événementiel (CRUD automations, fin session).

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
- Périodique : AIOrchestrator coroutine (1 min, app-open) + WorkManager (15 min, app-closed)
- Événementiel : CRUD automations (create/update/enable/disable)
- Fin session : scheduler déclenché après libération slot

### Architecture pull-based
```kotlin
tick() {
  if (slotOccupé) return
  if (queueNotEmpty) processQueue()
  else {
    nextSession = AutomationScheduler.getNextSession() // Calcul dynamique
    if (nextSession) executeAutomation(id)
  }
}
```

**AutomationScheduler** : Helper pur de calcul. Trouve sessions incomplètes (endReason null/NETWORK_ERROR/SUSPENDED) OU prochaine execution depuis historique.

### Exécutions manquées

Quand l'app n'a pas tourné à l'heure prévue, les réglages `catch_up` d'une automation programmée (déclarés dans `AutomationSettings.catchUpNodes`, présents avec la planification et seulement avec elle ; `AutomationService` refuse le reste) :

- **`limit`** : `limited` ou `unlimited`, choisi explicitement, sans valeur par défaut. `limited` apporte **`window`**, une DURÉE en millisecondes : au-delà de ce retard, l'occurrence est sautée — une ligne de log, pas de session (l'historique est fait de sessions ; une session vide « sautée » serait une forme de plus à gérer partout).
- **`dismiss_older_instances`** : parmi les occurrences dues, ne lancer que la plus récente. Ne se déduit pas de la fenêtre.

Les réglages d'une automation (nom, fournisseur, groupe, activation, planification, rattrapage) sont déclarés dans `AutomationSettings` ; `AutomationService` vérifie toute écriture contre le schéma généré, et `Automation.fromResult` est le seul lecteur de ses résultats.

La recherche de la prochaine occurrence démarre au plus tôt à `maintenant − fenêtre` (`AutomationScheduler.searchStart`). La plus récente due se trouve par dichotomie sur le départ de la recherche (`lastDueOccurrence`) : le calculateur ne répond que « la première après cet instant », et cette réponse ne décroît jamais quand l'instant grandit.

**Résolution temporelle** : une session AUTOMATION résout ses périodes relatives et le marqueur `NOW` sur son `scheduledExecutionTime`, pas sur l'horloge — sinon toutes les exécutions de rattrapage lisent le même jour. L'instant est choisi par `AIEventProcessor.periodReference()` et traverse `UserCommandProcessor`/`AICommandProcessor` jusqu'à `CommandTransformer`. `resolveRelativePeriod` l'exige, sans valeur par défaut. Le prompt porte les deux dates (cf. §8) : les données lues sont ancrées sur la date prévue, ce que l'IA fait reste au présent.

**Données antérieures** : une automation programmée enregistrée avant ces réglages est lue « sans limite, la plus récente seulement » (`LegacyCatchUp`) — règle appliquée par la migration 22→23 et par l'import d'une sauvegarde qui ne porte pas le champ.

### Spécificités AUTOMATION vs CHAT

**Flag completed** : l'IA signale la fin avec `completed: true`. L'app ne la prend pas au mot : elle repasse en `PREPARING_CONTINUATION` avec `COMPLETION_CONFIRMATION_REQUIRED` et renvoie l'IA au travail une fois. Si la réponse suivante redit `completed: true` sans porter de commande, la session se termine avec `endReason=COMPLETED`. Toute réponse portant une commande rabaisse le drapeau : l'IA qui continue de travailler recommence à zéro.

**Continuation automatique** : Après succès actions, AUTOMATION continue automatiquement (pas de keepControl requis).

**Réseau** : Retry infini avec delay 30s si offline. Phase `WAITING_NETWORK_RETRY` (watchdog ne timeout pas).

**Communication modules** : Interdits pour AUTOMATION (validationRequest, communicationModule ignorés).

### Arrêt AUTOMATION
**UI boutons** :
- **STOP** : `stopActiveSession()` → `endReason=CANCELLED` (ne reprendra pas) ; l'appel IA en cours est coupé, sans attendre sa réponse

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

Event SchemaRequired (écritures sur un outil dont l'IA n'a pas reçu le schéma des entrées dans la session) :
  → émis par parseAIResponse, avant la validation : rien n'est exécuté ni soumis à l'utilisateur
  → message SCHEMA_REQUIRED avec les schémas manquants, transition CALLING_AI

Event DataConfirmationRequested (CHAT, données au-delà du seuil):
  → transition WAITING_DATA_CONFIRMATION

Event DataConfirmationReceived:
  → envoyées ou refusées, transition CALLING_AI, le compteur d'appels repart de 1

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
- `ValidationResolver` analyse hiérarchie (app > tool > session > AI request)
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

**Seuil de taille des données** :
- Les données récupérées pour l'IA, par les pointeurs de l'utilisateur ou par ses propres requêtes, sont mesurées en caractères du texte qu'elle recevrait, contre `chat_max_data_chars` ou `automation_max_data_chars` (`ai_limits`, réglables dans l'écran des limites IA).
- CHAT au-delà : le message de données est stocké hors du prompt (`DATA_AWAITING_CONFIRMATION`), phase `WAITING_DATA_CONFIRMATION`. Le contexte d'attente est relu depuis ce message, donc l'attente survit à un redémarrage. « Envoyer » le rend `DATA_ADDED` et l'intègre au prompt ; « Refuser » le rend `DATA_REFUSED`, sans données, avec un résumé qui demande à l'IA de resserrer.
- AUTOMATION au-delà : les données ne sont pas stockées ; un message `DATA_REFUSED` part à l'IA avec la taille, le seuil et les requêtes concernées, et reste visible dans l'historique d'exécution.
- Les données des outils « toujours envoyer » (niveau 2) ne sont pas mesurées : c'est un choix de configuration de l'utilisateur.

## 8. Gestion réseau et erreurs

**NetworkUtils** : `isNetworkAvailable(context)` pour vérification connectivité (core/utils).

**Timeout HTTP** (providers) : connexion 15 s, lecture 10 min, écriture 2 min. Sans streaming, rien n'arrive avant la fin de la génération : le délai de lecture couvre une réponse longue entière.

**Appel en cours** : `callAI` tourne dans sa propre tâche, hors de la boucle qui traite les changements d'état. `SessionCompleted` (dont STOP) et `AIRoundInterrupted` (Interrompre, CHAT) l'annulent avant la transition, ce qui ferme la connexion HTTP (`OkHttpClient.awaitReply()`) : rien n'est gardé de la réponse. Interrompre passe par `INTERRUPTED`, le temps d'écrire le message d'interruption, puis revient à `IDLE`.

**Appel parti sans réponse** : coupé après l'envoi de la requête (Stop, Interrompre) ou perdu (`LOST`), l'appel a peut-être été facturé et son usage est inconnu. Son message système le dit et porte `usage_unknown`. Un appel coupé avant l'envoi n'a rien coûté et n'est pas marqué ; `RequestSent`, posé dans le contexte de la coroutine autour de l'appel, fait la différence.

**Coût** : chaque message IA garde ses tokens, le modèle de la config et les quatre prix par token de son appel, tels qu'au moment de l'appel (`CallPricing`) ; au-delà du seuil d'un palier LiteLLM (`…_above_<N>k_tokens`), ce sont ceux du palier. Rien n'est stocké sur la session : `SessionCost.of()` additionne ses messages à la lecture (`AIDao.getCallUsages`). Un appel est de coût inconnu s'il porte `usage_unknown`, ou si une catégorie où il a des tokens n'a pas de prix (`null`, jamais 0) ; le total ne compte que le connu et s'affiche alors en « ≥ ». Les prix viennent de la liste LiteLLM, gardée sur le téléphone (`ModelPriceManager`) : relue au premier besoin, retéléchargée en arrière-plan après un jour, et tout de suite quand un modèle manque d'une copie de plus d'une heure.

**Nature de l'échec** : `AIResponse.failure` (`AIFailure`), posé par le provider là où l'échec se produit — jamais déduit du texte du message. `NETWORK` (rien n'a atteint le provider) = retry ; `LOST` (requête partie en entier, réponse jamais reçue : connexion coupée ou délai de lecture dépassé) = arrêt comme un refus, car le provider l'a peut-être facturée — `OkHttpClient.awaitReply()` fait la différence, et marque le corps « one-shot » pour qu'OkHttp ne renvoie jamais de lui-même une requête déjà partie ; `REFUSED` (429, 529, crédit épuisé) et `CONFIG` (clé, modèle, requête) = `ProviderErrorOccurred`, la session s'arrête. Un provider ne formule donc plus ses messages d'erreur pour tomber du bon côté d'un test de chaîne.

**AUTOMATION** :
- Check réseau avant appel → offline = phase `WAITING_NETWORK_RETRY`
- Delay 30s + retry infini, réservé aux échecs `NETWORK` : tant que rien ne part, rien n'est facturé
- Watchdog ne timeout pas pendant retry réseau
- Retry jusqu'à réseau disponible OU user STOP

**CHAT** :
- Check réseau avant appel → offline = toast + `NETWORK_ERROR` event
- Pas de retry automatique
- Session reste active

## 9. Enrichissements

### Types d'enrichissements
- ** POINTER** - Référencer données (zones ou instances)
- ** USE** - Utiliser données d'outils (config + schemas + data + stats)
- ** CREATE** - Créer éléments (schemas pour tooltype)
- ** MODIFY_CONFIG** - Modifier config outils (schema + config actuelle)

### Texte d'un bloc
Un bloc ne stocke que son type et sa config ; son texte s'écrit à chaque lecture du message (`EnrichmentText`), qui relit en deux lectures toutes les zones et tous les outils. Un pointeur nomme donc sa cible comme elle s'appelle aujourd'hui, et une cible supprimée se lit « supprimé » ; un échec de lecture s'affiche comme tel, jamais comme une suppression.
- **À l'écran** (`display`, via `rememberDisplayText`) : « Outil : Poids (Suivi), données, période filtrée ».
- **Pour l'IA** (`prompt`, dans `PromptManager` à chaque envoi) : le même texte avec l'id de la cible, et pour une mention d'entrées restreintes la requête qui les lit. Un message utilisateur part à l'IA comme texte ; une automation relit donc les noms à chaque exécution.

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
**Transformations** : SCHEMA → schemas.get, TOOL_CONFIG → tools.get, TOOL_DATA → tool_data.get (dates, périodes relatives et durées de ses `filters` mises en forme stockée d'après le type du champ, `FilterValues`), ZONE_CONFIG → zones.get, ZONES → zones.list, TOOL_INSTANCES → tools.list, ICONS → icons.overview (sans paramètre) ou icons.search (`categories` et/ou `query`).

### User vs AI Commands
**User** : Source EnrichmentBlocks, types POINTER/USE/CREATE/MODIFY_CONFIG uniquement, but données contextuelles, jamais d'actions.
**AI** : Source AIMessage.dataCommands + actionCommands, types queries + actions réelles, but demander données + exécuter actions.

## 10. Architecture prompts

### 2 niveaux de contexte
**Level 1: DOC** - Généré par PromptChunks avec degrés d'importance configurables. Inclut rôle IA, documentation API, **limites IA dynamiques** selon SessionType, la légende de la notation des schémas, la définition d'un champ et les schémas de la réponse de l'IA et d'une zone, écrits dans cette notation (`SchemaNotation`, voir `docs/DATA.md`). Pour AUTOMATION : documentation flag `completed: true` obligatoire + continuation automatique après succès actions.
**Level 2: USER DATA** - Données tool instances avec `always_send: true`.

**APP_STATE** : Zones et tool instances disponibles via command dédiée (à la demande).
**Enrichments** : Stockés comme SessionMessage sender=SYSTEM, inclus dans l'historique.
**RichComposer UI** : Architecture multi-blocs (TextBlock = texte + enrichments), navigation focus-based avec highlight visuel.

### Le L1 est un contrat, à retester à chaque modification

`ai_prompt_chunks.xml` est la seule description que l'IA reçoit de l'API de commandes. Rien ne le compile ni ne le teste : une divergence avec le code ne produit aucune erreur, juste un paramètre ignoré en silence et un résultat faux côté IA.

Règle : **chaque exemple de requête présent dans le L1 doit être exécutable tel quel et produire ce qu'il annonce.** À toute modification du L1 ou du pipeline qui le sert (`AICommandProcessor`, `CommandTransformer`, `CommandExecutor.formatResultData`), rejouer les exemples concernés dans une session CHAT réelle.

Le filet a deux mailles, et elles ne prennent pas la même chose. `scripts/check_prompt_examples.py`, lancé à chaque `./run test`, confronte les exemples JSON du prompt au code, et `FieldTypeSchemasTest` et `CommunicationModulesTest` valident ses définitions de champ et ses modules contre les schémas générés (le prompt est une entrée de la tâche de test, qu'il relance) : ils disent que le prompt ne promet rien que le code ne tienne. Le rejeu en session dit ce que l'IA fait du prompt — s'il l'amène à demander un schéma avant d'agir, à poser une période où le code l'attend, à répondre dans la langue de l'utilisateur. La procédure et sa grille de lecture sont dans `docs/ai-prompt-replay.md`, à tenir à jour quand le L1 bouge.

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
    fun getProviderId(): String
    fun getDisplayName(): String
    fun getConfigSettings(context: Context): List<SettingNode>
    fun getConfigHelp(context: Context): String
    suspend fun listModels(apiKey: String): ProviderModels
    suspend fun query(promptData: PromptData, config: String): AIResponse
}
```

**Config** : déclarée avec les champs (`getConfigSettings`) ; la clé d'API est un réglage `secret`, saisi masqué. Le schéma (`AIProviderSettings.schema`), contre lequel `AIProviderConfigService` vérifie toute écriture, et l'écran (`AIProviderConfigScreen`) en sont générés ; le modèle se choisit parmi ceux que liste `listModels` pour la clé saisie. Le code lit une config par `AIProviderSettings.read`.

**Responsabilités provider** : Parser config, transformer promptData (structure spécifique API), fusionner messages (contraintes alternance si applicable), appeler API HTTP, parser réponse (content, tokens, erreurs).

### Pattern extensions
**Exemple ClaudeExtensions.kt** :
```kotlin
internal fun PromptData.toClaudeJson(config: JSONObject, datetimeText: String): JsonObject
internal fun JsonElement.toClaudeAIResponse(): AIResponse
```

**Avantages** : Testable séparément, concis, logique complexe isolée. Le message daté (horloge et strings) est construit par l'appelant et passé en texte, ce qui garde ces fonctions pures : `ClaudeExtensionsTest` et `OpenAIExtensionsTest` les couvrent sans réseau.

### Fusion messages (pattern général)
Le provider fusionne USER/SYSTEM consécutifs pour respecter contraintes API.

**Exemple** : DB (1.USER "Question" → 2.SYSTEM enrichments → 3.AI réponse → 4.SYSTEM queries → 5.USER "Autre") transformé en API (1.USER ["Question", "enrichments"] → 2.ASSISTANT réponse → 3.USER ["queries", "Autre"]). Un message vide est omis, et un message IA vide ne coupe pas le tour utilisateur qui l'entoure.

### ClaudeProvider - Cache control (spécifique)
**4 breakpoints**, le maximum de l'API : un par bloc système (L1, L2, L3), et le dernier bloc du dernier message de l'historique. Le message daté vient après, hors breakpoint, puisqu'il change à chaque appel.
**Automatic prefix checking** : Messages précédents (sans cache_control) automatiquement cachés (~20 blocs avant le dernier breakpoint).

**Structure** : system array avec L1/L2/L3 + cache_control, messages array avec fusion USER/SYSTEM. Le dernier message est forcé en format array pour supporter cache_control sur son dernier bloc.

### DeepSeek - Endpoint compatible Anthropic
DeepSeek passe par `ClaudeProviderCore` sur son endpoint `/anthropic` (`MessagesApi.DEEPSEEK` : adresse, liste des modèles, niveaux d'effort). Particularités :
- Effort (`output_config.effort`) obligatoire dans la config, parmi `low`/`high`/`max` : le raisonnement est actif par défaut chez DeepSeek, les autres valeurs n'en sont que des alias.
- Substitution de modèle : un nom `claude-*` est servi par `deepseek-flash` sans erreur, et le champ `model` de la réponse le révèle. Toute réponse dont le `model` diffère du modèle demandé est rejetée (erreur définitive). Mesuré le 2026-09-19 ; un ID inconnu, lui, reçoit une erreur explicite.
- Pas de suffixe `[1m]` sur l'ID du modèle. L'API l'accepte et répond avec le modèle sans suffixe (mesuré le 2026-09-19) ; son effet sur la taille du contexte n'est pas mesuré. La doc Claude Code le décrit comme un réglage de la fenêtre de contexte de Claude Code lui-même.
- `cache_control` ignoré : le cache de contexte de DeepSeek est automatique. Il reprend d'un appel à l'autre tout le début identique, conversation comprise, message daté final et raisonnement retiré n'y changeant rien (mesuré le 2026-09-25) ; ce qui change avant l'historique, comme un L3 reconstruit, l'arrête là.
- La réponse commence par un bloc `thinking` : le parsing lit le bloc `text`.

### Configuration
Configurations gérées par `AIProviderConfigService`, providers découverts via `AIProviderRegistry`, `AIClient` utilise coordinator (pas d'accès DB direct).

## 12. Communication modules

### Structure
Un module est une liste de champs déclarée par l'IA : `{"fields": [...]}`, chaque champ une définition de champ comme dans `extra_fields`, que l'IA nomme elle-même (la réponse revient sous ce nom) et qui dit s'il est `required`. Sans champ, le module demande une confirmation ; la question est le `pre_text`.

**Validation** : `CommunicationModules` déclare le module avec les champs (`declarationNodes`, schéma `communication_module` généré) ; `check` le confronte à ce schéma puis à `FieldConfigValidator` (clé en snake_case, clés distinctes, réglages du type). Un module refusé devient un FORMAT_ERROR qui dit pourquoi ; rien n'est ignoré en silence. `CommunicationModule` garde la déclaration telle qu'écrite, et ses `fields` ne se lisent qu'une fois le module vérifié.

**Réponse** : la carte dessine les champs avec `SettingsForm` ; Confirmer n'est possible que quand la réponse tient au schéma généré depuis les champs (`checkAnswer`). La réponse part à l'IA en objet de valeurs, dates et durées en ISO 8601 (`answerForModel`), une confirmation en `confirmed` ; le fil la réaffiche par les composants d'affichage des champs (`CommunicationAnswer`).

**Deux autres sorties**, sous le formulaire : « Ajouter une précision » ouvre un texte libre, envoyé après la réponse en message système (`ai_module_note_prefix`) ; « Répondre par un message » débloque le composeur, le formulaire restant répondable. C'est l'envoi du message qui remplace le module : `sendMessage` écrit d'abord le message système `ai_module_replaced_by_message`, et `UserMessageSent` quitte l'attente comme depuis IDLE.

### Flow de réponse utilisateur
```kotlin
// Pattern UI
val aiState by AIOrchestrator.currentState.collectAsState()

// Affichage inline dans ChatMessageBubble (dernier message AI uniquement)
if (isLastAIMessage && aiState.waitingContext is WaitingContext.Communication) {
    val ctx = aiState.waitingContext as WaitingContext.Communication
    CommunicationModuleCard(
        module = ctx.communicationModule,
        onResponse = { response, note ->
            AIOrchestrator.resumeWithResponse(response, note)
        },
        onCancel = { AIOrchestrator.cancelCommunication() },
        freeReply = freeReply,
        onFreeReply = { freeReply = true }
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
**Hiérarchie OR** : app > tool > session > AI request. Si UN niveau true → validation requise. `validationRequest` = Boolean dans AIMessage.

**Flow** : ValidationResolver analyse actions → génère ValidationContext (actions verbalisées + raisons + warnings config) → `WaitingContext.Validation` créé → UI affiche inline → user valide/refuse → `resumeWithValidation(validated)`.

**Messages fallback** : COMMUNICATION_CANCELLED / VALIDATION_CANCELLED créés AVANT suspension (trace si fermeture app/navigation). Supprimés si user répond/valide effectivement.

**Persistance** : aucune. Le contexte se déduit du dernier message de l'IA et des règles de validation ; une session restaurée dans une phase d'attente le retrouve parce que `AIEventProcessor`, en collectant l'état restauré, le reconstruit comme à l'entrée dans cette phase.

## 13. SystemMessages

### Génération et stockage
**Générés par** : CommandExecutor après chaque série de commandes. **Stockés comme** : SessionMessage sender=SYSTEM. **Point unique** : CommandExecutor seul responsable (User et AI).

### Types et placement
**Actions dans le fil** : sous chaque commande d'écriture réussie d'un message ACTIONS_EXECUTED, le fil montre les entrées écrites champ par champ, comme la demande de validation (`ProposedEntries.read`, cartes `ProposedEntryItem`). Rien n'est stocké pour ça : elles se lisent dans les commandes du message de l'IA qui précède, un résultat d'action par commande, dans l'ordre, avec les champs de l'outil tel qu'il est aujourd'hui.
**Enrichments user** : Générés après exécution enrichments, stockés après message USER, type DATA_ADDED avec formattedData.
**AI queries** : Générés après exécution dataCommands IA, stockés après réponse AI, type DATA_ADDED avec formattedData.
**AI actions** : Générés après exécution actionCommands IA, stockés après réponse AI, type ACTIONS_EXECUTED sans formattedData.
**Limites** : Générés quand limite atteinte, type LIMIT_REACHED avec summary, pas de renvoie auto (attend message user).
**Format errors** : Générés quand la réponse de l'IA ne se lit pas ou enfreint une règle du format (module de communication compris), type FORMAT_ERROR avec détails erreurs, renvoie auto à l'IA pour correction.
**Erreurs système** : Générés pour erreurs réseau (NETWORK_ERROR), provider (PROVIDER_ERROR) et timeout watchdog (SESSION_TIMEOUT). **TOUJOURS visibles dans l'UI** pour transparence utilisateur. Filtrés du prompt IA (excludeFromPrompt=true, audit uniquement).

### Format dans prompts
Provider décide du format d'inclusion. Généralement fusion avec messages USER consécutifs (formattedData ajouté comme content block).

**Filtrage** : NETWORK_ERROR, PROVIDER_ERROR, SESSION_TIMEOUT et messages avec `excludeFromPrompt=true` exclus du contexte IA (visibles UI + audit, pas dans prompt).

---

*L'architecture IA V2 event-driven garantit cohérence state, recovery automatique, autonomie contrôlée et extensibilité.*
