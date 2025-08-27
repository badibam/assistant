# Décisions UI - Architecture Finale

## 1. ARCHITECTURE DES COMPOSANTS

### 📐 LAYOUTS : Compose natif
```kotlin
// ✅ UTILISER DIRECTEMENT
Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { }
Column(verticalArrangement = Arrangement.Center) { }
Box(modifier = Modifier.fillMaxSize()) { }
Spacer(modifier = Modifier.height(16.dp))
```

### 🎨 COMPOSANTS VISUELS : UI.*
```kotlin
// ✅ UTILISER UI.*
UI.Button(type = ButtonType.PRIMARY) { }
UI.Text("Titre", TextType.TITLE)
UI.TextField(type = TextFieldType.TEXT, value, onChange, placeholder)
UI.Card(type = CardType.DEFAULT) { }
```

### 🏗️ COMPOSANTS MÉTIER : UI.*
```kotlin
// ✅ LOGIQUE + APPARENCE
UI.ZoneCard(zone, onClick, onLongClick)
UI.ToolCard(tool, displayMode, onClick, onLongClick)
```

## ❌ INTERDICTIONS
```kotlin
// Pas de wrappers layout dans UI.*
UI.Column { }    // → Column { }
UI.Row { }       // → Row { }
UI.Box { }       // → Box { }
UI.Spacer(..)    // → Spacer(..)
```

## 💡 PRINCIPE
- **Layout = logique universelle** → Compose direct + modifiers
- **Visuel = apparence thématique** → UI.* pour cohérence
- **Métier = logique + apparence** → UI.* pour encapsulation
- **⚠️ Initialisation état : JAMAIS LaunchedEffect** → Utiliser `remember(dependencies) { calcul immédiat }` sinon affichage conditionnel bugué au premier rendu
- **⚠️ Valeurs par défaut : JAMAIS hardcodées** → Utiliser `.getDefaultConfig()`, `.orEmpty()` ou sources de vérité appropriées
- **⚠️ FormSelection : TOUJOURS conversion bidirectionnelle** → `when(valeurInterne) → "Valeur Affichée"` + `when(valeurAffichée) → valeurInterne` avec `else` pour cohérence
- **⚠️ FormSelection : JAMAIS de Boolean** → Utiliser String avec conversion ("show"/"hide" ↔ "Afficher"/"Masquer")
- **⚠️ Validation élégante** → `required: Boolean` + `fieldType: FieldType` dans UI.FormField/FormSelection

## 2. COMPOSANTS SPÉCIALISÉS

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
    UI.Button(type = PRIMARY, onClick = { save() }) { }
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
