# Plan: Format Custom Fields in Execution Snapshots

## Objectif

Stocker les custom fields FORMATÉS (strings lisibles) dans les snapshots d'exécutions au lieu des valeurs brutes + metadata.

**Scope**: tool_executions uniquement (tool_data reste inchangé)

---

## 1. MessageScheduler.kt

### Nouvelle méthode
```kotlin
private suspend fun formatCustomFields(
    customFieldsJson: String?,
    toolInstanceConfig: Map<String, Any>,
    context: Context
): JSONObject?
```
- Récupère les définitions de custom fields depuis la config
- Pour chaque custom field, applique `FieldDefinition.formatValue()`
- Retourne JSONObject avec valeurs formatées ou null

### Modifier méthode existante
```kotlin
private suspend fun processMessage(
    context: Context,
    messageId: String,
    messageName: String,
    dataJson: String,
    customFieldsJson: String?,
    now: Long,
    messageService: MessageService,
    coordinator: Coordinator,
    instance: Map<String, Any>
)
```
- Ligne 199-206: Remplacer stockage custom_fields bruts
- Appeler `formatCustomFields()` pour obtenir valeurs formatées
- Stocker résultat formaté dans snapshotData

---

## 2. ToolExecutionService.kt

### Retirer enrichissement automatique
```kotlin
suspend fun execute(
    operation: String,
    params: JSONObject,
    token: CancellationToken
): OperationResult
```
- Lignes 74-116: SUPPRIMER le bloc d'enrichissement custom_fields_metadata
- L'outil appelant (MessageScheduler) est responsable du formatage
- Simplifier la logique de création

---

## 3. MessageToolType.kt

### Modifier schéma d'exécution
```kotlin
private fun createMessagesExecutionSchema(context: Context): Schema
```
- Lignes 266-269: Modifier description custom_fields
  - Préciser "formatted string values for human readability"
- Lignes 270-292: SUPPRIMER la propriété `custom_fields_metadata`
- Ligne 294: Retirer `custom_fields_metadata` des required (si présent)

---

## 4. ai_prompt_chunks.xml

### Mettre à jour documentation TOOL_EXECUTIONS
```xml
<string name="ai_chunk_commands_queries_signatures">
```
- Ligne 280: Clarifier description du retour
- Préciser que `snapshot_data` contient:
  - Champs core de l'outil (structure spécifique, valeurs brutes si pertinent)
  - `custom_fields`: valeurs formatées (strings lisibles, pas de metadata)

---

## 5. FieldDefinition.kt (vérification)

### Vérifier disponibilité
```kotlin
fun FieldDefinition.formatValue(value: Any?, context: Context): String
```
- Confirmer que la méthode est accessible depuis MessageScheduler
- Vérifier tous les types de fields supportés (TEXT, SCALE, CHOICE, BOOLEAN, etc.)

---

## 6. Tests manuels

### Scénarios à tester
1. Créer un Message avec custom fields (SCALE, CHOICE, TEXT)
2. Planifier l'exécution
3. Déclencher l'exécution (attendre ou forcer)
4. Vérifier dans DB:
   - `tool_executions.snapshot_data` contient custom_fields formatés
   - Pas de custom_fields_metadata
5. Tester query TOOL_EXECUTIONS via IA
   - Vérifier que l'IA reçoit les valeurs formatées
   - Confirmer lisibilité

---

## Notes importantes

- **tool_data reste inchangé** (valeurs brutes pour l'IA)
- **Pas de migration DB nécessaire** (vieux snapshots restent lisibles tels quels)
- **Rétrocompatibilité**: Lecture des vieux snapshots avec custom_fields_metadata fonctionne toujours
- **Logique par outil**: Chaque ToolType peut décider sa structure `data` dans snapshot
