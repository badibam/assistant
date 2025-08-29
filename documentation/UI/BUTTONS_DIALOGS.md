# Documentation Buttons & Dialogs

## Architecture des Boutons

### **2 Types de boutons disponibles**

#### **1) UI.Button** - Générique et flexible
```kotlin
UI.Button(
    type = ButtonType.PRIMARY,     // PRIMARY, SECONDARY, DEFAULT
    size = Size.M,                 // XS, S, M, L, XL, XXL
    state = ComponentState.NORMAL, // NORMAL, ERROR, DISABLED
    onClick = { },
    content = { UI.Text("Custom", TextType.LABEL) }
)
```

#### **2) UI.ActionButton** - Actions standardisées
```kotlin
UI.ActionButton(
    action = ButtonAction.SAVE,              // Actions prédéfinies
    display = ButtonDisplay.LABEL,           // ICON ou LABEL
    size = Size.M,                          // Adapté auto pour ICON
    type = ButtonType.PRIMARY,              // Override optionnel
    enabled = true,
    requireConfirmation = false,            // Dialogue auto
    confirmMessage = "Message custom",       // Override message
    onClick = handleSave
)
```

---

## ButtonAction - Actions Disponibles

### **Actions principales**
```kotlin
ButtonAction.SAVE        // ✓ → "Sauvegarder" → PRIMARY
ButtonAction.CREATE      // + → "Créer" → PRIMARY  
ButtonAction.UPDATE      // ✎ → "Modifier" → DEFAULT
ButtonAction.DELETE      // ✕ → "Supprimer" → SECONDARY
ButtonAction.CANCEL      // ✕ → "Annuler" → SECONDARY
ButtonAction.CONFIRM     // ✓ → "Confirmer" → PRIMARY
```

### **Actions navigation**
```kotlin
ButtonAction.BACK        // ◀ → "Retour" → DEFAULT
ButtonAction.UP          // ▲ → "Monter" → DEFAULT
ButtonAction.DOWN        // ▼ → "Descendre" → DEFAULT
```

### **Actions utilitaires**
```kotlin
ButtonAction.ADD         // + → "Ajouter" → DEFAULT
ButtonAction.EDIT        // ✎ → "Modifier" → DEFAULT
ButtonAction.CONFIGURE   // ⚙ → "Configurer" → DEFAULT
ButtonAction.REFRESH     // ↻ → "Actualiser" → DEFAULT
ButtonAction.SELECT      // ✓ → "Sélectionner" → DEFAULT
```

---

## Modes d'Affichage

### **ButtonDisplay.LABEL** (par défaut)
- Texte automatique depuis strings.xml
- Taille standard Material
- Pour actions principales

### **ButtonDisplay.ICON**  
- Symboles Unicode compacts
- Taille réduite automatiquement (M→S, L→M, etc.)
- Mode circulaire avec `minimumInteractiveComponentSize()`
- Pour actions secondaires et barres d'outils

---

## Dialogues de Confirmation

### **Confirmation automatique**
```kotlin
UI.ActionButton(
    action = ButtonAction.DELETE,
    requireConfirmation = true,                    // Active le dialogue
    confirmMessage = "Supprimer \"${item}\" ?",   // Optionnel
    onClick = handleDelete                         // Appelé APRÈS confirmation
)
```

### **Message par défaut**
- DELETE → "Êtes-vous sûr de vouloir supprimer cet élément ? Cette action est irréversible."
- Autres actions → "Confirmer cette action ?"

### **UI.Dialog** - Dialogues manuels
```kotlin
UI.Dialog(
    type = DialogType.DANGER,        // DANGER, CONFIRM, INFO, etc.
    onConfirm = { },
    onCancel = { },
    content = { UI.Text("Message", TextType.BODY) }
)
```

---

## Exemples d'Usage

### **Formulaires standard**
```kotlin
UI.FormActions {
    UI.ActionButton(
        action = if (isEditing) ButtonAction.SAVE else ButtonAction.CREATE,
        onClick = handleSave
    )
    
    UI.ActionButton(
        action = ButtonAction.CANCEL,
        onClick = onCancel
    )
    
    if (isEditing) {
        UI.ActionButton(
            action = ButtonAction.DELETE,
            requireConfirmation = true,
            confirmMessage = "Supprimer \"${name}\" ?",
            onClick = handleDelete
        )
    }
}
```

