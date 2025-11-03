# AutomationScreen - Décisions d'implémentation

## 1. Corrections système préalables (COMPLÉTÉES)

### 1.1 Système de couleurs Material Theme
**Problème:** Incohérences dans l'usage des couleurs sémantiques

**Décisions:**
- `tertiary` (orange) = warning
  - Light: `#F5A47A`, Dark: `#FFB88C`
- `secondary` (mauve décoratif) = accent cohérent avec thème violet
  - Light: `#B08BBE`, Dark: `#C9A8D8`
- `error` (rouge) = erreurs/destructif (déjà existant)
- `primary` (bleu) = succès/actions importantes

**Mapping pour automation endReason:**
- `primary`: COMPLETED
- `tertiary`: LIMIT_REACHED, NETWORK_ERROR, SUSPENDED, null
- `error`: ERROR, CANCELLED, TIMEOUT

**Fichiers modifiés:**
- `themes/default/DefaultTheme.kt`: ColorSchemes light/dark, Snackbar containerColor

### 1.2 Renommage ButtonType.SECONDARY → DANGER
**Rationale:** SECONDARY utilisait errorContainer (rouge) pour actions destructives

**Changements:**
- Enum: `PRIMARY, DANGER, DEFAULT`
- DANGER = actions destructives (DELETE, STOP)
- DEFAULT = actions neutres (CANCEL, BACK, REFRESH, VIEW)

**Fichiers modifiés:**
- `core/ui/UITypes.kt`
- `themes/default/DefaultTheme.kt`
- Usages: DataSettingsScreen, TranscribableTextField, AutomationCard

### 1.3 Ajout ButtonAction.VIEW
**Usage:** Navigation vers détails/historique

**Caractéristiques:**
- Icône: 👁 (eye)
- Type: DEFAULT
- String: `action_view` = "Voir"

**Fichiers:**
- `core/ui/UITypes.kt`: enum ButtonAction
- `themes/default/DefaultTheme.kt`: mappings type/text/icon
- `core/strings/sources/shared.xml`: string action_view

### 1.4 AIState enrichi avec automationId
**Rationale:** Identifier quelle automation est en cours pour UI temps réel

**Changement:**
```kotlin
data class AIState(
    val sessionId: String?,
    val phase: Phase,
    val sessionType: SessionType?,
    val automationId: String? = null,  // NOUVEAU
    // ... autres champs
)
```

**Usage:**
```kotlin
val aiState by AIOrchestrator.currentState.collectAsState()
val isThisAutomationRunning = aiState.sessionType == SessionType.AUTOMATION &&
                               aiState.automationId == automationId
```

**Fichiers:**
- `core/ai/domain/AIState.kt`
- `core/ai/state/AIStateRepository.kt`: entityToState()

### 1.5 Strings i18n SessionEndReason
**Ajout strings:**
```xml
ai_end_reason_completed, ai_end_reason_limit_reached,
ai_end_reason_timeout, ai_end_reason_error,
ai_end_reason_cancelled, ai_end_reason_interrupted,
ai_end_reason_network_error, ai_end_reason_suspended
```

**Strings Phase existantes:** `ai_phase_*` (déjà présentes)

## 2. Architecture AutomationScreen

### 2.1 Objectif
Écran dédié pour afficher et gérer l'historique des exécutions d'une automation spécifique.

### 2.2 Navigation
**Depuis:** AutomationCard via nouveau ButtonAction.VIEW
**Signature:**
```kotlin
@Composable
fun AutomationScreen(
    automationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExecution: (sessionId: String) -> Unit
)
```

### 2.3 Structure écran (top → bottom)

#### PageHeader
- Titre: Nom automation
- Icône: automation.icon
- leftButton: BACK
- Pas de rightButton (config ailleurs)

#### Prochaine exécution (premier item)
**Affichage:** Card distincte si `nextExecution != null`
**Données:**
```kotlin
data class NextExecution(
    val scheduledTime: Long,
    val message: String  // Ex: "Prochaine exécution: dans 2h"
)
```
**Calcul:** `AutomationScheduler.getNextExecutionForAutomation(automationId)`
- Tenir compte `isActive = false` → pas de prochaine exécution
- Fonction à créer dans AutomationScheduler (logique similaire à getNextSession)

