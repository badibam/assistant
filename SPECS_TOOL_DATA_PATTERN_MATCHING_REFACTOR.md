# Specs: Refactor TOOL_DATA validation with pattern matching

## Objectif

Simplifier la validation TOOL_DATA en utilisant pattern matching au lieu de lister explicitement les champs autorisés.

**Cohérence**: Aligner avec l'approche pattern matching de TOOL_EXECUTIONS.

---

## État actuel (à vérifier)

### Validation probable
- Liste explicite des champs root autorisés
- Validation séparée pour `data.*` et `custom_fields.*`
- Messages d'erreur multiples

### Documentation
- Liste tous les champs root disponibles
- Exemples séparés pour chaque type

---

## État cible

### Règles de validation - Pattern matching

#### Patterns valides

**1. Champs root** (sans point):
- Pattern: `*` (pas de `.` dans le nom)
- Exemples: `id`, `timestamp`, `name`, `toolInstanceId`, `tooltype`, `createdAt`, `updatedAt`

**2. Champs data** (avec sous-champ):
- Pattern: `data.*` (au moins 1 niveau après `data.`)
- Exemples: `data.value`, `data.text`, `data.content`, `data.priority`

**3. Champs custom fields** (avec nom de field):
- Pattern: `custom_fields.*` (au moins 1 niveau après `custom_fields.`)
- Exemples: `custom_fields.notes`, `custom_fields.priority_level`, `custom_fields.mood`

#### Patterns INVALIDES

- ❌ `data` (seul, sans sous-champ)
- ❌ `custom_fields` (seul, sans nom de field)
- ❌ Tout champ avec pattern inconnu

---

## Modifications nécessaires

### 1. Strings d'erreur (shared.xml)

#### String: ai_validation_tool_data_invalid_field_pattern
```
Champ invalide '%1$s' dans TOOL_DATA.

Patterns valides :
- Champs root (sans point) : "id", "timestamp", "name"
- Champs data : "data.value", "data.text"
- Custom fields : "custom_fields.field_name"

Patterns INTERDITS :
- "data" seul (spécifier sous-champ)
- "custom_fields" seul (spécifier nom du field)
```

#### String: ai_validation_tool_data_example
```
Exemple de requête TOOL_DATA valide :
{
  "type": "TOOL_DATA",
  "params": {
    "id": "tool_instance_id",
    "fields": [
      "id",
      "timestamp",
      "name",
      "data.value",
      "custom_fields.notes"
    ]
  }
}
```

---

### 2. Validation (AICommandProcessor.kt)

#### Modifier le bloc TOOL_DATA existant

Remplacer la validation actuelle par pattern matching :

```kotlin
// Dans processDataCommands(), bloc TOOL_DATA
for ((index, field) in fields.withIndex()) {
    val fieldStr = field.toString()

    // Pattern detection
    val isRootField = !fieldStr.contains(".")
    val isDataField = fieldStr.startsWith("data.")
    val isCustomField = fieldStr.startsWith("custom_fields.")

    // Validation
    when {
        // INVALIDE: data seul
        fieldStr == "data" -> {
            val errorMsg = s.shared("ai_validation_tool_data_invalid_field_pattern")
                .format(fieldStr) + "\n" + s.shared("ai_validation_tool_data_example")
            validationErrors.add("Field[$index]: $errorMsg")
        }

        // INVALIDE: custom_fields seul
        fieldStr == "custom_fields" -> {
            val errorMsg = s.shared("ai_validation_tool_data_invalid_field_pattern")
                .format(fieldStr) + "\n" + s.shared("ai_validation_tool_data_example")
            validationErrors.add("Field[$index]: $errorMsg")
        }

        // VALIDE: root, data.*, custom_fields.*
        isRootField || isDataField || isCustomField -> {
            // OK
        }

        // INVALIDE: pattern inconnu
        else -> {
            val errorMsg = s.shared("ai_validation_tool_data_invalid_field_pattern")
                .format(fieldStr) + "\n" + s.shared("ai_validation_tool_data_example")
            validationErrors.add("Field[$index]: $errorMsg")
        }
    }
}
```

---

### 3. Documentation IA (ai_prompt_chunks.xml)

#### Mettre à jour section TOOL_DATA

Simplifier la description du paramètre `fields` :

