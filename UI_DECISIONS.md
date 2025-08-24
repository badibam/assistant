# Décisions UI - Synthèse exhaustive

## 1. ARCHITECTURE DES COMPOSANTS UI

### Principe de base validé
- **UI wrapper** : `UI.Component()` → délégation au thème actuel
- **Types sémantiques** : `ButtonType.SAVE`, `TextType.TITLE`, etc.
- **Pas de paramètre `semantic`** : supprimé, les types précis suffisent

### Paramètres retenus (métier, pas apparence)
- **Size** : `SMALL, MEDIUM, LARGE` (contrainte d'espace)
- **State** : `NORMAL, LOADING, DISABLED, ERROR, READONLY` (état fonctionnel)
- **Types métier précis** plutôt que priority générique

### Liste finale des composants + paramètres

#### Layout (sans paramètres)
```kotlin
UI.Column(content)
UI.Row(content) 
UI.Box(content)
UI.Spacer(modifier)
```

#### Interactive
```kotlin
UI.Button(
    type: ButtonType,           // SAVE, DELETE, CANCEL, ADD, BACK, CONFIRM_DELETE
    size: Size = MEDIUM,        // SMALL, MEDIUM, LARGE
    state: InteractiveState = NORMAL,  // NORMAL, LOADING, DISABLED
    onClick, content
)

UI.TextField(
    type: TextFieldType,        // TEXT, NUMERIC, SEARCH, PASSWORD
    state: InputState = NORMAL, // NORMAL, ERROR, READONLY
    value, onChange, placeholder
)
```

#### Display
```kotlin
UI.Text(
    type: TextType,             // TITLE, SUBTITLE, BODY, CAPTION, LABEL, ERROR, WARNING
    text
)

UI.Card(
    type: CardType,             // ZONE, TOOL, DATA, SYSTEM
    size: Size = MEDIUM,        // SMALL, MEDIUM, LARGE
    content
)
```

#### System
```kotlin
UI.LoadingIndicator(size: Size = MEDIUM)

UI.Dialog(
    type: DialogType,           // CONFIGURE, CREATE, EDIT, CONFIRM, DANGER, SELECTION, INFO
    onConfirm: () -> Unit,      // Action principale
    onCancel: () -> Unit = { }, // Action secondaire
    content
)
```

## 2. DISPLAY MODES DES TOOL INSTANCES

### Nouveaux modes d'affichage
- **1/4×1/4** → `ICON` (icône seule)
- **1/2×1/4** → `MINIMAL` (icône + titre côte à côte)
- **1×1/4** → `LINE` (icône + titre à gauche, contenu libre droite)
- **1/2×1/2** → `CONDENSED` (icône + titre en haut, reste libre dessous)
- **1×1/2** → `EXTENDED` (icône + titre en haut, zone libre dessous)
- **1×1** → `SQUARE` (nouveau - icône + titre en haut, grande zone libre)
- **1×∞** → `FULL` (icône + titre en haut, zone libre infinie)

### Stratégies de layout conservées
- `HORIZONTAL_SPLIT` et `VERTICAL_SPLIT` gardées pour flexibilité future

## 3. DIALOG TYPES AVEC STRUCTURE INTÉGRÉE

### DialogType encode la logique ET l'interface
```kotlin
CONFIGURE → boutons "Valider" + "Annuler"
CREATE → boutons "Créer" + "Annuler"  
EDIT → boutons "Sauvegarder" + "Annuler"
DANGER → boutons "Supprimer" + "Annuler" (rouge)
```

### Textes des boutons
- Le **thème récupère les strings** correspondantes au moment de créer les boutons
- Mapping automatique `DialogType` → `stringResource()`

## 4. COMPOSANTS SPÉCIFIQUES vs GÉNÉRIQUES

### Décision : Composants spécifiques pour éléments métier complexes
```kotlin
// Spécifiques (logique métier complexe)
UI.ZoneCard(zone, onClick)
UI.ToolCard(tool, displayMode, onClick) 
UI.TrackingInput(config, onSave)

// Génériques (bases simples)
UI.Card(type, content)
UI.Button(type, onClick)
```

## 5. ZONES : CARDS vs BUTTONS vs LIST ITEMS

### Décision : Cards pour les zones
**Justification :** 
- Contenu riche (nom + description + stats)
- Information ET action
- Aspect "dashboard" approprié
- Peu d'éléments (quelques zones)

## 6. CORRECTION BUG ZONE NAME

### Problème résolu : Nom de zone hardcodé "Zone"
**Solution :** Récupération via coordinateur :
```kotlin
// Get tool instance info via coordinator
val toolInstanceResult = coordinator.processUserAction("get->tool_instance", mapOf("tool_instance_id" to toolInstanceId))
val zoneId = toolInstanceData?.get("zone_id") as? String
val zoneResult = coordinator.processUserAction("get->zone", mapOf("zone_id" to zoneId))
val zoneName = zoneData?.get("name") as? String
```

### Principe validé : Pas de fallbacks hardcodés
Échec propre avec messages d'erreur explicites au lieu de valeurs par défaut aléatoires.

## 7. FORMULAIRES UNIFIÉS

### Décision : Composants de form universels simples
```kotlin
UI.FormField(label, value, onChange, type = TEXT)  // TEXT, NUMERIC, PASSWORD
UI.FormSelection(label, options, selected, onSelect)
UI.FormActions { UI.Button(type = SAVE) { } }
```

### Avantages
- Simple et flexible
- Affichage conditionnel naturel
- Cohérence visuelle garantie

## 8. DIALOG vs INLINE vs PAGE

### Critères de choix validés
- **Dialog :** Action rapide (< 4 champs), contexte à conserver, critique
- **Inline :** Modification directe dans contexte, workflow fluide  
- **Page :** Formulaire complexe (> 4 champs), contenu riche, état important

## 9. SIMPLIFICATION PAGE DE CONFIG

### Structure finale validée

#### Sections logiques
```kotlin
// Section 1 : Identité
UI.FormField("Nom", name) { name = it }
UI.FormField("Description", description) { description = it }

// Section 2 : Comportement  
UI.FormSelection("Type de suivi", listOf("Numérique", "Durée"), type) { type = it }
UI.FormSelection("Gestion", listOf("Manuel", "IA", "Collaboratif"), management) { management = it }
UI.FormSelection("Validation config par IA", listOf("Activée", "Désactivée"), configValidation) { }
UI.FormSelection("Validation données par IA", listOf("Activée", "Désactivée"), dataValidation) { }

// Section 3 : Affichage
UI.FormSelection("Mode d'affichage", displayModes, displayMode) { displayMode = it }
UI.FormField("Icône", iconName, readonly = true, onClick = { showIconDialog = true })
```

#### Champs conditionnels groupés
```kotlin
if (type == "numeric") {
    UI.FormSelection("Mode des items", listOf("Libre", "Prédéfini", "Mixte"), itemMode) { }
    UI.FormSelection("Afficher valeur", listOf("Oui", "Non"), showValue) { }
}
if (type == "duration") {
    UI.FormSelection("Commutation auto", listOf("Activée", "Désactivée"), autoSwitch) { }
}
```

#### Sélection d'icône simple
```kotlin
UI.FormField("Icône", iconName, readonly = true) { showIconDialog = true }

// Dialog simple
if (showIconDialog) {
    UI.Dialog(type = SELECTION) {
        availableIcons.forEach { icon ->
            UI.Button(
                type = if (icon == iconName) PRIMARY else GHOST,
                onClick = { iconName = icon; showIconDialog = false }
            ) { UI.Text(icon) }
        }
    }
}
```

#### Layout final
- Colonne unique scrollable  
- Header avec titre + bouton retour
- FormActions en bas avec boutons SAVE/CANCEL

## 10. ENUMS PARTAGÉS

```kotlin
enum class Size { SMALL, MEDIUM, LARGE }
enum class InteractiveState { NORMAL, LOADING, DISABLED }
enum class InputState { NORMAL, ERROR, READONLY }
enum class DialogType { CONFIGURE, CREATE, EDIT, CONFIRM, DANGER, SELECTION, INFO }
enum class ButtonType { SAVE, DELETE, CANCEL, ADD, BACK, CONFIRM_DELETE, PRIMARY, GHOST }
enum class TextType { TITLE, SUBTITLE, BODY, CAPTION, LABEL, ERROR, WARNING }
enum class CardType { ZONE, TOOL, DATA, SYSTEM }
enum class DisplayMode { ICON, MINIMAL, LINE, CONDENSED, EXTENDED, SQUARE, FULL }
```

---

**Document créé le :** 2025-01-25  
**Status :** Décisions validées, prêtes pour implémentation