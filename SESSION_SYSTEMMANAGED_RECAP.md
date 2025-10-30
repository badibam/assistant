# Session systemManaged - Récapitulatif

## 1. Objectif initial

Implémenter un système pour empêcher l'IA de fournir des champs systemManaged (id, created_at, updated_at, schema_id, tooltype, etc.) qui doivent être générés automatiquement par le système.

## 2. Ce qui a été fait

### ✅ Modifications conservées (utiles)

1. **BaseSchemas.kt** - Ajout flag `systemManaged: true`
   - `BaseDataSchema` : `id`, `created_at`, `updated_at`, `schema_id` marqués systemManaged
   - `BaseDataSchema` : `timestamp` retiré du required (devient optionnel avec default)
   - `BaseConfigSchema` : `schema_id`, `data_schema_id`, `execution_schema_id` PAS systemManaged (choix IA)
   - `BaseDataSchema` : `tool_instance_id` PAS systemManaged (fourni par IA)
   - `BaseDataSchema` : `tooltype` systemManaged (injecté automatiquement)

2. **ToolDataService.kt** - Default timestamp
   - Ligne 55 : `val timestamp = if (params.has("timestamp")) params.optLong("timestamp") else System.currentTimeMillis()`

3. **ai_prompt_chunks.xml** - Documentation L1
   - Nouveau chunk `ai_chunk_validation_system_managed` (DEGRÉ 1)
   - Explique que les champs avec `systemManaged: true` ne doivent jamais être fournis

4. **PromptChunks.kt** - Inclusion du chunk
   - Ligne 73 : Ajout du chunk dans les prompts L1

5. **JsonNormalizer.kt** - Fix pattern matching JSONObject
   - Lignes 59-76 : Détection `JSONObject$1` et sous-classes anonymes via `javaClass.name.startsWith()`
   - **CRITIQUE** : Ce fix était nécessaire pour un autre problème (sérialisation)

### ❌ Modifications à simplifier/retirer (over-engineering)

1. **AICommandProcessor.kt** - Architecture complexe inutile

   **Fonctions à SUPPRIMER** :
   - `stripSystemManagedFromCommand()` (lignes 486-522) → TROP COMPLEXE
   - `getApplicableSchema()` (lignes 524-595) → TROP COMPLEXE
   - `extractSystemManagedFields()` (lignes 597-628) → TROP COMPLEXE
   - `stripFieldsRecursively()` (lignes 630-704) → TROP COMPLEXE, déjà géré par JsonNormalizer
   - `injectTooltype()` (lignes 422-463) → TROP COMPLEXE

   **Fonction à CONSERVER mais SIMPLIFIER** :
   - `enrichWithSchemaId()` → Retirer la normalisation redondante (lignes 383-386)

2. **Logique de stripping à revoir complètement**

## 3. Le problème identifié

### Problème root cause
L'IA envoie :
```json
{
  "entries": [{
    "name": "Test",
    "data": {
      "schema_id": "messages_data",  // ← REQUIS, fourni par IA
      "priority": "default"
    }
  }]
}
```

Mais `stripFieldsRecursively()` strip `schema_id` **partout récursivement**, y compris dans `data` où il est REQUIS.

### Pourquoi c'est arrivé
On a confondu DEUX types de `schema_id` :
- **Entry-level** (racine) : `entry.schema_id` → systemManaged, auto-injecté par `enrichWithSchemaId()`
- **Data-level** (nested) : `entry.data.schema_id` → REQUIS, fourni par l'IA pour identifier le variant

### Erreur de conception
On a créé un système ultra-complexe pour stripper récursivement alors que :
1. Les champs systemManaged sont UNIQUEMENT au niveau racine des entries (BaseDataSchema)
2. Il n'y a AUCUN champ systemManaged nested actuellement
3. `data.schema_id` est un champ MÉTIER différent qui ne doit JAMAIS être strippé

## 4. La solution correcte (à implémenter)

### Principe simple
**Stripper UNIQUEMENT au niveau racine de chaque entry, JAMAIS récursivement dans les nested objects**

### Implémentation

