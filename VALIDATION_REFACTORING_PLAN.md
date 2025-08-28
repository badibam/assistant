# Plan de Refactoring - Système de Validation Unifié

**Date**: 28 août 2025  
**Contexte**: Après implémentation du système de tracking, analyse des multiples niveaux de validation dispersés dans le code.

## 🚨 Problème Identifié

Actuellement, le système a **6 niveaux de validation différents** :
1. Validation UI/Frontend (`required = true`, `fieldType = FieldType.NUMERIC`)
2. Validation dans les actions de formulaire (`onConfirm`)
3. Validation dans les parsers (`NumberFormatting.parseUserInput()`)
4. Validation dans les services (`TrackingService`, `ToolType.validateData()`)
5. Validation au niveau DAO (actuellement inexistante)
6. Validation dans le Coordinator (paramètres)

**Problèmes** :
- Redondance et incohérence (`?: 0.0` vs `null` check)
- Logique dispersée (maintenance difficile)
- Gaps dans la validation UI (non bloquante)
- Code dupliqué

## 📋 État Actuel de `validateData()`

### Utilisation actuelle
- ✅ `TrackingService.handleCreate()` ligne 94
- ❌ Pas dans `handleUpdate()` 
- ❌ Pas dans `handleDelete()`
- ❌ Pas côté UI

### Implémentation existante
```kotlin
// TrackingToolType.kt
override fun validateData(data: Any, operation: String): ValidationResult {
    if (data !is TrackingData) {
        return ValidationResult.error("Expected TrackingData, got ${data::class.simpleName}")
    }
    
    return when (operation) {
        "create" -> validateCreate(data)
        "update" -> validateUpdate(data)
        "delete" -> validateDelete(data)
        else -> ValidationResult.error("Unknown operation: $operation")
    }
}
```

## 🎯 Solution Recommandée : Option C Améliorée

**Architecture à 3 niveaux avec usage extensif de `validateData()`** :

1. **UI** : Validation immédiate pour UX (`FormValidator` léger)
2. **Service** : Validation business via `ToolTypeContract.validateData()` 
3. **Data** : Validation contraintes critiques en DAO

## 📝 Plan d'Implémentation

### 1. 🔧 Extensions côté Service

**Ajouter validation `validateData()` dans** :
- `TrackingService.handleUpdate()`
- `TrackingService.handleDelete()`

```kotlin
// Exemple pour handleUpdate()
private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
    // ... code existant pour créer updatedEntry ...
    
    // NOUVEAU : Validation avant update
    val toolType = ToolTypeManager.getToolType("tracking")
    if (toolType != null) {
        val validation = toolType.validateData(updatedEntry, "update")
        if (!validation.isValid) {
            return OperationResult.error("Validation failed: ${validation.errorMessage}")
        }
    }
    
    trackingDao.updateEntry(updatedEntry)
    // ... reste du code ...
}
```

### 2. 🎨 Refactoring côté UI

**Remplacer dans** :
- `NumericTrackingInput.kt` (onConfirm)
- `TrackingHistory.kt` (dialog onConfirm)
- `TrackingConfigScreen.kt` (si applicable)

```kotlin
// Nouveau pattern pour UI
onConfirm = { name, unit, defaultValue, addToPredefined ->
    // Créer l'objet temporaire pour validation
    val tempEntry = TrackingData(
        tool_instance_id = toolInstanceId,
        zone_name = zoneName,
        tool_instance_name = toolInstanceName,
        name = name,
        value = createJsonValue(defaultValue, unit)
    )
    
    // Validation via ToolType au lieu de parsing manuel
    val toolType = ToolTypeManager.getToolType("tracking")
    val validation = toolType?.validateData(tempEntry, "create")
    
    if (validation?.isValid == true) {
        onSave(tempEntry.value, name)
    } else {
        // Afficher erreur de validation
        showValidationError(validation?.errorMessage ?: "Validation failed")
    }
}
```

### 3. ➕ Helper pour UI

**Créer** `ValidationHelper.kt` :
```kotlin
object ValidationHelper {
    suspend fun validateTrackingEntry(
        entry: TrackingData, 
        operation: String
    ): ValidationResult {
        val toolType = ToolTypeManager.getToolType("tracking")
        return toolType?.validateData(entry, operation) 
            ?: ValidationResult.error("ToolType not found")
    }
    
    fun createJsonValue(value: String, unit: String): String {
        val numericValue = NumberFormatting.parseUserInput(value)
            ?: throw IllegalArgumentException("Invalid numeric value: $value")
        
        return JSONObject().apply {
            put("amount", numericValue)
            put("unit", unit)
            put("type", "numeric")
            put("raw", if (unit.isNotBlank()) "$numericValue\u00A0$unit" else "$numericValue")
        }.toString()
    }
}
```

## 🗑️ Code à Supprimer

### Validation manuelle dans UI
```kotlin
// NumericTrackingInput.kt - SUPPRIMER
val numericValue = NumberFormatting.parseUserInput(defaultValue)
if (numericValue != null) {
    val jsonValue = JSONObject().apply { ... }
    onSave(jsonValue.toString(), name)
} else {
    Log.w("Invalid value")
}
```

### Validation dans TrackingHistory
```kotlin
// TrackingHistory.kt - SUPPRIMER lignes ~253-267
val numericValue = com.assistant.core.utils.NumberFormatting.parseUserInput(defaultValue)
if (numericValue != null) {
    val jsonValue = JSONObject().apply { ... }
    updateEntry(entry.id, jsonValue.toString(), name)
} else {
    Log.w("Invalid numeric value")
}
```

### Fallbacks avec elvis operator
```kotlin
// Partout - SUPPRIMER
NumberFormatting.parseUserInput(value) ?: 0.0
NumberFormatting.parseUserInput(value) ?: 1.0
```

## 📊 Bilan Estimé

### Code supprimé : ~50 lignes
- Validations manuelles dispersées
- Parsing avec fallbacks `?: 0.0`
- Checks `if (isBlank())` multiples
- Logique de création JSON dupliquée

### Code ajouté : ~30 lignes  
- Calls `validateData()` standardisés
- ValidationHelper
- Extensions service pour update/delete

### Résultat net : -20 lignes, logique centralisée

## 🎯 Avantages Attendus

1. **Cohérence** : Toutes les validations passent par le même système
2. **Maintenance** : Une seule source de vérité pour les règles métier
3. **Extensibilité** : Facile d'ajouter de nouveaux types d'outils
4. **Sécurité** : Validation systématique à tous les niveaux
5. **Code plus propre** : Moins de duplication, logique centralisée

## 📋 TODO pour Implémentation

1. [ ] Étendre `TrackingService.handleUpdate()` avec validation
2. [ ] Étendre `TrackingService.handleDelete()` avec validation  
3. [ ] Créer `ValidationHelper.kt`
4. [ ] Refactorer `NumericTrackingInput.kt` onConfirm
5. [ ] Refactorer `TrackingHistory.kt` dialog onConfirm
6. [ ] Supprimer les validations manuelles obsolètes
7. [ ] Tests pour s'assurer que tout fonctionne
8. [ ] Documentation des nouvelles pratiques de validation

## 🔍 Points d'Attention

- **Migration progressive** : Implémenter par petits blocs testables
- **Gestion d'erreurs** : S'assurer que les messages d'erreur remontent bien à l'UI
- **Performance** : `validateData()` ne doit pas être trop lourd côté UI
- **Compatibilité** : Vérifier que les autres types d'outils ne sont pas impactés

---
**Note** : Ce plan peut être exécuté en plusieurs sessions. Commencer par les extensions Service (plus sûr) avant le refactoring UI.