# Interface Utilisateur

Guide des patterns et composants UI pour maintenir cohérence et simplicité.

## ═══════════════════════════════════
## Architecture Hybride

**Layouts** : Compose natif (Row, Column, Box, Spacer)
**Visuels** : Composants UI.* (Button, Text, Card, FormField)  
**Métier** : Composants spécialisés (ZoneCard, ToolCard)

### Layout Standard

```kotlin
Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    // Contenu avec espacement automatique 16dp
}
```

### Headers de Page

```kotlin
UI.PageHeader(
    title = "Configuration",
    subtitle = "Paramètres du suivi", // optionnel
    icon = "activity",                 // optionnel
    leftButton = ButtonAction.BACK,
    rightButton = ButtonAction.ADD,
    onLeftClick = onBack,
    onRightClick = onAdd
)
```

## ═══════════════════════════════════
## Système de Texte Simplifié

### UI.Text - 4 paramètres maximum

```kotlin
UI.Text(
    text: String,
    type: TextType,                // TITLE, SUBTITLE, BODY, LABEL, SMALL
    fillMaxWidth: Boolean = false, // Largeur complète
    textAlign: TextAlign? = null   // Alignement optionnel
)
```

### Séparation Layout/Contenu

```kotlin
// Pattern weight avec Box wrapper
Row {
    UI.Text("Label", TextType.BODY)
    Box(modifier = Modifier.weight(1f)) {
        UI.Text("Contenu", TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
    }
}

// Pattern padding avec Box
Box(modifier = Modifier.padding(8.dp)) {
    UI.Text("Texte", TextType.BODY)
}

// Pattern clickable avec Box
Box(modifier = Modifier.clickable { navigate() }) {
    UI.Text("Menu", TextType.BODY)  
}
```

**Principe** : UI.Text pour le rendu, Box+Modifier pour layout et interactions.

### Pattern Row Standardisé

```kotlin
// Pattern espacement uniforme
Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    // Colonnes avec weight + Box pour alignement précis
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { 
        UI.ActionButton(action = ButtonAction.UP, display = ButtonDisplay.ICON, size = Size.S)
    }
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { 
        UI.ActionButton(action = ButtonAction.DOWN, display = ButtonDisplay.ICON, size = Size.S)
    }
    Box(modifier = Modifier.weight(4f)) { 
        UI.CenteredText("Contenu principal", TextType.BODY)
    }
}
```

**Usage** : Tableaux, listes avec actions, formulaires multi-colonnes  
**Espacement** : `spacedBy(8.dp)` standard entre colonnes  
**Padding** : `vertical = 4.dp` pour les rows de tableaux

## ═══════════════════════════════════
## Boutons et Actions

### Deux Types de Boutons

**UI.Button** - Générique et flexible :
```kotlin
UI.Button(
    type = ButtonType.PRIMARY,     // PRIMARY, SECONDARY, DEFAULT
    size = Size.M,                 // XS, S, M, L, XL, XXL
    state = ComponentState.NORMAL, // NORMAL, ERROR, DISABLED
    onClick = { },
    content = { UI.Text("Custom", TextType.LABEL) }
)
```

**UI.ActionButton** - Actions standardisées :
```kotlin
UI.ActionButton(
    action = ButtonAction.SAVE,     // Actions prédéfinies
    display = ButtonDisplay.LABEL,  // ICON ou LABEL
    size = Size.M,
    requireConfirmation = false,    // Dialogue automatique
    onClick = handleSave
)
```

### Actions Disponibles

**Principales** : SAVE, CREATE, UPDATE, DELETE, CANCEL, CONFIRM
**Navigation** : BACK, UP, DOWN
**Utilitaires** : ADD, EDIT, CONFIGURE, REFRESH, SELECT

### Hiérarchie Visuelle

**PRIMARY** : Actions critiques/importantes (vert terminal)
- SAVE, CREATE, CONFIRM, ADD, CONFIGURE, SELECT, EDIT, UPDATE
- Usage : Actions qui créent, modifient ou valident du contenu

**DEFAULT** : Actions neutres/navigation (gris moyen distinct)  
- CANCEL, BACK, REFRESH, UP, DOWN
- Usage : Navigation et actions sans impact sur les données

**SECONDARY** : Actions destructives (rouge sombre)
- DELETE uniquement
- Usage : Actions irréversibles nécessitant attention

### Confirmation Automatique