```kotlin
// Dans AICommandProcessor.processActionCommands()

for ((index, command) in commands.withIndex()) {
    try {
        // 1. Aucun stripping générique ! Les champs systemManaged sont au niveau racine entry uniquement
        //    JsonNormalizer a déjà tout normalisé (JSONObject → Map)

        // 2. Pour CREATE_DATA/UPDATE_DATA : strip au niveau racine de chaque entry
        val cleanedCommand = if (command.type == "CREATE_DATA" || command.type == "UPDATE_DATA") {
            stripRootLevelSystemManagedFields(command)
        } else {
            command
        }

        // 3. Inject tooltype (si toolInstanceId présent)
        val enrichedCommand = injectTooltypeIfNeeded(cleanedCommand)

        // 4. Transform
        val executableCommand = transformActionCommand(enrichedCommand)
        ...
    }
}

// Fonction simple : strip seulement les champs racine de chaque entry
private fun stripRootLevelSystemManagedFields(command: DataCommand): DataCommand {
    if (command.type != "CREATE_DATA" && command.type != "UPDATE_DATA") {
        return command
    }

    val entries = command.params["entries"] as? List<Map<String, Any>> ?: return command

    // Champs systemManaged de BaseDataSchema (niveau racine entry uniquement)
    val rootSystemManagedFields = setOf("id", "created_at", "updated_at", "schema_id", "tooltype")

    val cleanedEntries = entries.map { entry ->
        val mutableEntry = entry.toMutableMap()
        rootSystemManagedFields.forEach { field ->
            mutableEntry.remove(field)  // Strip seulement au niveau racine, pas nested
        }
        mutableEntry
    }

    return command.copy(params = command.params.toMutableMap().apply {
        put("entries", cleanedEntries)
    })
}

// Simplifier injectTooltype : pas besoin de try/catch complexe
private suspend fun injectTooltypeIfNeeded(command: DataCommand): DataCommand {
    val toolInstanceId = command.params["toolInstanceId"] as? String ?: return command

    // Fetch tool_type from DB
    val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
    if (!result.isSuccess) return command

    val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
    val tooltype = toolInstance?.get("tool_type") as? String ?: return command

    return command.copy(params = command.params.toMutableMap().apply {
        put("tooltype", tooltype)
    })
}
```

## 5. Simplifications à faire

### À SUPPRIMER complètement
1. `stripSystemManagedFromCommand()` - Over-engineering
2. `getApplicableSchema()` - Over-engineering
3. `extractSystemManagedFields()` - Over-engineering
4. `stripFieldsRecursively()` dans AICommandProcessor - Redondant avec JsonNormalizer
5. Appel à `stripFieldsRecursively()` dans `enrichWithSchemaId()` ligne 385 - Redondant

### À SIMPLIFIER
1. `injectTooltype()` - Retirer le try/catch, retirer les logs verbeux
2. `enrichWithSchemaId()` - Retirer la normalisation (déjà faite par JsonNormalizer)
3. `processActionCommands()` - Remplacer toute la logique par les 2 fonctions simples ci-dessus

### À CONSERVER tel quel
1. JsonNormalizer fix - CRITIQUE pour la sérialisation
2. BaseSchemas flags systemManaged - Documentation pour l'IA
3. Prompt L1 chunk - Documentation pour l'IA
4. Timestamp default - Bonne pratique

## 6. Pourquoi c'est devenu si complexe

### Erreurs de conception
1. **Over-généralisation** : On a cru qu'il y avait des champs systemManaged nested → création d'un système récursif complexe
2. **Duplication** : JsonNormalizer normalise déjà, pas besoin de re-normaliser dans stripFieldsRecursively
3. **Abstraction excessive** : `getApplicableSchema()` + `extractSystemManagedFields()` alors qu'on connaît les champs à l'avance
4. **Pas de recul** : On a continué à ajouter des couches au lieu de revenir aux fondamentaux

### Leçon
Quand un problème simple (strip quelques champs au niveau racine) nécessite 500+ lignes de code complexe, c'est qu'on fait fausse route.

## 7. Plan d'action pour la prochaine session

1. **SUPPRIMER** toutes les fonctions complexes listées ci-dessus
2. **IMPLÉMENTER** les 2 fonctions simples : `stripRootLevelSystemManagedFields()` et `injectTooltypeIfNeeded()`
3. **TESTER** que ça marche
4. **COMMIT** avec message : "Simplify systemManaged stripping - strip only at entry root level"

## 8. Note sur tool_type vs tooltype

**ATTENTION** : En DB la colonne s'appelle `tool_type` (underscore), pas `tooltype`.

Déjà corrigé dans :
- `injectTooltype()` ligne 447 : `toolInstance?.get("tool_type")`
- `getApplicableSchema()` ligne 552 : `toolInstance?.get("tool_type")`

Ne pas oublier dans les nouvelles simplifications.