#### Filtres (Row - 3 colonnes)
**Pattern TrackingHistory ligne 316-381:**
1. **PeriodFilterType dropdown** (weight 1f)
   - Options: ALL, HOUR, DAY, WEEK, MONTH, YEAR
   - State: `periodFilter`, `currentPeriod`
2. **Limit dropdown** (weight 1f)
   - Options: 10, 25, 100, 250, 1000
   - State: `entriesLimit`
3. **Refresh button** (icon only)
   - Affiche "..." si loading

#### SinglePeriodSelector
**Condition:** `if (periodFilter != ALL)`
**State:** `currentPeriod` (Period)
**Pattern:** TrackingHistory ligne 384-392

#### Liste exécutions (Column avec cards)
**Source données:** Query `ai_sessions.list` avec params:
```kotlin
mapOf(
    "automationId" to automationId,
    "type" to "AUTOMATION",
    "limit" to entriesLimit,
    "page" to currentPage,
    "startTime" to periodStart,  // si period != ALL
    "endTime" to periodEnd        // si period != ALL
)
```

**Card structure (2 colonnes):**
```
┌─────────────────────────────────┐
│ Prévue: 15/01/2025 14:00       │ Phase: COMPLETED
│ Lancée: 15/01/2025 14:02       │ EndReason: COMPLETED
├─────────────────────────────────┤
│ Durée: 2m 34s                  │ 3 roundtrips
├─────────────────────────────────┤
│ 45,231 tokens                  │ $0.023
├─────────────────────────────────┤
│         [VIEW] [pas de delete]  │
└─────────────────────────────────┘
```

**Données card:**
```kotlin
data class ExecutionSummary(
    val sessionId: String,
    val scheduledExecutionTime: Long?,  // Date prévue
    val createdAt: Long,                // Date lancée
    val phase: Phase,                   // Phase actuelle/finale
    val endReason: SessionEndReason?,   // Raison arrêt
    val duration: Long,                 // lastActivity - createdAt
    val totalRoundtrips: Int,
    val tokensUsed: String,             // JSON → parser
    val cost: Double                    // Calculé via SessionCostCalculator
)
```

**Formats affichage:**
- Dates: `DateUtils.formatDateTime(timestamp)` → "15/01/2025 14:30"
- Durée: `formatDuration(duration)` → "2m 34s"
- Phase: `s.shared("ai_phase_${phase.name.lowercase()}")`
- EndReason: `s.shared("ai_end_reason_${endReason.name.lowercase()}")`
- Tokens: Format numérique avec séparateurs (ex: "45,231")
- Coût: `SessionCostCalculator.calculateSessionCost(tokensUsedJson)` → "$0.023"

**Couleur phase/endReason:**
```kotlin
val statusColor = when {
    endReason == COMPLETED -> MaterialTheme.colorScheme.primary
    endReason in listOf(LIMIT_REACHED, NETWORK_ERROR, SUSPENDED, null) ->
        MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
```

**Actions:** ButtonAction.VIEW uniquement (read-only, pas de delete)

#### Pagination
**Pattern:** TrackingHistory ligne 472-479
```kotlin
if (totalPages > 1) {
    UI.Pagination(
        currentPage = currentPage,
        totalPages = totalPages,
        onPageChange = { newPage -> currentPage = newPage }
    )
}
```

### 2.4 Updates temps réel

#### Pattern A: DataChangeNotifier (liste complète)
**Ajout event:**
```kotlin
sealed class DataChangeEvent {
    data class AISessionsChanged(val automationId: String?) : DataChangeEvent()
}

fun notifyAISessionsChanged(automationId: String? = null)
```

**Usage AutomationScreen:**
```kotlin
LaunchedEffect(automationId) {
    DataChangeNotifier.changes.collect { event ->
        when (event) {
            is DataChangeEvent.AISessionsChanged -> {
                if (event.automationId == null || event.automationId == automationId) {
                    refreshTrigger++ // Reload list
                }
            }
        }
    }
}
```

**Appel:** AIOrchestrator/AIEventProcessor après création/fin session

#### Pattern B: AIOrchestrator.currentState (session active)
**Usage:**
```kotlin
val aiState by AIOrchestrator.currentState.collectAsState()

// Highlight active execution in list
val isActive = aiState.sessionType == SessionType.AUTOMATION &&
               aiState.automationId == automationId
```

### 2.5 Service requis

#### AISessionService - nouvelle opération
```kotlin
"list_sessions_for_automation" -> listSessionsForAutomation(params)
```