```kotlin
UI.ActionButton(
    action = ButtonAction.DELETE,
    requireConfirmation = true,
    confirmMessage = "Supprimer \"${item}\" ?", // optionnel
    onClick = handleDelete // appelé APRÈS confirmation
)
```

## ═══════════════════════════════════
## Champs de Texte et Saisie

### Extensions FieldType

```kotlin
// Limites automatiques selon le contexte
UI.FormField(
    fieldType = FieldType.TEXT,           // 60 chars - noms, identifiants, labels
    fieldType = FieldType.TEXT_MEDIUM,    // 250 chars - descriptions, valeurs tracking texte  
    fieldType = FieldType.TEXT_LONG,      // 1500 chars - contenu libre long
    fieldType = FieldType.TEXT_UNLIMITED, // Aucune limite - documentation, exports
    fieldType = FieldType.NUMERIC,        // Clavier numérique
    fieldType = FieldType.EMAIL,          // Clavier email, pas d'autocorrect
    fieldType = FieldType.PASSWORD,       // Masqué, pas d'autocorrect
    fieldType = FieldType.SEARCH          // Autocorrect + action search
)
```

### Autocorrection Intelligente

**TEXT** : Words + autocorrect (noms, identifiants)  
**TEXT_MEDIUM/LONG** : Sentences + autocorrect (contenu utilisateur)  
**EMAIL/PASSWORD** : Pas d'autocorrect (sécurité/précision)  
**NUMERIC** : Clavier numérique uniquement  
**SEARCH** : Words + action loupe

### Mapping Contexte → FieldType

**Identifiants** : `FieldType.TEXT` (60) - Noms zones, outils, items  
**Descriptions** : `FieldType.TEXT_MEDIUM` (250) - Descriptions outils  
**Valeurs tracking texte** : `FieldType.TEXT_MEDIUM` (250) - Observations utilisateur  
**Documentation** : `FieldType.TEXT_LONG` (1500) - Aide, notes longues

## ═══════════════════════════════════
## Formulaires et Validation

### Toast d'Erreurs Automatique

```kotlin
errorMessage?.let { message ->
    LaunchedEffect(message) {
        UI.Toast(context, message, Duration.LONG)
        errorMessage = null
    }
}
```

### Composants Formulaire

**UI.FormField** - Champ standard :
```kotlin
UI.FormField(
    label = "Nom de la zone",
    value = name,
    onChange = { name = it },
    fieldType = FieldType.TEXT,        // TEXT, NUMERIC, EMAIL, PASSWORD, SEARCH
    required = true,                   // "(optionnel)" si false
    state = ComponentState.NORMAL,     // Gestion erreur automatique
    readonly = false,
    onClick = null                     // Pour champs cliquables
)
```

**UI.FormSelection** - Sélections :
```kotlin
UI.FormSelection(
    label = "Mode d'affichage",
    options = listOf("Minimal", "Étendu", "Carré"),
    selected = displayMode,
    onSelect = { displayMode = it },
    required = true
)
```

**UI.FormActions** - Boutons standardisés :
```kotlin
UI.FormActions {
    UI.ActionButton(action = ButtonAction.SAVE, onClick = handleSave)
    UI.ActionButton(action = ButtonAction.CANCEL, onClick = onCancel)
    
    if (isEditing) {
        UI.ActionButton(
            action = ButtonAction.DELETE,
            requireConfirmation = true,
            onClick = handleDelete
        )
    }
}
```

## ═══════════════════════════════════
## Cards et Conteneurs

### Cards Pleine Largeur

```kotlin
UI.Card(type = CardType.DEFAULT) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Contenu avec padding interne 16dp
    }
}
```

### Titres et Sections

```kotlin
// Titre principal écran (centré)
UI.Text("Titre", TextType.TITLE, fillMaxWidth = true, textAlign = TextAlign.Center)

// Titre section hors card (padding horizontal)
Box(modifier = Modifier.padding(horizontal = 16.dp)) {
    UI.Text("Section", TextType.SUBTITLE)
}
```

## ═══════════════════════════════════
## Changements d'Orientation

### Problem : États perdus lors de rotation écran

```kotlin
// ❌ État perdu  
var showDialog by remember { mutableStateOf(false) }

// ✅ État conservé
var showDialog by rememberSaveable { mutableStateOf(false) }
```

### Pattern ID pour objets complexes

```kotlin
// Sauvegarder l'ID, pas l'objet
var selectedZoneId by rememberSaveable { mutableStateOf<String?>(null) }
val selectedZone = zones.find { it.id == selectedZoneId }

// Usage
onClick = { selectedZoneId = zone.id }
```

