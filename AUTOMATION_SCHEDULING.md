# Système de Scheduling Automations - Spécification

## Vue d'ensemble

Architecture **pull-based** où `AISessionController` (possède le slot actif) demande à `AutomationScheduler` quelle est la prochaine automation à lancer.

**Principes** :
- Pas de `nextExecutionTime` stocké → calcul dynamique depuis historique
- Une session AUTOMATION = une exécution (pas de table supplémentaire)
- Sessions orphelines (crash) détectées et reprises automatiquement
- Déclenchement périodique (tick 5 min) + événementiel (CRUD, fin session)
- **Watchdog unique externe** : Un seul point de surveillance (tick), pas de watchdog interne
- **Reprise transparente** : Aucun message système ajouté lors des reprises
- **Gestion réseau** : Flag `isWaitingForNetwork` en DB pour distinction timeout vs réseau

---

## 1. Architecture des composants

### AISessionController (modifié)

**Responsabilité COMPLÈTE** : Gérer le slot actif, la queue mémoire ET demander nouvelles automations au scheduler

**Nouveau : tick()** - Point d'entrée unique
```kotlin
// AISessionController
suspend fun tick() {
    // 1. Si slot occupé, vérifier si session zombie/inactive (sécurité)
    if (activeSessionId != null) {
        if (shouldStopInactiveSession()) {
            LogManager.aiSession("Stopping inactive/zombie session: $activeSessionId", "WARN")
            closeActiveSession()  // Libère le slot et continue
            // Ne pas return, on continue le tick normalement
        } else {
            return  // Session légitime en cours, rien à faire
        }
    }

    // 2. Queue mémoire non vide → la vider d'abord (priorités)
    if (sessionQueue.isNotEmpty()) {
        processNextInQueue()
        return
    }

    // 3. Queue vide → demander nouvelles automations scheduled au scheduler
    val next = AutomationScheduler(context).getNextSession()

    when (next) {
        is Resume -> resumeSession(next.sessionId)
        is Create -> createScheduledSession(next.automationId, next.scheduledFor)
        is None -> /* Rien, prochain tick WorkManager réessaiera */
    }
}

/**
 * Check if active session should be stopped (zombie/inactive detection)
 * UNIQUE watchdog - pas de watchdog interne dans AIRoundExecutor
 */
private suspend fun shouldStopInactiveSession(): Boolean {
    val now = System.currentTimeMillis()
    val sessionAge = now - lastActivityTimestamp
    val limits = AppConfigManager.getAILimits()

    return when (activeSessionType) {
        SessionType.AUTOMATION -> {
            if (sessionAge > limits.automationMaxSessionDuration) {
                // Déterminer endReason selon flag réseau
                val session = loadSession(activeSessionId)
                val endReason = if (session.isWaitingForNetwork) {
                    SessionEndReason.NETWORK_ERROR  // Reprendre
                } else {
                    SessionEndReason.TIMEOUT  // Abandonner
                }

                // Set endReason sur session
                coordinator.processUserAction("ai_sessions.set_end_reason", mapOf(
                    "sessionId" to activeSessionId,
                    "endReason" to endReason.name
                ))

                true  // Stop session
            } else {
                false
            }
        }
        SessionType.CHAT -> {
            // Pas de timeout automatique pour CHAT via tick
            // (seulement éviction si AUTOMATION demande, ou user clique stop)
            false
        }
        else -> false
    }
}
```

**Déclenché par** :
- WorkManager (automatique, toutes les 5 min)
- Fin session (`closeActiveSession()`)
- CRUD automation (événementiel)

**Sécurité anti-zombie** :
- Si slot occupé, vérifie inactivité/timeout
- AUTOMATION > `automationMaxSessionDuration` → arrêt forcé avec endReason selon flag réseau
- CHAT : pas de timeout automatique (user control ou éviction par AUTOMATION)

---

### AutomationScheduler (nouveau helper)

