# Bug : Custom Fields absents des Executions

## Problème

Les custom fields ne sont **pas inclus dans les snapshots d'exécution** (tool_executions).

### Situation actuelle

- `ToolDataEntity` : contient `customFields` (colonne séparée) ✅
- `ToolExecutionEntity` : ne contient **pas** de colonne `customFields` ❌
- Seule `snapshotData` (JSON) pourrait contenir les custom fields

### Impact

Quand un outil supporte les exécutions (Messages, Goals, Alerts...) :
- Le template (tool_data) contient les custom fields ✅
- L'historique d'exécution (tool_executions) **ne capture pas** les custom fields ❌
- L'IA ne peut pas analyser l'évolution des custom fields via EXECUTIONS context

### Exemple concret (Messages)

```
Template en DB :
- title: "Rappel méditation"
- content: "N'oublie pas ta séance"
- custom_fields: {"mood_before": "stressed", "duration_minutes": "20"}

Execution créée :
- snapshot_data: {"title": "...", "content": "...", "priority": "default"}
- custom_fields: ❌ ABSENTS
```

## Solution

### 1. Schéma de base (BaseSchemas)

Ajouter support custom_fields dans `getBaseExecutionSchema()` :
- Même pattern que `getBaseDataSchema()`
- `custom_fields` optionnel dans properties

### 2. Schémas d'exécution spécifiques

**Messages** (MessageToolType.createMessagesExecutionSchema) :
- Dans `snapshot_data.properties` : enlever `"additionalProperties": false` OU ajouter `custom_fields` explicite
- Le schéma doit autoriser custom_fields dans le snapshot

**Pattern général** : Tous les tooltypes avec executions doivent mettre à jour leur execution schema.

### 3. Schedulers et création d'exécutions

**MessageScheduler.checkScheduled()** :
- Récupérer `customFields` de l'entry (en plus de `data`)
- Passer à `processMessage()`

**MessageScheduler.processMessage()** :
- Accepter param `customFieldsJson`
- L'ajouter dans `snapshotData` lors de création execution

### 4. Service tool_executions

Vérifier que `ToolExecutionsService.create()` accepte et stocke les custom_fields dans snapshotData.

## Notes

- Pas besoin de colonne DB séparée pour executions (contrairement à tool_data)
- Les custom_fields vont directement dans le JSON `snapshotData`
- Le schéma execution doit supporter `custom_fields` optionnel
- Pattern à généraliser pour tous les tooltypes avec `supportsExecutions() = true`

## Statut

**Non corrigé** - Priorité moyenne (feature manquante, pas de régression)

## Autres points :
- compléter migration actuelle (pas besoin d'en faire une nouvelle, l'actuelle n'a pas été released)
- vérifier export/import des executions dans backupservices
- vérifier fonctionnement POINTER pour les executions (schema execution avec instance id - comme pour DATA)
- vérifier transfo des commandes IA si tout va bien