**Params:**
```kotlin
{
    "automationId": String,
    "limit": Int,
    "page": Int,
    "startTime": Long?,  // Optional
    "endTime": Long?     // Optional
}
```

**Return:**
```kotlin
{
    "sessions": List<Map<String, Any>>,
    "pagination": {
        "currentPage": Int,
        "totalPages": Int,
        "totalEntries": Int
    }
}
```

#### AutomationScheduler - nouvelle fonction
```kotlin
suspend fun getNextExecutionForAutomation(automationId: String): NextExecution?
```

**Logique:**
1. Charger automation (vérifier isActive)
2. Si isActive = false → return null
3. Calculer prochaine exécution selon schedule config
4. Return NextExecution ou null

## 3. Architecture ExecutionDetailScreen

### 3.1 Objectif
Afficher le déroulé complet d'une exécution (messages conversation IA).

### 3.2 Signature
```kotlin
@Composable
fun ExecutionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit
)
```

### 3.3 Structure
**Simple réutilisation composants existants:**

```kotlin
Column {
    // Header
    UI.PageHeader(
        title = "Exécution",  // ou date/heure
        leftButton = ButtonAction.BACK,
        onLeftClick = onNavigateBack
    )

    // Messages (pattern AIScreen)
    val messages by remember {
        AIOrchestrator.observeMessages(sessionId)
    }.collectAsState(initial = emptyList())

    LazyColumn {
        items(messages) { message ->
            ChatMessageBubble(
                message = message,
                isLatestAI = false  // Read-only, pas d'interactions
            )
        }
    }
}
```

**Pas de:**
- RichComposer (lecture seule)
- Actions sur messages
- Validation/communication modules

## 4. Fichiers à créer/modifier

### À créer
- `core/ai/ui/automation/AutomationScreen.kt`
- `core/ai/ui/automation/ExecutionDetailScreen.kt`
- `core/ai/ui/automation/ExecutionCard.kt` (card 2 colonnes)

### À modifier
- `core/utils/DataChangeNotifier.kt`: event AISessionsChanged
- `core/ai/services/AISessionService.kt`: list_sessions_for_automation
- `core/ai/scheduling/AutomationScheduler.kt`: getNextExecutionForAutomation()
- `core/ai/ui/automation/AutomationCard.kt`: ButtonAction.VIEW

## 5. Helpers à créer

### FormatUtils.kt
```kotlin
fun formatDuration(durationMs: Long): String
// Ex: 153000L → "2m 33s"

fun formatTokenCount(count: Int): String
// Ex: 45231 → "45,231"
```

### PhaseUtils.kt (extension functions)
```kotlin
fun Phase.toDisplayString(s: StringsContext): String
// Mapping Phase → string i18n

fun SessionEndReason.toDisplayString(s: StringsContext): String
// Mapping EndReason → string i18n

fun SessionEndReason.toColor(colorScheme: ColorScheme): Color
// Mapping EndReason → couleur
```

## 6. Patterns réutilisés

### TrackingHistory (référence complète)
- Filtres période + limite: lignes 316-381
- SinglePeriodSelector: lignes 384-392
- Pagination: lignes 472-479
- LaunchedEffect pour reload: ligne 307
- Reset page on filter change: ligne 302

### AIScreen (référence messages)
- observeMessages: ligne 1316
- ChatMessageBubble rendering
- LazyColumn pattern

### SessionCostCalculator
```kotlin
fun calculateSessionCost(tokensUsedJson: String): Double
```

## 7. Checklist implémentation

- [ ] Ajouter DataChangeEvent.AISessionsChanged
- [ ] Implémenter AISessionService.list_sessions_for_automation
- [ ] Implémenter AutomationScheduler.getNextExecutionForAutomation
- [ ] Créer FormatUtils helpers
- [ ] Créer PhaseUtils extensions
- [ ] Créer ExecutionCard composable
- [ ] Implémenter AutomationScreen (filtres + liste + pagination)
- [ ] Implémenter ExecutionDetailScreen (messages read-only)
- [ ] Ajouter ButtonAction.VIEW dans AutomationCard
- [ ] Tests compilation
- [ ] Tests UI (filtres, navigation, temps réel)

---

**Priorité:** Implémentation séquentielle (services → helpers → UI)
**Principe:** Read-only, historique immuable, pas de suppression d'exécutions