**Responsabilité** : Calculateur pur - quelle automation scheduled devrait se lancer maintenant

**API publique** :
```kotlin
class AutomationScheduler(context: Context) {

    /**
     * Pure calculation - returns next automation session to execute (if any)
     * Checks all enabled automations and returns oldest scheduled/orphaned/suspended
     * NO notion of slot/queue - just calculation
     */
    suspend fun getNextSession(): NextSession
}
```

**NextSession return type** :
```kotlin
sealed class NextSession {
    // Resume incomplete session (crash/network/suspended)
    data class Resume(val sessionId: String) : NextSession()

    // Create new scheduled session
    data class Create(
        val automationId: String,
        val scheduledFor: Long
    ) : NextSession()

    // Nothing to execute now
    object None : NextSession()
}
```

**Logique getNextSession()** :

Pour chaque automation enabled avec schedule :
1. Chercher session à reprendre (`endReason IN (null, 'NETWORK_ERROR', 'SUSPENDED')`, `isActive=false`)
   - Si trouvée → ajouter à liste candidats (scheduledExecutionTime, RESUME, sessionId)
2. Sinon → calculer next expected execution :
   - Query dernière session COMPLÉTÉE (`endReason IN ('COMPLETED', 'CANCELLED', 'TIMEOUT', 'ERROR')`)
   - `nextExpected = ScheduleCalculator.calculate(schedule, after = lastCompleted.scheduledExecutionTime)`
   - Si `nextExpected <= now` → ajouter à liste candidats (nextExpected, CREATE, automationId)
3. Trier candidats par `scheduledTime` ASC (plus ancien first)
4. Retourner first OU None si vide

**Note importante** : Sessions `NETWORK_ERROR` reprises en boucle si réseau reste down (comportement voulu). Pas de limite tentatives.

---

### Modifié : AISessionController

**Changements** :

**1. Nouveau tick() - point d'entrée unique** (voir ci-dessus)

**2. Appel tick() à chaque libération slot**
```kotlin
fun closeActiveSession() {
    // ... cleanup existant ...

    // Trigger tick pour traiter queue ou demander nouvelles automations
    scope.launch {
        tick()
    }
}
```

**3. Priorités dans processNextInQueue()**
```
Queue mémoire contient UNIQUEMENT : CHAT + MANUAL

Tri :
1. CHAT (priorité absolue)
2. MANUAL (user clicked execute)

Note : SCHEDULED ne sont JAMAIS en queue mémoire
       Créées à la demande par tick() quand slot libre + queue vide
```

**4. ExecutionTrigger dans QueuedSession**
```kotlin
enum class ExecutionTrigger {
    SCHEDULED,  // Créée par AutomationScheduler (mais ne passe PAS par queue)
    MANUAL,     // User clicked execute (en queue si slot occupé)
    EVENT       // Trigger event (futur)
}

data class QueuedSession(
    val sessionId: String,
    val type: SessionType,
    val trigger: ExecutionTrigger?,  // null for CHAT
    val automationId: String?,
    val scheduledExecutionTime: Long,
    val enqueuedAt: Long
)
```

**5. Gestion éviction CHAT par AUTOMATION**
```kotlin
// Quand AUTOMATION demande slot et CHAT inactif > seuil
private fun requestSessionControl(sessionId: String, type: SessionType, ...): SessionControlResult {
    if (type == SessionType.AUTOMATION && activeSessionType == SessionType.CHAT) {
        val chatInactivityDuration = System.currentTimeMillis() - lastActivityTimestamp
        val limits = AppConfigManager.getAILimits()

        if (chatInactivityDuration > limits.chatMaxInactivityBeforeAutomationEviction) {
            // Éviction CHAT → marquer CANCELLED (user peut relancer)
            scope.launch {
                coordinator.processUserAction("ai_sessions.set_end_reason", mapOf(
                    "sessionId" to activeSessionId,
                    "endReason" to SessionEndReason.CANCELLED.name
                ))
            }

            closeActiveSession()
            // Continue avec activation AUTOMATION
        } else {
            // CHAT récent → AUTOMATION en queue
            return SessionControlResult.QUEUED
        }
    }

    // ... reste logique existante ...
}
```

