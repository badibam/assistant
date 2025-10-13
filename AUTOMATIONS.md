# Automations - Documentation Technique

Guide pour l'implémentation du système d'automations IA dans l'architecture Assistant.

## Concepts Fondamentaux

### Automation vs Exécution

**Automation** : Configuration/template définissant quand et comment une IA doit agir automatiquement.

**Exécution** : Session IA créée chaque fois qu'une automation se déclenche.

### Session Types

L'architecture utilise 3 types de sessions IA :

- **CHAT** : Conversation interactive temps réel avec l'utilisateur
- **AUTOMATION** : Session d'exécution d'une automation (autonome)
- **SEED** : Template contenant le(s) message(s) de démarrage d'une automation

**Relation** : Automation → référence session SEED → crée sessions AUTOMATION à chaque exécution

### Une automation = Un message unique

Contrainte simplificatrice : Chaque automation contient exactement 1 message utilisateur (avec enrichments possibles).

Ce message est stocké dans une session de type SEED et copié dans chaque nouvelle session d'exécution.

## Architecture des Données

### Entité Automation

```kotlin
data class Automation(
    val id: String,
    val name: String,
    val icon: String,                       // Icône choisie par l'utilisateur
    val zoneId: String,                     // Automation attachée à une zone
    val seedSessionId: String,              // Pointe vers session SEED
    val schedule: ScheduleConfig?,          // null = pas de déclenchement temporel
    val triggerIds: List<String>,           // vide = pas de déclenchement événementiel
    val dismissOlderInstances: Boolean = false,  // Skip instances plus anciennes si plus récente existe
    val providerId: String,                 // Provider IA à utiliser
    val isEnabled: Boolean,
    val createdAt: Long,
    val lastExecutionId: String?,           // Dernière session d'exécution
    val executionHistory: List<String>      // IDs des sessions d'exécution
)
```

