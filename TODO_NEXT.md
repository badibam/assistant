# TODO - Prochaines étapes après migration UI

## ✅ MIGRATION UI COMPLÈTE 
- [x] **Système UI unifié** avec `UI.*` et support spacing + alignement par défaut
- [x] **Écrans migrés** : MainScreen, ZoneScreen, CreateZoneScreen vers nouveaux UI.*
- [x] **Long clicks fonctionnels** pour ZoneCard et ToolCard
- [x] **Layout optimisé** ZoneScreen avec header horizontal (← Titre +)
- [x] **Compilation réussie** et app fonctionnelle

## 🚨 TRATRACKINGCONFIGSCREEN - FONCTIONNALITÉS MANQUANTES (Priorité 1)
En migrant vers le nouveau système UI, des fonctionnalités importantes ont été perdues :

### **GESTION DES ITEMS**
- [ ] **Dialog AddItemForm** pour ajouter/éditer des items de tracking
- [ ] **Liste des items créés** avec leurs propriétés (nom, valeur défaut, unité, etc.)
- [ ] **Édition inline** des items existants dans la liste
- [ ] **Suppression individuelle** d'items avec confirmation

### **GESTION DES ICÔNES** 
- [ ] **Sélection d'icône** parmi liste prédéfinie (IconOption)
- [ ] **Aperçu visuel** de l'icône sélectionnée
- [ ] **Intégration** avec ThemeIconManager

### **RÉORGANISATION & UX**
- [ ] **Boutons ↑↓** pour réordonner les items 
- [ ] **Warning changement type** si items existants (pendingTrackingType)
- [ ] **States manquants** : showAddItem, editingItemIndex, etc.

### **CONFIGURATIONS SPÉCIFIQUES**
- [ ] **Config par type** : numeric (min/max/unité), scale (plages), choice (options)
- [ ] **Propriétés d'items** différentes selon le type de tracking
- [ ] **Validation** des champs obligatoires selon le contexte

## 🛠️ INTÉGRATION TOOL TYPES (Priorité 2)  
- [ ] **Étendre ToolTypeContract** pour définir le contenu des zones libres par DisplayMode
- [ ] **Implémenter dans TrackingToolType** :
  - `getContentForDisplayMode(LINE)`
  - `getContentForDisplayMode(CONDENSED)`
  - `getContentForDisplayMode(EXTENDED)`
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
