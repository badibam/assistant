# TODO - Prochaines étapes après migration UI

## ✅ MIGRATION UI COMPLÈTE 
- [x] **Système UI unifié** avec `UI.*` et support spacing + alignement par défaut
- [x] **Écrans migrés** : MainScreen, ZoneScreen, CreateZoneScreen vers nouveaux UI.*
- [x] **Long clicks fonctionnels** pour ZoneCard et ToolCard
- [x] **Layout optimisé** ZoneScreen avec header horizontal (← Titre +)
- [x] **Compilation réussie** et app fonctionnelle

## 🛠️ INTÉGRATION TOOL TYPES (Priorité 1)  
- [ ] **Étendre ToolTypeContract** pour définir le contenu des zones libres par DisplayMode
- [ ] **Implémenter dans TrackingToolType** :
  - `getContentForDisplayMode(LINE)` → boutons "+" etc.
  - `getContentForDisplayMode(CONDENSED)` → dernière valeur + boutons
  - `getContentForDisplayMode(EXTENDED)` → valeur + champs + boutons
- [ ] **Remplacer tous les TODO** dans UI.ToolCard() par appels tool type
- [ ] **Connecter icônes** via ThemeIconManager + tool types

## 📝 LAYOUT AVANCÉ (Priorité 2)
- [ ] **Implémenter weight** pour répartition d'espace (si nécessaire)
- [ ] **Ajouter arrangement** pour alignement spécifique (Start/End/SpaceBetween)
- [ ] **Tester composants avancés** : Dialog, Toast, Snackbar

## 🔧 FONCTIONNALITÉS MANQUANTES
- [ ] **Navigation outils** : Implémenter onClick pour ouvrir les écrans d'outils
- [ ] **Ajout d'outils** : Connecter le bouton "+" pour afficher les types disponibles
- [ ] **Configuration outils** : Connecter onLongClick pour configuration
- [ ] **Formulaire CreateZone** : Connecter au coordinator pour création réelle

## 🎯 VALIDATION & TESTS
- [ ] **Test navigation complète** : Main → Zone → Outil → Config
- [ ] **Test gestuelles** : Click, long click, retour
- [ ] **Vérifier DisplayModes** : tailles correctes ICON/MINIMAL/LINE/etc.
- [ ] **Validation theme** : Vérifier que tous les composants respectent DefaultTheme

---

**État actuel** : Système UI complet et fonctionnel, écrans de base migrés.  
**Prochaine étape** : Intégrer les tool types pour affichage dynamique du contenu.