**Logique de déclenchement (déduite de la configuration) :**
- `schedule == null && triggerIds.isEmpty()` → MANUAL uniquement
- `schedule != null && triggerIds.isEmpty()` → SCHEDULE uniquement
- `schedule == null && triggerIds.isNotEmpty()` → TRIGGER uniquement (OR entre triggers)
- `schedule != null && triggerIds.isNotEmpty()` → HYBRID (schedule OU n'importe quel trigger)

### Session SEED

Session IA de type SEED contenant uniquement le message utilisateur initial :

```kotlin
AISession(
    type = SessionType.SEED,
    messages = [
        SessionMessage(
            sender = MessageSender.USER,
            richContent = RichMessage(...),  // Message + enrichments
            // Pas d'autres messages (pas de réponse IA)
        )
    ]
)
```

Cette session n'est jamais "exécutée", elle sert uniquement de template.

### Session d'exécution

À chaque déclenchement, création d'une nouvelle session AUTOMATION :

```kotlin
AISession(
    type = SessionType.AUTOMATION,
    automationId = "automation-xyz",
    scheduledExecutionTime = 1234567890,  // Timestamp de déclenchement prévu
    messages = [
        // Messages copiés depuis session SEED (uniquement USER)
    ]
)
```

Les sessions d'exécution sont **persistées en base** (pas éphémères). Possibilité d'archivage ultérieur.

### SessionState et tracking réseau

Ajout de champs pour différencier inactivité réelle vs problèmes réseau :

```kotlin
enum class SessionState {
    IDLE,                   // En attente
    PROCESSING,             // En train de traiter
    WAITING_NETWORK,        // Bloqué sur réseau
    WAITING_USER_RESPONSE,  // Attente communication module (ne devrait pas arriver pour AUTOMATION)
    WAITING_VALIDATION      // Attente validation (ne devrait pas arriver pour AUTOMATION)
}

enum class SessionEndReason {
    COMPLETED,              // IA a terminé (flag completed: true)
    LIMIT_REACHED,          // Limites de boucles dépassées
    INACTIVITY_TIMEOUT,     // Inactivité réelle > 10 min
    CHAT_EVICTION,          // Évincée par demande CHAT
    DISMISSED,              // Annulée par paramètre dismiss
    USER_CANCELLED          // Annulée manuellement par utilisateur
}

data class AISession(
    // ... champs existants
    val state: SessionState = SessionState.IDLE,
    val lastNetworkErrorTime: Long? = null,
    val endReason: SessionEndReason? = null
)
```

## Configuration de Schedule

### ScheduleConfig

```kotlin
data class ScheduleConfig(
    val pattern: SchedulePattern,
    val timezone: String = "Europe/Paris",
    val enabled: Boolean = true,
    val startDate: Long? = null,        // Commence à partir de cette date
    val endDate: Long? = null,          // Termine à cette date (null = indéfini)
    val nextExecutionTime: Long? = null // Calculé par le système
)
```

### SchedulePattern - 6 types

```kotlin
sealed class SchedulePattern {
    // Type 1: Quotidien - plusieurs horaires
    // Ex: "Tous les jours à 9h, 14h et 18h"
    data class DailyMultiple(
        val times: List<String>  // ["09:00", "14:00", "18:00"]
    ) : SchedulePattern()

    // Type 2: Hebdomadaire - jours spécifiques + horaire commun
    // Ex: "Lundi, mercredi, vendredi à 9h"
    data class WeeklySimple(
        val daysOfWeek: List<Int>,  // 1=lundi, 7=dimanche
        val time: String             // "09:00"
    ) : SchedulePattern()

    // Type 3: Mensuel - certains mois + jour fixe
    // Ex: "Le 15 de janvier, mars et juin à 10h"
    data class MonthlyRecurrent(
        val months: List<Int>,       // 1-12
        val dayOfMonth: Int,         // 1-31
        val time: String
    ) : SchedulePattern()

    // Type 4: Hebdomadaire - moments personnalisés
    // Ex: "Lundi 9h, mercredi 14h, vendredi 17h"
    data class WeeklyCustom(
        val moments: List<WeekMoment>
    ) : SchedulePattern()

    // Type 5: Annuel - dates récurrentes chaque année
    // Ex: "Tous les 1er janvier et 25 décembre à 8h"
    data class YearlyRecurrent(
        val dates: List<YearlyDate>
    ) : SchedulePattern()

    // Type 6: Dates spécifiques - one-shot (pas de répétition)
    // Ex: "Le 15 mars 2025 à 14h30 et le 20 avril 2025 à 10h"
    data class SpecificDates(
        val timestamps: List<Long>
    ) : SchedulePattern()
}

data class WeekMoment(val dayOfWeek: Int, val time: String)
data class YearlyDate(val month: Int, val day: Int, val time: String)
```

**Note** : Les intervalles réguliers (toutes les X minutes/heures/jours) ne sont PAS inclus car aucun besoin réel identifié.

### Composant UI réutilisable

Un dialogue générique `ScheduleConfigEditor` sera créé pour configurer les schedules, réutilisable partout dans l'app (automations, alertes, rappels, etc.).

## Triggers (Préparation)

Architecture préparée pour les triggers événementiels (implémentation ultérieure).

### Structure Trigger (implémenter en stub uniquement, détails à précicer/implémenter ultérieurement)

```kotlin
data class Trigger(
    val id: String,
    val name: String,
    val zoneId: String?,                    // null = trigger global, sinon trigger de zone
    val conditions: List<TriggerCondition>,
    val operator: LogicalOperator = LogicalOperator.AND,  // AND/OR entre conditions
    val isEnabled: Boolean,
    val createdAt: Long
)

enum class LogicalOperator { AND, OR }

sealed class TriggerCondition {
    data class OnDataCreated(
        val toolInstanceId: String
    ) : TriggerCondition()

    data class OnDataThreshold(
        val toolInstanceId: String,
        val fieldPath: String,
        val operator: ComparisonOperator,
        val threshold: Any
    ) : TriggerCondition()

    data class OnDataChange(
        val toolInstanceId: String,
        val fieldPath: String? = null
    ) : TriggerCondition()

    data class OnPeriodEnd(
        val periodType: PeriodType
    ) : TriggerCondition()

    // Extensions futures possibles
    data class OnToolConfigChanged(val toolInstanceId: String) : TriggerCondition()
}

enum class ComparisonOperator { GT, LT, EQ, NE, GTE, LTE, CONTAINS }
```

### Réutilisabilité des triggers 

Les triggers sont des **entités de premier ordre** :
- Table séparée avec `TriggerService`
- Un trigger peut être utilisé par plusieurs automations
- Composition : Liste d'IDs dans `Automation.triggerIds` avec OR implicite entre eux

**Exemple :**
```kotlin
// Trigger 1: "Poids > 80kg"
Trigger(id = "t1", conditions = [OnDataThreshold(...)], operator = AND)

// Trigger 2: "Fin de journée"
Trigger(id = "t2", conditions = [OnPeriodEnd(DAY)], operator = AND)

// Automation: se déclenche si (Trigger 1 OU Trigger 2)
Automation(triggerIds = ["t1", "t2"])
```

## Exécution des Automations

### Scheduler via WorkManager

```kotlin
// SchedulerWorker.kt - Worker Android
class SchedulerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Récupère toutes les automations enabled
        val automations = automationService.getAllEnabled()

        val now = System.currentTimeMillis()

        // Filtre celles qui doivent se déclencher
        val pendingAutomations = automations
            .filter { shouldTrigger(it, now) }
            .sortedBy { it.schedule?.nextExecutionTime ?: Long.MAX_VALUE }  // FIFO par horaire prévu

        // Lance chaque automation (AIOrchestrator gère la queue)
        pendingAutomations.forEach { automation ->
            AIOrchestrator.executeAutomation(automation.id)
        }

        return Result.success()
    }
}

// Enregistrement dans MainActivity.onCreate()
val workRequest = PeriodicWorkRequestBuilder<SchedulerWorker>(15, TimeUnit.MINUTES).build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "automation_scheduler",
    ExistingPeriodicWorkPolicy.KEEP,
    workRequest
)
```

**Fréquence** : Vérification toutes les 15 minutes (minimum Android pour PeriodicWork).

### Flow d'exécution

```kotlin
// AIOrchestrator.executeAutomation(automationId)

suspend fun executeAutomation(automationId: String) {
    // 1. Charger l'automation
    val automation = automationService.get(automationId)

    // 2. Charger la session SEED
    val seedSession = sessionService.get(automation.seedSessionId)

    // 3. Créer nouvelle session AUTOMATION
    val executionSession = AISession(
        id = generateId(),
        type = SessionType.AUTOMATION,
        automationId = automationId,
        scheduledExecutionTime = System.currentTimeMillis(),
        providerId = automation.providerId,
        messages = seedSession.messages
            .filter { it.sender == MessageSender.USER }  // Copier uniquement messages USER
            .map { it.copy(id = generateId(), timestamp = System.currentTimeMillis()) }
    )

    // 4. Sauvegarder la session
    sessionService.create(executionSession)

    // 5. Request session control (gestion queue FIFO)
    requestSessionControl(executionSession.id)
}
```

### Ordre d'exécution strict

**Règle** : 1 seule session active à la fois, les autres en queue FIFO.

Si plusieurs automations doivent se déclencher :
- Tri par `nextExecutionTime` (horaire prévu)
- Exécution séquentielle stricte
- Pas de parallélisation

**Exemple :**
```
9h00 : Automation A doit se déclencher
9h15 : Automation B doit se déclencher (pendant que A tourne encore)
→ B va en queue position 1
→ B commence uniquement quand A termine
```

### Paramètre dismiss - Skip instances plus anciennes

**Comportement** : Au moment de prendre la prochaine automation en queue, vérifier si une instance plus récente de la même automation existe plus loin. Si oui, skip l'actuelle.

```kotlin
// Dans AIOrchestrator.processNextInQueue()

private suspend fun processNextInQueue() {
    if (sessionQueue.isEmpty()) return

    val nextSession = sessionQueue.first()

    // Si automation avec dismiss activé
    if (nextSession.type == SessionType.AUTOMATION && nextSession.automationId != null) {
        val automation = automationService.get(nextSession.automationId!!)

        if (automation.dismissOlderInstances) {
            // Chercher instances plus récentes dans la queue
            val hasNewerInstance = sessionQueue
                .drop(1)  // Ignorer la première (celle qu'on regarde)
                .any { it.automationId == nextSession.automationId }

            if (hasNewerInstance) {
                // Skip cette instance
                sessionQueue.removeFirst()

                // Marquer comme dismissed
                storeSystemMessage(nextSession.id, SystemMessageType.DISMISSED, "...")
                sessionService.updateEndReason(nextSession.id, SessionEndReason.DISMISSED)

                // Passer à la suivante (récursif)
                processNextInQueue()
                return
            }
        }
    }

    // Pas de dismiss ou pas d'instance plus récente → exécuter
    sessionQueue.removeFirst()
    activateSession(nextSession.id)
    executeAIRound(RoundReason.AUTOMATION_START)
}
```

**Use case typique** : Rapport périodique qui se déclenche plusieurs fois avant d'être traité. Seule la plus récente (avec les données les plus fraîches) est exécutée.

## Gestion Réseau et Erreurs

### Tentatives infinies si pas de réseau

Pour les automations, si pas de réseau disponible :
- Tentatives infinies avec pause de 30 secondes entre chaque
- Pas de décompte du temps (pas de watchdog temporel)
- L'automation reste en position active jusqu'à ce que le réseau revienne

```kotlin
// Dans executeAIRound() pour AUTOMATION

while (totalRoundtrips < limits.maxAutonomousRoundtrips) {

    // Gestion réseau : tentatives infinies
    if (!NetworkUtils.isNetworkAvailable(context)) {
        sessionService.updateSessionState(sessionId, SessionState.WAITING_NETWORK)
        sessionService.updateLastNetworkError(sessionId, System.currentTimeMillis())
        delay(30_000)  // Pause 30s
        continue  // Boucle jusqu'à réseau ou éviction CHAT
    }

    // Reset state si réseau revenu
    sessionService.updateSessionState(sessionId, SessionState.PROCESSING)

    // Boucle normale...
}
```

### Distinction inactivité réelle vs attente réseau

```kotlin
fun isInactiveForRealReason(session: AISession, threshold: Duration): Boolean {
    val lastCommandTime = session.messages
        .filter { it.systemMessage?.type in listOf(DATA_ADDED, ACTIONS_EXECUTED) }
        .maxOfOrNull { it.timestamp } ?: session.createdAt

    val inactivityDuration = now - lastCommandTime

    if (inactivityDuration < threshold) {
        return false  // Pas encore inactif
    }

    // Inactif depuis > threshold, mais est-ce dû au réseau ?
    return when {
        session.state == SessionState.WAITING_NETWORK -> false  // Légitime
        session.lastNetworkErrorTime != null &&
            (now - session.lastNetworkErrorTime!!) < threshold -> false  // Erreur réseau récente
        else -> true  // Inactivité réelle
    }
}
```

## Cas d'Arrêt d'une Automation

Une automation s'arrête dans 4 cas uniquement :

### 1. Flag IA `completed: true`

L'IA indique explicitement qu'elle a terminé son travail :

```kotlin
// Dans AIMessage
data class AIMessage(
    val preText: String,
    val validationRequest: Boolean?,
    val dataCommands: List<DataCommand>?,
    val actionCommands: List<DataCommand>?,
    val postText: String?,
    val keepControl: Boolean?,
    val communicationModule: CommunicationModule?,
    val completed: Boolean?  // NEW: true = travail terminé
)

// Exemple réponse IA finale
{
  "preText": "J'ai terminé l'analyse. Voici le résumé...",
  "completed": true
}
```

**Important** : Documenter clairement dans le prompt Level 1 pour AUTOMATION que l'IA DOIT utiliser `completed: true` pour signaler la fin.

### 2. Limites de boucles autonomes dépassées

```kotlin
if (totalRoundtrips >= limits.maxAutonomousRoundtrips) {
    storeLimitReachedMessage()
    deactivateSession()
    processNextInQueue()
    break
}
```

Valeurs par défaut (cf AI.md) : `automationMaxAutonomousRoundtrips = 20`

### 3. Inactivité réelle > 10 minutes

Pas de commandes exécutées (queries ou actions) pendant plus de 10 minutes, ET pas de problème réseau en cours.

```kotlin
if (isInactiveForRealReason(session, 10.minutes)) {
    storeInactivityTimeoutMessage()
    deactivateSession()
    processNextInQueue()
    break
}
```

**Note** : Si l'inactivité est due au réseau (état `WAITING_NETWORK`), l'automation continue indéfiniment.

### 4. Éviction par demande CHAT

Si un CHAT demande la main ET que l'automation est inactive depuis > `chatMaxInactivityBeforeAutomationEviction` :

```kotlin
if (chatRequestPending) {
    val inactivityDuration = now - lastCommandTime

    if (inactivityDuration > chatMaxInactivityBeforeAutomationEviction) {
        deactivateSession()
        requeueSessionAtPosition1(session.id)  // Position 1 absolue, pas fin de queue
        activateChatSession()
        break
    }
}
```

**Particularité** : L'automation évincée est remise en **position 1** de la queue (priorité absolue). Quand le CHAT se termine, l'automation reprend exactement où elle en était.

**Reprise après éviction** : Pas de message artificiel "Continue", juste relancer `executeAIRound()` avec l'état existant.

## Initialisation au Démarrage

### AIOrchestrator.initialize()

Au démarrage de l'app (MainActivity.onCreate()), nettoyer les sessions actives obsolètes :

```kotlin
suspend fun initialize(context: Context) {
    val activeSessions = sessionService.getAllActiveSessions()

    activeSessions.forEach { session ->
        when (session.type) {
            SessionType.CHAT -> {
                // Chat jamais repris automatiquement
                sessionService.deactivateSession(session.id)
            }
            SessionType.AUTOMATION -> {
                // Vérifier si timeout watchdog dépassé
                val elapsed = System.currentTimeMillis() - session.lastActivity
                if (elapsed > AppConfigManager.getAILimits().automationMaxSessionDuration) {
                    storeTimeoutMessage(session.id)
                    sessionService.deactivateSession(session.id)
                }
                // Sinon, reste active (peut-être un Worker qui tourne)
            }
            SessionType.SEED -> {
                // Jamais active
                sessionService.deactivateSession(session.id)
            }
        }
    }

    // Vérifier automations en attente
    checkPendingAutomations()
}
```

**Règle importante** : Les sessions CHAT ne sont jamais réactivées automatiquement au démarrage (changement par rapport à la logique actuelle).

## Services et Responsabilités

### AutomationService

Service ExecutableService pour CRUD des automations :

```kotlin
class AutomationService(context: Context) : ExecutableService {

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        return when (operation) {
            "create" -> createAutomation(params)
            "update" -> updateAutomation(params)
            "delete" -> deleteAutomation(params)
            "get" -> getAutomation(params)
            "list" -> listAutomations(params)      // Par zone
            "list_all" -> listAllAutomations()
            "enable" -> enableAutomation(params)
            "disable" -> disableAutomation(params)
            "execute_manual" -> executeManual(params)  // Déclenchement manuel
            else -> OperationResult.error("Unknown operation: $operation")
        }
    }

    private suspend fun executeManual(params: JSONObject): OperationResult {
        val automationId = params.getString("automation_id")
        AIOrchestrator.executeAutomation(automationId)
        return OperationResult.success(mapOf("status" to "triggered"))
    }
}
```

**Validation** : Le service valide que le schedule est cohérent, que la seedSession existe, etc.

### AIOrchestrator

Responsable de l'exécution effective :
- Créer session d'exécution
- Copier messages SEED
- Gérer queue FIFO
- Contrôle de session (éviction, reprise)
- Boucles autonomes

### SchedulerWorker

Worker Android vérifiant les schedules toutes les 15 minutes et appelant `AIOrchestrator.executeAutomation()`.

## Interface Utilisateur

### AIScreen adaptatif

Une seule screen `AIScreen` qui s'adapte selon `session.type` :

```kotlin
@Composable
fun AIScreen(sessionId: String) {
    val session = loadSession(sessionId)

    when (session.type) {
        SessionType.CHAT -> {
            // Mode chat normal (écran actuel)
            // Header: titre session + boutons contrôle (stop/start)
            // Messages + input standard
        }
        SessionType.SEED -> {
            // Mode édition automation
            // Header: titre automation + bouton ⚙️ config
            // Messages (1 seul) + input éditable
            // Footer: AutomationEditorFooter (schedule, triggers, enregistrer)
        }
        SessionType.AUTOMATION -> {
            // Mode viewer exécution (read-only)
            // Header: titre automation + statut exécution
            // Messages (déroulement complet)
            // Footer: bouton Interrompre si en cours, sinon Fermer
        }
    }
}
```

### AutomationEditorFooter

Footer spécifique pour le mode SEED :

```kotlin
@Composable
fun AutomationEditorFooter(
    automation: Automation?,  // null si création
    message: String,
    onMessageChange: (String) -> Unit,
    onConfigureSchedule: () -> Unit,
    onConfigureTriggers: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onTest: (() -> Unit)?  // Non-null si édition
) {
    Column {
        // Input message avec enrichments
        MessageInputField(...)

        // Boutons configuration
        Row {
            Button(onClick = onConfigureSchedule) {
                Icon(schedule)
                Text(scheduleLabel)  // "Quotidien 9h" ou "Non configuré"
            }
            Button(onClick = onConfigureTriggers) {
                Icon(trigger)
                Text(triggersLabel)  // "2 triggers" ou "Aucun"
            }
        }

        // Actions
        FormActions(
            onSave = onSave,
            onCancel = onCancel,
            additionalActions = {
                if (onTest != null) {
                    ActionButton(action = REFRESH, onClick = onTest)
                }
            }
        )
    }
}
```

### Cards dans ZoneScreen

Liste compacte des automations dans un onglet "Automations" :

```
┌─────────────────────────────────────────┐
│ [📊] Rapport quotidien santé        [⚙️]│
│ Prochain déclenchement : dans 3h       │
└─────────────────────────────────────────┘
```

**Éléments** :
- Icône choisie + nom
- Statut déduit de la config :
  - "Prochain déclenchement : dans 3h" (schedule)
  - "En cours" (exécution active)
  - "Manuel" (pas de schedule ni triggers)
  - "Selon déclencheurs" (triggers uniquement)
- Bouton ⚙️ pour éditer

**Interactions** :
- **Clic simple** → AutomationDetailsScreen (infos, stats, historique)
- **Bouton ⚙️** → AIScreen mode SEED (édition)
- **Clic long** → Menu contextuel (éditer, dupliquer, supprimer, activer/désactiver)

### AutomationDetailsScreen

Screen de détails avec :
- **Configuration** : Schedule, provider, statut
- **Message seed** : Nom + message
- **Statistiques** : Nombre exécutions, succès/échecs, durée moyenne, nombre de tokens, coût
- **Historique récent** : avec pagination comme pour les tools
- **Actions** : Exécuter maintenant, Éditer, Dupliquer, Supprimer

**Navigation** :

- Clic sur item historique → AIScreen mode AUTOMATION (session d'exécution read-only)
- "Éditer" → AIScreen mode SEED

### Flow création automation

```
ZoneScreen [Tab Automations]
  → Bouton "Créer automation"
    → Dialog compact :
       - Nom : [________]
       - Icône : [Sélecteur]
       - Provider : [Claude ▼]
       - [Continuer]
    → AIScreen mode SEED (nouvelle session vide)
      → Composer message + enrichments
      → Configurer schedule/triggers via footer
      → Enregistrer automation
```

### Flow édition automation

```
ZoneScreen [Tab Automations]
  → Card automation [⚙️]
    → AIScreen mode SEED
      → Message actuel éditable
      → Modifier schedule/triggers
      → Enregistrer modifications
```

## Points d'Attention pour l'Implémentation

### Documentation prompt Level 1

Ajouter dans `PromptChunks.kt` pour les sessions AUTOMATION :

```
Pour les automations, tu DOIS indiquer explicitement quand tu as terminé
ton travail en ajoutant "completed": true dans ta réponse finale.

Exemple de réponse finale :
{
  "preText": "J'ai terminé l'analyse. Voici le résumé...",
  "completed": true
}

Sans ce flag, l'automation continuera jusqu'aux limites de boucles ou timeout d'inactivité.
```

### Persistance SessionState

Les champs `state` et `lastNetworkErrorTime` doivent être persistés en DB pour survivre aux redémarrages de l'app.

### Ordre FIFO strict

L'ordre d'exécution est déterminé par `scheduledExecutionTime` (horaire prévu), pas par l'heure d'ajout en queue.

### Position 1 absolue après éviction

Quand une automation est évincée par un CHAT, elle est remise en position 1 de la queue (avant toutes les autres), pas à la fin.

### Sessions dismissed dans historique

Les sessions dismissed (via paramètre dismiss) restent dans l'historique avec `endReason = DISMISSED` pour traçabilité.

---

*Cette documentation définit l'architecture complète du système d'automations. L'implémentation se fera de manière incrémentale en respectant ces specifications.*
