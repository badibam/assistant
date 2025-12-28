# Vérification: Features TOOL_EXECUTIONS vs TOOL_DATA

## Features implémentées (commit f24528e)

### ✅ 1. Field filtering
**TOOL_DATA**: `ToolDataService.filterEntryFields()`
**TOOL_EXECUTIONS**: `ToolExecutionService.filterExecutionFields()` ✅

Support des champs:
- Root fields: id, timestamp, status, etc.
- Nested fields: snapshot_data.title, execution_result.read, etc.

### ✅ 2. Fields parameter in CommandTransformer
**TOOL_DATA**: `transformToolDataCommand()` supporte `fields` param
**TOOL_EXECUTIONS**: `transformToolExecutionsCommand()` supporte `fields` param ✅

### ✅ 3. Dynamic field display in prompts
**CommandExecutor.kt** lignes 804-811:
- Affiche les champs demandés ou "tous" pour backward compatibility
- Appliqué aux deux ✅

### ✅ 4. Schema support
**Commande SCHEMA**: Supporte les execution schemas
- L'IA peut demander `messages_execution` schema
- toolInstanceId enrichit avec custom_fields

### ✅ 5. Schema deduplication
**loadHistoricalSchemas()** lignes 1287-1342:
- Fonctionne de manière générique (schemas.get peu importe le type)
- Clé: schema_id + toolInstanceId
- S'applique aux execution schemas ✅

### ✅ 6. SCHEMA_REQUIRED handling
**SystemMessageType.SCHEMA_REQUIRED**:
- Crée CommandResults pour tracking
- formattedData preservé
- S'applique aux deux types ✅

---

## Impact du changement custom_fields formatés

### Pas d'impact sur features existantes
Les features ci-dessus fonctionnent au niveau des **champs root et nested**, pas au niveau du **contenu** des champs.

**Exemple**:
```json
// Avant (brut)
{
  "custom_fields": {
    "priority_level": 5
  }
}

// Après (formaté)
{
  "custom_fields": {
    "priority_level": "5/10"
  }
}
```

**Fields filtering**:
- `fields: ["custom_fields"]` → retourne l'objet complet (fonctionne pareil)
- `fields: ["custom_fields.priority_level"]` → retourne "5/10" au lieu de 5 (fonctionne pareil, juste valeur différente)

**Schema**:
- Le schema d'exécution change (plus de custom_fields_metadata)
- Mais la déduplication reste fonctionnelle

---

## Actions nécessaires

### ✅ Rien à ajouter
Toutes les features TOOL_DATA sont déjà implémentées pour TOOL_EXECUTIONS.

### ⚠️ À vérifier après implémentation du plan
1. **Schema execution sans custom_fields_metadata**:
   - L'IA peut toujours demander le schema
   - La déduplication fonctionne
   - Le schema retourné est correct

2. **Field filtering avec custom_fields formatés**:
   - Les filtres sur custom_fields.* fonctionnent
   - Les valeurs retournées sont formatées (attendu)

3. **Documentation IA**:
   - Clarifier dans ai_prompt_chunks.xml que custom_fields dans snapshots = formatés
   - Pas de metadata disponible

---

## Conclusion

✅ **Toutes les features queries sont déjà implémentées pour TOOL_EXECUTIONS**

Le changement vers custom_fields formatés n'impacte que:
- Le contenu stocké (formaté vs brut)
- Le schema (retrait de custom_fields_metadata)
- La documentation IA (clarification)

Pas besoin de "refaire" les améliorations queries pour les executions.
