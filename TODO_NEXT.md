# TODO - Prochaines étapes après refactor UI

## 🔗 INTÉGRATION & COMPILATION (Priorité 1)
- [x] **Ajouter imports manquants** dans tous les fichiers ui/core/
- [x] **Corriger erreurs de compilation** (probablement des références manquantes)  
- [x] **Connecter entités réelles** : remplacer `zone: Any` par `zone: Zone`, `tool: Any` par `tool: ToolInstance`
- [ ] **Tester rendu basique** : vérifier qu'un `UI.Button(ButtonType.SAVE)` s'affiche
- [ ] **Tester changement thème** : `CurrentTheme.switchTheme()` fonctionne

## 🛠️ INTÉGRATION TOOL TYPES (Priorité 2)  
- [ ] **Étendre ToolTypeContract** pour définir le contenu des zones libres par DisplayMode
- [ ] **Implémenter dans TrackingToolType** :
  - `getContentForDisplayMode(LINE)` → boutons "+" etc.
  - `getContentForDisplayMode(CONDENSED)` → dernière valeur + boutons
  - `getContentForDisplayMode(EXTENDED)` → valeur + champs + boutons
- [ ] **Remplacer tous les TODO** dans UI.ToolCard() par appels tool type
- [ ] **Connecter icônes** via ThemeIconManager + tool types

## 📝 SYSTÈME DE FORMULAIRES (Si validé)
- [ ] **Décider** : FormField/FormSelection/FormActions à implémenter ?
- [ ] **ValidationRule** : implémenter la validation intégrée
- [ ] **Intégrer dans ThemeContract** et DefaultTheme

## 🧪 TESTS & MIGRATION
- [ ] **Tester tous les composants** : Button, TextField, Text, Card, Dialog, Toast, Snackbar
- [ ] **Vérifier DisplayModes** : tailles correctes selon ICON/MINIMAL/LINE/etc.
- [ ] **Migrer écrans existants** : MainScreen, ZoneScreen, CreateZoneScreen avec nouveaux UI.*

## 🎯 VALIDATION FINALE
- [ ] **Compilation réussie** sans erreurs
- [ ] **Affichage correct** des composants avec thème par défaut
- [ ] **Architecture respectée** : contenu (UI.kt) séparé de l'apparence (thème)
- [ ] **DisplayModes fonctionnels** avec zones libres définies par tool types

---

**État actuel** : Architecture complète créée, prête pour intégration et tests.  
**Prochaine étape** : Résoudre compilation + imports pour tester le système.