**rememberSaveable pour** : navigation, sélections, formulaires
**remember pour** : loading, données auto-rechargées

## ═══════════════════════════════════
## Tableaux et Listes

### Pattern Weight pour Tableaux

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    // Colonnes fixes
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        UI.ActionButton(
            action = ButtonAction.UP, 
            display = ButtonDisplay.ICON, 
            size = Size.XS
        )
    }
    
    // Colonnes flexibles  
    Box(modifier = Modifier.weight(4f).padding(8.dp)) {
        UI.Text("Nom de l'item", TextType.BODY)
    }
}
```

### Composants Métier Spécialisés

```kotlin
// Zone card avec logique métier intégrée
UI.ZoneCard(
    zone = zone,
    onClick = { navigateToZone(zone.id) },
    contentDescription = "Zone ${zone.name}"
)

// Tool instance avec modes d'affichage
UI.ToolCard(
    tool = toolInstance,
    displayMode = DisplayMode.EXTENDED, // ICON, MINIMAL, LINE, CONDENSED, EXTENDED, SQUARE, FULL
    onClick = { openTool(tool.id) },
    onLongClick = { showToolMenu(tool.id) }
)
```

## ═══════════════════════════════════
## Navigation et États

### Navigation Conditionnelle

```kotlin
var showCreateZone by remember { mutableStateOf(false) }

if (showCreateZone) {
    CreateZoneScreen(onCancel = { showCreateZone = false })
} else {
    // Écran principal
}
```

**Avantage** : Simple, pas de NavController pour cas basiques.

### Feedback Utilisateur

```kotlin
// Messages temporaires
UI.Toast(context, "Configuration sauvegardée", Duration.SHORT)
UI.Toast(context, "Erreur réseau", Duration.LONG)
// Note: Snackbar avec actions à implémenter plus tard si nécessaire
```

## ═══════════════════════════════════
## Thèmes et Personnalisation

### Palette Personnalisée

Le système de thème utilise une palette de couleurs personnalisée branchée sur Material Design.

**Formes** : Définies au niveau thème pour boutons et cards
**Couleurs** : Palette Material adaptée aux couleurs personnalisées
**Typography** : Cohérente via TextType enum

### Espacement Standard

- **Entre sections** : `spacedBy(16.dp)`
- **Vertical screens** : `padding(vertical = 16.dp)`  
- **Cards internes** : `padding(16.dp)`
- **Sections hors cards** : `padding(horizontal = 16.dp)`

## ═══════════════════════════════════
## Pattern Loading/Error Standard 🆕

### États Obligatoires
```kotlin
var data by remember { mutableStateOf<DataType?>(null) }
var isLoading by remember { mutableStateOf(true) }
var errorMessage by remember { mutableStateOf<String?>(null) }
```

### Affichage Conditionnel
```kotlin
// Early return pour loading
if (isLoading) {
    UI.Text(s.shared("tools_loading"), TextType.BODY)
    return
}

// Toast automatique pour erreurs
errorMessage?.let { message ->
    LaunchedEffect(message) {
        UI.Toast(context, message, Duration.LONG)
        errorMessage = null
    }
}
```

### INTERDIT : Valeurs par défaut silencieuses
```kotlin
// ❌ MAUVAIS - masque les erreurs
Period.now(PeriodType.DAY, 0, "monday")

// ✅ BON - erreur explicite
if (config == null) return
Period.now(PeriodType.DAY, config.dayStartHour, config.weekStartDay)
```

## ═══════════════════════════════════
## Règles d'Usage

### À Faire

- **ActionButton** pour actions standard (SAVE, DELETE, BACK)
- **UI.Button** pour textes dynamiques et contenu complexe
- **FormActions** pour tous boutons de formulaire
- **Box wrappers** pour layout et interactions UI.Text
- **Validation centralisée** via SchemaValidator
- **isLoading pattern** pour tous états async
- **rememberSaveable** pour navigation (rotation safe)

### À Éviter

- Mélanger ActionButton et UI.Button dans même écran
- Boutons Row/Column manuels au lieu de FormActions  
- Layout modifiers directement sur UI.Text
- Valeurs par défaut qui masquent erreurs de config
- Strings hardcodées (toujours s.shared/s.tool)

---

*L'interface UI privilégie cohérence et simplicité sans sacrifier flexibilité.*