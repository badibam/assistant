# Avancement UI Automations

## ✅ Phase 1 : Refactoring Architecture (TERMINÉ)

### Backend réactif
- **AISessionController.activeSessionId** : StateFlow exposé pour détecter changements de session
- **AIOrchestrator.activeSessionId** : Exposition publique du StateFlow
- Émission automatique lors activation/désactivation/éviction de sessions

### AIScreen.kt (composable pur, ~600 lignes)
- **Architecture** : Composable pur (pas Dialog) pour flexibilité maximale
- **Routing automatique** selon `session.type` :
  - `ChatMode` : Conversation interactive complète (fonctionnel)
  - `SeedMode` : Stub temporaire "Coming soon" (TODO Phase 2)
  - `AutomationMode` : Viewer read-only + bouton interrupt (fonctionnel)
- **3 headers différenciés** :
  - `ChatHeader` : Stop/Config/Stats/Close
  - `SeedHeader` : Config automation/Close (stub)
  - `AutomationHeader` : Interrupt/Close + statut temps réel

### ChatComponents.kt (~350 lignes)
Extraction composants partagés :
- `ChatMessageBubble` : Rendu messages avec communication modules/validation
- `ChatLoadingIndicator` : Indicateur avec bouton interrupt
- `SettingsMenuDialog` : Menu settings (coûts, validation)
- `SessionSettingsDialog` : Toggle validation session
- `SessionStatsDialog` : Affichage coûts détaillés

### AIFloatingChat.kt (750 → 186 lignes !)
- **Simplification drastique** : Wrapper Dialog autour de AIScreen
- **Détection automatique** éviction via `activeSessionId.collectAsState()`
- **Nouveau chat** : Affiche prompt + composer si pas de session active
- **Auto-switch** : Bascule vers AIScreen quand session activée

### Compilation
✅ Build successful (warnings variables unused uniquement)

---

## 🔄 Différences avec AUTOMATIONS.md

### Architecture modifiée (plus flexible)
**Specs initiales** : AIScreen comme Dialog intégré partout
**Implémentation** : AIScreen = composable pur + AIFloatingChat = wrapper Dialog

**Avantages** :
- SEED éditable fullscreen dans ZoneScreen (pas modal)
- AUTOMATION viewer peut être Dialog OU fullscreen selon contexte
- Réutilisable dans n'importe quel contexte

### Éviction bidirectionnelle simplifiée
**Specs initiales** : Position 1 absolue après éviction CHAT
**Implémentation** : Sélection intelligente par `scheduledExecutionTime` au démarrage de processNextInQueue

**Résultat** : Plus élégant, ordre naturel par ancienneté d'exécution prévue

---

## 📋 TODO Phase 2 : SEED Mode & Configuration

### 1. AutomationEditorFooter
Composant footer pour édition SEED :
- Message input avec RichComposer (enrichments possibles)
- Bouton "Configurer horaire" → ScheduleConfigEditor
- Bouton "Configurer déclencheurs" → TriggerSelector (stub)
- FormActions : Save/Cancel/Test (test = execute_manual)

### 2. ScheduleConfigEditor Dialog
Dialogue réutilisable pour 6 patterns :
- **DailyMultiple** : "Tous les jours à 9h, 14h, 18h"
- **WeeklySimple** : "Lundi, mercredi, vendredi à 9h"
- **MonthlyRecurrent** : "Le 15 de janvier, mars, juin à 10h"
- **WeeklyCustom** : "Lundi 9h, mercredi 14h, vendredi 17h"
- **YearlyRecurrent** : "Tous les 1er janvier et 25 décembre à 8h"
- **SpecificDates** : "Le 15 mars 2025 à 14h30 et le 20 avril 2025 à 10h"

Interface :
- Sélecteur de pattern (tabs ou dropdown)
- UI adaptée selon pattern sélectionné
- Preview label : "Quotidien 9h" ou "Hebdomadaire Lun/Mer/Ven"