**Note** : CHAT n'évince JAMAIS automation automatiquement. User doit explicitement STOP ou PAUSE.

**6. Reprise session**
```kotlin
/**
 * Resume incomplete session (crash/network/suspended)
 * NO system message added - transparent resume
 */
private suspend fun resumeSession(sessionId: String) {
    val result = requestSessionControl(
        sessionId = sessionId,
        type = SessionType.AUTOMATION,
        trigger = ExecutionTrigger.SCHEDULED,
        automationId = session.automationId,
        scheduledExecutionTime = session.scheduledExecutionTime
    )

    when (result) {
        SessionControlResult.ACTIVATED -> {
            // Déterminer RoundReason selon endReason
            val reason = when (session.endReason) {
                null -> RoundReason.AUTOMATION_RESUME_CRASH
                "NETWORK_ERROR" -> RoundReason.AUTOMATION_RESUME_NETWORK
                "SUSPENDED" -> RoundReason.AUTOMATION_RESUME_SUSPENDED
                else -> RoundReason.AUTOMATION_START
            }

            AIOrchestrator.executeAIRound(reason)
        }
        SessionControlResult.QUEUED -> {
            // Rare : slot pris par CHAT entre temps
        }
        // ...
    }
}
```

---

### AISessionEntity (modifié)

**Nouveaux champs** :
```kotlin
@Entity
data class AISessionEntity(
    // ... champs existants ...

    val endReason: String?,           // COMPLETED, TIMEOUT, ERROR, CANCELLED, INTERRUPTED, NETWORK_ERROR, SUSPENDED
    val tokensUsed: Int?,             // Total tokens (pour monitoring)
    val isWaitingForNetwork: Boolean = false  // Flag réseau pour watchdog
)
```

**Nouvelles queries DAO** :
```kotlin
@Query("""
    SELECT * FROM ai_sessions
    WHERE automationId = :automationId
      AND type = 'AUTOMATION'
      AND endReason IN ('null', 'NETWORK_ERROR', 'SUSPENDED')
      AND isActive = 0
    ORDER BY scheduledExecutionTime DESC
    LIMIT 1
""")
suspend fun getIncompleteAutomationSession(automationId: String): AISessionEntity?

@Query("""
    SELECT * FROM ai_sessions
    WHERE automationId = :automationId
      AND type = 'AUTOMATION'
      AND endReason IN ('COMPLETED', 'CANCELLED', 'TIMEOUT', 'ERROR')
    ORDER BY scheduledExecutionTime DESC
    LIMIT 1
""")
suspend fun getLastCompletedAutomationSession(automationId: String): AISessionEntity?
```

---

### SessionEndReason (étendu)

```kotlin
enum class SessionEndReason {
    COMPLETED,       // IA a mis "completed": true
    TIMEOUT,         // Watchdog timeout (inactivité sans réseau)
    ERROR,           // Erreur technique fatale
    CANCELLED,       // User a cliqué stop (OU CHAT évincé par AUTOMATION)
    INTERRUPTED,     // Legacy/alias pour null (crash détecté comme orpheline)
    NETWORK_ERROR,   // Timeout avec flag réseau actif (à reprendre)
    SUSPENDED        // User a cliqué pause (à reprendre)
}
```

**Sessions à reprendre** : `null` (crash), `NETWORK_ERROR`, `SUSPENDED`
**Sessions terminées** : `COMPLETED`, `CANCELLED`, `TIMEOUT`, `ERROR`

---

### AIRoundExecutor (modifié)

