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
- [x] **Dialog AddItemForm** pour ajouter/éditer des items de tracking
- [x] **Liste des items créés** avec leurs propriétés (nom, valeur défaut, unité, etc.)
- [x] **Édition inline** des items existants dans la liste
- [ ] **Suppression individuelle** d'items avec confirmation

### **GESTION DES ICÔNES** 
- [x] **Sélection d'icône** parmi liste prédéfinie (IconOption)
- [x] **Aperçu visuel** de l'icône sélectionnée
- [x] **Intégration** avec ThemeIconManager

### **RÉORGANISATION & UX**
- [x] **Boutons ↑↓** pour réordonner les items 
- [x] **Warning changement type** si items existants (pendingTrackingType)
- [x] **States manquants** : showAddItem, editingItemIndex, etc.

### **CONFIGURATIONS SPÉCIFIQUES**
- [x] **Config par type** : numeric etc.
- [ ] **Propriétés d'items** différentes selon le type de tracking
- [x] **Validation** des champs obligatoires selon le contexte

## 🛠️ INTÉGRATION TOOL TYPES (Priorité 2)  
- [ ] **Étendre ToolTypeContract** pour définir le contenu des zones libres par DisplayMode
- [ ] **Implémenter dans TrackingToolType** :
  - `getContentForDisplayMode(LINE)`
  - `getContentForDisplayMode(CONDENSED)`
  - `getContentForDisplayMode(EXTENDED)`
  - `getContentForDisplayMode(SQUARE)`
  - `getContentForDisplayMode(FULL)`
- [ ] **Remplacer tous les TODO** dans UI.ToolCard() par appels tool type
- [ ] **Connecter icônes** via ThemeIconManager + tool types



## 🔧 FONCTIONNALITÉS MANQUANTES
- [ ] **Navigation outils** : Implémenter onClick pour ouvrir les écrans d'outils
- [ ] **Ajout d'outils** : Connecter le bouton "+" pour afficher les types disponibles
- [x] **Configuration outils** : Connecter onLongClick pour configuration
- [?] **Formulaire CreateZone** : Connecter au coordinator pour création réelle

## 🎯 VALIDATION & TESTS
- [ ] **Test navigation complète** : Main → Zone → Outil → Config
- [ ] **Test gestuelles** : Click, long click, retour
- [ ] **Vérifier DisplayModes** : tailles correctes ICON/MINIMAL/LINE/etc.
- [ ] **Validation theme** : Vérifier que tous les composants respectent DefaultTheme

---

**État actuel** : Système UI complet et fonctionnel, écrans de base migrés.  
**Prochaine étape** : Intégrer les tool types pour affichage dynamique du contenu.





/src/main/java/com/assistant/core/tools/ToolTypeContract.kt with 1 addition
       88        fun getUsageScreen(
       89            toolInstanceId: String,
       90            configJson: String,
       91 +          zoneName: String,
       92            onNavigateBack: () -> Unit,
       93            onLongClick: () -> Unit
       94        )