### **Barre d'outils avec icônes**
```kotlin
Row {
    UI.ActionButton(
        action = ButtonAction.BACK,
        display = ButtonDisplay.ICON,
        onClick = onBack
    )
    
    UI.ActionButton(
        action = ButtonAction.CONFIGURE,
        display = ButtonDisplay.ICON,
        onClick = onConfigure
    )
    
    UI.ActionButton(
        action = ButtonAction.REFRESH,
        display = ButtonDisplay.ICON,
        onClick = onRefresh
    )
}
```

### **Liste avec actions item**
```kotlin
Row {
    UI.ActionButton(
        action = ButtonAction.EDIT,
        display = ButtonDisplay.ICON,
        size = Size.S,
        onClick = onEdit
    )
    
    UI.ActionButton(
        action = ButtonAction.DELETE,
        display = ButtonDisplay.ICON,
        size = Size.S,
        requireConfirmation = true,
        onClick = onDelete
    )
}
```

### **Cas spéciaux avec UI.Button**
```kotlin
// Texte dynamique
UI.Button(
    type = ButtonType.PRIMARY,
    onClick = onSelect
) {
    UI.Text(toolType.getDisplayName(), TextType.LABEL)
}

// Contenu complexe
UI.Button(
    type = ButtonType.SECONDARY,
    onClick = onClick
) {
    Row {
        SafeIcon(iconName)
        UI.Text(iconDisplayName, TextType.SMALL)
    }
}
```

---

## ButtonType - Styles Visuels

### **PRIMARY** - Action principale
- Couleur vive (Material primary)
- Pour SAVE, CREATE, CONFIRM
- Maximum 1 par écran

### **SECONDARY** - Actions destructives/secondaires  
- Contour + fond transparent
- Pour DELETE, CANCEL
- Couleur d'erreur pour DELETE

### **DEFAULT** - Actions neutres
- Fond surface + contour subtil
- Pour BACK, CONFIGURE, EDIT, ADD, etc.
- Le plus fréquent

---

## Sizing Automatique

### **Display LABEL**
```
Size.XS  → 20dp min
Size.S   → 24dp min  
Size.M   → 32dp min (défaut)
Size.L   → 40dp min
Size.XL  → 48dp min
Size.XXL → 56dp min
```

### **Display ICON** (réduit automatiquement)
```
Demandé → Effectif → Taille
XXL     → XL       → 48dp carré
XL      → L        → 40dp carré  
L       → M        → 32dp carré
M       → S        → 28dp carré (défaut icon)
S       → XS       → 24dp carré
XS      → XS       → 20dp carré
```

---

## Règles d'Usage

### **✅ Utiliser ActionButton pour**
- Actions standard (SAVE, DELETE, BACK, etc.)
- Barres d'outils avec icônes
- Formulaires avec confirmation auto
- Actions répétitives dans l'app

### **✅ Utiliser UI.Button pour**  
- Textes dynamiques (noms d'outils, etc.)
- Contenu complexe (icône + texte custom)
- Actions métier très spécifiques
- Prototypage rapide

### **❌ À éviter**
- ~~Mélanger les 2 systèmes dans un même écran~~
- ~~Créer des ActionButton pour 1 seul usage~~
- ~~Dialogues de confirmation manuels pour DELETE~~
- ~~Boutons sans feedback visuel d'état~~

---

## Migration depuis Anciens Boutons

### **Avant** 
```kotlin
UI.SaveButton(onClick = save)         // ❌ Supprimé
UI.DeleteButton(onClick = delete)     // ❌ Supprimé  
UI.BackButton(onClick = back)         // ❌ Supprimé
```

### **Après**
```kotlin
UI.ActionButton(action = ButtonAction.SAVE, onClick = save)     // ✅
UI.ActionButton(action = ButtonAction.DELETE, onClick = delete) // ✅
UI.ActionButton(action = ButtonAction.BACK, onClick = back)     // ✅
```

**Migration automatique** : Même fonctionnalité + confirmation auto + internationalisation prête.