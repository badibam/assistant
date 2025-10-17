# Tâches Restantes - Système IA

## 1. CHAT : preText seul → Phase.IDLE
**Fichier** : `AIStateMachine.kt` → `handleAIResponseParsed()`
**Logique** : Quand CHAT sans commandes/completed/communicationModule → `Phase.IDLE` au lieu de `Phase.CLOSED`

---

## 2. AUTOMATION : Ignorer validationRequest et communicationModule
**Fichier** : `AIStateMachine.kt` → `handleAIResponseParsed()`
**Logique** : Priorités 1 et 2 deviennent conditionnelles avec `&& sessionType == CHAT`

---

## 3. Nouvelle phase PREPARING_CONTINUATION
**Fichiers** :
- `Phase.kt` : ajouter `PREPARING_CONTINUATION` dans enum
- `AIState.kt` : ajouter `continuationReason: ContinuationReason? = null`
- Créer `ContinuationReason.kt` : enum avec `AUTOMATION_NO_COMMANDS` et `COMPLETION_CONFIRMATION_REQUIRED`

---

## 4. AUTOMATION sans commandes → Guidage
**Fichier** : `AIStateMachine.kt` → `handleAIResponseParsed()`
**Logique** :
```
Si AUTOMATION && pas de dataCommands && pas de actionCommands && pas de completed:
  → Phase.PREPARING_CONTINUATION + continuationReason = AUTOMATION_NO_COMMANDS
```

**Fichier** : `AIEventProcessor.kt` → `handleStateChange()`
**Logique** :
```
Phase.PREPARING_CONTINUATION:
  switch (continuationReason):
    AUTOMATION_NO_COMMANDS:
      - Créer SystemMessage du genre "Tu es en mode automation, tes réponses doivent contenir des requêtes ou actions. Si tu as tout terminé, utilise le flag completed." 
      - Émettre AIEvent.ContinuationReady
```

---

## 5. Double confirmation du flag `completed`
**Fichier** : `AIState.kt`
**Ajout** : `awaitingCompletionConfirmation: Boolean = false`

**Fichier** : `AIStateMachine.kt` → `handleAIResponseParsed()`
**Logique** :
```
Si completed == true && AUTOMATION:
  Si awaitingCompletionConfirmation == false:
    → Phase.PREPARING_CONTINUATION + continuationReason = COMPLETION_CONFIRMATION_REQUIRED
       + awaitingCompletionConfirmation = true
  Si awaitingCompletionConfirmation == true:
    → Phase.AWAITING_SESSION_CLOSURE (vraiment terminé)
```

**Fichier** : `AIEventProcessor.kt` → `PREPARING_CONTINUATION`
**Ajout case** :
```
COMPLETION_CONFIRMATION_REQUIRED:
  - Créer SystemMessage (envoyé à l'IA, mais qui s'affiche de façon simplifier dans l'UI "Demande de confirmation de fin envoyée". Il faut donc un type de message system spécifique pour qu'il soit traité de cette façon spécifique) (voir ci dessous)
  - Émettre AIEvent.ContinuationReady
```

---

## 6. Délai fermeture 5s pour AUTOMATION
**Fichier** : `Phase.kt`
**Ajout** : `AWAITING_SESSION_CLOSURE` dans enum

**Fichier** : `AIStateMachine.kt`
**Logique** : Quand `completed` confirmé → `Phase.AWAITING_SESSION_CLOSURE` au lieu de `Phase.CLOSED`

**Fichier** : `AIEventProcessor.kt`
**Ajout** :
```
Phase.AWAITING_SESSION_CLOSURE:
  - Job avec delay 5s
  - Si pauseActiveSession() : annuler Job, phaseBeforePause = AWAITING_SESSION_CLOSURE
  - Si resumeActiveSession() depuis AWAITING_SESSION_CLOSURE : relancer delay 5s
  - Après 5s : émettre SessionCompleted
```

---

## 7. Strings manquants
**Fichier** : `core/strings/sources/shared.xml`
**Ajouter** :
- `ai_automation_no_commands_guidance` : "Tu es en mode automation, tes réponses doivent contenir des requêtes de données ou des actions. Si tu as terminé, utilise le flag 'completed'."
- `ai_completion_confirmation_required` : "Le flag 'completed' signifie que TOUTE la demande initiale est terminée, pas juste une étape. Si c'était bien ton intention, renvoie un message avec 'completed: true'. Sinon, continue ton travail."
- Textes statuts pour SessionStatusBar (voir ci-dessous)

---

## 8. Composant SessionStatusBar
**Fichier** : Créer `core/ai/ui/components/SessionStatusBar.kt`
**Logique** :
```kotlin
@Composable
fun SessionStatusBar(phase: Phase, sessionType: SessionType) {
  Box(fillMaxWidth, padding 8dp, Center) {
    UI.Text(getStatusText(phase, sessionType), TextType.SMALL)
  }
}

fun getStatusText(phase, type): String {
  IDLE → "En attente" (CHAT) / "Prêt" (AUTO)
  CALLING_AI → "Appel IA..."
  EXECUTING_* → "Traitement..."
  WAITING_VALIDATION → "En attente de validation"
  PAUSED → "⏸ Session en pause"
  AWAITING_SESSION_CLOSURE → "⏱ Fermeture dans 5s..."
  CLOSED → "Session terminée"
  ...
}
```
-> utiliser le système i18n, pas de string en dur.
---

## 9. Intégration SessionStatusBar
**Fichier** : `core/ai/ui/screens/AIScreen.kt`
**Logique** : Ajouter `SessionStatusBar(aiState.phase, aiState.sessionType)` en bas de l'écran (toujours visible)
