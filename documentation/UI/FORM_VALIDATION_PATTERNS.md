# Patterns Formulaires & Validation

## Principe Architectural

**Validation en 2 couches** - Plus jamais 6 couches de validation !

1. **Validation UI légère** : Champs vides, format basique
2. **Validation Service robuste** : Logique métier, contraintes données, JSON

---

## 1. VALIDATION UI LÉGÈRE

### Pattern `isFormValid`
```kotlin
// État de validation calculé automatiquement
val isFormValid = remember(name, email) { 
    name.trim().isNotEmpty() && email.contains("@") 
}

// Utilisation pour activer/désactiver actions
UI.Button(
    type = ButtonType.PRIMARY,
    onClick = handleSave,
    enabled = isFormValid  // Optionnel : désactiver si invalide
) {
    UI.Text("Sauvegarder", TextType.LABEL)
}
```

### Pattern validation dans `handleSave`
```kotlin
val handleSave = {
    if (isFormValid) {
        // Procéder à la sauvegarde
        coordinator.processUserAction(...)
    }
    // Pas d'else - UI.FormField gère l'affichage des erreurs
}
```

---

## 2. COMPOSANTS DE FORMULAIRE

### UI.FormField - Standard unifié
```kotlin
UI.FormField(
    label = "Nom de la zone",
    value = name,
    onChange = { name = it },
    fieldType = FieldType.TEXT,        // TEXT, NUMERIC, EMAIL, PASSWORD, SEARCH
    required = true,                   // Affichage "(optionnel)" si false
    state = ComponentState.NORMAL,     // NORMAL, ERROR, DISABLED
    readonly = false,
    onClick = null                     // Pour champs cliquables (ex: sélection icône)
)
```

### UI.FormSelection - Sélections
```kotlin
UI.FormSelection(
    label = "Mode d'affichage",
    options = listOf("Minimal", "Étendu", "Carré"),
    selected = displayMode,
    onSelect = { displayMode = it },
    required = true
)
```

### UI.FormActions - Boutons standardisés
```kotlin
UI.FormActions {
    UI.Button(
        type = ButtonType.PRIMARY,
        onClick = handleSave
    ) {
        UI.Text(if (isEditing) "Sauvegarder" else "Créer", TextType.LABEL)
    }
    
    UI.Button(
        type = ButtonType.SECONDARY,
        onClick = onCancel
    ) {
        UI.Text("Annuler", TextType.LABEL)
    }
    
    if (isEditing && onDelete != null) {
        UI.Button(
            type = ButtonType.SECONDARY,
            onClick = { showDeleteDialog = true }
        ) {
            UI.Text("Supprimer", TextType.LABEL)
        }
    }
}
```

---

## 3. VALIDATION SERVICE ROBUSTE

### Pattern Service avec ToolType validation
```kotlin
class MyService(private val context: Context) : ExecutableService {
    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        // 1. Créer l'entité
        val newEntry = MyData(...)
        
        // 2. Validation via ToolType (discovery pattern)
        val toolType = ToolTypeManager.getToolType("my_tool")
        if (toolType != null) {
            val validation = toolType.validateData(newEntry, operation)
            if (!validation.isValid) {
                Log.e("MyService", "Validation failed: ${validation.errorMessage}")
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }
        
        // 3. Persistence
        dao.insert(newEntry)
        return OperationResult.success()
    }
}
```

### Pattern ToolType validation
```kotlin
override fun validateData(data: Any, operation: String): ValidationResult {
    if (data !is MyData) {
        return ValidationResult.error("Invalid data type")
    }
    
    return when (operation) {
        "create" -> validateCreate(data)
        "update" -> validateUpdate(data) 
        "delete" -> validateDelete(data)
        else -> ValidationResult.error("Unknown operation: $operation")
    }
}

private fun validateCreate(data: MyData): ValidationResult {
    // Validation JSON structure
    return try {
        val json = JSONObject(data.value)
        
        if (!json.has("quantity")) {
            return ValidationResult.error("Missing required 'quantity' field")
        }
        
        val quantity = json.getDouble("quantity")
        if (quantity < 0 || !quantity.isFinite()) {
            return ValidationResult.error("Invalid quantity value: $quantity")
        }
        
        ValidationResult.success()
    } catch (e: Exception) {
        ValidationResult.error("Invalid JSON format: ${e.message}")
    }
}
```