**SUPPRESSION watchdog interne** : Plus de watchdog dans AIRoundExecutor. Seul tick() surveille.

**Gestion flag réseau** :
```kotlin
// Avant query IA
suspend fun executeAIRound(reason: RoundReason) {
    // ... code existant ...

    // Check réseau AVANT query
    if (!NetworkUtils.isNetworkAvailable(context)) {
        // Set flag réseau
        coordinator.processUserAction("ai_sessions.set_network_flag", mapOf(
            "sessionId" to sessionId,
            "isWaitingForNetwork" to true
        ))

        if (sessionType == SessionType.AUTOMATION) {
            // Retry avec delay (tentatives infinies si réseau down)
            delay(30_000)
            return executeAIRound(reason)  // Recursive retry
        } else {
            // CHAT : toast + stop
            return
        }
    }

    // Query IA
    val response = AIClient.query(...)

    if (response.success) {
        // Reset flag réseau dès réponse OK
        coordinator.processUserAction("ai_sessions.set_network_flag", mapOf(
            "sessionId" to sessionId,
            "isWaitingForNetwork" to false
        ))

        // ... traitement réponse ...
    }
}
```

**Fin d'automation** :
```kotlin
// Quand automation se termine (completed, error, etc.)
suspend fun onAutomationEnd(sessionId: String, endReason: SessionEndReason) {
    // 1. Update session avec endReason
    coordinator.processUserAction("ai_sessions.set_end_reason", mapOf(
        "sessionId" to sessionId,
        "endReason" to endReason.name
    ))

    // 2. closeActiveSession() appellera tick() automatiquement
}
```

---

### AutomationEntity (modifié)

**Suppressions** :
```kotlin
// SUPPRIMER ce champ (calcul dynamique)
// val nextExecutionTime: Long?
```

**Champs conservés** :
```kotlin
@Entity
data class AutomationEntity(
    val id: String,
    val name: String,
    val zoneId: String,
    val seedSessionId: String,
    val scheduleJson: String?,           // ScheduleConfig serialized
    val triggerIdsJson: String,          // List<String> serialized
    val dismissOlderInstances: Boolean,
    val providerId: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    val lastExecutionId: String?,        // Last session ID (pour UI)
    val executionHistoryJson: String     // List<String> session IDs (limité)
)
```

---

### AutomationService (modifié)

**Triggers événementiels** :
```kotlin
override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
    val result = when (operation) {
        "create" -> createAutomation(params, token)
        "update" -> updateAutomation(params, token)
        "enable" -> setEnabled(params, token, true)
        "disable" -> setEnabled(params, token, false)
        "execute_manual" -> executeManual(params, token)
        // ...
    }

    // Trigger tick après CRUD (si succès)
    if (result.success && operation in listOf("create", "update", "enable", "disable")) {
        scope.launch {
            AISessionController.tick()
        }
    }

    return result
}
```

**execute_manual modifié** :
```kotlin
private suspend fun executeManual(params: JSONObject, token: CancellationToken): OperationResult {
    val automationId = params.optString("automation_id")

    // Déléguer à AIOrchestrator avec trigger MANUAL
    // (passera par requestSessionControl, queue si slot occupé)
    return AIOrchestrator.executeAutomation(
        automationId = automationId,
        trigger = ExecutionTrigger.MANUAL,
        scheduledFor = System.currentTimeMillis()
    )
}
```

---

### SchedulerWorker (modifié)

**Changement interval** : 15 min → **5 min**

**Tick périodique automatique** : WorkManager déclenche tick() toutes les 5 min, même si slot occupé (early return)

```kotlin
override suspend fun doWork(): Result {
    // Déléguer à AISessionController
    // tick() fera early return si slot occupé
    // Le prochain tick WorkManager viendra automatiquement dans 5 min
    AISessionController.tick()

    return Result.success()
}
```

