# Décisions UI - Architecture Finale

## 1. ARCHITECTURE DES COMPOSANTS UI

### Principe fondamental
- **UI wrapper** : `UI.Component()` → délégation au thème actuel
- **Types sémantiques précis** : `ButtonType.SAVE`, `TextType.TITLE`, etc.
- **Paramètres métier uniquement** : taille, état, validation

### Système d'états unifié
```kotlin
enum class ComponentState { 
    NORMAL,     // État standard
    LOADING,    // Traitement en cours
    DISABLED,   // Non interactif
    ERROR,      // Erreur de validation/système
    READONLY,   // Lecture seule
    SUCCESS     // Feedback positif
}
```

### Tailles standardisées
```kotlin
enum class Size { XS, S, M, L, XL, XXL }
```

### Validation intégrée
```kotlin
enum class ValidationRule { 
    NONE, EMAIL, NUMERIC, REQUIRED, MIN_LENGTH, MAX_LENGTH
}
```

## 2. COMPOSANTS CORE

### Layout
```kotlin
UI.Column(content)
UI.Row(content) 
UI.Box(content)
UI.Spacer(modifier)
```

### Interactive
```kotlin
UI.Button(
    type: ButtonType,                    // SAVE, DELETE, CANCEL, ADD, BACK, CONFIRM_DELETE
    size: Size = M,
    state: ComponentState = NORMAL,
    contentDescription: String? = null,   // Accessibilité
    onClick, content
)

UI.TextField(
    type: TextFieldType,                 // TEXT, NUMERIC, SEARCH, PASSWORD
    state: ComponentState = NORMAL,
    validation: ValidationRule = NONE,   // Validation intégrée
    value, onChange, placeholder,
    contentDescription: String? = null
)
```

### Display
```kotlin
UI.Text(
    type: TextType,                      // TITLE, SUBTITLE, BODY, CAPTION, LABEL, ERROR, WARNING
    text,
    contentDescription: String? = null
)

UI.Card(
    type: CardType,                      // Types ajoutés selon besoins
    size: Size = M,
    content
)
```

### Feedback System
```kotlin
UI.Toast(
    type: FeedbackType,                  // SUCCESS, ERROR, WARNING, INFO
    message: String,
    duration: Duration = SHORT
)

UI.Snackbar(
    type: FeedbackType,
    message: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
)
```

### System
```kotlin
UI.LoadingIndicator(size: Size = M)

UI.Dialog(
    type: DialogType,                    // CONFIGURE, CREATE, EDIT, CONFIRM, DANGER, SELECTION, INFO
    onConfirm: () -> Unit,
    onCancel: () -> Unit = { },
    content
)
```

## 3. COMPOSANTS SPÉCIALISÉS

### Tool Instance Display Modes
```kotlin
enum class DisplayMode {
    ICON,       // 1/4×1/4 - icône seule
    MINIMAL,    // 1/2×1/4 - icône + titre côte à côte
    LINE,       // 1×1/4 - icône + titre à gauche, contenu libre droite
    CONDENSED,  // 1/2×1/2 - icône + titre en haut, reste libre dessous
    EXTENDED,   // 1×1/2 - icône + titre en haut, zone libre dessous
    SQUARE,     // 1×1 - icône + titre en haut, grande zone libre
    FULL        // 1×∞ - icône + titre en haut, zone libre infinie
}
```

### Composants métier spécialisés
```kotlin
// Logique métier complexe - composants dédiés
UI.ZoneCard(
    zone: Zone,
    onClick: () -> Unit,
    contentDescription: String? = null
)

UI.ToolCard(
    tool: ToolInstance,
    displayMode: DisplayMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit = { },
    contentDescription: String? = null
)

// Bases simples - composants génériques
UI.Card(type, content)
UI.Button(type, onClick)
```

## 4. DIALOG SYSTEM INTÉGRÉ

### Types avec logique automatique
```kotlin
enum class DialogType {
    CONFIGURE,   // → boutons "Valider" + "Annuler"
    CREATE,      // → boutons "Créer" + "Annuler"
    EDIT,        // → boutons "Sauvegarder" + "Annuler"  
    CONFIRM,     // → boutons "Confirmer" + "Annuler"
    DANGER,      // → boutons "Supprimer" + "Annuler" (rouge)
    SELECTION,   // → pas de boutons prédéfinis
    INFO         // → bouton "OK"
}
```

## 5. FORMULAIRES UNIFIÉS

### Composants de form standardisés
```kotlin
UI.FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    type: TextFieldType = TEXT,
    validation: ValidationRule = NONE,
    state: ComponentState = NORMAL,
    readonly: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
)

UI.FormSelection(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    contentDescription: String? = null
)

UI.FormActions(content: @Composable () -> Unit)
```

### Structure type page de configuration
```kotlin
// Section 1 : Identité
UI.FormField("Nom", name, { name = it }, validation = REQUIRED)
UI.FormField("Description", description, { description = it })

// Section 2 : Affichage
UI.FormSelection("Mode d'affichage", displayModes, displayMode, { displayMode = it })
UI.FormField("Icône", iconName, { }, readonly = true, onClick = { showIconDialog = true })

// Section 3 : Comportement
UI.FormSelection("Gestion", listOf("Manuel", "IA", "Collaboratif"), management, { management = it })
UI.FormSelection("Validation config par IA", listOf("Activée", "Désactivée"), configValidation, { })
UI.FormSelection("Validation données par IA", listOf("Activée", "Désactivée"), dataValidation, { })

// Section 4 : Config spécifique (conditionnelle)
if (type == "numeric") {
    UI.FormSelection("Mode des items", listOf("Libre", "Prédéfini", "Mixte"), itemMode, { })
    UI.FormSelection("Afficher valeur", listOf("Oui", "Non"), showValue, { })
}
if (type == "duration") {
    UI.FormSelection("Commutation auto", listOf("Activée", "Désactivée"), autoSwitch, { })
}

// Actions
UI.FormActions {
    UI.Button(type = SAVE, onClick = { save() }) { }
    UI.Button(type = CANCEL, onClick = { cancel() }) { }
}
```

## 6. FEEDBACK UTILISATEUR

### Messages temporaires intégrés
```kotlin
enum class FeedbackType { SUCCESS, ERROR, WARNING, INFO }

enum class Duration { SHORT, LONG, INDEFINITE }
```

### Usage dans l'application
```kotlin
// Après sauvegarde
coordinator.processUserAction("save_config") 
→ UI.Toast(SUCCESS, "Configuration sauvegardée")

// Après suppression avec undo
UI.Snackbar(SUCCESS, "Élément supprimé", "ANNULER") { undoDelete() }

// Erreur réseau
UI.Snackbar(ERROR, "Erreur de connexion", "RÉESSAYER") { retry() }
```
