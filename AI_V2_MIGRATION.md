# Migration IA Architecture V2 - État d'avancement

**Date :** 16 octobre 2025
**Status :** Architecture de base implémentée - Intégration en cours

---

## ✅ IMPLÉMENTÉ

### 1. Domain Layer (Pure Logic)
- ✅ `Phase.kt` - Enum des phases d'exécution avec helpers
- ✅ `AIState.kt` - État complet avec compteurs et timestamps
- ✅ `AIEvent.kt` - Hiérarchie complète des events
- ✅ `AIStateMachine.kt` - Logique pure de transitions
- ✅ `WaitingContext.kt` - Contextes d'attente (validation, communication, completion)
- ✅ `AILimitsConfig.kt` - Configuration des limites par session type

### 2. State Management
- ✅ `AIStateRepository.kt` - Gestion state avec sync DB atomique
- ✅ `AIMessageRepository.kt` - Persistence synchrone des messages

### 3. Scheduling & Validation
- ✅ `AISessionScheduler.kt` - Logique scheduling + interruption + inactivité
- ✅ `ValidationResolver.kt` - Déjà existant et compatible V2

### 4. Event Processing
- ✅ `AIEventProcessor.kt` - Event loop + side effects (structure de base)

### 5. Orchestration
- ✅ `AIOrchestrator.kt` - Nouvelle façade mince V2
- ✅ `AIOrchestrator_OLD.kt` - Ancienne version préservée

### 6. Database
- ✅ `AISessionEntity.kt` - Migré avec nouveaux champs V2
- ✅ `AppDatabase.kt` - Version incrémentée (v8 → v9)
- ✅ `AIDao.kt` - Nettoyé (supprimé updateNetworkFlag obsolète)

---

## 🚧 À COMPLÉTER

### 1. AIEventProcessor - Side Effects Complets

**Fichier :** `core/ai/processing/AIEventProcessor.kt`

#### TODOs critiques :
```kotlin
// executeEnrichments()
- Load enrichments from last user message
- Process enrichments via EnrichmentProcessor
- Execute commands via CommandExecutor

// callAI()
- Implement network check via NetworkUtils
- Handle AI response parsing via AIResponseParser

// executeDataQueries()
- Extract dataCommands from last AI message
- Execute via CommandExecutor
- Format results for prompt

// executeActions()
- Extract actionCommands from last AI message
- Full validation flow integration
- Execute via CommandExecutor
- Handle keepControl logic
```

### 2. AIMessageRepository - Serialization

**Fichier :** `core/ai/state/AIMessageRepository.kt`

#### TODOs :
```kotlin
// Extension functions placeholders
- RichMessage.toJson() / fromJson()
- SystemMessage.toJson() / fromJson()
- ExecutionMetadata.toJson() / fromJson()
```

### 3. AIOrchestrator - API Complète

**Fichier :** `core/ai/orchestration/AIOrchestrator.kt`

#### TODOs :
```kotlin
// sendMessage()
- Implement UserMessageSent event
- Full message + enrichments flow

// requestChatSession()
- Session creation logic
- Activation flow

// executeAutomation()
- AUTOMATION session creation from SEED
- Activation flow
```

### 4. Intégration UI

**Fichiers à mettre à jour :**
- `core/ai/ui/screens/AIScreen.kt` - Observer AIState au lieu de WaitingState
- `core/ai/ui/chat/ChatComponents.kt` - Adaptation aux nouveaux states
- `core/ai/ui/automation/AutomationCard.kt` - Affichage phase au lieu de state

#### Pattern UI à suivre :
```kotlin
@Composable
fun AIScreen() {
    val state by AIOrchestrator.currentState.collectAsState()

    when (state.phase) {
        Phase.CALLING_AI -> ShowLoadingIndicator()
        Phase.WAITING_VALIDATION -> ShowValidationDialog()
        Phase.WAITING_COMMUNICATION_RESPONSE -> ShowCommunicationModule()
        // etc.
    }
}
```

### 5. Intégration Services

**Fichiers concernés :**
- `core/ai/services/AISessionService.kt` - Utiliser nouveaux champs Entity
- `core/ai/services/AutomationService.kt` - Appeler tick() après CRUD

### 6. Strings i18n

**Fichier :** `core/strings/sources/shared.xml`