**MainActivity setup** :
```kotlin
// Changer de 15 à 5 minutes
PeriodicWorkRequestBuilder<SchedulerWorker>(5, TimeUnit.MINUTES).build()
```

---

## 2. Reprise sessions transparente

### Pas de message système

Quand `Resume(sessionId)` détecté :

1. **AUCUN message ajouté** à la session
2. Activer session via `requestSessionControl()`
3. Trigger `executeAIRound()` avec RoundReason approprié (pour logs uniquement)

**RoundReason nouveaux** :
```kotlin
enum class RoundReason {
    // ... existants ...
    AUTOMATION_RESUME_CRASH,      // Reprise après crash
    AUTOMATION_RESUME_NETWORK,    // Reprise après timeout réseau
    AUTOMATION_RESUME_SUSPENDED   // Reprise après pause
}
```

**Comportement** : L'IA reprend exactement où elle s'était arrêtée, transparence totale. Boucles infinies NETWORK_ERROR acceptées si réseau reste down.

---

## 3. Points de déclenchement résumé

**Tous appellent AISessionController.tick()** (point d'entrée unique) :

| Événement | Qui appelle | Notes |
|-----------|-------------|-------|
| **Tick périodique (5 min)** | WorkManager → SchedulerWorker | Automatique, continue même si slot occupé (early return) |
| **Fin session** | `closeActiveSession()` → `tick()` | Réactivité immédiate (pas attendre 5 min) |
| **CRUD automation** | AutomationService → `tick()` | Schedule peut avoir changé |

**Flux directs (ne passent PAS par tick)** :

| Événement | Action | Notes |
|-----------|--------|-------|
| **User démarre CHAT** | `sendMessageAsync()` → `requestSessionControl(CHAT)` | Entre directement dans queue si slot occupé |
| **User execute manual** | `executeAutomation(MANUAL)` → `requestSessionControl(AUTOMATION, MANUAL)` | Entre directement dans queue si slot occupé |

---

## 4. Flow complets

### Flow 1 : Automation scheduled normale

```
T=10:00 - Tick WorkManager
  ↓
AISessionController.tick()
  → Slot libre ? Oui
  → Queue vide ? Oui
  → AutomationScheduler.getNextSession()
    → Automation A : next = 09:55 (déjà passé)
    → Automation B : next = 10:15 (pas encore)
    → Liste : [A(09:55)]
    → Retourne Create(A, 09:55)
  ↓
createScheduledSession(A, 09:55)
  → AIOrchestrator.executeAutomation(A, SCHEDULED, 09:55)
  → Crée session AUTOMATION
  → requestSessionControl(sessionId, AUTOMATION, SCHEDULED)
  → Slot libre → active immédiatement
```

### Flow 2 : Reprise après crash (transparente)

```
T=09:50 - Automation A démarre
T=09:52 - App crash → session reste endReason=null, isActive=false

T=10:00 - App redémarre + Tick WorkManager
  ↓
AISessionController.tick()
  → Slot libre ? Oui
  → Queue vide ? Oui
  → AutomationScheduler.getNextSession()
    → Automation A : session incomplete (endReason=null)
    → Retourne Resume(sessionA_09:50)
  ↓
resumeSession(sessionA_09:50)
  → AUCUN message ajouté
  → requestSessionControl(sessionA, AUTOMATION, SCHEDULED, 09:50)
  → Slot libre → active
  → executeAIRound(AUTOMATION_RESUME_CRASH)
  → L'IA reprend exactement où elle s'était arrêtée
```

### Flow 3 : CHAT attend fin automation (ou user stop/pause)

```
T=10:00 - Automation A en cours
T=10:02 - User ouvre CHAT
  → requestSessionControl(chatId, CHAT)
  → Automation en cours → CHAT en queue
  → User doit explicitement STOP ou PAUSE si veut passer immédiatement

Queue : [CHAT(@10:02)]

Cas 1 : User clique STOP sur automation
  → set endReason = CANCELLED
  → closeActiveSession()
  → tick()
    → Queue non vide → processNextInQueue()
      → Active CHAT

Cas 2 : User clique PAUSE sur automation
  → set endReason = SUSPENDED
  → closeActiveSession()
  → tick()
    → Queue non vide → processNextInQueue()
      → Active CHAT
  → Automation A sera reprise plus tard (après CHAT terminé)

Cas 3 : User attend, automation termine naturellement
  → set endReason = COMPLETED
  → closeActiveSession()
  → tick()
    → Queue non vide → processNextInQueue()
      → Active CHAT
```

### Flow 4 : AUTOMATION évince CHAT inactif

```
T=10:00 - CHAT actif
T=10:01 - User n'interagit plus (lastActivityTimestamp = 10:01)

T=10:07 - Tick WorkManager (CHAT toujours actif)
  → AutomationScheduler.getNextSession()
    → Automation A : schedulée pour 09:55 (passé)
    → Retourne Create(A, 09:55)
  → requestSessionControl(sessionA, AUTOMATION, SCHEDULED, 09:55)
    → CHAT actif → check inactivité
      → 10:07 - 10:01 = 6 min > chatMaxInactivityBeforeAutomationEviction (5 min)
      → Éviction CHAT :
        → set endReason = CANCELLED
        → closeActiveSession()
      → Active AUTOMATION immédiatement
```

### Flow 5 : Boucle NETWORK_ERROR (transparente)

```
T=10:00 - Automation A démarre
T=10:01 - Réseau down détecté
  → set isWaitingForNetwork = true
  → Retry loop avec delay 30s

T=10:11 - Watchdog tick() détecte inactivité > 10 min
  → shouldStopInactiveSession()
    → isWaitingForNetwork = true
    → set endReason = NETWORK_ERROR
    → closeActiveSession()

  → tick() continue
    → AutomationScheduler.getNextSession()
      → Automation A : session incomplete (endReason=NETWORK_ERROR)
      → Retourne Resume(sessionA)

  → resumeSession(sessionA)
    → AUCUN message ajouté
    → executeAIRound(AUTOMATION_RESUME_NETWORK)
    → Réseau toujours down...
    → Retry loop...

T=10:21 - Watchdog à nouveau (toujours down)
  → NETWORK_ERROR again
  → Resume again
  → Loop continue...

T=10:45 - Réseau revient
  → Query IA réussit
  → set isWaitingForNetwork = false
  → Continue normalement
```

### Flow 6 : Plusieurs en queue (CHAT prioritaire)

```
T=10:00 - Automation A en cours

T=10:01 - User execute manual B
  → requestSessionControl(sessionB, AUTOMATION, MANUAL)
  → Slot occupé → enqueue

T=10:02 - User ouvre CHAT
  → requestSessionControl(chatId, CHAT)
  → Slot occupé → enqueue

Queue : [
  AUTO_B(MANUAL, @10:01),
  CHAT(@10:02)
]

T=10:05 - Automation A terminée
  → closeActiveSession()
  → tick()
    → Slot libre ? Oui
    → Queue non vide ? Oui
      → processNextInQueue()
        → Tri : CHAT prioritaire
        → Active CHAT

Queue : [AUTO_B(MANUAL, @10:01)]

T=10:10 - CHAT terminé
  → closeActiveSession()
  → tick()
    → Slot libre ? Oui
    → Queue non vide ? Oui
      → processNextInQueue()
        → Active AUTO_B
```

---

## 5. Plan d'implémentation (étapes)

### Étape 1 : Préparation DB et models

**Modifications** :
- ✅ Ajouter `endReason: String?`, `tokensUsed: Int?` et `isWaitingForNetwork: Boolean` à `AISessionEntity`
- ✅ Ajouter queries DAO : `getIncompleteAutomationSession()`, `getLastCompletedAutomationSession()`
- ✅ Étendre `SessionEndReason` avec `NETWORK_ERROR`, `SUSPENDED`
- ✅ Créer `ExecutionTrigger` enum
- ✅ Ajouter `trigger: ExecutionTrigger?` à `QueuedSession`
- ✅ Supprimer `nextExecutionTime` de `AutomationEntity`
- ✅ Ajouter `RoundReason.AUTOMATION_RESUME_*`
- ✅ Nouvelle opération AISessionService : `set_network_flag`

**Migration DB** : Ajouter colonnes `endReason`, `tokensUsed` (nullable), `isWaitingForNetwork` (default false)

---

### Étape 2 : Créer AutomationScheduler

**Nouveau fichier** : `core/ai/scheduling/AutomationScheduler.kt`

**Implémente** :
- `getNextSession()` : logique calcul dynamique + détection incomplètes (helper pur, pas d'état)
- Query sessions incomplètes (`endReason IN (null, 'NETWORK_ERROR', 'SUSPENDED')`)
- Calcul next execution depuis dernière complétée
- Tri par `scheduledExecutionTime` ASC
- Pas besoin de l'enregistrer dans ServiceRegistry (juste un helper appelé par AISessionController)

---

### Étape 3 : Modifier AISessionController

**Changements** :
- Ajouter méthode `tick()` (point d'entrée unique, logique complète : slot/queue/scheduler)
- Ajouter `shouldStopInactiveSession()` (watchdog unique : détection zombie/timeout + flag réseau)
- Appeler `tick()` dans `closeActiveSession()`
- Modifier `processNextInQueue()` pour tri : CHAT > MANUAL (PAS de SCHEDULED en queue)
- Modifier `requestSessionControl()` pour éviction CHAT par AUTOMATION (si inactif > seuil)
- Implémenter `resumeSession()` sans message système
- Implémenter `createScheduledSession()`

---

### Étape 4 : Modifier AIRoundExecutor

**Changements** :
- **SUPPRIMER watchdog interne** (plus de surveillance dans executeAIRound)
- Ajouter gestion flag `isWaitingForNetwork` :
  - Set `true` quand réseau absent détecté
  - Set `false` dès réponse IA success
- Retry infini pour AUTOMATION si réseau down (avec delay 30s)
- À la fin d'automation, appeler `set_end_reason` avec endReason approprié
- `closeActiveSession()` déclenchera automatiquement `tick()`

---

### Étape 5 : Modifier AutomationService

**Changements** :
- Supprimer logique `nextExecutionTime` dans create/update
- Ajouter trigger `AISessionController.tick()` après CRUD operations (create/update/enable/disable)
- Modifier `execute_manual` pour passer trigger MANUAL (passera par requestSessionControl + queue)

---

### Étape 6 : Modifier AISessionService

**Nouvelle opération** :
```kotlin
"set_network_flag" -> {
    val sessionId = params.optString("sessionId")
    val isWaiting = params.optBoolean("isWaitingForNetwork")
    dao.updateNetworkFlag(sessionId, isWaiting)
    OperationResult.success()
}
```

---

### Étape 7 : Modifier SchedulerWorker

**Changements** :
- Changer interval 15 min → 5 min dans `MainActivity`
- Remplacer logique par simple appel `AISessionController.tick()`
- Note : tick() continue automatiquement toutes les 5 min (WorkManager), même si slot occupé (early return)

---

### Étape 8 : UI - Boutons STOP et PAUSE

**Modifications** : `AIScreen.kt:AutomationHeader`

**Ajouter** :
```kotlin
// Stop button (CANCELLED)
UI.ActionButton(
    action = ButtonAction.STOP,
    display = ButtonDisplay.ICON,
    size = Size.M,
    onClick = {
        coordinator.processUserAction("ai_sessions.set_end_reason", mapOf(
            "sessionId" to sessionId,
            "endReason" to SessionEndReason.CANCELLED.name
        ))
        AISessionController.closeActiveSession()
    }
)

// Pause button (SUSPENDED - reprendra plus tard)
UI.ActionButton(
    action = ButtonAction.PAUSE,
    display = ButtonDisplay.ICON,
    size = Size.M,
    onClick = {
        coordinator.processUserAction("ai_sessions.set_end_reason", mapOf(
            "sessionId" to sessionId,
            "endReason" to SessionEndReason.SUSPENDED.name
        ))
        AISessionController.closeActiveSession()
    }
)
```

**Strings** :
```xml
<string name="action_pause">Pause</string>
```

---

### Étape 9 : Documentation IA - Flag completed

**Modifier** : `PromptChunks.kt` section AUTOMATION

**Améliorer doc** :
```
Le flag "completed": true indique que TOUTE l'automation est terminée.

❌ NE PAS utiliser "completed": true après chaque étape intermédiaire
✅ UTILISER "completed": true UNIQUEMENT quand ton travail complet est terminé

Exemples d'usage INCORRECT :
- Après avoir collecté des données → NON
- Après avoir créé un outil → NON
- Entre deux actions → NON

Exemple d'usage CORRECT :
- Toutes les données collectées ET analysées ET rapport créé → OUI

Si tu rencontres des problèmes réseau ou erreurs techniques, continue de travailler normalement.
Le système gère automatiquement les interruptions et tu reprendras exactement où tu t'es arrêté.
```

---

### Étape 10 : Tests et validation

**Scénarios à tester** :
1. Automation scheduled normale (tick détecte et lance)
2. Reprise après crash (orpheline détectée et reprise transparente, aucun message)
3. CHAT attend fin automation (user doit stop/pause pour passer)
4. AUTOMATION évince CHAT inactif > seuil (CHAT marqué CANCELLED)
5. Priorités queue : CHAT > MANUAL
6. Modification schedule → trigger tick → lance si c'est l'heure
7. Plusieurs automations scheduled simultanées → tri par date
8. IA oublie flag completed → watchdog timeout → prochaine session calculée
9. **Session zombie** : automation bloquée > 10 min → tick() la stoppe et lance suivante
10. **Boucle réseau** : automation timeout réseau → reprend en boucle transparente jusqu'à réseau OK
11. **Pause/Resume** : user pause automation → SUSPENDED → reprend plus tard sans message
12. **Watchdog unique** : Pas de watchdog interne, tick() seul surveille

---

## 6. Avantages architecture finale

- ✅ **Robuste** : Crash recovery automatique via détection incomplètes (null/NETWORK_ERROR/SUSPENDED)
- ✅ **Sécurisé** : Watchdog unique externe dans tick() (pas de duplication logique)
- ✅ **Simple** : Pas de duplication (session = exécution), pas de nextExecutionTime stocké
- ✅ **Réactif** : Tick 5 min automatique + événements (CRUD, fin session)
- ✅ **Priorités claires** : CHAT > MANUAL > SCHEDULED (dont reprises)
- ✅ **Source unique** : Schedule + historique sessions → calcul dynamique
- ✅ **Responsabilité claire** : AISessionController orchestre tout (slot + queue + demande scheduler)
- ✅ **Testable** : Logique calcul isolée dans AutomationScheduler (helper pur)
- ✅ **Flux unifiés** : Tout passe par requestSessionControl(), tout appelle tick()
- ✅ **Auto-réparation** : tick() nettoie sessions bloquées/zombies automatiquement
- ✅ **Gestion réseau** : Flag DB pour distinction timeout vs réseau, boucles infinies acceptées
- ✅ **Transparence** : Reprises sans message système, expérience continue pour l'IA
- ✅ **Contrôle user** : Boutons STOP/PAUSE explicites, pas d'éviction automatique CHAT → AUTO

---

*Document de conception - Système de scheduling automations*
*Version 2.0 - Architecture clarifiée et simplifiée*
