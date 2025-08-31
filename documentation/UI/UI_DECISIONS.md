# Patterns UI - Architecture Finale

## 🏗️ **Layout Principal**
```kotlin
Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) { /* contenu */ }
```

## 🎨 **Titres et Headers**
```kotlin
// Titre principal écran (centré)
UI.Text("Titre", TextType.TITLE, fillMaxWidth = true, textAlign = TextAlign.Center)

// Titre section hors card (padding horizontal)
Box(modifier = Modifier.padding(horizontal = 16.dp)) {
    UI.Text("Section", TextType.SUBTITLE)
}
```

## 📱 **Cards Pleine Largeur**
```kotlin
UI.Card(type = CardType.DEFAULT) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Contenu avec padding interne
    }
}
```

## 🎯 **Headers de Page**
```kotlin
UI.PageHeader(
    title = "Titre",
    subtitle = "Sous-titre optionnel",
    icon = "activity",          // Optionnel
    leftButton = ButtonAction.BACK,
    rightButton = ButtonAction.ADD,
    onLeftClick = onBack,
    onRightClick = onAdd
)
```

## 🔘 **Boutons et Formulaires**
```kotlin
// Boutons centrés
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    UI.ActionButton(action = ButtonAction.SAVE, onClick = save)
}

// Formulaires
UI.FormField(label = "Nom", value = name, onChange = { name = it }, required = true)
UI.FormActions {
    UI.ActionButton(action = ButtonAction.SAVE, onClick = save)
    UI.ActionButton(action = ButtonAction.CANCEL, onClick = cancel)
}
```

## 📊 **Tableaux avec Weight**
```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    // Boutons fixes
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        UI.ActionButton(action = ButtonAction.UP, display = ButtonDisplay.ICON, size = Size.XS)
    }
    // Colonnes flexibles  
    Box(modifier = Modifier.weight(4f).padding(8.dp)) {
        UI.Text("Nom", TextType.BODY)
    }
}
```

## ⚡ **Espacement Standard**
- **Entre sections** : `spacedBy(16.dp)`
- **Vertical screens** : `padding(vertical = 16.dp)`
- **Cards internes** : `padding(16.dp)`
- **Sections hors cards** : `padding(horizontal = 16.dp)`

## 🎯 **Architecture Hybride**
- **Layouts** : Compose natif (Row, Column, Box, Spacer)
- **Visuels** : UI.* (Button, Text, Card, FormField)
- **Métier** : UI.* (ZoneCard, ToolCard)

## ❌ **Interdictions**
- ~~UI.Column/Row/Box~~ → Compose direct
- ~~LaunchedEffect pour état initial~~ → `remember(deps) { calcul }`
- ~~Valeurs hardcodées~~ → `.getDefaultConfig()`, `.orEmpty()`

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

## 3. UI.TEXT - ARCHITECTURE SIMPLIFIÉE

### ✅ **Signature finale (4 paramètres)**
```kotlin
@Composable
fun Text(
    text: String,
    type: TextType,
    fillMaxWidth: Boolean = false,
    textAlign: TextAlign? = null
)
```

### 💡 **Layout séparé = Box wrapper**
```kotlin
// ✅ Pattern weight avec Box
Row {
    UI.Text("Label", TextType.BODY)
    Box(modifier = Modifier.weight(1f)) {
        UI.Text("Flexible", TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
    }
}

// ✅ Pattern padding avec Box  
Box(modifier = Modifier.padding(8.dp)) {
    UI.Text("Padded", TextType.BODY)
}

// ✅ Pattern clickable avec Box
Box(modifier = Modifier.clickable { navigate() }) {
    UI.Text("Menu", TextType.BODY)
}
```

### 🎯 **Principe de séparation**
- **UI.Text** → Rendu du texte uniquement
- **Box + Modifier** → Layout, interactions, espacement
- **Compose-idiomatique** → Responsabilités séparées

---

## 4. FEEDBACK UTILISATEUR

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
