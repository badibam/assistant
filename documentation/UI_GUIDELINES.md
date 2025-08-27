# UI Guidelines

## Architecture Hybride : Layout vs Composants Visuels

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
UI.Button(type = ButtonType.SAVE) { }
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