#### Strings à ajouter :
```xml
<!-- AI Phases -->
<string name="ai_phase_idle">En attente</string>
<string name="ai_phase_executing_enrichments">Enrichissements...</string>
<string name="ai_phase_calling_ai">Appel IA...</string>
<string name="ai_phase_parsing">Analyse réponse...</string>
<string name="ai_phase_waiting_validation">Validation requise</string>
<string name="ai_phase_waiting_communication">En attente de réponse</string>
<string name="ai_phase_executing_queries">Récupération données...</string>
<string name="ai_phase_executing_actions">Exécution actions...</string>
<string name="ai_phase_waiting_completion">Confirmation...</string>
<string name="ai_phase_waiting_network">Attente réseau...</string>
<string name="ai_phase_retrying">Nouvelle tentative...</string>
<string name="ai_phase_completed">Terminé</string>

<!-- Errors -->
<string name="ai_error_network_unavailable">Réseau indisponible</string>
<string name="ai_error_timeout">Délai dépassé</string>
<string name="ai_error_limit_reached">Limite atteinte</string>
```

### 7. Tests

#### Tests unitaires à créer :
- `AIStateMachineTest.kt` - Test toutes les transitions
- `AIStateRepositoryTest.kt` - Test sync DB
- `AISessionSchedulerTest.kt` - Test logique scheduling + inactivité

---

## 📋 ORDRE D'IMPLÉMENTATION RECOMMANDÉ

1. **Compléter AIEventProcessor** (priorité haute)
   - Side effects essentiels pour flow complet

2. **Compléter AIMessageRepository**
   - Serialization RichMessage/SystemMessage

3. **Compléter AIOrchestrator API**
   - sendMessage(), requestChatSession(), executeAutomation()

4. **Intégrer UI**
   - Observer AIState dans tous les composants UI

5. **Ajouter i18n strings**
   - Phases et erreurs

6. **Tests**
   - Tests unitaires des composants purs

---

## 🔧 COMPATIBILITÉ

### Composants réutilisés sans modification :
- ✅ `ValidationResolver` - Compatible V2
- ✅ `PromptManager` - Compatible V2
- ✅ `AIClient` - Compatible V2
- ✅ `CommandExecutor` - Compatible V2
- ✅ `AutomationScheduler` - Compatible V2

### Composants dépréciés (à supprimer après migration) :
- ❌ `AIOrchestrator_OLD.kt` - Ancienne architecture
- ❌ `AISessionController.kt` - Remplacé par AISessionScheduler
- ❌ `AIRoundExecutor.kt` - Logique intégrée dans AIEventProcessor
- ❌ `AIMessageStorage.kt` - Remplacé par AIMessageRepository
- ❌ `AIUserInteractionManager.kt` - Logique intégrée dans AIEventProcessor

---

## 💡 POINTS D'ATTENTION

### 1. Session Queue
La queue des sessions est actuellement en mémoire dans l'ancienne architecture. Dans la V2, elle devrait être :
- Soit en mémoire dans AISessionScheduler (sessions légères)
- Soit persistée en DB (si besoin de survie app restart)

**Décision à prendre :** Queue en mémoire suffit (CHAT + MANUAL uniquement, SCHEDULED calculé dynamiquement)

### 2. Network Retry Coroutine
AIEventProcessor utilise un Job pour le retry network. Ce job doit survivre aux changements de phase.
**Implémenté :** Job stocké dans networkRetryJob et annulé proprement

### 3. Completion Confirmation (AUTOMATION)
Le délai de 1s pour auto-confirmation est géré par coroutine dans AIEventProcessor.
**Implémenté :** Utilise delay(1000) puis emit(CompletionConfirmed)

### 4. State Restoration
AIStateRepository.initializeFromDb() restaure le state depuis DB au démarrage.
**Implémenté :** Conversion Entity → AIState avec parsing Phase enum

---

## 📊 MÉTRIQUES

- **Fichiers créés :** 10
- **Fichiers modifiés :** 3
- **Fichiers dépréciés :** 5
- **Lignes de code (nouveaux fichiers) :** ~2000
- **Version DB :** 8 → 9

---

## 🎯 PROCHAINE SESSION

**Objectif :** Compléter les TODOs dans AIEventProcessor pour avoir un flow end-to-end fonctionnel.

**Priorité 1 :**
1. Implémenter executeEnrichments() complet
2. Implémenter callAI() avec parsing réponse
3. Implémenter executeDataQueries() et executeActions()

**Priorité 2 :**
4. Intégrer UI avec observation AIState
5. Ajouter strings i18n

---

**Document créé le 16 octobre 2025**
**Architecture V2 Event-Driven - Migration en cours**