---

## 4. STRUCTURE COMPLÈTE D'UN ÉCRAN

### Template Screen avec validation
```kotlin
@Composable
fun MyFormScreen(
    onCancel: () -> Unit,
    onCreate: ((String, String) -> Unit)? = null,
    // ... autres paramètres
) {
    val coroutineScope = rememberCoroutineScope()
    val coordinator = LocalCoordinator.current
    
    // États du formulaire
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Validation UI légère
    val isFormValid = remember(name) { name.trim().isNotEmpty() }
    
    // Logique de sauvegarde
    val handleSave = {
        if (isFormValid) {
            coroutineScope.launch {
                try {
                    val result = coordinator.processUserAction(
                        "create->my_entity",
                        mapOf(
                            "name" to name.trim(),
                            "description" to description.trim()
                        )
                    )
                    onCreate?.invoke(name.trim(), description.trim())
                } catch (e: Exception) {
                    // TODO: Error handling
                }
            }
        }
    }
    
    // UI
    Column {
        UI.Text("Mon Formulaire", TextType.TITLE)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Champs
        UI.FormField(
            label = "Nom",
            value = name,
            onChange = { name = it },
            fieldType = FieldType.TEXT,
            required = true
        )
        
        UI.FormField(
            label = "Description", 
            value = description,
            onChange = { description = it },
            fieldType = FieldType.TEXT,
            required = false
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Actions
        UI.FormActions {
            UI.Button(
                type = ButtonType.PRIMARY,
                onClick = handleSave
            ) {
                UI.Text("Créer", TextType.LABEL)
            }
            
            UI.Button(
                type = ButtonType.SECONDARY,
                onClick = onCancel
            ) {
                UI.Text("Annuler", TextType.LABEL)
            }
        }
    }
}
```

---

## 5. RÈGLES ET BONNES PRATIQUES

### ✅ **À FAIRE**
- **Validation UI** : `isFormValid = remember(dependencies) { validation simple }`
- **Champs requis** : `required = true` dans UI.FormField
- **Actions** : `UI.FormActions { }` pour tous les boutons
- **Types** : `FieldType` unifié (TEXT, NUMERIC, EMAIL, PASSWORD, SEARCH)
- **Service** : Validation robuste via ToolType + discovery pattern
- **Logs** : `Log.e("Service", "Validation failed: ...")` pour debug

### ❌ **À ÉVITER**
- ~~Validation manuelle avec états d'erreur~~ → `UI.FormField` gère l'affichage
- ~~TextFieldType~~ → `FieldType` unifié
- ~~Boutons Row/Column manuels~~ → `UI.FormActions`
- ~~Validation côté UI ET Service ET DAO ET...~~ → **2 couches seulement**
- ~~Imports hardcodés dans Core~~ → Discovery pattern

### 🔧 **Patterns spéciaux**

**Champs cliquables** (ex: sélection icône) :
```kotlin
UI.FormField(
    label = "Icône",
    value = iconName,
    onChange = { },
    readonly = true,
    onClick = { showIconDialog = true }
)
```

**Validation avec dépendances multiples** :
```kotlin
val isFormValid = remember(name, email, password) {
    name.trim().isNotEmpty() && 
    email.contains("@") && 
    password.length >= 8
}
```

**Dialog avec validation** :
```kotlin
UI.Dialog(
    type = DialogType.CREATE,
    onConfirm = {
        if (itemName.isNotBlank()) {
            onConfirm(itemName.trim(), ...)
        }
    },
    onCancel = onCancel
)
```

---

## 6. EXEMPLES D'IMPLÉMENTATION

### ✅ **Écrans conformes**
- `CreateZoneScreen` : Pattern complet avec UI.FormActions
- `TrackingConfigScreen` : Validation complexe avec isValid
- `TrackingItemDialog` : Dialog avec validation simple

### 🎯 **Architecture finale**
- **UI** → Validation légère + UI.FormField + UI.FormActions
- **Service** → Validation robuste + ToolType + Discovery
- **Zero** couche intermédiaire - Simplicité maximale

**Règle d'or** : Si c'est plus complexe que ces patterns, c'est probablement mal conçu.