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

## 3. FEEDBACK UTILISATEUR

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