### 3. SeedMode implémentation complète
Remplacer stub actuel par :
- Affichage message SEED (read-only ou éditable ?)
- AutomationEditorFooter en bas
- Sauvegarde via `AutomationService.update()`
- **Isolation complète** : Pas d'appel `requestSessionControl()`, juste `loadSession()`

---

## 📋 TODO Phase 3 : ZoneScreen Integration

### Onglet Automations dans ZoneScreen
Pattern similaire à Tools :
```kotlin
// ZoneScreen.kt - Ajouter state
var selectedTab by remember { mutableStateOf("tools") }

// Tabs: "Outils" | "Automations"
// Si tab = "automations" → AutomationsList
```

### AutomationCard
Card compacte pour liste :
```
┌─────────────────────────────────────┐
│ [📊] Rapport quotidien santé    [⚙️]│
│ Prochain : dans 3h                  │
└─────────────────────────────────────┘
```

Éléments :
- Icône + nom
- Statut déduit : "Prochain : dans 3h" | "En cours" | "Manuel" | "Selon déclencheurs"
- Bouton ⚙️ édition

Interactions :
- **Clic simple** → AutomationDetailsScreen
- **Bouton ⚙️** → AIScreen mode SEED (fullscreen, pas Dialog)
- **Long press** → Menu contextuel (éditer, dupliquer, supprimer, activer/désactiver)

### Dialogue création automation
Dialogue compact avant ouverture SEED :
- Champ : Nom
- Sélecteur : Icône
- Dropdown : Provider IA
- Bouton "Continuer" → Ouvre AIScreen SEED fullscreen

---

## 📋 TODO Phase 4 : AutomationDetailsScreen

Screen de détails avec :
- **Configuration** : Schedule verbalisé, provider, statut enabled/disabled
- **Message seed** : Affichage du message template
- **Statistiques** :
  - Nombre exécutions totales
  - Taux succès/échecs
  - Durée moyenne exécution
  - Tokens consommés (total + moyenne)
  - Coût estimé (total + moyenne)
- **Historique** : Liste sessions d'exécution (pagination)
  - Clic item → AIScreen mode AUTOMATION (Dialog viewer)
- **Actions** : Exécuter maintenant, Éditer, Dupliquer, Supprimer

---

## 📋 TODO Phase 5 : Strings & Polishing

### Strings à ajouter
Préfixes suggérés :
- `automation_*` : Noms, labels, actions automations
- `schedule_*` : Labels patterns horaires
- `trigger_*` : Labels déclencheurs (Phase future)

Exemples nécessaires :
```
automation_display_name = "Automation"
automation_create = "Créer une automation"
automation_edit = "Éditer l'automation"
automation_execute_now = "Exécuter maintenant"
automation_next_execution = "Prochain déclenchement : %1$s"
automation_status_running = "En cours"
automation_status_manual = "Manuel"
automation_status_triggered = "Selon déclencheurs"

schedule_daily = "Quotidien"
schedule_weekly = "Hebdomadaire"
schedule_monthly = "Mensuel"
schedule_yearly = "Annuel"
schedule_specific_dates = "Dates spécifiques"
schedule_not_configured = "Non configuré"
```

### Tests manuels
- [ ] CHAT → AUTOMATION éviction (changement UI automatique)
- [ ] Création automation complète (nom → SEED → schedule → save)
- [ ] Exécution manuelle depuis details screen
- [ ] Édition message SEED pendant CHAT actif (isolation)
- [ ] Viewer AUTOMATION avec interrupt

---

## 🎯 Priorités

**Phase 2 immédiate** : SEED mode fonctionnel = ScheduleConfigEditor + AutomationEditorFooter
**Phase 3** : ZoneScreen integration (création workflow complet)
**Phase 4** : Details screen (stats + historique)
**Phase 5** : Strings + tests

---

**Status** : Compilation OK, architecture robuste, prêt pour Phase 2