```
**Paramètres** :

- `id` (string, **requis**) : tool_instance_id
- `fields` (array, **requis**) : Champs à récupérer (pattern matching)
- `period` (object, optionnel) : Filtre temporel sur timestamp
- `limit` (number, optionnel) : Nombre maximum d\'entrées
- `offset` (number, optionnel) : Décalage pour pagination

**Champ 'fields' - Patterns valides** :

1. **Champs root** (sans point) : `"id"`, `"timestamp"`, `"name"`, `"createdAt"`, `"updatedAt"`, `"toolInstanceId"`, `"tooltype"`

2. **Champs data** (avec sous-champ) : `"data.value"`, `"data.text"`, `"data.content"`, `"data.priority"`

3. **Custom fields** : `"custom_fields.notes"`, `"custom_fields.priority_level"`, `"custom_fields.mood"`

**INTERDICTIONS** :
- ❌ `"data"` seul (spécifier sous-champ)
- ❌ `"custom_fields"` seul (spécifier nom du field)

**Exemple** :
```json
{
  "type": "TOOL_DATA",
  "params": {
    "id": "tool_instance_id",
    "fields": [
      "id",
      "timestamp",
      "name",
      "data.value",
      "custom_fields.notes"
    ],
    "period": {"start": 123456789, "end": 123456999}
  }
}
```

**Retour** : Données filtrées selon les champs demandés, avec `config_extract` pour contexte.

**IMPORTANT - Schema requis** : Le schéma de données doit être disponible AVANT de requêter.
Si manquant, vous recevrez SCHEMA_REQUIRED avec le schéma complet.
```

---

### 4. Field filtering (ToolDataService.kt)

#### Vérifier filterEntryFields()

S'assurer que la méthode existante est compatible avec l'approche pattern matching.

La logique actuelle devrait déjà fonctionner :
- Sépare par pattern (root, data.*, custom_fields.*)
- Parse et filtre les JSONs
- Retourne champs demandés

**Aucun changement nécessaire** si déjà pattern-based.

---

### 5. Retirer validations spécifiques (si existantes)

#### Chercher et retirer

Si des validations spécifiques existent ailleurs (liste de champs autorisés), les retirer :

```kotlin
// ANCIEN (à retirer si existe)
val allowedRootFields = listOf("id", "timestamp", "name", "toolInstanceId", ...)
if (field !in allowedRootFields) { error... }

// NOUVEAU (pattern matching)
val isRootField = !field.contains(".")
```

---

## Ordre d'implémentation

1. Vérifier état actuel de la validation TOOL_DATA dans AICommandProcessor
2. Ajouter 2 strings d'erreur dans shared.xml
3. Générer strings (./gradlew generateStringResources)
4. Remplacer validation TOOL_DATA par pattern matching dans AICommandProcessor
5. Vérifier que filterEntryFields() dans ToolDataService fonctionne avec patterns
6. Mettre à jour documentation IA (ai_prompt_chunks.xml)
7. Retirer anciennes validations spécifiques si existantes
8. Compiler et tester
9. Mettre à jour exemples dans documentation si nécessaire

---

## Avantages du refactor

### Simplicité
- Moins de code de validation
- Pas de liste hardcodée de champs
- Messages d'erreur unifiés

### Extensibilité
- Nouveaux champs root automatiquement supportés
- Pas besoin de modifier validation pour nouveaux champs data

### Cohérence
- Même approche que TOOL_EXECUTIONS
- Documentation unifiée

### Maintenabilité
- Moins de strings d'erreur
- Code plus générique

---

## Tests à effectuer après implémentation

### Test 1: Champs valides
```json
{
  "fields": [
    "id",
    "timestamp",
    "data.value",
    "custom_fields.notes"
  ]
}
```
**Résultat attendu**: ✅ Validation OK

### Test 2: data seul
```json
{
  "fields": ["id", "data"]
}
```
**Résultat attendu**: ❌ Erreur avec message pattern + exemple

### Test 3: custom_fields seul
```json
{
  "fields": ["id", "custom_fields"]
}
```
**Résultat attendu**: ❌ Erreur avec message pattern + exemple

### Test 4: Pattern inconnu
```json
{
  "fields": ["id", "unknown.field.pattern"]
}
```
**Résultat attendu**: ❌ Erreur avec message pattern + exemple

### Test 5: Nouveaux champs root futurs
```json
{
  "fields": ["id", "new_future_root_field"]
}
```
**Résultat attendu**: ✅ Validation OK (extensibilité)

---

## Notes importantes

- **Rétrocompatibilité**: Validation plus permissive pour champs root
- **Cohérence**: Aligner exactement avec TOOL_EXECUTIONS
- **Documentation**: Simplifier en expliquant patterns au lieu de lister champs
- **Messages d'erreur**: Utiliser s.shared(), jamais hardcoded
- **Tests**: Vérifier que tous les cas existants passent toujours

---

## Checklist de validation

- [ ] Strings d'erreur ajoutées dans shared.xml
- [ ] Validation pattern matching implémentée dans AICommandProcessor
- [ ] Documentation IA mise à jour avec patterns
- [ ] filterEntryFields() vérifié (devrait fonctionner tel quel)
- [ ] Anciennes validations spécifiques retirées
- [ ] Tests manuels effectués (5 scénarios ci-dessus)
- [ ] Compilation OK
- [ ] Pas de régression sur queries existantes
