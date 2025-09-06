# Debug: Problème delete_all_entries

## Contexte
Implémentation de la suppression automatique des données lors du changement de type d'un outil de tracking existant.

## Problème actuel
L'opération `delete_all_entries` retourne `UNKNOWN_ACTION` dans le coordinator, bien qu'elle soit correctement définie.

## État de l'implémentation

### ✅ Ce qui fonctionne
1. **Interface utilisateur** : Alerte de changement de type avec suppression des données
2. **Détection du changement** : `originalType != trackingType` détecté correctement
3. **Dialogue de confirmation** : Affichage et gestion corrects
4. **Logs de debug** : Traçabilité complète du flow

### ❌ Ce qui ne fonctionne pas
L'appel `coordinator.processUserAction("delete_all_entries", ...)` retourne `UNKNOWN_ACTION`

## Diagnostic effectué

### 1. Vérification de la liste des opérations
```
Available operations: [add_entry, get_entries, update_entry, delete_entry, delete_all_entries, start_activity, stop_activity, stop_all]
```
✅ `delete_all_entries` est bien présent dans `getAvailableOperations()`

### 2. Vérification de l'implémentation TrackingService
✅ Fonction `handleDeleteAllEntries()` implémentée dans TrackingService.kt
✅ Opération ajoutée dans le `when (operation)` du service
✅ Méthode DAO `deleteAllEntriesForToolInstance()` créée

### 3. Vérification des logs
```
=== FINAL SAVE STARTED ===
About to call delete_all_entries for tool: 0bf11908-d2cf-474b-9c04-27a9e278e37d
Delete result - status: UNKNOWN_ACTION, message: null
Failed to delete existing data: null
```
❌ Le service n'est jamais appelé (pas de log "=== handleDeleteAllEntries() called ===")

## Analyse du problème

### Pattern observé pour les opérations qui fonctionnent
D'après les logs de `get_entries` qui fonctionne :
```
execute() called with operation: get_entries, params: {"tool_type":"tracking","operation":"get_entries","tool_instance_id":"..."}
```

### Hypothèses à vérifier
1. **Pattern de nommage** : Le coordinator cherche peut-être une fonction avec un nom spécifique
2. **Mapping des opérations** : Il peut y avoir un mapping manquant entre l'opération et sa fonction
3. **Découverte des opérations** : Le coordinator ne découvre peut-être pas automatiquement toutes les opérations
4. **Implémentation dans le service** : Différence entre les opérations CRUD standard et les opérations custom

## Prochaines étapes à investiguer

### 1. Analyser le pattern des opérations existantes
- Comment `get_entries` est mappé dans le service
- Vérifier s'il y a des fonctions `handle*` pour toutes les opérations
- Examiner le mécanisme de découverte des opérations dans le coordinator

### 2. Solutions possibles à tester
1. **Renommer l'opération** : Utiliser un nom plus standard comme `delete_entries`
2. **Changer le mapping** : Mapper `delete_all_entries` vers une opération existante
3. **Implémenter différemment** : Utiliser le pattern exact des autres opérations
4. **Débugger le coordinator** : Ajouter des logs pour voir comment il découvre les opérations

## Code modifié

### TrackingToolType.kt
```kotlin
override fun getAvailableOperations(): List<String> {
    return listOf(
        "add_entry", "get_entries", "update_entry", "delete_entry", "delete_all_entries",
        "start_activity", "stop_activity", "stop_all"
    )
}
```

### TrackingService.kt
```kotlin
when (operation) {
    "create" -> handleCreate(params, token)
    "update" -> handleUpdate(params, token)
    "delete" -> handleDelete(params, token)
    "delete_all_entries" -> handleDeleteAllEntries(params, token)  // ← Ajouté
    "get_entries" -> handleGetEntries(params, token)
    // ...
}

private suspend fun handleDeleteAllEntries(params: JSONObject, token: CancellationToken): OperationResult {
    Log.d("TrackingService", "=== handleDeleteAllEntries() called ===")
    // ... implémentation complète
}
```

### TrackingDao.kt
```kotlin
@Query("DELETE FROM tracking_data WHERE tool_instance_id = :toolInstanceId")
suspend fun deleteAllEntriesForToolInstance(toolInstanceId: String)
```

### TrackingConfigScreen.kt
```kotlin
val deleteResult = coordinator.processUserAction(
    "delete_all_entries", 
    mapOf("tool_instance_id" to existingToolId)
)
```

## Notes importantes
- Les autres fonctionnalités de validation et d'UI fonctionnent parfaitement
- Le problème est uniquement dans la communication coordinator → service
- L'architecture generale est correcte, il manque juste le lien final